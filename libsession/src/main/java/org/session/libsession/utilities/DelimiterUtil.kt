package org.session.libsession.utilities

import java.util.regex.Pattern

object DelimiterUtil {
    @JvmStatic
    fun escape(value: String, delimiter: Char): String {
        return value.replace("" + delimiter, "\\" + delimiter)
    }

    @JvmStatic
    fun unescape(value: String, delimiter: Char): String {
        return value.replace("\\" + delimiter, "" + delimiter)
    }

    @JvmStatic
    fun split(value: String, delimiter: Char): List<String> {
        val regex = Regex("(?<!\\\\)" + Pattern.quote(delimiter + ""))
        return value.split(regex)
    }
}