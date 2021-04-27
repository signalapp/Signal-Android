package org.session.libsession.utilities

import android.telephony.PhoneNumberUtils
import android.util.Patterns

object NumberUtil {
    private val emailPattern = Patterns.EMAIL_ADDRESS

    @JvmStatic
    fun isValidEmail(number: String): Boolean {
        val matcher = emailPattern.matcher(number)
        return matcher.matches()
    }

    @JvmStatic
    fun isValidSmsOrEmail(number: String): Boolean {
        return PhoneNumberUtils.isWellFormedSmsAddress(number) || isValidEmail(number)
    }
}