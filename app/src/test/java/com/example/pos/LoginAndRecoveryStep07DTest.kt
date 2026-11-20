package com.example.pos

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.PosMainApp
import com.example.pos.core.security.InMemoryAesGcmSessionCipher
import com.example.pos.core.security.PasswordSecurity
import com.example.pos.core.security.SecureSessionManager
import com.example.pos.core.security.SessionCipher
import com.example.pos.data.Localization
import com.example.pos.data.db.AppSettingsEntity
import com.example.pos.data.db.CompanyHeaderEntity
import com.example.pos.data.db.CustomerEntity
import com.example.pos.data.db.InvoiceEntity
import com.example.pos.data.db.InvoiceLineEntity
import com.example.pos.data.db.OperatorEntity
import com.example.pos.data.db.PosDatabase
import com.example.pos.data.db.ProductEntity
import com.example.pos.data.repository.PosRepository
import com.example.pos.ui.screens.AuthScreen
import com.example.pos.ui.viewmodel.PosViewModel
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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
import java.security.GeneralSecurityException

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7, sdk = [36])
class LoginAndRecoveryStep07DTest {

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
    private var currentTimeMs = 1_750_000_000_000L

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
        dataStoreFile = tempFolder.newFile("step07d_session.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) { dataStoreFile }
        cipher = InMemoryAesGcmSessionCipher()
        currentTimeMs = 1_750_000_000_000L
    }

    private val createdViewModels = mutableListOf<PosViewModel>()

    @After
    fun teardown() {
        createdViewModels.forEach { it.cancelScopeForTest() }
        createdViewModels.clear()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        db.close()
        dataStoreScope.cancel()
    }

    private fun createViewModel(repo: PosRepository = createRepo()): PosViewModel {
        return PosViewModel.createForTest(app, repo).also { createdViewModels.add(it) }
    }

    private fun createSessionManager(
        ds: DataStore<Preferences> = dataStore,
        sessionCipher: SessionCipher = cipher
    ): SecureSessionManager {
        return SecureSessionManager.createForTest(
            dataStore = ds,
            cipher = sessionCipher,
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

    private fun registerExistingOperatorAndSignOut(
        name: String = "Salman Operator",
        phone: String = "01711223344",
        password: String = "pass1234",
        clearRememberedHint: Boolean = true
    ): Pair<PosRepository, String> = runBlocking {
        val sessionManager = createSessionManager()
        val repo = createRepo(sessionManager)
        val res = repo.registerOperatorWithRecovery(name, phone, "Bogura", password)
        assertTrue(res.isSuccess)
        val code = res.getOrThrow().recoveryCode
        repo.logout()
        if (clearRememberedHint) {
            sessionManager.clearSession(clearRememberedHint = true)
        }
        repo to code
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
    fun testLoginScreenRendersWhenOperatorsExistWithoutSessionAndDisallowsUnauthenticatedRegistration() {
        registerExistingOperatorAndSignOut()
        val vm = createViewModel()
        waitForMainCondition { !vm.isSessionChecking.value }

        composeTestRule.setContent {
            MyApplicationTheme {
                PosMainApp(viewModel = vm)
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1500)
        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("login_screen").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("auth_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("login_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("login_phone_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("login_password_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("login_forgot_password_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("submit_login_btn").assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithTag("registration_screen").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun testLoginAndRecoveryLocalizationEnglishHasNoBengaliFallbackAndBengaliRenders() {
        val step07DKeys = listOf(
            Localization.Key.LOGIN_WELCOME_TITLE,
            Localization.Key.LOGIN_WELCOME_SUBTITLE,
            Localization.Key.LOGIN_MOBILE_HINT,
            Localization.Key.LOGIN_PASSWORD_HINT,
            Localization.Key.LOGIN_FORGOT_PASSWORD,
            Localization.Key.LOGIN_SIGNING_IN,
            Localization.Key.LOGIN_ERR_INVALID_CREDENTIALS,
            Localization.Key.LOGIN_ERR_GENERIC,
            Localization.Key.LOCKOUT_TITLE,
            Localization.Key.LOCKOUT_DESC,
            Localization.Key.LOCKOUT_RETRY_IN,
            Localization.Key.RECOVERY_SCREEN_TITLE,
            Localization.Key.RECOVERY_SCREEN_SUBTITLE,
            Localization.Key.RECOVERY_REGISTERED_PHONE,
            Localization.Key.RECOVERY_CODE_INPUT_LABEL,
            Localization.Key.RECOVERY_CODE_INPUT_HINT,
            Localization.Key.RECOVERY_ERR_CODE_REQUIRED,
            Localization.Key.RECOVERY_ERR_INVALID_OR_UNAVAILABLE,
            Localization.Key.BTN_VERIFY_RECOVERY_CODE,
            Localization.Key.RECOVERY_VERIFYING,
            Localization.Key.RECOVERY_VERIFIED_BADGE,
            Localization.Key.RECOVERY_STEP_NEW_PASSWORD_TITLE,
            Localization.Key.RECOVERY_STEP_NEW_PASSWORD_SUBTITLE,
            Localization.Key.RECOVERY_NEW_PASSWORD,
            Localization.Key.RECOVERY_CONFIRM_NEW_PASSWORD,
            Localization.Key.BTN_UPDATE_PASSWORD,
            Localization.Key.RECOVERY_UPDATING_PASSWORD,
            Localization.Key.RECOVERY_PASSWORD_UPDATED_SUCCESS,
            Localization.Key.BTN_RETURN_TO_LOGIN
        )
        step07DKeys.forEach { key ->
            val enText = Localization.get(key, "en")
            assertFalse(
                "English localization for $key must not fall back to Bengali: $enText",
                enText.any { it in '\u0980'..'\u09FF' }
            )
            assertTrue("English localization for $key must not be blank", enText.isNotBlank())
            val bnText = Localization.get(key, "bn")
            assertTrue("Bengali localization for $key must not be blank", bnText.isNotBlank())
        }

        registerExistingOperatorAndSignOut()
        val vmBn = createViewModel()
        waitForMainCondition { !vmBn.isSessionChecking.value }

        composeTestRule.setContent {
            MyApplicationTheme {
                AuthScreen(viewModel = vmBn, language = "bn", onLoginSuccess = {})
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(Localization.get(Localization.Key.LOGIN_WELCOME_TITLE, "bn"))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(Localization.get(Localization.Key.LOGIN_FORGOT_PASSWORD, "bn"))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun testLoginValidationAndPasswordVisibilityToggle() {
        registerExistingOperatorAndSignOut()
        val vm = createViewModel()
        waitForMainCondition { !vm.isSessionChecking.value }

        var loginSuccessCount = 0
        composeTestRule.setContent {
            MyApplicationTheme {
                AuthScreen(viewModel = vm, language = "en", onLoginSuccess = { loginSuccessCount++ })
            }
        }
        composeTestRule.waitForIdle()

        // 1. Empty submit -> empty mobile & empty password errors
        composeTestRule.onNodeWithTag("submit_login_btn").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("login_phone_input").performScrollTo()
        composeTestRule.onNodeWithTag("login_phone_error", useUnmergedTree = true)
            .assertTextContains(Localization.get(Localization.Key.REG_ERR_PHONE_REQUIRED, "en"))
        composeTestRule.onNodeWithTag("login_password_input").performScrollTo()
        composeTestRule.onNodeWithTag("login_password_error", useUnmergedTree = true)
            .assertTextContains(Localization.get(Localization.Key.REG_ERR_PASSWORD_REQUIRED, "en"))

        // 2. Invalid mobile format -> REG_ERR_PHONE_INVALID
        composeTestRule.onNodeWithTag("login_phone_input").performScrollTo().performTextInput("12")
        composeTestRule.onNodeWithTag("login_password_input").performScrollTo().performTextInput("pass1234")
        composeTestRule.onNodeWithTag("login_password_toggle", useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("submit_login_btn").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("login_phone_input").performScrollTo()
        composeTestRule.onNodeWithTag("login_phone_error", useUnmergedTree = true)
            .assertTextContains(Localization.get(Localization.Key.REG_ERR_PHONE_INVALID, "en"))
        assertEquals(0, loginSuccessCount)
    }

    @Test
    fun testLoginWithRememberMeEnabledRestoresAcrossRestartAndNavigatesToProductList() {
        registerExistingOperatorAndSignOut()
        val vm1 = createViewModel()
        waitForMainCondition { !vm1.isSessionChecking.value }

        composeTestRule.setContent {
            MyApplicationTheme {
                PosMainApp(viewModel = vm1)
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1500)
        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("login_screen").fetchSemanticsNodes().isNotEmpty()
        }

        // Default credentials must NOT be pre-filled
        composeTestRule.onNodeWithTag("login_remember_checkbox", useUnmergedTree = true).assertIsOff()
        composeTestRule.onNodeWithTag("login_phone_input").performScrollTo().performTextInput("01711-223 344")
        composeTestRule.onNodeWithTag("login_password_input").performScrollTo().performTextInput("pass1234")
        composeTestRule.onNodeWithTag("login_remember_row").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("login_remember_checkbox", useUnmergedTree = true).assertIsOn()

        composeTestRule.onNodeWithTag("submit_login_btn").performScrollTo().performClick()
        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("history_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("history_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_transactions").assertIsDisplayed()
        assertNotNull(vm1.activeOperator.value)
        assertTrue(vm1.activeOperator.value!!.rememberMe)

        // Verify valid 30-day session restores cleanly in fresh ViewModel after restart
        val vmRestart = createViewModel()
        waitForMainCondition { !vmRestart.isSessionChecking.value }
        assertNotNull(vmRestart.activeOperator.value)
        assertEquals("01711223344", vmRestart.activeOperator.value!!.operatorPhone)
    }

    @Test
    fun testLoginWithRememberMeDisabledEntersPosThenClearsOnRestart() {
        registerExistingOperatorAndSignOut()
        val vm1 = createViewModel()
        waitForMainCondition { !vm1.isSessionChecking.value }

        composeTestRule.setContent {
            MyApplicationTheme {
                PosMainApp(viewModel = vm1)
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1500)
        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("login_screen").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("login_phone_input").performScrollTo().performTextInput("01711223344")
        composeTestRule.onNodeWithTag("login_password_input").performScrollTo().performTextInput("pass1234")
        composeTestRule.onNodeWithTag("login_remember_checkbox", useUnmergedTree = true).assertIsOff()
        composeTestRule.onNodeWithTag("submit_login_btn").performScrollTo().performClick()

        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("history_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("history_screen").assertIsDisplayed()
        assertNotNull(vm1.activeOperator.value)
        assertFalse(vm1.activeOperator.value!!.rememberMe)

        // Next app launch without Remember Me returns to Login cleanly
        val vmRestart = createViewModel()
        waitForMainCondition { !vmRestart.isSessionChecking.value }
        assertNull(vmRestart.activeOperator.value)
    }

    @Test
    fun testInvalidLoginErrorSafetyAndDuplicateSubmissionPrevention() {
        registerExistingOperatorAndSignOut()
        val vm = createViewModel()
        waitForMainCondition { !vm.isSessionChecking.value }

        composeTestRule.setContent {
            MyApplicationTheme {
                AuthScreen(viewModel = vm, language = "en", onLoginSuccess = {})
            }
        }
        composeTestRule.waitForIdle()

        // Non-existent phone and wrong password must show identical generic error message
        composeTestRule.onNodeWithTag("login_phone_input").performScrollTo().performTextInput("01999999999")
        composeTestRule.onNodeWithTag("login_password_input").performScrollTo().performTextInput("wrong123")
        composeTestRule.onNodeWithTag("submit_login_btn").performScrollTo().performClick()

        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("login_error_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("login_error_banner")
            .assertTextContains(Localization.get(Localization.Key.LOGIN_ERR_INVALID_CREDENTIALS, "en"))

        // Duplicate submission guard in ViewModel: only first in-flight login executes
        var callbackCount = 0
        vm.login("01711223344", "pass1234", true) { _, _ -> callbackCount++ }
        vm.login("01711223344", "pass1234", true) { _, _ -> callbackCount++ }
        waitForMainCondition { !vm.isLoggingIn.value && vm.activeOperator.value != null }
        assertEquals(1, callbackCount)
    }

    @Test
    fun testForgotPasswordRecoveryFlowInvalidGenericErrorValidationAndPasswordResetToLogin() {
        val (_, recoveryCode) = registerExistingOperatorAndSignOut(
            phone = "01722334455",
            password = "oldPass123"
        )
        val vm = createViewModel()
        waitForMainCondition { !vm.isSessionChecking.value }

        var landedInPos = false
        composeTestRule.setContent {
            MyApplicationTheme {
                AuthScreen(viewModel = vm, language = "en", onLoginSuccess = { landedInPos = true })
            }
        }
        composeTestRule.waitForIdle()

        // Navigate to Forgot Password screen
        composeTestRule.onNodeWithTag("login_forgot_password_btn").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("recovery_screen").assertIsDisplayed()

        // Invalid recovery code for unregistered phone vs registered phone -> identical generic error
        composeTestRule.onNodeWithTag("recovery_phone_input").performScrollTo().performTextInput("01988887777")
        composeTestRule.onNodeWithTag("recovery_code_input").performScrollTo().performTextInput("BAD-CODE")
        composeTestRule.onNodeWithTag("recovery_verify_btn").performScrollTo().performClick()
        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("recovery_error_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("recovery_error_banner")
            .assertTextContains(Localization.get(Localization.Key.RECOVERY_ERR_INVALID_OR_UNAVAILABLE, "en"))

        // Correct phone + wrong code -> same generic message
        composeTestRule.onNodeWithTag("recovery_phone_input").performScrollTo().performTextClearance()
        composeTestRule.onNodeWithTag("recovery_phone_input").performTextInput("01722334455")
        composeTestRule.onNodeWithTag("recovery_verify_btn").performScrollTo().performClick()
        waitForMainCondition { !vm.isVerifyingRecovery.value }
        composeTestRule.onNodeWithTag("recovery_error_banner")
            .assertTextContains(Localization.get(Localization.Key.RECOVERY_ERR_INVALID_OR_UNAVAILABLE, "en"))

        // Valid phone & valid recovery code -> proceeds to New Password setup step
        composeTestRule.onNodeWithTag("recovery_code_input").performScrollTo().performTextClearance()
        composeTestRule.onNodeWithTag("recovery_code_input").performTextInput(recoveryCode)
        composeTestRule.onNodeWithTag("recovery_verify_btn").performScrollTo().performClick()
        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("recovery_new_password_input").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("recovery_verified_badge").assertIsDisplayed()

        // Short password check
        composeTestRule.onNodeWithTag("recovery_new_password_input").performScrollTo().performTextInput("12")
        composeTestRule.onNodeWithTag("recovery_confirm_password_input").performScrollTo().performTextInput("12")
        composeTestRule.onNodeWithTag("recovery_update_password_btn").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("recovery_new_password_input").performScrollTo()
        composeTestRule.onNodeWithTag("recovery_new_password_error", useUnmergedTree = true)
            .assertTextContains(Localization.get(Localization.Key.REG_ERR_PASSWORD_MIN, "en"))

        // Mismatch check
        composeTestRule.onNodeWithTag("recovery_new_password_input").performScrollTo().performTextClearance()
        composeTestRule.onNodeWithTag("recovery_new_password_input").performTextInput("newPass987")
        composeTestRule.onNodeWithTag("recovery_confirm_password_input").performScrollTo().performTextClearance()
        composeTestRule.onNodeWithTag("recovery_confirm_password_input").performTextInput("different987")
        composeTestRule.onNodeWithTag("recovery_update_password_btn").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("recovery_confirm_password_input").performScrollTo()
        composeTestRule.onNodeWithTag("recovery_confirm_password_error", useUnmergedTree = true)
            .assertTextContains(Localization.get(Localization.Key.REG_ERR_PASSWORD_MISMATCH, "en"))

        // Valid new password -> resets & returns to Login without bypassing authentication
        composeTestRule.onNodeWithTag("recovery_confirm_password_input").performScrollTo().performTextClearance()
        composeTestRule.onNodeWithTag("recovery_confirm_password_input").performTextInput("newPass987")
        composeTestRule.onNodeWithTag("recovery_update_password_btn").performScrollTo().performClick()

        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("login_screen").fetchSemanticsNodes().isNotEmpty()
        }
        assertFalse("Password reset must NEVER bypass authentication", landedInPos)
        assertNull(vm.activeOperator.value)
        composeTestRule.onNodeWithTag("login_success_banner")
            .assertIsDisplayed()
            .assertTextContains(Localization.get(Localization.Key.RECOVERY_PASSWORD_UPDATED_SUCCESS, "en"))

        // Old password must fail; new password must log in cleanly
        composeTestRule.onNodeWithTag("login_phone_input").performScrollTo().performTextInput("01722334455")
        composeTestRule.onNodeWithTag("login_password_input").performScrollTo().performTextInput("oldPass123")
        composeTestRule.onNodeWithTag("submit_login_btn").performScrollTo().performClick()
        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("login_error_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("login_password_input").performScrollTo().performTextClearance()
        composeTestRule.onNodeWithTag("login_password_input").performTextInput("newPass987")
        composeTestRule.onNodeWithTag("submit_login_btn").performScrollTo().performClick()
        waitForMainCondition { landedInPos }
        assertTrue(landedInPos)
        assertNotNull(vm.activeOperator.value)
    }

    @Test
    fun testAccountLockoutCountdownPersistenceAcrossRestartAndExpirationReEnablesLogin() {
        registerExistingOperatorAndSignOut(phone = "01733445566", password = "correctPin")
        val vm = createViewModel()
        waitForMainCondition { !vm.isSessionChecking.value }

        var loggedIn = false
        composeTestRule.setContent {
            MyApplicationTheme {
                AuthScreen(viewModel = vm, language = "en", onLoginSuccess = { loggedIn = true })
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("login_phone_input").performScrollTo().performTextInput("01733445566")

        // 5 failed attempts trigger 5-minute lockout
        repeat(5) { attempt ->
            composeTestRule.onNodeWithTag("login_password_input").performScrollTo().performTextClearance()
            composeTestRule.onNodeWithTag("login_password_input").performTextInput("wrong$attempt")
            composeTestRule.onNodeWithTag("submit_login_btn").performScrollTo().performClick()
            waitForMainCondition { !vm.isLoggingIn.value }
        }

        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("login_lockout_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("login_lockout_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("login_lockout_countdown", useUnmergedTree = true).assertTextContains("05:00")
        composeTestRule.onNodeWithTag("submit_login_btn").assertIsNotEnabled()

        // Verify live countdown ticks down per second
        repeat(3) {
            composeTestRule.mainClock.advanceTimeBy(1_000L)
            composeTestRule.waitForIdle()
        }
        composeTestRule.onNodeWithTag("login_lockout_countdown", useUnmergedTree = true).assertTextContains("04:57")
        composeTestRule.onNodeWithTag("submit_login_btn").assertIsNotEnabled()

        // Simulate app restart 2 minutes later: lockout must remain enforced from DB locked_until_millis
        currentTimeMs += 2L * 60L * 1000L
        val vmRestarted = createViewModel()
        waitForMainCondition { !vmRestarted.isSessionChecking.value }
        var restartLoggedIn = false
        vmRestarted.login("01733445566", "correctPin", true) { ok, _ -> restartLoggedIn = ok }
        waitForMainCondition { !vmRestarted.isLoggingIn.value }
        assertFalse("Valid password must stay blocked while DB lockout is active", restartLoggedIn)
        assertTrue(vmRestarted.loginLockoutSeconds.value in 175L..180L)

        // Advance time past remaining 3 minutes so 5-minute lockout expires cleanly
        currentTimeMs += 3L * 60L * 1000L + 1_000L
        vmRestarted.refreshLockoutStatus("01733445566")
        waitForMainCondition { vmRestarted.loginLockoutSeconds.value == 0L }

        var unlockedSuccess = false
        vmRestarted.login("01733445566", "correctPin", true) { ok, _ -> unlockedSuccess = ok }
        waitForMainCondition { !vmRestarted.isLoggingIn.value && vmRestarted.activeOperator.value != null }
        assertTrue(unlockedSuccess)
        assertFalse(loggedIn)
    }

    @Test
    fun testStartupRoutingValidExpiredAndTamperedSessions() = runBlocking {
        // Login with Remember Me = true to persist encrypted 30-day session
        val (repo1, _) = registerExistingOperatorAndSignOut(phone = "01744556677", password = "secret99")
        assertTrue(repo1.login("01744556677", "secret99", rememberMe = true).isSuccess)

        // 1. Valid remembered session skips Login and lands on Order List
        val vmValid = createViewModel()
        waitForMainCondition { !vmValid.isSessionChecking.value }
        composeTestRule.setContent {
            MyApplicationTheme {
                PosMainApp(viewModel = vmValid)
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1500)
        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("history_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("history_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_transactions").assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithTag("login_screen").fetchSemanticsNodes().isEmpty())

        // 2. Expired remembered session (>30 days) falls back to Login
        currentTimeMs += 31L * 24L * 60L * 60L * 1000L
        val vmExpired = createViewModel()
        waitForMainCondition { !vmExpired.isSessionChecking.value }
        assertNull("Expired 30-day session must not restore operator", vmExpired.activeOperator.value)

        // 3. Re-authenticate, then corrupt/fail decryption on startup -> fail closed to Login
        assertTrue(createRepo().login("01744556677", "secret99", rememberMe = true).isSuccess)
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("encrypted_session_payload")] = "tampered-corrupted-base64"
        }
        val failingCipher = object : SessionCipher {
            override fun encrypt(plainText: String): Result<String> = cipher.encrypt(plainText)
            override fun decrypt(encodedCipherText: String): String? = null
        }
        val vmTampered = createViewModel(createRepo(createSessionManager(sessionCipher = failingCipher)))
        waitForMainCondition { !vmTampered.isSessionChecking.value }
        assertNull("Tampered or unreadable session must fail closed", vmTampered.activeOperator.value)
    }

    @Test
    fun testLogoutPreservesBusinessDataAndAnalyticsNavigationRemainsAccessible() = runBlocking {
        // Seed customer and invoice alongside product and operator
        val sessionManager = createSessionManager()
        val repo = createRepo(sessionManager)
        assertTrue(repo.registerOperatorWithRecovery("Manager", "01755667788", "Dhaka", "pass55").isSuccess)
        db.customerDao().insertOrUpdateCustomer(
            CustomerEntity(
                customerInfo = "01811223344",
                customerName = "Karim",
                phoneNumber = "01811223344",
                address = "Dhaka",
                totalOrders = 1,
                totalSpentPaisa = 13000L
            )
        )
        db.invoiceDao().saveFullOrder(
            InvoiceEntity(
                orderId = "INV-7001",
                dateTime = currentTimeMs,
                operatorId = "OP-1",
                operatorName = "Manager",
                customerInfo = "01811223344",
                rawTotalPaisa = 13000L,
                roundedTotalBdt = 130L,
                isPaid = true,
                paidAmountBdt = 130L
            ),
            listOf(
                InvoiceLineEntity(
                    orderId = "INV-7001",
                    itemId = "prod-1",
                    itemName = "Octane",
                    itemType = "Liquid",
                    unit = "Liter",
                    quantityStr = "1",
                    unitPricePaisa = 13000L,
                    lineTotalPaisa = 13000L,
                    sortOrder = 1
                )
            )
        )
        repo.logout()
        sessionManager.clearSession(clearRememberedHint = true)

        val vm = createViewModel()
        waitForMainCondition { !vm.isSessionChecking.value }

        composeTestRule.setContent {
            MyApplicationTheme {
                PosMainApp(viewModel = vm)
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1500)
        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("login_screen").fetchSemanticsNodes().isNotEmpty()
        }

        // Login -> Order List (HistoryScreen Tab 0)
        composeTestRule.onNodeWithTag("login_phone_input").performScrollTo().performTextInput("01755667788")
        composeTestRule.onNodeWithTag("login_password_input").performScrollTo().performTextInput("pass55")
        composeTestRule.onNodeWithTag("submit_login_btn").performScrollTo().performClick()
        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("history_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("history_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_transactions").assertIsDisplayed()
        composeTestRule.onNodeWithTag("bottom_nav_bar").assertIsDisplayed()

        // Navigate to Graph Analytics
        composeTestRule.onNodeWithTag("tab_analytics").performClick()
        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("analytics_dashboard_view").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("analytics_dashboard_view").assertIsDisplayed()

        // Logout -> returns to Login without deleting products, invoices, customers, or operator
        vm.logout()
        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("login_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("login_screen").assertIsDisplayed()
        assertNull(vm.activeOperator.value)
        assertEquals(1, db.operatorDao().getOperatorCount())
        assertEquals(1, db.productDao().getProductCount())
        assertEquals(1, db.invoiceDao().getTotalInvoiceCount())
        assertEquals(1, db.customerDao().getCustomerCount())
    }

    @Test
    fun testFreshInstallRegistrationAndRecoveryAckRegression() {
        val vm = createViewModel()
        waitForMainCondition { !vm.isSessionChecking.value }

        composeTestRule.setContent {
            MyApplicationTheme {
                PosMainApp(viewModel = vm)
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1500)
        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("registration_screen").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("reg_name_input").performScrollTo().performTextInput("First Operator")
        composeTestRule.onNodeWithTag("reg_phone_input").performScrollTo().performTextInput("01766778899")
        composeTestRule.onNodeWithTag("reg_password_input").performScrollTo().performTextInput("pin4321")
        composeTestRule.onNodeWithTag("reg_confirm_password_input").performScrollTo().performTextInput("pin4321")
        composeTestRule.onNodeWithTag("reg_submit_btn").performScrollTo().performClick()

        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("recovery_confirmation_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("recovery_ack_row").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("recovery_continue_btn").performScrollTo().performClick()

        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("history_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("history_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_transactions").assertIsDisplayed()
        assertNotNull(vm.activeOperator.value)
    }

    @Test
    fun testLoginCreateNewAccountTriggerNavigatesToRegistrationAndBack() {
        assertEquals("Don't have an account?", Localization.get(Localization.Key.LOGIN_NO_ACCOUNT, "en"))
        assertEquals("Create New Account", Localization.get(Localization.Key.BTN_CREATE_NEW_ACCOUNT, "en"))
        assertEquals("অ্যাকাউন্ট নেই?", Localization.get(Localization.Key.LOGIN_NO_ACCOUNT, "bn"))
        assertEquals("নতুন অ্যাকাউন্ট তৈরি করুন", Localization.get(Localization.Key.BTN_CREATE_NEW_ACCOUNT, "bn"))

        registerExistingOperatorAndSignOut()
        val vm = createViewModel()
        waitForMainCondition { !vm.isSessionChecking.value }

        composeTestRule.setContent {
            MyApplicationTheme {
                PosMainApp(viewModel = vm)
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1500)
        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("login_screen").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("login_create_account_btn").performScrollTo().assertIsDisplayed().performClick()
        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("registration_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("registration_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("reg_back_to_login_btn").performScrollTo().assertIsDisplayed().performClick()

        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("login_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("login_screen").assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithTag("registration_screen").fetchSemanticsNodes().isEmpty())

        composeTestRule.onNodeWithTag("login_create_account_btn").performScrollTo().performClick()
        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("registration_screen").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("reg_name_input").performScrollTo().performTextInput("Second Operator")
        composeTestRule.onNodeWithTag("reg_phone_input").performScrollTo().performTextInput("01799887766")
        composeTestRule.onNodeWithTag("reg_password_input").performScrollTo().performTextInput("pin9876")
        composeTestRule.onNodeWithTag("reg_confirm_password_input").performScrollTo().performTextInput("pin9876")
        composeTestRule.onNodeWithTag("reg_submit_btn").performScrollTo().performClick()

        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("recovery_confirmation_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("recovery_ack_row").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("recovery_continue_btn").performScrollTo().performClick()

        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("history_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("history_screen").assertIsDisplayed()
        assertEquals("Second Operator", vm.activeOperator.value?.operatorName)
    }
}
