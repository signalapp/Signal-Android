package org.session.libsignal.utilities

enum class IdPrefix(val value: String) {
    STANDARD("05"), BLINDED("15"), UN_BLINDED("00");

    companion object {
        fun fromValue(rawValue: String): IdPrefix? = when(rawValue.take(2)) {
            STANDARD.value -> STANDARD
            BLINDED.value -> BLINDED
            UN_BLINDED.value -> UN_BLINDED
            else -> null
        }
    }

}