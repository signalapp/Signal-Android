/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contactshare

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import org.thoughtcrime.securesms.contactshare.screens.editname.ContactNameParts

/** Routes for [ContactShareNavHost]. */
@Serializable
sealed interface ContactShareRoute : NavKey {
  @Serializable
  data object SelectContact : ContactShareRoute

  @Serializable
  data class Share(val source: SharedContactSource) : ContactShareRoute

  @Serializable
  data class EditName(val parts: ContactNameParts) : ContactShareRoute
}
