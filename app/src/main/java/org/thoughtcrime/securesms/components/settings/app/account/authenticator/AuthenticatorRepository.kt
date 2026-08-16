/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.authenticator

class AuthenticatorRepository {

  fun getSetupKey(): String = AuthenticatorAppStore.MOCK_SETUP_KEY

  fun hasAuthenticatorApp(): Boolean = AuthenticatorAppStore.hasAuthenticatorApp

  fun setHasAuthenticatorApp(hasAuthenticatorApp: Boolean) {
    AuthenticatorAppStore.hasAuthenticatorApp = hasAuthenticatorApp
  }
}
