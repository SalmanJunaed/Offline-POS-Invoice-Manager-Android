package com.example.pos

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.PosMainApp
import com.example.pos.core.security.InMemoryAesGcmSessionCipher
import com.example.pos.core.security.SecureSessionManager
import com.example.pos.data.Localization
import com.example.pos.data.db.AppSettingsEntity
import com.example.pos.data.db.CompanyHeaderEntity
import com.example.pos.data.db.PosDatabase
import com.example.pos.data.db.ProductEntity
import com.example.pos.data.repository.PosRepository
import com.example.pos.ui.screens.AuthScreen
import com.example.pos.ui.screens.RecoveryCodeConfirmationScreen
import com.example.pos.ui.screens.RegistrationOnboardingView
import com.example.pos.ui.viewmodel.PosViewModel
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class RegistrationOnboardingStep07CTest {

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

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext<Application>()
        context = app
        db = Room.inMemoryDatabaseBuilder(context, PosDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        runBlocking {
            db.companyHeaderDao().insertOrUpdate(
                CompanyHeaderEntity(
                    id = 1,
                    companyName = "Test Fuel Station",
                    branchName = "Main Branch",
                    branchNumber = "01",
                    address = "Dhaka",
                    phoneNumber = "01700000000"
                )
            )
            db.appSettingsDao().insertOrUpdate(
                AppSettingsEntity(id = 1, language = "en", themeMode = "LIGHT")
            )
            db.productDao().insertProducts(
                listOf(
                    ProductEntity(
                        itemId = "prod-1",
                        itemName = "Octane",
                        nameBn = "অকটেন",
                        nameEn = "Octane",
                        itemType = "Liquid",
                        unit = "Liter",
                        defaultUnitPricePaisa = 13000L,
                        sortOrder = 1,
                        isActive = true
                    )
                )
            )
        }
        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStoreFile = tempFolder.newFile("step07c_session.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) { dataStoreFile }
        cipher = InMemoryAesGcmSessionCipher()
    }

    @After
    fun teardown() {
        db.close()
        dataStoreScope.cancel()
    }

    private fun createSessionManager(): SecureSessionManager {
        return SecureSessionManager.createForTest(
            dataStore = dataStore,
            cipher = cipher
        )
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
    fun testRegistrationValidationPasswordToggleAndMismatchErrors() {
        var submittedCount = 0

        composeTestRule.setContent {
            MyApplicationTheme {
                RegistrationOnboardingView(
                    language = "en",
                    logoUri = null,
                    isSubmitting = false,
                    serverErrorMessage = null,
                    showBackToLogin = false,
                    onBackToLogin = {},
                    onClearServerError = {},
                    onSubmitRegistration = { _, _, _, _ -> submittedCount++ }
                )
            }
        }

        // Empty submission shows required field errors
        composeTestRule.onNodeWithTag("reg_submit_btn").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("reg_name_input").performScrollTo()
        composeTestRule.onNodeWithTag("reg_name_error", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("reg_phone_input").performScrollTo()
        composeTestRule.onNodeWithTag("reg_phone_error", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("reg_password_input").performScrollTo()
        composeTestRule.onNodeWithTag("reg_password_error", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("reg_confirm_password_input").performScrollTo()
        composeTestRule.onNodeWithTag("reg_confirm_password_error", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(0, submittedCount)

        // Invalid length & mismatch checks
        composeTestRule.onNodeWithTag("reg_name_input").performScrollTo().performTextInput("A")
        composeTestRule.onNodeWithTag("reg_phone_input").performScrollTo().performTextInput("123")
        composeTestRule.onNodeWithTag("reg_password_input").performScrollTo().performTextInput("12")
        composeTestRule.onNodeWithTag("reg_confirm_password_input").performScrollTo().performTextInput("1234")

        // Password visibility toggles work safely
        composeTestRule.onNodeWithTag("reg_password_toggle", useUnmergedTree = true).performClick()
        composeTestRule.onNodeWithTag("reg_confirm_password_toggle", useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("reg_submit_btn").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("reg_name_input").performScrollTo()
        composeTestRule.onNodeWithTag("reg_name_error", useUnmergedTree = true)
            .assertTextContains(Localization.get(Localization.Key.REG_ERR_NAME_TOO_SHORT, "en"))
        composeTestRule.onNodeWithTag("reg_phone_input").performScrollTo()
        composeTestRule.onNodeWithTag("reg_phone_error", useUnmergedTree = true)
            .assertTextContains(Localization.get(Localization.Key.REG_ERR_PHONE_INVALID, "en"))
        composeTestRule.onNodeWithTag("reg_password_input").performScrollTo()
        composeTestRule.onNodeWithTag("reg_password_error", useUnmergedTree = true)
            .assertTextContains(Localization.get(Localization.Key.REG_ERR_PASSWORD_MIN, "en"))
        composeTestRule.onNodeWithTag("reg_confirm_password_input").performScrollTo()
        composeTestRule.onNodeWithTag("reg_confirm_password_error", useUnmergedTree = true)
            .assertTextContains(Localization.get(Localization.Key.REG_ERR_PASSWORD_MISMATCH, "en"))
        assertEquals(0, submittedCount)
    }

    @Test
    fun testLocalizationCoverageEnglishHasNoBengaliFallbackAndBengaliRenders() {
        val registrationKeys = listOf(
            Localization.Key.APP_TITLE,
            Localization.Key.APP_SUBTITLE_MANAGE,
            Localization.Key.OFFLINE_POS_BADGE,
            Localization.Key.REG_WELCOME_TITLE,
            Localization.Key.REG_WELCOME_SUBTITLE,
            Localization.Key.FULL_NAME,
            Localization.Key.PHONE_NUMBER,
            Localization.Key.REG_BUSINESS_ADDRESS,
            Localization.Key.PASSWORD,
            Localization.Key.CONFIRM_PASSWORD,
            Localization.Key.REG_PASSWORD_HINT,
            Localization.Key.BTN_CREATE_ACCOUNT,
            Localization.Key.REG_CREATING_ACCOUNT,
            Localization.Key.REG_BACK_TO_LOGIN,
            Localization.Key.REG_ERR_NAME_REQUIRED,
            Localization.Key.REG_ERR_PHONE_INVALID,
            Localization.Key.REG_ERR_PASSWORD_MIN,
            Localization.Key.REG_ERR_PASSWORD_MISMATCH,
            Localization.Key.REG_ERR_DUPLICATE_PHONE,
            Localization.Key.REG_SUCCESS_MSG,
            Localization.Key.RECOVERY_CODE_TITLE,
            Localization.Key.RECOVERY_CODE_WARNING,
            Localization.Key.BTN_COPY_RECOVERY_CODE,
            Localization.Key.RECOVERY_ACK_CHECKBOX,
            Localization.Key.BTN_CONTINUE_TO_POS
        )
        registrationKeys.forEach { key ->
            val enText = Localization.get(key, "en")
            assertFalse(
                "English localization for $key must not contain Bengali fallback characters: $enText",
                enText.any { it in '\u0980'..'\u09FF' }
            )
            assertTrue("English localization for $key must not be blank", enText.isNotBlank())
        }

        // Verify Bengali UI rendering cleanly
        composeTestRule.setContent {
            MyApplicationTheme {
                RegistrationOnboardingView(
                    language = "bn",
                    logoUri = null,
                    isSubmitting = false,
                    serverErrorMessage = null,
                    showBackToLogin = false,
                    onBackToLogin = {},
                    onClearServerError = {},
                    onSubmitRegistration = { _, _, _, _ -> }
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(Localization.get(Localization.Key.REG_WELCOME_TITLE, "bn"))
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("reg_submit_btn")
            .performScrollTo()
            .assertTextContains(Localization.get(Localization.Key.BTN_CREATE_ACCOUNT, "bn"))
    }

    @Test
    fun testLoadingStateBlocksDuplicateClicksAndDisplaysServerError() {
        var submitClicks = 0
        var isLoading by mutableStateOf(true)
        var serverErr by mutableStateOf<String?>("Duplicate mobile error")

        composeTestRule.setContent {
            MyApplicationTheme {
                RegistrationOnboardingView(
                    language = "en",
                    logoUri = null,
                    isSubmitting = isLoading,
                    serverErrorMessage = serverErr,
                    showBackToLogin = false,
                    onBackToLogin = {},
                    onClearServerError = { serverErr = null },
                    onSubmitRegistration = { _, _, _, _ -> submitClicks++ }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("reg_error_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("reg_submit_btn").performScrollTo()
            .assertIsNotEnabled()
            .performClick()
        composeTestRule.onNodeWithTag("reg_loading_indicator").assertIsDisplayed()
        assertEquals(0, submitClicks)
    }

    @Test
    fun testRecoveryCodeDisplayCopyAcknowledgeAndNoRegenerationOnRecomposition() {
        var lang by mutableStateOf("en")
        var continued = false
        val fixedCode = "ABCD-7890-WXYZ"

        composeTestRule.setContent {
            MyApplicationTheme {
                RecoveryCodeConfirmationScreen(
                    language = lang,
                    recoveryCode = fixedCode,
                    operatorName = "Salman Operator",
                    onContinueToPos = { continued = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("recovery_code_value").assertTextContains(fixedCode)
        composeTestRule.onNodeWithTag("recovery_warning_banner")
            .assertTextContains(Localization.get(Localization.Key.RECOVERY_CODE_WARNING, "en"))
        composeTestRule.onNodeWithTag("recovery_continue_btn").performScrollTo().assertIsNotEnabled()

        // Toggle language recomposition -> exact same recovery code stays displayed
        lang = "bn"
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("recovery_code_value").assertTextContains(fixedCode)
        composeTestRule.onNodeWithTag("recovery_warning_banner")
            .assertTextContains(Localization.get(Localization.Key.RECOVERY_CODE_WARNING, "bn"))

        // Copy recovery code to Android ClipboardManager
        composeTestRule.onNodeWithTag("recovery_copy_btn").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals(fixedCode, clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        composeTestRule.onNodeWithTag("recovery_copy_btn")
            .assertTextContains(Localization.get(Localization.Key.RECOVERY_CODE_COPIED, "bn"))
        composeTestRule.onNodeWithTag("recovery_copied_badge", useUnmergedTree = true).assertIsDisplayed()

        // Acknowledge warning -> Continue button enables -> click triggers navigation
        composeTestRule.onNodeWithTag("recovery_ack_checkbox").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("recovery_continue_btn").performScrollTo()
            .assertIsEnabled()
            .performClick()
        composeTestRule.waitForIdle()
        assertTrue(continued)
    }

    @Test
    fun testFreshInstallRoutingOpensRegistrationThenRecoveryThenProductListAndPreservesPosFeatures(): Unit = runBlocking {
        val repo = PosRepository(context, db, createSessionManager())
        val vm = PosViewModel.createForTest(app, repo)

        waitForMainCondition { !vm.isSessionChecking.value }
        assertFalse(vm.hasRegisteredOperators.value)
        assertNull(vm.activeOperator.value)

        composeTestRule.setContent {
            MyApplicationTheme {
                PosMainApp(viewModel = vm)
            }
        }

        // Advance past SplashScreen (1000ms) -> zero operators routes directly to Registration
        composeTestRule.mainClock.advanceTimeBy(1500)
        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("registration_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("registration_screen").assertIsDisplayed()
        assertTrue(
            "Login form must NOT be shown by default on fresh install",
            composeTestRule.onAllNodesWithTag("login_phone_input").fetchSemanticsNodes().isEmpty()
        )

        // Fill valid details (with dashes/spaces to verify normalization) & submit
        composeTestRule.onNodeWithTag("reg_name_input").performScrollTo().performTextInput("Rakib Station Owner")
        composeTestRule.onNodeWithTag("reg_phone_input").performScrollTo().performTextInput("01711-223 344")
        composeTestRule.onNodeWithTag("reg_address_input").performScrollTo().performTextInput("Mohadevpur Branch")
        composeTestRule.onNodeWithTag("reg_password_input").performScrollTo().performTextInput("secure123")
        composeTestRule.onNodeWithTag("reg_confirm_password_input").performScrollTo().performTextInput("secure123")
        composeTestRule.onNodeWithTag("reg_submit_btn").performScrollTo().performClick()

        // Must land on One-Time Recovery Code confirmation (NOT jump straight into POS)
        waitForMainCondition {
            vm.pendingRecoveryCode.value != null &&
                composeTestRule.onAllNodesWithTag("recovery_confirmation_screen").fetchSemanticsNodes().isNotEmpty()
        }
        val returnedCode = vm.pendingRecoveryCode.value
        assertNotNull(returnedCode)
        composeTestRule.onNodeWithTag("recovery_confirmation_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("recovery_code_value").assertTextContains(returnedCode!!)

        // DB should contain exactly 1 operator with normalized phone
        val createdOp = db.operatorDao().getOperatorByPhone("01711223344")
        assertNotNull("Phone must be normalized on persist", createdOp)
        assertEquals("Mohadevpur Branch", createdOp!!.operatorAddress)

        // Copy, Acknowledge, and Continue to POS
        composeTestRule.onNodeWithTag("recovery_copy_btn").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("recovery_ack_checkbox").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("recovery_continue_btn").performScrollTo().performClick()

        // Must clear temporary recovery state and land on Order List (HistoryScreen)
        waitForMainCondition {
            vm.pendingRecoveryCode.value == null &&
                composeTestRule.onAllNodesWithTag("history_screen").fetchSemanticsNodes().isNotEmpty()
        }
        assertNull("Temporary plaintext recovery code must be cleared after acknowledgement", vm.pendingRecoveryCode.value)
        composeTestRule.onNodeWithTag("history_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_transactions").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_analytics").assertIsDisplayed()

        // Verify ProductsScreen remains accessible from bottom nav
        composeTestRule.onNodeWithTag("nav_products_item").performClick()
        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("products_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("products_screen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Octane").assertIsDisplayed()

        // Verify History & Analytics remain accessible from bottom nav
        composeTestRule.onNodeWithTag("nav_history_item").performClick()
        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("history_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("history_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_analytics").assertIsDisplayed()
    }

    @Test
    fun testExistingOperatorsLoggedOutRoutesToLoginAndBlocksUnauthenticatedRegistration(): Unit = runBlocking {
        val repo = PosRepository(context, db, createSessionManager())
        val regRes = repo.registerOperatorWithRecovery("Existing Admin", "01744001122", "Dhaka", "pass456")
        assertTrue(regRes.isSuccess)
        repo.logout()

        val vm = PosViewModel.createForTest(app, repo)
        waitForMainCondition { !vm.isSessionChecking.value }
        assertTrue(vm.hasRegisteredOperators.value)
        assertNull(vm.activeOperator.value)

        composeTestRule.setContent {
            MyApplicationTheme {
                PosMainApp(viewModel = vm)
            }
        }

        composeTestRule.mainClock.advanceTimeBy(1500)
        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("auth_screen").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("login_phone_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("submit_login_btn").assertIsDisplayed()
        assertTrue(
            "Unauthenticated users must not be shown Registration when operators already exist",
            composeTestRule.onAllNodesWithTag("registration_screen").fetchSemanticsNodes().isEmpty()
        )
        assertTrue(
            "Unauthenticated Register tab must not exist when operators exist",
            composeTestRule.onAllNodesWithText(Localization.get(Localization.Key.TAB_REGISTER, "en")).fetchSemanticsNodes().isEmpty()
        )

        // Logging in navigates to Order List landing screen
        composeTestRule.onNodeWithTag("login_phone_input").performTextClearance()
        composeTestRule.onNodeWithTag("login_phone_input").performTextInput("01744001122")
        composeTestRule.onNodeWithTag("login_password_input").performTextClearance()
        composeTestRule.onNodeWithTag("login_password_input").performTextInput("pass456")
        composeTestRule.onNodeWithTag("submit_login_btn").performClick()

        waitForMainCondition {
            vm.activeOperator.value != null &&
                composeTestRule.onAllNodesWithTag("history_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("history_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_transactions").assertIsDisplayed()
    }

    @Test
    fun testValidRememberedSessionSkipsAuthAndLandsOnProductList(): Unit = runBlocking {
        val sm1 = createSessionManager()
        val repo1 = PosRepository(context, db, sm1)
        assertTrue(repo1.registerOperatorWithRecovery("Saved Operator", "01788990011", "Bogura", "pin987").isSuccess)

        // Simulate app restart with fresh repository & ViewModel sharing same DataStore + DB
        val repo2 = PosRepository(context, db, createSessionManager())
        val vm = PosViewModel.createForTest(app, repo2)
        waitForMainCondition { !vm.isSessionChecking.value }
        assertNotNull(vm.activeOperator.value)

        composeTestRule.setContent {
            MyApplicationTheme {
                PosMainApp(viewModel = vm)
            }
        }

        composeTestRule.mainClock.advanceTimeBy(1500)
        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("history_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("history_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_transactions").assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithTag("registration_screen").fetchSemanticsNodes().isEmpty())
        assertTrue(composeTestRule.onAllNodesWithTag("auth_screen").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun testDuplicatePhoneRegistrationShowsLocalizedErrorAndAuthorizedEntrySupportsBackToLogin(): Unit = runBlocking {
        val repo = PosRepository(context, db, createSessionManager())
        assertTrue(repo.registerOperatorWithRecovery("First Op", "01755667788", "Naogaon", "1234").isSuccess)
        val vm = PosViewModel.createForTest(app, repo)
        waitForMainCondition { !vm.isSessionChecking.value }

        // Test ViewModel duplicate phone error directly
        var dupSuccess = true
        var dupError: String? = null
        var dupDone = false
        vm.registerWithRecovery("Second Op", "01755-667 788", "Dhaka", "5678") { ok, _, err ->
            dupSuccess = ok
            dupError = err
            dupDone = true
        }
        waitForMainCondition { dupDone }
        assertFalse(dupSuccess)
        assertEquals(Localization.get(Localization.Key.REG_ERR_DUPLICATE_PHONE, "en"), dupError)
        assertEquals(1, db.operatorDao().getOperatorCount())

        // Verify authorized registration mode supports Back to Login when operators already exist
        var loginCalled = false
        composeTestRule.setContent {
            MyApplicationTheme {
                AuthScreen(
                    viewModel = vm,
                    language = "en",
                    allowRegistrationWhenOperatorsExist = true,
                    onLoginSuccess = { loginCalled = true }
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("registration_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("reg_back_to_login_btn").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("login_phone_input").assertIsDisplayed()
        assertFalse(loginCalled)
    }
}
