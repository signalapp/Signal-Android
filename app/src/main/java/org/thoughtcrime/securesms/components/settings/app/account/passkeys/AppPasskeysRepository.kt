/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.passkeys

import org.signal.appsettings.account.TwoFactorMethod

/**
 * Stand-in for wherever passkeys will eventually be read from. Nothing is fetched from the service yet, so there are
 * never any passkeys.
 */
class AppPasskeysRepository {

  companion object {
    /** Empty so nothing fake reaches a real account. Fill it in locally to see passkey rows while testing. */
    private val MOCK_PASSKEYS = emptyList<TwoFactorMethod>()
  }

  fun getPasskeys(): List<TwoFactorMethod> = MOCK_PASSKEYS
}
