package com.example.pos.core.util

import com.example.pos.domain.FinancialCalculationEngine

/**
 * CurrencyFormatter localized for Bengali currency display with ৳ symbol and Bengali numerals.
 */
object CurrencyFormatter {
    fun formatBdtWithSymbol(paisa: Long, language: String = "bn"): String {
        val bdtString = FinancialCalculationEngine.formatPaisaToBdt(paisa)
        val bengaliDigits = FinancialCalculationEngine.toBengaliNumerals(bdtString)
        return "৳ $bengaliDigits"
    }

    fun formatWholeBdtWithSymbol(wholeBdt: Long, language: String = "bn"): String {
        val bdtString = FinancialCalculationEngine.formatWholeBdtToDisplay(wholeBdt)
        val bengaliDigits = FinancialCalculationEngine.toBengaliNumerals(bdtString)
        return "৳ $bengaliDigits"
    }

    fun formatToBengaliBdt(wholeBdt: Long): String {
        return formatWholeBdtWithSymbol(wholeBdt, "bn")
    }
}
