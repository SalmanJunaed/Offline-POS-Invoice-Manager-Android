package com.example.pos.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BengaliTransliterationEngineTest {

    @Test
    fun testCoreRequiredExamples() {
        // Fuel / Product names
        assertEquals("ডিজেল", BengaliTransliterationEngine.transliterate("Diesel"))
        assertEquals("ডিজেল", BengaliTransliterationEngine.transliterate("diesel"))
        assertEquals("পেট্রোল", BengaliTransliterationEngine.transliterate("Petrol"))
        assertEquals("পেট্রোল", BengaliTransliterationEngine.transliterate("petrol"))
        assertEquals("অকটেন", BengaliTransliterationEngine.transliterate("Octane"))
        assertEquals("সিএনজি", BengaliTransliterationEngine.transliterate("CNG"))
        assertEquals("এলপিজি", BengaliTransliterationEngine.transliterate("LPG"))

        // Customer & Operator names
        assertEquals("রহিম", BengaliTransliterationEngine.transliterate("Rahim"))
        assertEquals("রহিম", BengaliTransliterationEngine.transliterate("rahim"))
        assertEquals("করিম", BengaliTransliterationEngine.transliterate("Karim"))
        assertEquals("করিম", BengaliTransliterationEngine.transliterate("karim"))
        assertEquals("হাসান", BengaliTransliterationEngine.transliterate("Hasan"))
        assertEquals("আলী", BengaliTransliterationEngine.transliterate("Ali"))
        assertEquals("খান", BengaliTransliterationEngine.transliterate("Khan"))

        // Multi-word company and branch names
        assertEquals("মেইন ব্রাঞ্চ", BengaliTransliterationEngine.transliterate("Main Branch"))
        assertEquals("পেট্রোল পাম্প", BengaliTransliterationEngine.transliterate("Petrol Pump"))
        assertEquals("সার্ভিস স্টেশন", BengaliTransliterationEngine.transliterate("Service Station"))
    }

    @Test
    fun testAlreadyBengaliTextRemainsUnchanged() {
        assertEquals("ডিজেল", BengaliTransliterationEngine.transliterate("ডিজেল"))
        assertEquals("পেট্রোল", BengaliTransliterationEngine.transliterate("পেট্রোল"))
        assertEquals("রহিম এন্টারপ্রাইজ", BengaliTransliterationEngine.transliterate("রহিম এন্টারপ্রাইজ"))
        assertEquals("প্রধান শাখা", BengaliTransliterationEngine.transliterate("প্রধান শাখা"))
        assertEquals("ঢাকা, বাংলাদেশ", BengaliTransliterationEngine.transliterate("ঢাকা, বাংলাদেশ"))
    }

    @Test
    fun testDigitsPunctuationAndSpacingPreserved() {
        // Numbers and units
        assertEquals("ডিজেল 50", BengaliTransliterationEngine.transliterate("Diesel 50"))
        assertEquals("পেট্রোল (100L)", BengaliTransliterationEngine.transliterate("Petrol (100L)"))
        
        // Bengali numerals mixed with English
        assertEquals("ডিজেল ৫০", BengaliTransliterationEngine.transliterate("Diesel ৫০"))

        // Phone numbers and punctuation
        assertEquals("রহিম - 01711000000", BengaliTransliterationEngine.transliterate("Rahim - 01711000000"))
        assertEquals("করিম: ০১৮১২৩৪৫৬৭৮", BengaliTransliterationEngine.transliterate("Karim: ০১৮১২৩৪৫৬৭৮"))

        // Preserves symbols and spacing
        assertEquals("মবিল #1 & ফিল্টার!", BengaliTransliterationEngine.transliterate("Mobil #1 & Filter!"))
    }

    @Test
    fun testTechnicalIdentifiersPreserved() {
        // Email addresses
        assertEquals("support@stationpos.com", BengaliTransliterationEngine.transliterate("support@stationpos.com"))
        assertEquals("admin.user@example.org", BengaliTransliterationEngine.transliterate("admin.user@example.org"))

        // URLs
        assertEquals("https://mypos.example.com", BengaliTransliterationEngine.transliterate("https://mypos.example.com"))
        assertEquals("www.petrolstation.bd", BengaliTransliterationEngine.transliterate("www.petrolstation.bd"))

        // SKU / Transaction / Order IDs
        assertEquals("INV-2026-001", BengaliTransliterationEngine.transliterate("INV-2026-001"))
        assertEquals("TRX_99214A", BengaliTransliterationEngine.transliterate("TRX_99214A"))
        assertEquals("SKU12345", BengaliTransliterationEngine.transliterate("SKU12345"))
    }

    @Test
    fun testMixedBengaliAndEnglish() {
        assertEquals("পেট্রোল ও ডিজেল", BengaliTransliterationEngine.transliterate("পেট্রোল ও Diesel"))
        val result = BengaliTransliterationEngine.transliterate("খুচরা ক্রেতা: Rahim")
        assertEquals("খুচরা ক্রেতা: রহিম", result)
    }

    @Test
    fun testContainsBengaliHelper() {
        assertTrue(BengaliTransliterationEngine.containsBengali("ডিজেল"))
        assertTrue(BengaliTransliterationEngine.containsBengali("ঢাকা 123"))
        assertTrue(BengaliTransliterationEngine.containsBengali("১০"))
        assertFalse(BengaliTransliterationEngine.containsBengali("Diesel"))
        assertFalse(BengaliTransliterationEngine.containsBengali("12345"))
    }

    @Test
    fun testWalkInCustomerPhoneticTransliteration() {
        assertEquals("ওয়াক-ইন কাস্টমার", BengaliTransliterationEngine.transliterate("Walk-in Customer"))
        assertEquals("ওয়াক-ইন কাস্টমার", BengaliTransliterationEngine.transliterate("walk-in customer"))
        assertEquals("ওয়াক-ইন কাস্টমার", BengaliTransliterationEngine.transliterate("Walk in Customer"))
        assertEquals("কাস্টমার", BengaliTransliterationEngine.transliterate("Customer"))
    }

    @Test
    fun testNullAndBlankHandling() {
        assertEquals("", BengaliTransliterationEngine.transliterate(""))
        assertEquals("   ", BengaliTransliterationEngine.transliterate("   "))
        assertEquals("", BengaliTransliterationEngine.transliterate(null))
    }
}
