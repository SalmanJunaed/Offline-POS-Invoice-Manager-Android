package com.example.pos.printer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.pos.data.db.CompanyHeaderEntity
import com.example.pos.data.db.InvoiceEntity
import com.example.pos.data.db.InvoiceLineEntity
import com.example.pos.core.util.BengaliTransliterationEngine
import com.example.pos.core.util.DateTimeUtils
import com.example.pos.domain.FinancialCalculationEngine
import com.example.pos.ui.screens.localizeUnit
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dedicated Thermal Receipt Formatter for 58mm and 80mm ESC/POS Printers.
 * Implements strict PRD visual hierarchy and hardware command sequences.
 *
 * Distinct layouts are generated for 58mm (32 columns) and 80mm (48 columns),
 * with tailored character widths, multi-line wrapping, and column alignments.
 */
object ThermalReceiptFormatter {

    // Paper width constraints in character columns (Font A)
    const val WIDTH_58MM = 32
    const val WIDTH_80MM = 48

    // Dot print widths for standard thermal heads
    const val DOTS_58MM = 384
    const val DOTS_80MM = 576

    // ESC/POS Commands
    val CMD_INIT = byteArrayOf(0x1B, 0x40) // ESC @ (Initialize printer)
    val CMD_ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00) // ESC a 0
    val CMD_ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01) // ESC a 1
    val CMD_ALIGN_RIGHT = byteArrayOf(0x1B, 0x61, 0x02) // ESC a 2

    val CMD_BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01) // ESC E 1
    val CMD_BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00) // ESC E 0

    val CMD_TEXT_NORMAL = byteArrayOf(0x1D, 0x21, 0x00) // GS ! 0x00
    val CMD_TEXT_DOUBLE_HEIGHT = byteArrayOf(0x1D, 0x21, 0x01) // GS ! 0x01
    val CMD_TEXT_DOUBLE_WIDTH = byteArrayOf(0x1D, 0x21, 0x10) // GS ! 0x10
    val CMD_TEXT_DOUBLE_SIZE = byteArrayOf(0x1D, 0x21, 0x11) // GS ! 0x11 (2x width & height)
    val CMD_TEXT_TRIPLE_SIZE = byteArrayOf(0x1D, 0x21, 0x22) // GS ! 0x22 (3x width & height)

    val CMD_FEED_3 = byteArrayOf(0x1B, 0x64, 0x03) // ESC d 3
    val CMD_FEED_4 = byteArrayOf(0x1B, 0x64, 0x04) // ESC d 4
    val CMD_CUT_PARTIAL = byteArrayOf(0x1D, 0x56, 0x42, 0x00) // GS V B 0 (Cut with feed)
    val CMD_CUT_FULL = byteArrayOf(0x1D, 0x56, 0x00) // GS V 0 (Full cut)

    /**
     * Validates that an invoice is ready for printing (non-empty items).
     */
    fun validateInvoice(lines: List<InvoiceLineEntity>): Result<Unit> {
        return if (lines.isEmpty()) {
            Result.failure(IllegalArgumentException("Cannot print empty invoice. Add at least one item."))
        } else {
            Result.success(Unit)
        }
    }

    /**
     * Splits company name by newlines (preserving 3-line headers like
     * মেসার্স রাকিব ফিলিং স্টেশন / এন্ড / এল পি জি অটোগ্যাস ফিলিং স্টেশন).
     * Handles \r\n, \r, \n, and escaped \\n safely without merging lines.
     */
    fun splitCompanyLines(rawName: String?, defaultName: String = "POS STATION"): List<String> {
        val name = rawName?.trim()?.ifBlank { null } ?: defaultName
        val normalized = name
            .replace("\\r\\n", "\n")
            .replace("\\n", "\n")
            .replace("\\r", "\n")
            .replace("\r\n", "\n")
            .replace("\r", "\n")
        val lines = normalized.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        return if (lines.isNotEmpty()) lines else listOf(defaultName)
    }

    /**
     * Formats receipt as plain text adhering to PRD visual hierarchy.
     * Used for preview dialogs, text logs, or plain text sharing.
     */
    /**
     * Helper to detect whether customer info should be omitted from receipt (blank or placeholder).
     */
    fun isCustomerInfoOmitted(info: String?): Boolean {
        if (info.isNullOrBlank()) return true
        val trimmed = info.trim()
        val placeholders = listOf(
            "N/A", "NA", "Walk-in", "Walk-in Customer", "(Walk-in Customer)", "Walk in",
            "01700000000", "01700-000000", "External Customer", "সাধারণ ক্রেতা", "ওয়াক-ইন"
        )
        return placeholders.any { it.equals(trimmed, ignoreCase = true) }
    }

    fun padRightVisual(text: String, targetWidth: Int): String {
        val vWidth = getVisualWidth(text)
        val spaces = maxOf(0, targetWidth - vWidth)
        return text + " ".repeat(spaces)
    }

    fun padLeftVisual(text: String, targetWidth: Int): String {
        val vWidth = getVisualWidth(text)
        val spaces = maxOf(0, targetWidth - vWidth)
        return " ".repeat(spaces) + text
    }

    fun format58mmSummaryLine(label: String, amount: String): String {
        val totalWidth = WIDTH_58MM
        val labelWidth = getVisualWidth(label)
        val amountWidth = getVisualWidth(amount)
        val spaces = maxOf(1, totalWidth - labelWidth - amountWidth)
        return label + " ".repeat(spaces) + amount
    }

    fun format80mmSummaryLine(label: String, amount: String): String {
        val totalWidth = WIDTH_80MM
        val labelWidth = getVisualWidth(label)
        val amountWidth = getVisualWidth(amount)
        val spaces = maxOf(1, totalWidth - labelWidth - amountWidth)
        return label + " ".repeat(spaces) + amount
    }

    /**
     * Resolves consistent (paidBdt, dueBdt, effectiveFullPaid) from authoritative invoice fields
     * and caller's isFullPaid flag without contradicting paidAmountBdt or invoice.isPaid.
     */
    private fun resolvePaymentBreakdown(invoice: InvoiceEntity, isFullPaid: Boolean): Triple<Long, Long, Boolean> {
        val totalBdt = maxOf(0L, invoice.roundedTotalBdt)
        val clampedPaid = invoice.paidAmountBdt.coerceIn(0L, totalBdt)
        val effectiveFullPaid = when {
            totalBdt <= 0L -> true
            clampedPaid >= totalBdt -> true
            !isFullPaid || !invoice.isPaid -> false
            clampedPaid == 0L -> true // Legacy/default InvoiceEntity(isPaid = true, paidAmountBdt = 0) with isFullPaid = true
            else -> false // Partial payment (0 < paid < total) cannot be full paid
        }
        val effectivePaidBdt = if (effectiveFullPaid && clampedPaid == 0L) totalBdt else clampedPaid
        val effectiveDueBdt = if (effectiveFullPaid) 0L else maxOf(0L, totalBdt - effectivePaidBdt)
        return Triple(effectivePaidBdt, effectiveDueBdt, effectiveFullPaid)
    }

    /**
     * Formats receipt as plain text adhering to PRD visual hierarchy.
     * Used for preview dialogs, text logs, or plain text sharing.
     */
    fun generateReceiptText(
        header: CompanyHeaderEntity?,
        invoice: InvoiceEntity,
        lines: List<InvoiceLineEntity>,
        paperSize: String,
        footerLine1: String,
        footerLine2: String,
        isFullPaid: Boolean = true,
        lang: String = "en"
    ): String {
        return if (paperSize.contains("80")) {
            format80mmReceiptText(header, invoice, lines, footerLine1, footerLine2, isFullPaid, lang)
        } else {
            format58mmReceiptText(header, invoice, lines, footerLine1, footerLine2, isFullPaid, lang)
        }
    }

    /**
     * Dedicated 58mm Receipt Layout (32 Character Columns).
     * Tailored for compact mobile/desktop receipt printers.
     */
    fun format58mmReceiptText(
        header: CompanyHeaderEntity?,
        invoice: InvoiceEntity,
        lines: List<InvoiceLineEntity>,
        footerLine1: String,
        footerLine2: String,
        isFullPaid: Boolean = true,
        lang: String = "en"
    ): String {
        val width = WIDTH_58MM
        val sb = StringBuilder()
        val sepStraight = "-".repeat(width)
        val isBn = lang.equals("bn", ignoreCase = true)

        val itemNameLabel = if (isBn) "পণ্যের নাম" else "Item Name"
        val qtyLabel = if (isBn) "পরিমাণ" else "Qty"
        val rateLabel = if (isBn) "দর" else "Rate"
        val totalColLabel = if (isBn) "মোট" else "Total"
        val subTotalLabel = if (isBn) "উপ-মোট" else "Sub Total"
        val totalSummaryLabel = if (isBn) "সর্বমোট প্রদেয়" else "TOTAL"
        val dateLabel = if (isBn) "তারিখ" else "Date"
        val orderIdLabel = if (isBn) "অর্ডার নম্বর" else "Order ID"
        val operatorLabel = if (isBn) "অপারেটর" else "Operator"
        val customerLabel = if (isBn) "গ্রাহক" else "Customer"
        val fullPaidText = if (isBn) "সম্পূর্ণ পরিশোধিত ✓" else "FULL PAID ✓"

        // 1. Company Name (Preserves line breaks and center-aligns every line dynamically)
        val defaultCompanyName = if (isBn) "পিওএস স্টেশন" else "POS STATION"
        val companyLines = splitCompanyLines(header?.companyName, defaultCompanyName)
        companyLines.forEach { line ->
            val displayLine = if (isBn) BengaliTransliterationEngine.transliterate(line) else line
            sb.appendLine(centerText(displayLine, width))
        }

        // 2. Branch info & Address (Centered)
        val branchInfo = if (!header?.branchName.isNullOrBlank()) {
            val bName = if (isBn) BengaliTransliterationEngine.transliterate(header.branchName) else header.branchName
            val bNum = if (isBn && !header.branchNumber.isNullOrBlank()) {
                DateTimeUtils.toBengaliNumerals(header.branchNumber)
            } else {
                header.branchNumber
            }
            if (!bNum.isNullOrBlank()) "$bName ($bNum)" else bName
        } else if (!header?.branchNumber.isNullOrBlank()) {
            val bNum = if (isBn) DateTimeUtils.toBengaliNumerals(header.branchNumber) else header.branchNumber
            if (isBn) " শাখা #$bNum" else "Branch #${header.branchNumber}"
        } else ""
        if (branchInfo.isNotBlank()) {
            sb.appendLine(centerText(branchInfo.trim(), width))
        }

        val address = header?.address ?: ""
        if (address.isNotBlank()) {
            val displayAddress = if (isBn) {
                DateTimeUtils.toBengaliNumerals(BengaliTransliterationEngine.transliterate(address))
            } else {
                address
            }
            wrapLinesForWidth(displayAddress, width).forEach {
                sb.appendLine(centerText(it, width))
            }
        }

        // 4. Phone (Centered)
        val phone = header?.phoneNumber ?: ""
        if (phone.isNotBlank()) {
            val phoneVal = if (isBn) DateTimeUtils.toBengaliNumerals(phone) else phone
            val phoneText = if (phone.startsWith("Phone") || phone.startsWith("ফোন")) {
                phoneVal
            } else if (isBn) {
                "ফোন: $phoneVal"
            } else {
                "Phone: $phone"
            }
            sb.appendLine(centerText(phoneText, width))
        }

        // Invoice Title
        if (isBn) {
            sb.appendLine(centerText("ক্যাশ মেমো", width))
        }

        // 5. Order ID (Independently Centered)
        val orderIdVal = if (isBn) DateTimeUtils.toBengaliNumerals(invoice.orderId) else invoice.orderId
        sb.appendLine(centerText("$orderIdLabel: #$orderIdVal", width))

        // 6. Date/Time (Independently Centered)
        val dateStr = if (isBn) {
            DateTimeUtils.formatReceiptDateTime(invoice.dateTime)
        } else {
            val dateFormat = SimpleDateFormat("dd-MMM-yyyy hh:mma", Locale.US)
            dateFormat.format(Date(invoice.dateTime))
        }
        sb.appendLine(centerText("$dateLabel: $dateStr", width))

        // 7. Operator (Independently Centered)
        val operatorVal = if (isBn) BengaliTransliterationEngine.transliterate(invoice.operatorName) else invoice.operatorName
        sb.appendLine(centerText("$operatorLabel: $operatorVal", width))

        // 8. Customer (Independently Centered, omitted if blank or placeholder)
        if (!isCustomerInfoOmitted(invoice.customerInfo)) {
            val customerVal = if (isBn) {
                DateTimeUtils.toBengaliNumerals(BengaliTransliterationEngine.transliterate(invoice.customerInfo))
            } else {
                invoice.customerInfo
            }
            sb.appendLine(centerText("$customerLabel: $customerVal", width))
        }

        // STRAIGHT SEPARATOR 1 (Between metadata and item table header)
        sb.appendLine(sepStraight)

        // 11. Item Header (58mm - 32 columns: Item Name (12) + Qty (4) + Rate (6) + Total (7))
        val headerLine = padRightVisual(itemNameLabel, 12) + " " +
                padLeftVisual(qtyLabel, 4) + " " +
                padLeftVisual(rateLabel, 6) + " " +
                padLeftVisual(totalColLabel, 7)
        sb.appendLine(headerLine)

        // STRAIGHT SEPARATOR 2 (Between item table header and item rows)
        sb.appendLine(sepStraight)

        // 12. Items
        for (item in lines) {
            val name = if (isBn) BengaliTransliterationEngine.transliterate(item.itemName) else item.itemName
            val unitFormatted = if (isBn) localizeUnit(item.unit, item.itemType) else item.unit
            val qtyVal = if (isBn) DateTimeUtils.toBengaliNumerals(item.quantityStr) else item.quantityStr
            val rate = if (isBn) {
                DateTimeUtils.toBengaliNumerals(FinancialCalculationEngine.formatPaisaToBdt(item.unitPricePaisa))
            } else {
                FinancialCalculationEngine.formatPaisaToBdt(item.unitPricePaisa)
            }
            val total = if (isBn) {
                DateTimeUtils.toBengaliNumerals(FinancialCalculationEngine.formatPaisaToBdt(item.lineTotalPaisa))
            } else {
                FinancialCalculationEngine.formatPaisaToBdt(item.lineTotalPaisa)
            }
            val currencySuffix = if (isBn) "৳" else "BDT"
            val qtyUnit = if (unitFormatted.isNotBlank()) "$qtyVal $unitFormatted".trim() else qtyVal

            val enQtyUnit = "${item.quantityStr}${item.unit.take(2)}".trim()
            if (!isBn && getVisualWidth(name) <= 12 &&
                getVisualWidth(enQtyUnit) <= 4 &&
                getVisualWidth(rate) <= 6 &&
                getVisualWidth(total) <= 7
            ) {
                sb.appendLine(
                    padRightVisual(name, 12) + " " +
                            padLeftVisual(enQtyUnit, 4) + " " +
                            padLeftVisual(rate, 6) + " " +
                            padLeftVisual(total, 7)
                )
            } else {
                // Item name on line 1 (wrapped), details indented on line 2 (never truncates decimals/units)
                val wrappedName = wrapText(name, width)
                wrappedName.forEach { sb.appendLine(it) }
                val detailLeft = "  $qtyUnit x $rate"
                val detailRight = "$total $currencySuffix"
                if (getVisualWidth(detailLeft) + 1 + getVisualWidth(detailRight) <= width) {
                    sb.appendLine(padRow(detailLeft, detailRight, width))
                } else {
                    wrapLinesForWidth(detailLeft, width).forEach { sb.appendLine(it) }
                    sb.appendLine(padLeftVisual(detailRight, width))
                }
            }
        }

        // STRAIGHT SEPARATOR 3 (Between item rows and Subtotal)
        sb.appendLine(sepStraight)

        // 14. Subtotal
        val rawSubtotalStr = FinancialCalculationEngine.formatPaisaToBdt(invoice.rawTotalPaisa)
        val subtotalStr = if (isBn) DateTimeUtils.toBengaliNumerals(rawSubtotalStr) else rawSubtotalStr
        sb.appendLine(format58mmSummaryLine(subTotalLabel, subtotalStr))

        // Rounding Adjustment (only when non-zero)
        val roundingDiffPaisa = FinancialCalculationEngine.calculateRoundingDifferencePaisa(invoice.rawTotalPaisa, invoice.roundedTotalBdt)
        if (roundingDiffPaisa != 0L) {
            val sign = if (roundingDiffPaisa > 0) "+" else "-"
            val absPaisa = kotlin.math.abs(roundingDiffPaisa)
            val absBdt = FinancialCalculationEngine.formatPaisaToBdt(absPaisa)
            val roundingStr = if (isBn) "$sign${DateTimeUtils.toBengaliNumerals(absBdt)}" else "$sign$absBdt"
            val roundingLabel = if (isBn) "রাউন্ডিং সমন্বয়" else "Rounding Adj:"
            sb.appendLine(format58mmSummaryLine(roundingLabel, roundingStr))
        }

        // 15. Rounded Total / Grand Total
        val rawRoundedStr = FinancialCalculationEngine.formatWholeBdtToDisplay(invoice.roundedTotalBdt)
        val roundedStr = if (isBn) DateTimeUtils.toBengaliNumerals(rawRoundedStr) else rawRoundedStr
        sb.appendLine(format58mmSummaryLine(totalSummaryLabel, roundedStr))

        // Payment Status / Breakdown
        val paidLabel = if (isBn) "জমা" else "Paid:"
        val dueLabel = if (isBn) "বকেয়া" else "Due:"
        val (paidAmountBdt, dueAmountBdt, effectiveFullPaid) = resolvePaymentBreakdown(invoice, isFullPaid)

        if (isBn) {
            val paidStr = "${DateTimeUtils.toBengaliNumerals(paidAmountBdt.toString())} ৳"
            val dueStr = "${DateTimeUtils.toBengaliNumerals(dueAmountBdt.toString())} ৳"
            sb.appendLine(format58mmSummaryLine(paidLabel, paidStr))
            sb.appendLine(format58mmSummaryLine(dueLabel, dueStr))

            if (effectiveFullPaid) {
                sb.appendLine(centerText(fullPaidText, width))
            } else {
                val dueStamp = "*** বকেয়া / DUE ✕ ***"
                sb.appendLine(centerText(dueStamp, width))
            }
        } else {
            if (effectiveFullPaid) {
                sb.appendLine(centerText(fullPaidText, width))
            } else {
                sb.appendLine(format58mmSummaryLine(paidLabel, "$paidAmountBdt ৳"))
                sb.appendLine(format58mmSummaryLine(dueLabel, "$dueAmountBdt ৳"))
                sb.appendLine(centerText("*** DUE / UNPAID ✕ ***", width))
            }
        }

        // 17. Footer notes (Centered)
        if (footerLine1.isNotBlank()) {
            footerLine1.lines().forEach { l ->
                if (l.isNotBlank()) {
                    sb.appendLine(centerText(l.trim(), width))
                }
            }
        }
        if (footerLine2.isNotBlank()) {
            footerLine2.lines().forEach { l ->
                if (l.isNotBlank()) {
                    sb.appendLine(centerText(l.trim(), width))
                }
            }
        }

        return sb.toString()
    }

    /**
     * Dedicated 80mm Receipt Layout (48 Character Columns).
     * Takes full advantage of wide format with 5-column table and independently centered metadata.
     */
    fun format80mmReceiptText(
        header: CompanyHeaderEntity?,
        invoice: InvoiceEntity,
        lines: List<InvoiceLineEntity>,
        footerLine1: String,
        footerLine2: String,
        isFullPaid: Boolean = true,
        lang: String = "en"
    ): String {
        val width = WIDTH_80MM
        val sb = StringBuilder()
        val sepStraight = "-".repeat(width)
        val isBn = lang.equals("bn", ignoreCase = true)

        val descriptionLabel = if (isBn) "বিবরণ" else "Item Description"
        val qtyLabel = if (isBn) "পরিমাণ" else "Qty"
        val unitLabel = if (isBn) "একক" else "Unit"
        val rateLabel = if (isBn) "দর" else "Rate"
        val totalColLabel = if (isBn) "মোট" else "Total"
        val subTotalLabel = if (isBn) "উপ-মোট" else "Sub Total"
        val totalSummaryLabel = if (isBn) "সর্বমোট প্রদেয়" else "TOTAL"
        val dateLabel = if (isBn) "তারিখ" else "Date"
        val orderIdLabel = if (isBn) "অর্ডার নম্বর" else "Order ID"
        val operatorLabel = if (isBn) "অপারেটর" else "Operator"
        val customerLabel = if (isBn) "গ্রাহক" else "Customer"
        val fullPaidText = if (isBn) "সম্পূর্ণ পরিশোধিত ✓" else "FULL PAID ✓"

        // 1. Company Name (Preserves line breaks and center-aligns every line dynamically)
        val defaultCompanyName = if (isBn) "পিওএস স্টেশন" else "POS & INVOICE STATION"
        val companyLines = splitCompanyLines(header?.companyName, defaultCompanyName)
        companyLines.forEach { line ->
            val displayLine = if (isBn) BengaliTransliterationEngine.transliterate(line) else line
            sb.appendLine(centerText(displayLine, width))
        }

        // 2. Branch & Address (Centered)
        val branchInfo = if (!header?.branchName.isNullOrBlank()) {
            val bName = if (isBn) BengaliTransliterationEngine.transliterate(header.branchName) else header.branchName
            val bNum = if (isBn && !header.branchNumber.isNullOrBlank()) {
                DateTimeUtils.toBengaliNumerals(header.branchNumber)
            } else {
                header.branchNumber
            }
            if (!bNum.isNullOrBlank()) "$bName ($bNum)" else bName
        } else if (!header?.branchNumber.isNullOrBlank()) {
            val bNum = if (isBn) DateTimeUtils.toBengaliNumerals(header.branchNumber) else header.branchNumber
            if (isBn) "শাখা #$bNum" else "Branch #${header.branchNumber}"
        } else ""
        if (branchInfo.isNotBlank()) {
            sb.appendLine(centerText(branchInfo.trim(), width))
        }

        val address = header?.address ?: ""
        if (address.isNotBlank()) {
            val displayAddress = if (isBn) {
                DateTimeUtils.toBengaliNumerals(BengaliTransliterationEngine.transliterate(address))
            } else {
                address
            }
            wrapLinesForWidth(displayAddress, width).forEach {
                sb.appendLine(centerText(it, width))
            }
        }

        // 4. Phone (Centered)
        val phone = header?.phoneNumber ?: ""
        if (phone.isNotBlank()) {
            val phoneVal = if (isBn) DateTimeUtils.toBengaliNumerals(phone) else phone
            val phoneText = if (phone.startsWith("Phone") || phone.startsWith("ফোন")) {
                phoneVal
            } else if (isBn) {
                "ফোন: $phoneVal"
            } else {
                "Phone: $phone"
            }
            sb.appendLine(centerText(phoneText, width))
        }

        // Invoice Title
        if (isBn) {
            sb.appendLine(centerText("ক্যাশ মেমো", width))
        }

        // 5. Order ID (Independently Centered)
        val orderIdVal = if (isBn) DateTimeUtils.toBengaliNumerals(invoice.orderId) else invoice.orderId
        sb.appendLine(centerText("$orderIdLabel: #$orderIdVal", width))

        // 6. Date/Time (Independently Centered)
        val dateStr = if (isBn) {
            DateTimeUtils.formatReceiptDateTime(invoice.dateTime)
        } else {
            val dateFormat = SimpleDateFormat("dd-MMM-yyyy hh:mma", Locale.US)
            dateFormat.format(Date(invoice.dateTime))
        }
        sb.appendLine(centerText("$dateLabel: $dateStr", width))

        // 7. Operator (Independently Centered)
        val operatorVal = if (isBn) BengaliTransliterationEngine.transliterate(invoice.operatorName) else invoice.operatorName
        sb.appendLine(centerText("$operatorLabel: $operatorVal", width))

        // 8. Customer (Independently Centered, omitted if blank or placeholder)
        if (!isCustomerInfoOmitted(invoice.customerInfo)) {
            val customerVal = if (isBn) {
                DateTimeUtils.toBengaliNumerals(BengaliTransliterationEngine.transliterate(invoice.customerInfo))
            } else {
                invoice.customerInfo
            }
            sb.appendLine(centerText("$customerLabel: $customerVal", width))
        }

        // STRAIGHT SEPARATOR 1 (Between metadata and item table header)
        sb.appendLine(sepStraight)

        // 11. Item Header (80mm - 48 columns: Description(18) + Qty(5) + Unit(5) + Rate(8) + Total(8))
        val headerLine = padRightVisual(descriptionLabel, 18) + " " +
                padLeftVisual(qtyLabel, 5) + " " +
                padRightVisual(unitLabel, 5) + " " +
                padLeftVisual(rateLabel, 8) + " " +
                padLeftVisual(totalColLabel, 8)
        sb.appendLine(headerLine)

        // STRAIGHT SEPARATOR 2 (Between item table header and item rows)
        sb.appendLine(sepStraight)

        // 12. Items
        for (item in lines) {
            val name = if (isBn) BengaliTransliterationEngine.transliterate(item.itemName) else item.itemName
            val unit = if (isBn) localizeUnit(item.unit, item.itemType) else item.unit.take(5)
            val qty = if (isBn) DateTimeUtils.toBengaliNumerals(item.quantityStr) else item.quantityStr
            val rate = if (isBn) {
                DateTimeUtils.toBengaliNumerals(FinancialCalculationEngine.formatPaisaToBdt(item.unitPricePaisa))
            } else {
                FinancialCalculationEngine.formatPaisaToBdt(item.unitPricePaisa)
            }
            val total = if (isBn) {
                DateTimeUtils.toBengaliNumerals(FinancialCalculationEngine.formatPaisaToBdt(item.lineTotalPaisa))
            } else {
                FinancialCalculationEngine.formatPaisaToBdt(item.lineTotalPaisa)
            }

            val fitsColumns = getVisualWidth(qty) <= 5 &&
                    getVisualWidth(unit) <= 5 &&
                    getVisualWidth(rate) <= 8 &&
                    getVisualWidth(total) <= 8

            if (!fitsColumns) {
                // 2-line fallback when fractional qty, unit, rate, or total exceeds 80mm column widths
                wrapText(name, width).forEach { sb.appendLine(it) }
                val unitPart = if (unit.isNotBlank()) " $unit" else ""
                val detailLeft = "  $qty$unitPart x $rate"
                if (getVisualWidth(detailLeft) + 1 + getVisualWidth(total) <= width) {
                    sb.appendLine(padRow(detailLeft, total, width))
                } else {
                    wrapLinesForWidth(detailLeft, width).forEach { sb.appendLine(it) }
                    sb.appendLine(padLeftVisual(total, width))
                }
            } else if (getVisualWidth(name) <= 18) {
                sb.appendLine(
                    padRightVisual(name, 18) + " " +
                            padLeftVisual(qty, 5) + " " +
                            padRightVisual(unit, 5) + " " +
                            padLeftVisual(rate, 8) + " " +
                            padLeftVisual(total, 8)
                )
            } else {
                // Wrap long item name
                val nameLines = wrapText(name, 18)
                sb.appendLine(
                    padRightVisual(nameLines.firstOrNull() ?: name, 18) + " " +
                            padLeftVisual(qty, 5) + " " +
                            padRightVisual(unit, 5) + " " +
                            padLeftVisual(rate, 8) + " " +
                            padLeftVisual(total, 8)
                )
                for (i in 1 until nameLines.size) {
                    sb.appendLine(nameLines[i])
                }
            }
        }

        // STRAIGHT SEPARATOR 3 (Between item rows and Subtotal)
        sb.appendLine(sepStraight)

        // 14. Subtotal
        val rawSubtotalStr = FinancialCalculationEngine.formatPaisaToBdt(invoice.rawTotalPaisa)
        val subtotalStr = if (isBn) DateTimeUtils.toBengaliNumerals(rawSubtotalStr) else rawSubtotalStr
        sb.appendLine(format80mmSummaryLine(subTotalLabel, subtotalStr))

        // Rounding Adjustment (only when non-zero)
        val roundingDiffPaisa = FinancialCalculationEngine.calculateRoundingDifferencePaisa(invoice.rawTotalPaisa, invoice.roundedTotalBdt)
        if (roundingDiffPaisa != 0L) {
            val sign = if (roundingDiffPaisa > 0) "+" else "-"
            val absPaisa = kotlin.math.abs(roundingDiffPaisa)
            val absBdt = FinancialCalculationEngine.formatPaisaToBdt(absPaisa)
            val roundingStr = if (isBn) "$sign${DateTimeUtils.toBengaliNumerals(absBdt)}" else "$sign$absBdt"
            val roundingLabel = if (isBn) "রাউন্ডিং সমন্বয়" else "Rounding Adj:"
            sb.appendLine(format80mmSummaryLine(roundingLabel, roundingStr))
        }

        // 15. Rounded Total / Grand Total
        val rawRoundedStr = FinancialCalculationEngine.formatWholeBdtToDisplay(invoice.roundedTotalBdt)
        val roundedStr = if (isBn) DateTimeUtils.toBengaliNumerals(rawRoundedStr) else rawRoundedStr
        sb.appendLine(format80mmSummaryLine(totalSummaryLabel, roundedStr))

        // Payment Status / Breakdown
        val paidLabel = if (isBn) "জমা" else "Paid:"
        val dueLabel = if (isBn) "বকেয়া" else "Due:"
        val (paidAmountBdt, dueAmountBdt, effectiveFullPaid) = resolvePaymentBreakdown(invoice, isFullPaid)

        if (isBn) {
            val paidStr = "${DateTimeUtils.toBengaliNumerals(paidAmountBdt.toString())} ৳"
            val dueStr = "${DateTimeUtils.toBengaliNumerals(dueAmountBdt.toString())} ৳"
            sb.appendLine(format80mmSummaryLine(paidLabel, paidStr))
            sb.appendLine(format80mmSummaryLine(dueLabel, dueStr))

            if (effectiveFullPaid) {
                sb.appendLine(centerText(fullPaidText, width))
            } else {
                val dueStamp = "*** বকেয়া / DUE ✕ ***"
                sb.appendLine(centerText(dueStamp, width))
            }
        } else {
            if (effectiveFullPaid) {
                sb.appendLine(centerText(fullPaidText, width))
            } else {
                sb.appendLine(format80mmSummaryLine(paidLabel, "$paidAmountBdt ৳"))
                sb.appendLine(format80mmSummaryLine(dueLabel, "$dueAmountBdt ৳"))
                sb.appendLine(centerText("*** DUE / UNPAID ✕ ***", width))
            }
        }

        // 17. Footer notes (Centered)
        if (footerLine1.isNotBlank()) {
            footerLine1.lines().forEach { l ->
                if (l.isNotBlank()) {
                    sb.appendLine(centerText(l.trim(), width))
                }
            }
        }
        if (footerLine2.isNotBlank()) {
            footerLine2.lines().forEach { l ->
                if (l.isNotBlank()) {
                    sb.appendLine(centerText(l.trim(), width))
                }
            }
        }

        return sb.toString()
    }

    /**
     * Builds standard ESC/POS binary command sequence.
     * Incorporates hardware styling: alignment, bold, double-sizing, optional raster logo, line feeds, and auto-cut.
     */
    fun buildEscPosReceipt(
        header: CompanyHeaderEntity?,
        invoice: InvoiceEntity,
        lines: List<InvoiceLineEntity>,
        paperSize: String,
        autoCutter: Boolean,
        footerLine1: String,
        footerLine2: String,
        logoBitmap: Bitmap? = null,
        isFullPaid: Boolean = true,
        lang: String = "en"
    ): ByteArray {
        val isBn = lang.equals("bn", ignoreCase = true)
        val itemNameLabel = if (isBn) "পণ্যের নাম" else "Item Name"
        val descriptionLabel = if (isBn) "বিবরণ" else "Item Description"
        val qtyLabel = if (isBn) "পরিমাণ" else "Qty"
        val unitLabel = if (isBn) "একক" else "Unit"
        val rateLabel = if (isBn) "দর" else "Rate"
        val totalColLabel = if (isBn) "মোট" else "Total"
        val subTotalLabel = if (isBn) "উপ-মোট" else "Sub Total"
        val totalSummaryLabel = if (isBn) "সর্বমোট প্রদেয়" else "TOTAL"
        val dateLabel = if (isBn) "তারিখ" else "Date"
        val orderIdLabel = if (isBn) "অর্ডার নম্বর" else "Order ID"
        val operatorLabel = if (isBn) "অপারেটর" else "Operator"
        val customerLabel = if (isBn) "গ্রাহক" else "Customer"
        val fullPaidText = if (isBn) "সম্পূর্ণ পরিশোধিত ✓" else "FULL PAID ✓"

        val is80mm = paperSize.contains("80")
        val width = if (is80mm) WIDTH_80MM else WIDTH_58MM
        val widthDots = if (is80mm) DOTS_80MM else DOTS_58MM
        val maxLogoDots = if (is80mm) 216 else 160

        val out = ByteArrayOutputStream()

        // 1. Initialize
        out.write(CMD_INIT)

        // 2. Logo / Placeholder (Balanced size, centered across paper width)
        if (logoBitmap != null) {
            val rasterLogo = renderLogoToRaster(logoBitmap, widthDots, maxLogoDots)
            if (rasterLogo != null && rasterLogo.isNotEmpty()) {
                out.write(CMD_ALIGN_CENTER)
                out.write(rasterLogo)
                out.write(CMD_FEED_lines(1))
            }
        } else {
            val placeholder = renderLogoPlaceholderRaster(widthDots, is80mm)
            if (placeholder != null && placeholder.isNotEmpty()) {
                out.write(CMD_ALIGN_CENTER)
                out.write(placeholder)
                out.write(CMD_FEED_lines(1))
            }
        }

        // 3. Company Header (Centered, Bold, 3x Magnification)
        out.write(CMD_ALIGN_CENTER)
        val defaultCompanyName = if (isBn) "পিওএস স্টেশন" else if (is80mm) "POS & INVOICE STATION" else "POS STATION"
        val companyLines = splitCompanyLines(header?.companyName, defaultCompanyName)
        val displayCompanyLines = if (isBn) companyLines.map { BengaliTransliterationEngine.transliterate(it) } else companyLines
        val hasBangla = displayCompanyLines.any { containsBangla(it) }
        if (hasBangla) {
            val headerTextSize = if (is80mm) 36f else 30f
            displayCompanyLines.forEach { line ->
                val raster = renderTextToRaster(
                    text = line,
                    widthDots = widthDots,
                    textSizePx = headerTextSize,
                    isBold = true,
                    alignment = Layout.Alignment.ALIGN_CENTER
                )
                if (raster != null) {
                    out.write(raster)
                } else {
                    out.write(line.toByteArray(Charsets.UTF_8))
                    out.write(0x0A)
                }
            }
        } else {
            out.write(CMD_BOLD_ON)
            out.write(CMD_TEXT_TRIPLE_SIZE)
            displayCompanyLines.forEach { line ->
                out.write(line.toByteArray(Charsets.UTF_8))
                out.write(0x0A)
            }
            out.write(CMD_TEXT_NORMAL)
            out.write(CMD_BOLD_OFF)
        }

        val branchInfo = if (!header?.branchName.isNullOrBlank()) {
            val bName = if (isBn) BengaliTransliterationEngine.transliterate(header.branchName) else header.branchName
            val bNum = if (isBn && !header.branchNumber.isNullOrBlank()) {
                DateTimeUtils.toBengaliNumerals(header.branchNumber)
            } else {
                header.branchNumber
            }
            if (!bNum.isNullOrBlank()) "$bName ($bNum)" else bName
        } else if (!header?.branchNumber.isNullOrBlank()) {
            val bNum = if (isBn) DateTimeUtils.toBengaliNumerals(header.branchNumber) else header.branchNumber
            if (isBn) "শাখা #$bNum" else "Branch #${header.branchNumber}"
        } else ""
        if (branchInfo.isNotBlank()) {
            if (containsBangla(branchInfo)) {
                out.write(CMD_ALIGN_CENTER)
                val raster = renderTextToRaster(
                    text = branchInfo.trim(),
                    widthDots = widthDots,
                    textSizePx = 23f,
                    isBold = false,
                    alignment = Layout.Alignment.ALIGN_CENTER
                )
                if (raster != null) {
                    out.write(raster)
                } else {
                    wrapLinesForWidth(branchInfo, width).forEach { line ->
                        out.write(line.toByteArray(Charsets.UTF_8))
                        out.write(0x0A)
                    }
                }
            } else {
                wrapLinesForWidth(branchInfo, width).forEach { line ->
                    out.write(line.toByteArray(Charsets.UTF_8))
                    out.write(0x0A)
                }
            }
        }

        val address = header?.address ?: ""
        if (address.isNotBlank()) {
            val displayAddress = if (isBn) {
                DateTimeUtils.toBengaliNumerals(BengaliTransliterationEngine.transliterate(address))
            } else {
                address
            }
            if (containsBangla(displayAddress)) {
                out.write(CMD_ALIGN_CENTER)
                val raster = renderTextToRaster(
                    text = displayAddress.trim(),
                    widthDots = widthDots,
                    textSizePx = 23f,
                    isBold = false,
                    alignment = Layout.Alignment.ALIGN_CENTER
                )
                if (raster != null) {
                    out.write(raster)
                } else {
                    wrapLinesForWidth(displayAddress, width).forEach { line ->
                        out.write(line.toByteArray(Charsets.UTF_8))
                        out.write(0x0A)
                    }
                }
            } else {
                wrapLinesForWidth(displayAddress, width).forEach { line ->
                    out.write(line.toByteArray(Charsets.UTF_8))
                    out.write(0x0A)
                }
            }
        }

        val phone = header?.phoneNumber ?: ""
        if (phone.isNotBlank()) {
            val phoneVal = if (isBn) DateTimeUtils.toBengaliNumerals(phone) else phone
            val phoneText = if (phone.startsWith("Phone") || phone.startsWith("ফোন")) {
                phoneVal
            } else if (isBn) {
                "ফোন: $phoneVal"
            } else {
                "Phone: $phone"
            }
            if (containsBangla(phoneText)) {
                out.write(CMD_ALIGN_CENTER)
                val raster = renderTextToRaster(
                    text = phoneText.trim(),
                    widthDots = widthDots,
                    textSizePx = 23f,
                    isBold = false,
                    alignment = Layout.Alignment.ALIGN_CENTER
                )
                if (raster != null) {
                    out.write(raster)
                } else {
                    wrapLinesForWidth(phoneText, width).forEach { line ->
                        out.write(line.toByteArray(Charsets.UTF_8))
                        out.write(0x0A)
                    }
                }
            } else {
                wrapLinesForWidth(phoneText, width).forEach { line ->
                    out.write(line.toByteArray(Charsets.UTF_8))
                    out.write(0x0A)
                }
            }
        }

        // Invoice Title
        if (isBn) {
            val titleRaster = renderTextToRaster(
                text = "ক্যাশ মেমো",
                widthDots = widthDots,
                textSizePx = if (is80mm) 30f else 26f,
                isBold = true,
                alignment = Layout.Alignment.ALIGN_CENTER
            )
            if (titleRaster != null) {
                out.write(titleRaster)
            }
        }

        // Metadata section (Independently Centered)
        val orderIdVal = if (isBn) DateTimeUtils.toBengaliNumerals(invoice.orderId) else invoice.orderId
        val dateStr = if (isBn) {
            DateTimeUtils.formatReceiptDateTime(invoice.dateTime)
        } else {
            val dateFormat = SimpleDateFormat("dd-MMM-yyyy hh:mma", Locale.US)
            dateFormat.format(Date(invoice.dateTime))
        }
        val operatorVal = if (isBn) BengaliTransliterationEngine.transliterate(invoice.operatorName) else invoice.operatorName
        val customerVal = if (isBn) {
            DateTimeUtils.toBengaliNumerals(BengaliTransliterationEngine.transliterate(invoice.customerInfo))
        } else {
            invoice.customerInfo
        }

        fun writeCenteredMetadata(text: String) {
            out.write(CMD_ALIGN_CENTER)
            if (containsBangla(text)) {
                val raster = renderTextToRaster(
                    text = text.trim(),
                    widthDots = widthDots,
                    textSizePx = 22f,
                    isBold = false,
                    alignment = Layout.Alignment.ALIGN_CENTER
                )
                if (raster != null) {
                    out.write(raster)
                } else {
                    out.write("${centerText(text, width)}\n".toByteArray(Charsets.UTF_8))
                }
            } else {
                out.write("${centerText(text, width)}\n".toByteArray(Charsets.UTF_8))
            }
        }

        writeCenteredMetadata("$orderIdLabel: #$orderIdVal")
        writeCenteredMetadata("$dateLabel: $dateStr")
        writeCenteredMetadata("$operatorLabel: $operatorVal")
        if (!isCustomerInfoOmitted(invoice.customerInfo)) {
            writeCenteredMetadata("$customerLabel: $customerVal")
        }

        // STRAIGHT SEPARATOR 1 (Between metadata and item table header)
        val sepStraight = "-".repeat(width)
        out.write(CMD_ALIGN_LEFT)
        out.write(sepStraight.toByteArray(Charsets.UTF_8))
        out.write(0x0A)

        // Table Header
        if (is80mm) {
            val th80 = padRightVisual(descriptionLabel, 18) + " " +
                    padLeftVisual(qtyLabel, 5) + " " +
                    padRightVisual(unitLabel, 5) + " " +
                    padLeftVisual(rateLabel, 8) + " " +
                    padLeftVisual(totalColLabel, 8)
            if (containsBangla(th80)) {
                val raster = renderTextToRaster(th80, widthDots, textSizePx = 22f, isBold = true, alignment = Layout.Alignment.ALIGN_NORMAL)
                if (raster != null) {
                    out.write(raster)
                } else {
                    out.write(CMD_BOLD_ON)
                    out.write("$th80\n".toByteArray(Charsets.UTF_8))
                    out.write(CMD_BOLD_OFF)
                }
            } else {
                out.write(CMD_BOLD_ON)
                out.write("$th80\n".toByteArray(Charsets.UTF_8))
                out.write(CMD_BOLD_OFF)
            }
        } else {
            val th58 = padRightVisual(itemNameLabel, 12) + " " +
                    padLeftVisual(qtyLabel, 4) + " " +
                    padLeftVisual(rateLabel, 6) + " " +
                    padLeftVisual(totalColLabel, 7)
            if (containsBangla(th58)) {
                val raster = renderTextToRaster(th58, widthDots, textSizePx = 22f, isBold = true, alignment = Layout.Alignment.ALIGN_NORMAL)
                if (raster != null) {
                    out.write(raster)
                } else {
                    out.write(CMD_BOLD_ON)
                    out.write("$th58\n".toByteArray(Charsets.UTF_8))
                    out.write(CMD_BOLD_OFF)
                }
            } else {
                out.write(CMD_BOLD_ON)
                out.write("$th58\n".toByteArray(Charsets.UTF_8))
                out.write(CMD_BOLD_OFF)
            }
        }

        // STRAIGHT SEPARATOR 2 (Between item table header and item rows)
        out.write(sepStraight.toByteArray(Charsets.UTF_8))
        out.write(0x0A)

        // Line Items
        for (item in lines) {
            val name = if (isBn) BengaliTransliterationEngine.transliterate(item.itemName) else item.itemName
            val unit = if (isBn) localizeUnit(item.unit, item.itemType) else item.unit
            val qty = if (isBn) DateTimeUtils.toBengaliNumerals(item.quantityStr) else item.quantityStr
            val rate = if (isBn) {
                DateTimeUtils.toBengaliNumerals(FinancialCalculationEngine.formatPaisaToBdt(item.unitPricePaisa))
            } else {
                FinancialCalculationEngine.formatPaisaToBdt(item.unitPricePaisa)
            }
            val total = if (isBn) {
                DateTimeUtils.toBengaliNumerals(FinancialCalculationEngine.formatPaisaToBdt(item.lineTotalPaisa))
            } else {
                FinancialCalculationEngine.formatPaisaToBdt(item.lineTotalPaisa)
            }
            val itemHasBangla = containsBangla(name) || containsBangla(unit) || containsBangla(qty) || containsBangla(rate) || containsBangla(total)

            if (is80mm) {
                if (itemHasBangla) {
                    val raster = render80mmItemRowToRaster(name, qty, unit, rate, total, widthDots)
                    if (raster != null) {
                        out.write(raster)
                    } else {
                        out.write(format80mmNativeItemLine(name, qty, unit, rate, total).toByteArray(Charsets.UTF_8))
                    }
                } else {
                    out.write(format80mmNativeItemLine(name, qty, unit, rate, total).toByteArray(Charsets.UTF_8))
                }
            } else {
                if (itemHasBangla) {
                    val raster = render58mmItemRowToRaster(name, qty, unit, rate, total, widthDots, isBn)
                    if (raster != null) {
                        out.write(raster)
                    } else {
                        out.write(format58mmNativeItemLine(name, qty, unit, rate, total, width, isBn).toByteArray(Charsets.UTF_8))
                    }
                } else {
                    out.write(format58mmNativeItemLine(name, qty, unit, rate, total, width, isBn).toByteArray(Charsets.UTF_8))
                }
            }
        }

        // STRAIGHT SEPARATOR 3 (Between item rows and Subtotal)
        out.write(sepStraight.toByteArray(Charsets.UTF_8))
        out.write(0x0A)

        // 14. Subtotal
        val rawSubtotalStr = FinancialCalculationEngine.formatPaisaToBdt(invoice.rawTotalPaisa)
        val subtotalStr = if (isBn) "${DateTimeUtils.toBengaliNumerals(rawSubtotalStr)} ৳" else rawSubtotalStr
        if (is80mm) {
            val subLine = format80mmSummaryLine(subTotalLabel, subtotalStr)
            if (containsBangla(subLine)) {
                val raster = render80mmSummaryRowToRaster(subTotalLabel, subtotalStr, widthDots, isBold = false)
                    ?: renderTextToRaster(subLine, widthDots, textSizePx = 22f, isBold = false)
                if (raster != null) out.write(raster) else out.write("$subLine\n".toByteArray(Charsets.UTF_8))
            } else {
                out.write("$subLine\n".toByteArray(Charsets.UTF_8))
            }
        } else {
            val subLine = format58mmSummaryLine(subTotalLabel, subtotalStr)
            if (containsBangla(subLine)) {
                val raster = render58mmSummaryRowToRaster(subTotalLabel, subtotalStr, widthDots, isBold = false)
                    ?: renderTextToRaster(subLine, widthDots, textSizePx = 22f, isBold = false)
                if (raster != null) out.write(raster) else out.write("$subLine\n".toByteArray(Charsets.UTF_8))
            } else {
                out.write("$subLine\n".toByteArray(Charsets.UTF_8))
            }
        }

        // Rounding Adjustment (only when non-zero)
        val roundingDiffPaisa = FinancialCalculationEngine.calculateRoundingDifferencePaisa(invoice.rawTotalPaisa, invoice.roundedTotalBdt)
        if (roundingDiffPaisa != 0L) {
            val sign = if (roundingDiffPaisa > 0) "+" else "-"
            val absPaisa = kotlin.math.abs(roundingDiffPaisa)
            val absBdt = FinancialCalculationEngine.formatPaisaToBdt(absPaisa)
            val roundingStr = if (isBn) "$sign${DateTimeUtils.toBengaliNumerals(absBdt)} ৳" else "$sign$absBdt"
            val roundingLabel = if (isBn) "রাউন্ডিং সমন্বয়" else "Rounding Adj:"
            if (is80mm) {
                val roundLine = format80mmSummaryLine(roundingLabel, roundingStr)
                if (containsBangla(roundLine)) {
                    val raster = render80mmSummaryRowToRaster(roundingLabel, roundingStr, widthDots, isBold = false)
                        ?: renderTextToRaster(roundLine, widthDots, textSizePx = 22f, isBold = false)
                    if (raster != null) out.write(raster) else out.write("$roundLine\n".toByteArray(Charsets.UTF_8))
                } else {
                    out.write("$roundLine\n".toByteArray(Charsets.UTF_8))
                }
            } else {
                val roundLine = format58mmSummaryLine(roundingLabel, roundingStr)
                if (containsBangla(roundLine)) {
                    val raster = render58mmSummaryRowToRaster(roundingLabel, roundingStr, widthDots, isBold = false)
                        ?: renderTextToRaster(roundLine, widthDots, textSizePx = 22f, isBold = false)
                    if (raster != null) out.write(raster) else out.write("$roundLine\n".toByteArray(Charsets.UTF_8))
                } else {
                    out.write("$roundLine\n".toByteArray(Charsets.UTF_8))
                }
            }
        }

        // 15. Rounded Total / Grand Total
        val rawRoundedStr = FinancialCalculationEngine.formatWholeBdtToDisplay(invoice.roundedTotalBdt)
        val roundedStr = if (isBn) "${DateTimeUtils.toBengaliNumerals(rawRoundedStr)} ৳" else rawRoundedStr
        if (is80mm) {
            val totalLine = format80mmSummaryLine(totalSummaryLabel, roundedStr)
            if (containsBangla(totalLine)) {
                val raster = render80mmSummaryRowToRaster(totalSummaryLabel, roundedStr, widthDots, isBold = true)
                    ?: renderTextToRaster(totalLine, widthDots, textSizePx = 28f, isBold = true)
                if (raster != null) out.write(raster) else out.write("$totalLine\n".toByteArray(Charsets.UTF_8))
            } else {
                out.write(CMD_BOLD_ON)
                out.write(CMD_TEXT_DOUBLE_HEIGHT)
                out.write("$totalLine\n".toByteArray(Charsets.UTF_8))
                out.write(CMD_TEXT_NORMAL)
                out.write(CMD_BOLD_OFF)
            }
        } else {
            val totalLine = format58mmSummaryLine(totalSummaryLabel, roundedStr)
            if (containsBangla(totalLine)) {
                val raster = render58mmSummaryRowToRaster(totalSummaryLabel, roundedStr, widthDots, isBold = true)
                    ?: renderTextToRaster(totalLine, widthDots, textSizePx = 28f, isBold = true)
                if (raster != null) out.write(raster) else out.write("$totalLine\n".toByteArray(Charsets.UTF_8))
            } else {
                out.write(CMD_BOLD_ON)
                out.write(CMD_TEXT_DOUBLE_HEIGHT)
                out.write("$totalLine\n".toByteArray(Charsets.UTF_8))
                out.write(CMD_TEXT_NORMAL)
                out.write(CMD_BOLD_OFF)
            }
        }

        // Payment Status / Breakdown
        val (paidAmountBdt, dueAmountBdt, effectiveFullPaid) = resolvePaymentBreakdown(invoice, isFullPaid)

        if (isBn) {
            val paidStr = "${DateTimeUtils.toBengaliNumerals(paidAmountBdt.toString())} ৳"
            val dueStr = "${DateTimeUtils.toBengaliNumerals(dueAmountBdt.toString())} ৳"
            if (is80mm) {
                val paidLine = format80mmSummaryLine("জমা", paidStr)
                val dueLine = format80mmSummaryLine("বকেয়া", dueStr)
                val rasterPaid = render80mmSummaryRowToRaster("জমা", paidStr, widthDots, isBold = false)
                    ?: renderTextToRaster(paidLine, widthDots, textSizePx = 22f, isBold = false)
                if (rasterPaid != null) out.write(rasterPaid) else out.write("$paidLine\n".toByteArray(Charsets.UTF_8))

                val rasterDue = render80mmSummaryRowToRaster("বকেয়া", dueStr, widthDots, isBold = dueAmountBdt > 0L)
                    ?: renderTextToRaster(dueLine, widthDots, textSizePx = 24f, isBold = dueAmountBdt > 0L)
                if (rasterDue != null) out.write(rasterDue) else out.write("$dueLine\n".toByteArray(Charsets.UTF_8))
            } else {
                val paidLine = format58mmSummaryLine("জমা", paidStr)
                val dueLine = format58mmSummaryLine("বকেয়া", dueStr)
                val rasterPaid = render58mmSummaryRowToRaster("জমা", paidStr, widthDots, isBold = false)
                    ?: renderTextToRaster(paidLine, widthDots, textSizePx = 22f, isBold = false)
                if (rasterPaid != null) out.write(rasterPaid) else out.write("$paidLine\n".toByteArray(Charsets.UTF_8))

                val rasterDue = render58mmSummaryRowToRaster("বকেয়া", dueStr, widthDots, isBold = dueAmountBdt > 0L)
                    ?: renderTextToRaster(dueLine, widthDots, textSizePx = 24f, isBold = dueAmountBdt > 0L)
                if (rasterDue != null) out.write(rasterDue) else out.write("$dueLine\n".toByteArray(Charsets.UTF_8))
            }

            if (effectiveFullPaid) {
                val raster = renderStatusStampRaster(
                    text = fullPaidText,
                    widthDots = widthDots,
                    isPaid = true,
                    is80mm = is80mm
                ) ?: renderTextToRaster(
                    text = fullPaidText,
                    widthDots = widthDots,
                    textSizePx = 24f,
                    isBold = true,
                    alignment = Layout.Alignment.ALIGN_CENTER
                )
                if (raster != null) {
                    out.write(raster)
                } else {
                    out.write(CMD_ALIGN_CENTER)
                    out.write(CMD_BOLD_ON)
                    out.write("$fullPaidText\n".toByteArray(Charsets.UTF_8))
                    out.write(CMD_BOLD_OFF)
                }
            } else {
                val dueStampText = "বকেয়া / DUE ✕"
                val rasterDueStamp = renderStatusStampRaster(
                    text = dueStampText,
                    widthDots = widthDots,
                    isPaid = false,
                    is80mm = is80mm
                )
                if (rasterDueStamp != null) {
                    out.write(rasterDueStamp)
                } else {
                    out.write(CMD_ALIGN_CENTER)
                    out.write(CMD_BOLD_ON)
                    out.write("*** $dueStampText ***\n".toByteArray(Charsets.UTF_8))
                    out.write(CMD_BOLD_OFF)
                }
            }
        } else {
            val paidLabel = "Paid:"
            val dueLabel = "Due:"
            if (effectiveFullPaid) {
                val raster = renderStatusStampRaster(
                    text = fullPaidText,
                    widthDots = widthDots,
                    isPaid = true,
                    is80mm = is80mm
                ) ?: renderTextToRaster(
                    text = fullPaidText,
                    widthDots = widthDots,
                    textSizePx = 24f,
                    isBold = true,
                    alignment = Layout.Alignment.ALIGN_CENTER
                )
                if (raster != null) {
                    out.write(raster)
                } else {
                    out.write(CMD_ALIGN_CENTER)
                    out.write(CMD_BOLD_ON)
                    out.write("$fullPaidText\n".toByteArray(Charsets.UTF_8))
                    out.write(CMD_BOLD_OFF)
                }
            } else {
                val paidStr = "$paidAmountBdt ৳"
                val dueStr = "$dueAmountBdt ৳"
                if (is80mm) {
                    val paidLine = format80mmSummaryLine(paidLabel, paidStr)
                    val dueLine = format80mmSummaryLine(dueLabel, dueStr)
                    val rasterPaid = render80mmSummaryRowToRaster(paidLabel, paidStr, widthDots, isBold = false)
                        ?: renderTextToRaster(paidLine, widthDots, textSizePx = 22f, isBold = false)
                    if (rasterPaid != null) out.write(rasterPaid) else out.write("$paidLine\n".toByteArray(Charsets.UTF_8))

                    val rasterDue = render80mmSummaryRowToRaster(dueLabel, dueStr, widthDots, isBold = true)
                        ?: renderTextToRaster(dueLine, widthDots, textSizePx = 24f, isBold = true)
                    if (rasterDue != null) out.write(rasterDue) else out.write("$dueLine\n".toByteArray(Charsets.UTF_8))
                } else {
                    val paidLine = format58mmSummaryLine(paidLabel, paidStr)
                    val dueLine = format58mmSummaryLine(dueLabel, dueStr)
                    val rasterPaid = render58mmSummaryRowToRaster(paidLabel, paidStr, widthDots, isBold = false)
                        ?: renderTextToRaster(paidLine, widthDots, textSizePx = 22f, isBold = false)
                    if (rasterPaid != null) out.write(rasterPaid) else out.write("$paidLine\n".toByteArray(Charsets.UTF_8))

                    val rasterDue = render58mmSummaryRowToRaster(dueLabel, dueStr, widthDots, isBold = true)
                        ?: renderTextToRaster(dueLine, widthDots, textSizePx = 24f, isBold = true)
                    if (rasterDue != null) out.write(rasterDue) else out.write("$dueLine\n".toByteArray(Charsets.UTF_8))
                }

                val dueStampText = "DUE / UNPAID ✕"
                val rasterDueStamp = renderStatusStampRaster(
                    text = dueStampText,
                    widthDots = widthDots,
                    isPaid = false,
                    is80mm = is80mm
                )
                if (rasterDueStamp != null) {
                    out.write(rasterDueStamp)
                } else {
                    out.write(CMD_ALIGN_CENTER)
                    out.write(CMD_BOLD_ON)
                    out.write("*** $dueStampText ***\n".toByteArray(Charsets.UTF_8))
                    out.write(CMD_BOLD_OFF)
                }
            }
        }

        // Footer Notes (Centered)
        out.write(CMD_ALIGN_CENTER)
        if (footerLine1.isNotBlank()) {
            if (containsBangla(footerLine1)) {
                val raster = renderTextToRaster(
                    text = footerLine1.trim(),
                    widthDots = widthDots,
                    textSizePx = 23f,
                    isBold = false,
                    alignment = Layout.Alignment.ALIGN_CENTER
                )
                if (raster != null) {
                    out.write(raster)
                } else {
                    wrapLinesForWidth(footerLine1, width).forEach { line ->
                        out.write("$line\n".toByteArray(Charsets.UTF_8))
                    }
                }
            } else {
                wrapLinesForWidth(footerLine1, width).forEach { line ->
                    out.write("$line\n".toByteArray(Charsets.UTF_8))
                }
            }
        }
        if (footerLine2.isNotBlank()) {
            if (containsBangla(footerLine2)) {
                val raster = renderTextToRaster(
                    text = footerLine2.trim(),
                    widthDots = widthDots,
                    textSizePx = 23f,
                    isBold = false,
                    alignment = Layout.Alignment.ALIGN_CENTER
                )
                if (raster != null) {
                    out.write(raster)
                } else {
                    wrapLinesForWidth(footerLine2, width).forEach { line ->
                        out.write("$line\n".toByteArray(Charsets.UTF_8))
                    }
                }
            } else {
                wrapLinesForWidth(footerLine2, width).forEach { line ->
                    out.write("$line\n".toByteArray(Charsets.UTF_8))
                }
            }
        }

        // Feed paper so bottom text clears cutter blade
        out.write(CMD_FEED_4)

        // Auto Cut command if enabled
        if (autoCutter) {
            out.write(CMD_CUT_PARTIAL)
        }

        return out.toByteArray()
    }

    private fun format80mmNativeItemLine(name: String, qty: String, unit: String, rate: String, total: String): String {
        val sb = StringBuilder()
        val displayUnit = if (!containsBangla(unit) && unit.length > 5) unit.take(5) else unit
        val fitsColumns = getVisualWidth(qty) <= 5 &&
                getVisualWidth(displayUnit) <= 5 &&
                getVisualWidth(rate) <= 8 &&
                getVisualWidth(total) <= 8

        if (!fitsColumns) {
            wrapText(name, WIDTH_80MM).forEach { sb.append("$it\n") }
            val unitPart = if (displayUnit.isNotBlank()) " $displayUnit" else ""
            val detailLeft = "  $qty$unitPart x $rate"
            if (getVisualWidth(detailLeft) + 1 + getVisualWidth(total) <= WIDTH_80MM) {
                sb.append("${padRow(detailLeft, total, WIDTH_80MM)}\n")
            } else {
                wrapLinesForWidth(detailLeft, WIDTH_80MM).forEach { sb.append("$it\n") }
                sb.append("${padLeftVisual(total, WIDTH_80MM)}\n")
            }
            return sb.toString()
        }

        val nameLines = if (getVisualWidth(name) <= 18) listOf(name) else wrapText(name, 18)
        sb.append(
            padRightVisual(nameLines.firstOrNull() ?: name, 18) + " " +
                padLeftVisual(qty, 5) + " " +
                padRightVisual(displayUnit, 5) + " " +
                padLeftVisual(rate, 8) + " " +
                padLeftVisual(total, 8) + "\n"
        )
        for (i in 1 until nameLines.size) {
            sb.append("${nameLines[i]}\n")
        }
        return sb.toString()
    }

    private fun format58mmNativeItemLine(name: String, qty: String, unit: String, rate: String, total: String, width: Int, isBn: Boolean = false): String {
        val sb = StringBuilder()
        val isBnLine = isBn || containsBangla(total) || containsBangla(name) || containsBangla(unit) || containsBangla(rate) || containsBangla(qty)
        if (!isBnLine && getVisualWidth(name) <= 12 && getVisualWidth(qty) <= 4 && unit.isBlank() && getVisualWidth(rate) <= 6 && getVisualWidth(total) <= 7) {
            sb.append(
                padRightVisual(name, 12) + " " +
                    padLeftVisual(qty, 4) + " " +
                    padLeftVisual(rate, 6) + " " +
                    padLeftVisual(total, 7) + "\n"
            )
        } else {
            val wrapped = wrapText(name, width)
            wrapped.forEach { sb.append("$it\n") }
            val unitStr = if (unit.isNotBlank()) " $unit" else ""
            val detailLeft = "  ${qty}${unitStr} x ${rate}"
            val totalCurrency = if (isBnLine) "৳" else "BDT"
            val detailRight = "$total $totalCurrency"
            if (getVisualWidth(detailLeft) + 1 + getVisualWidth(detailRight) <= width) {
                sb.append("${padRow(detailLeft, detailRight, width)}\n")
            } else {
                wrapLinesForWidth(detailLeft, width).forEach { sb.append("$it\n") }
                sb.append("${padLeftVisual(detailRight, width)}\n")
            }
        }
        return sb.toString()
    }

    /**
     * Builds ESC/POS bytes for a hardware test slip.
     */
    fun buildEscPosTestSlip(
        companyName: String?,
        paperSize: String,
        autoCutter: Boolean
    ): ByteArray {
        val is80mm = paperSize.contains("80")
        val width = if (is80mm) WIDTH_80MM else WIDTH_58MM
        val widthDots = if (is80mm) DOTS_80MM else DOTS_58MM
        val out = ByteArrayOutputStream()

        out.write(CMD_INIT)
        out.write(CMD_ALIGN_CENTER)
        out.write(CMD_BOLD_ON)
        out.write(CMD_TEXT_DOUBLE_SIZE)
        out.write("*** TEST PRINT ***\n".toByteArray(Charsets.UTF_8))

        out.write(CMD_TEXT_NORMAL)
        out.write(CMD_BOLD_OFF)
        out.write("================================\n".toByteArray(Charsets.UTF_8))

        out.write(CMD_BOLD_ON)
        val name = companyName ?: "POS Station"
        val testCompanyLines = splitCompanyLines(name, "POS Station")
        if (testCompanyLines.any { containsBangla(it) }) {
            val textSize = if (is80mm) 32f else 26f
            testCompanyLines.forEach { line ->
                val raster = renderTextToRaster(
                    text = line,
                    widthDots = widthDots,
                    textSizePx = textSize,
                    isBold = true,
                    alignment = Layout.Alignment.ALIGN_CENTER
                )
                raster?.let { out.write(it) }
            }
        } else {
            testCompanyLines.forEach { line ->
                out.write("$line\n".toByteArray(Charsets.UTF_8))
            }
        }
        out.write(CMD_BOLD_OFF)

        out.write(CMD_ALIGN_LEFT)
        out.write("Paper Format: ${if (is80mm) "80mm (48 Columns)" else "58mm (32 Columns)"}\n".toByteArray(Charsets.UTF_8))
        out.write("Auto-Cutter : ${if (autoCutter) "ENABLED (GS V 66 0)" else "DISABLED"}\n".toByteArray(Charsets.UTF_8))
        out.write("Timestamp   : ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}\n".toByteArray(Charsets.UTF_8))
        out.write("Connection  : Bluetooth SPP / RFCOMM OK\n".toByteArray(Charsets.UTF_8))

        out.write("--------------------------------\n".toByteArray(Charsets.UTF_8))
        out.write("Font Test: Regular, Bold, Double\n".toByteArray(Charsets.UTF_8))
        out.write(CMD_ALIGN_CENTER)
        out.write("ESC/POS Commands Verified!\n".toByteArray(Charsets.UTF_8))
        out.write("================================\n".toByteArray(Charsets.UTF_8))

        out.write(CMD_FEED_4)
        if (autoCutter) {
            out.write(CMD_CUT_PARTIAL)
        }
        return out.toByteArray()
    }

    /**
     * Plain text representation for test slip preview.
     */
    fun generateTestSlipText(
        companyName: String?,
        paperSize: String,
        autoCutter: Boolean
    ): String {
        val width = if (paperSize.contains("80")) WIDTH_80MM else WIDTH_58MM
        val sep = "=".repeat(width)
        val sepMinor = "-".repeat(width)
        return StringBuilder().apply {
            appendLine(sep)
            appendLine(centerText("*** TEST PRINT SLIP ***", width))
            appendLine(sep)
            splitCompanyLines(companyName, "POS Station").forEach { line ->
                appendLine(centerText(line, width))
            }
            appendLine("Paper Size : ${if (paperSize.contains("80")) "80mm (48 cols)" else "58mm (32 cols)"}")
            appendLine("Auto Cutter: ${if (autoCutter) "ON" else "OFF"}")
            appendLine("Date/Time  : ${SimpleDateFormat("dd-MMM-yyyy hh:mm:ss a", Locale.US).format(Date())}")
            appendLine("Status     : Bluetooth Hardware Verified OK")
            appendLine(sepMinor)
            appendLine(centerText("Thermal Printing Ready!", width))
            appendLine(sep)
        }.toString()
    }

    /**
     * Renders a logo bitmap centered across the entire paper dot width (widthDots).
     * Centering on a full-width white canvas ensures consistent center-alignment
     * on all ESC/POS thermal printers regardless of firmware-level raster alignment support.
     */
    fun renderLogoToRaster(source: Bitmap, widthDots: Int, maxLogoSizeDots: Int): ByteArray? {
        return try {
            val width = source.width
            val height = source.height
            if (width <= 0 || height <= 0) return null

            val scale = minOf(
                maxLogoSizeDots.toFloat() / width.toFloat(),
                maxLogoSizeDots.toFloat() / height.toFloat(),
                1.0f
            )
            val targetWidth = (width * scale).toInt().coerceAtLeast(1)
            val targetHeight = (height * scale).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)

            val fullWidthBitmap = Bitmap.createBitmap(widthDots, targetHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(fullWidthBitmap)
            canvas.drawColor(Color.WHITE)
            val left = ((widthDots - targetWidth) / 2f).coerceAtLeast(0f)
            canvas.drawBitmap(scaled, left, 0f, null)

            bitmapToEscPosRaster(fullWidthBitmap, widthDots)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Renders a reserved logo placeholder area with a neat border and "LOGO" text,
     * centered across widthDots when no custom company logo is configured.
     */
    fun renderLogoPlaceholderRaster(widthDots: Int, is80mm: Boolean): ByteArray? {
        return try {
            val boxWidth = if (is80mm) 120 else 96
            val boxHeight = if (is80mm) 48 else 40
            val bitmap = Bitmap.createBitmap(widthDots, boxHeight + 4, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)

            val left = ((widthDots - boxWidth) / 2f).coerceAtLeast(0f)
            val top = 2f
            val right = left + boxWidth
            val bottom = top + boxHeight

            // Border
            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }
            val rect = RectF(left, top, right, bottom)
            canvas.drawRoundRect(rect, 6f, 6f, strokePaint)

            // Centered "LOGO" text
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = if (is80mm) 18f else 15f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val fontMetrics = textPaint.fontMetrics
            val baseline = (top + bottom - fontMetrics.top - fontMetrics.bottom) / 2f
            canvas.drawText("LOGO", left + (boxWidth / 2f), baseline, textPaint)

            bitmapToEscPosRaster(bitmap, widthDots)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Renders a monochrome status stamp (PAID or DUE) centered across widthDots.
     * PAID: Clear bordered box with checkmark.
     * DUE: Inverted high-contrast black box with bold white text.
     * Guarantees distinct monochrome appearance on all standard thermal paper.
     */
    fun renderStatusStampRaster(
        text: String,
        widthDots: Int,
        isPaid: Boolean,
        is80mm: Boolean
    ): ByteArray? {
        return try {
            val boxWidth = if (is80mm) 280 else 220
            val boxHeight = if (is80mm) 48 else 40
            val bitmap = Bitmap.createBitmap(widthDots, boxHeight + 8, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)

            val left = ((widthDots - boxWidth) / 2f).coerceAtLeast(0f)
            val top = 4f
            val right = left + boxWidth
            val bottom = top + boxHeight
            val rect = RectF(left, top, right, bottom)

            if (isPaid) {
                // Clear bordered box
                val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    style = Paint.Style.STROKE
                    strokeWidth = 2.5f
                }
                canvas.drawRoundRect(rect, 6f, 6f, strokePaint)

                val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    textSize = if (is80mm) 20f else 16f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                val fontMetrics = textPaint.fontMetrics
                val baseline = (top + bottom - fontMetrics.top - fontMetrics.bottom) / 2f
                canvas.drawText(text, left + (boxWidth / 2f), baseline, textPaint)
            } else {
                // Inverted solid black box with bold white text
                val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    style = Paint.Style.FILL
                }
                canvas.drawRoundRect(rect, 6f, 6f, fillPaint)

                val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textSize = if (is80mm) 20f else 16f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                val fontMetrics = textPaint.fontMetrics
                val baseline = (top + bottom - fontMetrics.top - fontMetrics.bottom) / 2f
                canvas.drawText(text, left + (boxWidth / 2f), baseline, textPaint)
            }

            bitmapToEscPosRaster(bitmap, widthDots)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Converts an Android Bitmap into standard ESC/POS raster bit image command:
     * `GS v 0 0 xL xH yL yH d1...dk`
     */
    fun bitmapToEscPosRaster(source: Bitmap, maxWidthDots: Int): ByteArray? {
        return try {
            val width = source.width
            val height = source.height
            if (width <= 0 || height <= 0) return null

            // Scale down if larger than max printable width
            val targetWidth = if (width > maxWidthDots) maxWidthDots else width
            // Align to multiple of 8 for whole byte rows
            val alignedWidth = ((targetWidth + 7) / 8) * 8
            val targetHeight = (height.toFloat() * (alignedWidth.toFloat() / width.toFloat())).toInt().coerceAtLeast(1)

            val scaled = Bitmap.createScaledBitmap(source, alignedWidth, targetHeight, true)
            val bytesWidth = alignedWidth / 8
            val totalBytes = bytesWidth * targetHeight

            val out = ByteArrayOutputStream()
            // GS v 0 m (m=0 normal)
            out.write(0x1D)
            out.write(0x76)
            out.write(0x30)
            out.write(0x00)

            // xL, xH
            out.write(bytesWidth and 0xFF)
            out.write((bytesWidth shr 8) and 0xFF)

            // yL, yH
            out.write(targetHeight and 0xFF)
            out.write((targetHeight shr 8) and 0xFF)

            // Raster monochrome data: 1 = black dot, 0 = white dot
            val pixels = IntArray(alignedWidth * targetHeight)
            scaled.getPixels(pixels, 0, alignedWidth, 0, 0, alignedWidth, targetHeight)

            for (y in 0 until targetHeight) {
                for (b in 0 until bytesWidth) {
                    var byteVal = 0
                    for (bit in 0 until 8) {
                        val x = b * 8 + bit
                        val pixel = pixels[y * alignedWidth + x]
                        val r = Color.red(pixel)
                        val g = Color.green(pixel)
                        val bVal = Color.blue(pixel)
                        val alpha = Color.alpha(pixel)
                        val luminance = (r * 0.299 + g * 0.587 + bVal * 0.114).toInt()

                        // Dark pixel or non-transparent printed dot
                        if (alpha > 128 && luminance < 160) {
                            byteVal = byteVal or (1 shl (7 - bit))
                        }
                    }
                    out.write(byteVal)
                }
            }
            out.toByteArray()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Checks if a string contains any Bengali Unicode character (\u0980..\u09FF).
     */
    fun containsBangla(text: String): Boolean {
        return text.any { it in '\u0980'..'\u09FF' }
    }

    /**
     * Renders a text string into an ESC/POS monochrome raster command (GS v 0)
     * using Android's native StaticLayout and TextPaint.
     * This guarantees HarfBuzz complex text shaping for Bengali conjuncts, ligatures,
     * vowel signs, and numbers without splitting or reordering characters manually.
     */
    fun renderTextToRaster(
        text: String,
        widthDots: Int,
        textSizePx: Float = 24f,
        isBold: Boolean = false,
        alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL
    ): ByteArray? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null

        return try {
            var currentTextSize = textSizePx
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = currentTextSize
                typeface = if (isBold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
            while (currentTextSize > 18f && textPaint.measureText(trimmed) > widthDots - 8) {
                currentTextSize -= 1f
                textPaint.textSize = currentTextSize
            }

            val staticLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                StaticLayout.Builder.obtain(trimmed, 0, trimmed.length, textPaint, widthDots)
                    .setAlignment(alignment)
                    .setLineSpacing(2f, 1.1f)
                    .setIncludePad(true)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                StaticLayout(trimmed, textPaint, widthDots, alignment, 1.1f, 2f, true)
            }

            val height = staticLayout.height.coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(widthDots, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            staticLayout.draw(canvas)

            bitmapToEscPosRaster(bitmap, widthDots)
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * Renders a 5-column 80mm item line containing Bangla characters to ESC/POS raster.
     * Columns: Item Description (up to 240 dots), Qty, Unit, Rate, Total.
     */
    fun render80mmItemRowToRaster(
        name: String,
        qty: String,
        unit: String,
        rate: String,
        total: String,
        widthDots: Int = DOTS_80MM
    ): ByteArray? {
        return try {
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 21f
                typeface = Typeface.DEFAULT
            }

            // Measure columns to prevent overlap on wide Bengali units (e.g. ঘনমিটার) or large rates/quantities
            val qtyW = textPaint.measureText(qty)
            val unitW = textPaint.measureText(unit)
            val rateW = textPaint.measureText(rate)
            val totalW = textPaint.measureText(total)

            val fitsInlineColumns = qtyW <= 60f && unitW <= 84f && rateW <= 86f && totalW <= 88f

            if (fitsInlineColumns) {
                // Column 1: Item description (x = 2..226), remaining width allocated cleanly across Qty, Unit, Rate, Total
                val nameWidth = 224
                val nameLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    StaticLayout.Builder.obtain(name, 0, name.length, textPaint, nameWidth)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(0f, 1.1f)
                        .setIncludePad(true)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    StaticLayout(name, textPaint, nameWidth, Layout.Alignment.ALIGN_NORMAL, 1.1f, 0f, true)
                }

                val rowHeight = maxOf(nameLayout.height, 28) + 4
                val bitmap = Bitmap.createBitmap(widthDots, rowHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)

                canvas.save()
                canvas.translate(2f, 2f)
                nameLayout.draw(canvas)
                canvas.restore()

                val baseline = 22f
                // Qty: 232..292, Unit: 300..384, Rate: 390..476, Total: 484..572
                textPaint.textAlign = Paint.Align.RIGHT
                canvas.drawText(qty, 292f, baseline, textPaint)

                textPaint.textAlign = Paint.Align.LEFT
                canvas.drawText(unit, 300f, baseline, textPaint)

                textPaint.textAlign = Paint.Align.RIGHT
                canvas.drawText(rate, 476f, baseline, textPaint)

                textPaint.textAlign = Paint.Align.RIGHT
                canvas.drawText(total, (widthDots - 4).toFloat(), baseline, textPaint)

                bitmapToEscPosRaster(bitmap, widthDots)
            } else {
                // Overflow fallback: wrap full product name on top, then render numeric/unit details cleanly on second baseline
                val nameWidth = widthDots - 8
                val nameLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    StaticLayout.Builder.obtain(name, 0, name.length, textPaint, nameWidth)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(0f, 1.1f)
                        .setIncludePad(true)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    StaticLayout(name, textPaint, nameWidth, Layout.Alignment.ALIGN_NORMAL, 1.1f, 0f, true)
                }

                val rowHeight = nameLayout.height + 28 + 4
                val bitmap = Bitmap.createBitmap(widthDots, rowHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)

                canvas.save()
                canvas.translate(4f, 2f)
                nameLayout.draw(canvas)
                canvas.restore()

                val baseline2 = nameLayout.height + 22f
                val unitPart = if (unit.isNotBlank()) " $unit" else ""
                val leftDetail = "  ${qty}${unitPart} x $rate"
                val leftW = textPaint.measureText(leftDetail)
                if (leftW + totalW + 16f <= widthDots - 8) {
                    textPaint.textAlign = Paint.Align.LEFT
                    canvas.drawText(leftDetail, 4f, baseline2, textPaint)
                    textPaint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(total, (widthDots - 4).toFloat(), baseline2, textPaint)
                } else {
                    val bitmap2 = Bitmap.createBitmap(widthDots, nameLayout.height + 50, Bitmap.Config.ARGB_8888)
                    val canvas2 = Canvas(bitmap2)
                    canvas2.drawColor(Color.WHITE)
                    canvas2.save()
                    canvas2.translate(4f, 2f)
                    nameLayout.draw(canvas2)
                    canvas2.restore()
                    textPaint.textAlign = Paint.Align.LEFT
                    canvas2.drawText(leftDetail, 4f, nameLayout.height + 20f, textPaint)
                    textPaint.textAlign = Paint.Align.RIGHT
                    canvas2.drawText(total, (widthDots - 4).toFloat(), nameLayout.height + 44f, textPaint)
                    return bitmapToEscPosRaster(bitmap2, widthDots)
                }

                bitmapToEscPosRaster(bitmap, widthDots)
            }
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * Renders a 58mm item line containing Bangla characters to ESC/POS raster.
     * Line 1: Item Name (full width up to 384 dots)
     * Line 2: Details: "  ${qty}${unit} x ${rate}        ${total} BDT"
     */
    fun render58mmItemRowToRaster(
        name: String,
        qty: String,
        unit: String,
        rate: String,
        total: String,
        widthDots: Int = DOTS_58MM,
        isBn: Boolean = false
    ): ByteArray? {
        return try {
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 21f
                typeface = Typeface.DEFAULT
            }

            // Line 1: Item Name (full width up to 384 dots)
            val nameWidth = widthDots - 8
            val nameLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                StaticLayout.Builder.obtain(name, 0, name.length, textPaint, nameWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1.1f)
                    .setIncludePad(true)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                StaticLayout(name, textPaint, nameWidth, Layout.Alignment.ALIGN_NORMAL, 1.1f, 0f, true)
            }

            val unitStr = if (unit.isNotBlank()) " $unit" else ""
            val detailLeft = "  ${qty}${unitStr} x $rate"
            val isBnTotal = isBn || containsBangla(total) || containsBangla(name) || containsBangla(unit) || containsBangla(qty) || containsBangla(rate)
            val totalRight = if (isBnTotal) "$total ৳" else "$total BDT"

            val leftWidth = textPaint.measureText(detailLeft)
            val rightWidth = textPaint.measureText(totalRight)
            val needsTwoLines = (leftWidth + rightWidth + 12f) > (widthDots - 8)
            val line2Height = if (needsTwoLines) 46 else 26
            val rowHeight = nameLayout.height + line2Height + 4
            val bitmap = Bitmap.createBitmap(widthDots, rowHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)

            // Draw name
            canvas.save()
            canvas.translate(4f, 2f)
            nameLayout.draw(canvas)
            canvas.restore()

            if (needsTwoLines) {
                val line2Baseline = nameLayout.height + 18f
                val line3Baseline = nameLayout.height + 40f
                textPaint.textAlign = Paint.Align.LEFT
                canvas.drawText(detailLeft, 4f, line2Baseline, textPaint)
                textPaint.textAlign = Paint.Align.RIGHT
                canvas.drawText(totalRight, (widthDots - 4).toFloat(), line3Baseline, textPaint)
            } else {
                val line2Baseline = nameLayout.height + 20f
                textPaint.textAlign = Paint.Align.LEFT
                canvas.drawText(detailLeft, 4f, line2Baseline, textPaint)
                textPaint.textAlign = Paint.Align.RIGHT
                canvas.drawText(totalRight, (widthDots - 4).toFloat(), line2Baseline, textPaint)
            }

            bitmapToEscPosRaster(bitmap, widthDots)
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * Renders a 58mm summary line (Subtotal or TOTAL) containing Bangla text to ESC/POS raster.
     * Label on left, amount on right on a single row.
     */
    fun render58mmSummaryRowToRaster(
        label: String,
        amount: String,
        widthDots: Int = DOTS_58MM,
        isBold: Boolean = false
    ): ByteArray? {
        return try {
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 21f
                typeface = if (isBold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
            val rowHeight = 28
            val bitmap = Bitmap.createBitmap(widthDots, rowHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)

            val baseline = 21f
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(label, 4f, baseline, textPaint)

            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(amount, (widthDots - 4).toFloat(), baseline, textPaint)

            bitmapToEscPosRaster(bitmap, widthDots)
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * Renders an 80mm summary line (Subtotal or TOTAL) containing Bangla text to ESC/POS raster.
     * Label in Description column (left), amount in Total column (right) on a single row.
     */
    fun render80mmSummaryRowToRaster(
        label: String,
        amount: String,
        widthDots: Int = DOTS_80MM,
        isBold: Boolean = false
    ): ByteArray? {
        return try {
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 21f
                typeface = if (isBold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
            val rowHeight = 28
            val bitmap = Bitmap.createBitmap(widthDots, rowHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)

            val baseline = 21f
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(label, 4f, baseline, textPaint)

            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(amount, (widthDots - 4).toFloat(), baseline, textPaint)

            bitmapToEscPosRaster(bitmap, widthDots)
        } catch (t: Throwable) {
            null
        }
    }

    private fun CMD_FEED_lines(n: Int): ByteArray {
        val count = n.coerceIn(1, 255)
        return byteArrayOf(0x1B, 0x64, count.toByte())
    }

    /**
     * Calculates visual monospace terminal width accounting for Indic/Bengali
     * combining marks, diacritics, and ligatures.
     */
    fun getVisualWidth(text: String): Int {
        var count = 0
        var prevWasVirama = false
        for (i in text.indices) {
            val c = text[i]
            val type = Character.getType(c.code)
            val isCombining = type == Character.NON_SPACING_MARK.toInt() ||
                    type == Character.COMBINING_SPACING_MARK.toInt() ||
                    c == '\u200C' || c == '\u200D'
            if (isCombining) {
                if (c == '\u09CD') {
                    prevWasVirama = true
                }
                continue
            }
            if (prevWasVirama) {
                prevWasVirama = false
                continue
            }
            prevWasVirama = false
            count++
        }
        return count
    }

    fun centerSingleLine(line: String, width: Int): String {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return ""
        val vWidth = getVisualWidth(trimmed)
        if (vWidth >= width) return trimmed
        val pad = (width - vWidth) / 2
        return " ".repeat(maxOf(0, pad)) + trimmed
    }

    fun wrapLinesForWidth(text: String, width: Int): List<String> {
        if (text.isBlank()) return emptyList()
        val lines = mutableListOf<String>()
        val paragraphs = text.split("\n")
        for (para in paragraphs) {
            val words = para.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.isEmpty()) continue
            var currentLine = ""
            for (word in words) {
                if (getVisualWidth(word) > width) {
                    if (currentLine.isNotEmpty()) {
                        lines.add(currentLine)
                        currentLine = ""
                    }
                    var chunk = ""
                    for (ch in word) {
                        if (getVisualWidth(chunk + ch) > width && chunk.isNotEmpty()) {
                            lines.add(chunk)
                            chunk = ch.toString()
                        } else {
                            chunk += ch
                        }
                    }
                    currentLine = chunk
                } else if (currentLine.isEmpty()) {
                    currentLine = word
                } else {
                    val candidate = "$currentLine $word"
                    if (getVisualWidth(candidate) <= width) {
                        currentLine = candidate
                    } else {
                        lines.add(currentLine)
                        currentLine = word
                    }
                }
            }
            if (currentLine.isNotEmpty()) {
                lines.add(currentLine)
            }
        }
        return lines
    }

    fun centerText(text: String, width: Int): String {
        if (text.isBlank()) return ""
        return wrapLinesForWidth(text, width).joinToString("\n") { centerSingleLine(it, width) }
    }

    fun wrapText(text: String, width: Int): List<String> {
        val result = mutableListOf<String>()
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        var current = ""
        for (w in words) {
            if (current.isEmpty()) {
                current = w
            } else {
                val candidate = "$current $w"
                if (getVisualWidth(candidate) <= width) {
                    current = candidate
                } else {
                    result.add(current)
                    current = w
                }
            }
        }
        if (current.isNotEmpty()) {
            if (getVisualWidth(current) > width) {
                var chunk = ""
                for (ch in current) {
                    if (getVisualWidth(chunk + ch) > width) {
                        result.add(chunk)
                        chunk = ch.toString()
                    } else {
                        chunk += ch
                    }
                }
                if (chunk.isNotEmpty()) result.add(chunk)
            } else {
                result.add(current)
            }
        }
        return if (result.isEmpty()) listOf(text) else result
    }

    fun padRow(left: String, right: String, width: Int): String {
        val spacesNeeded = width - getVisualWidth(left) - getVisualWidth(right)
        return if (spacesNeeded > 0) {
            left + " ".repeat(spacesNeeded) + right
        } else {
            "$left $right"
        }
    }
}
