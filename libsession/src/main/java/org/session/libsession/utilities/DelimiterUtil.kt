package org.session.libsession.utilities

import java.util.regex.Pattern

object DelimiterUtil {
    fun escape(value: String, delimiter: Char): String {
        return value.replace("" + delimiter, "\\" + delimiter)
    }

    fun unescape(value: String, delimiter: Char): String {
        return value.replace("\\" + delimiter, "" + delimiter)
    }

    fun split(value: String, delimiter: Char): Array<String> {
        val regex = "(?<!\\\\)" + Pattern.quote(delimiter.toString() + "")
        return value.split(regex).toTypedArray()
    }
}