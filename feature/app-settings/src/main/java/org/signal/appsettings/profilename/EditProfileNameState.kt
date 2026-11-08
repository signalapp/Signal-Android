/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.profilename

import org.signal.core.util.StringUtil

data class EditProfileNameState(
  /** Already capped to the profile name limits by whoever fed it in. */
  val givenName: String = "",
  /** Already capped to the profile name limits by whoever fed it in. */
  val familyName: String = "",
  val saving: Boolean = false,
  val snackbar: Snackbar = Snackbar.None
) {

  val canSave: Boolean
    get() = !StringUtil.isVisuallyEmpty(givenName) && !saving

  override fun toString(): String = "EditProfileNameState(givenNameLength=${givenName.length}, familyNameLength=${familyName.length}, saving=$saving, snackbar=$snackbar)"

  sealed interface Snackbar {
    data object None : Snackbar

    /** The name couldn't be uploaded, so nothing was saved. */
    data object NetworkFailure : Snackbar
  }
}
