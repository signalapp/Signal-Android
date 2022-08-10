package org.session.libsignal.utilities

fun String.removingIdPrefixIfNeeded(): String {
  return if (length == 66 && IdPrefix.fromValue(this) != null) removeRange(0..1) else this
}

fun ByteArray.removingIdPrefixIfNeeded(): ByteArray {
    val string = Hex.toStringCondensed(this).removingIdPrefixIfNeeded()
    return Hex.fromStringCondensed(string)
}
