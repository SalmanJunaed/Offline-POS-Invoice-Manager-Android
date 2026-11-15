package com.example.pos.modules.pdf

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import com.example.R
import com.example.pos.core.error.PosError
import com.example.pos.core.error.PosResult
import com.example.pos.core.util.BengaliTransliterationEngine
import com.example.pos.core.util.CompanyLogoManager
import com.example.pos.core.util.DateTimeUtils
import com.example.pos.data.db.CompanyHeaderEntity
import com.example.pos.data.db.InvoiceEntity
import com.example.pos.data.db.InvoiceLineEntity
import com.example.pos.domain.FinancialCalculationEngine
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Professional Native Android PDF Invoice Generation Engine.
 * Produces crisp, multi-page vector-quality standard A4 invoice documents 100% offline
 * using native Android PdfDocument without external network services or bloated dependencies.
 * Fully supports Android save and share mechanisms via FileProvider.
 */
object PdfInvoiceEngine {

    const val PAGE_WIDTH = 595 // Standard A4 width in points (72 DPI)
    const val PAGE_HEIGHT = 842 // Standard A4 height in points (72 DPI)

    const val MARGIN_LEFT = 40f
    const val MARGIN_RIGHT = 555f
    const val MARGIN_TOP = 40f
    const val MARGIN_BOTTOM = 800f
    const val FOOTER_Y = 815f

    const val ROW_HEIGHT = 22f
    const val PAGE1_START_Y = 210f
    const val CONTINUATION_START_Y = 90f
    const val MAX_TABLE_Y = 750f
    const val SUMMARY_HEIGHT = 110f

    // Color palette
    private const val COLOR_PRIMARY = 0xFF0F172A.toInt() // Deep Slate 900
    private const val COLOR_SECONDARY = 0xFF334155.toInt() // Slate 700
    private const val COLOR_MUTED = 0xFF64748B.toInt() // Slate 500
    private const val COLOR_BORDER = 0xFFCBD5E1.toInt() // Slate 300
    private const val COLOR_BG_LIGHT = 0xFFF8FAFC.toInt() // Slate 50
    private const val COLOR_BG_ALT_ROW = 0xFFF1F5F9.toInt() // Slate 100
    private const val COLOR_ACCENT_GREEN = 0xFF15803D.toInt() // Green 700

    /**
     * Calculates the exact total page count based on line item count and layout constraints.
     */
    fun calculatePageCount(linesCount: Int): Int {
        var page = 1
        var currentY = PAGE1_START_Y

        for (i in 0 until linesCount) {
            if (currentY + ROW_HEIGHT > MAX_TABLE_Y) {
                page++
                currentY = CONTINUATION_START_Y
            }
            currentY += ROW_HEIGHT
        }

        if (currentY + SUMMARY_HEIGHT > MAX_TABLE_Y) {
            page++
        }
        return page
    }

    /**
     * Generates a complete, multi-page, professional PDF invoice document offline.
     * Saves the resulting PDF to cacheDir/exports/Invoice_<orderId>.pdf.
     */
    fun generateInvoicePdf(
        context: Context,
        company: CompanyHeaderEntity?,
        invoice: InvoiceEntity,
        lines: List<InvoiceLineEntity>
    ): PosResult<File> {
        return try {
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val outputFile = File(exportDir, "Invoice_${invoice.orderId}.pdf")

            FileOutputStream(outputFile).use { out ->
                val writeResult = writeInvoicePdfToStream(context, company, invoice, lines, out)
                if (writeResult is PosResult.Error) {
                    return writeResult
                }
            }

            PosResult.Success(outputFile)
        } catch (e: Exception) {
            PosResult.Error(PosError.UnexpectedError("Failed to generate invoice PDF", e))
        }
    }

    /**
     * Generates the PDF document directly into any writable OutputStream (e.g. Storage Access Framework Uri).
     */
    fun writeInvoicePdfToStream(
        context: Context,
        company: CompanyHeaderEntity?,
        invoice: InvoiceEntity,
        lines: List<InvoiceLineEntity>,
        outputStream: OutputStream
    ): PosResult<Unit> {
        return try {
            val totalPages = calculatePageCount(lines.size)
            val pdfDoc = PdfDocument()

            // Paint objects
            val textPaint = Paint().apply {
                isAntiAlias = true
                color = COLOR_PRIMARY
            }
            val linePaint = Paint().apply {
                isAntiAlias = true
                color = COLOR_BORDER
                strokeWidth = 1f
            }
            val fillPaint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.FILL
            }

            // Attempt to load company logo bitmap
            val logoBitmap = loadLogoBitmap(context, company?.logoUri)

            var currentPageNum = 1
            var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNum).create()
            var page = pdfDoc.startPage(pageInfo)
            var canvas = page.canvas

            // Draw Page 1 Full Header & Metadata
            drawPage1Header(canvas, company, invoice, logoBitmap, textPaint, linePaint, fillPaint)
            drawTableHeader(canvas, textPaint, fillPaint, PAGE1_START_Y - 24f)

            var currentY = PAGE1_START_Y
            var itemIndex = 0

            while (itemIndex < lines.size) {
                if (currentY + ROW_HEIGHT > MAX_TABLE_Y) {
                    // Draw bottom footer on current page
                    drawPageFooter(canvas, currentPageNum, totalPages, textPaint, linePaint)
                    pdfDoc.finishPage(page)

                    // Start next page
                    currentPageNum++
                    pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNum).create()
                    page = pdfDoc.startPage(pageInfo)
                    canvas = page.canvas

                    // Draw continuation header
                    drawContinuationHeader(canvas, company, invoice, textPaint, linePaint)
                    drawTableHeader(canvas, textPaint, fillPaint, CONTINUATION_START_Y - 24f)
                    currentY = CONTINUATION_START_Y
                }

                val line = lines[itemIndex]
                val isAltRow = (itemIndex % 2 == 1)
                drawTableRow(canvas, itemIndex + 1, line, currentY, isAltRow, textPaint, fillPaint, linePaint)

                currentY += ROW_HEIGHT
                itemIndex++
            }

            // Check if Summary and Footer Notes fit on current page
            if (currentY + SUMMARY_HEIGHT > MAX_TABLE_Y) {
                drawPageFooter(canvas, currentPageNum, totalPages, textPaint, linePaint)
                pdfDoc.finishPage(page)

                currentPageNum++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNum).create()
                page = pdfDoc.startPage(pageInfo)
                canvas = page.canvas

                drawContinuationHeader(canvas, company, invoice, textPaint, linePaint)
                currentY = CONTINUATION_START_Y
            }

            // Draw Summary and Footer notes
            drawInvoiceSummary(canvas, invoice, currentY, textPaint, fillPaint, linePaint)

            // Draw bottom page footer on final page
            drawPageFooter(canvas, currentPageNum, totalPages, textPaint, linePaint)
            pdfDoc.finishPage(page)

            pdfDoc.writeTo(outputStream)
            pdfDoc.close()

            PosResult.Success(Unit)
        } catch (e: Exception) {
            PosResult.Error(PosError.UnexpectedError("Failed to write invoice PDF to stream", e))
        }
    }

    /**
     * Exports the invoice PDF to a user-selected Storage Access Framework Uri.
     */
    fun saveInvoicePdfToUri(
        context: Context,
        uri: Uri,
        company: CompanyHeaderEntity?,
        invoice: InvoiceEntity,
        lines: List<InvoiceLineEntity>
    ): PosResult<Unit> {
        return try {
            val outputStream = context.contentResolver.openOutputStream(uri)
                ?: return PosResult.Error(PosError.UnexpectedError("Unable to open output stream for destination Uri"))
            outputStream.use { out ->
                writeInvoicePdfToStream(context, company, invoice, lines, out)
            }
        } catch (e: Exception) {
            PosResult.Error(PosError.UnexpectedError("Failed to save PDF to selected location", e))
        }
    }

    /**
     * Constructs a system Intent to share the PDF via Android Share Sheet.
     */
    fun getSharePdfIntent(context: Context, pdfFile: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            pdfFile
        )
        val rawOrderId = pdfFile.nameWithoutExtension.removePrefix("Invoice_")
        val orderBn = DateTimeUtils.toBengaliNumerals(rawOrderId)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "ক্যাশ মেমো #$orderBn")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    internal fun formatPdfDateTimeBn(timeMillis: Long): String {
        val rawFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US)
        val formatted = rawFormat.format(Date(timeMillis))
        var s = formatted
        val monthMap = listOf(
            "January" to "জানুয়ারি", "February" to "ফেব্রুয়ারি", "March" to "মার্চ",
            "April" to "এপ্রিল", "May" to "মে", "June" to "জুন",
            "July" to "জুলাই", "August" to "আগস্ট", "September" to "সেপ্টেম্বর",
            "October" to "অক্টোবর", "November" to "নভেম্বর", "December" to "ডিসেম্বর",
            "Jan" to "জানুয়ারি", "Feb" to "ফেব্রুয়ারি", "Mar" to "মার্চ", "Apr" to "এপ্রিল",
            "May" to "মে", "Jun" to "জুন", "Jul" to "জুলাই", "Aug" to "আগস্ট",
            "Sep" to "সেপ্টেম্বর", "Oct" to "অক্টোবর", "Nov" to "নভেম্বর", "Dec" to "ডিসেম্বর"
        )
        for ((enM, bnM) in monthMap) {
            s = s.replace(enM, bnM)
        }
        s = s.replace("AM", "পূর্বাহ্ন").replace("PM", "অপরাহ্ন")
            .replace("am", "পূর্বাহ্ন").replace("pm", "অপরাহ্ন")
        return DateTimeUtils.toBengaliNumerals(s)
    }

    internal fun formatPdfDateOnlyBn(timeMillis: Long): String {
        val rawFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)
        val formatted = rawFormat.format(Date(timeMillis))
        var s = formatted
        val monthMap = listOf(
            "January" to "জানুয়ারি", "February" to "ফেব্রুয়ারি", "March" to "মার্চ",
            "April" to "এপ্রিল", "May" to "মে", "June" to "জুন",
            "July" to "জুলাই", "August" to "আগস্ট", "September" to "সেপ্টেম্বর",
            "October" to "অক্টোবর", "November" to "নভেম্বর", "December" to "ডিসেম্বর",
            "Jan" to "জানুয়ারি", "Feb" to "ফেব্রুয়ারি", "Mar" to "মার্চ", "Apr" to "এপ্রিল",
            "May" to "মে", "Jun" to "জুন", "Jul" to "জুলাই", "Aug" to "আগস্ট",
            "Sep" to "সেপ্টেম্বর", "Oct" to "অক্টোবর", "Nov" to "নভেম্বর", "Dec" to "ডিসেম্বর"
        )
        for ((enM, bnM) in monthMap) {
            s = s.replace(enM, bnM)
        }
        return DateTimeUtils.toBengaliNumerals(s)
    }

    internal fun localizeUnit(unit: String, itemType: String? = null): String {
        return com.example.pos.ui.screens.localizeUnit(unit, itemType)
    }

    /**
     * Constructs a system Intent to view / open the PDF with any installed PDF viewer app.
     */
    fun getViewPdfIntent(context: Context, pdfFile: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            pdfFile
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    // -------------------------------------------------------------------------
    // Internal Drawing Routines
    // -------------------------------------------------------------------------

    private fun loadLogoBitmap(context: Context, logoUri: String?): Bitmap? {
        return try {
            CompanyLogoManager.loadScaledBitmap(context, logoUri, 180, 180)
                ?: run {
                    // Fallback to built-in logo if available
                    BitmapFactory.decodeResource(context.resources, R.drawable.logo_original)
                }
        } catch (_: Exception) {
            null
        }
    }

    internal fun drawPage1Header(
        canvas: Canvas,
        company: CompanyHeaderEntity?,
        invoice: InvoiceEntity,
        logoBitmap: Bitmap?,
        textPaint: Paint,
        linePaint: Paint,
        fillPaint: Paint
    ) {
        val logoLeft = MARGIN_LEFT
        val logoTop = MARGIN_TOP
        val logoWidth = 70f
        val logoHeight = 46f

        // 1. Logo area
        val logoRect = RectF(logoLeft, logoTop, logoLeft + logoWidth, logoTop + logoHeight)
        if (logoBitmap != null) {
            val srcRect = Rect(0, 0, logoBitmap.width, logoBitmap.height)
            canvas.drawBitmap(logoBitmap, srcRect, logoRect, null)
        } else {
            // Stylized reserved placeholder badge without literal LOGO text
            fillPaint.color = COLOR_BG_ALT_ROW
            canvas.drawRoundRect(logoRect, 4f, 4f, fillPaint)
            linePaint.color = COLOR_BORDER
            linePaint.style = Paint.Style.STROKE
            canvas.drawRoundRect(logoRect, 4f, 4f, linePaint)
            linePaint.style = Paint.Style.FILL
        }

        // 2. Company Details (beside logo)
        val detailsLeft = logoLeft + logoWidth + 14f
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = COLOR_PRIMARY
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = 15f

        val compRaw = company?.companyName?.ifBlank { "পেট্রোল পাম্প ও সার্ভিস স্টেশন" }
            ?: "পেট্রোল পাম্প ও সার্ভিস স্টেশন"
        val compName = if (compRaw == "PETROL PUMP & SERVICE STATION") {
            "পেট্রোল পাম্প ও সার্ভিস স্টেশন"
        } else {
            BengaliTransliterationEngine.transliterate(compRaw)
        }
        canvas.drawText(compName, detailsLeft, logoTop + 14f, textPaint)

        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textPaint.textSize = 9.5f
        textPaint.color = COLOR_SECONDARY

        val branchText = if (!company?.branchName.isNullOrBlank()) {
            val bName = BengaliTransliterationEngine.transliterate(company.branchName.trim())
            if (!company.branchNumber.isNullOrBlank()) {
                val bNum = DateTimeUtils.toBengaliNumerals(company.branchNumber.trim())
                "$bName (শাখা #$bNum)"
            } else {
                bName
            }
        } else if (!company?.branchNumber.isNullOrBlank()) {
            val bNum = DateTimeUtils.toBengaliNumerals(company.branchNumber.trim())
            "শাখা #$bNum"
        } else {
            "প্রধান শাখা"
        }
        canvas.drawText(branchText, detailsLeft, logoTop + 27f, textPaint)

        val addressRaw = company?.address?.ifBlank { "ঢাকা, বাংলাদেশ" } ?: "ঢাকা, বাংলাদেশ"
        val addressText = if (addressRaw == "Dhaka, Bangladesh") "ঢাকা, বাংলাদেশ" else BengaliTransliterationEngine.transliterate(addressRaw)
        canvas.drawText(addressText, detailsLeft, logoTop + 38f, textPaint)

        val phoneRaw = company?.phoneNumber?.ifBlank { "০১৭০০০০০০০০" } ?: "০১৭০০০০০০০০"
        val cleanPhone = phoneRaw.replace("Phone", "").replace("ফোন", "").replace(":", "").trim()
        val phoneText = "ফোন: ${DateTimeUtils.toBengaliNumerals(cleanPhone)}"
        canvas.drawText(phoneText, detailsLeft, logoTop + 49f, textPaint)

        // 3. Invoice Title Badge (Top-Right)
        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = 16f
        textPaint.color = COLOR_PRIMARY
        canvas.drawText("ক্যাশ মেমো", MARGIN_RIGHT, logoTop + 16f, textPaint)

        textPaint.textSize = 10f
        textPaint.color = COLOR_MUTED
        val dateStr = formatPdfDateTimeBn(invoice.dateTime)
        canvas.drawText("তারিখ: $dateStr", MARGIN_RIGHT, logoTop + 32f, textPaint)

        val isPaid = invoice.isPaid || (invoice.roundedTotalBdt - invoice.paidAmountBdt <= 0L)
        if (isPaid) {
            textPaint.color = COLOR_ACCENT_GREEN
            canvas.drawText("● পরিশোধিত", MARGIN_RIGHT, logoTop + 46f, textPaint)
        } else {
            textPaint.color = Color.rgb(198, 40, 40)
            canvas.drawText("● বকেয়া", MARGIN_RIGHT, logoTop + 46f, textPaint)
        }

        // 4. Header Divider
        linePaint.color = COLOR_BORDER
        linePaint.strokeWidth = 1.2f
        canvas.drawLine(MARGIN_LEFT, 102f, MARGIN_RIGHT, 102f, linePaint)

        // 5. Invoice Metadata Card (Order ID, Customer, Operator)
        val metaTop = 112f
        val metaBottom = 175f
        val metaRect = RectF(MARGIN_LEFT, metaTop, MARGIN_RIGHT, metaBottom)
        fillPaint.color = COLOR_BG_LIGHT
        canvas.drawRoundRect(metaRect, 6f, 6f, fillPaint)
        linePaint.color = COLOR_BORDER
        linePaint.style = Paint.Style.STROKE
        canvas.drawRoundRect(metaRect, 6f, 6f, linePaint)
        linePaint.style = Paint.Style.FILL

        // Metadata Left Column
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = COLOR_SECONDARY
        textPaint.textSize = 9.5f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("অর্ডার নম্বর:", MARGIN_LEFT + 12f, metaTop + 18f, textPaint)
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.color = COLOR_PRIMARY
        textPaint.textSize = 11f
        canvas.drawText("#${DateTimeUtils.toBengaliNumerals(invoice.orderId)}", MARGIN_LEFT + 12f, metaTop + 34f, textPaint)

        textPaint.textSize = 9.5f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textPaint.color = COLOR_SECONDARY
        val custRaw = invoice.customerInfo.trim()
        val custText = if (custRaw.isNotBlank()) BengaliTransliterationEngine.transliterate(custRaw) else "ওয়াক-ইন কাস্টমার"
        val custBn = DateTimeUtils.toBengaliNumerals(custText)
        canvas.drawText("গ্রাহক: $custBn", MARGIN_LEFT + 12f, metaTop + 50f, textPaint)

        // Metadata Right Column
        val col2X = 350f
        val opRaw = invoice.operatorName.trim()
        val opText = if (opRaw.isNotBlank()) BengaliTransliterationEngine.transliterate(opRaw) else "অপারেটর"
        canvas.drawText("অপারেটর: $opText", col2X, metaTop + 18f, textPaint)
        val statusText = if (isPaid) "বিল স্ট্যাটাস: পরিশোধিত (নগদ)" else "বিল স্ট্যাটাস: বকেয়া"
        canvas.drawText(statusText, col2X, metaTop + 50f, textPaint)
    }

    internal fun drawContinuationHeader(
        canvas: Canvas,
        company: CompanyHeaderEntity?,
        invoice: InvoiceEntity,
        textPaint: Paint,
        linePaint: Paint
    ) {
        val headerY = MARGIN_TOP + 12f

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = 11f
        textPaint.color = COLOR_PRIMARY
        val compRaw = company?.companyName?.ifBlank { "পেট্রোল পাম্প ও সার্ভিস স্টেশন" }
            ?: "পেট্রোল পাম্প ও সার্ভিস স্টেশন"
        val compName = if (compRaw == "PETROL PUMP & SERVICE STATION") {
            "পেট্রোল পাম্প ও সার্ভিস স্টেশন"
        } else {
            BengaliTransliterationEngine.transliterate(compRaw)
        }
        canvas.drawText(compName, MARGIN_LEFT, headerY, textPaint)

        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textPaint.textSize = 9.5f
        textPaint.color = COLOR_MUTED
        val orderBn = DateTimeUtils.toBengaliNumerals(invoice.orderId)
        canvas.drawText("— ক্যাশ মেমো #$orderBn (চলমান)", MARGIN_LEFT + textPaint.measureText(compName) + 8f, headerY, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        val dateStr = formatPdfDateOnlyBn(invoice.dateTime)
        canvas.drawText("তারিখ: $dateStr", MARGIN_RIGHT, headerY, textPaint)

        linePaint.color = COLOR_BORDER
        linePaint.strokeWidth = 1f
        canvas.drawLine(MARGIN_LEFT, headerY + 8f, MARGIN_RIGHT, headerY + 8f, linePaint)
    }

    internal fun drawTableHeader(canvas: Canvas, textPaint: Paint, fillPaint: Paint, yTop: Float) {
        val height = 22f
        val rect = RectF(MARGIN_LEFT, yTop, MARGIN_RIGHT, yTop + height)

        fillPaint.color = COLOR_SECONDARY
        canvas.drawRoundRect(rect, 4f, 4f, fillPaint)

        textPaint.color = Color.WHITE
        textPaint.textSize = 9f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

        val textY = yTop + 14.5f
        // SL
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("নং", MARGIN_LEFT + 15f, textY, textPaint)

        // Item / Description
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("বিবরণ", MARGIN_LEFT + 36f, textY, textPaint)

        // Rate
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("দর (৳)", 340f, textY, textPaint)

        // Qty
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("পরিমাণ", 430f, textY, textPaint)

        // Total
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("মোট (৳)", MARGIN_RIGHT - 10f, textY, textPaint)
    }

    internal fun drawTableRow(
        canvas: Canvas,
        sl: Int,
        line: InvoiceLineEntity,
        yTop: Float,
        isAltRow: Boolean,
        textPaint: Paint,
        fillPaint: Paint,
        linePaint: Paint
    ) {
        val rect = RectF(MARGIN_LEFT, yTop, MARGIN_RIGHT, yTop + ROW_HEIGHT)

        // Alternate row background
        if (isAltRow) {
            fillPaint.color = COLOR_BG_ALT_ROW
            canvas.drawRect(rect, fillPaint)
        }

        // Bottom row divider
        linePaint.color = COLOR_BORDER
        linePaint.strokeWidth = 0.5f
        canvas.drawLine(MARGIN_LEFT, yTop + ROW_HEIGHT, MARGIN_RIGHT, yTop + ROW_HEIGHT, linePaint)

        val textY = yTop + 15f
        textPaint.textSize = 9f
        textPaint.color = COLOR_PRIMARY
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

        // 1. SL
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = COLOR_MUTED
        canvas.drawText(DateTimeUtils.toBengaliNumerals(sl.toString()), MARGIN_LEFT + 15f, textY, textPaint)

        // 2. Item Name (transliterated and ellipsized safely to prevent overlapping Rate column)
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = COLOR_PRIMARY
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val transliterated = BengaliTransliterationEngine.transliterate(line.itemName)
        val itemName = TextUtils.ellipsize(transliterated, TextPaint(textPaint), 245f, TextUtils.TruncateAt.END).toString()
        canvas.drawText(DateTimeUtils.toBengaliNumerals(itemName), MARGIN_LEFT + 36f, textY, textPaint)

        // 3. Unit Rate
        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        val rateFormatted = DateTimeUtils.toBengaliNumerals(FinancialCalculationEngine.formatPaisaToBdt(line.unitPricePaisa))
        canvas.drawText(rateFormatted, 340f, textY, textPaint)

        // 4. Quantity & Unit
        textPaint.textAlign = Paint.Align.RIGHT
        val unitBn = localizeUnit(line.unit, line.itemType)
        val qtyBn = DateTimeUtils.toBengaliNumerals(line.quantityStr)
        val qtyText = if (unitBn.isNotBlank()) "$qtyBn $unitBn" else qtyBn
        canvas.drawText(qtyText, 430f, textY, textPaint)

        // 5. Total
        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val totalFormatted = DateTimeUtils.toBengaliNumerals(FinancialCalculationEngine.formatPaisaToBdt(line.lineTotalPaisa))
        canvas.drawText(totalFormatted, MARGIN_RIGHT - 10f, textY, textPaint)
    }

    internal fun drawInvoiceSummary(
        canvas: Canvas,
        invoice: InvoiceEntity,
        yStart: Float,
        textPaint: Paint,
        fillPaint: Paint,
        linePaint: Paint
    ) {
        val dividerY = yStart + 8f
        linePaint.color = COLOR_BORDER
        linePaint.strokeWidth = 1f
        canvas.drawLine(MARGIN_LEFT, dividerY, MARGIN_RIGHT, dividerY, linePaint)

        val summaryTop = dividerY + 10f

        // Footer Note Box (Left Side)
        val noteWidth = 270f
        val noteHeight = 90f
        val noteRect = RectF(MARGIN_LEFT, summaryTop, MARGIN_LEFT + noteWidth, summaryTop + noteHeight)

        fillPaint.color = COLOR_BG_LIGHT
        canvas.drawRoundRect(noteRect, 4f, 4f, fillPaint)
        linePaint.color = COLOR_BORDER
        linePaint.style = Paint.Style.STROKE
        canvas.drawRoundRect(noteRect, 4f, 4f, linePaint)
        linePaint.style = Paint.Style.FILL

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = COLOR_MUTED
        textPaint.textSize = 8.5f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("মন্তব্য ও শর্তাবলী:", MARGIN_LEFT + 8f, summaryTop + 14f, textPaint)

        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textPaint.textSize = 8.5f
        textPaint.color = COLOR_SECONDARY

        val note = if (invoice.footerNote.isNotBlank()) {
            BengaliTransliterationEngine.transliterate(invoice.footerNote)
        } else {
            "আমাদের সাথে থাকার জন্য ধন্যবাদ! আবার আসবেন।"
        }

        var noteLineY = summaryTop + 27f
        note.lines().take(4).forEach { lineText ->
            canvas.drawText(lineText.take(45), MARGIN_LEFT + 8f, noteLineY, textPaint)
            noteLineY += 13f
        }

        // Totals Card (Right Side)
        val totalsLeft = 325f
        val totalsRight = MARGIN_RIGHT
        val contentRight = MARGIN_RIGHT - 8f

        val rawPaisa = invoice.rawTotalPaisa
        val roundedPaisa = invoice.roundedTotalBdt * 100L
        val diffPaisa = roundedPaisa - rawPaisa
        val hasRounding = diffPaisa != 0L

        val subtotalFormatted = "৳ ${DateTimeUtils.toBengaliNumerals(FinancialCalculationEngine.formatPaisaToBdt(rawPaisa))}"
        val paidAmount = invoice.paidAmountBdt
        val dueAmount = maxOf(0L, invoice.roundedTotalBdt - paidAmount)
        val isFullPaid = invoice.isPaid || dueAmount == 0L

        val paidFormatted = "৳ ${DateTimeUtils.toBengaliNumerals(paidAmount.toString())}.০০"
        val dueFormatted = "৳ ${DateTimeUtils.toBengaliNumerals(dueAmount.toString())}.০০"
        val grandTotalFormatted = "৳ ${DateTimeUtils.toBengaliNumerals(invoice.roundedTotalBdt.toString())}.০০"

        if (hasRounding) {
            // Row 1: Subtotal
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.textSize = 9f
            textPaint.color = COLOR_SECONDARY
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("উপ-মোট:", totalsLeft + 4f, summaryTop + 11f, textPaint)

            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textPaint.color = COLOR_PRIMARY
            canvas.drawText(subtotalFormatted, contentRight, summaryTop + 11f, textPaint)

            // Row 2: Rounding Adjustment
            val sign = if (diffPaisa > 0) "+" else "-"
            val absDiffBdt = String.format(Locale.US, "%.2f", kotlin.math.abs(diffPaisa) / 100.0)
            val roundingFormatted = "$sign৳ ${DateTimeUtils.toBengaliNumerals(absDiffBdt)}"

            textPaint.textAlign = Paint.Align.LEFT
            textPaint.textSize = 8.5f
            textPaint.color = COLOR_MUTED
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("রাউন্ডিং সমন্বয়:", totalsLeft + 4f, summaryTop + 23f, textPaint)

            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(roundingFormatted, contentRight, summaryTop + 23f, textPaint)

            // Grand Total Card Box
            val roundedTop = summaryTop + 28f
            val roundedRect = RectF(totalsLeft, roundedTop, totalsRight, roundedTop + 22f)
            fillPaint.color = COLOR_PRIMARY
            canvas.drawRoundRect(roundedRect, 4f, 4f, fillPaint)

            textPaint.textAlign = Paint.Align.LEFT
            textPaint.color = Color.WHITE
            textPaint.textSize = 9.5f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("সর্বমোট প্রদেয়:", totalsLeft + 8f, roundedTop + 15f, textPaint)

            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.textSize = 11.5f
            canvas.drawText(grandTotalFormatted, contentRight, roundedTop + 16f, textPaint)

            // Row 4: Paid
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.textSize = 9f
            textPaint.color = COLOR_SECONDARY
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("জমা (পরিশোধিত):", totalsLeft + 4f, summaryTop + 62f, textPaint)

            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textPaint.color = COLOR_ACCENT_GREEN
            canvas.drawText(paidFormatted, contentRight, summaryTop + 62f, textPaint)

            // Row 5: Due
            val dueColor = if (dueAmount > 0) Color.rgb(198, 40, 40) else COLOR_MUTED
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.color = COLOR_SECONDARY
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("বাকি (বকেয়া):", totalsLeft + 4f, summaryTop + 74f, textPaint)

            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textPaint.color = dueColor
            canvas.drawText(dueFormatted, contentRight, summaryTop + 74f, textPaint)

            // Row 6: Status Stamp Badge
            val stampTop = summaryTop + 81f
            val stampHeight = 20f
            val stampRect = RectF(totalsLeft, stampTop, totalsRight, stampTop + stampHeight)

            if (isFullPaid) {
                fillPaint.color = Color.rgb(232, 245, 233)
                canvas.drawRoundRect(stampRect, 4f, 4f, fillPaint)
                linePaint.color = COLOR_ACCENT_GREEN
                linePaint.style = Paint.Style.STROKE
                linePaint.strokeWidth = 1f
                canvas.drawRoundRect(stampRect, 4f, 4f, linePaint)
                linePaint.style = Paint.Style.FILL

                textPaint.textAlign = Paint.Align.CENTER
                textPaint.color = COLOR_ACCENT_GREEN
                textPaint.textSize = 9.5f
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("পরিশোধিত ✓", (totalsLeft + totalsRight) / 2f, stampTop + 14f, textPaint)
            } else {
                fillPaint.color = Color.rgb(255, 235, 238)
                canvas.drawRoundRect(stampRect, 4f, 4f, fillPaint)
                linePaint.color = Color.rgb(198, 40, 40)
                linePaint.style = Paint.Style.STROKE
                linePaint.strokeWidth = 1f
                canvas.drawRoundRect(stampRect, 4f, 4f, linePaint)
                linePaint.style = Paint.Style.FILL

                textPaint.textAlign = Paint.Align.CENTER
                textPaint.color = Color.rgb(198, 40, 40)
                textPaint.textSize = 9f
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("বকেয়া ✕ (বাকি $dueFormatted)", (totalsLeft + totalsRight) / 2f, stampTop + 14f, textPaint)
            }
        } else {
            // Row 1: Subtotal
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.textSize = 9.5f
            textPaint.color = COLOR_SECONDARY
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("উপ-মোট:", totalsLeft + 4f, summaryTop + 13f, textPaint)

            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textPaint.color = COLOR_PRIMARY
            canvas.drawText(subtotalFormatted, contentRight, summaryTop + 13f, textPaint)

            // Grand Total Card Box
            val roundedTop = summaryTop + 20f
            val roundedRect = RectF(totalsLeft, roundedTop, totalsRight, roundedTop + 24f)
            fillPaint.color = COLOR_PRIMARY
            canvas.drawRoundRect(roundedRect, 4f, 4f, fillPaint)

            textPaint.textAlign = Paint.Align.LEFT
            textPaint.color = Color.WHITE
            textPaint.textSize = 10f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("সর্বমোট প্রদেয়:", totalsLeft + 8f, roundedTop + 16f, textPaint)

            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.textSize = 12f
            canvas.drawText(grandTotalFormatted, contentRight, roundedTop + 17f, textPaint)

            // Row 3: Paid
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.textSize = 9f
            textPaint.color = COLOR_SECONDARY
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("জমা (পরিশোধিত):", totalsLeft + 4f, summaryTop + 58f, textPaint)

            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textPaint.color = COLOR_ACCENT_GREEN
            canvas.drawText(paidFormatted, contentRight, summaryTop + 58f, textPaint)

            // Row 4: Due
            val dueColor = if (dueAmount > 0) Color.rgb(198, 40, 40) else COLOR_MUTED
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.color = COLOR_SECONDARY
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("বাকি (বকেয়া):", totalsLeft + 4f, summaryTop + 72f, textPaint)

            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textPaint.color = dueColor
            canvas.drawText(dueFormatted, contentRight, summaryTop + 72f, textPaint)

            // Row 5: Status Stamp Badge
            val stampTop = summaryTop + 81f
            val stampHeight = 20f
            val stampRect = RectF(totalsLeft, stampTop, totalsRight, stampTop + stampHeight)

            if (isFullPaid) {
                fillPaint.color = Color.rgb(232, 245, 233)
                canvas.drawRoundRect(stampRect, 4f, 4f, fillPaint)
                linePaint.color = COLOR_ACCENT_GREEN
                linePaint.style = Paint.Style.STROKE
                linePaint.strokeWidth = 1f
                canvas.drawRoundRect(stampRect, 4f, 4f, linePaint)
                linePaint.style = Paint.Style.FILL

                textPaint.textAlign = Paint.Align.CENTER
                textPaint.color = COLOR_ACCENT_GREEN
                textPaint.textSize = 9.5f
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("পরিশোধিত ✓", (totalsLeft + totalsRight) / 2f, stampTop + 14f, textPaint)
            } else {
                fillPaint.color = Color.rgb(255, 235, 238)
                canvas.drawRoundRect(stampRect, 4f, 4f, fillPaint)
                linePaint.color = Color.rgb(198, 40, 40)
                linePaint.style = Paint.Style.STROKE
                linePaint.strokeWidth = 1f
                canvas.drawRoundRect(stampRect, 4f, 4f, linePaint)
                linePaint.style = Paint.Style.FILL

                textPaint.textAlign = Paint.Align.CENTER
                textPaint.color = Color.rgb(198, 40, 40)
                textPaint.textSize = 9f
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("বকেয়া ✕ (বাকি $dueFormatted)", (totalsLeft + totalsRight) / 2f, stampTop + 14f, textPaint)
            }
        }
    }

    internal fun drawPageFooter(
        canvas: Canvas,
        pageNum: Int,
        totalPages: Int,
        textPaint: Paint,
        linePaint: Paint
    ) {
        linePaint.color = COLOR_BORDER
        linePaint.strokeWidth = 0.8f
        canvas.drawLine(MARGIN_LEFT, FOOTER_Y - 14f, MARGIN_RIGHT, FOOTER_Y - 14f, linePaint)

        textPaint.textSize = 8.5f
        textPaint.color = COLOR_MUTED
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

        // Left note
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("কম্পিউটার জেনারেটেড অফলাইন ক্যাশ মেমো • পিওএস ও ইনভেন্টরি সিস্টেম", MARGIN_LEFT, FOOTER_Y, textPaint)

        // Right Page number
        textPaint.textAlign = Paint.Align.RIGHT
        val pBn = DateTimeUtils.toBengaliNumerals(pageNum.toString())
        val tBn = DateTimeUtils.toBengaliNumerals(totalPages.toString())
        canvas.drawText("পৃষ্ঠা $pBn এর $tBn", MARGIN_RIGHT, FOOTER_Y, textPaint)
    }
}
