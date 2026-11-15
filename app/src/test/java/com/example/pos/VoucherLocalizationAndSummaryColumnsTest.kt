package com.example.pos

import com.example.pos.data.Localization
import com.example.pos.data.db.CompanyHeaderEntity
import com.example.pos.data.db.InvoiceEntity
import com.example.pos.data.db.InvoiceLineEntity
import com.example.pos.printer.EscPosPrinterEngine
import com.example.pos.printer.ThermalReceiptFormatter
import com.example.pos.ui.screens.parseReceiptSectionsForPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VoucherLocalizationAndSummaryColumnsTest {

    private fun sampleCompany(): CompanyHeaderEntity {
        return CompanyHeaderEntity(
            companyName = "Green Grocery",
            branchName = "Dhanmondi Branch",
            branchNumber = "04",
            phoneNumber = "01711223344",
            address = "Road 27, Dhanmondi, Dhaka"
        )
    }

    private fun sampleInvoice(): InvoiceEntity {
        return InvoiceEntity(
            orderId = "ORD-9910",
            dateTime = 1711188000000L,
            operatorId = "OP-1",
            operatorName = "Kamal Hossain",
            customerInfo = "01912345678",
            rawTotalPaisa = 45000L,
            roundedTotalBdt = 450L,
            footerNote = "Thank you!\nVisit again."
        )
    }

    private fun sampleLines(): List<InvoiceLineEntity> {
        return listOf(
            InvoiceLineEntity(
                orderId = "ORD-9910",
                itemId = "P1",
                itemName = "Mustard Oil",
                itemType = "Liquid",
                unitPricePaisa = 25000L,
                quantityStr = "1",
                unit = "Ltr",
                lineTotalPaisa = 25000L
            ),
            InvoiceLineEntity(
                orderId = "ORD-9910",
                itemId = "P2",
                itemName = "Sugar",
                itemType = "Product",
                unitPricePaisa = 10000L,
                quantityStr = "2",
                unit = "Kg",
                lineTotalPaisa = 20000L
            )
        )
    }

    @Test
    fun testLocalizationKeysCompleteness() {
        // Audit and verify all required voucher labels exist in en and bn
        val keys = listOf(
            Localization.Key.VOUCHER_ITEM_NAME to Pair("Item Name", "পণ্যের নাম"),
            Localization.Key.VOUCHER_DESCRIPTION to Pair("Item Description", "বিবরণ"),
            Localization.Key.VOUCHER_QTY to Pair("Qty", "পরিমাণ"),
            Localization.Key.VOUCHER_UNIT to Pair("Unit", "একক"),
            Localization.Key.VOUCHER_RATE to Pair("Rate", "দর"),
            Localization.Key.VOUCHER_TOTAL to Pair("Total", "মোট"),
            Localization.Key.VOUCHER_SUBTOTAL to Pair("Sub Total", "উপমোট"),
            Localization.Key.VOUCHER_TOTAL_SUMMARY to Pair("TOTAL", "মোট"),
            Localization.Key.VOUCHER_DATE to Pair("Date", "তারিখ"),
            Localization.Key.VOUCHER_ORDER_ID to Pair("Order ID", "অর্ডার আইডি"),
            Localization.Key.VOUCHER_OPERATOR to Pair("Operator", "অপারেটর"),
            Localization.Key.VOUCHER_CUSTOMER to Pair("Customer", "ক্রেতা"),
            Localization.Key.VOUCHER_FULL_PAID to Pair("FULL PAID ✓", "সম্পূর্ণ পরিশোধিত ✓"),
            Localization.Key.VOUCHER_FULL_PAID_BADGE to Pair("Full Paid", "সম্পূর্ণ পরিশোধিত")
        )

        for ((key, expected) in keys) {
            val enVal = Localization.get(key, "en")
            val bnVal = Localization.get(key, "bn")
            assertEquals("English mismatch for key $key", expected.first, enVal)
            assertEquals("Bangla mismatch for key $key", expected.second, bnVal)
        }
    }

    @Test
    fun testEnglish58mmVoucherSummaryRowsAndLabels() {
        val text = ThermalReceiptFormatter.generateReceiptText(
            header = sampleCompany(),
            invoice = sampleInvoice(),
            lines = sampleLines(),
            paperSize = "58mm",
            footerLine1 = "Thank you!",
            footerLine2 = "",
            isFullPaid = true,
            lang = "en"
        )

        assertTrue("Should contain English Item Name header", text.contains("Item Name"))
        assertTrue("Should contain English Qty header", text.contains("Qty"))
        assertTrue("Should contain English Rate header", text.contains("Rate"))
        assertTrue("Should contain English Total header", text.contains("Total"))
        assertTrue("Should contain English Date label", text.contains("Date:"))
        assertTrue("Should contain English Order ID label", text.contains("Order ID:"))
        assertTrue("Should contain English Operator label", text.contains("Operator:"))
        assertTrue("Should contain English Customer label", text.contains("Customer:"))
        assertTrue("Should contain English FULL PAID", text.contains("FULL PAID ✓"))

        // Check Subtotal line layout: Sub Total on left, amount on right
        val lines = text.lines()
        val subtotalLine = lines.firstOrNull { it.contains("Sub Total") }
        assertNotNull("Sub Total line must be present", subtotalLine)
        assertTrue("Sub Total must start at column 0", subtotalLine!!.startsWith("Sub Total"))
        assertTrue("Sub Total amount must end at column width 32", subtotalLine.trimEnd().endsWith("450.00"))

        // Check TOTAL line layout: TOTAL on left, amount on right
        val totalLine = lines.firstOrNull { it.contains("TOTAL") }
        assertNotNull("TOTAL line must be present", totalLine)
        assertTrue("TOTAL must start at column 0", totalLine!!.startsWith("TOTAL"))
        assertTrue("TOTAL amount must end at column width 32", totalLine.trimEnd().endsWith("450.00"))
    }

    @Test
    fun testBangla58mmVoucherSummaryRowsAndLabels() {
        val text = ThermalReceiptFormatter.generateReceiptText(
            header = sampleCompany(),
            invoice = sampleInvoice(),
            lines = sampleLines(),
            paperSize = "58mm",
            footerLine1 = "ধন্যবাদ!",
            footerLine2 = "",
            isFullPaid = true,
            lang = "bn"
        )

        assertTrue("Should contain Bangla Item Name header", text.contains("পণ্যের নাম"))
        assertTrue("Should contain Bangla Qty header", text.contains("পরিমাণ"))
        assertTrue("Should contain Bangla Rate header", text.contains("দর"))
        assertTrue("Should contain Bangla Total header", text.contains("মোট"))
        assertTrue("Should contain Bangla Date label", text.contains("তারিখ:"))
        assertTrue("Should contain Bangla Order ID label", text.contains("অর্ডার নম্বর:"))
        assertTrue("Should contain Bangla Operator label", text.contains("অপারেটর:"))
        assertTrue("Should contain Bangla Customer label", text.contains("গ্রাহক:"))
        assertTrue("Should contain Bangla FULL PAID", text.contains("সম্পূর্ণ পরিশোধিত ✓"))

        // Check Subtotal line layout: উপ-মোট on left, amount on right
        val lines = text.lines()
        val subtotalLine = lines.firstOrNull { it.contains("উপ-মোট") }
        assertNotNull("উপ-মোট line must be present", subtotalLine)
        assertTrue("উপ-মোট must start at column 0", subtotalLine!!.startsWith("উপ-মোট"))
        assertTrue("উপ-মোট amount must end at right", subtotalLine.trimEnd().endsWith("৪৫০.০০"))

        // Check TOTAL line layout: সর্বমোট প্রদেয় on left, amount on right
        val totalLine = lines.firstOrNull { it.startsWith("সর্বমোট প্রদেয়") }
        assertNotNull("সর্বমোট প্রদেয় line must be present", totalLine)
        assertTrue("সর্বমোট প্রদেয় must start at column 0", totalLine!!.startsWith("সর্বমোট প্রদেয়"))
        assertTrue("সর্বমোট প্রদেয় amount must end at right", totalLine.trimEnd().endsWith("৪৫০.০০"))
    }

    @Test
    fun testEnglish80mmVoucherSummaryRowsAndLabels() {
        val text = ThermalReceiptFormatter.generateReceiptText(
            header = sampleCompany(),
            invoice = sampleInvoice(),
            lines = sampleLines(),
            paperSize = "80mm",
            footerLine1 = "Thank you!",
            footerLine2 = "",
            isFullPaid = true,
            lang = "en"
        )

        assertTrue("Should contain English Description header", text.contains("Item Description"))
        assertTrue("Should contain English Unit header", text.contains("Unit"))

        val lines = text.lines()
        val subtotalLine = lines.firstOrNull { it.contains("Sub Total") }
        assertNotNull("Sub Total line must be present", subtotalLine)
        assertTrue("Sub Total must start at column 0", subtotalLine!!.startsWith("Sub Total"))
        assertTrue("Sub Total amount must end at column width 48", subtotalLine.trimEnd().endsWith("450.00"))

        val totalLine = lines.firstOrNull { it.contains("TOTAL") }
        assertNotNull("TOTAL line must be present", totalLine)
        assertTrue("TOTAL must start at column 0", totalLine!!.startsWith("TOTAL"))
        assertTrue("TOTAL amount must end at column width 48", totalLine.trimEnd().endsWith("450.00"))
    }

    @Test
    fun testBangla80mmVoucherSummaryRowsAndLabels() {
        val text = ThermalReceiptFormatter.generateReceiptText(
            header = sampleCompany(),
            invoice = sampleInvoice(),
            lines = sampleLines(),
            paperSize = "80mm",
            footerLine1 = "ধন্যবাদ!",
            footerLine2 = "",
            isFullPaid = true,
            lang = "bn"
        )

        assertTrue("Should contain Bangla Description header", text.contains("বিবরণ"))
        assertTrue("Should contain Bangla Unit header", text.contains("একক"))

        val lines = text.lines()
        val subtotalLine = lines.firstOrNull { it.contains("উপ-মোট") }
        assertNotNull("উপ-মোট line must be present", subtotalLine)
        assertTrue("উপ-মোট must start at column 0", subtotalLine!!.startsWith("উপ-মোট"))
        assertTrue("উপ-মোট amount must end at right", subtotalLine.trimEnd().endsWith("৪৫০.০০"))

        val totalLine = lines.firstOrNull { it.startsWith("সর্বমোট প্রদেয়") }
        assertNotNull("সর্বমোট প্রদেয় line must be present", totalLine)
        assertTrue("সর্বমোট প্রদেয় must start at column 0", totalLine!!.startsWith("সর্বমোট প্রদেয়"))
        assertTrue("সর্বমোট প্রদেয় amount must end at right", totalLine.trimEnd().endsWith("৪৫০.০০"))
    }

    @Test
    fun testParseReceiptSectionsForPreviewWithSummaryRows() {
        // Test parsing English 58mm receipt
        val enText = ThermalReceiptFormatter.generateReceiptText(
            header = sampleCompany(),
            invoice = sampleInvoice(),
            lines = sampleLines(),
            paperSize = "58mm",
            footerLine1 = "Footer 1",
            footerLine2 = "",
            isFullPaid = true,
            lang = "en"
        )
        val enSections = parseReceiptSectionsForPreview(enText, emptyList(), "en")
        assertNotNull("English preview sections must parse", enSections)
        assertEquals(2, enSections!!.table.rows.size)
        assertEquals(2, enSections.table.summaryRows.size)
        assertEquals("Sub Total", enSections.table.summaryRows[0].label)
        assertEquals("450.00", enSections.table.summaryRows[0].amount)
        assertFalse(enSections.table.summaryRows[0].isBold)
        assertEquals("TOTAL", enSections.table.summaryRows[1].label)
        assertEquals("450.00", enSections.table.summaryRows[1].amount)
        assertTrue(enSections.table.summaryRows[1].isBold)
        // postTableText should NOT duplicate Subtotal or Total
        assertFalse(enSections.postTableText.contains("Sub Total"))
        assertFalse(enSections.postTableText.contains("TOTAL"))

        // Test parsing Bangla 58mm receipt
        val bnText = ThermalReceiptFormatter.generateReceiptText(
            header = sampleCompany(),
            invoice = sampleInvoice(),
            lines = sampleLines(),
            paperSize = "58mm",
            footerLine1 = "ধন্যবাদ",
            footerLine2 = "",
            isFullPaid = true,
            lang = "bn"
        )
        val bnSections = parseReceiptSectionsForPreview(bnText, emptyList(), "bn")
        assertNotNull("Bangla preview sections must parse", bnSections)
        assertEquals(2, bnSections!!.table.rows.size)
        assertEquals(4, bnSections.table.summaryRows.size)
        assertEquals("উপ-মোট", bnSections.table.summaryRows[0].label)
        assertEquals("৪৫০.০০", bnSections.table.summaryRows[0].amount)
        assertFalse(bnSections.table.summaryRows[0].isBold)
        assertEquals("সর্বমোট প্রদেয়", bnSections.table.summaryRows[1].label)
        assertEquals("৪৫০.০০", bnSections.table.summaryRows[1].amount)
        assertTrue(bnSections.table.summaryRows[1].isBold)
        assertEquals("জমা", bnSections.table.summaryRows[2].label)
        assertEquals("৪৫০", bnSections.table.summaryRows[2].amount)
        assertFalse(bnSections.table.summaryRows[2].isDue)
        assertEquals("বকেয়া", bnSections.table.summaryRows[3].label)
        assertEquals("০", bnSections.table.summaryRows[3].amount)
        assertTrue(bnSections.table.summaryRows[3].isDue)
        // postTableText should NOT duplicate Subtotal, Total, Paid, or Due
        assertFalse(bnSections.postTableText.contains("উপ-মোট"))
        assertFalse(bnSections.postTableText.contains("সর্বমোট প্রদেয়"))
        assertFalse(bnSections.postTableText.contains("জমা"))
        assertFalse(bnSections.postTableText.contains("বকেয়া"))
    }

    @Test
    fun testEscPosBinaryReceiptWithBanglaSummaryAndFullPaid() {
        val payload = ThermalReceiptFormatter.buildEscPosReceipt(
            header = sampleCompany(),
            invoice = sampleInvoice(),
            lines = sampleLines(),
            paperSize = "58mm",
            autoCutter = true,
            footerLine1 = "ধন্যবাদ!",
            footerLine2 = "",
            logoBitmap = null,
            isFullPaid = true,
            lang = "bn"
        )
        assertNotNull(payload)
        assertTrue("ESC/POS payload must not be empty", payload.isNotEmpty())
    }

    @Test
    fun testStep05B_BengaliUnitMappingsAnd58mmCurrency() {
        val unitLines = listOf(
            InvoiceLineEntity(orderId = "U1", itemId = "1", itemName = "CNG Fuel", itemType = "Liquid", unit = "Ltr", quantityStr = "10.5", unitPricePaisa = 10000L, lineTotalPaisa = 105000L),
            InvoiceLineEntity(orderId = "U1", itemId = "2", itemName = "Filter", itemType = "Product", unit = "Pcs", quantityStr = "1", unitPricePaisa = 5000L, lineTotalPaisa = 5000L),
            InvoiceLineEntity(orderId = "U1", itemId = "3", itemName = "Maintenance", itemType = "Service", unit = "Service", quantityStr = "1", unitPricePaisa = 20000L, lineTotalPaisa = 20000L),
            InvoiceLineEntity(orderId = "U1", itemId = "4", itemName = "CNG Gas", itemType = "Gas", unit = "CFT", quantityStr = "1", unitPricePaisa = 4000L, lineTotalPaisa = 4000L),
            InvoiceLineEntity(orderId = "U1", itemId = "5", itemName = "Bulk Gas", itemType = "Gas", unit = "m³", quantityStr = "1", unitPricePaisa = 6000L, lineTotalPaisa = 6000L),
            InvoiceLineEntity(orderId = "U1", itemId = "6", itemName = "Sugar", itemType = "Product", unit = "Kg", quantityStr = "2", unitPricePaisa = 5000L, lineTotalPaisa = 10000L),
            InvoiceLineEntity(orderId = "U1", itemId = "7", itemName = "Spice", itemType = "Product", unit = "Gram", quantityStr = "100", unitPricePaisa = 200L, lineTotalPaisa = 20000L),
            InvoiceLineEntity(orderId = "U1", itemId = "8", itemName = "Biscuit", itemType = "Product", unit = "Pack", quantityStr = "1", unitPricePaisa = 3000L, lineTotalPaisa = 3000L),
            InvoiceLineEntity(orderId = "U1", itemId = "9", itemName = "Soap", itemType = "Product", unit = "Box", quantityStr = "1", unitPricePaisa = 12000L, lineTotalPaisa = 12000L),
            InvoiceLineEntity(orderId = "U1", itemId = "10", itemName = "Syrup", itemType = "Liquid", unit = "ML", quantityStr = "250", unitPricePaisa = 100L, lineTotalPaisa = 25000L)
        )
        val inv = sampleInvoice().copy(orderId = "U1", rawTotalPaisa = 210000L, roundedTotalBdt = 2100L, paidAmountBdt = 2100L, isPaid = true)

        val text58 = ThermalReceiptFormatter.generateReceiptText(sampleCompany(), inv, unitLines, "58mm", "ধন্যবাদ", "", isFullPaid = true, lang = "bn")
        assertTrue(text58.contains("১০.৫ লি."))
        assertTrue(text58.contains("১ টি"))
        assertTrue(text58.contains("১ x ২০০.০০"))
        assertTrue(text58.contains("১ ঘনফুট"))
        assertTrue(text58.contains("১ ঘনমিটার"))
        assertTrue(text58.contains("২ কেজি"))
        assertTrue(text58.contains("১০০ গ্রাম"))
        assertTrue(text58.contains("১ প্যাক"))
        assertTrue(text58.contains("১ বক্স"))
        assertTrue(text58.contains("২৫০ মিলি"))
        assertTrue("58mm Bengali receipt should use ৳ currency suffix", text58.contains("৳"))
        assertFalse("58mm Bengali receipt should not contain BDT", text58.contains("BDT"))

        val text80 = ThermalReceiptFormatter.generateReceiptText(sampleCompany(), inv, unitLines, "80mm", "ধন্যবাদ", "", isFullPaid = true, lang = "bn")
        assertTrue(text80.contains("ঘনফুট"))
        assertTrue(text80.contains("ঘনমিটার"))
        assertTrue(text80.contains("লি."))
        assertTrue(text80.contains("টি"))

        val raster80 = ThermalReceiptFormatter.render80mmItemRowToRaster("বাল্ক অটোগ্যাস প্রিমিয়াম", "১০.৫", "ঘনমিটার", "৪০০.০০", "৪২০০.০০")
        assertNotNull("80mm raster for ঘনমিটার must render cleanly", raster80)
        assertTrue(raster80!!.size > 8)
        assertEquals((ThermalReceiptFormatter.DOTS_80MM / 8).toByte(), raster80[4])
    }

    @Test
    fun testStep05B_FinancialSummaryRoundingAndPaymentStates() {
        // 1. Rounding Adjustment present when non-zero, omitted when zero
        val roundedInvoice = sampleInvoice().copy(rawTotalPaisa = 45040L, roundedTotalBdt = 450L, paidAmountBdt = 450L, isPaid = true)
        val roundedText = ThermalReceiptFormatter.generateReceiptText(sampleCompany(), roundedInvoice, sampleLines(), "58mm", "", "", isFullPaid = true, lang = "bn")
        assertTrue("Rounding adjustment label must appear when non-zero", roundedText.contains("রাউন্ডিং সমন্বয়"))

        val exactInvoice = sampleInvoice().copy(rawTotalPaisa = 45000L, roundedTotalBdt = 450L, paidAmountBdt = 450L, isPaid = true)
        val exactText = ThermalReceiptFormatter.generateReceiptText(sampleCompany(), exactInvoice, sampleLines(), "58mm", "", "", isFullPaid = true, lang = "bn")
        assertFalse("Rounding adjustment must be omitted when zero", exactText.contains("রাউন্ডিং সমন্বয়"))

        // 2. Unpaid: Total 1000, Paid 0, Due 1000
        val unpaidInv = sampleInvoice().copy(rawTotalPaisa = 100000L, roundedTotalBdt = 1000L, paidAmountBdt = 0L, isPaid = false)
        val unpaidText = ThermalReceiptFormatter.generateReceiptText(sampleCompany(), unpaidInv, sampleLines(), "58mm", "", "", isFullPaid = false, lang = "bn")
        val unpaidSections = parseReceiptSectionsForPreview(unpaidText, emptyList(), "bn")!!
        assertEquals("১০০০.০০", unpaidSections.table.summaryRows.first { it.label == "সর্বমোট প্রদেয়" }.amount)
        // Verify unpaid row specifically
        val unpaidPaidRow = unpaidSections.table.summaryRows.first { it.label == "জমা" }
        val unpaidDueRow = unpaidSections.table.summaryRows.first { it.label == "বকেয়া" }
        assertEquals("০", unpaidPaidRow.amount)
        assertEquals("১০০০", unpaidDueRow.amount)
        assertFalse(unpaidText.contains("সম্পূর্ণ পরিশোধিত ✓"))

        // 3. Partial: Total 1000, Paid 400, Due 600
        val partialInv = sampleInvoice().copy(rawTotalPaisa = 100000L, roundedTotalBdt = 1000L, paidAmountBdt = 400L, isPaid = false)
        val partialText = ThermalReceiptFormatter.generateReceiptText(sampleCompany(), partialInv, sampleLines(), "80mm", "", "", isFullPaid = false, lang = "bn")
        val partialSections = parseReceiptSectionsForPreview(partialText, emptyList(), "bn")!!
        assertEquals("৪০০", partialSections.table.summaryRows.first { it.label == "জমা" }.amount)
        assertEquals("৬০০", partialSections.table.summaryRows.first { it.label == "বকেয়া" }.amount)
        assertFalse(partialText.contains("সম্পূর্ণ পরিশোধিত ✓"))

        // 4. Fully paid: Total 450, Paid 450, Due 0
        val fullInv = sampleInvoice().copy(rawTotalPaisa = 45000L, roundedTotalBdt = 450L, paidAmountBdt = 450L, isPaid = true)
        val fullText = ThermalReceiptFormatter.generateReceiptText(sampleCompany(), fullInv, sampleLines(), "58mm", "", "", isFullPaid = true, lang = "bn")
        val fullSections = parseReceiptSectionsForPreview(fullText, emptyList(), "bn")!!
        assertEquals("৪৫০", fullSections.table.summaryRows.first { it.label == "জমা" }.amount)
        assertEquals("০", fullSections.table.summaryRows.first { it.label == "বকেয়া" }.amount)
        assertTrue(fullText.contains("সম্পূর্ণ পরিশোধিত ✓"))
    }
}
