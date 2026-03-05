package org.thoughtcrime.securesms.util

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.thoughtcrime.securesms.stickers.StickerUrl
import java.io.ByteArrayOutputStream
import java.net.IDN
import java.net.URI
import java.net.URISyntaxException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.Objects
import java.util.regex.Pattern

/**
 * Utilities for validating various links for multiple situations.
 */
object LinkUtil {
  private val DOMAIN_PATTERN = Pattern.compile("^(https?://)?([^/]+).*$")
  private val ILLEGAL_CHARACTERS_PATTERN = Pattern.compile("[\u202C\u202D\u202E\u2500-\u25FF]")
  private val ILLEGAL_PERIODS_PATTERN = Pattern.compile("(\\.{2,}|…)")
  private val INVALID_DOMAINS = listOf("example", "example\\.com", "example\\.net", "example\\.org", "i2p", "invalid", "localhost", "onion", "test")
  private val INVALID_DOMAINS_REGEX: Regex = Regex("^(.+\\.)?(${INVALID_DOMAINS.joinToString("|")})\\.?\$")

  /**
   * Link previews must have all valid URL characters, an allowed domain if present, and must include https://
   */
  @JvmStatic
  fun isValidPreviewUrl(linkUrl: String?): Boolean {
    if (linkUrl == null) {
      return false
    }

    if (StickerUrl.isValidShareLink(linkUrl)) {
      return true
    }

    val (isLegal, domain) = isLegalUrlInternal(linkUrl)

    if (!isLegal || domain?.matches(INVALID_DOMAINS_REGEX) == true) {
      return false
    }

    if (!isValidURI(linkUrl)) {
      return false
    }

    return linkUrl.toHttpUrlOrNull()?.scheme == "https"
  }

  /**
   * Text story link previews must have all valid URL characters, a present and allowed domain, and must have a TLD.
   */
  @JvmStatic
  fun isValidTextStoryPostPreview(url: String): Boolean {
    val (isLegal, domain) = isLegalUrlInternal(url)

    if (!isLegal || domain == null || domain.matches(INVALID_DOMAINS_REGEX)) {
      return false
    }

    return domain.lastIndexOf('.', domain.lastIndex) != -1
  }

  /**
   * A URL is legal if it has all valid URL characters.
   */
  @JvmStatic
  fun isLegalUrl(url: String): Boolean {
    return isLegalUrlInternal(url).isLegal
  }

  private fun isLegalUrlInternal(url: String): LegalCharactersResult {
    if (ILLEGAL_CHARACTERS_PATTERN.matcher(url).find()) {
      return LegalCharactersResult(false)
    }

    val matcher = DOMAIN_PATTERN.matcher(url)
    if (!matcher.matches()) {
      return LegalCharactersResult(false)
    }

    val domain = Objects.requireNonNull(matcher.group(2))

    if (ILLEGAL_PERIODS_PATTERN.matcher(domain).find()) {
      return LegalCharactersResult(false)
    }

    return LegalCharactersResult(
      isLegal = !mixesScripts(domain),
      domain = domain
    )
  }

  /**
   * Returns true if [str] contains letters from more than one Unicode script,
   * ignoring characters with script COMMON or INHERITED (digits, punctuation, etc.).
   * Used to detect potential homograph attacks in domain names: a domain that mixes,
   * say, Cyrillic and Latin letters is suspicious, while an IDN label like "grå"
   * that uses only Latin letters (including extended Latin like å) is fine.
   */
  private fun mixesScripts(str: String): Boolean {
    var firstScript: Character.UnicodeScript? = null
    var i = 0
    while (i < str.length) {
      val cp = str.codePointAt(i)
      if (Character.isLetter(cp)) {
        val script = Character.UnicodeScript.of(cp)
        if (script != Character.UnicodeScript.COMMON && script != Character.UnicodeScript.INHERITED) {
          if (firstScript == null) {
            firstScript = script
          } else if (script != firstScript) {
            return true
          }
        }
      }
      i += Character.charCount(cp)
    }
    return false
  }

  /**
   * Converts a URL to a human-readable display form:
   * 1. ACE/punycode domain labels are decoded to Unicode when the decoded domain passes [isLegalUrl].
   * 2. Percent-encoded path bytes are decoded when they represent ASCII letters, ASCII digits,
   *    hyphens, or sequences of UTF-8 bytes that decode to Unicode letters or digits.
   *    All other percent-encoded bytes (spaces, slashes, control chars, …) are left as-is.
   */
  @JvmStatic
  fun toDisplayUrl(url: String): String {
    return try {
      val uri = URI(url)
      val host = uri.host ?: return url

      val unicodeHost = IDN.toUnicode(host)
      val displayHost = if (isLegalUrl(unicodeHost)) unicodeHost else host
      val niceRawPath = decodeUrlSafeChars(uri.rawPath ?: "")

      buildString {
        if (uri.scheme != null) append("${uri.scheme}://")
        if (uri.rawUserInfo != null) append("${uri.rawUserInfo}@")
        append(displayHost)
        if (uri.port != -1) append(":${uri.port}")
        append(niceRawPath)
        if (uri.rawQuery != null) append("?${uri.rawQuery}")
        if (uri.rawFragment != null) append("#${uri.rawFragment}")
      }
    } catch (e: Exception) {
      url
    }
  }

  /**
   * Decodes percent-encoded byte sequences that represent ASCII letters, ASCII digits, hyphens,
   * or multi-byte UTF-8 sequences whose decoded Unicode code point is a letter or digit.
   * All other percent-encoded sequences are left unchanged.
   *
   * If fully decoding all percent-encoded bytes would not yield valid UTF-8, the string is
   * returned unchanged — partial decoding would produce misleading output (e.g. a bare lead
   * byte next to a decoded ASCII character that happened to share a code unit with a
   * continuation byte).
   */
  private fun decodeUrlSafeChars(encoded: String): String {
    if (!encoded.contains('%')) return encoded
    if (!isFullyDecodedUtf8Valid(encoded)) return encoded
    val sb = StringBuilder(encoded.length)
    var i = 0
    while (i < encoded.length) {
      val c = encoded[i]
      if (c != '%' || i + 2 >= encoded.length) {
        sb.append(c)
        i++
        continue
      }
      val firstHex = encoded.substring(i + 1, i + 3).toIntOrNull(16)
      if (firstHex == null) {
        sb.append(c)
        i++
        continue
      }
      val firstByte = firstHex and 0xFF
      val cpByteCount = when {
        firstByte and 0x80 == 0    -> 1  // 0xxxxxxx  ASCII
        firstByte and 0xE0 == 0xC0 -> 2  // 110xxxxx  2-byte UTF-8
        firstByte and 0xF0 == 0xE0 -> 3  // 1110xxxx  3-byte UTF-8
        firstByte and 0xF8 == 0xF0 -> 4  // 11110xxx  4-byte UTF-8
        else                       -> 0  // continuation or invalid lead byte
      }
      if (cpByteCount <= 0) {
        sb.append(encoded, i, i + 3)
        i += 3
        continue
      }
      // Collect cpByteCount consecutive %XX tokens.
      val rawTokens = ArrayList<String>(cpByteCount)
      val rawBytes  = ArrayList<Byte>(cpByteCount)
      var j = i
      var ok = true
      for (k in 0 until cpByteCount) {
        if (j + 2 >= encoded.length || encoded[j] != '%') { ok = false; break }
        val hex  = encoded.substring(j + 1, j + 3)
        val bInt = hex.toIntOrNull(16)
        if (bInt == null) { ok = false; break }
        if (k > 0 && (bInt and 0xC0 != 0x80)) { ok = false; break }  // must be continuation byte
        rawTokens.add(encoded.substring(j, j + 3))
        rawBytes.add(bInt.toByte())
        j += 3
      }
      if (!ok || rawBytes.size != cpByteCount) {
        // Could not assemble a complete code point — emit only the first %XX raw.
        sb.append(encoded, i, i + 3)
        i += 3
        continue
      }
      val byteArray = rawBytes.toByteArray()
      val decoded   = String(byteArray, Charsets.UTF_8)
      val cp        = decoded.codePointAt(0)
      if (cp != 0xFFFD && (Character.isLetter(cp) || Character.isDigit(cp) || cp == '-'.code)) {
        sb.appendCodePoint(cp)
      } else {
        sb.append(rawTokens.joinToString(""))
      }
      i = j
    }
    return sb.toString()
  }

  /**
   * Returns true if decoding every percent-encoded byte sequence in [encoded] would yield a
   * byte stream that is valid UTF-8. Literal (non-encoded) characters are already valid Unicode
   * and always contribute valid UTF-8 bytes. Percent sequences with invalid hex digits are
   * treated as literal '%' characters.
   */
  private fun isFullyDecodedUtf8Valid(encoded: String): Boolean {
    val buf = ByteArrayOutputStream(encoded.length)
    var i = 0
    while (i < encoded.length) {
      if (encoded[i] == '%' && i + 2 < encoded.length) {
        val hex = encoded.substring(i + 1, i + 3).toIntOrNull(16)
        if (hex != null) {
          buf.write(hex)
          i += 3
          continue
        }
      }
      val cp = encoded.codePointAt(i)
      buf.write(String(Character.toChars(cp)).toByteArray(Charsets.UTF_8))
      i += Character.charCount(cp)
    }
    return try {
      Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(buf.toByteArray()))
      true
    } catch (_: CharacterCodingException) {
      false
    }
  }

  @JvmStatic
  private fun isValidURI(linkUri: String?): Boolean {
    return if (linkUri == null) {
      false
    } else {
      try {
        val ignored = URI(linkUri)
        true
      } catch (e: URISyntaxException) {
        false
      }
    }
  }

  private data class LegalCharactersResult(val isLegal: Boolean, val domain: String? = null)
}
