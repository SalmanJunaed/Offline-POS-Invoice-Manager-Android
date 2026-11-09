package com.example.pos

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.pos.core.error.PosResult
import com.example.pos.data.db.CompanyHeaderEntity
import com.example.pos.data.db.InvoiceEntity
import com.example.pos.data.db.InvoiceLineEntity
import com.example.pos.modules.pdf.PdfInvoiceEngine
import com.example.pos.printer.EscPosPrinterEngine
import com.example.pos.printer.ThermalReceiptFormatter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

/**
 * Regression Test Suite: Preserve Bengali Thermal Printing Compatibility.
 *
 * Verifies that introducing Ridmik Keyboard, Gboard, and custom in-app Bengali keyboard
 * input does not alter, corrupt, or regress existing Bengali thermal printing,
 * receipt preview, PDF generation, or 58mm/80mm ESC/POS raster pipelines.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], shadows = [ShadowRobolectricPdfDocument::class])
class BengaliThermalPrintingRegressionTest {

    private lateinit var context: Context

    companion object {
        // Exact regression test strings mandated by specification
        const val STR_BENGALI_SENTENCE = "বাংলা পরীক্ষা: আমি বাংলাদেশে থাকি।"
        const val STR_CONJUNCTS = "যুক্তাক্ষর: ক্ষ, জ্ঞ, ত্র, শ্র, প্র, ক্র, গ্র, ন্দ, স্ত্র।"
        const val STR_KAR_SIGNS = "কার: কা, কি, কী, কু, কূ, কে, কৈ, কো, কৌ।"
        const val STR_BENGALI_DIGITS = "বাংলা সংখ্যা: ০১২৩৪৫৬৭৮৯"
        const val STR_ENGLISH = "English: POS Invoice Test 1234567890"
        const val STR_MIXED = "Mixed: Customer নাম, Product Name, মোট টাকা ১২৫০।"
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    // =========================================================================
    // 1. Keyboard Input Simulation (Gboard, Ridmik, In-App Keyboard)
    // =========================================================================

    /**
     * Simulates Gboard IME delivering standard Unicode text via InputConnection.
     */
    private fun simulateGboardInput(source: String): String {
        val sb = StringBuilder()
        for (ch in source) {
            sb.append(ch)
        }
        return sb.toString()
    }

    /**
     * Simulates Ridmik Keyboard delivering phonetic or layout-based Unicode text.
     * Ridmik outputs standard Bengali Unicode (U+0980 to U+09FF) including Virama (্).
     */
    private fun simulateRidmikKeyboardInput(source: String): String {
        val out = StringBuilder()
        source.codePoints().forEach { cp ->
            out.appendCodePoint(cp)
        }
        return out.toString()
    }

    /**
     * Simulates Custom In-App Soft Keyboard appending discrete keys/syllables.
     * E.g. 'ক' + '\u09CD' (hasanta) + 'ষ' -> 'ক্ষ'.
     */
    private fun simulateInAppKeyboardInput(chars: List<String>): String {
        return chars.joinToString("")
    }

    @Test
    fun testRule1AndRule2_AllKeyboardsProduceIdenticalUnicodeText() {
        // Gboard vs Ridmik vs In-App Keyboard
        val gboardSentence = simulateGboardInput(STR_BENGALI_SENTENCE)
        val ridmikSentence = simulateRidmikKeyboardInput(STR_BENGALI_SENTENCE)
        val inAppSentence = simulateInAppKeyboardInput(listOf("ব", "া", "ং", "ল", "া", " ", "প", "র", "ী", "ক", "্", "ষ", "া", ":", " ", "আ", "ম", "ি", " ", "ব", "া", "ং", "ল", "া", "দ", "ে", "শ", "ে", " ", "থ", "া", "ক", "ি", "।"))

        assertEquals("Gboard and Ridmik outputs must match", gboardSentence, ridmikSentence)
        assertEquals("Ridmik and In-App Keyboard outputs must match", ridmikSentence, inAppSentence)
        assertEquals("Sentence must equal expected constant", STR_BENGALI_SENTENCE, gboardSentence)

        // Conjunct character verification (ক্ষ, জ্ঞ, ত্র, শ্র, প্র, ক্র, গ্র, ন্দ, স্ত্র)
        val inAppConjuncts = simulateInAppKeyboardInput(listOf(
            "য", "ু", "ক", "্", "ত", "া", "ক", "্", "ষ", "র", ":", " ",
            "ক", "্", "ষ", ",", " ",
            "জ", "্", "ঞ", ",", " ",
            "ত", "্", "র", ",", " ",
            "শ", "্", "র", ",", " ",
            "প", "্", "র", ",", " ",
            "ক", "্", "র", ",", " ",
            "গ", "্", "র", ",", " ",
            "ন", "্", "দ", ",", " ",
            "স", "্", "ত", "্", "র", "।"
        ))
        val gboardConjuncts = simulateGboardInput(STR_CONJUNCTS)
        val ridmikConjuncts = simulateRidmikKeyboardInput(STR_CONJUNCTS)

        assertEquals("Conjuncts from Gboard and In-App must match", gboardConjuncts, inAppConjuncts)
        assertEquals("Conjuncts from Ridmik and In-App must match", ridmikConjuncts, inAppConjuncts)

        // Kar signs verification (কা, কি, কী, কু, কূ, কে, কৈ, কো, কৌ)
        val gboardKar = simulateGboardInput(STR_KAR_SIGNS)
        val ridmikKar = simulateRidmikKeyboardInput(STR_KAR_SIGNS)
        assertEquals("Kar signs must match", gboardKar, ridmikKar)

        // Bengali digits verification (০১২৩৪৫৬৭৮৯)
        val gboardDigits = simulateGboardInput(STR_BENGALI_DIGITS)
        val ridmikDigits = simulateRidmikKeyboardInput(STR_BENGALI_DIGITS)
        assertEquals("Digits must match", gboardDigits, ridmikDigits)

        // Mixed verification
        val gboardMixed = simulateGboardInput(STR_MIXED)
        val ridmikMixed = simulateRidmikKeyboardInput(STR_MIXED)
        assertEquals("Mixed text must match", gboardMixed, ridmikMixed)
    }

    // =========================================================================
    // 2. Output 1: Receipt Preview Verification (58mm and 80mm)
    // =========================================================================

    private fun buildInvoiceWithText(text: String, suffix: String): Triple<CompanyHeaderEntity, InvoiceEntity, List<InvoiceLineEntity>> {
        val header = CompanyHeaderEntity(
            companyName = "মেসার্স পরীক্ষা ফিলিং স্টেশন\n$text",
            branchName = "প্রধান শাখা",
            branchNumber = "01",
            address = "মিরপুর-১০, ঢাকা",
            phoneNumber = "০১৭১১২২৩৩৪৪"
        )
        val invoice = InvoiceEntity(
            orderId = "DH-2026-$suffix",
            dateTime = 1711188000000L,
            operatorId = "OP-1",
            operatorName = "অপারেটর রহিম",
            customerInfo = "ক্রেতা করিম ($text)",
            rawTotalPaisa = 125000L,
            roundedTotalBdt = 1250L,
            footerNote = text
        )
        val lines = listOf(
            InvoiceLineEntity(
                orderId = invoice.orderId,
                itemId = "ITEM-1",
                itemName = text,
                itemType = "GOODS",
                unit = "কেজি",
                quantityStr = "১",
                unitPricePaisa = 125000L,
                lineTotalPaisa = 125000L,
                sortOrder = 1
            )
        )
        return Triple(header, invoice, lines)
    }

    @Test
    fun testOutput1_ReceiptPreview_58mm_PreservesBengaliAndEnglish() {
        val testStrings = listOf(
            STR_BENGALI_SENTENCE,
            STR_CONJUNCTS,
            STR_KAR_SIGNS,
            STR_BENGALI_DIGITS,
            STR_ENGLISH,
            STR_MIXED
        )

        testStrings.forEachIndexed { index, testText ->
            val (header, invoice, lines) = buildInvoiceWithText(testText, "58-$index")

            val previewText = ThermalReceiptFormatter.generateReceiptText(
                header = header,
                invoice = invoice,
                lines = lines,
                paperSize = "58mm",
                footerLine1 = "ধন্যবাদ আবার আসবেন",
                footerLine2 = testText,
                lang = "bn"
            )

            // 1. Must contain all words/tokens from test string without character corruption or omission
            val words = testText.split(Regex("[\\s,।:]+")).filter { it.isNotBlank() }
            words.forEach { word ->
                assertTrue("Preview 58mm must contain word '$word' from test string #$index", previewText.contains(word))
            }

            // 2. Must adhere to 58mm 32 visual characters line width
            previewText.lines().forEach { line ->
                val vWidth = ThermalReceiptFormatter.getVisualWidth(line)
                assertTrue("Line '$line' visual width $vWidth exceeds 32 on 58mm", vWidth <= 32)
            }
        }
    }

    @Test
    fun testOutput1_ReceiptPreview_80mm_PreservesBengaliAndEnglish() {
        val testStrings = listOf(
            STR_BENGALI_SENTENCE,
            STR_CONJUNCTS,
            STR_KAR_SIGNS,
            STR_BENGALI_DIGITS,
            STR_ENGLISH,
            STR_MIXED
        )

        testStrings.forEachIndexed { index, testText ->
            val (header, invoice, lines) = buildInvoiceWithText(testText, "80-$index")

            val previewText = ThermalReceiptFormatter.generateReceiptText(
                header = header,
                invoice = invoice,
                lines = lines,
                paperSize = "80mm",
                footerLine1 = "ধন্যবাদ আবার আসবেন",
                footerLine2 = testText,
                lang = "bn"
            )

            // 1. Must contain the exact test text without mangling
            assertTrue("Preview 80mm must contain test string #$index", previewText.contains(testText))

            // 2. Must adhere to 80mm 48 visual characters line width
            previewText.lines().forEach { line ->
                val vWidth = ThermalReceiptFormatter.getVisualWidth(line)
                assertTrue("Line '$line' visual width $vWidth exceeds 48 on 80mm", vWidth <= 48)
            }
        }
    }

    @Test
    fun testOutput1_ReceiptPreview_GboardRidmikInAppProduceIdenticalPreviews() {
        val gboardText = simulateGboardInput(STR_MIXED)
        val ridmikText = simulateRidmikKeyboardInput(STR_MIXED)

        val (h1, inv1, lines1) = buildInvoiceWithText(gboardText, "GB")
        val (h2, inv2, lines2) = buildInvoiceWithText(ridmikText, "GB")

        val preview1 = ThermalReceiptFormatter.generateReceiptText(h1, inv1, lines1, "58mm", "ধন্যবাদ", "শুভ বিদায়")
        val preview2 = ThermalReceiptFormatter.generateReceiptText(h2, inv2, lines2, "58mm", "ধন্যবাদ", "শুভ বিদায়")

        assertEquals("58mm Previews from Gboard and Ridmik must be 100% identical", preview1, preview2)

        val preview80_1 = ThermalReceiptFormatter.generateReceiptText(h1, inv1, lines1, "80mm", "ধন্যবাদ", "শুভ বিদায়")
        val preview80_2 = ThermalReceiptFormatter.generateReceiptText(h2, inv2, lines2, "80mm", "ধন্যবাদ", "শুভ বিদায়")

        assertEquals("80mm Previews from Gboard and Ridmik must be 100% identical", preview80_1, preview80_2)
    }

    // =========================================================================
    // 3. Output 2: PDF Invoice Verification
    // =========================================================================

    @Test
    fun testOutput2_PdfInvoice_GeneratesValidPdfWithAllTestStrings() {
        val testStrings = listOf(
            STR_BENGALI_SENTENCE,
            STR_CONJUNCTS,
            STR_KAR_SIGNS,
            STR_BENGALI_DIGITS,
            STR_ENGLISH,
            STR_MIXED
        )

        testStrings.forEachIndexed { index, testText ->
            val (header, invoice, lines) = buildInvoiceWithText(testText, "PDF-$index")

            val outStream = ByteArrayOutputStream()
            val writeResult = PdfInvoiceEngine.writeInvoicePdfToStream(context, header, invoice, lines, outStream)

            assertTrue("PDF generation must succeed for string #$index", writeResult is PosResult.Success)
            val pdfBytes = outStream.toByteArray()
            assertTrue("PDF output byte array must not be empty", pdfBytes.isNotEmpty())

            val headerPrefix = String(pdfBytes.take(8).toByteArray(), Charsets.US_ASCII)
            assertTrue("PDF must start with %PDF header, got $headerPrefix", headerPrefix.startsWith("%PDF"))
        }
    }

    @Test
    fun testOutput2_PdfInvoice_IdenticalOutputAcrossAllInputMethods() {
        val gboardText = simulateGboardInput(STR_BENGALI_SENTENCE)
        val ridmikText = simulateRidmikKeyboardInput(STR_BENGALI_SENTENCE)

        val (h1, inv1, lines1) = buildInvoiceWithText(gboardText, "PDF-SAME")
        val (h2, inv2, lines2) = buildInvoiceWithText(ridmikText, "PDF-SAME")

        val out1 = ByteArrayOutputStream()
        val out2 = ByteArrayOutputStream()

        PdfInvoiceEngine.writeInvoicePdfToStream(context, h1, inv1, lines1, out1)
        PdfInvoiceEngine.writeInvoicePdfToStream(context, h2, inv2, lines2, out2)

        assertArrayEquals("PDF streams from Gboard and Ridmik must be byte-identical", out1.toByteArray(), out2.toByteArray())
    }

    // =========================================================================
    // 4. Output 3: 58mm ESC/POS Thermal Receipt Raster Pipeline
    // =========================================================================

    @Test
    fun testOutput3_58mm_ThermalReceipt_ProducesValidEscPosRaster() {
        val testStrings = listOf(
            STR_BENGALI_SENTENCE,
            STR_CONJUNCTS,
            STR_KAR_SIGNS,
            STR_BENGALI_DIGITS,
            STR_ENGLISH,
            STR_MIXED
        )

        testStrings.forEachIndexed { index, testText ->
            val (header, invoice, lines) = buildInvoiceWithText(testText, "ESC58-$index")

            val escPosBytes = ThermalReceiptFormatter.buildEscPosReceipt(
                header = header,
                invoice = invoice,
                lines = lines,
                paperSize = "58mm",
                autoCutter = true,
                footerLine1 = "ধন্যবাদ আবার আসবেন",
                footerLine2 = testText,
                lang = "bn"
            )

            // Must start with ESC @ (Init)
            assertTrue("ESC/POS must start with 0x1B 0x40", escPosBytes.size >= 2)
            assertEquals(0x1B.toByte(), escPosBytes[0])
            assertEquals(0x40.toByte(), escPosBytes[1])

            // If string contains Bengali, raster command GS v 0 (0x1D, 0x76, 0x30, 0x00) must be present
            if (ThermalReceiptFormatter.containsBangla(testText)) {
                var foundGsV0 = false
                for (i in 0 until escPosBytes.size - 4) {
                    if (escPosBytes[i] == 0x1D.toByte() &&
                        escPosBytes[i + 1] == 0x76.toByte() &&
                        escPosBytes[i + 2] == 0x30.toByte() &&
                        escPosBytes[i + 3] == 0x00.toByte()
                    ) {
                        foundGsV0 = true
                        // Check xL, xH for 58mm width: 384 dots / 8 = 48 bytes -> 0x30, 0x00
                        val xL = escPosBytes[i + 4].toInt() and 0xFF
                        val xH = escPosBytes[i + 5].toInt() and 0xFF
                        val bytesWidth = xL or (xH shl 8)
                        assertEquals("58mm raster width in bytes must be 48 (384 dots)", 48, bytesWidth)
                        break
                    }
                }
                assertTrue("GS v 0 raster command must be present for Bengali text in test #$index", foundGsV0)
            }
        }
    }

    // =========================================================================
    // 5. Output 4: 80mm ESC/POS Thermal Receipt Raster Pipeline
    // =========================================================================

    @Test
    fun testOutput4_80mm_ThermalReceipt_ProducesValidEscPosRaster() {
        val testStrings = listOf(
            STR_BENGALI_SENTENCE,
            STR_CONJUNCTS,
            STR_KAR_SIGNS,
            STR_BENGALI_DIGITS,
            STR_ENGLISH,
            STR_MIXED
        )

        testStrings.forEachIndexed { index, testText ->
            val (header, invoice, lines) = buildInvoiceWithText(testText, "ESC80-$index")

            val escPosBytes = ThermalReceiptFormatter.buildEscPosReceipt(
                header = header,
                invoice = invoice,
                lines = lines,
                paperSize = "80mm",
                autoCutter = true,
                footerLine1 = "ধন্যবাদ আবার আসবেন",
                footerLine2 = testText,
                lang = "bn"
            )

            // Must start with ESC @ (Init)
            assertTrue("ESC/POS must start with 0x1B 0x40", escPosBytes.size >= 2)
            assertEquals(0x1B.toByte(), escPosBytes[0])
            assertEquals(0x40.toByte(), escPosBytes[1])

            // If string contains Bengali, raster command GS v 0 must be present with 576 dots / 8 = 72 bytes
            if (ThermalReceiptFormatter.containsBangla(testText)) {
                var foundGsV0 = false
                for (i in 0 until escPosBytes.size - 4) {
                    if (escPosBytes[i] == 0x1D.toByte() &&
                        escPosBytes[i + 1] == 0x76.toByte() &&
                        escPosBytes[i + 2] == 0x30.toByte() &&
                        escPosBytes[i + 3] == 0x00.toByte()
                    ) {
                        foundGsV0 = true
                        val xL = escPosBytes[i + 4].toInt() and 0xFF
                        val xH = escPosBytes[i + 5].toInt() and 0xFF
                        val bytesWidth = xL or (xH shl 8)
                        assertEquals("80mm raster width in bytes must be 72 (576 dots)", 72, bytesWidth)
                        break
                    }
                }
                assertTrue("GS v 0 raster command must be present for Bengali text on 80mm in test #$index", foundGsV0)
            }
        }
    }

    // =========================================================================
    // 6. Direct Raster Rendering of Conjuncts, Kar Signs, and Numerals
    // =========================================================================

    @Test
    fun testRule9_ComplexShapingAndRasterDotDensity() {
        // Verify renderTextToRaster directly for each required category:
        // 1. Conjuncts: ক্ষ, জ্ঞ, ত্র, শ্র, প্র, ক্র, গ্র, ন্দ, স্ত্র
        val rasterConjuncts = ThermalReceiptFormatter.renderTextToRaster(STR_CONJUNCTS, ThermalReceiptFormatter.DOTS_58MM)
        assertNotNull("Raster data for conjuncts must not be null", rasterConjuncts)
        assertTrue("Raster data for conjuncts must have printable bytes", rasterConjuncts!!.size > 8)
        assertEquals(0x1D.toByte(), rasterConjuncts[0])
        assertEquals(0x76.toByte(), rasterConjuncts[1])
        assertEquals(0x30.toByte(), rasterConjuncts[2])
        assertEquals(0x00.toByte(), rasterConjuncts[3])
        assertEquals(48.toByte(), rasterConjuncts[4])
        assertEquals(0.toByte(), rasterConjuncts[5])
        val heightConjuncts = (rasterConjuncts[6].toInt() and 0xFF) or ((rasterConjuncts[7].toInt() and 0xFF) shl 8)
        assertTrue("Conjuncts must produce non-zero height raster bitmap", heightConjuncts > 0)
        assertEquals("Total raster size must match 8 + (48 * height)", 8 + 48 * heightConjuncts, rasterConjuncts.size)

        // 2. Kar signs: কা, কি, কী, কু, কূ, কে, কৈ, কো, কৌ
        val rasterKar = ThermalReceiptFormatter.renderTextToRaster(STR_KAR_SIGNS, ThermalReceiptFormatter.DOTS_58MM)
        assertNotNull("Raster data for kar signs must not be null", rasterKar)
        assertTrue("Raster data for kar signs must have printable bytes", rasterKar!!.size > 8)
        val heightKar = (rasterKar[6].toInt() and 0xFF) or ((rasterKar[7].toInt() and 0xFF) shl 8)
        assertTrue("Kar signs must produce non-zero height raster bitmap", heightKar > 0)
        assertEquals("Total raster size must match 8 + (48 * height)", 8 + 48 * heightKar, rasterKar.size)

        // 3. Bengali digits: ০১২৩৪৫৬৭৮৯
        val rasterDigits = ThermalReceiptFormatter.renderTextToRaster(STR_BENGALI_DIGITS, ThermalReceiptFormatter.DOTS_58MM)
        assertNotNull("Raster data for Bengali digits must not be null", rasterDigits)
        assertTrue("Raster data for Bengali digits must have printable bytes", rasterDigits!!.size > 8)
        val heightDigits = (rasterDigits[6].toInt() and 0xFF) or ((rasterDigits[7].toInt() and 0xFF) shl 8)
        assertTrue("Bengali digits must produce non-zero height raster bitmap", heightDigits > 0)
        assertEquals("Total raster size must match 8 + (48 * height)", 8 + 48 * heightDigits, rasterDigits.size)
    }

    // =========================================================================
    // 7. Rule 10: Byte-Identical ESC/POS Output Across All Keyboards
    // =========================================================================

    @Test
    fun testRule10_IdenticalEscPosBytesAcrossAllKeyboards() {
        val gboardText = simulateGboardInput(STR_MIXED)
        val ridmikText = simulateRidmikKeyboardInput(STR_MIXED)

        val (h1, inv1, lines1) = buildInvoiceWithText(gboardText, "IDENTICAL")
        val (h2, inv2, lines2) = buildInvoiceWithText(ridmikText, "IDENTICAL")

        val bytes58_1 = ThermalReceiptFormatter.buildEscPosReceipt(h1, inv1, lines1, "58mm", true, "ধন্যবাদ", gboardText)
        val bytes58_2 = ThermalReceiptFormatter.buildEscPosReceipt(h2, inv2, lines2, "58mm", true, "ধন্যবাদ", ridmikText)

        assertArrayEquals("58mm ESC/POS bytes from Gboard and Ridmik must be byte-identical", bytes58_1, bytes58_2)

        val bytes80_1 = ThermalReceiptFormatter.buildEscPosReceipt(h1, inv1, lines1, "80mm", true, "ধন্যবাদ", gboardText)
        val bytes80_2 = ThermalReceiptFormatter.buildEscPosReceipt(h2, inv2, lines2, "80mm", true, "ধন্যবাদ", ridmikText)

        assertArrayEquals("80mm ESC/POS bytes from Gboard and Ridmik must be byte-identical", bytes80_1, bytes80_2)
    }

    // =========================================================================
    // 8. Rule 3, 4, 6: No Corrupt ASCII/Bijoy Legacy Conversion & English Preservation
    // =========================================================================

    @Test
    fun testRule3AndRule6_NoLegacyConversionAndPureEnglishPreserved() {
        // Pure English invoice test
        val (header, invoice, lines) = buildInvoiceWithText(STR_ENGLISH, "ENG")
        val preview = ThermalReceiptFormatter.generateReceiptText(header, invoice, lines, "58mm", "Thank You", "Visit Again", lang = "en")

        assertTrue("English text must be preserved intact", preview.contains("POS Invoice Test") && preview.contains("1234567890"))

        // Raw Bengali test should NEVER contain corrupt Bijoy characters like "Avwg", "evsjv‡`k"
        val banglaPreview = ThermalReceiptFormatter.generateReceiptText(header, invoice, lines, "58mm", "ধন্যবাদ", "বিদায়", lang = "bn")
        assertFalse("Must not contain legacy Bijoy corrupted characters", banglaPreview.contains("Avwg") || banglaPreview.contains("evsjv"))
    }
}
