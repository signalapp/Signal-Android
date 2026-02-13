/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.keyvalue.SignalStore

/**
 * Application subclass for the quickstart build variant.
 * On first launch, if the account is not yet registered, it triggers
 * [QuickstartInitializer] to import pre-baked credentials from assets.
 */
class QuickstartApplicationContext : ApplicationContext() {

  companion object {
    private val TAG = Log.tag(QuickstartApplicationContext::class.java)
  }

  override fun onCreate() {
    super.onCreate()
    if (!SignalStore.account.isRegistered) {
      Log.i(TAG, "Account not registered, attempting quickstart initialization...")
      QuickstartInitializer.initialize(this)
    }
  }
}
