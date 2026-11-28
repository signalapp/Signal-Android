/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.invite

/**
 * [InviteScreen] state.
 *
 * This is logged, so be sure `toString()` contains nothing sensitive.
 */
data class InviteState(val inviteText: String) {
  override fun toString(): String = "InviteState"
}
