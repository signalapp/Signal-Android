package org.thoughtcrime.securesms.util.dynamiclanguage

import android.content.res.Resources
import androidx.core.os.ConfigurationCompat
import network.loki.messenger.BuildConfig
import org.session.libsession.utilities.dynamiclanguage.LocaleParserHelperProtocol
import java.util.*

class LocaleParseHelper: LocaleParserHelperProtocol {

    override fun appSupportsTheExactLocale(locale: Locale?): Boolean {
        return if (locale == null) {
            false
        } else Arrays.asList(*BuildConfig.LANGUAGES).contains(locale.toString())
    }

    override fun findBestSystemLocale(): Locale {
        val config = Resources.getSystem().configuration

        val firstMatch = ConfigurationCompat.getLocales(config)
                .getFirstMatch(BuildConfig.LANGUAGES)

        return firstMatch ?: Locale.ENGLISH

    }
}