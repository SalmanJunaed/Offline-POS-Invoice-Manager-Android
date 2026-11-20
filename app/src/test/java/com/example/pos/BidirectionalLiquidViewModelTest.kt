package com.example.pos

import android.app.Application
import android.os.Looper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.pos.core.security.InMemoryAesGcmSessionCipher
import com.example.pos.core.security.SecureSessionManager
import com.example.pos.data.db.AppSettingsEntity
import com.example.pos.data.db.PosDatabase
import com.example.pos.data.db.ProductEntity
import com.example.pos.data.repository.PosRepository
import com.example.pos.ui.viewmodel.DraftCalculationDriver
import com.example.pos.ui.viewmodel.PosViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
class BidirectionalLiquidViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var app: Application
    private lateinit var db: PosDatabase
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var repository: PosRepository
    private lateinit var viewModel: PosViewModel

    private val octaneProduct = ProductEntity(
        itemId = "liq-octane",
        itemName = "Octane",
        nameBn = "অকটেন",
        nameEn = "Octane",
        itemType = "Liquid",
        unit = "Liter",
        defaultUnitPricePaisa = 13000L,
        sortOrder = 1
    )

    private val fuelProduct = ProductEntity(
        itemId = "liq-diesel",
        itemName = "Diesel",
        nameBn = "ডিজেল",
        nameEn = "Diesel",
        itemType = "Fuel",
        unit = "Liter",
        defaultUnitPricePaisa = 10500L,
        sortOrder = 2
    )

    private val mobilProductWithLiterUnit = ProductEntity(
        itemId = "prod-mobil",
        itemName = "Mobil 10W-40",
        nameBn = "মবিল",
        nameEn = "Mobil 10W-40",
        itemType = "Product",
        unit = "Liter",
        defaultUnitPricePaisa = 68000L,
        sortOrder = 3
    )

    @Before
    fun setup() = runBlocking {
        app = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(app, PosDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        db.appSettingsDao().insertOrUpdate(
            AppSettingsEntity(id = 1, language = "en", themeMode = "LIGHT", nextOrderSequence = 20001L)
        )
        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dsFile = tempFolder.newFile("bidir_vm_${System.nanoTime()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) { dsFile }
        val sessionManager = SecureSessionManager.createForTest(dataStore, InMemoryAesGcmSessionCipher())
        repository = PosRepository(app, db, sessionManager)
        val reg = repository.registerOperator("Cashier", "01712345678", "Main", "1234")
        assertTrue(reg.isSuccess)
        viewModel = PosViewModel.createForTest(app, repository)
        var tries = 0
        while (viewModel.activeOperator.value == null && tries < 30) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            delay(15)
            tries++
        }
        assertNotNull(viewModel.activeOperator.value)
    }

    @After
    fun tearDown() = runBlocking {
        viewModel.cancelScopeForTest()
        db.close()
        dataStoreScope.cancel()
    }

    @Test
    fun testCatalogProductInitializesWithQuantityDriver() {
        viewModel.addItemFromCatalog(octaneProduct)
        val item = viewModel.draftItems.value.single()
        assertEquals(DraftCalculationDriver.QUANTITY, item.calculationDriver)
        assertEquals("1", item.quantityStr)
        assertEquals("130", item.unitPriceBdtStr)
        assertEquals(13000L, item.lineTotalPaisa)
        assertEquals("130", item.lineTotalBdtStr)
    }

    @Test
    fun testQuantityEditRecalculatesTotal() {
        viewModel.addItemFromCatalog(octaneProduct)
        val tempId = viewModel.draftItems.value.single().tempId

        viewModel.updateItemQuantity(tempId, "12")
        val updated = viewModel.draftItems.value.single()
        assertEquals("12", updated.quantityStr)
        assertEquals(156000L, updated.lineTotalPaisa)
        assertEquals("1560", updated.lineTotalBdtStr)
        assertEquals(DraftCalculationDriver.QUANTITY, updated.calculationDriver)
    }

    @Test
    fun testTotalEditRecalculatesQuantityAndKeepsAuthoritativeTotal() {
        viewModel.addItemFromCatalog(octaneProduct)
        val tempId = viewModel.draftItems.value.single().tempId

        viewModel.updateItemUnitPrice(tempId, "13")
        viewModel.updateItemTotalAmount(tempId, "100")

        val updated = viewModel.draftItems.value.single()
        assertEquals("7.69", updated.quantityStr)
        assertEquals(10000L, updated.lineTotalPaisa)
        assertEquals("100", updated.lineTotalBdtStr)
        assertEquals(DraftCalculationDriver.TOTAL_AMOUNT, updated.calculationDriver)
        // Subtotal uses authoritative 100.00 BDT, NOT 13 * 7.69 = 99.97
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(10000L, viewModel.rawSubtotalPaisa.value)
    }

    @Test
    fun testUnitPriceEditRespectsQuantityAndTotalDrivers() {
        viewModel.addItemFromCatalog(octaneProduct)
        val tempId = viewModel.draftItems.value.single().tempId

        // 1. In QUANTITY mode: Qty = 12, Price 130 -> 125 => Total becomes 1,500
        viewModel.updateItemQuantity(tempId, "12")
        viewModel.updateItemUnitPrice(tempId, "125")
        val qtyModeItem = viewModel.draftItems.value.single()
        assertEquals("12", qtyModeItem.quantityStr)
        assertEquals(150000L, qtyModeItem.lineTotalPaisa)
        assertEquals("1500", qtyModeItem.lineTotalBdtStr)
        assertEquals(DraftCalculationDriver.QUANTITY, qtyModeItem.calculationDriver)

        // 2. Switch to TOTAL_AMOUNT mode: Price = 13, Total = 100 -> Qty = 7.69
        viewModel.updateItemUnitPrice(tempId, "13")
        viewModel.updateItemTotalAmount(tempId, "100")
        assertEquals("7.69", viewModel.draftItems.value.single().quantityStr)

        // Now change Price 13 -> 10: Total MUST remain 10000 Paisa, Qty recalculates to 10
        viewModel.updateItemUnitPrice(tempId, "10")
        val amountModeItem = viewModel.draftItems.value.single()
        assertEquals("10", amountModeItem.quantityStr)
        assertEquals(10000L, amountModeItem.lineTotalPaisa)
        assertEquals("100", amountModeItem.lineTotalBdtStr)
        assertEquals(DraftCalculationDriver.TOTAL_AMOUNT, amountModeItem.calculationDriver)
    }

    @Test
    fun testAlternatingDriverSwitchingPreservesRawInput() {
        viewModel.addItemFromCatalog(fuelProduct)
        val tempId = viewModel.draftItems.value.single().tempId

        viewModel.updateItemTotalAmount(tempId, "210.")
        var item = viewModel.draftItems.value.single()
        assertEquals(DraftCalculationDriver.TOTAL_AMOUNT, item.calculationDriver)
        assertEquals("210.", item.lineTotalBdtStr)
        assertEquals("2", item.quantityStr)

        // Switch back to Quantity driver
        viewModel.updateItemQuantity(tempId, "3.")
        item = viewModel.draftItems.value.single()
        assertEquals(DraftCalculationDriver.QUANTITY, item.calculationDriver)
        assertEquals("3.", item.quantityStr)
        assertEquals(31500L, item.lineTotalPaisa)
        assertEquals("315", item.lineTotalBdtStr)

        // Unit price edit now follows QUANTITY mode
        viewModel.updateItemUnitPrice(tempId, "100")
        item = viewModel.draftItems.value.single()
        assertEquals("3.", item.quantityStr)
        assertEquals(30000L, item.lineTotalPaisa)
    }

    @Test
    fun testEmptyTotalZeroUnitPriceAndInvalidPriceHandling() {
        viewModel.addItemFromCatalog(octaneProduct)
        val tempId = viewModel.draftItems.value.single().tempId

        // Empty total resets lineTotal to 0 and qty to 0 without crashing
        viewModel.updateItemTotalAmount(tempId, "")
        var item = viewModel.draftItems.value.single()
        assertEquals("", item.lineTotalBdtStr)
        assertEquals(0L, item.lineTotalPaisa)
        assertEquals("0", item.quantityStr)

        // Zero unit price while editing total must not divide by zero
        viewModel.updateItemUnitPrice(tempId, "0")
        viewModel.updateItemTotalAmount(tempId, "500")
        item = viewModel.draftItems.value.single()
        assertEquals(50000L, item.lineTotalPaisa)
        assertEquals("500", item.lineTotalBdtStr)
        assertEquals(0L, item.unitPricePaisa)

        // Entering valid price afterward recovers quantity seamlessly
        viewModel.updateItemUnitPrice(tempId, "125")
        item = viewModel.draftItems.value.single()
        assertEquals("4", item.quantityStr)
        assertEquals(50000L, item.lineTotalPaisa)

        // Invalid price input safely resolves to 0L without crash
        viewModel.updateItemUnitPrice(tempId, "abc")
        item = viewModel.draftItems.value.single()
        assertEquals("abc", item.unitPriceBdtStr)
        assertEquals(0L, item.unitPricePaisa)
        assertEquals(50000L, item.lineTotalPaisa)
    }

    @Test
    fun testNonLiquidProductIncludingLiterUnitRemainsUnchanged() {
        viewModel.addItemFromCatalog(mobilProductWithLiterUnit)
        val item = viewModel.draftItems.value.single()
        val tempId = item.tempId

        // Attempting updateItemTotalAmount on Product must be ignored
        viewModel.updateItemTotalAmount(tempId, "100")
        val afterAttempt = viewModel.draftItems.value.single()
        assertEquals(68000L, afterAttempt.lineTotalPaisa)
        assertEquals("1", afterAttempt.quantityStr)
        assertEquals(DraftCalculationDriver.QUANTITY, afterAttempt.calculationDriver)

        // Standard Qty * Price still works
        viewModel.updateItemQuantity(tempId, "2")
        assertEquals(136000L, viewModel.draftItems.value.single().lineTotalPaisa)
        viewModel.updateItemUnitPrice(tempId, "700")
        assertEquals(140000L, viewModel.draftItems.value.single().lineTotalPaisa)
    }

    @Test
    fun testCustomLiquidItemSupportsBidirectionalMath() {
        viewModel.addCustomItem("Custom Kerosene", "110", "1", "Liter", "Liquid")
        val custom = viewModel.draftItems.value.single()
        viewModel.updateItemTotalAmount(custom.tempId, "275")
        val updated = viewModel.draftItems.value.single()
        assertEquals(DraftCalculationDriver.TOTAL_AMOUNT, updated.calculationDriver)
        assertEquals("2.5", updated.quantityStr)
        assertEquals(27500L, updated.lineTotalPaisa)
    }

    @Test
    fun testMultipleItemsIndependentDriversAndCheckoutPersistence() = runBlocking {
        viewModel.addItemFromCatalog(octaneProduct) // Item 0: Amount-driven 100 @ 13
        viewModel.addItemFromCatalog(fuelProduct)   // Item 1: Quantity-driven 2 @ 105
        viewModel.addItemFromCatalog(mobilProductWithLiterUnit) // Item 2: Product

        val items = viewModel.draftItems.value
        assertEquals(3, items.size)

        viewModel.updateItemUnitPrice(items[0].tempId, "13")
        viewModel.updateItemTotalAmount(items[0].tempId, "100")

        viewModel.updateItemQuantity(items[1].tempId, "2")

        val updated = viewModel.draftItems.value
        assertEquals(DraftCalculationDriver.TOTAL_AMOUNT, updated[0].calculationDriver)
        assertEquals("7.69", updated[0].quantityStr)
        assertEquals(10000L, updated[0].lineTotalPaisa)

        assertEquals(DraftCalculationDriver.QUANTITY, updated[1].calculationDriver)
        assertEquals("2", updated[1].quantityStr)
        assertEquals(21000L, updated[1].lineTotalPaisa)

        assertEquals(68000L, updated[2].lineTotalPaisa)

        // Raw subtotal = 100.00 + 210.00 + 680.00 = 990.00 BDT (99000 Paisa)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(99000L, viewModel.rawSubtotalPaisa.value)
        assertEquals(990L, viewModel.roundedTotalBdt.value)

        val orderId = viewModel.draftOrderId.value
        viewModel.checkoutPaid(printDirectly = false)
        var tries = 0
        while (db.invoiceDao().getInvoiceById(orderId) == null && tries < 40) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            delay(20)
            tries++
        }
        val savedInv = db.invoiceDao().getInvoiceById(orderId)
        assertNotNull(savedInv)
        assertEquals(99000L, savedInv!!.rawTotalPaisa)
        assertEquals(990L, savedInv.roundedTotalBdt)

        val savedLines = db.invoiceDao().getLinesForOrder(orderId)
        assertEquals(3, savedLines.size)
        // Authoritative 10000 Paisa must be saved even though 1300 * 7.69 = 9997
        assertEquals("7.69", savedLines[0].quantityStr)
        assertEquals(1300L, savedLines[0].unitPricePaisa)
        assertEquals(10000L, savedLines[0].lineTotalPaisa)
    }
}
