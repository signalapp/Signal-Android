/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.screener

/**
 * The language a [StressMatrix] renders string resources in, by BCP-47 tag in [locale].
 */
sealed interface StressLanguage {
  val label: String
  val locale: String

  /**
   * A real translation, such as "de". Layouts tested against one can start failing whenever new translations land,
   * so prefer a [PseudoLanguage] for tests that gate merges.
   */
  data class Translation(override val locale: String) : StressLanguage {
    override val label: String = locale
  }
}

/**
 * Translations generated from the English strings into debug builds by the `pseudo-locales` build plugin, which
 * describes how each one stretches them. A layout fails against one as soon as its code or English copy changes,
 * rather than when real translations next land. Only strings from Signal modules are translated: framework and
 * library strings stay English.
 */
enum class PseudoLanguage(override val label: String, override val locale: String) : StressLanguage {
  /** More words, a little longer, with the same places to break between them, like French or Dutch. */
  EXPANSIVE("pseudo-expansive", "qaa"),

  /** Longer words joined into compounds with nowhere to break, like German or Finnish. */
  COMPOUND("pseudo-compound", "qab")
}
