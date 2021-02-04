package org.session.libsession.utilities.dynamiclanguage

import java.util.*

class LocaleParser(val helper: LocaleParserHelperProtocol) {
    companion object {
        lateinit var shared: LocaleParser

        fun configure(helper: LocaleParserHelperProtocol) {
            if (Companion::shared.isInitialized) { return }
            shared = LocaleParser(helper)
        }

        /**
         * Given a language, gets the best choice from the apps list of supported languages and the
         * Systems set of languages.
         */
        @JvmStatic
        fun findBestMatchingLocaleForLanguage(language: String?): Locale? {
            val locale = LanguageString.parseLocale(language)
            return if (shared.helper.appSupportsTheExactLocale(locale)) {
                locale
            } else {
                shared.helper.findBestSystemLocale()
            }
        }
    }
}