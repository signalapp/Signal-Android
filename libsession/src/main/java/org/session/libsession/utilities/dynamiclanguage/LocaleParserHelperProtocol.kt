package org.session.libsession.utilities.dynamiclanguage

import java.util.Locale

interface LocaleParserHelperProtocol {
    fun appSupportsTheExactLocale(locale: Locale?): Boolean
    fun findBestSystemLocale(): Locale
}