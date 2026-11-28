/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.locale

import java.util.Locale

/**
 * The languages whose resources are on disk. A universal APK carries every language.
 *
 * @param splitLanguageCodes The language codes for available splits on disk. Null for a universal APK.
 */
data class InstalledLanguages(val splitLanguageCodes: Set<String>?) {
  val isSplitInstall: Boolean
    get() = splitLanguageCodes != null

  fun contains(locale: Locale): Boolean {
    return splitLanguageCodes == null || LanguageString.isBaseLanguage(locale) || splitLanguageCodes.any { LanguageString.matches(it, locale) }
  }
}
