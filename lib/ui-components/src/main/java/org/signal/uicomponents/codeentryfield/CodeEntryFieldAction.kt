/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.uicomponents.codeentryfield

/**
 * Side effects that can be emitted by a [CodeEntryFieldPresenter] that need to be handled by the user of the component.
 *
 * Actions are logged, so be sure `toString()` contains nothing sensitive.
 */
sealed interface CodeEntryFieldAction {

  /** Every digit has a value, so here's the [code] the user entered. */
  data class CodeEntered(val code: String) : CodeEntryFieldAction {
    override fun toString(): String = "CodeEntered()"
  }
}
