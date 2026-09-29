/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.twofactornameentry

import org.signal.appsettings.account.TwoFactorMethod

data class TwoFactorNameEntryState(
  /**
   * The name so far, already capped in grapheme clusters and in UTF-8 bytes by whoever fed it in. Nothing here needs to
   * know either limit: an over-length name is never a state this screen has to render or explain, because the field
   * simply stops accepting one.
   */
  val name: String = "",
  /** What sort of second factor is being named, which is all that separates the two sets of copy. */
  val kind: TwoFactorMethod.Kind = TwoFactorMethod.Kind.AUTHENTICATOR_APP,
  /** True when an already-configured method is being renamed, false when one is being named for the first time. */
  val renaming: Boolean = false,
  val submitting: Boolean = false
) {

  val canSubmit: Boolean
    get() = name.isNotBlank() && !submitting

  override fun toString(): String = "TwoFactorNameEntryState(nameLength=${name.length}, kind=$kind, renaming=$renaming, submitting=$submitting)"
}
