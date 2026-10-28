package com.yaz.dialer.feature.dialpad

import com.google.i18n.phonenumbers.PhoneNumberUtil

/**
 * The number as it is shown on the keypad: grouped while it is typed ("06 97 54").
 * The typed number itself stays raw digits; only the display is grouped.
 */
object DialFormat {

    /**
     * Ported from AOSP PhoneNumberFormattingTextWatcher.reformat() and getFormattedNumber(): a fresh
     * AsYouTypeFormatter is cleared, fed every digit of the number in order, and the result of the last
     * inputDigit is the text. Cursor tracking (inputDigitAndRememberPosition) is dropped: the field
     * is not editable. The watcher stops formatting once a separator is typed; here a "*", "#", ","
     * or ";" or a letter anywhere keeps the whole number raw, so codes like *#06# stay as typed.
     * https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/telephony/java/android/telephony/PhoneNumberFormattingTextWatcher.java
     * Formatter: https://github.com/google/libphonenumber (AsYouTypeFormatter.java, Apache-2.0).
     */
    fun asYouType(number: String, iso: String): String {
        if (number.isEmpty() || !isDialable(number)) return number
        val region = iso.uppercase().takeIf { it.length == 2 } ?: return number
        return runCatching {
            val formatter = PhoneNumberUtil.getInstance().getAsYouTypeFormatter(region)
            formatter.clear()
            var formatted = number
            for (c in number) formatted = formatter.inputDigit(c)
            formatted
        }.getOrDefault(number)
    }

    /** Digits, with a "+" only first: what a phone number is; anything else (codes, letters) is shown raw. */
    private fun isDialable(number: String): Boolean =
        number.withIndex().all { (i, c) -> c in '0'..'9' || (c == '+' && i == 0) }
}
