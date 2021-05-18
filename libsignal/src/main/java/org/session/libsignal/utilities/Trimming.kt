package org.session.libsignal.utilities

import org.session.libsignal.utilities.Hex

fun String.removing05PrefixIfNeeded(): String {
  return if (length == 66) removePrefix("05") else this
}

fun ByteArray.removing05PrefixIfNeeded(): ByteArray {
    val string = Hex.toStringCondensed(this).removing05PrefixIfNeeded()
    return Hex.fromStringCondensed(string)
}
