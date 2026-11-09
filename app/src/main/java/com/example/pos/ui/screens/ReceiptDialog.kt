package com.example.pos.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.pos.core.util.BengaliTransliterationEngine
import com.example.pos.core.util.CompanyLogoManager
import com.example.pos.core.util.DateTimeUtils
import com.example.pos.data.Localization
import com.example.pos.data.db.InvoiceLineEntity
import com.example.pos.domain.FinancialCalculationEngine
import com.example.pos.printer.ThermalReceiptFormatter
import com.example.pos.ui.components.CompanyLogoView
import java.util.Locale

@Composable
fun ReceiptPreviewDialog(
    receiptText: String,
    language: String,
    logoUri: String? = null,
    companyName: String? = null,
    isFullPaid: Boolean = true,
    onDismiss: () -> Unit,
    onPrintBluetooth: () -> Unit,
    onSharePdf: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    // Balanced 48 x 48 dp square reserved area
    val logoSizeDp = 48.dp
    val isLogoConfigured = remember(logoUri, context) {
        CompanyLogoManager.hasConfiguredLogo(context, logoUri)
    }

    val is80mm = (receiptText.lines().firstOrNull()?.length ?: 32) >= 40
    val receiptFontSize = if (is80mm) 9.sp else 12.sp
    val receiptLineHeight = if (is80mm) 13.sp else 16.sp
    // Company/Station Name: clearly larger than body text (12sp / 10.5sp) but moderate, balanced and proportionate
    val companyFontSize = if (is80mm) 14.sp else 16.sp

    val (displayCompanyName, remainingReceiptBody) = remember(receiptText, companyName) {
        parseReceiptForPreview(receiptText, companyName)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .testTag("receipt_preview_dialog"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ক্যাশ মেমো প্রিভিউ",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Realistic Thermal Paper Container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .height(350.dp)
                        .background(Color(0xFFFFFDF8), RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFFDCD6CD), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(4.dp))

                        // Logo / Placeholder: Balanced size (48x48 dp), centered, reserved area
                        Box(
                            modifier = Modifier
                                .size(logoSizeDp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFF1EDE6))
                                .border(1.dp, Color(0xFFD6CFC4), RoundedCornerShape(6.dp))
                                .padding(if (isLogoConfigured) 3.dp else 0.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLogoConfigured) {
                                CompanyLogoView(
                                    logoUri = logoUri,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    text = "লোগো",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        letterSpacing = 1.sp
                                    ),
                                    color = Color(0xFF7A7268),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Company/Station Name: dynamically from Settings, ~3x larger, center aligned, multi-line preserved
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            ThermalReceiptFormatter.splitCompanyLines(displayCompanyName).forEach { line ->
                                val localizedLine = BengaliTransliterationEngine.transliterate(line)
                                Text(
                                    text = localizedLine,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = companyFontSize,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        lineHeight = companyFontSize * 1.25f
                                    ),
                                    color = Color(0xFF111111),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Body of Receipt (branch, address, phone, metadata, aligned item table, totals, full paid, footer note)
                        ReceiptFormattedBody(
                            bodyText = remainingReceiptBody,
                            is80mm = is80mm,
                            receiptFontSize = receiptFontSize,
                            receiptLineHeight = receiptLineHeight,
                            textColor = Color(0xFF1A1A1A),
                            separatorColor = Color(0xFF555555),
                            language = language,
                            isFullPaid = isFullPaid
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Buttons: Print, PDF, Edit, Delete, Close
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Button 1: Print Voucher (Thermal Print Bluetooth ESC/POS)
                    Button(
                        onClick = onPrintBluetooth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("preview_print_btn"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "প্রিন্ট ভাউচার",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    // Button 2: PDF Download (Generates and shares/downloads PDF)
                    if (onSharePdf != null) {
                        OutlinedButton(
                            onClick = onSharePdf,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .testTag("preview_pdf_btn"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("PDF ডাউনলোড", fontSize = 13.sp)
                        }
                    }

                    // Button 3: Close / Dismiss
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .testTag("dialog_close_btn"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            "বন্ধ করুন",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }

                    // Secondary Utility Actions: Copy & Share Raw Text
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Receipt", receiptText))
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("কপি করুন", fontSize = 12.sp)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        TextButton(onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, receiptText)
                                putExtra(Intent.EXTRA_SUBJECT, "Invoice Receipt")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Receipt"))
                        }) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("শেয়ার করুন", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Extracts company/station name for enlarged 3x rendering, separating it from the rest of receipt text.
 */
private fun parseReceiptForPreview(receiptText: String, configuredName: String?): Pair<String, String> {
    val cleanConfigName = configuredName?.trim()?.ifBlank { null }
    val normalizedConfigName = cleanConfigName
        ?.replace("\\r\\n", "\n")
        ?.replace("\\n", "\n")
        ?.replace("\\r", "\n")
        ?.replace("\r\n", "\n")
        ?.replace("\r", "\n")

    val lines = receiptText.lines()
    if (lines.isEmpty()) {
        return (normalizedConfigName ?: "POS STATION") to receiptText
    }

    val firstSepIdx = lines.indexOfFirst { it.trim().startsWith("=") || it.trim().startsWith("-") }
    val hasLeadingSep = firstSepIdx in 0..1

    val startIdx = if (hasLeadingSep) firstSepIdx + 1 else 0
    val endHeaderIdx = if (hasLeadingSep) {
        val secondSep = lines.drop(startIdx).indexOfFirst { it.trim().startsWith("=") || it.trim().startsWith("-") }
        if (secondSep != -1) startIdx + secondSep else lines.size
    } else {
        val nextSep = lines.indexOfFirst { it.trim().startsWith("-") || it.trim().startsWith("=") }
        if (nextSep != -1) nextSep else lines.size
    }

    val headerLines = lines.subList(startIdx, endHeaderIdx)

    if (normalizedConfigName != null) {
        val width = if ((lines.firstOrNull()?.length ?: 32) >= 40) {
            ThermalReceiptFormatter.WIDTH_80MM
        } else {
            ThermalReceiptFormatter.WIDTH_58MM
        }
        val expectedCompanyLines = ThermalReceiptFormatter.splitCompanyLines(normalizedConfigName, "POS STATION")
            .flatMap { ThermalReceiptFormatter.wrapLinesForWidth(it, width) }
        var matchedCount = 0
        for (line in headerLines) {
            val t = line.trim()
            if (t.isBlank()) continue
            if (matchedCount < expectedCompanyLines.size &&
                t.equals(expectedCompanyLines[matchedCount], ignoreCase = true)
            ) {
                matchedCount++
            } else {
                break
            }
        }
        val skipCount = if (matchedCount > 0) {
            matchedCount
        } else {
            minOf(expectedCompanyLines.size.coerceAtLeast(1), headerLines.size)
        }
        val remaining = lines.subList(startIdx + skipCount, lines.size).joinToString("\n")
        return normalizedConfigName to remaining
    }

    // Default: find company name line(s) before branch/address/metadata
    val nameCandidates = mutableListOf<String>()
    var splitAt = startIdx
    for (i in startIdx until endHeaderIdx) {
        val l = lines[i].trim()
        if (l.startsWith("Branch") || l.contains(" শাখা") || l.endsWith(" শাখা") ||
            l.startsWith("Phone") || l.contains("ফোন") ||
            l.startsWith("Date:") || l.startsWith("Order ID:") || l.startsWith("Operator:") || l.startsWith("Customer:") ||
            l.startsWith("তারিখ:") || l.startsWith("অর্ডার আইডি:") || l.startsWith("অপারেটর:") || l.startsWith("ক্রেতা:")
        ) {
            splitAt = i
            break
        }
        if (l.isNotBlank()) {
            nameCandidates.add(l)
            splitAt = i + 1
        }
    }

    val title = if (nameCandidates.isNotEmpty()) nameCandidates.joinToString("\n") else "POS STATION"
    val remaining = lines.subList(splitAt, lines.size).joinToString("\n")
    return title to remaining
}

data class VoucherItemTableRow(
    val itemName: String,
    val qty: String,
    val unit: String,
    val rate: String,
    val total: String
)

data class VoucherSummaryRow(
    val label: String,
    val amount: String,
    val isBold: Boolean = false,
    val isDue: Boolean = false
)

data class ParsedVoucherTable(
    val isFiveColumn: Boolean,
    val separatorWidth: Int,
    val rows: List<VoucherItemTableRow>,
    val summaryRows: List<VoucherSummaryRow> = emptyList()
)

data class ParsedReceiptBodySections(
    val preTableText: String,
    val table: ParsedVoucherTable,
    val postTableText: String
)

@Composable
internal fun ReceiptFormattedBody(
    bodyText: String,
    is80mm: Boolean,
    receiptFontSize: TextUnit,
    receiptLineHeight: TextUnit,
    textColor: Color,
    separatorColor: Color,
    explicitLines: List<InvoiceLineEntity> = emptyList(),
    language: String = "en",
    isFullPaid: Boolean = true
) {
    val parsedSections = remember(bodyText, explicitLines, language) {
        parseReceiptSectionsForPreview(bodyText, explicitLines, language)
    }

    if (parsedSections != null) {
        val density = LocalDensity.current
        val textMeasurer = rememberTextMeasurer()
        val sepWidth = parsedSections.table.separatorWidth
        val measuredSlipWidthDp = remember(sepWidth, receiptFontSize, density) {
            val widthPx = textMeasurer.measure(
                text = "=".repeat(sepWidth),
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = receiptFontSize
                ),
                softWrap = false,
                maxLines = 1
            ).size.width
            with(density) { widthPx.toDp() }
        }

        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            val tableWidth = if (measuredSlipWidthDp >= 180.dp) {
                minOf(maxWidth, measuredSlipWidthDp)
            } else {
                maxWidth
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (parsedSections.preTableText.isNotBlank()) {
                    ReceiptPreTableSection(
                        preTableText = parsedSections.preTableText,
                        receiptFontSize = receiptFontSize,
                        receiptLineHeight = receiptLineHeight,
                        textColor = textColor,
                        separatorColor = separatorColor
                    )
                }

                VoucherItemTable(
                    table = parsedSections.table,
                    is80mm = is80mm,
                    receiptFontSize = receiptFontSize,
                    receiptLineHeight = receiptLineHeight,
                    textColor = textColor,
                    separatorColor = separatorColor,
                    language = language,
                    isFullPaid = isFullPaid,
                    modifier = Modifier.width(tableWidth)
                )

                if (parsedSections.postTableText.isNotBlank()) {
                    ReceiptBodyWithFullPaidBadge(
                        text = parsedSections.postTableText.trimStart('\n'),
                        receiptFontSize = receiptFontSize,
                        receiptLineHeight = receiptLineHeight,
                        textColor = textColor,
                        separatorColor = separatorColor,
                        language = language,
                        isFullPaid = isFullPaid
                    )
                }
            }
        }
    } else {
        ReceiptBodyWithFullPaidBadge(
            text = bodyText,
            receiptFontSize = receiptFontSize,
            receiptLineHeight = receiptLineHeight,
            textColor = textColor,
            separatorColor = separatorColor,
            language = language,
            isFullPaid = isFullPaid
        )
    }
}

@Composable
private fun ReceiptPreTableSection(
    preTableText: String,
    receiptFontSize: TextUnit,
    receiptLineHeight: TextUnit,
    textColor: Color,
    separatorColor: Color
) {
    val allLines = preTableText.trimEnd().lines()
    val metadataLineIdx = allLines.indexOfFirst { line ->
        val t = line.trim()
        t.startsWith("Date:", ignoreCase = true) ||
            t.startsWith("Order ID:", ignoreCase = true) ||
            t.startsWith("Customer:", ignoreCase = true) ||
            t.startsWith("Operator:", ignoreCase = true) ||
            t.startsWith("তারিখ:", ignoreCase = true) ||
            t.startsWith("অর্ডার আইডি:", ignoreCase = true) ||
            t.startsWith("অর্ডার নম্বর:", ignoreCase = true) ||
            t.startsWith("ক্রেতা:", ignoreCase = true) ||
            t.startsWith("গ্রাহক:", ignoreCase = true) ||
            t.startsWith("অপারেটর:", ignoreCase = true)
    }
    val headerEndIdx = if (metadataLineIdx != -1) {
        if (metadataLineIdx > 0 && isMajorSeparatorLine(allLines[metadataLineIdx - 1])) {
            metadataLineIdx - 1
        } else {
            metadataLineIdx
        }
    } else {
        allLines.size
    }

    val headerRegionLines = allLines.subList(0, headerEndIdx)
    val metadataRegionLines = allLines.subList(headerEndIdx, allLines.size)

    if (headerRegionLines.isNotEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("voucher_header_details"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            for (line in headerRegionLines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue
                if (isMajorSeparatorLine(trimmed) || isMinorSeparatorLine(trimmed)) {
                    Text(
                        text = trimmed,
                        modifier = Modifier.wrapContentWidth(Alignment.CenterHorizontally),
                        fontFamily = FontFamily.Monospace,
                        fontSize = receiptFontSize,
                        lineHeight = receiptLineHeight,
                        softWrap = false,
                        color = separatorColor
                    )
                } else {
                    val localizedLine = localizePreTableLine(trimmed)
                    Text(
                        text = localizedLine,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        fontFamily = FontFamily.Monospace,
                        fontSize = receiptFontSize,
                        lineHeight = receiptLineHeight,
                        textAlign = TextAlign.Center,
                        color = textColor
                    )
                }
            }
        }
    }

    if (metadataRegionLines.isNotEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("voucher_metadata_section"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            for (line in metadataRegionLines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue
                if (isMajorSeparatorLine(trimmed) || isMinorSeparatorLine(trimmed)) {
                    Text(
                        text = trimmed,
                        modifier = Modifier.wrapContentWidth(Alignment.CenterHorizontally),
                        fontFamily = FontFamily.Monospace,
                        fontSize = receiptFontSize,
                        lineHeight = receiptLineHeight,
                        softWrap = false,
                        color = separatorColor
                    )
                } else {
                    val localizedLine = localizePreTableLine(trimmed)
                    Text(
                        text = localizedLine,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        fontFamily = FontFamily.Monospace,
                        fontSize = receiptFontSize,
                        lineHeight = receiptLineHeight,
                        textAlign = TextAlign.Center,
                        color = textColor
                    )
                }
            }
        }
    }
}

fun localizePreTableLine(line: String): String {
    val trimmed = line.trim()
    if (trimmed.isEmpty() || isMajorSeparatorLine(trimmed) || isMinorSeparatorLine(trimmed)) {
        return trimmed
    }

    // 0. Header / Memo Title
    if (trimmed.contains("INVOICE", ignoreCase = true) || trimmed.contains("ক্যাশ মেমো")) {
        val replaced = trimmed
            .replace(Regex("INVOICE\\s*/?\\s*ক্যাশ মেমো|ক্যাশ মেমো\\s*\\(?INVOICE\\)?|INVOICE", RegexOption.IGNORE_CASE), "ক্যাশ মেমো")
            .replace(Regex("\\((?:PAID|DUE|paid|due)\\)"), "")
            .trim()
        return replaced
    }

    // 1. Order ID
    if (trimmed.startsWith("Order ID:", ignoreCase = true) || trimmed.startsWith("অর্ডার আইডি:") || trimmed.startsWith("অর্ডার নম্বর:")) {
        val colonIdx = trimmed.indexOf(':')
        val value = if (colonIdx != -1) trimmed.substring(colonIdx + 1).trim() else ""
        val cleanValue = value.removePrefix("#").trim()
        return "অর্ডার নম্বর: #${DateTimeUtils.toBengaliNumerals(cleanValue)}"
    }

    // 2. Date
    if (trimmed.startsWith("Date:", ignoreCase = true) || trimmed.startsWith("তারিখ:")) {
        val colonIdx = trimmed.indexOf(':')
        val value = if (colonIdx != -1) trimmed.substring(colonIdx + 1).trim() else ""
        val bnDate = localizeDateString(value)
        return "তারিখ: $bnDate"
    }

    // 3. Operator
    if (trimmed.startsWith("Operator:", ignoreCase = true) || trimmed.startsWith("অপারেটর:")) {
        val colonIdx = trimmed.indexOf(':')
        val value = if (colonIdx != -1) trimmed.substring(colonIdx + 1).trim() else ""
        val transliterated = BengaliTransliterationEngine.transliterate(value)
        return "অপারেটর: $transliterated"
    }

    // 4. Customer
    if (trimmed.startsWith("Customer:", ignoreCase = true) || trimmed.startsWith("ক্রেতা:") || trimmed.startsWith("গ্রাহক:")) {
        val colonIdx = trimmed.indexOf(':')
        val value = if (colonIdx != -1) trimmed.substring(colonIdx + 1).trim() else ""
        val transliterated = BengaliTransliterationEngine.transliterate(value)
        val bnCustomer = DateTimeUtils.toBengaliNumerals(transliterated)
        return "গ্রাহক: $bnCustomer"
    }

    // 5. Phone
    if (trimmed.startsWith("Phone:", ignoreCase = true) || trimmed.startsWith("ফোন:")) {
        val colonIdx = trimmed.indexOf(':')
        val value = if (colonIdx != -1) trimmed.substring(colonIdx + 1).trim() else ""
        return "ফোন: ${DateTimeUtils.toBengaliNumerals(value)}"
    }

    // 6. Branch
    if (trimmed.startsWith("Branch", ignoreCase = true) || trimmed.startsWith("শাখা")) {
        val bnBranch = trimmed
            .replace(Regex("Branch\\s*#?", RegexOption.IGNORE_CASE), "শাখা #")
        return DateTimeUtils.toBengaliNumerals(BengaliTransliterationEngine.transliterate(bnBranch))
    }

    // Default address or header text: apply transliteration and numeral conversion
    return DateTimeUtils.toBengaliNumerals(BengaliTransliterationEngine.transliterate(trimmed))
}

fun localizeDateString(rawDate: String): String {
    var result = rawDate
    val months = listOf(
        "January" to "জানুয়ারি", "February" to "ফেব্রুয়ারি", "March" to "মার্চ",
        "April" to "এপ্রিল", "May" to "মে", "June" to "জুন",
        "July" to "জুলাই", "August" to "আগস্ট", "September" to "সেপ্টেম্বর",
        "October" to "অক্টোবর", "November" to "নভেম্বর", "December" to "ডিসেম্বর",
        "Jan" to "জানুয়ারি", "Feb" to "ফেব্রুয়ারি", "Mar" to "মার্চ",
        "Apr" to "এপ্রিল", "Jun" to "জুন", "Jul" to "জুলাই",
        "Aug" to "আগস্ট", "Sep" to "সেপ্টেম্বর", "Oct" to "অক্টোবর",
        "Nov" to "নভেম্বর", "Dec" to "ডিসেম্বর"
    )
    for ((enM, bnM) in months) {
        result = result.replace(enM, bnM, ignoreCase = true)
    }
    result = result.replace("AM", "পূর্বাহ্ন", ignoreCase = true)
        .replace("PM", "অপরাহ্ন", ignoreCase = true)
    return DateTimeUtils.toBengaliNumerals(result)
}

@Composable
private fun ReceiptBodyWithFullPaidBadge(
    text: String,
    receiptFontSize: TextUnit,
    receiptLineHeight: TextUnit,
    textColor: Color,
    separatorColor: Color = textColor,
    language: String = "en",
    isFullPaid: Boolean = true
) {
    val allLines = text.trimEnd().lines()

    val statusLineIdx = allLines.indexOfFirst {
        it.contains("FULL PAID") || it.contains("সম্পূর্ণ পরিশোধিত") || it.contains("✓") ||
            it.contains("বকেয়া") || it.contains("DUE") || it.contains("UNPAID") || it.contains("✕")
    }

    val sepBeforeStatusIdx = if (statusLineIdx != -1) {
        (statusLineIdx - 1 downTo 0).firstOrNull { isMajorSeparatorLine(allLines[it]) || isMinorSeparatorLine(allLines[it]) }
    } else null

    val splitIdx = sepBeforeStatusIdx ?: if (statusLineIdx != -1) statusLineIdx else null

    if (splitIdx != null) {
        val totalsText = if (sepBeforeStatusIdx != null) {
            allLines.subList(0, sepBeforeStatusIdx + 1).joinToString("\n").trimEnd()
        } else {
            allLines.subList(0, statusLineIdx).joinToString("\n").trimEnd()
        }
        val footerRegionLines = if (sepBeforeStatusIdx != null) {
            allLines.subList(sepBeforeStatusIdx + 1, allLines.size)
        } else {
            allLines.subList(statusLineIdx, allLines.size)
        }

        if (totalsText.isNotBlank()) {
            Text(
                text = DateTimeUtils.toBengaliNumerals(BengaliTransliterationEngine.transliterate(totalsText)),
                modifier = Modifier.wrapContentWidth(Alignment.CenterHorizontally),
                fontFamily = FontFamily.Monospace,
                fontSize = receiptFontSize,
                lineHeight = receiptLineHeight,
                softWrap = false,
                color = textColor
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("voucher_footer_section"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            for (line in footerRegionLines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue
                when {
                    trimmed.contains("FULL PAID") || trimmed.contains("সম্পূর্ণ পরিশোধিত") || trimmed.contains("✓") -> {
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFE8F5E9),
                            border = BorderStroke(1.5.dp, Color(0xFF2E7D32)),
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .testTag("voucher_paid_badge")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Paid",
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "সম্পূর্ণ পরিশোধিত",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color(0xFF2E7D32)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    trimmed.contains("বকেয়া") || trimmed.contains("DUE") || trimmed.contains("UNPAID") || trimmed.contains("✕") -> {
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFFEBEE),
                            border = BorderStroke(1.5.dp, Color(0xFFC62828)),
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .testTag("voucher_due_badge")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Due",
                                    tint = Color(0xFFC62828),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "বকেয়া বিল",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color(0xFFC62828)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    isMajorSeparatorLine(trimmed) || isMinorSeparatorLine(trimmed) -> {
                        Text(
                            text = trimmed,
                            modifier = Modifier.wrapContentWidth(Alignment.CenterHorizontally),
                            fontFamily = FontFamily.Monospace,
                            fontSize = receiptFontSize,
                            lineHeight = receiptLineHeight,
                            softWrap = false,
                            color = separatorColor
                        )
                    }
                    else -> {
                        val transliterated = BengaliTransliterationEngine.transliterate(trimmed)
                        val localizedFooter = transliterated
                            .replace("Thank you", "ধন্যবাদ", ignoreCase = true)
                            .replace("Thanks", "ধন্যবাদ", ignoreCase = true)
                            .replace("Visit again", "আবার আসবেন", ignoreCase = true)
                        Text(
                            text = DateTimeUtils.toBengaliNumerals(localizedFooter),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            fontFamily = FontFamily.Monospace,
                            fontSize = receiptFontSize,
                            lineHeight = receiptLineHeight,
                            textAlign = TextAlign.Center,
                            color = textColor
                        )
                    }
                }
            }
        }
    } else {
        Text(
            text = DateTimeUtils.toBengaliNumerals(BengaliTransliterationEngine.transliterate(text)),
            modifier = Modifier.wrapContentWidth(Alignment.CenterHorizontally),
            fontFamily = FontFamily.Monospace,
            fontSize = receiptFontSize,
            lineHeight = receiptLineHeight,
            softWrap = false,
            color = textColor
        )
    }
}

fun localizeUnit(unit: String): String {
    val trimmed = unit.trim()
    return when (trimmed.lowercase(Locale.ROOT)) {
        "l", "ltr", "liter", "litre" -> "লিটার"
        "kg" -> "কেজি"
        "gm", "g" -> "গ্রাম"
        "pcs", "pc" -> "পিস"
        "unit" -> "একক"
        "pack" -> "প্যাক"
        "box" -> "বক্স"
        "ml" -> "মিলি"
        "" -> ""
        else -> BengaliTransliterationEngine.transliterate(trimmed)
    }
}

fun buildEffectivePreviewSummaryRows(
    parsedSummaryRows: List<VoucherSummaryRow>,
    isFullPaid: Boolean
): List<VoucherSummaryRow> {
    val subtotalRow = parsedSummaryRows.firstOrNull {
        it.label.contains("উপমোট") || it.label.contains("উপ-মোট") || it.label.contains("Sub Total", ignoreCase = true)
    }
    val totalRow = parsedSummaryRows.firstOrNull {
        it.label.contains("মোট") || it.label.contains("সর্বমোট") || it.label.contains("TOTAL", ignoreCase = true) || it.label.contains("NET PAYABLE", ignoreCase = true)
    }
    val paidRow = parsedSummaryRows.firstOrNull {
        it.label.contains("জমা") || it.label.contains("পরিশোধ") || it.label.contains("Paid", ignoreCase = true)
    }
    val dueRow = parsedSummaryRows.firstOrNull {
        it.label.contains("বাকি") || it.label.contains("বকেয়া") || it.label.contains("বকেয়া") || it.label.contains("Due", ignoreCase = true)
    }

    val result = mutableListOf<VoucherSummaryRow>()

    if (subtotalRow != null) {
        val cleanAmount = subtotalRow.amount.replace("৳", "").replace("BDT", "", ignoreCase = true).trim()
        val bnAmount = "৳${DateTimeUtils.toBengaliNumerals(cleanAmount)}"
        result.add(VoucherSummaryRow(label = "উপ-মোট", amount = bnAmount, isBold = false, isDue = false))
    }

    val totalAmountRaw = totalRow?.amount?.replace("৳", "")?.replace("BDT", "", ignoreCase = true)?.trim() ?: "0.00"
    val totalBnAmount = "৳${DateTimeUtils.toBengaliNumerals(totalAmountRaw)}"
    result.add(VoucherSummaryRow(label = "মোট", amount = totalBnAmount, isBold = true, isDue = false))

    val paidAmountRaw = paidRow?.amount?.replace("৳", "")?.replace("BDT", "", ignoreCase = true)?.trim()
    val dueAmountRaw = dueRow?.amount?.replace("৳", "")?.replace("BDT", "", ignoreCase = true)?.trim()

    val effectivePaidStr: String
    val effectiveDueStr: String

    if (paidAmountRaw != null && dueAmountRaw != null) {
        effectivePaidStr = paidAmountRaw
        effectiveDueStr = dueAmountRaw
    } else if (paidAmountRaw != null) {
        effectivePaidStr = paidAmountRaw
        val totalDouble = totalAmountRaw.toDoubleOrNull() ?: 0.0
        val paidDouble = paidAmountRaw.toDoubleOrNull() ?: 0.0
        val calculatedDue = (totalDouble - paidDouble).coerceAtLeast(0.0)
        effectiveDueStr = String.format(Locale.US, "%.2f", calculatedDue)
    } else if (dueAmountRaw != null) {
        effectiveDueStr = dueAmountRaw
        val totalDouble = totalAmountRaw.toDoubleOrNull() ?: 0.0
        val dueDouble = dueAmountRaw.toDoubleOrNull() ?: 0.0
        val calculatedPaid = (totalDouble - dueDouble).coerceAtLeast(0.0)
        effectivePaidStr = String.format(Locale.US, "%.2f", calculatedPaid)
    } else if (isFullPaid) {
        effectivePaidStr = totalAmountRaw
        effectiveDueStr = "0.00"
    } else {
        effectivePaidStr = "0.00"
        effectiveDueStr = totalAmountRaw
    }

    val paidBnAmount = "৳${DateTimeUtils.toBengaliNumerals(effectivePaidStr)}"
    val dueBnAmount = "৳${DateTimeUtils.toBengaliNumerals(effectiveDueStr)}"

    result.add(VoucherSummaryRow(label = "জমা", amount = paidBnAmount, isBold = false, isDue = false))
    result.add(VoucherSummaryRow(label = "বকেয়া", amount = dueBnAmount, isBold = true, isDue = true))

    return result
}

@Composable
private fun VoucherItemTable(
    table: ParsedVoucherTable,
    is80mm: Boolean,
    receiptFontSize: TextUnit,
    receiptLineHeight: TextUnit,
    textColor: Color,
    separatorColor: Color,
    language: String = "en",
    isFullPaid: Boolean = true,
    modifier: Modifier = Modifier
) {
    val cellFontSize = if (is80mm) receiptFontSize else 10.5.sp
    val cellLineHeight = if (is80mm) receiptLineHeight else 14.sp
    val minorSeparator = "-".repeat(table.separatorWidth)

    Column(
        modifier = modifier.testTag("voucher_item_table"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Table Column Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 1.dp)
                .testTag("voucher_item_table_header"),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            if (table.isFiveColumn) {
                Text(
                    text = "বিবরণ",
                    modifier = Modifier.weight(1.85f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = cellFontSize,
                    lineHeight = cellLineHeight,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Start,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = textColor
                )
                Text(
                    text = "পরিমাণ",
                    modifier = Modifier.weight(0.60f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = cellFontSize,
                    lineHeight = cellLineHeight,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    softWrap = false,
                    color = textColor
                )
                Text(
                    text = "একক",
                    modifier = Modifier.weight(0.60f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = cellFontSize,
                    lineHeight = cellLineHeight,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Start,
                    maxLines = 1,
                    softWrap = false,
                    color = textColor
                )
                Text(
                    text = "দর",
                    modifier = Modifier.weight(0.85f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = cellFontSize,
                    lineHeight = cellLineHeight,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    softWrap = false,
                    color = textColor
                )
                Text(
                    text = "মোট",
                    modifier = Modifier.weight(0.95f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = cellFontSize,
                    lineHeight = cellLineHeight,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    softWrap = false,
                    color = textColor
                )
            } else {
                Text(
                    text = "পণ্য",
                    modifier = Modifier.weight(1.35f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = cellFontSize,
                    lineHeight = cellLineHeight,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Start,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = textColor
                )
                Text(
                    text = "পরিমাণ",
                    modifier = Modifier.weight(0.75f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = cellFontSize,
                    lineHeight = cellLineHeight,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    softWrap = false,
                    color = textColor
                )
                Text(
                    text = "দর",
                    modifier = Modifier.weight(0.90f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = cellFontSize,
                    lineHeight = cellLineHeight,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    softWrap = false,
                    color = textColor
                )
                Text(
                    text = "মোট",
                    modifier = Modifier.weight(1.00f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = cellFontSize,
                    lineHeight = cellLineHeight,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    softWrap = false,
                    color = textColor
                )
            }
        }

        // Top Table Minor Separator
        Text(
            text = minorSeparator,
            fontFamily = FontFamily.Monospace,
            fontSize = receiptFontSize,
            lineHeight = receiptLineHeight,
            softWrap = false,
            color = separatorColor
        )

        // Item Rows
        table.rows.forEachIndexed { index, row ->
            val localizedName = BengaliTransliterationEngine.transliterate(row.itemName)
            val localizedQty = DateTimeUtils.toBengaliNumerals(row.qty)
            val localizedUnit = localizeUnit(row.unit)
            val localizedRate = DateTimeUtils.toBengaliNumerals(row.rate)
            val localizedTotal = DateTimeUtils.toBengaliNumerals(row.total)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .testTag("voucher_item_row_$index"),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Top
            ) {
                if (table.isFiveColumn) {
                    Text(
                        text = localizedName,
                        modifier = Modifier
                            .weight(1.85f)
                            .testTag("voucher_item_name_$index"),
                        fontFamily = FontFamily.Monospace,
                        fontSize = cellFontSize,
                        lineHeight = cellLineHeight,
                        textAlign = TextAlign.Start,
                        color = textColor
                    )
                    Text(
                        text = localizedQty,
                        modifier = Modifier
                            .weight(0.60f)
                            .testTag("voucher_item_qty_$index"),
                        fontFamily = FontFamily.Monospace,
                        fontSize = if (localizedQty.length > 5) (cellFontSize.value - 1f).sp else cellFontSize,
                        lineHeight = cellLineHeight,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        color = textColor
                    )
                    Text(
                        text = localizedUnit,
                        modifier = Modifier
                            .weight(0.60f)
                            .testTag("voucher_item_unit_$index"),
                        fontFamily = FontFamily.Monospace,
                        fontSize = if (localizedUnit.length > 5) (cellFontSize.value - 1f).sp else cellFontSize,
                        lineHeight = cellLineHeight,
                        textAlign = TextAlign.Start,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        color = textColor
                    )
                    Text(
                        text = localizedRate,
                        modifier = Modifier
                            .weight(0.85f)
                            .testTag("voucher_item_rate_$index"),
                        fontFamily = FontFamily.Monospace,
                        fontSize = if (localizedRate.length > 8) (cellFontSize.value - 1f).sp else cellFontSize,
                        lineHeight = cellLineHeight,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        color = textColor
                    )
                    Text(
                        text = localizedTotal,
                        modifier = Modifier
                            .weight(0.95f)
                            .testTag("voucher_item_total_$index"),
                        fontFamily = FontFamily.Monospace,
                        fontSize = if (localizedTotal.length > 8) (cellFontSize.value - 1f).sp else cellFontSize,
                        lineHeight = cellLineHeight,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        color = textColor
                    )
                } else {
                    val localized4ColQty = DateTimeUtils.toBengaliNumerals(BengaliTransliterationEngine.transliterate(row.qty))
                    Text(
                        text = localizedName,
                        modifier = Modifier
                            .weight(1.35f)
                            .testTag("voucher_item_name_$index"),
                        fontFamily = FontFamily.Monospace,
                        fontSize = cellFontSize,
                        lineHeight = cellLineHeight,
                        textAlign = TextAlign.Start,
                        color = textColor
                    )
                    Text(
                        text = localized4ColQty,
                        modifier = Modifier
                            .weight(0.75f)
                            .testTag("voucher_item_qty_$index"),
                        fontFamily = FontFamily.Monospace,
                        fontSize = if (localized4ColQty.length > 6) (cellFontSize.value - 1f).sp else cellFontSize,
                        lineHeight = cellLineHeight,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        color = textColor
                    )
                    Text(
                        text = localizedRate,
                        modifier = Modifier
                            .weight(0.90f)
                            .testTag("voucher_item_rate_$index"),
                        fontFamily = FontFamily.Monospace,
                        fontSize = if (localizedRate.length > 7) (cellFontSize.value - 1f).sp else cellFontSize,
                        lineHeight = cellLineHeight,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        color = textColor
                    )
                    Text(
                        text = localizedTotal,
                        modifier = Modifier
                            .weight(1.00f)
                            .testTag("voucher_item_total_$index"),
                        fontFamily = FontFamily.Monospace,
                        fontSize = if (localizedTotal.length > 8) (cellFontSize.value - 1f).sp else cellFontSize,
                        lineHeight = cellLineHeight,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        color = textColor
                    )
                }
            }
        }

        // Bottom Table Minor Separator
        Text(
            text = minorSeparator,
            fontFamily = FontFamily.Monospace,
            fontSize = receiptFontSize,
            lineHeight = receiptLineHeight,
            softWrap = false,
            color = separatorColor
        )

        // Summary Rows (Subtotal, TOTAL, Paid, Due) guaranteed to display in Bengali
        val effectiveSummary = remember(table.summaryRows, isFullPaid) {
            buildEffectivePreviewSummaryRows(table.summaryRows, isFullPaid)
        }

        effectiveSummary.forEachIndexed { sIdx, sRow ->
            val tag = if (sRow.isDue) "voucher_due_row" else if (sRow.label.contains("জমা")) "voucher_paid_row" else if (sRow.isBold) "voucher_total_row" else "voucher_subtotal_row"
            val rowColor = if (sRow.isDue) Color(0xFFC62828) else textColor
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 1.dp)
                    .testTag(tag),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (table.isFiveColumn) {
                    Text(
                        text = sRow.label,
                        modifier = Modifier
                            .weight(1.85f)
                            .testTag("${tag}_label"),
                        fontFamily = FontFamily.Monospace,
                        fontSize = cellFontSize,
                        lineHeight = cellLineHeight,
                        fontWeight = if (sRow.isBold) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Start,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = rowColor
                    )
                    Spacer(modifier = Modifier.weight(0.60f))
                    Spacer(modifier = Modifier.weight(0.60f))
                    Spacer(modifier = Modifier.weight(0.85f))
                    Text(
                        text = sRow.amount,
                        modifier = Modifier
                            .weight(0.95f)
                            .testTag("${tag}_amount"),
                        fontFamily = FontFamily.Monospace,
                        fontSize = cellFontSize,
                        lineHeight = cellLineHeight,
                        fontWeight = if (sRow.isBold) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        softWrap = false,
                        color = rowColor
                    )
                } else {
                    Text(
                        text = sRow.label,
                        modifier = Modifier
                            .weight(1.35f)
                            .testTag("${tag}_label"),
                        fontFamily = FontFamily.Monospace,
                        fontSize = cellFontSize,
                        lineHeight = cellLineHeight,
                        fontWeight = if (sRow.isBold) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Start,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = rowColor
                    )
                    Spacer(modifier = Modifier.weight(0.75f))
                    Spacer(modifier = Modifier.weight(0.90f))
                    Text(
                        text = sRow.amount,
                        modifier = Modifier
                            .weight(1.00f)
                            .testTag("${tag}_amount"),
                        fontFamily = FontFamily.Monospace,
                        fontSize = cellFontSize,
                        lineHeight = cellLineHeight,
                        fontWeight = if (sRow.isBold) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        softWrap = false,
                        color = rowColor
                    )
                }
            }
        }
    }
}

private val DETAIL_58MM_REGEX = Regex(
    """^\s+(.+?)\s+x\s+([\d\u09E6-\u09EF]+(?:\.[\d\u09E6-\u09EF]*)?)\s+([\d\u09E6-\u09EF]+(?:\.[\d\u09E6-\u09EF]*)?)\s*(?:BDT)?\s*$"""
)

private val SINGLE_58MM_REGEX = Regex(
    """^(.+?)\s+(\S+)\s+([\d\u09E6-\u09EF]+(?:\.[\d\u09E6-\u09EF]*)?)\s+([\d\u09E6-\u09EF]+(?:\.[\d\u09E6-\u09EF]*)?)\s*$"""
)

private val ROW_80MM_REGEX = Regex(
    """^(.+?)\s+([\d\u09E6-\u09EF]+(?:\.[\d\u09E6-\u09EF]*)?)\s+(\S+(?:\s+\S+)?)\s+([\d\u09E6-\u09EF]+(?:\.[\d\u09E6-\u09EF]*)?)\s+([\d\u09E6-\u09EF]+(?:\.[\d\u09E6-\u09EF]*)?)\s*$"""
)

private val ROW_80MM_NO_UNIT_REGEX = Regex(
    """^(.+?)\s+([\d\u09E6-\u09EF]+(?:\.[\d\u09E6-\u09EF]*)?)\s{2,}([\d\u09E6-\u09EF]+(?:\.[\d\u09E6-\u09EF]*)?)\s+([\d\u09E6-\u09EF]+(?:\.[\d\u09E6-\u09EF]*)?)\s*$"""
)

private fun isMajorSeparatorLine(line: String): Boolean {
    val trimmed = line.trim()
    return trimmed.length >= 16 && trimmed.all { it == '=' }
}

private fun isMinorSeparatorLine(line: String): Boolean {
    val trimmed = line.trim()
    return trimmed.length >= 16 && trimmed.all { it == '-' }
}

private fun extractAmountFromSummaryLine(line: String): String {
    val clean = line.replace("৳", " ").replace("BDT", " ", ignoreCase = true).trim()
    val tokens = clean.split(Regex("\\s+")).filter { it.isNotBlank() }
    return tokens.lastOrNull() ?: ""
}

fun parseReceiptSectionsForPreview(
    bodyText: String,
    explicitLines: List<InvoiceLineEntity> = emptyList(),
    language: String = "en"
): ParsedReceiptBodySections? {
    val allLines = bodyText.lines()
    val tableHeaderIdx = allLines.indexOfFirst { line ->
        val t = line.trim()
        val hasNameOrDesc = t.startsWith("Item Name") || t.startsWith("Item Description") ||
                t.startsWith("পণ্যের নাম") || t.startsWith("বিবরণ")
        val hasQty = t.contains("Qty") || t.contains("পরিমাণ")
        val hasRate = t.contains("Rate") || t.contains("দর")
        val hasTotal = t.contains("Total") || t.contains("মোট")
        hasNameOrDesc && hasQty && hasRate && hasTotal
    }
    if (tableHeaderIdx == -1) return null

    val headerLine = allLines[tableHeaderIdx].trim()
    val isFiveColumn = headerLine.startsWith("Item Description") || headerLine.startsWith("বিবরণ")
    val topSepIdx = if (tableHeaderIdx + 1 < allLines.size && isMinorSeparatorLine(allLines[tableHeaderIdx + 1])) {
        tableHeaderIdx + 1
    } else {
        tableHeaderIdx
    }
    val bottomSepIdx = ((topSepIdx + 1) until allLines.size).firstOrNull { idx ->
        isMinorSeparatorLine(allLines[idx])
    } ?: return null

    val sepWidth = if (topSepIdx > tableHeaderIdx) {
        allLines[topSepIdx].trim().length
    } else if (isFiveColumn) {
        ThermalReceiptFormatter.WIDTH_80MM
    } else {
        ThermalReceiptFormatter.WIDTH_58MM
    }

    val preTableText = allLines.subList(0, tableHeaderIdx).joinToString("\n")
    val rawItemLines = allLines.subList(topSepIdx + 1, bottomSepIdx)

    // Parse summary rows (Subtotal and TOTAL) following bottom separator
    val summaryRows = mutableListOf<VoucherSummaryRow>()
    var postTableStartIdx = bottomSepIdx + 1

    for (idx in (bottomSepIdx + 1) until allLines.size) {
        val line = allLines[idx]
        val trimmed = line.trim()
        if (trimmed.isEmpty()) {
            postTableStartIdx = idx + 1
            continue
        }

        val isSubTotal = trimmed.startsWith("Sub Total", ignoreCase = true) || trimmed.startsWith("উপমোট")
        val isTotal = trimmed.startsWith("TOTAL", ignoreCase = true) ||
                trimmed.startsWith("NET PAYABLE", ignoreCase = true) ||
                trimmed.startsWith("মোট") ||
                trimmed.startsWith("সর্বমোট")
        val isPaidRow = trimmed.startsWith("Paid:", ignoreCase = true) ||
                trimmed.startsWith("জমা (Paid):") ||
                trimmed.startsWith("জমা:") ||
                trimmed.startsWith("পরিশোধ:") ||
                trimmed.startsWith("পরিশোধিত:")
        val isDueRow = trimmed.startsWith("Due:", ignoreCase = true) ||
                trimmed.startsWith("বাকি (Due):") ||
                trimmed.startsWith("বাকি:") ||
                trimmed.startsWith("বকেয়া:") ||
                trimmed.startsWith("বকেয়া:")

        if (isSubTotal) {
            val amount = extractAmountFromSummaryLine(trimmed)
            val label = if (trimmed.startsWith("উপমোট")) "উপমোট" else "Sub Total"
            summaryRows.add(VoucherSummaryRow(label = label, amount = amount, isBold = false))
            postTableStartIdx = idx + 1
        } else if (isTotal) {
            val amount = extractAmountFromSummaryLine(trimmed)
            val label = if (trimmed.startsWith("মোট") || trimmed.startsWith("সর্বমোট")) "মোট" else "TOTAL"
            summaryRows.add(VoucherSummaryRow(label = label, amount = amount, isBold = true))
            postTableStartIdx = idx + 1
        } else if (isPaidRow) {
            val amount = extractAmountFromSummaryLine(trimmed)
            val label = if (trimmed.startsWith("Paid:", ignoreCase = true)) "Paid:" else "জমা (Paid):"
            summaryRows.add(VoucherSummaryRow(label = label, amount = amount, isBold = false, isDue = false))
            postTableStartIdx = idx + 1
        } else if (isDueRow) {
            val amount = extractAmountFromSummaryLine(trimmed)
            val label = if (trimmed.startsWith("Due:", ignoreCase = true)) "Due:" else "বাকি (Due):"
            summaryRows.add(VoucherSummaryRow(label = label, amount = amount, isBold = true, isDue = true))
            postTableStartIdx = idx + 1
        } else {
            // Reached non-summary line (FULL PAID, DUE stamp, separator, or footer)
            break
        }
    }

    val postTableText = allLines.subList(postTableStartIdx, allLines.size).joinToString("\n")

    val rows = if (explicitLines.isNotEmpty()) {
        explicitLines.map { item ->
            val rateStr = FinancialCalculationEngine.formatPaisaToBdt(item.unitPricePaisa)
            val totalStr = FinancialCalculationEngine.formatPaisaToBdt(item.lineTotalPaisa)
            if (isFiveColumn) {
                VoucherItemTableRow(
                    itemName = item.itemName,
                    qty = item.quantityStr.trim(),
                    unit = item.unit.trim().take(5),
                    rate = rateStr,
                    total = totalStr
                )
            } else {
                VoucherItemTableRow(
                    itemName = item.itemName,
                    qty = "${item.quantityStr.trim()}${item.unit.trim().take(2)}".trim(),
                    unit = "",
                    rate = rateStr,
                    total = totalStr
                )
            }
        }
    } else if (isFiveColumn) {
        parse80mmItemLines(rawItemLines)
    } else {
        parse58mmItemLines(rawItemLines)
    }

    return ParsedReceiptBodySections(
        preTableText = preTableText,
        table = ParsedVoucherTable(
            isFiveColumn = isFiveColumn,
            separatorWidth = sepWidth,
            rows = rows,
            summaryRows = summaryRows
        ),
        postTableText = postTableText
    )
}

private fun parse58mmItemLines(rawItemLines: List<String>): List<VoucherItemTableRow> {
    val rows = mutableListOf<VoucherItemTableRow>()
    val pendingNameLines = mutableListOf<String>()

    for (i in rawItemLines.indices) {
        val line = rawItemLines[i]
        if (line.isBlank()) continue

        val detailMatch = DETAIL_58MM_REGEX.matchEntire(line)
        if (detailMatch != null) {
            val fullName = if (pendingNameLines.isNotEmpty()) {
                pendingNameLines.joinToString(" ") { it.trim() }
            } else {
                "Item"
            }
            pendingNameLines.clear()
            rows.add(
                VoucherItemTableRow(
                    itemName = fullName,
                    qty = detailMatch.groupValues[1].trim(),
                    unit = "",
                    rate = detailMatch.groupValues[2].trim(),
                    total = detailMatch.groupValues[3].trim()
                )
            )
            continue
        }

        val singleMatch = SINGLE_58MM_REGEX.matchEntire(line)
        val nextIsDetail = (i + 1 < rawItemLines.size) && DETAIL_58MM_REGEX.matches(rawItemLines[i + 1])
        if (singleMatch != null && pendingNameLines.isEmpty() && !nextIsDetail) {
            val candidateName = singleMatch.groupValues[1].trim()
            if (ThermalReceiptFormatter.getVisualWidth(candidateName) <= 12) {
                rows.add(
                    VoucherItemTableRow(
                        itemName = candidateName,
                        qty = singleMatch.groupValues[2].trim(),
                        unit = "",
                        rate = singleMatch.groupValues[3].trim(),
                        total = singleMatch.groupValues[4].trim()
                    )
                )
                continue
            }
        }

        pendingNameLines.add(line.trim())
    }

    if (pendingNameLines.isNotEmpty()) {
        for (pending in pendingNameLines) {
            val fallbackMatch = SINGLE_58MM_REGEX.matchEntire(pending)
            if (fallbackMatch != null) {
                rows.add(
                    VoucherItemTableRow(
                        itemName = fallbackMatch.groupValues[1].trim(),
                        qty = fallbackMatch.groupValues[2].trim(),
                        unit = "",
                        rate = fallbackMatch.groupValues[3].trim(),
                        total = fallbackMatch.groupValues[4].trim()
                    )
                )
            } else {
                rows.add(
                    VoucherItemTableRow(
                        itemName = pending,
                        qty = "",
                        unit = "",
                        rate = "",
                        total = ""
                    )
                )
            }
        }
    }

    return rows
}

private fun parse80mmItemLines(rawItemLines: List<String>): List<VoucherItemTableRow> {
    val rows = mutableListOf<VoucherItemTableRow>()

    for (line in rawItemLines) {
        if (line.isBlank()) continue

        val match = ROW_80MM_REGEX.matchEntire(line)
        if (match != null) {
            rows.add(
                VoucherItemTableRow(
                    itemName = match.groupValues[1].trim(),
                    qty = match.groupValues[2].trim(),
                    unit = match.groupValues[3].trim(),
                    rate = match.groupValues[4].trim(),
                    total = match.groupValues[5].trim()
                )
            )
            continue
        }

        val noUnitMatch = ROW_80MM_NO_UNIT_REGEX.matchEntire(line)
        if (noUnitMatch != null) {
            rows.add(
                VoucherItemTableRow(
                    itemName = noUnitMatch.groupValues[1].trim(),
                    qty = noUnitMatch.groupValues[2].trim(),
                    unit = "",
                    rate = noUnitMatch.groupValues[3].trim(),
                    total = noUnitMatch.groupValues[4].trim()
                )
            )
            continue
        }

        if (rows.isNotEmpty()) {
            val lastIdx = rows.lastIndex
            val prev = rows[lastIdx]
            rows[lastIdx] = prev.copy(
                itemName = "${prev.itemName} ${line.trim()}".trim()
            )
        } else {
            rows.add(
                VoucherItemTableRow(
                    itemName = line.trim(),
                    qty = "",
                    unit = "",
                    rate = "",
                    total = ""
                )
            )
        }
    }

    return rows
}

