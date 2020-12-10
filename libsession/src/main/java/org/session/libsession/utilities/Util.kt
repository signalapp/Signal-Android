package org.session.libsession.utilities

object Util {
    fun join(list: Collection<String?>, delimiter: String?): String {
        val result = StringBuilder()
        var i = 0
        for (item in list) {
            result.append(item)
            if (++i < list.size) result.append(delimiter)
        }
        return result.toString()
    }
}