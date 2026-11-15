package com.example.pos

import android.content.Context
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import androidx.test.core.app.ApplicationProvider
import com.example.pos.core.error.PosResult
import com.example.pos.data.db.CompanyHeaderEntity
import com.example.pos.data.db.InvoiceEntity
import com.example.pos.data.db.InvoiceLineEntity
import com.example.pos.modules.pdf.PdfInvoiceEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.io.ByteArrayOutputStream
import java.io.File

@Implements(PdfDocument::class)
class ShadowRobolectricPdfDocument {
    @Implementation
    fun startPage(pageInfo: PdfDocument.PageInfo): PdfDocument.Page {
        val pageConstructor = PdfDocument.Page::class.java.getDeclaredConstructor(Canvas::class.java, PdfDocument.PageInfo::class.java)
        pageConstructor.isAccessible = true
        return pageConstructor.newInstance(Canvas(), pageInfo)
    }

    @Implementation
    fun finishPage(page: PdfDocument.Page) {
        // No-op in JVM shadow
    }

    @Implementation
    fun writeTo(out: java.io.OutputStream) {
        out.write("%PDF-1.4\n%mock-pdf-content-for-tests\n%%EOF\n".toByteArray(Charsets.UTF_8))
    }

    @Implementation
    fun close() {
        // No-op in JVM shadow
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], shadows = [ShadowRobolectricPdfDocument::class])
class PdfInvoiceEngineTest {

    private lateinit var context: Context
    private lateinit var sampleHeader: CompanyHeaderEntity
    private lateinit var sampleInvoice: InvoiceEntity

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        sampleHeader = CompanyHeaderEntity(
            companyName = "মেসার্স রাকিব ফিলিং স্টেশন এন্ড এল পি জি অটোগ্যাস ফিলিং স্টেশন",
            branchName = "প্রধান শাখা",
            branchNumber = "01",
            address = "মহাদেবপুর, নওগাঁ",
            phoneNumber = "01700000000"
        )
        sampleInvoice = InvoiceEntity(
            orderId = "DH-2026-PDF01",
            globalTransactionId = "TXN-TEST-001",
            branchCode = "01",
            dateTime = System.currentTimeMillis(),
            operatorId = "OP-101",
            operatorName = "Rakib Operator",
            customerInfo = "01711223344",
            rawTotalPaisa = 136500L,
            roundedTotalBdt = 1365L,
            footerNote = "Thank you for your business!"
        )
    }

    private fun createDummyLines(count: Int): List<InvoiceLineEntity> {
        return (1..count).map { i ->
            InvoiceLineEntity(
                orderId = sampleInvoice.orderId,
                itemId = "ITEM-$i",
                itemName = "Test Product Item #$i",
                itemType = "GOODS",
                unit = "Pcs",
                quantityStr = "1.0",
                unitPricePaisa = 10000L,
                lineTotalPaisa = 10000L,
                sortOrder = i,
                createdAt = System.currentTimeMillis()
            )
        }
    }

    @Test
    fun testPaginationCalculation_SinglePageSmallList() {
        // 0 lines -> empty invoice, fits on 1 page
        assertEquals(1, PdfInvoiceEngine.calculatePageCount(0))

        // 1 line -> fits easily on 1 page
        assertEquals(1, PdfInvoiceEngine.calculatePageCount(1))

        // 10 lines -> 210 + 10*22 = 430. 430 + 110 (summary) = 540 <= 750 -> 1 page
        assertEquals(1, PdfInvoiceEngine.calculatePageCount(10))

        // 19 lines -> 210 + 19*22 = 628. 628 + 110 = 738 <= 750 -> 1 page
        assertEquals(1, PdfInvoiceEngine.calculatePageCount(19))
    }

    @Test
    fun testPaginationCalculation_LinePageBudgetingSummaryOverflow() {
        // Page 1 start Y = 210, MAX_TABLE_Y = 750, ROW_HEIGHT = 22, SUMMARY_HEIGHT = 110
        // Available space for items on page 1: (750 - 210) = 540
        // Max items that can fit on page 1 alone: 540 / 22 = 24 items (210 + 24*22 = 738 <= 750)
        // With 20 items:
        // currentY after items = 210 + 20*22 = 650.
        // Summary check: 650 + 110 = 760 > 750 (overflows page 1!).
        // Thus, summary overflows to Page 2 -> 2 pages total.
        assertEquals(2, PdfInvoiceEngine.calculatePageCount(20))
        assertEquals(2, PdfInvoiceEngine.calculatePageCount(24))
    }

    @Test
    fun testPaginationCalculation_MultiPageHandling() {
        // 25 items:
        // 24 items fit on page 1 (Y = 738).
        // 25th item overflows (738 + 22 = 760 > 750) -> page 2 starts.
        // Page 2 start Y = 90. Item 25 at Y = 90 -> currentY = 112.
        // 112 + 110 = 222 <= 750 -> fits on page 2.
        // Total = 2 pages.
        assertEquals(2, PdfInvoiceEngine.calculatePageCount(25))

        // Page 2 capacity: (750 - 90) = 660. 660 / 22 = 30 items.
        // Total items that can fit on Page 1 (24 items) + Page 2 (30 items) = 54 items.
        // If 54 items: 24 on page 1, 30 on page 2. Page 2 currentY = 90 + 30*22 = 750.
        // Summary: 750 + 110 = 860 > 750 -> summary pushes to Page 3.
        assertEquals(3, PdfInvoiceEngine.calculatePageCount(54))

        // 60 items:
        // Page 1 has 24 items.
        // Page 2 has 30 items (total 54 items).
        // Page 3 has 6 items (total 60 items) + summary.
        // Total = 3 pages.
        assertEquals(3, PdfInvoiceEngine.calculatePageCount(60))

        // 100 items:
        // Page 1: 24 items (remaining 76)
        // Page 2: 30 items (remaining 46)
        // Page 3: 30 items (remaining 16)
        // Page 4: 16 items (Y = 90 + 16*22 = 442) + summary (442 + 110 = 552 <= 750)
        // Total = 4 pages.
        assertEquals(4, PdfInvoiceEngine.calculatePageCount(100))
    }

    @Test
    fun testGenerateInvoicePdf_SinglePageDocument() {
        val lines = createDummyLines(3)
        val result = PdfInvoiceEngine.generateInvoicePdf(
            context = context,
            company = sampleHeader,
            invoice = sampleInvoice,
            lines = lines
        )

        if (result is PosResult.Error) {
            System.err.println("generateInvoicePdf ERROR: " + result.error.message + " cause: " + (result.error as? com.example.pos.core.error.PosError.UnexpectedError)?.cause)
            (result.error as? com.example.pos.core.error.PosError.UnexpectedError)?.cause?.printStackTrace()
        }
        assertTrue("generateInvoicePdf must succeed: ${(result as? PosResult.Error)?.error?.message}", result is PosResult.Success)
        val pdfFile = (result as PosResult.Success).data
        assertNotNull(pdfFile)
        assertTrue("PDF file must exist", pdfFile.exists())
        assertTrue("PDF file must not be empty", pdfFile.length() > 0)
        assertEquals("Invoice_${sampleInvoice.orderId}.pdf", pdfFile.name)
    }

    @Test
    fun testGenerateInvoicePdf_MultiPageDocumentStream() {
        // Generate with 60 lines (multi-page PDF)
        val lines = createDummyLines(60)
        val outputStream = ByteArrayOutputStream()

        val result = PdfInvoiceEngine.writeInvoicePdfToStream(
            context = context,
            company = sampleHeader,
            invoice = sampleInvoice,
            lines = lines,
            outputStream = outputStream
        )

        assertTrue("writeInvoicePdfToStream must succeed", result is PosResult.Success)
        val bytes = outputStream.toByteArray()
        assertTrue("Output bytes must not be empty", bytes.isNotEmpty())

        // Standard PDF magic header check "%PDF-"
        val header = String(bytes.take(5).toByteArray())
        assertEquals("%PDF-", header)
    }

    @Test
    fun testSharePdfIntent() {
        val dummyFile = File(context.cacheDir, "exports/Invoice_TEST-1.pdf").apply {
            parentFile?.mkdirs()
            writeText("dummy-pdf-content")
        }

        val intent = PdfInvoiceEngine.getSharePdfIntent(context, dummyFile)
        assertNotNull(intent)
        assertEquals("application/pdf", intent.type)
        assertEquals(android.content.Intent.ACTION_SEND, intent.action)
        assertTrue(intent.hasExtra(android.content.Intent.EXTRA_STREAM))
        assertEquals("ক্যাশ মেমো #TEST-১", intent.getStringExtra(android.content.Intent.EXTRA_SUBJECT))
    }

    @Test
    fun testPdfDateTimeAndDateOnlyFormattingBengali() {
        val cal = java.util.Calendar.getInstance()
        cal.set(2026, java.util.Calendar.MARCH, 15, 14, 30, 0)
        val timeMs = cal.timeInMillis

        val fullDateTimeBn = PdfInvoiceEngine.formatPdfDateTimeBn(timeMs)
        // Must have Bengali month name
        assertTrue("Must contain Bengali month মার্চ: $fullDateTimeBn", fullDateTimeBn.contains("মার্চ"))
        // Must have Bengali numerals for year 2026
        assertTrue("Must contain Bengali numerals ২০২৬: $fullDateTimeBn", fullDateTimeBn.contains("২০২৬"))
        // Must have Bengali numerals for day 15
        assertTrue("Must contain Bengali numerals ১৫: $fullDateTimeBn", fullDateTimeBn.contains("১৫"))
        // Must have অপরাহ্ন for PM
        assertTrue("Must contain অপরাহ্ন for PM: $fullDateTimeBn", fullDateTimeBn.contains("অপরাহ্ন"))

        val dateOnlyBn = PdfInvoiceEngine.formatPdfDateOnlyBn(timeMs)
        assertTrue("Must contain মার্চ in date only: $dateOnlyBn", dateOnlyBn.contains("মার্চ"))
        assertTrue("Must contain ২০২৬ in date only: $dateOnlyBn", dateOnlyBn.contains("২০২৬"))
    }

    @Test
    fun testBengaliLabelsAndNumeralsInHeaderAndMetadata() {
        val canvas = RecordingCanvas()
        val textPaint = android.graphics.Paint()
        val linePaint = android.graphics.Paint()
        val fillPaint = android.graphics.Paint()

        val invoice = sampleInvoice.copy(
            orderId = "ORD-12345",
            customerInfo = "Kamal",
            operatorName = "Rahim",
            isPaid = false,
            roundedTotalBdt = 1000L,
            paidAmountBdt = 400L
        )

        PdfInvoiceEngine.drawPage1Header(
            canvas = canvas,
            company = sampleHeader,
            invoice = invoice,
            logoBitmap = null,
            textPaint = textPaint,
            linePaint = linePaint,
            fillPaint = fillPaint
        )

        val allTexts = canvas.drawnTexts.joinToString(" | ")

        // 1. Fixed labels must be purely Bengali (no bilingual duplicate slashes like "INVOICE / ক্যাশ মেমো")
        assertTrue("Title must be ক্যাশ মেমো", canvas.drawnTexts.contains("ক্যাশ মেমো"))
        assertTrue("Order ID label must be অর্ডার নম্বর:", canvas.drawnTexts.contains("অর্ডার নম্বর:"))
        assertTrue("Status badge must be ● বকেয়া", canvas.drawnTexts.contains("● বকেয়া"))

        // 2. Transliteration and Bengali numerals
        assertTrue("Order ID must have Bengali numerals #ORD-১২৩৪৫", canvas.drawnTexts.contains("#ORD-১২৩৪৫"))
        assertTrue("Customer Kamal must be transliterated to কামাল", allTexts.contains("গ্রাহক: কামাল"))
        assertTrue("Operator Rahim must be transliterated to রহিম", allTexts.contains("অপারেটর: রহিম"))
        assertTrue("Already-Bengali company name must be preserved", canvas.drawnTexts.contains(sampleHeader.companyName))
        assertTrue("Branch must be in Bengali", allTexts.contains("প্রধান শাখা"))
    }

    @Test
    fun testTableHeaderAndTableRowBengaliRendering() {
        val canvas = RecordingCanvas()
        val textPaint = android.graphics.Paint()
        val linePaint = android.graphics.Paint()
        val fillPaint = android.graphics.Paint()

        PdfInvoiceEngine.drawTableHeader(canvas, textPaint, fillPaint, 200f)

        // Verify Bengali table headers
        assertTrue("Header must contain নং", canvas.drawnTexts.contains("নং"))
        assertTrue("Header must contain বিবরণ", canvas.drawnTexts.contains("বিবরণ"))
        assertTrue("Header must contain দর (৳)", canvas.drawnTexts.contains("দর (৳)"))
        assertTrue("Header must contain পরিমাণ", canvas.drawnTexts.contains("পরিমাণ"))
        assertTrue("Header must contain মোট (৳)", canvas.drawnTexts.contains("মোট (৳)"))

        val rowLine = InvoiceLineEntity(
            orderId = "ORD-123",
            itemId = "ITM-1",
            itemName = "Diesel Fuel",
            itemType = "Liquid",
            unit = "Ltr",
            quantityStr = "10.5",
            unitPricePaisa = 10500L, // 105.00
            lineTotalPaisa = 110250L // 1102.50
        )

        PdfInvoiceEngine.drawTableRow(canvas, 1, rowLine, 230f, false, textPaint, fillPaint, linePaint)

        val rowTexts = canvas.drawnTexts.joinToString(" | ")
        // SL in Bengali
        assertTrue("SL must be ১", canvas.drawnTexts.contains("১"))
        // Item transliterated: Diesel Fuel -> ডিজেল ফুয়েল
        assertTrue("Item must be transliterated: $rowTexts", rowTexts.contains("ডিজেল"))
        // Quantity & Unit: 10.5 Ltr -> ১০.৫ লি.
        assertTrue("Quantity must be ১০.৫ লি.: $rowTexts", rowTexts.contains("১০.৫ লি."))
        // Unit rate: 105.00 -> ১০৫.০০
        assertTrue("Rate must be in Bengali numerals: $rowTexts", rowTexts.contains("১০৫.০০"))
        // Line total: 1102.50 -> ১১০২.৫০
        assertTrue("Total must be in Bengali numerals: $rowTexts", rowTexts.contains("১১০২.৫০"))
    }

    @Test
    fun testPdfQuantityUnitFormattingRules() {
        assertEquals("লি.", PdfInvoiceEngine.localizeUnit("Liter"))
        assertEquals("লি.", PdfInvoiceEngine.localizeUnit("L"))
        assertEquals("টি", PdfInvoiceEngine.localizeUnit("Pcs"))
        assertEquals("টি", PdfInvoiceEngine.localizeUnit("Quantity"))
        assertEquals("", PdfInvoiceEngine.localizeUnit("Service"))
        assertEquals("", PdfInvoiceEngine.localizeUnit("Quantity", itemType = "Service"))
        assertEquals("ঘনফুট", PdfInvoiceEngine.localizeUnit("CFT"))
        assertEquals("ঘনমিটার", PdfInvoiceEngine.localizeUnit("m³"))
        assertEquals("কেজি", PdfInvoiceEngine.localizeUnit("kg"))
    }

    @Test
    fun testFinancialSummaryScenario1_UnpaidInvoice() {
        // Scenario 1: Total ৳১০০০, Paid ৳০, Due ৳১০০০
        val canvas = RecordingCanvas()
        val textPaint = android.graphics.Paint()
        val linePaint = android.graphics.Paint()
        val fillPaint = android.graphics.Paint()

        val unpaidInvoice = sampleInvoice.copy(
            orderId = "ORD-UNPAID",
            rawTotalPaisa = 100000L,
            roundedTotalBdt = 1000L,
            paidAmountBdt = 0L,
            isPaid = false
        )

        PdfInvoiceEngine.drawInvoiceSummary(canvas, unpaidInvoice, 300f, textPaint, fillPaint, linePaint)

        val summary = canvas.drawnTexts.joinToString(" | ")
        assertTrue("Must contain উপ-মোট:", summary.contains("উপ-মোট:"))
        assertTrue("Subtotal must be ৳ ১০০০.০০", summary.contains("৳ ১০০০.০০"))
        assertTrue("Must contain সর্বমোট প্রদেয়:", summary.contains("সর্বমোট প্রদেয়:"))
        assertTrue("Grand Total must be ৳ ১০০০.০০", summary.contains("৳ ১০০০.০০"))
        assertTrue("Must contain জমা (পরিশোধিত):", summary.contains("জমা (পরিশোধিত):"))
        assertTrue("Paid must be ৳ ০.০০", summary.contains("৳ ০.০০"))
        assertTrue("Must contain বাকি (বকেয়া):", summary.contains("বাকি (বকেয়া):"))
        assertTrue("Due must be ৳ ১০০০.০০", summary.contains("৳ ১০০০.০০"))
        assertTrue("Stamp must show বকেয়া", summary.contains("বকেয়া ✕ (বাকি ৳ ১০০০.০০)"))
    }

    @Test
    fun testFinancialSummaryScenario2_PartiallyPaidInvoice() {
        // Scenario 2: Total ৳১০০০, Paid ৳৪০০, Due ৳৬০০
        val canvas = RecordingCanvas()
        val textPaint = android.graphics.Paint()
        val linePaint = android.graphics.Paint()
        val fillPaint = android.graphics.Paint()

        val partialInvoice = sampleInvoice.copy(
            orderId = "ORD-PARTIAL",
            rawTotalPaisa = 100000L,
            roundedTotalBdt = 1000L,
            paidAmountBdt = 400L,
            isPaid = false
        )

        PdfInvoiceEngine.drawInvoiceSummary(canvas, partialInvoice, 300f, textPaint, fillPaint, linePaint)

        val summary = canvas.drawnTexts.joinToString(" | ")
        assertTrue("Grand Total must be ৳ ১০০০.০০", summary.contains("৳ ১০০০.০০"))
        assertTrue("Paid must be ৳ ৪০০.০০", summary.contains("৳ ৪০০.০০"))
        assertTrue("Due must be ৳ ৬০০.০০", summary.contains("৳ ৬০০.০০"))
        assertTrue("Stamp must show বকেয়া ৳ ৬০০.০০", summary.contains("বকেয়া ✕ (বাকি ৳ ৬০০.০০)"))
    }

    @Test
    fun testFinancialSummaryScenario3_FullyPaidInvoice() {
        // Scenario 3: Total ৳৪৫০, Paid ৳৪৫০, Due ৳০
        val canvas = RecordingCanvas()
        val textPaint = android.graphics.Paint()
        val linePaint = android.graphics.Paint()
        val fillPaint = android.graphics.Paint()

        val fullPaidInvoice = sampleInvoice.copy(
            orderId = "ORD-FULLPAID",
            rawTotalPaisa = 45000L,
            roundedTotalBdt = 450L,
            paidAmountBdt = 450L,
            isPaid = true
        )

        PdfInvoiceEngine.drawInvoiceSummary(canvas, fullPaidInvoice, 300f, textPaint, fillPaint, linePaint)

        val summary = canvas.drawnTexts.joinToString(" | ")
        assertTrue("Subtotal must be ৳ ৪৫০.০০", summary.contains("৳ ৪৫০.০০"))
        assertTrue("Grand Total must be ৳ ৪৫০.০০", summary.contains("৳ ৪৫০.০০"))
        assertTrue("Paid must be ৳ ৪৫০.০০", summary.contains("৳ ৪৫০.০০"))
        assertTrue("Due must be ৳ ০.০০", summary.contains("৳ ০.০০"))
        assertTrue("Stamp must show পরিশোধিত ✓", summary.contains("পরিশোধিত ✓"))
    }

    @Test
    fun testLongBengaliProductNameDoesNotCrashOrOverlap() {
        val canvas = RecordingCanvas()
        val textPaint = android.graphics.Paint()
        val linePaint = android.graphics.Paint()
        val fillPaint = android.graphics.Paint()

        val longName = "মেসার্স রাকিব ফিলিং স্টেশন এন্ড অটোমোবাইল সার্ভিসিং সেন্টার প্রিমিয়াম ইঞ্জিন অয়েল ও লুব্রিকেন্ট ফিল্টার প্যাক"
        val line = InvoiceLineEntity(
            orderId = "ORD-LONG",
            itemId = "ITM-99",
            itemName = longName,
            itemType = "Liquid",
            unit = "প্যাক",
            quantityStr = "1",
            unitPricePaisa = 50000L,
            lineTotalPaisa = 50000L
        )

        // Must execute cleanly without exception
        PdfInvoiceEngine.drawTableRow(canvas, 1, line, 100f, false, textPaint, fillPaint, linePaint)
        assertTrue("Canvas must have drawn text", canvas.drawnTexts.isNotEmpty())
    }

    @Test
    fun testContinuationHeaderAndPageFooterBengali() {
        val canvas = RecordingCanvas()
        val textPaint = android.graphics.Paint()
        val linePaint = android.graphics.Paint()

        val invoice = sampleInvoice.copy(orderId = "ORD-9999", dateTime = 1773563400000L)
        PdfInvoiceEngine.drawContinuationHeader(canvas, sampleHeader, invoice, textPaint, linePaint)

        val continuationText = canvas.drawnTexts.joinToString(" | ")
        assertTrue("Continuation header must have ক্যাশ মেমো #ORD-৯৯৯৯ (চলমান): $continuationText", continuationText.contains("ক্যাশ মেমো #ORD-৯৯৯৯ (চলমান)"))
        assertTrue("Continuation header must have তারিখ:", continuationText.contains("তারিখ:"))

        PdfInvoiceEngine.drawPageFooter(canvas, 1, 3, textPaint, linePaint)
        val footerText = canvas.drawnTexts.joinToString(" | ")
        assertTrue("Footer must have কম্পিউটার জেনারেটেড অফলাইন ক্যাশ মেমো: $footerText", footerText.contains("কম্পিউটার জেনারেটেড অফলাইন ক্যাশ মেমো"))
        assertTrue("Footer must have পৃষ্ঠা ১ এর ৩: $footerText", footerText.contains("পৃষ্ঠা ১ এর ৩"))
    }

    @Test
    fun testEntityImmutability() {
        val originalOrderId = sampleInvoice.orderId
        val originalRawTotal = sampleInvoice.rawTotalPaisa
        val originalRoundedTotal = sampleInvoice.roundedTotalBdt
        val originalPaid = sampleInvoice.paidAmountBdt
        val originalCustomer = sampleInvoice.customerInfo
        val originalOperator = sampleInvoice.operatorName

        val lines = createDummyLines(3)
        val originalLine0Name = lines[0].itemName
        val originalLine0Paisa = lines[0].lineTotalPaisa

        PdfInvoiceEngine.generateInvoicePdf(context, sampleHeader, sampleInvoice, lines)

        // Assert that original database entities were 100% untouched
        assertEquals(originalOrderId, sampleInvoice.orderId)
        assertEquals(originalRawTotal, sampleInvoice.rawTotalPaisa)
        assertEquals(originalRoundedTotal, sampleInvoice.roundedTotalBdt)
        assertEquals(originalPaid, sampleInvoice.paidAmountBdt)
        assertEquals(originalCustomer, sampleInvoice.customerInfo)
        assertEquals(originalOperator, sampleInvoice.operatorName)
        assertEquals(originalLine0Name, lines[0].itemName)
        assertEquals(originalLine0Paisa, lines[0].lineTotalPaisa)
    }
}

class RecordingCanvas : Canvas() {
    val drawnTexts = mutableListOf<String>()

    override fun drawText(text: String, x: Float, y: Float, paint: android.graphics.Paint) {
        drawnTexts.add(text)
    }

    override fun drawText(text: CharSequence, start: Int, end: Int, x: Float, y: Float, paint: android.graphics.Paint) {
        drawnTexts.add(text.subSequence(start, end).toString())
    }
}
