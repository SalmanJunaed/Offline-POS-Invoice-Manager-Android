package com.example.pos

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.pos.core.util.DateTimeUtils
import com.example.pos.data.Localization
import com.example.pos.ui.screens.DatabaseStatusSettingsTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupExportDatePickerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testSettingsTabOrderAndNumbering() {
        val tabTitles = listOf(
            Localization.get(Localization.Key.TAB_COMPANY_HEADER, "bn"),
            Localization.get(Localization.Key.TAB_USER_PROFILE, "bn"),
            Localization.get(Localization.Key.TAB_FOOTER_NOTE, "bn"),
            Localization.get(Localization.Key.TAB_PRINTER, "bn"),
            Localization.get(Localization.Key.TAB_DATABASE_STATUS, "bn")
        )

        assertEquals("Settings must have exactly 5 tabs", 5, tabTitles.size)
        assertEquals("Tab 1 must be Company Header", "১. কোম্পানি হেডার", tabTitles[0])
        assertEquals("Tab 2 must be Operator Profile", "২. অপারেটর প্রোফাইল", tabTitles[1])
        assertEquals("Tab 3 must be Footer Note", "৩. ফুটার নোট", tabTitles[2])
        assertEquals("Tab 4 must be Printer Settings", "৪. প্রিন্টার সেটিং", tabTitles[3])
        assertEquals("Tab 5 must be Database Capacity & Storage", "৫. ডাটাবেজ ধারণক্ষমতা ও স্টোরেজ", tabTitles[4])

        // Ensure Language and Theme tabs are not present in settings navigation
        assertFalse(tabTitles.any { it.contains("ভাষা") })
        assertFalse(tabTitles.any { it.contains("থিম") })
    }

    @Test
    fun testBengaliNumeralDateParsing() {
        // Test ASCII digits
        assertEquals("15-03-2026", DateTimeUtils.toAsciiDigits("15-03-2026"))
        val dateAscii = DateTimeUtils.parseDateDdMmYyyy("15-03-2026")
        assertNotNull(dateAscii)

        // Test Bengali digits
        val bnDateString = "১৫-০৩-২০২৬"
        assertEquals("15-03-2026", DateTimeUtils.toAsciiDigits(bnDateString))
        val dateBn = DateTimeUtils.parseDateDdMmYyyy(bnDateString)
        assertNotNull("Bengali digits must parse into a valid Date object", dateBn)

        val cal = Calendar.getInstance().apply { time = dateBn!! }
        assertEquals(15, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.MARCH, cal.get(Calendar.MONTH))
        assertEquals(2026, cal.get(Calendar.YEAR))

        // Test invalid dates
        assertNull(DateTimeUtils.parseDateDdMmYyyy("32-01-2026"))
        assertNull(DateTimeUtils.parseDateDdMmYyyy("invalid-date"))
        assertNull(DateTimeUtils.parseDateDdMmYyyy(""))
    }

    @Test
    fun testBengaliDateFormattingConversion() {
        val formattedAscii = "15-03-2026"
        val formattedBn = DateTimeUtils.toBengaliNumerals(formattedAscii)
        assertEquals("১৫-০৩-২০২৬", formattedBn)
    }

    @Test
    fun testDatabaseStatusSettingsTab_DatePickersRenderAndClickable() {
        var exportedRange: Pair<Long, Long>? = null
        var backupRange: Pair<Long, Long>? = null

        composeTestRule.setContent {
            androidx.compose.foundation.layout.Column(
                modifier = androidx.compose.ui.Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())
            ) {
                DatabaseStatusSettingsTab(
                    totalInvoices = 150,
                    totalLines = 450,
                    totalProducts = 25,
                    totalCustomers = 40,
                    databaseSizeBytes = 1024 * 500,
                    supportedCapacity = 200_000,
                    language = "bn",
                    isExporting = false,
                    isImporting = false,
                    onRefreshSize = {},
                    onExportExcelFile = { range -> exportedRange = range },
                    onImportExcel = {},
                    onBackupExcel = { range -> backupRange = range }
                )
            }
        }

        // Verify date input fields and their picker buttons exist in composition
        composeTestRule.onNodeWithTag("export_from_date_input").assertExists()
        composeTestRule.onNodeWithTag("export_to_date_input").assertExists()
        composeTestRule.onNodeWithTag("export_from_date_picker_btn").assertExists()
        composeTestRule.onNodeWithTag("export_to_date_picker_btn").assertExists()

        // Clicking on the from date picker button opens the DatePickerDialog
        composeTestRule.onNodeWithTag("export_from_date_picker_btn").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("from_date_picker_cancel_btn", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("from_date_picker_confirm_btn", useUnmergedTree = true).assertExists()

        // Clicking cancel closes the dialog without crashing
        composeTestRule.onNodeWithTag("from_date_picker_cancel_btn", useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("from_date_picker_cancel_btn", useUnmergedTree = true).assertDoesNotExist()

        // Clicking on the to date picker button opens the DatePickerDialog
        composeTestRule.onNodeWithTag("export_to_date_picker_btn").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("to_date_picker_cancel_btn", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("to_date_picker_confirm_btn", useUnmergedTree = true).assertExists()

        // Clicking cancel closes the dialog
        composeTestRule.onNodeWithTag("to_date_picker_cancel_btn", useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("to_date_picker_cancel_btn", useUnmergedTree = true).assertDoesNotExist()
    }
}
