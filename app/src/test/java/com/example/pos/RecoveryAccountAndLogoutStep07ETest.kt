package com.example.pos

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.os.Looper
import androidx.compose.ui.semantics.SemanticsProperties
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
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.PosMainApp
import com.example.pos.core.security.InMemoryAesGcmSessionCipher
import com.example.pos.core.security.SecureSessionManager
import com.example.pos.data.Localization
import com.example.pos.data.db.AppSettingsEntity
import com.example.pos.data.db.CompanyHeaderEntity
import com.example.pos.data.db.CustomerEntity
import com.example.pos.data.db.InvoiceEntity
import com.example.pos.data.db.InvoiceLineEntity
import com.example.pos.data.db.PaymentRecordEntity
import com.example.pos.data.db.PosDatabase
import com.example.pos.data.db.ProductEntity
import com.example.pos.data.repository.PosRepository
import com.example.pos.ui.screens.SettingsScreen
import com.example.pos.ui.viewmodel.PosViewModel
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7, sdk = [36])
class RecoveryAccountAndLogoutStep07ETest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var app: Application
    private lateinit var context: Context
    private lateinit var db: PosDatabase
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var dataStoreFile: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var cipher: InMemoryAesGcmSessionCipher
    private var currentTimeMs = 1_760_000_000_000L

    private val createdViewModels = mutableListOf<PosViewModel>()

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        context = app
        db = Room.inMemoryDatabaseBuilder(context, PosDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        runBlocking {
            db.companyHeaderDao().insertOrUpdate(
                CompanyHeaderEntity(
                    id = 1,
                    companyName = "Rakib Filling Station",
                    branchName = "Main Branch",
                    branchNumber = "01",
                    address = "Bogura",
                    phoneNumber = "01700000000"
                )
            )
            db.appSettingsDao().insertOrUpdate(
                AppSettingsEntity(id = 1, language = "en", themeMode = "LIGHT")
            )
            db.productDao().insertProducts(
                listOf(
                    ProductEntity(
                        itemId = "prod-lpg",
                        itemName = "LPG Autogas",
                        nameBn = "এলপিজি অটোগ্যাস",
                        nameEn = "LPG Autogas",
                        itemType = "Liquid",
                        unit = "Liter",
                        defaultUnitPricePaisa = 6700L,
                        sortOrder = 1,
                        isActive = true
                    )
                )
            )
        }
        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStoreFile = tempFolder.newFile("step07e_session.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) { dataStoreFile }
        cipher = InMemoryAesGcmSessionCipher()
        currentTimeMs = 1_760_000_000_000L
    }

    @After
    fun teardown() {
        createdViewModels.forEach { it.cancelScopeForTest() }
        createdViewModels.clear()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        db.close()
        dataStoreScope.cancel()
    }

    private fun createSessionManager(): SecureSessionManager {
        return SecureSessionManager.createForTest(
            dataStore = dataStore,
            cipher = cipher,
            timeProvider = { currentTimeMs }
        )
    }

    private fun createRepo(
        sessionManager: SecureSessionManager = createSessionManager()
    ): PosRepository {
        return PosRepository(
            context = context,
            db = db,
            sessionManager = sessionManager,
            timeProvider = { currentTimeMs }
        )
    }

    private fun createViewModel(repo: PosRepository = createRepo()): PosViewModel {
        return PosViewModel.createForTest(app, repo).also { createdViewModels.add(it) }
    }

    private fun registerActiveOperator(
        name: String = "Salman Junaed",
        phone: String = "01711223344",
        address: String = "Main Branch, Bogura",
        password: String = "pass1234"
    ): Pair<PosRepository, String> = runBlocking {
        val repo = createRepo()
        val res = repo.registerOperatorWithRecovery(name, phone, address, password)
        assertTrue(res.isSuccess)
        repo to res.getOrThrow().recoveryCode
    }

    private fun waitForMainCondition(timeoutMs: Long = 5000L, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeTestRule.waitForIdle()
            if (condition()) return
            Thread.sleep(25)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()
    }

    @Test
    fun testStep07ELocalizationSupportsEnglishAndBengaliWithZeroFallback() {
        val keys = listOf(
            Localization.Key.TAB_USER_PROFILE,
            Localization.Key.BTN_SAVE,
            Localization.Key.CANCEL,
            Localization.Key.OPERATOR_PROFILE_SAVED_SUCCESS,
            Localization.Key.OPERATOR_PROFILE_UPDATE_FAILED,
            Localization.Key.SETTINGS_ACCOUNT_INFO_TITLE,
            Localization.Key.SETTINGS_ACCOUNT_STATUS_LABEL,
            Localization.Key.SETTINGS_ACCOUNT_STATUS_ACTIVE,
            Localization.Key.SETTINGS_OPERATOR_ID_LABEL,
            Localization.Key.SETTINGS_EDIT_PROFILE_TITLE,
            Localization.Key.SETTINGS_SHARED_DATA_NOTICE,
            Localization.Key.SETTINGS_RECOVERY_MGMT_TITLE,
            Localization.Key.SETTINGS_RECOVERY_MGMT_DESC,
            Localization.Key.BTN_MANAGE_RECOVERY_CODE,
            Localization.Key.BTN_REGENERATE_RECOVERY_CODE,
            Localization.Key.RECOVERY_MGMT_WHY_IMPORTANT,
            Localization.Key.RECOVERY_MGMT_CURRENT_OPERATOR,
            Localization.Key.RECOVERY_REGEN_CONFIRM_TITLE,
            Localization.Key.RECOVERY_REGEN_CONFIRM_MSG,
            Localization.Key.BTN_CONFIRM_REGENERATE,
            Localization.Key.RECOVERY_REGENERATING,
            Localization.Key.RECOVERY_REGEN_SUCCESS,
            Localization.Key.RECOVERY_REGEN_ACK_CHECKBOX,
            Localization.Key.BTN_DONE_CLEAR_CODE,
            Localization.Key.RECOVERY_ERR_REGEN_FAILED,
            Localization.Key.LOGOUT_CONFIRM_TITLE,
            Localization.Key.LOGOUT_CONFIRM_MSG,
            Localization.Key.BTN_CONFIRM_LOGOUT,
            Localization.Key.NOT_PROVIDED
        )

        keys.forEach { key ->
            val bn = Localization.get(key, "bn")
            val en = Localization.get(key, "en")
            assertTrue("Missing bn translation for $key", bn.isNotBlank() && bn != key.name)
            assertTrue("Missing en translation for $key", en.isNotBlank() && en != key.name)
            assertFalse(
                "English translation for $key leaked Bengali script: $en",
                en.any { it in '\u0980'..'\u09FF' }
            )
            assertNotEquals("English fell back to Bengali for $key", bn, en)
        }
    }

    @Test
    fun testSettingsAccountInfoDisplaysOperatorMetadataWithoutSensitiveSecretsAndAllowsProfileUpdate() {
        val (repo, _) = registerActiveOperator(
            name = "Salman Operator",
            phone = "01711223344",
            address = "Bogura Main Station"
        )
        val vm = createViewModel(repo)
        waitForMainCondition { vm.activeOperator.value != null }
        val activeOp = vm.activeOperator.value!!

        composeTestRule.setContent {
            MyApplicationTheme {
                SettingsScreen(viewModel = vm)
            }
        }
        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("settings_tab_1").fetchSemanticsNodes().isNotEmpty()
        }

        // Open Operator Profile tab
        composeTestRule.onNodeWithTag("settings_tab_1").performClick()
        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("settings_account_info_card").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("settings_account_info_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("account_info_operator_id").assertTextContains(activeOp.userId)
        composeTestRule.onNodeWithTag("account_info_status").assertTextContains("Active", substring = true)
        composeTestRule.onNodeWithTag("account_info_name").assertTextContains("Salman Operator")
        composeTestRule.onNodeWithTag("account_info_phone").assertTextContains("01711223344")
        composeTestRule.onNodeWithTag("account_info_address").assertTextContains("Bogura Main Station")
        composeTestRule.onNodeWithTag("account_shared_data_notice").assertIsDisplayed()

        // Ensure no hashes, salts, keys, or tokens appear anywhere in UI
        assertTrue(composeTestRule.onAllNodesWithText("pbkdf2_sha256", substring = true).fetchSemanticsNodes().isEmpty())
        assertTrue(composeTestRule.onAllNodesWithText(activeOp.passwordHash, substring = true).fetchSemanticsNodes().isEmpty())
        assertTrue(composeTestRule.onAllNodesWithText(activeOp.recoveryCodeHash, substring = true).fetchSemanticsNodes().isEmpty())

        // Update profile fields and verify persistence in Room + UI refresh
        composeTestRule.onNodeWithTag("profile_name_input").performScrollTo().performTextReplacement("Salman Updated")
        composeTestRule.onNodeWithTag("profile_address_input").performScrollTo().performTextReplacement("Rajshahi Hub")
        composeTestRule.onNodeWithTag("btn_save_profile").performScrollTo().performClick()

        waitForMainCondition {
            vm.activeOperator.value?.operatorName == "Salman Updated" &&
                vm.activeOperator.value?.operatorAddress == "Rajshahi Hub"
        }

        composeTestRule.onNodeWithTag("account_info_name").assertTextContains("Salman Updated")
        composeTestRule.onNodeWithTag("account_info_address").assertTextContains("Rajshahi Hub")
    }

    @Test
    fun testRecoveryCodeManagementRequiresConfirmationInvalidatesOldCodeCopiesAndClearsOnAcknowledge() {
        val (repo, oldCode) = registerActiveOperator(phone = "01755667788", password = "oldPassword1")
        val initialHash = runBlocking { db.operatorDao().getActiveOperator()!!.recoveryCodeHash }
        val vm = createViewModel(repo)
        waitForMainCondition { vm.activeOperator.value != null }

        composeTestRule.setContent {
            MyApplicationTheme {
                SettingsScreen(viewModel = vm)
            }
        }
        composeTestRule.onNodeWithTag("settings_tab_1").performClick()
        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("btn_manage_recovery_code").fetchSemanticsNodes().isNotEmpty()
        }

        // Open Recovery Code Management dialog
        composeTestRule.onNodeWithTag("btn_manage_recovery_code").performScrollTo().performClick()
        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("recovery_management_dialog").fetchSemanticsNodes().isNotEmpty()
        }

        // Must NOT auto-regenerate on open or recomposition
        assertNull(vm.managedRecoveryCode.value)
        assertEquals(initialHash, runBlocking { db.operatorDao().getActiveOperator()!!.recoveryCodeHash })
        composeTestRule.onNodeWithTag("recovery_mgmt_operator_card").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            Localization.get(Localization.Key.RECOVERY_MGMT_WHY_IMPORTANT, "en"),
            substring = true
        ).assertIsDisplayed()

        // Tap Regenerate -> confirmation dialog appears; canceling keeps old code active
        composeTestRule.onNodeWithTag("btn_regenerate_recovery_code").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("recovery_regen_confirm_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_cancel_regenerate_recovery").performClick()
        composeTestRule.waitForIdle()
        assertNull(vm.managedRecoveryCode.value)
        assertTrue(runBlocking { repo.verifyRecoveryCode("01755667788", oldCode).isSuccess })

        // Confirm regeneration -> new recovery code generated once & displayed
        composeTestRule.onNodeWithTag("btn_regenerate_recovery_code").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("btn_confirm_regenerate_recovery").performClick()

        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("managed_recovery_code_value").fetchSemanticsNodes().isNotEmpty()
        }

        val codeNode = composeTestRule.onNodeWithTag("managed_recovery_code_value").fetchSemanticsNode()
        val newCode = codeNode.config[SemanticsProperties.Text].joinToString("") { it.text }
        assertTrue(newCode.isNotBlank())
        assertNotEquals(oldCode, newCode)
        assertEquals(newCode, vm.managedRecoveryCode.value)

        // Old recovery code is immediately invalidated; new recovery code verifies & allows offline reset
        runBlocking {
            assertTrue(repo.verifyRecoveryCode("01755667788", oldCode).isFailure)
            assertTrue(repo.verifyRecoveryCode("01755667788", newCode).isSuccess)
        }

        // Verify recovery code is NEVER stored in plaintext in Room or DataStore
        runBlocking {
            val op = db.operatorDao().getActiveOperator()!!
            assertNotEquals(newCode, op.recoveryCodeHash)
            assertFalse(op.recoveryCodeHash.contains(newCode.replace("-", "")))
            val prefs = dataStore.data.first()
            val allPrefsValues = prefs.asMap().values.joinToString("|")
            assertFalse(allPrefsValues.contains(newCode))
            assertFalse(allPrefsValues.contains(newCode.replace("-", "")))
            assertNull(prefs[stringPreferencesKey("plaintext_recovery_code")])
        }

        // Copy button updates copied badge & populates clipboard
        composeTestRule.onNodeWithTag("btn_copy_managed_recovery_code").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("btn_copy_managed_recovery_code")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains(Localization.get(Localization.Key.RECOVERY_CODE_COPIED, "en"))
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals(newCode, clipboard.primaryClip?.getItemAt(0)?.text?.toString())

        // Done button must require explicit acknowledgement checkbox before closing
        composeTestRule.onNodeWithTag("btn_ack_close_recovery").performScrollTo().assertIsNotEnabled()
        composeTestRule.onNodeWithTag("managed_recovery_ack_row").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("btn_ack_close_recovery").assertIsEnabled().performClick()

        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("recovery_management_dialog").fetchSemanticsNodes().isEmpty()
        }

        // In-memory plaintext recovery code must be cleared after acknowledgement
        assertNull(vm.managedRecoveryCode.value)

        // End-to-end verification: newly regenerated recovery code resets password cleanly
        runBlocking {
            assertTrue(repo.resetPassword("01755667788", newCode, "newPass99").isSuccess)
            assertTrue(repo.login("01755667788", "newPass99", false).isSuccess)
        }
    }

    @Test
    fun testBengaliRecoveryManagementAndLogoutConfirmationRenderProperly() {
        runBlocking {
            db.appSettingsDao().updateLanguage("bn")
        }
        val (repo, _) = registerActiveOperator(name = "রহিম অপারেটর", phone = "01811223344")
        val vm = createViewModel(repo)
        waitForMainCondition { vm.activeOperator.value != null && vm.language.value == "bn" }

        composeTestRule.setContent {
            MyApplicationTheme {
                SettingsScreen(viewModel = vm)
            }
        }
        composeTestRule.onNodeWithTag("settings_tab_1").performClick()
        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("settings_account_info_card").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText(Localization.get(Localization.Key.SETTINGS_ACCOUNT_INFO_TITLE, "bn")).assertIsDisplayed()
        composeTestRule.onNodeWithText(Localization.get(Localization.Key.SETTINGS_ACCOUNT_STATUS_ACTIVE, "bn")).assertIsDisplayed()

        // Tap Logout -> verify Bengali confirmation dialog appears
        composeTestRule.onNodeWithTag("btn_settings_logout").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("logout_confirmation_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText(Localization.get(Localization.Key.LOGOUT_CONFIRM_TITLE, "bn")).assertIsDisplayed()
        composeTestRule.onNodeWithText(Localization.get(Localization.Key.BTN_CONFIRM_LOGOUT, "bn")).assertIsDisplayed()
    }

    @Test
    fun testDuplicateRecoveryRegenerationGuardBlocksOverlappingRequests() {
        val vm = createViewModel()
        val results = mutableListOf<Triple<Boolean, String?, String?>>()

        // Without active operator, returns localized error cleanly
        vm.regenerateRecoveryCode { ok, code, err ->
            results.add(Triple(ok, code, err))
        }
        assertEquals(1, results.size)
        assertFalse(results[0].first)
        assertNull(results[0].second)
        assertNotNull(results[0].third)

        // With active operator, rapid repeated calls are blocked while first regeneration runs
        registerActiveOperator(phone = "01799887766")
        waitForMainCondition { vm.activeOperator.value != null }

        val callbackResults = mutableListOf<String>()
        vm.regenerateRecoveryCode { ok, code, _ ->
            if (ok && code != null) callbackResults.add(code)
        }
        vm.regenerateRecoveryCode { ok, code, _ ->
            if (ok && code != null) callbackResults.add(code)
        }
        vm.regenerateRecoveryCode { ok, code, _ ->
            if (ok && code != null) callbackResults.add(code)
        }

        waitForMainCondition { callbackResults.isNotEmpty() && !vm.isRegeneratingRecoveryCode.value }
        assertEquals(1, callbackResults.size)
        assertEquals(callbackResults.first(), vm.managedRecoveryCode.value)
    }

    @Test
    fun testLogoutConfirmationCancelKeepsSessionConfirmLandsOnLoginAndPreservesAllBusinessData() {
        val (repo, _) = registerActiveOperator(name = "Operator One", phone = "01711112222")
        runBlocking {
            // Seed existing customer & invoice data to confirm non-destructive logout
            db.customerDao().insertOrUpdateCustomer(
                CustomerEntity(
                    customerInfo = "01900001111",
                    customerName = "Metro Transport",
                    phoneNumber = "01900001111",
                    totalOrders = 1,
                    totalSpentPaisa = 50000L
                )
            )
            db.invoiceDao().saveFullOrderAtomic(
                invoice = InvoiceEntity(
                    orderId = "INV-PRESERVE-1",
                    dateTime = currentTimeMs,
                    operatorId = db.operatorDao().getActiveOperator()!!.userId,
                    operatorName = "Operator One",
                    customerInfo = "01900001111",
                    rawTotalPaisa = 50000L,
                    roundedTotalBdt = 500L,
                    isPaid = true,
                    paidAmountBdt = 500L
                ),
                lines = listOf(
                    InvoiceLineEntity(
                        orderId = "INV-PRESERVE-1",
                        itemId = "prod-lpg",
                        itemName = "LPG Autogas",
                        itemType = "Liquid",
                        unit = "Liter",
                        quantityStr = "10",
                        unitPricePaisa = 5000L,
                        lineTotalPaisa = 50000L,
                        sortOrder = 1
                    )
                ),
                payment = PaymentRecordEntity(
                    orderId = "INV-PRESERVE-1",
                    paymentMethod = "CASH",
                    amountPaidPaisa = 50000L,
                    timestamp = currentTimeMs
                )
            )
        }

        val vm = createViewModel(repo)
        waitForMainCondition { vm.activeOperator.value != null && !vm.isSessionChecking.value }

        // Populate transient in-memory draft state to verify logout clears it
        vm.setCustomerInfo("01900001111")

        composeTestRule.setContent {
            MyApplicationTheme {
                PosMainApp(viewModel = vm)
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1500)
        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("nav_settings_item").fetchSemanticsNodes().isNotEmpty()
        }

        // Navigate to Settings -> Profile Tab
        composeTestRule.onNodeWithTag("nav_settings_item").performClick()
        composeTestRule.onNodeWithTag("settings_tab_1").performClick()
        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("btn_settings_logout").fetchSemanticsNodes().isNotEmpty()
        }

        // 1. Tap Logout -> Cancel keeps active session
        composeTestRule.onNodeWithTag("btn_settings_logout").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("logout_confirmation_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_cancel_logout").performClick()
        composeTestRule.waitForIdle()
        assertNotNull(vm.activeOperator.value)
        assertTrue(composeTestRule.onAllNodesWithTag("pos_main_scaffold").fetchSemanticsNodes().isNotEmpty())

        // 2. Tap Logout -> Confirm Logout clears session & navigates to Login (NOT Registration)
        composeTestRule.onNodeWithTag("btn_settings_logout").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("btn_confirm_logout").performClick()

        waitForMainCondition {
            vm.activeOperator.value == null &&
                composeTestRule.onAllNodesWithTag("login_screen").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("login_screen").assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithTag("registration_screen").fetchSemanticsNodes().isEmpty())
        assertEquals("", vm.customerInfo.value)
        assertNull(vm.managedRecoveryCode.value)

        // 3. Verify all business records, analytics base tables, and operator accounts are intact
        runBlocking {
            assertEquals(1, db.operatorDao().getOperatorCount())
            assertEquals(1, db.productDao().getProductCount())
            assertEquals(1, db.invoiceDao().getTotalInvoiceCount())
            assertEquals(1, db.customerDao().getCustomerCount())
            assertNotNull(db.invoiceDao().getInvoiceById("INV-PRESERVE-1"))
            assertEquals(1, db.invoiceDao().getLinesForOrder("INV-PRESERVE-1").size)
        }
    }

    @Test
    fun testMultiOperatorSharedHistoryAnalyticsAndHistoricalCreatorAttributionPreservation() {
        val repo = createRepo()

        // Operator A registers and checks out INV-OP-A
        val regA = runBlocking {
            repo.registerOperatorWithRecovery("Operator Alpha", "01710000001", "Gate 1", "alpha123")
        }
        assertTrue(regA.isSuccess)
        val opA = regA.getOrThrow().operator

        val vm = createViewModel(repo)
        waitForMainCondition { vm.activeOperator.value?.userId == opA.userId }

        val product = runBlocking { db.productDao().getAllActiveProductsFlow().first().first() }
        vm.addItemFromCatalog(product)
        vm.updateItemQuantity(vm.draftItems.value.first().tempId, "10")
        vm.setCustomerInfo("01911112222")
        vm.checkoutDue(depositBdt = 300L, printDirectly = false)
        waitForMainCondition {
            runBlocking { db.invoiceDao().getTotalInvoiceCount() == 1 }
        }
        val invA = runBlocking {
            db.invoiceDao().getInvoicesForExport(0L, Long.MAX_VALUE).first()
        }
        assertEquals(opA.userId, invA.operatorId)
        assertEquals("Operator Alpha", invA.operatorName)
        vm.closeReceiptPreview()

        // Operator A signs out; Operator B registers/logs in
        vm.logout()
        waitForMainCondition { vm.activeOperator.value == null }

        val regB = runBlocking {
            repo.registerOperatorWithRecovery("Operator Beta", "01710000002", "Gate 2", "beta1234")
        }
        assertTrue(regB.isSuccess)
        val opB = regB.getOrThrow().operator
        waitForMainCondition { vm.activeOperator.value?.userId == opB.userId }

        // Operator B creates INV-OP-B
        vm.addItemFromCatalog(product)
        vm.updateItemQuantity(vm.draftItems.value.first().tempId, "5")
        vm.checkoutPaid(printDirectly = false)
        waitForMainCondition {
            runBlocking { db.invoiceDao().getTotalInvoiceCount() == 2 }
        }
        vm.closeReceiptPreview()

        // Shared History & Analytics include BOTH Operator A's and Operator B's invoices
        vm.refreshHistory()
        waitForMainCondition {
            vm.historyInvoices.value.size == 2 && vm.aggregatedOrders.value == 2
        }
        val orderIds = vm.historyInvoices.value.map { it.orderId }.toSet()
        assertTrue(orderIds.contains(invA.orderId))
        assertEquals(2, vm.aggregatedOrders.value)
        assertTrue(vm.chartDataPoints.value.isNotEmpty() || vm.analyticsDataPoints.value.isNotEmpty())

        // Operator B edits metadata on Operator A's invoice & settles its full remaining due -> creator attribution MUST stay Operator A!
        val remainingDue = invA.roundedTotalBdt - invA.paidAmountBdt
        assertTrue(remainingDue > 0L)
        vm.updateInvoiceDetails(invA.orderId, "01911112222", "Edited by Beta")
        vm.settleInvoiceDue(invA.orderId, additionalPaymentBdt = remainingDue, openPreviewAfterSettlement = false)
        waitForMainCondition {
            runBlocking {
                val updated = db.invoiceDao().getInvoiceById(invA.orderId)
                updated != null && updated.footerNote == "Edited by Beta" && updated.isPaid
            }
        }

        val reloadedInvA = runBlocking { db.invoiceDao().getInvoiceById(invA.orderId)!! }
        assertEquals(opA.userId, reloadedInvA.operatorId)
        assertEquals("Operator Alpha", reloadedInvA.operatorName)
        assertEquals("Edited by Beta", reloadedInvA.footerNote)
        assertTrue(reloadedInvA.isPaid)
    }
}
