package org.thoughtcrime.securesms.util

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.thoughtcrime.securesms.stickers.StickerUrl
import java.net.URI
import java.net.URISyntaxException
import java.util.Objects
import java.util.regex.Pattern

/**
 * Utilities for validating various links for multiple situations.
 */
object LinkUtil {
  private val DOMAIN_PATTERN = Pattern.compile("^(https?://)?([^/]+).*$")
  private val ALL_ASCII_PATTERN = Pattern.compile("^[\\x00-\\x7F]*$")
  private val ALL_NON_ASCII_PATTERN = Pattern.compile("^[^\\x00-\\x7F]*$")
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

    val cleanedDomain = domain.replace("\\.".toRegex(), "")
    return LegalCharactersResult(
      isLegal = ALL_ASCII_PATTERN.matcher(cleanedDomain).matches() || ALL_NON_ASCII_PATTERN.matcher(cleanedDomain).matches(),
      domain = domain
    )
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
