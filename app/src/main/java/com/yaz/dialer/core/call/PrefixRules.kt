package com.yaz.dialer.core.call

import android.telephony.PhoneNumberUtils
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.yaz.dialer.data.settings.BlockedPrefix
import com.yaz.dialer.data.settings.PrefixAction
import java.util.Locale

/**
 * The prefixes the user blocks or silences, matched on the number in international form.
 * The idea is SpamBlocker's number rules, a pattern on the raw number with a blacklist flag
 * (https://github.com/aj3423/SpamBlocker/blob/main/app/src/main/java/spam/blocker/ui/setting/regex/RegexPresets.kt),
 * kept as a plain start of number. The number is put in E.164 the way Android's blocked list does
 * (AOSP BlockedNumberProvider, Utils.getE164Number, using PhoneNumberUtils.formatNumberToE164:
 * https://android.googlesource.com/platform/packages/providers/BlockedNumberProvider/+/refs/heads/main/src/com/android/providers/blockednumber/Utils.java).
 */
object PrefixRules {

    /** Digits that must follow the country code, so a rule never covers a whole country or city by slip. */
    const val MIN_DIGITS = 3

    /**
     * [typed] ("02 81 03", "+33 2 81 03", "0033 2 81 03", "+33 (0)2 81 03") as "+3328103", or null when
     * it is too short, not a number, or national with no [region] to read it in. The country code of a
     * national form and the trunk digit come from libphonenumber (getCountryCodeForRegion,
     * getNddPrefixForRegion, getRegionCodeForCountryCode:
     * https://github.com/google/libphonenumber/blob/master/java/libphonenumber/src/com/google/i18n/phonenumbers/PhoneNumberUtil.java),
     * so the trunk 0 goes in France and stays in Italy.
     */
    fun normalize(typed: String, region: String?, minDigits: Int = MIN_DIGITS): String? {
        val text = typed.substringBefore(';').trim()
        val digits = text.filterIndexed { i, c -> c.isDigit() || (c == '+' && i == 0) }
        if (digits.isEmpty()) return null
        val util = PhoneNumberUtil.getInstance()
        val international = when {
            digits.startsWith("+") -> digits
            digits.startsWith("00") -> "+" + digits.removePrefix("00")
            else -> null
        }
        val (code, rest) = if (international != null) {
            val all = international.drop(1)
            val length = (1..3).firstOrNull { it <= all.length && util.getRegionCodeForCountryCode(all.take(it).toInt()) != "ZZ" }
                ?: return null
            val code = all.take(length)
            val regionOfCode = util.getRegionCodeForCountryCode(code.toInt())
            val trunk = util.getNddPrefixForRegion(regionOfCode, false)
            val rest = all.drop(length)
            code to (if (!trunk.isNullOrEmpty() && rest.startsWith(trunk)) rest.removePrefix(trunk) else rest)
        } else {
            val iso = region?.uppercase(Locale.ROOT) ?: return null
            val code = util.getCountryCodeForRegion(iso)
            if (code == 0) return null
            val trunk = util.getNddPrefixForRegion(iso, false)
            code.toString() to (if (!trunk.isNullOrEmpty() && digits.startsWith(trunk)) digits.removePrefix(trunk) else digits)
        }
        if (rest.length < minDigits || !rest.all(Char::isDigit)) return null
        return "+$code$rest"
    }

    /** Lines given out in ranges to companies and call centres: a range of them can be blocked. */
    private val RANGE_TYPES = setOf(
        PhoneNumberUtil.PhoneNumberType.FIXED_LINE,
        PhoneNumberUtil.PhoneNumberType.VOIP,
        PhoneNumberUtil.PhoneNumberType.TOLL_FREE,
        PhoneNumberUtil.PhoneNumberType.PREMIUM_RATE,
        PhoneNumberUtil.PhoneNumberType.SHARED_COST,
        PhoneNumberUtil.PhoneNumberType.UAN
    )

    /**
     * Whether blocking the numbers that start like [number] makes sense: only for a fixed, internet or service
     * line (France's 01 to 05 and 09, 08), never a mobile (06, 07), which belongs to a person, nor a number of a
     * country where the two cannot be told apart (FIXED_LINE_OR_MOBILE, the United States). The type is
     * libphonenumber's, read from its metadata for every country (parse and getNumberType:
     * https://github.com/google/libphonenumber/blob/master/java/libphonenumber/src/com/google/i18n/phonenumbers/PhoneNumberUtil.java).
     */
    fun offersRange(number: String, region: String?): Boolean {
        val util = PhoneNumberUtil.getInstance()
        val parsed = runCatching { util.parse(number, region?.uppercase(Locale.ROOT) ?: "ZZ") }.getOrNull() ?: return false
        return util.getNumberType(parsed) in RANGE_TYPES
    }

    /**
     * [number] in international form: the system's own E.164 when it knows the number, else the same
     * reading as [normalize] (a number cut short, or the system not answering).
     */
    fun e164(number: String, region: String?): String? {
        val iso = region?.uppercase(Locale.ROOT)
        val system = runCatching { PhoneNumberUtils.formatNumberToE164(number, iso) }.getOrNull()
        return system ?: normalize(number, region, minDigits = 1)
    }

    /**
     * The rule [number] falls under: the longest prefix it starts with; Block before Silence for two
     * of the same length. [region] is how a national number is read.
     */
    fun match(rules: List<BlockedPrefix>, number: String, region: String?): BlockedPrefix? {
        if (rules.isEmpty() || number.isBlank()) return null
        val e164 = e164(number, region) ?: return null
        return rules.filter { e164.startsWith(it.prefix) }
            .maxWithOrNull(compareBy<BlockedPrefix> { it.prefix.length }.thenBy { it.action == PrefixAction.BLOCK })
    }

    /** [rules] with [rule] added, replacing the one for the same prefix. */
    fun with(rules: List<BlockedPrefix>, rule: BlockedPrefix): List<BlockedPrefix> =
        rules.filterNot { it.prefix == rule.prefix } + rule

    /** [rules] without the one for [prefix]. */
    fun without(rules: List<BlockedPrefix>, prefix: String): List<BlockedPrefix> =
        rules.filterNot { it.prefix == prefix }
}
