package com.example.pos

import android.app.Application
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.pos.core.error.PosResult
import com.example.pos.core.security.InMemoryAesGcmSessionCipher
import com.example.pos.core.security.SecureSessionManager
import com.example.pos.data.audit.AuditActionType
import com.example.pos.data.audit.AuditEntityType
import com.example.pos.data.db.AppSettingsEntity
import com.example.pos.data.db.AuditLogEntity
import com.example.pos.data.db.CompanyHeaderEntity
import com.example.pos.data.db.InvoiceEntity
import com.example.pos.data.db.InvoiceLineEntity
import com.example.pos.data.db.OperatorEntity
import com.example.pos.data.db.PosDatabase
import com.example.pos.data.db.ProductEntity
import com.example.pos.data.repository.PosRepository
import com.example.pos.ui.screens.HistoryScreen
import com.example.pos.ui.screens.InvoiceDetailsDialog
import com.example.pos.ui.screens.ProductEditDialog
import com.example.pos.ui.screens.SettingsScreen
import com.example.pos.ui.viewmodel.PosViewModel
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7, sdk = [36])
class EmployeeActivityUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var app: Application
    private lateinit var db: PosDatabase
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var repository: PosRepository
    private val createdViewModels = mutableListOf<PosViewModel>()
    private var nowMillis = 1_767_000_000_000L

    @Before
    fun setup() = runBlocking {
        app = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(app, PosDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        db.companyHeaderDao().insertOrUpdate(
            CompanyHeaderEntity(1, "Rakib Station", "Main", "01", "Dhaka", "01700000000")
        )
        db.appSettingsDao().insertOrUpdate(
            AppSettingsEntity(id = 1, language = "en", themeMode = "LIGHT", nextOrderSequence = 5001L)
        )
        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dsFile = tempFolder.newFile("activity_ui_${System.nanoTime()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) { dsFile }
        val sessionManager = SecureSessionManager.createForTest(
            dataStore = dataStore,
            cipher = InMemoryAesGcmSessionCipher()
        )
        repository = PosRepository(app, db, sessionManager) {
            nowMillis += 1000L
            nowMillis
        }
    }

    @After
    fun tearDown() {
        createdViewModels.forEach { it.cancelScopeForTest() }
        createdViewModels.clear()
        db.close()
        dataStoreScope.cancel()
    }

    private fun createVm(): PosViewModel {
        val vm = PosViewModel.createForTest(app, repository)
        createdViewModels.add(vm)
        var tries = 0
        while (vm.activeOperator.value == null && tries < 40) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeTestRule.waitForIdle()
            Thread.sleep(20)
            tries++
        }
        return vm
    }

    @Test
    fun testSettingsActivityTabFilteringPaginationAndDetailDialogInEnglishAndBangla(): Unit = runBlocking {
        val opA = repository.registerOperator("Karim Operator", "01711112222", "Dhaka", "1234").getOrThrow()

        // Create an invoice and edit it so we have Before/After diff JSON in audit_logs
        val inv = InvoiceEntity(
            orderId = "INV-UI-501",
            globalTransactionId = "GLB-UI-501",
            branchCode = "01",
            dateTime = System.currentTimeMillis(),
            operatorId = opA.userId,
            operatorName = opA.operatorName,
            customerInfo = "Old Customer",
            rawTotalPaisa = 30000L,
            roundedTotalBdt = 300L,
            isPaid = true,
            paidAmountBdt = 300L,
            footerNote = "Old Note"
        )
        assertTrue(
            repository.checkoutOrderAtomic(
                inv,
                listOf(InvoiceLineEntity(orderId = "INV-UI-501", itemId = "item-1", itemName = "Octane", itemType = "Liquid", unit = "L", quantityStr = "2", unitPricePaisa = 15000L, lineTotalPaisa = 30000L)),
                "Old Customer"
            ) is PosResult.Success
        )
        assertTrue(
            repository.updateInvoiceCustomerAndNoteAudited("INV-UI-501", "New Customer", "Updated Note") is PosResult.Success
        )

        val vm = createVm()
        composeTestRule.setContent {
            MyApplicationTheme {
                SettingsScreen(viewModel = vm)
            }
        }
        composeTestRule.waitForIdle()

        // Switch to Tab 5 (Employee Activity History)
        composeTestRule.onNodeWithTag("settings_tab_5").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("employee_activity_screen").assertIsDisplayed()

        // Filter by Invoice category -> find INV-UI-501 edit event and open detail dialog
        composeTestRule.onNodeWithTag("activity_cat_invoice").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("activity_search_input").performTextReplacement("INV-UI-501")
        composeTestRule.waitForIdle()

        // Wait for edit audit event card
        val logs = db.auditLogDao().getAuditLogsForEntity(AuditEntityType.INVOICE, "INV-UI-501")
        val editEvent = logs.first { it.actionType == AuditActionType.INVOICE_METADATA_UPDATED }
        composeTestRule.waitUntil(3000) {
            composeTestRule.onAllNodesWithTag("audit_log_card_${editEvent.eventId}").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("audit_log_card_${editEvent.eventId}").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("audit_detail_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("audit_detail_before_section").assertIsDisplayed()
        composeTestRule.onNodeWithTag("audit_detail_after_section").assertIsDisplayed()
        composeTestRule.onNodeWithText("Old Customer", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("New Customer", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("audit_detail_close_btn").performClick()
        composeTestRule.waitForIdle()

        // Search nonexistent term -> empty state -> Reset Filters
        composeTestRule.onNodeWithTag("activity_search_input").performTextReplacement("NO_SUCH_TERM_XYZ")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("activity_reset_filters_btn").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        // Seed 35 entries so pagination has 2 pages (30 per page)
        val baseTs = System.currentTimeMillis()
        for (i in 1..35) {
            db.auditLogDao().insertAuditLog(
                AuditLogEntity(
                    eventId = "AUD-PAGE-$i",
                    timestamp = baseTs - (i * 1000L),
                    operatorId = opA.userId,
                    operatorName = opA.operatorName,
                    actionType = AuditActionType.PRODUCT_UPDATED,
                    entityType = AuditEntityType.PRODUCT,
                    entityId = "ITEM-PAGE-$i",
                    summaryEn = "Updated product #$i",
                    summaryBn = "পণ্য #$i হালনাগাদ",
                    isSuccess = true
                )
            )
        }
        composeTestRule.waitUntil(3000) {
            composeTestRule.onAllNodesWithText("Page 1 / 2", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("activity_prev_btn").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("activity_next_btn").assertIsEnabled().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("activity_page_indicator").assertTextContains("Page 2 / 2", substring = true)

        // Toggle language to Bangla and verify localized labels render
        vm.setLanguage("bn")
        composeTestRule.waitUntil(3000) {
            composeTestRule.onAllNodesWithText("অপারেটর কার্যকলাপ", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("activity_page_indicator").assertTextContains("পৃষ্ঠা", substring = true)
    }

    @Test
    fun testInvoiceDetailsDialogDisplaysMultiOperatorAttribution(): Unit = runBlocking {
        val opA = repository.registerOperator("Alpha Creator", "01710000001", "Dhaka", "1234").getOrThrow()
        val invoiceTemplate = InvoiceEntity(
            orderId = "INV-ATTR-UI-1",
            globalTransactionId = "GLB-ATTR-UI-1",
            branchCode = "01",
            dateTime = System.currentTimeMillis(),
            operatorId = opA.userId,
            operatorName = opA.operatorName,
            customerInfo = "01900000001",
            rawTotalPaisa = 50000L,
            roundedTotalBdt = 500L,
            isPaid = false,
            paidAmountBdt = 200L
        )
        val lines = listOf(
            InvoiceLineEntity(
                orderId = "INV-ATTR-UI-1",
                itemId = "item-1",
                itemName = "Octane",
                itemType = "Liquid",
                unit = "Liter",
                quantityStr = "4",
                unitPricePaisa = 12500L,
                lineTotalPaisa = 50000L
            )
        )
        assertTrue(repository.checkoutOrderAtomic(invoiceTemplate, lines, "01900000001") is PosResult.Success)

        // Operator B settles due -> updatedBy becomes Beta Settler, payment 2 collectedBy Beta Settler
        repository.logout()
        val opB = repository.registerOperator("Beta Settler", "01720000002", "Bogura", "5678").getOrThrow()
        assertTrue(opB.userId.isNotBlank())
        assertTrue(repository.settleInvoiceDue("INV-ATTR-UI-1", 300L, "CASH") is PosResult.Success)

        val freshInvoice = repository.getInvoiceById("INV-ATTR-UI-1")!!
        val payments = repository.getPaymentsForOrderDirect("INV-ATTR-UI-1")

        composeTestRule.setContent {
            MyApplicationTheme {
                InvoiceDetailsDialog(
                    invoice = freshInvoice,
                    lines = lines,
                    payments = payments,
                    language = "en",
                    onDismiss = {},
                    onPreviewPrint = {},
                    onSharePdf = {},
                    onEdit = {},
                    onDelete = {},
                    onSettleDue = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("details_created_by").assertTextContains("Alpha Creator", substring = true)
        composeTestRule.onNodeWithTag("details_updated_by").assertTextContains("Beta Settler", substring = true)
        composeTestRule.onNodeWithTag("details_payment_history").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Collected by: Alpha Creator", substring = true).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Collected by: Beta Settler", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun testProductEditDialogDisplaysMultiOperatorAttribution(): Unit = runBlocking {
        repository.registerOperator("Alpha Creator", "01710000001", "Dhaka", "1234").getOrThrow()
        val savedProd = (repository.saveProductAudited(
            ProductEntity(
                itemId = "prod-ui-attr",
                itemName = "Diesel",
                nameBn = "ডিজেল",
                nameEn = "Diesel",
                itemType = "Liquid",
                unit = "Liter",
                defaultUnitPricePaisa = 10900L,
                sortOrder = 1
            )
        ) as PosResult.Success).data

        repository.logout()
        repository.registerOperator("Beta Settler", "01720000002", "Bogura", "5678").getOrThrow()
        val updatedProd = (repository.saveProductAudited(savedProd.copy(defaultUnitPricePaisa = 11200L)) as PosResult.Success).data

        composeTestRule.setContent {
            MyApplicationTheme {
                ProductEditDialog(
                    existing = updatedProd,
                    language = "en",
                    onDismiss = {},
                    onSave = { _, _, _, _, _, _, _, _ -> }
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("product_attribution_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("product_created_by").assertTextContains("Alpha Creator", substring = true)
        composeTestRule.onNodeWithTag("product_updated_by").assertTextContains("Beta Settler", substring = true)
    }
}
