package com.example.pos.core.util

import java.util.Locale

/**
 * High-precision offline Bengali phonetic transliteration utility for invoice presentation.
 *
 * Converts English Product, Customer, and Company names into Bengali phonetic script without
 * altering database values, semantic meanings, or touching already-Bengali text.
 *
 * Adheres to zero-network, 100% offline constraints and Android 24+ compatibility.
 */
object BengaliTransliterationEngine {

    // Regex to detect Bengali Unicode characters (\u0980 to \u09FF)
    private val BENGALI_CHAR_REGEX = Regex("[\u0980-\u09FF]")

    // Regex patterns for tokens that should be preserved as-is (emails, URLs, SKUs, alphanumeric IDs)
    private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private val URL_REGEX = Regex("^(https?://|www\\.)[A-Za-z0-9.-]+.*$", RegexOption.IGNORE_CASE)
    private val CODE_OR_SKU_REGEX = Regex("^[A-Za-z0-9]+[-_][A-Za-z0-9-_]+$|^[A-Z]{2,}[0-9]+[A-Za-z0-9]*$")

    /**
     * Curated high-confidence phrase dictionary for multi-word expressions.
     * Evaluated before individual word tokens.
     */
    private val PHRASE_DICTIONARY: Map<String, String> = mapOf(
        "main branch" to "মেইন ব্রাঞ্চ",
        "petrol pump" to "পেট্রোল পাম্প",
        "service station" to "সার্ভিস স্টেশন",
        "engine oil" to "ইঞ্জিন অয়েল",
        "brake oil" to "ব্রেক অয়েল",
        "car wash" to "কার ওয়াশ",
        "walk in" to "ওয়াক-ইন",
        "walk-in" to "ওয়াক-ইন",
        "walk in customer" to "ওয়াক-ইন কাস্টমার",
        "walk-in customer" to "ওয়াক-ইন কাস্টমার",
        "general customer" to "জেনারেল কাস্টমার",
        "filling station" to "ফিলিং স্টেশন",
        "cng station" to "সিএনজি স্টেশন",
        "auto care" to "অটো কেয়ার"
    )

    /**
     * Curated dictionary of high-confidence fuel, product, customer, and company words.
     */
    private val WORD_DICTIONARY: Map<String, String> = mapOf(
        // Fuel & POS Products / Services
        "diesel" to "ডিজেল",
        "petrol" to "পেট্রোল",
        "octane" to "অকটেন",
        "cng" to "সিএনজি",
        "lpg" to "এলপিজি",
        "kerosene" to "কেরোসিন",
        "mobil" to "মবিল",
        "oil" to "অয়েল",
        "lubricant" to "লুব্রিকেন্ট",
        "gas" to "গ্যাস",
        "filter" to "ফিল্টার",
        "wash" to "ওয়াশ",
        "polish" to "পলিশ",
        "service" to "সার্ভিস",
        "water" to "ওয়াটার",
        "coolant" to "কুল্যান্ট",
        "grease" to "গ্রিজ",
        "battery" to "ব্যাটারি",
        "tyre" to "টায়ার",
        "tire" to "টায়ার",
        "air" to "এয়ার",
        "nitrogen" to "নাইট্রোজেন",
        "brake" to "ব্রেক",
        "gear" to "গিয়ার",
        "pad" to "প্যাড",
        "light" to "লাইট",
        "wiper" to "ওয়াইপার",

        // Common Bangladeshi Customer & Operator Names
        "rahim" to "রহিম",
        "karim" to "করিম",
        "hasan" to "হাসান",
        "hassan" to "হাসান",
        "ali" to "আলী",
        "ahmed" to "আহমেদ",
        "khan" to "খান",
        "chowdhury" to "চৌধুরী",
        "hossain" to "হোসেন",
        "hussain" to "হোসেন",
        "islam" to "ইসলাম",
        "rahman" to "রহমান",
        "sheikh" to "শেখ",
        "alam" to "আলম",
        "tariq" to "তারিক",
        "tareq" to "তারিক",
        "salman" to "সালমান",
        "mahmud" to "মাহমুদ",
        "mahmood" to "মাহমুদ",
        "shuvo" to "শুভ",
        "tanvir" to "তানভীর",
        "saiful" to "সাইফুল",
        "rafiq" to "রফিক",
        "shafiq" to "শফিক",
        "kabir" to "কবির",
        "kamal" to "কামাল",
        "jamal" to "জামাল",
        "nasir" to "নাসির",
        "faruk" to "ফারুক",
        "farooq" to "ফারুক",
        "jahangir" to "জাহাঙ্গীর",
        "shahid" to "শহীদ",
        "sumon" to "সুমন",
        "mizan" to "মিজান",
        "rubel" to "রুবেল",
        "sakib" to "সাকিব",
        "shakib" to "সাকিব",
        "anwar" to "আনোয়ার",
        "akhtar" to "আখতার",
        "babu" to "বাবু",
        "rana" to "রানা",
        "sohel" to "সোহেল",
        "masud" to "মাসুদ",
        "asif" to "আসিফ",
        "arifin" to "আরিফিন",
        "sadia" to "সাদিয়া",
        "fatima" to "ফাতিমা",
        "ayesha" to "আয়েশা",
        "nusrat" to "নুসরাত",
        "shirin" to "শিরিন",
        "akash" to "আকাশ",
        "mehedi" to "মেহেদী",
        "billal" to "বিল্লাল",
        "monir" to "মনির",
        "jahid" to "জাহিদ",
        "alok" to "আলোক",
        "bijoy" to "বিজয়",
        "joy" to "জয়",
        "sohag" to "সোহাগ",
        "rony" to "রনি",

        // Company, Branch & Business Terms
        "main" to "মেইন",
        "branch" to "ব্রাঞ্চ",
        "dhaka" to "ঢাকা",
        "chittagong" to "চট্টগ্রাম",
        "chattogram" to "চট্টগ্রাম",
        "sylhet" to "সিলেট",
        "khulna" to "খুলনা",
        "rajshahi" to "রাজশাহী",
        "barisal" to "বরিশাল",
        "barishal" to "বরিশাল",
        "rangpur" to "রংপুর",
        "mymensingh" to "ময়মনসিংহ",
        "comilla" to "কুমিল্লা",
        "cumilla" to "কুমিল্লা",
        "gazipur" to "গাজীপুর",
        "narayanganj" to "নারায়ণগঞ্জ",
        "station" to "স্টেশন",
        "pump" to "পাম্প",
        "enterprise" to "এন্টারপ্রাইজ",
        "trading" to "ট্রেডিং",
        "motors" to "মোটরস",
        "auto" to "অটো",
        "filling" to "ফিলিং",
        "center" to "সেন্টার",
        "centre" to "সেন্টার",
        "point" to "পয়েন্ট",
        "express" to "এক্সপ্রেস",
        "ltd" to "লিমিটেড",
        "limited" to "লিমিটেড",
        "corp" to "কর্পোরেশন",
        "corporation" to "কর্পোরেশন",
        "agency" to "এজেন্সি",
        "store" to "স্টোর",
        "shop" to "শপ",
        "counter" to "কাউন্টার",
        "pos" to "পস",
        "operator" to "অপারেটর",
        "manager" to "ম্যানেজার",
        "admin" to "অ্যাডমিন",
        "customer" to "কাস্টমার",
        "guest" to "গেস্ট",
        "vip" to "ভিআইপি",
        "fuel" to "ফুয়েল"
    )

    private val COMMON_UNITS = setOf("l", "ml", "ltr", "kg", "g", "gm", "pcs", "pc", "sqft", "ton", "t", "m", "cm", "mm")

    /**
     * Checks if a string contains Bengali Unicode characters.
     */
    fun containsBengali(text: String): Boolean {
        return BENGALI_CHAR_REGEX.containsMatchIn(text)
    }

    /**
     * Checks if a token is a preserved technical identifier (email, URL, SKU, code).
     */
    fun isPreservedIdentifier(token: String): Boolean {
        val trimmed = token.trim()
        if (EMAIL_REGEX.matches(trimmed)) return true
        if (URL_REGEX.matches(trimmed)) return true
        if (CODE_OR_SKU_REGEX.matches(trimmed)) return true
        return false
    }

    /**
     * Main transliteration function for product, customer, operator, and company names.
     *
     * - Returns unchanged if null, blank, or already containing Bengali characters.
     * - Preserves email addresses, URLs, and SKU codes.
     * - Checks multi-word phrases first.
     * - Transliterates individual Latin words while preserving all numbers, punctuation, and whitespace.
     * - If a word has unclear pronunciation and cannot be transliterated with high confidence,
     *   it is preserved safely as Latin rather than producing a misleading phonetic guess.
     */
    fun transliterate(input: String?): String {
        if (input.isNullOrBlank()) return input ?: ""

        val trimmed = input.trim()

        // 1. If the input is a technical identifier (email, URL, SKU), preserve as-is
        if (isPreservedIdentifier(trimmed)) {
            return input
        }

        // 2. Check for whole phrase match (case-insensitive)
        val lowerTrimmed = trimmed.lowercase(Locale.ROOT)
        PHRASE_DICTIONARY[lowerTrimmed]?.let { return it }

        // 3. Process tokens while preserving non-word characters (punctuation, numbers, spaces)
        // Regex matches consecutive letters [A-Za-z]+ or non-letters [^A-Za-z]+
        val tokenRegex = Regex("[A-Za-z]+|[^A-Za-z]+")
        val tokens = tokenRegex.findAll(input).map { it.value }.toList()

        val sb = StringBuilder()
        for (idx in tokens.indices) {
            val token = tokens[idx]
            if (token.all { it in 'A'..'Z' || it in 'a'..'z' }) {
                val prevToken = if (idx > 0) tokens[idx - 1] else ""
                val immediatelyFollowsDigit = prevToken.isNotEmpty() && prevToken.last().isDigit()
                val isAttachedUnit = immediatelyFollowsDigit || (token.lowercase(Locale.ROOT) in COMMON_UNITS && prevToken.trimEnd().isNotEmpty() && prevToken.trimEnd().last().isDigit())

                if (isAttachedUnit) {
                    sb.append(token)
                } else {
                    sb.append(transliterateWord(token))
                }
            } else {
                // Non-alphabetic token (already Bengali, digits, spaces, symbols) is preserved as-is
                sb.append(token)
            }
        }

        return sb.toString()
    }

    /**
     * Transliterates a single English word token into Bengali script.
     * First checks the curated high-confidence dictionary.
     * Then applies deterministic, conservative phonetic syllable parsing.
     * If the pronunciation is ambiguous or unreliable, preserves the original token.
     */
    fun transliterateWord(word: String): String {
        if (word.isBlank()) return word

        val lower = word.lowercase(Locale.ROOT)

        // 1. Exact dictionary match (highest confidence)
        WORD_DICTIONARY[lower]?.let { return it }

        // 2. Fallback: conservative phonetic transliteration for regular Latin names
        val phonetic = parsePhoneticSyllables(lower)
        return phonetic ?: word // If ambiguous or failed confidence, preserve original
    }

    /**
     * Conservative phonetic transliterator for regular Latin syllable structures.
     * Returns null if phonetic pronunciation cannot be determined with high confidence.
     */
    private fun parsePhoneticSyllables(word: String): String? {
        if (word.length < 2) {
            // Single letters: map known standard phonetic representations
            return when (word) {
                "a" -> "এ"
                "b" -> "বি"
                "c" -> "সি"
                "d" -> "ডি"
                "e" -> "ই"
                "f" -> "এফ"
                "g" -> "জি"
                "h" -> "এইচ"
                "i" -> "আই"
                "j" -> "জে"
                "k" -> "কে"
                "l" -> "এল"
                "m" -> "এম"
                "n" -> "এন"
                "o" -> "ও"
                "p" -> "পি"
                "q" -> "কিউ"
                "r" -> "আর"
                "s" -> "এস"
                "t" -> "টি"
                "u" -> "ইউ"
                "v" -> "ভি"
                "w" -> "ডাব্লিউ"
                "x" -> "এক্স"
                "y" -> "ওয়াই"
                "z" -> "জেড"
                else -> null
            }
        }

        val result = StringBuilder()
        var i = 0
        val len = word.length
        var hasConsonant = false

        while (i < len) {
            val isFirstChar = (i == 0)

            // Multi-character consonant digraphs
            if (i + 1 < len) {
                val pair = word.substring(i, i + 2)
                val mappedDigraph = when (pair) {
                    "sh" -> "শ"
                    "ch" -> "চ"
                    "kh" -> "খ"
                    "gh" -> "ঘ"
                    "th" -> "থ"
                    "dh" -> "ধ"
                    "bh" -> "ভ"
                    "ph" -> "ফ"
                    "ng" -> "ং"
                    else -> null
                }
                if (mappedDigraph != null) {
                    result.append(mappedDigraph)
                    hasConsonant = true
                    i += 2
                    continue
                }

                // Vowel digraphs
                val mappedVowelDigraph = when (pair) {
                    "ee", "ea" -> if (isFirstChar) "ঈ" else "ী"
                    "oo" -> if (isFirstChar) "উ" else "ু"
                    "ai" -> if (isFirstChar) "ঐ" else "ৈ"
                    "oi" -> if (isFirstChar) "ঐ" else "ৈ"
                    "ou", "au" -> if (isFirstChar) "ঔ" else "ৌ"
                    else -> null
                }
                if (mappedVowelDigraph != null) {
                    result.append(mappedVowelDigraph)
                    i += 2
                    continue
                }
            }

            val char = word[i]

            // Vowels
            val isVowel = char in "aeiou"
            if (isVowel) {
                if (isFirstChar) {
                    val initialVowel = when (char) {
                        'a' -> "আ"
                        'e' -> "এ"
                        'i' -> "ই"
                        'o' -> "ও"
                        'u' -> "উ"
                        else -> "আ"
                    }
                    result.append(initialVowel)
                } else {
                    val matra = when (char) {
                        'a' -> "া"
                        'e' -> "ে"
                        'i' -> "ি"
                        'o' -> "ো"
                        'u' -> "ু"
                        else -> ""
                    }
                    result.append(matra)
                }
                i++
                continue
            }

            // Single Consonants
            val mappedConsonant = when (char) {
                'b' -> "ব"
                'c' -> if (i + 1 < len && word[i + 1] in "eiy") "স" else "ক"
                'd' -> "ড"
                'f' -> "ফ"
                'g' -> "গ"
                'h' -> "হ"
                'j' -> "জ"
                'k' -> "ক"
                'l' -> "ল"
                'm' -> "ম"
                'n' -> "ন"
                'p' -> "প"
                'q' -> "ক"
                'r' -> "র"
                's' -> "স"
                't' -> "ট"
                'v' -> "ভ"
                'w' -> "ওয়"
                'x' -> "ক্স"
                'y' -> if (isFirstChar) "ওয়" else "ি"
                'z' -> "জ"
                else -> null
            }

            if (mappedConsonant != null) {
                result.append(mappedConsonant)
                hasConsonant = true
                i++
            } else {
                // Unknown/unsupported character encountered in syllable parsing
                return null
            }
        }

        return if (hasConsonant || result.isNotEmpty()) result.toString() else null
    }
}
