/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contactshare.screens.editname

sealed interface EditContactNameResult {
  data class Saved(val parts: ContactNameParts) : EditContactNameResult
  data object Cancelled : EditContactNameResult
}
