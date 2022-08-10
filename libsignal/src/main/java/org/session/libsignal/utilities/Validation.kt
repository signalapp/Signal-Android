package org.session.libsignal.utilities

object PublicKeyValidation {

    @JvmStatic
    fun isValid(candidate: String): Boolean {
        return isValid(candidate, 66, true)
    }

    @JvmStatic
    fun isValid(candidate: String, expectedLength: Int, isPrefixRequired: Boolean): Boolean {
        val hexCharacters = "0123456789ABCDEF".toSet()
        val isValidHexEncoding = hexCharacters.containsAll(candidate.uppercase().toSet())
        val hasValidLength = candidate.length == expectedLength
        val hasValidPrefix = if (isPrefixRequired) IdPrefix.fromValue(candidate) != null else true
        return isValidHexEncoding && hasValidLength && hasValidPrefix
    }
}
