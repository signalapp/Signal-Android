package org.thoughtcrime.securesms.fonts

/**
 * Attempt to guess the script based on the unicode characters used. The script
 * with the most use in a string will be picked. Tie goes to [SupportedScript.LATIN].
 * Unicode does not cleanly separate Hong Kong and Chinese character ranges so some
 * other means must be used to distinguish it (e.g., Locale).
 */
object TextToScript {

  private val LATIN_RANGES: List<IntRange> = listOf(0x0000..0x024F, 0x1E00..0x1EFF, 0x2C60..0x2C7F, 0xA720..0xA7FF, 0xAB30..0xAB6F)
  private val CYRILLIC_RANGES: List<IntRange> = listOf(0x0400..0x04FF, 0x0500..0x052F, 0x1C80..0x1C8F, 0x2DE0..0x2DFF, 0xA640..0xA69F)
  private val DEVANAGARI_RANGES: List<IntRange> = listOf(0x0900..0x097F, 0xA8E0..0xA8FF, 0x30A0..0x30FF)
  private val CJK_RANGES: List<IntRange> = listOf(0x31C0..0x31EF, 0x3300..0x33FF, 0x4E00..0x9FFF, 0xF900..0xFAFF, 0xFE30..0xFE4F, 0x20000..0x2EBEF, 0x2F800..0x2FA1F)
  private val CJK_JAPANESE_RANGES: List<IntRange> = listOf(0x3040..0x309F, 0x30A0..0x30FF, 0x3190..0x319F)
  private val ARABIC_RANGES: List<IntRange> = listOf(0x0600..0x06FF, 0x0750..0x077F, 0x0870..0x089F, 0x08A0..0x08FF)

  private val allRanges = mapOf(
    SupportedScript.LATIN to LATIN_RANGES,
    SupportedScript.CYRILLIC to CYRILLIC_RANGES,
    SupportedScript.DEVANAGARI to DEVANAGARI_RANGES,
    SupportedScript.UNKNOWN_CJK to CJK_RANGES,
    SupportedScript.JAPANESE to CJK_JAPANESE_RANGES,
    SupportedScript.ARABIC to ARABIC_RANGES
  )

  fun guessScript(text: CharSequence): SupportedScript {
    val scriptCounts: MutableMap<SupportedScript, Int> = SupportedScript.entries.associate { it to 0 }.toMutableMap()
    val input = text.toString()

    for (i in 0 until input.codePointCount(0, input.length)) {
      val codePoint = input.codePointAt(i)
      for ((script, ranges) in allRanges) {
        if (ranges.contains(codePoint)) {
          scriptCounts[script] = scriptCounts[script]!! + 1
        }
      }
    }

    val most: SupportedScript = scriptCounts.maxByOrNull { it.value }?.key ?: SupportedScript.UNKNOWN

    return if (most == SupportedScript.UNKNOWN_CJK && scriptCounts[SupportedScript.JAPANESE]!! > 0) {
      SupportedScript.JAPANESE
    } else {
      most
    }
  }

  private fun List<IntRange>.contains(x: Int): Boolean = any { x in it }
}
