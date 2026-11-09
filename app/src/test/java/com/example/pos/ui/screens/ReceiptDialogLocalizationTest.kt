package com.example.pos.ui.screens

import com.example.pos.core.util.BengaliTransliterationEngine
import com.example.pos.core.util.DateTimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptDialogLocalizationTest {

    @Test
    fun testLocalizePreTableLineHeadersAndMetadata() {
        // Memo title
        val titleLine = localizePreTableLine("INVOICE / ক্যাশ মেমো (PAID)")
        assertEquals("ক্যাশ মেমো", titleLine)

        // Order ID
        val orderLine = localizePreTableLine("Order ID: #12345")
        assertEquals("অর্ডার নম্বর: #১২৩৪৫", orderLine)

        // Date
        val dateLine = localizePreTableLine("Date: 15 Mar 2026 02:30 PM")
        assertTrue("Date should be localized with Bengali numerals and month", dateLine.contains("তারিখ:"))
        assertTrue("Date should have Bengali numerals", dateLine.contains("১৫"))
        assertTrue("Date should have Bengali month", dateLine.contains("মার্চ"))
        assertTrue("Date should have Bengali PM marker", dateLine.contains("অপরাহ্ন"))

        // Operator
        val opLine = localizePreTableLine("Operator: Rahim")
        assertEquals("অপারেটর: রহিম", opLine)

        // Customer
        val custLine = localizePreTableLine("Customer: Karim")
        assertEquals("গ্রাহক: করিম", custLine)

        // Phone
        val phoneLine = localizePreTableLine("Phone: 01700000000")
        assertEquals("ফোন: ০১৭০০০০০০০০", phoneLine)

        // Branch
        val branchLine = localizePreTableLine("Branch #1 Dhaka")
        assertTrue("Branch should be localized", branchLine.contains("শাখা #১"))
    }

    @Test
    fun testLocalizeUnit() {
        assertEquals("লিটার", localizeUnit("L"))
        assertEquals("লিটার", localizeUnit("ltr"))
        assertEquals("কেজি", localizeUnit("kg"))
        assertEquals("পিস", localizeUnit("pcs"))
        assertEquals("গ্রাম", localizeUnit("gm"))
    }

    @Test
    fun testScenarioAFullyUnpaidSummaryRows() {
        // Scenario A: Only TOTAL present, isFullPaid = false
        val parsed = listOf(
            VoucherSummaryRow(label = "TOTAL", amount = "1000.00", isBold = true)
        )
        val result = buildEffectivePreviewSummaryRows(parsed, isFullPaid = false)

        assertEquals(3, result.size)
        // মোট
        assertEquals("মোট", result[0].label)
        assertEquals("৳১০০০.০০", result[0].amount)
        assertTrue(result[0].isBold)
        // জমা
        assertEquals("জমা", result[1].label)
        assertEquals("৳০.০০", result[1].amount)
        // বকেয়া
        assertEquals("বকেয়া", result[2].label)
        assertEquals("৳১০০০.০০", result[2].amount)
        assertTrue(result[2].isDue)
    }

    @Test
    fun testScenarioBPartialPaymentSummaryRows() {
        // Scenario B: Partial payment (Total: 1000, Paid: 400, Due: 600)
        val parsed = listOf(
            VoucherSummaryRow(label = "উপমোট", amount = "1000.00", isBold = false),
            VoucherSummaryRow(label = "মোট", amount = "1000.00", isBold = true),
            VoucherSummaryRow(label = "জমা (Paid):", amount = "400.00", isBold = false),
            VoucherSummaryRow(label = "বাকি (Due):", amount = "600.00", isBold = true, isDue = true)
        )
        val result = buildEffectivePreviewSummaryRows(parsed, isFullPaid = false)

        assertEquals(4, result.size)
        // উপ-মোট
        assertEquals("উপ-মোট", result[0].label)
        assertEquals("৳১০০০.০০", result[0].amount)
        // মোট
        assertEquals("মোট", result[1].label)
        assertEquals("৳১০০০.০০", result[1].amount)
        // জমা
        assertEquals("জমা", result[2].label)
        assertEquals("৳৪০০.০০", result[2].amount)
        // বকেয়া
        assertEquals("বকেয়া", result[3].label)
        assertEquals("৳৬০০.০০", result[3].amount)
        assertTrue(result[3].isDue)
    }

    @Test
    fun testScenarioCFullyPaidSummaryRows() {
        // Scenario C: Fully paid (Total: 450.00, isFullPaid = true, no explicit paid/due lines)
        val parsed = listOf(
            VoucherSummaryRow(label = "Sub Total", amount = "450.00", isBold = false),
            VoucherSummaryRow(label = "TOTAL", amount = "450.00", isBold = true)
        )
        val result = buildEffectivePreviewSummaryRows(parsed, isFullPaid = true)

        assertEquals(4, result.size)
        // উপ-মোট
        assertEquals("উপ-মোট", result[0].label)
        assertEquals("৳৪৫০.০০", result[0].amount)
        // মোট
        assertEquals("মোট", result[1].label)
        assertEquals("৳৪৫০.০০", result[1].amount)
        // জমা
        assertEquals("জমা", result[2].label)
        assertEquals("৳৪৫০.০০", result[2].amount)
        // বকেয়া
        assertEquals("বকেয়া", result[3].label)
        assertEquals("৳০.০০", result[3].amount)
    }

    @Test
    fun testItemTransliterationAndBengaliNumerals() {
        val dieselBn = BengaliTransliterationEngine.transliterate("Diesel")
        val petrolBn = BengaliTransliterationEngine.transliterate("Petrol")
        assertEquals("ডিজেল", dieselBn)
        assertEquals("পেট্রোল", petrolBn)

        val qtyBn = DateTimeUtils.toBengaliNumerals("10.50")
        val rateBn = DateTimeUtils.toBengaliNumerals("105.00")
        val totalBn = DateTimeUtils.toBengaliNumerals("1102.50")
        assertEquals("১০.৫০", qtyBn)
        assertEquals("১০৫.০০", rateBn)
        assertEquals("১১০২.৫০", totalBn)
    }
}
