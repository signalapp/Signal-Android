/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.help

sealed interface HelpScreenEvents {
  data class ProblemTextChanged(val text: String) : HelpScreenEvents
  data class CategorySelected(val index: Int) : HelpScreenEvents
  data class FeelingSelected(val feeling: Feeling) : HelpScreenEvents
  data class DebugLogsToggled(val toggle: Boolean) : HelpScreenEvents
  object OnNextClick : HelpScreenEvents
}