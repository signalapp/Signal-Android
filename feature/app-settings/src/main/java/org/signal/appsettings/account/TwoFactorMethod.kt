/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.account

import org.signal.core.util.censor

/**
 * One second factor on the user's account, as shown in the unified two-factor list on [AccountSettingsScreen].
 *
 * Ids are only unique within a [kind], since each kind comes from a different place on the account, so anything
 * identifying a method has to carry both.
 */
data class TwoFactorMethod(
  val id: Long,
  val kind: Kind,
  val name: String,
  /** When the method was added to the account, in epoch milliseconds. */
  val createdAt: Long
) {

  /** What sort of second factor this is, which decides its icon, its subtitle, and what its menu can do. */
  enum class Kind {
    AUTHENTICATOR_APP,
    PASSKEY
  }

  override fun toString(): String = "TwoFactorMethod(id=$id, kind=$kind, name=${name.censor()}, createdAt=$createdAt)"
}
