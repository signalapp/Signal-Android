package org.signal.locale

import java.util.Locale

object LanguageString {

  private const val BASE_LANGUAGE = "en"

  /**
   * @param languageString String in format language_REGION, e.g. en_US
   * @return Locale, or null if cannot parse
   */
  @JvmStatic
  fun parseLocale(languageString: String?): Locale? {
    if (languageString.isNullOrEmpty()) {
      return null
    }

    val locale = createLocale(languageString)

    return if (isValid(locale)) locale else null
  }

  /** English strings are the default resources, which are always in the base APK. */
  @JvmStatic
  fun isBaseLanguage(locale: Locale): Boolean = normalize(locale.language) == BASE_LANGUAGE

  /** Play reports installed languages as tags such as "de" or "pt-BR" but splits are named by language only. */
  @JvmStatic
  fun matches(installed: String, locale: Locale): Boolean {
    return installed.equals(locale.toLanguageTag(), ignoreCase = true) ||
      normalize(installed.substringBefore('-')) == normalize(locale.language)
  }

  /** Android resources and older Locale implementations still use the legacy codes. */
  private fun normalize(language: String): String {
    return when (val lowercase = language.lowercase(Locale.ROOT)) {
      "iw" -> "he"
      "in" -> "id"
      "ji" -> "yi"
      else -> lowercase
    }
  }

  private fun createLocale(languageString: String): Locale {
    val language = languageString.split("_")
    return if (language.size == 2) {
      Locale(language[0], language[1])
    } else {
      Locale(language[0])
    }
  }

  private fun isValid(locale: Locale): Boolean {
    return try {
      locale.getISO3Language() != null && locale.getISO3Country() != null
    } catch (_: Exception) {
      false
    }
  }
}
