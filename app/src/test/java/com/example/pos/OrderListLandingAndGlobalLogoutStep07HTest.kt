package com.example.pos

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
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
import com.example.pos.data.db.InvoiceEntity
import com.example.pos.data.db.InvoiceLineEntity
import com.example.pos.data.db.PosDatabase
import com.example.pos.data.db.ProductEntity
import com.example.pos.data.repository.HistorySortOption
import com.example.pos.data.repository.PosRepository
import com.example.pos.ui.components.TopStationHeader
import com.example.pos.ui.viewmodel.DateFilterMode
import com.example.pos.ui.viewmodel.PosViewModel
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
class OrderListLandingAndGlobalLogoutStep07HTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

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
        app = ApplicationProvider.getApplicationContext<Application>()
        context = app
        db = Room.inMemoryDatabaseBuilder(context, PosDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        runBlocking {
            db.companyHeaderDao().insertOrUpdate(
                CompanyHeaderEntity(
                    id = 1,
                    companyName = "মেসার্স রাকিব ফিলিং স্টেশন\nএন্ড\nএল পি জি অটোগ্যাস ফিলিং স্টেশন",
                    branchName = "Main Branch",
                    branchNumber = "01",
                    address = "Dhaka",
                    phoneNumber = "01700000000"
                )
            )
            db.appSettingsDao().insertOrUpdate(
                AppSettingsEntity(id = 1, language = "bn", themeMode = "LIGHT")
            )
            db.productDao().insertProducts(
                listOf(
                    ProductEntity(
                        itemId = "prod-octane",
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
        dataStoreFile = tempFolder.newFile("step07h_session.preferences_pb")
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

    private fun createRepo(sessionManager: SecureSessionManager = createSessionManager()): PosRepository {
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

    private fun seedOperatorAndSignOut(
        name: String = "Operator One",
        phone: String = "01711223344",
        password: String = "pass1234",
        language: String = "bn"
    ): Pair<PosRepository, SecureSessionManager> = runBlocking {
        db.appSettingsDao().insertOrUpdate(
            AppSettingsEntity(id = 1, language = language, themeMode = "LIGHT")
        )
        val sm = createSessionManager()
        val repo = createRepo(sm)
        assertTrue(repo.registerOperatorWithRecovery(name, phone, "Mohadevpur", password).isSuccess)
        repo.logout()
        sm.clearSession(clearRememberedHint = true)
        repo to sm
    }

    private fun seedSampleInvoice(orderId: String = "INV-7001") = runBlocking {
        db.invoiceDao().saveFullOrder(
            InvoiceEntity(
                orderId = orderId,
                dateTime = currentTimeMs,
                operatorId = "OP-1",
                operatorName = "Operator One",
                customerInfo = "01900112233",
                rawTotalPaisa = 26000L,
                roundedTotalBdt = 260L,
                isPaid = true,
                paidAmountBdt = 260L
            ),
            listOf(
                InvoiceLineEntity(
                    orderId = orderId,
                    itemId = "prod-octane",
                    itemName = "Octane",
                    itemType = "Liquid",
                    unit = "Liter",
                    quantityStr = "2",
                    unitPricePaisa = 13000L,
                    lineTotalPaisa = 26000L,
                    sortOrder = 1
                )
            )
        )
    }

    private fun loginThroughUi(phone: String = "01711223344", password: String = "pass1234") {
        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("login_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("login_phone_input").performScrollTo().performTextInput(phone)
        composeTestRule.onNodeWithTag("login_password_input").performScrollTo().performTextInput(password)
        composeTestRule.onNodeWithTag("submit_login_btn").performScrollTo().performClick()
        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("history_screen").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun testLandingFreshLoginRegistrationRememberedRestartAndCrossScreenReloginOpenOrderList() {
        // Part 1: Fresh install registration -> recovery acknowledgement -> lands on Order List (Tab 0)
        val repo = createRepo()
        val vmFresh = createViewModel(repo)
        waitForMainCondition { !vmFresh.isSessionChecking.value }

        composeTestRule.activity.setContent {
            MyApplicationTheme {
                PosMainApp(viewModel = vmFresh)
            }
        }

        // Splash screen must NOT show global logout button
        assertTrue(composeTestRule.onAllNodesWithTag("btn_global_logout").fetchSemanticsNodes().isEmpty())

        composeTestRule.mainClock.advanceTimeBy(1500)
        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("registration_screen").fetchSemanticsNodes().isNotEmpty()
        }
        // Registration screen must NOT show global logout button
        assertTrue(composeTestRule.onAllNodesWithTag("btn_global_logout").fetchSemanticsNodes().isEmpty())

        composeTestRule.onNodeWithTag("reg_name_input").performScrollTo().performTextInput("Owner Alpha")
        composeTestRule.onNodeWithTag("reg_phone_input").performScrollTo().performTextInput("01711223344")
        composeTestRule.onNodeWithTag("reg_password_input").performScrollTo().performTextInput("pass1234")
        composeTestRule.onNodeWithTag("reg_confirm_password_input").performScrollTo().performTextInput("pass1234")
        composeTestRule.onNodeWithTag("reg_submit_btn").performScrollTo().performClick()

        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("recovery_confirmation_screen").fetchSemanticsNodes().isNotEmpty()
        }
        // Recovery confirmation screen must NOT show global logout button
        assertTrue(composeTestRule.onAllNodesWithTag("btn_global_logout").fetchSemanticsNodes().isEmpty())

        composeTestRule.onNodeWithTag("recovery_ack_row").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("recovery_continue_btn").performScrollTo().performClick()

        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("history_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("history_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_transactions").assertIsDisplayed().assertIsSelected()
        composeTestRule.onNodeWithTag("nav_history_item").assertIsSelected()
        composeTestRule.onNodeWithText(Localization.get(Localization.Key.TRANSACTIONS_TAB_TITLE, "bn")).assertIsDisplayed()

        // Products, Settings, Analytics, and New Invoice must remain accessible
        composeTestRule.onNodeWithTag("tab_analytics").performClick()
        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("analytics_dashboard_view").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("nav_products_item").performClick()
        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("products_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("nav_settings_item").performClick()
        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("settings_screen").fetchSemanticsNodes().isNotEmpty()
        }

        // Logout while on SettingsScreen, then log back in -> must open Order List (Tab 0), NOT SettingsScreen
        composeTestRule.onNodeWithTag("btn_global_logout").performClick()
        composeTestRule.onNodeWithTag("logout_confirmation_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_confirm_logout").performClick()
        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("login_screen").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(composeTestRule.onAllNodesWithTag("btn_global_logout").fetchSemanticsNodes().isEmpty())

        // Forgot password screen must NOT show global logout button either
        composeTestRule.onNodeWithTag("login_forgot_password_btn").performScrollTo().performClick()
        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("recovery_screen").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(composeTestRule.onAllNodesWithTag("btn_global_logout").fetchSemanticsNodes().isEmpty())
        composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("login_screen").fetchSemanticsNodes().isNotEmpty()
        }

        // Log back in -> lands directly on Order List (HistoryScreen Tab 0)
        composeTestRule.onNodeWithTag("login_phone_input").performScrollTo().performTextInput("01711223344")
        composeTestRule.onNodeWithTag("login_password_input").performScrollTo().performTextInput("pass1234")
        composeTestRule.onNodeWithTag("login_remember_row").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("submit_login_btn").performScrollTo().performClick()
        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("history_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("tab_transactions").assertIsSelected()
        composeTestRule.onNodeWithTag("nav_history_item").assertIsSelected()

        // Open InvoiceScreen, then log out from InvoiceScreen and re-login -> lands on Order List with isNewInvoiceOpen = false
        composeTestRule.onNodeWithTag("fab_new_invoice").performClick()
        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("invoice_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("btn_global_logout").performClick()
        composeTestRule.onNodeWithTag("btn_confirm_logout").performClick()
        loginThroughUi()
        composeTestRule.onNodeWithTag("history_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_transactions").assertIsSelected()

        // Part 2: Remembered-session cold start with fresh ViewModel & PosMainApp opens Order List (Tab 0)
        runBlocking {
            assertTrue(repo.login("01711223344", "pass1234", rememberMe = true).isSuccess)
        }
        val vmRestart = createViewModel(createRepo())
        waitForMainCondition { !vmRestart.isSessionChecking.value && vmRestart.activeOperator.value != null }
        composeTestRule.activity.setContent {
            MyApplicationTheme {
                PosMainApp(viewModel = vmRestart)
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1500)
        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("history_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("history_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_transactions").assertIsSelected()
        composeTestRule.onNodeWithTag("nav_history_item").assertIsSelected()
    }

    @Test
    fun testGlobalLogoutButtonPresenceTouchTargetAndDialogAcrossEveryAuthenticatedScreen() {
        seedOperatorAndSignOut(language = "bn")
        seedSampleInvoice("INV-7001")
        val vm = createViewModel()
        waitForMainCondition { !vm.isSessionChecking.value }

        composeTestRule.activity.setContent {
            MyApplicationTheme {
                PosMainApp(viewModel = vm)
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1500)
        loginThroughUi()

        val assertGlobalLogoutAccessible = {
            composeTestRule.onAllNodesWithTag("btn_global_logout").assertCountEquals(1)
            val node = composeTestRule.onNodeWithTag("btn_global_logout")
            node.assertIsDisplayed()
            node.assertWidthIsAtLeast(48.dp)
            node.assertHeightIsAtLeast(48.dp)
            val desc = node.fetchSemanticsNode().config[SemanticsProperties.ContentDescription].firstOrNull()
            assertEquals(Localization.get(Localization.Key.LOGOUT, "bn"), desc)
        }

        // 1. Order List (HistoryScreen Tab 0)
        assertGlobalLogoutAccessible()

        // 2. Analytics (HistoryScreen Tab 1)
        composeTestRule.onNodeWithTag("tab_analytics").performClick()
        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("analytics_dashboard_view").fetchSemanticsNodes().isNotEmpty()
        }
        assertGlobalLogoutAccessible()

        // 3. Products
        composeTestRule.onNodeWithTag("nav_products_item").performClick()
        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("products_screen").fetchSemanticsNodes().isNotEmpty()
        }
        assertGlobalLogoutAccessible()

        // 4. Settings (including Operator Profile & existing btn_settings_logout)
        composeTestRule.onNodeWithTag("nav_settings_item").performClick()
        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("settings_screen").fetchSemanticsNodes().isNotEmpty()
        }
        assertGlobalLogoutAccessible()

        composeTestRule.onNodeWithTag("settings_tab_1").performClick()
        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("btn_settings_logout").fetchSemanticsNodes().isNotEmpty()
        }
        // Existing Settings logout routes cleanly to the shared LogoutConfirmationDialog without duplicates
        composeTestRule.onNodeWithTag("btn_settings_logout").performScrollTo().performClick()
        composeTestRule.onAllNodesWithTag("logout_confirmation_dialog").assertCountEquals(1)
        composeTestRule.onNodeWithTag("btn_cancel_logout").performClick()
        composeTestRule.waitForIdle()
        assertNotNull(vm.activeOperator.value)

        // 5. Employee Activity History (Settings Tab 5)
        composeTestRule.onNodeWithTag("settings_tab_5").performClick()
        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("employee_activity_screen").fetchSemanticsNodes().isNotEmpty()
        }
        assertGlobalLogoutAccessible()

        // 6. InvoiceScreen -> cancel preserves active draft, customer info & session
        composeTestRule.onNodeWithTag("nav_history_item").performClick()
        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("history_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("fab_new_invoice").performClick()
        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("invoice_screen").fetchSemanticsNodes().isNotEmpty()
        }
        assertGlobalLogoutAccessible()

        val product = vm.allActiveProducts.value.first()
        vm.addItemFromCatalog(product)
        vm.setCustomerInfo("01811223344")
        composeTestRule.waitForIdle()
        assertEquals(1, vm.draftItems.value.size)

        // Tap global logout -> Cancel keeps InvoiceScreen, draft, customer info & session intact
        composeTestRule.onNodeWithTag("btn_global_logout").performClick()
        composeTestRule.onNodeWithTag("logout_confirmation_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_cancel_logout").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("invoice_screen").assertIsDisplayed()
        assertNotNull(vm.activeOperator.value)
        assertEquals(1, vm.draftItems.value.size)
        assertEquals("01811223344", vm.customerInfo.value)
    }

    @Test
    fun testGlobalLogoutConfirmClearsSessionResetsTransientFiltersBlocksBackAndPersistsOnRestart() {
        val (repo, sm) = seedOperatorAndSignOut(language = "en")
        seedSampleInvoice("INV-9001")
        val vm = createViewModel(repo)
        waitForMainCondition { !vm.isSessionChecking.value }

        composeTestRule.activity.setContent {
            MyApplicationTheme {
                PosMainApp(viewModel = vm)
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1500)
        loginThroughUi()

        // Mutate transient History filters & expand an invoice, verify Cancel preserves them
        vm.setHistoryQuery("INV-9001")
        vm.setDateFilter(DateFilterMode.TODAY)
        vm.setHistorySortOption(HistorySortOption.AMOUNT_ASC)
        vm.toggleOrderExpansion("INV-9001")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("btn_global_logout").performClick()
        composeTestRule.onNodeWithTag("logout_confirmation_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_cancel_logout").performClick()
        composeTestRule.waitForIdle()
        assertEquals("INV-9001", vm.historyQuery.value)
        assertEquals(DateFilterMode.TODAY, vm.dateFilter.value)
        assertEquals("INV-9001", vm.expandedOrderId.value)

        // Confirm global logout -> clears session, clears filters, removes protected Scaffold, returns to Login
        composeTestRule.onNodeWithTag("btn_global_logout").performClick()
        composeTestRule.onNodeWithTag("btn_confirm_logout").performClick()
        waitForMainCondition {
            vm.activeOperator.value == null &&
                composeTestRule.onAllNodesWithTag("login_screen").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("login_screen").assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithTag("pos_main_scaffold").fetchSemanticsNodes().isEmpty())
        assertTrue(composeTestRule.onAllNodesWithTag("btn_global_logout").fetchSemanticsNodes().isEmpty())
        assertEquals("", vm.historyQuery.value)
        assertEquals(DateFilterMode.ALL, vm.dateFilter.value)
        assertEquals(HistorySortOption.DATE_DESC, vm.historySortOption.value)
        assertNull(vm.expandedOrderId.value)
        assertTrue(vm.expandedLines.value.isEmpty())
        assertEquals(1, runBlocking { db.invoiceDao().getTotalInvoiceCount() })

        // Verify session token in SecureSessionManager is null
        runBlocking {
            assertNull(sm.readValidSession())
        }

        // System Back on Login must NOT navigate back into protected screens
        composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        composeTestRule.waitForIdle()
        assertNull(vm.activeOperator.value)
        assertTrue(composeTestRule.onAllNodesWithTag("pos_main_scaffold").fetchSemanticsNodes().isEmpty())

        // Simulated app restart after logout must remain logged out on Login screen
        val vmRestart = createViewModel(createRepo(sm))
        waitForMainCondition { !vmRestart.isSessionChecking.value }
        assertNull(vmRestart.activeOperator.value)
        composeTestRule.activity.setContent {
            MyApplicationTheme {
                PosMainApp(viewModel = vmRestart)
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1500)
        waitForMainCondition {
            composeTestRule.onAllNodesWithTag("login_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("login_screen").assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithTag("btn_global_logout").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun testTopStationHeaderLogoutButtonFitsCleanlyWithoutOverlapOnNarrow320dpAnd360dpWidths() {
        composeTestRule.activity.setContent {
            MyApplicationTheme {
                Box(modifier = Modifier.width(320.dp)) {
                    TopStationHeader(
                        company = CompanyHeaderEntity(
                            id = 1,
                            companyName = "মেসার্স রাকিব ফিলিং স্টেশন\nএন্ড\nএল পি জি অটোগ্যাস ফিলিং স্টেশন",
                            branchName = "প্রধান",
                            branchNumber = "01",
                            address = "ঢাকা",
                            phoneNumber = "01700000000"
                        ),
                        operator = null,
                        currentLanguage = "bn",
                        onLogoutClick = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        val logoutBtn320 = composeTestRule.onNodeWithTag("btn_global_logout")
        logoutBtn320.assertIsDisplayed()
        logoutBtn320.assertWidthIsAtLeast(48.dp)
        logoutBtn320.assertHeightIsAtLeast(48.dp)
        val btnBounds = logoutBtn320.getUnclippedBoundsInRoot()
        val titleBounds = composeTestRule.onNodeWithText("মেসার্স রাকিব ফিলিং স্টেশন").getUnclippedBoundsInRoot()
        assertTrue(
            "Centered title (${titleBounds.right}) must not overlap top-right logout button (${btnBounds.left}) on 320dp",
            titleBounds.right <= btnBounds.left
        )
        assertTrue(
            "Logout button (${btnBounds.right}) must fit within 320dp container",
            btnBounds.right <= 320.dp
        )

        // Verify English contentDescription localization too
        val enDesc = Localization.get(Localization.Key.LOGOUT, "en")
        assertFalse(enDesc.any { it in '\u0980'..'\u09FF' })
        assertEquals("Logout", enDesc)
        assertEquals("লগআউট", Localization.get(Localization.Key.LOGOUT, "bn"))
    }
}
