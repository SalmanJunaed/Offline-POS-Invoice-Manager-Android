package com.example.pos.core.util

import com.example.pos.domain.FinancialCalculationEngine
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * DateTimeUtils formatted with Bengali numerals, Bengali month names, and AM/PM indicators.
 */
object DateTimeUtils {
    private val rawDateOnlyFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)
    private val rawHistoryDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    private val rawReceiptDateFormat = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.US)
    private val rawTimeOnlyFormat = SimpleDateFormat("hh:mm:ss a", Locale.US)

    private val bengaliMonths = arrayOf(
        "জানু", "ফেব", "মার্চ", "এপ্রিল", "মে", "জুন",
        "জুলাই", "আগস্ট", "সেপ্টে", "অক্টো", "নভে", "ডিসে"
    )

    private val englishMonths = arrayOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )

    private fun toBengaliAmPm(text: String): String {
        return text.replace("AM", "পূর্বাহ্ন")
            .replace("PM", "অপরাহ্ন")
            .replace("am", "পূর্বাহ্ন")
            .replace("pm", "অপরাহ্ন")
    }

    private fun replaceMonthNamesWithBengali(text: String): String {
        var result = text
        for (i in englishMonths.indices) {
            result = result.replace(englishMonths[i], bengaliMonths[i])
        }
        return result
    }

    fun toBengaliNumerals(number: String): String {
        return FinancialCalculationEngine.toBengaliNumerals(number)
    }

    fun formatDateOnly(timeMillis: Long = System.currentTimeMillis()): String {
        val formatted = rawDateOnlyFormat.format(Date(timeMillis))
        val bnMonth = replaceMonthNamesWithBengali(formatted)
        return FinancialCalculationEngine.toBengaliNumerals(bnMonth)
    }

    fun formatHistoryDateTime(timeMillis: Long): String {
        val formatted = rawHistoryDateFormat.format(Date(timeMillis))
        return FinancialCalculationEngine.toBengaliNumerals(formatted)
    }

    fun formatLiveTime(timeMillis: Long = System.currentTimeMillis()): String {
        val formatted = rawTimeOnlyFormat.format(Date(timeMillis))
        val bnAmPm = toBengaliAmPm(formatted)
        return FinancialCalculationEngine.toBengaliNumerals(bnAmPm)
    }

    fun formatReceiptDateTime(timeMillis: Long): String {
        val formatted = rawReceiptDateFormat.format(Date(timeMillis))
        val bnAmPm = toBengaliAmPm(formatted)
        return FinancialCalculationEngine.toBengaliNumerals(bnAmPm)
    }

    fun formatPeriodKeyForDisplay(periodKey: String, language: String = "bn"): String {
        return try {
            if (periodKey.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val date = parser.parse(periodKey) ?: return periodKey
                val rawFormatted = SimpleDateFormat("dd MMM", Locale.US).format(date)
                val withBnMonth = replaceMonthNamesWithBengali(rawFormatted)
                FinancialCalculationEngine.toBengaliNumerals(withBnMonth)
            } else if (periodKey.matches(Regex("\\d{4}-\\d{2}"))) {
                val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val date = parser.parse("$periodKey-01") ?: return periodKey
                val rawFormatted = SimpleDateFormat("MMM yyyy", Locale.US).format(date)
                val withBnMonth = replaceMonthNamesWithBengali(rawFormatted)
                FinancialCalculationEngine.toBengaliNumerals(withBnMonth)
            } else if (periodKey.matches(Regex("\\d{4}"))) {
                FinancialCalculationEngine.toBengaliNumerals(periodKey)
            } else {
                FinancialCalculationEngine.toBengaliNumerals(periodKey)
            }
        } catch (_: Exception) {
            FinancialCalculationEngine.toBengaliNumerals(periodKey)
        }
    }

    fun getStartOfDay(timeMillis: Long = System.currentTimeMillis()): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timeMillis
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    fun getStartOfWeek(timeMillis: Long = System.currentTimeMillis()): Long {
        return getStartOfDay(timeMillis) - (7L * 24L * 60L * 60L * 1000L)
    }

    fun getStartOfMonth(timeMillis: Long = System.currentTimeMillis()): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timeMillis
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    fun getStartOfYear(timeMillis: Long = System.currentTimeMillis()): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timeMillis
        calendar.set(Calendar.DAY_OF_YEAR, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    fun toAsciiDigits(input: String): String {
        val bnDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
        val sb = StringBuilder(input.length)
        for (ch in input) {
            val idx = bnDigits.indexOf(ch)
            if (idx >= 0) {
                sb.append(('0'.code + idx).toChar())
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun parseDateDdMmYyyy(input: String): Date? {
        val ascii = toAsciiDigits(input.trim())
        val parts = ascii.split("-")
        if (parts.size != 3) return null
        val d = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        val y = parts[2].toIntOrNull() ?: return null
        if (d in 1..31 && m in 1..12 && y in 1970..2100) {
            return try {
                val parser = SimpleDateFormat("dd-MM-yyyy", Locale.US)
                parser.isLenient = false
                parser.parse(ascii)
            } catch (_: Exception) {
                null
            }
        }
        return null
    }
}
