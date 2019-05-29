package org.thoughtcrime.securesms.loki

import android.content.Context
import java.util.zip.CRC32

class MnemonicCodec(val context: Context) {

    class Language(val context: Context, val configuration: Configuration) {

        data class Configuration(val filename: String, val prefixLength: Int) {

            companion object {
                val english = Configuration("english", 3)
                val japanese = Configuration("japanese", 3)
                val portuguese = Configuration("portuguese", 4)
                val spanish = Configuration("spanish", 4)
            }
        }

        companion object {
            val wordSetCache = mutableMapOf<Language, List<String>>()
            val truncatedWordSetCache = mutableMapOf<Language, List<String>>()
        }

        fun loadWordSet(): List<String> {
            val cachedResult = wordSetCache[this]
            return if (cachedResult != null) {
                cachedResult
            } else {
                val contents = context.assets.open(configuration.filename + ".txt").bufferedReader().readLine()
                val result = contents.split(",")
                wordSetCache[this] = result
                result
            }
        }

        fun loadTruncatedWordSet(): List<String> {
            val cachedResult = wordSetCache[this]
            return if (cachedResult != null) {
                cachedResult
            } else {
                val prefixLength = configuration.prefixLength
                val result = loadWordSet().map { it.substring(0 until prefixLength) }
                truncatedWordSetCache[this] = result
                result
            }
        }
    }

    sealed class DecodingError(val description: String) : Error() {
        object Generic : DecodingError("Something went wrong. Please check your mnemonic and try again.")
        object InputTooShort : DecodingError("Looks like you didn't enter enough words. Please check your mnemonic and try again.")
        object MissingLastWord : DecodingError("You seem to be missing the last word of your mnemonic. Please check what you entered and try again.")
        object InvalidWord : DecodingError("There appears to be an invalid word in your mnemonic. Please check what you entered and try again.")
        object VerificationFailed : DecodingError("Your mnemonic couldn't be verified. Please check what you entered and try again.")
    }

    fun encode(hexEncodedString: String, languageConfiguration: Language.Configuration = Language.Configuration.english): String {
        var string = hexEncodedString
        val language = Language(context, languageConfiguration)
        val wordSet = language.loadWordSet()
        val prefixLength = languageConfiguration.prefixLength
        val result = mutableListOf<String>()
        val n = wordSet.size
        val characterCount = string.length
        for (chunkStartIndex in 0 until characterCount step 2) {
            val chunkEndIndex = chunkStartIndex + 8
            val p1 = string.substring(0 until chunkStartIndex)
            val p2 = swap(string.substring(chunkStartIndex until chunkEndIndex))
            val p3 = string.substring(chunkEndIndex until characterCount)
            string = p1 + p2 + p3
        }
        for (chunkStartIndex in 0 until characterCount step 2) {
            val chunkEndIndex = chunkStartIndex + 8
            val x = string.substring(chunkStartIndex until chunkEndIndex).toInt(16)
            val w1 = x % n
            val w2 = ((x / n) + w1) % n
            val w3 = (((x / n) / n) + w2) % n
            result += listOf ( wordSet[w1], wordSet[w2], wordSet[w3] )
        }
        val checksumIndex = determineChecksumIndex(result, prefixLength)
        val checksumWord = result[checksumIndex]
        result.add(checksumWord)
        return result.joinToString(" ")
    }

    fun decode(mnemonic: String, languageConfiguration: Language.Configuration = Language.Configuration.english): String {
        val words = mnemonic.split(" ").toMutableList()
        val language = Language(context, languageConfiguration)
        val truncatedWordSet = language.loadTruncatedWordSet()
        val prefixLength = languageConfiguration.prefixLength
        var result = ""
        val n = truncatedWordSet.size
        // Check preconditions
        if (words.size < 12) { throw DecodingError.InputTooShort }
        if (words.size % 3 != 0) { throw DecodingError.MissingLastWord }
        // Get checksum word
        val checksumWord = words.removeAt(words.lastIndex)
        // Decode
        for (chunkStartIndex in 0 until words.size step 3) {
            try {
                val w1 = truncatedWordSet.indexOf(words[chunkStartIndex].substring(0 until prefixLength))
                val w2 = truncatedWordSet.indexOf(words[chunkStartIndex + 1].substring(0 until prefixLength))
                val w3 = truncatedWordSet.indexOf(words[chunkStartIndex + 2].substring(0 until prefixLength))
                val x = w1 + n * ((n - w1 + w2) % n) + n * n * ((n - w2 + w3) % n)
                if (x % n != w1) { throw DecodingError.Generic }
                val string = "0000000" + x.toString(16)
                result += swap(string.substring(string.lastIndex - 8 until string.lastIndex))
            } catch (e: Exception) {
                throw DecodingError.InvalidWord
            }
        }
        // Verify checksum
        val checksumIndex = determineChecksumIndex(words, prefixLength)
        val expectedChecksumWord = words[checksumIndex]
        if (expectedChecksumWord.substring(0 until prefixLength) != checksumWord.substring(0 until prefixLength)) { throw DecodingError.VerificationFailed }
        // Return
        return result
    }

    private fun swap(x: String): String {
        val p1 = x.substring(6 until 8)
        val p2 = x.substring(4 until 6)
        val p3 = x.substring(2 until 4)
        val p4 = x.substring(0 until 2)
        return p1 + p2 + p3 + p4
    }

    private fun determineChecksumIndex(x: List<String>, prefixLength: Int): Int {
        val bytes = x.joinToString { it.substring(0 until prefixLength) }.toByteArray()
        val crc32 = CRC32()
        crc32.update(bytes)
        val checksum = crc32.value
        return checksum.toInt() % x.size

    }
}