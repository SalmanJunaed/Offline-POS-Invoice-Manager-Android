package com.example.pos

import android.app.Application
import android.os.Looper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.example.pos.core.security.InMemoryAesGcmSessionCipher
import com.example.pos.core.security.SecureSessionManager
import com.example.pos.data.db.PosDatabase
import com.example.pos.data.db.ProductEntity
import com.example.pos.data.repository.PosRepository
import com.example.pos.domain.FinancialCalculationEngine
import com.example.pos.ui.viewmodel.PosViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BillingRateFieldRegressionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var app: Application
    private lateinit var db: PosDatabase
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var repository: PosRepository
    private lateinit var viewModel: PosViewModel
    private lateinit var operatorPhone: String

    @Before
    fun setup() = runBlocking {
        app = ApplicationProvider.getApplicationContext<Application>()
        db = PosDatabase.getInstance(app)
        db.awaitInitialized()

        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dsFile = tempFolder.newFile("billing_rate_session_${System.nanoTime()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) { dsFile }
        val sessionManager = SecureSessionManager.createForTest(
            dataStore = dataStore,
            cipher = InMemoryAesGcmSessionCipher()
        )
        repository = PosRepository(app, db, sessionManager)
        operatorPhone = "01788${(100000..999999).random()}"
        val regRes = repository.registerOperator("Billing Operator", operatorPhone, "Main Branch", "1234")
        assertTrue("Operator setup must succeed: ${regRes.exceptionOrNull()?.message}", regRes.isSuccess)

        viewModel = PosViewModel.createForTest(app, repository)
        var attempts = 0
        while (viewModel.activeOperator.value == null && attempts < 40) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            delay(20)
            attempts++
        }
        assertNotNull("Operator session must be active for checkout test", viewModel.activeOperator.value)
    }

    @After
    fun tearDown() = runBlocking {
        if (::viewModel.isInitialized) {
            viewModel.clearDraft()
        }
        if (::repository.isInitialized) {
            repository.logout()
        }
        if (::dataStoreScope.isInitialized) {
            dataStoreScope.cancel()
        }
    }

    @Test
    fun testBillingRateFieldRegressionSuite() = runBlocking {
        // Seed catalog product: Octane / অকটেন with default price 130 BDT (13000 Paisa)
        val octane = ProductEntity(
            itemId = "prod-octane-reg",
            itemName = "Octane",
            nameBn = "অকটেন",
            nameEn = "Octane",
            itemType = "Fuel",
            unit = "Liter",
            defaultUnitPricePaisa = 13000L,
            sortOrder = 1
        )
        db.productDao().insertProduct(octane)

        // -------------------------------------------------------------
        // TEST A: Add অকটেন, catalog price 130
        // -> Rate/দর shows 130
        // -> Quantity shows 1
        // -> Total shows 130
        // -------------------------------------------------------------
        viewModel.addItemFromCatalog(octane)
        val itemsA = viewModel.draftItems.first()
        assertEquals(1, itemsA.size)
        val itemA = itemsA[0]
        assertEquals("1", itemA.quantityStr)
        assertEquals("130", itemA.unitPriceBdtStr)
        assertEquals(13000L, itemA.unitPricePaisa)
        assertEquals(13000L, itemA.lineTotalPaisa)
        assertEquals("130.00", FinancialCalculationEngine.formatPaisaToBdt(itemA.lineTotalPaisa))

        // -------------------------------------------------------------
        // TEST B: Change Rate/দর 130 -> 120
        // -> Rate shows 120
        // -> Total becomes 120
        // -> No "120130"
        // -------------------------------------------------------------
        viewModel.updateItemUnitPrice(itemA.tempId, "120")
        val itemsB = viewModel.draftItems.first()
        val itemB = itemsB[0]
        assertEquals("120", itemB.unitPriceBdtStr)
        assertNotEquals("120130", itemB.unitPriceBdtStr)
        assertEquals(12000L, itemB.unitPricePaisa)
        assertEquals(12000L, itemB.lineTotalPaisa)
        assertEquals("120.00", FinancialCalculationEngine.formatPaisaToBdt(itemB.lineTotalPaisa))

        // -------------------------------------------------------------
        // TEST C: Quantity 2.5, Rate 120
        // -> Total becomes 300
        // -------------------------------------------------------------
        viewModel.updateItemQuantity(itemB.tempId, "2.5")
        val itemsC = viewModel.draftItems.first()
        val itemC = itemsC[0]
        assertEquals("2.5", itemC.quantityStr)
        assertEquals("120", itemC.unitPriceBdtStr)
        assertEquals(12000L, itemC.unitPricePaisa)
        // 2.5 * 120 = 300.00 BDT -> 30000 Paisa
        assertEquals(30000L, itemC.lineTotalPaisa)
        assertEquals("300.00", FinancialCalculationEngine.formatPaisaToBdt(itemC.lineTotalPaisa))

        // -------------------------------------------------------------
        // TEST D: Change Rate 120 -> 130
        // -> Total becomes 325 for quantity 2.5
        // -------------------------------------------------------------
        viewModel.updateItemUnitPrice(itemC.tempId, "130")
        val itemsD = viewModel.draftItems.first()
        val itemD = itemsD[0]
        assertEquals("2.5", itemD.quantityStr)
        assertEquals("130", itemD.unitPriceBdtStr)
        assertEquals(13000L, itemD.unitPricePaisa)
        // 2.5 * 130 = 325.00 BDT -> 32500 Paisa
        assertEquals(32500L, itemD.lineTotalPaisa)
        assertEquals("325.00", FinancialCalculationEngine.formatPaisaToBdt(itemD.lineTotalPaisa))

        // -------------------------------------------------------------
        // TEST E: Verify Product Management still shows catalog price 130
        // -------------------------------------------------------------
        val catalogProduct = db.productDao().getProductById("prod-octane-reg")
        assertNotNull(catalogProduct)
        assertEquals(13000L, catalogProduct?.defaultUnitPricePaisa)
        assertEquals("130", FinancialCalculationEngine.formatPaisaToBdtInput(catalogProduct!!.defaultUnitPricePaisa))

        // -------------------------------------------------------------
        // TEST F: Checkout and verify saved invoice uses the actual invoice-specific rate
        // -------------------------------------------------------------
        val currentOrderId = viewModel.draftOrderId.value
        viewModel.checkoutAndSave(printDirectly = false)

        // Pump Robolectric main looper & allow IO coroutine to persist
        var saveAttempts = 0
        while (db.invoiceDao().getInvoiceById(currentOrderId) == null && saveAttempts < 40) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            delay(25)
            saveAttempts++
        }

        val savedInvoice = db.invoiceDao().getInvoiceById(currentOrderId)
        assertNotNull(savedInvoice)
        assertEquals(32500L, savedInvoice?.rawTotalPaisa)
        assertEquals(325L, savedInvoice?.roundedTotalBdt)
        assertEquals(operatorPhone, db.operatorDao().getOperatorById(savedInvoice!!.operatorId)?.operatorPhone)

        val savedLines = db.invoiceDao().getLinesForOrder(currentOrderId)
        assertEquals(1, savedLines.size)
        val savedLine = savedLines[0]
        assertEquals("prod-octane-reg", savedLine.itemId)
        assertEquals(13000L, savedLine.unitPricePaisa)
        assertEquals(32500L, savedLine.lineTotalPaisa)
        assertEquals("2.5", savedLine.quantityStr)
    }

    @Test
    fun testFormatPaisaToBdtInputCleanFormatting() {
        assertEquals("130", FinancialCalculationEngine.formatPaisaToBdtInput(13000L))
        assertEquals("120", FinancialCalculationEngine.formatPaisaToBdtInput(12000L))
        assertEquals("105", FinancialCalculationEngine.formatPaisaToBdtInput(10500L))
        assertEquals("130.5", FinancialCalculationEngine.formatPaisaToBdtInput(13050L))
        assertEquals("130.75", FinancialCalculationEngine.formatPaisaToBdtInput(13075L))
        assertEquals("0", FinancialCalculationEngine.formatPaisaToBdtInput(0L))
    }
}
