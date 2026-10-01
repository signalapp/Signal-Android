/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contactshare

import android.content.Context
import android.content.Intent
import android.os.Bundle
import org.thoughtcrime.securesms.PassphraseRequiredActivity

/**
 * Hosts [SelectContactFragment].
 */
class SelectContactActivity : PassphraseRequiredActivity() {

  companion object {
    const val KEY_SOURCE = "source"

    @JvmStatic
    fun getIntent(context: Context): Intent {
      return Intent(context, SelectContactActivity::class.java)
    }
  }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    super.onCreate(savedInstanceState, ready)

    if (savedInstanceState == null) {
      supportFragmentManager.beginTransaction()
        .replace(android.R.id.content, SelectContactFragment.create())
        .commit()
    }
  }
}
