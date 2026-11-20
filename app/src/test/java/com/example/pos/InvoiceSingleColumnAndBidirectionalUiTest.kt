package com.example.pos

import android.app.Application
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.pos.core.security.InMemoryAesGcmSessionCipher
import com.example.pos.core.security.SecureSessionManager
import com.example.pos.data.Localization
import com.example.pos.data.db.AppSettingsEntity
import com.example.pos.data.db.CompanyHeaderEntity
import com.example.pos.data.db.PosDatabase
import com.example.pos.data.db.ProductEntity
import com.example.pos.data.repository.PosRepository
import com.example.pos.ui.screens.InvoiceScreen
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
class InvoiceSingleColumnAndBidirectionalUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var app: Application
    private lateinit var db: PosDatabase
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var repository: PosRepository
    private lateinit var viewModel: PosViewModel

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
            AppSettingsEntity(id = 1, language = "en", themeMode = "LIGHT", nextOrderSequence = 30001L)
        )
        db.productDao().insertProducts(
            listOf(
                ProductEntity("item-1", "Octane", "অকটেন", "Octane", "Liquid", "Liter", 13000L, 1, true),
                ProductEntity("item-2", "Diesel", "ডিজেল", "Diesel", "Fuel", "Liter", 10500L, 2, true),
                ProductEntity("item-3", "Engine Oil", "ইঞ্জিন অয়েল", "Engine Oil", "Product", "Pcs", 50000L, 3, true)
            )
        )
        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dsFile = tempFolder.newFile("invoice_ui_${System.nanoTime()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) { dsFile }
        val sessionManager = SecureSessionManager.createForTest(dataStore, InMemoryAesGcmSessionCipher())
        repository = PosRepository(app, db, sessionManager)
        assertTrue(repository.registerOperator("Cashier", "01711112222", "Main", "1234").isSuccess)
        viewModel = PosViewModel.createForTest(app, repository)
        var tries = 0
        while ((viewModel.activeOperator.value == null || viewModel.allActiveProducts.value.size < 3 || viewModel.language.value != "en") && tries < 40) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            delay(15)
            tries++
        }
    }

    @After
    fun tearDown() = runBlocking {
        viewModel.cancelScopeForTest()
        db.close()
        dataStoreScope.cancel()
    }

    @Test
    fun testSingleColumnCatalogLayoutSearchFilterAndSelection() {
        composeTestRule.setContent {
            MyApplicationTheme {
                InvoiceScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("catalog_pane").assertIsDisplayed()
        composeTestRule.onNodeWithTag("catalog_item_item-1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("catalog_item_item-2").assertIsDisplayed()
        composeTestRule.onNodeWithTag("catalog_item_item-3").assertIsDisplayed()

        // Verify strictly single-column vertical stacking (same left edge, item-2 below item-1)
        val bounds1 = composeTestRule.onNodeWithTag("catalog_item_item-1").getUnclippedBoundsInRoot()
        val bounds2 = composeTestRule.onNodeWithTag("catalog_item_item-2").getUnclippedBoundsInRoot()
        assertEquals(bounds1.left, bounds2.left)
        assertTrue("Second product card must stack vertically below first", bounds2.top >= bounds1.bottom)
        assertTrue("Product card touch target height must be >= 48.dp", (bounds1.bottom - bounds1.top).value >= 47.9f)

        // Category filter chips work on single-column catalog
        val lang = viewModel.language.value
        composeTestRule.onNodeWithText(Localization.get(Localization.Key.FILTER_LIQUID, lang)).performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, composeTestRule.onAllNodesWithTag("catalog_item_item-1").fetchSemanticsNodes().size)
        assertEquals(1, composeTestRule.onAllNodesWithTag("catalog_item_item-2").fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithTag("catalog_item_item-3").fetchSemanticsNodes().size)

        composeTestRule.onNodeWithText(Localization.get(Localization.Key.FILTER_ALL, lang)).performClick()
        composeTestRule.waitForIdle()

        // Search works
        composeTestRule.onNodeWithTag("catalog_search_input").performTextReplacement("Diesel")
        composeTestRule.waitForIdle()
        assertEquals(1, composeTestRule.onAllNodesWithTag("catalog_item_item-2").fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithTag("catalog_item_item-1").fetchSemanticsNodes().size)

        // Clear search and select Octane
        composeTestRule.onNodeWithTag("catalog_search_input").performTextReplacement("")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("catalog_item_item-1").performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, viewModel.draftItems.value.size)
        assertEquals("Octane", viewModel.draftItems.value[0].itemName)
    }

    @Test
    fun testLiquidBidirectionalUiFieldsAndNonLiquidReadOnlyTotal() {
        composeTestRule.setContent {
            MyApplicationTheme {
                InvoiceScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        // Add Liquid product (item-1) and Product (item-3)
        composeTestRule.onNodeWithTag("catalog_item_item-1").performClick()
        composeTestRule.onNodeWithTag("catalog_item_item-3").performClick()
        composeTestRule.waitForIdle()

        // Liquid row must expose all 3 editable inputs; Product must NOT expose total_input_item-3
        composeTestRule.onNodeWithTag("qty_input_item-1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("price_input_item-1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("total_input_item-1").assertIsDisplayed()

        composeTestRule.onNodeWithTag("qty_input_item-3").assertIsDisplayed()
        composeTestRule.onNodeWithTag("price_input_item-3").assertIsDisplayed()
        assertEquals(0, composeTestRule.onAllNodesWithTag("total_input_item-3").fetchSemanticsNodes().size)

        // Quantity edit (12) -> Total becomes 1560
        composeTestRule.onNodeWithTag("qty_input_item-1").performTextReplacement("12")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("total_input_item-1").assertTextContains("1560")

        // Change Price to 13 -> Total becomes 156 (still QUANTITY mode)
        composeTestRule.onNodeWithTag("price_input_item-1").performTextReplacement("13")
        composeTestRule.waitForIdle()

        // Total edit (100) -> Quantity updates to 7.69 and total remains 100
        composeTestRule.onNodeWithTag("total_input_item-1").performTextReplacement("100")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("qty_input_item-1").assertTextContains("7.69")
        assertEquals(10000L, viewModel.draftItems.value.first { it.itemId == "item-1" }.lineTotalPaisa)

        // Focus + type in Price in TOTAL_AMOUNT mode: changing 13 -> 10 does not concatenate ("1013") and updates Qty -> 10
        composeTestRule.onNodeWithTag("price_input_item-1").performClick()
        composeTestRule.onNodeWithTag("price_input_item-1").performTextInput("10")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("qty_input_item-1").assertTextContains("10")
        composeTestRule.onNodeWithTag("total_input_item-1").assertTextContains("100")
        assertEquals(10000L, viewModel.draftItems.value.first { it.itemId == "item-1" }.lineTotalPaisa)

        // Switch back to Quantity edit (15) -> Total becomes 150
        composeTestRule.onNodeWithTag("qty_input_item-1").performTextReplacement("15")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("total_input_item-1").assertTextContains("150")
        assertEquals(15000L, viewModel.draftItems.value.first { it.itemId == "item-1" }.lineTotalPaisa)
    }
}
