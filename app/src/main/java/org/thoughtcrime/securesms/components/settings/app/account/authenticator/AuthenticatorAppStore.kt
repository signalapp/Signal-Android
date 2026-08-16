/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.authenticator

/**
 * Stand-in for wherever authenticator app state will eventually live. Nothing is persisted or sent to the service yet,
 * so all of this is mocked up and lasts only as long as the process does.
 */
object AuthenticatorAppStore {

  /** The key we'd hand off to an authenticator app, which the service will supply for real later on. */
  const val MOCK_SETUP_KEY = "KVZ7WL3FDDWJZMTOB7PLZPKVRFD4LYSX"

  @Volatile
  var hasAuthenticatorApp: Boolean = false
}
