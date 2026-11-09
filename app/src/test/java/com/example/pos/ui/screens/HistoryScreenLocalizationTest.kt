package com.example.pos.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.pos.core.util.BengaliTransliterationEngine
import com.example.pos.core.util.DateTimeUtils
import com.example.pos.data.db.InvoiceEntity
import com.example.pos.data.db.InvoiceLineEntity
import com.example.pos.domain.FinancialCalculationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HistoryScreenLocalizationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testFormatHistoryDateTimeBn() {
        val cal = Calendar.getInstance()
        cal.set(2026, Calendar.MARCH, 15, 14, 30, 0)
        val timeMs = cal.timeInMillis

        val formatted = formatHistoryDateTimeBn(timeMs)
        // Must contain Bengali numerals and Bengali month
        assertTrue("Must contain Bengali year/day numerals", formatted.contains("২০২৬") || formatted.contains("১৫"))
        assertTrue("Must contain Bengali month March", formatted.contains("মার্চ"))
        assertTrue("Must contain Bengali PM marker", formatted.contains("অপরাহ্ন"))
    }

    @Test
    fun testHistoryInvoiceCardPartialPaymentDisplay() {
        val invoice = InvoiceEntity(
            orderId = "ORD-12345",
            dateTime = 1773563400000L, // Fixed timestamp
            operatorId = "OP-1",
            operatorName = "Rahim",
            customerInfo = "Kamal",
            rawTotalPaisa = 100000L,
            roundedTotalBdt = 1000L,
            paidAmountBdt = 400L,
            isPaid = false,
            branchCode = "01",
            footerNote = "Thank you"
        )
        val lines = listOf(
            InvoiceLineEntity(
                orderId = "ORD-12345",
                itemId = "ITM-1",
                itemName = "Diesel",
                itemType = "Liquid",
                unitPricePaisa = 10500L,
                quantityStr = "10.0",
                unit = "Ltr",
                lineTotalPaisa = 105000L
            )
        )

        composeTestRule.setContent {
            HistoryInvoiceCard(
                invoice = invoice,
                isExpanded = true,
                lines = lines,
                language = "bn",
                onToggleExpand = {},
                onSelectForDetails = {},
                onViewReceipt = {},
                onSharePdf = {},
                onDelete = {},
                onEdit = {}
            )
        }

        // 1. Order ID in Bengali numerals
        composeTestRule.onNodeWithText("#ORD-১২৩৪৫").assertExists()

        // 2. Status Badge in Bengali
        composeTestRule.onNodeWithTag("status_badge_ORD-12345", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("বকেয়া").assertExists()

        // 3. Paid & Due amounts with Bengali numerals
        val paidNode = composeTestRule.onNodeWithTag("paid_amount_ORD-12345", useUnmergedTree = true)
        paidNode.assertExists()
        composeTestRule.onNodeWithText("জমা: ৪০০ ৳").assertExists()

        val dueNode = composeTestRule.onNodeWithTag("due_amount_ORD-12345", useUnmergedTree = true)
        dueNode.assertExists()
        composeTestRule.onNodeWithText("বাকি: ৬০০ ৳").assertExists()

        // 4. Customer transliterated
        composeTestRule.onNodeWithText("কামাল").assertExists()

        // 5. Operator transliterated
        composeTestRule.onNodeWithText("অপারেটর: রহিম").assertExists()

        // 6. Item name transliterated and details in Bengali
        composeTestRule.onNodeWithText("ডিজেল").assertExists()

        // 7. Verify original entity fields remained completely unaltered
        assertEquals("ORD-12345", invoice.orderId)
        assertEquals(1000L, invoice.roundedTotalBdt)
        assertEquals(400L, invoice.paidAmountBdt)
        assertEquals("Kamal", invoice.customerInfo)
        assertEquals("Rahim", invoice.operatorName)
        assertEquals("Diesel", lines[0].itemName)
    }

    @Test
    fun testHistoryInvoiceCardFullyPaidDisplay() {
        val invoice = InvoiceEntity(
            orderId = "ORD-9999",
            dateTime = System.currentTimeMillis(),
            operatorId = "OP-2",
            operatorName = "Karim",
            customerInfo = "",
            rawTotalPaisa = 50000L,
            roundedTotalBdt = 500L,
            paidAmountBdt = 500L,
            isPaid = true,
            branchCode = "02"
        )

        composeTestRule.setContent {
            HistoryInvoiceCard(
                invoice = invoice,
                isExpanded = false,
                lines = emptyList(),
                language = "bn",
                onToggleExpand = {},
                onSelectForDetails = {},
                onViewReceipt = {}
            )
        }

        // Status badge should display "পরিশোধিত"
        composeTestRule.onNodeWithTag("status_badge_ORD-9999", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("পরিশোধিত").assertExists()

        // Walk-in customer fallback in Bengali
        composeTestRule.onNodeWithText("(ওয়াক-ইন কাস্টমার)").assertExists()

        // Paid amount
        composeTestRule.onNodeWithText("জমা: ৫০০ ৳").assertExists()
    }
}
