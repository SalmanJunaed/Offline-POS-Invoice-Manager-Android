package com.example.pos.data.audit

import com.example.pos.core.util.DateTimeUtils
import com.example.pos.domain.FinancialCalculationEngine
import org.json.JSONObject

/**
 * Security sanitizer, diff generator, and bilingual readable formatter for audit logs.
 * Guarantees sensitive credentials, hashes, recovery codes, tokens, and binary payloads
 * can never be written into audit_logs.
 */
object AuditSanitizer {

    private val blockedKeyTokens = listOf(
        "password",
        "passcode",
        "pin",
        "hash",
        "salt",
        "recovery",
        "token",
        "secret",
        "cipher",
        "keystore",
        "datastore",
        "session",
        "base64",
        "bitmap",
        "rawbytes"
    )

    private const val MAX_VALUE_LEN = 300

    fun isSensitiveKey(key: String): Boolean {
        val lower = key.lowercase()
        return blockedKeyTokens.any { token -> lower.contains(token) }
    }

    fun sanitizeValue(key: String, rawValue: Any?): String? {
        if (rawValue == null) return null
        if (isSensitiveKey(key)) return "[REDACTED]"
        val str = rawValue.toString().trim()
        if (str.startsWith("v1$") || str.startsWith("v2$") || str.contains("data:image", ignoreCase = true)) {
            return "[REDACTED]"
        }
        return if (str.length > MAX_VALUE_LEN) str.take(MAX_VALUE_LEN) + "..." else str
    }

    fun maskPhone(phone: String): String {
        val cleaned = phone.trim()
        if (cleaned.length <= 4) return "****"
        if (cleaned.length <= 6) return cleaned.take(2) + "****"
        return cleaned.take(3) + "****" + cleaned.takeLast(4)
    }

    fun buildSnapshotJson(fields: Map<String, Any?>): String? {
        val json = JSONObject()
        var count = 0
        for ((key, value) in fields) {
            if (isSensitiveKey(key)) continue
            val sanitized = sanitizeValue(key, value) ?: continue
            if (sanitized == "[REDACTED]") continue
            json.put(key, sanitized)
            count++
        }
        return if (count == 0) null else json.toString()
    }

    /**
     * Compares only the provided fields and emits JSON snapshots strictly for keys that changed.
     * Returns (null, null) when no fields changed so callers can cleanly skip no-op edits.
     */
    fun computeDiffSnapshots(
        before: Map<String, Any?>,
        after: Map<String, Any?>
    ): Pair<String?, String?> {
        val beforeJson = JSONObject()
        val afterJson = JSONObject()
        var diffCount = 0

        val allKeys = (before.keys + after.keys).distinct()
        for (key in allKeys) {
            if (isSensitiveKey(key)) continue
            val bVal = sanitizeValue(key, before[key]) ?: ""
            val aVal = sanitizeValue(key, after[key]) ?: ""
            if (bVal == "[REDACTED]" || aVal == "[REDACTED]") continue
            if (bVal != aVal) {
                beforeJson.put(key, bVal)
                afterJson.put(key, aVal)
                diffCount++
            }
        }
        return if (diffCount == 0) {
            null to null
        } else {
            beforeJson.toString() to afterJson.toString()
        }
    }

    fun formatBdt(amountBdt: Long, lang: String): String {
        val formatted = FinancialCalculationEngine.formatWholeBdtToDisplay(amountBdt)
        return if (lang == "bn") "${DateTimeUtils.toBengaliNumerals(formatted)} ৳" else "৳$formatted"
    }

    fun formatPaisaAsBdt(paisa: Long, lang: String): String {
        val formatted = FinancialCalculationEngine.formatPaisaToBdt(paisa)
        return if (lang == "bn") "${DateTimeUtils.toBengaliNumerals(formatted)} ৳" else "৳$formatted"
    }

    fun localizeCount(count: Number, lang: String): String {
        val s = count.toString()
        return if (lang == "bn") DateTimeUtils.toBengaliNumerals(s) else s
    }

    /**
     * Converts stored flat JSON snapshots (before_data / after_data / metadata_json) into clean,
     * localized human-readable label/value pairs for UI dialogs. Never exposes raw secrets.
     */
    fun parseSnapshotPairs(jsonStr: String?, language: String): List<Pair<String, String>> {
        if (jsonStr.isNullOrBlank()) return emptyList()
        val isBn = language.equals("bn", ignoreCase = true)
        return try {
            val obj = JSONObject(jsonStr)
            val result = mutableListOf<Pair<String, String>>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (isSensitiveKey(key)) continue
                val rawVal = obj.optString(key, "")
                val label = localizedFieldLabel(key, isBn)
                val value = localizedFieldValue(key, rawVal, isBn)
                result.add(label to value)
            }
            result
        } catch (_: Exception) {
            listOf((if (isBn) "বিবরণ" else "Details") to jsonStr)
        }
    }

    private fun localizedFieldLabel(key: String, isBn: Boolean): String = when (key) {
        "orderId" -> if (isBn) "ইনভয়েস নম্বর" else "Invoice No."
        "customerInfo" -> if (isBn) "কাস্টমার তথ্য" else "Customer Info"
        "customerName" -> if (isBn) "কাস্টমারের নাম" else "Customer Name"
        "phoneNumber", "operatorPhone", "maskedPhone" -> if (isBn) "মোবাইল নম্বর" else "Mobile Number"
        "footerNote" -> if (isBn) "ইনভয়েস নোট" else "Invoice Note"
        "roundedTotalBdt" -> if (isBn) "সর্বমোট বিল" else "Total Payable"
        "rawTotalBdt" -> if (isBn) "আসল মোট" else "Raw Subtotal"
        "paidAmountBdt", "paidBdt" -> if (isBn) "পরিশোধিত" else "Paid"
        "dueAmountBdt", "dueBdt" -> if (isBn) "বকেয়া" else "Due"
        "amountBdt" -> if (isBn) "টাকার পরিমাণ" else "Amount"
        "method", "paymentMethod" -> if (isBn) "পেমেন্ট মাধ্যম" else "Payment Method"
        "transactionRef" -> if (isBn) "রেফারেন্স" else "Reference"
        "lineCount" -> if (isBn) "আইটেম সংখ্যা" else "Line Count"
        "paymentCount" -> if (isBn) "পেমেন্ট সংখ্যা" else "Payment Records"
        "itemId" -> if (isBn) "পণ্য আইডি" else "Product ID"
        "itemName" -> if (isBn) "পণ্যের নাম" else "Product Name"
        "nameBn" -> if (isBn) "বাংলা নাম" else "Bangla Name"
        "nameEn" -> if (isBn) "ইংরেজি নাম" else "English Name"
        "itemType" -> if (isBn) "ধরণ" else "Category"
        "unit" -> if (isBn) "একক" else "Unit"
        "unitPriceBdt" -> if (isBn) "একক দর" else "Unit Price"
        "sortOrder" -> if (isBn) "ক্রম" else "Sort Order"
        "isActive" -> if (isBn) "সক্রিয়" else "Active"
        "reorderedCount" -> if (isBn) "পুনর্বিন্যাসকৃত পণ্য" else "Products Reordered"
        "address" -> if (isBn) "ঠিকানা" else "Address"
        "notes" -> if (isBn) "মন্তব্য" else "Notes"
        "operatorName" -> if (isBn) "অপারেটরের নাম" else "Operator Name"
        "companyName" -> if (isBn) "প্রতিষ্ঠানের নাম" else "Company Name"
        "branchName" -> if (isBn) "শাখার নাম" else "Branch Name"
        "branchNumber" -> if (isBn) "শাখা নম্বর" else "Branch No."
        "logoStatus" -> if (isBn) "লোগো অবস্থা" else "Logo Status"
        "footerLine1" -> if (isBn) "ফুটার লাইন ১" else "Footer Line 1"
        "footerLine2" -> if (isBn) "ফুটার লাইন ২" else "Footer Line 2"
        "paperSize" -> if (isBn) "পেপার সাইজ" else "Paper Size"
        "autoCutter" -> if (isBn) "অটো-কাটার" else "Auto-Cutter"
        "printerName" -> if (isBn) "ব্লুটুথ প্রিন্টার" else "Bluetooth Printer"
        "printerAddress" -> if (isBn) "প্রিন্টার ঠিকানা" else "Printer Address"
        "language" -> if (isBn) "ভাষা" else "Language"
        "themeMode" -> if (isBn) "থিম মোড" else "Theme Mode"
        "exportType" -> if (isBn) "এক্সপোর্ট ধরন" else "Export Type"
        "recordCount" -> if (isBn) "রেকর্ড সংখ্যা" else "Records"
        "importedCount" -> if (isBn) "নতুন যুক্ত" else "Imported"
        "skippedCount" -> if (isBn) "বাদ দেওয়া" else "Skipped"
        "conflictCount" -> if (isBn) "কনফ্লিক্ট" else "Conflicts"
        "invalidCount" -> if (isBn) "অবৈধ" else "Invalid"
        "fileName" -> if (isBn) "ফাইলের নাম" else "File Name"
        "dateRange" -> if (isBn) "সময়সীমা" else "Date Range"
        "reason" -> if (isBn) "কারণ" else "Reason"
        "failedAttempts" -> if (isBn) "ব্যর্থ চেষ্টা" else "Failed Attempts"
        "lockoutSeconds" -> if (isBn) "লকআউট সময় (সেকেন্ড)" else "Lockout (sec)"
        "rememberMe" -> if (isBn) "লগইন মনে রাখা" else "Remember Me"
        else -> key
    }

    private fun localizedFieldValue(key: String, raw: String, isBn: Boolean): String {
        if (raw.isBlank()) return if (isBn) "ফাঁকা" else "Empty"
        return when (key) {
            "autoCutter", "isActive", "rememberMe" -> {
                if (raw.equals("true", ignoreCase = true)) {
                    if (isBn) "সক্রিয় / হ্যাঁ" else "Enabled / Yes"
                } else {
                    if (isBn) "নিষ্ক্রিয় / না" else "Disabled / No"
                }
            }
            "language" -> when (raw.lowercase()) {
                "bn" -> if (isBn) "বাংলা" else "Bangla"
                "en" -> if (isBn) "ইংরেজি" else "English"
                else -> raw
            }
            "reason" -> when (raw) {
                "invalid_credentials" -> if (isBn) "ভুল মোবাইল নম্বর বা পাসওয়ার্ড" else "Invalid credentials"
                "account_locked" -> if (isBn) "অ্যাকাউন্ট সাময়িকভাবে লক" else "Account temporarily locked"
                "invalid_recovery_code" -> if (isBn) "ভুল রিকভারি কোড" else "Invalid recovery code"
                else -> raw
            }
            "logoStatus" -> when (raw) {
                "CUSTOM_CONFIGURED" -> if (isBn) "কাস্টম লোগো সেট করা হয়েছে" else "Custom logo configured"
                "OFFICIAL_DEFAULT" -> if (isBn) "অফিসিয়াল ডিফল্ট লোগো" else "Official default logo"
                "REMOVED" -> if (isBn) "লোগো মুছে ফেলা হয়েছে" else "Logo removed"
                else -> raw
            }
            else -> if (isBn) DateTimeUtils.toBengaliNumerals(raw) else raw
        }
    }
}
