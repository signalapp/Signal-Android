/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contactshare

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.content.IntentCompat
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.PassphraseRequiredActivity

/**
 * Hosts [SharedContactDetailsFragment].
 */
class SharedContactDetailsActivityV2 : PassphraseRequiredActivity() {

  companion object {
    private val TAG = Log.tag(SharedContactDetailsActivityV2::class)

    private const val KEY_CONTACT = "contact"

    @JvmStatic
    fun getIntent(context: Context, contact: Contact): Intent {
      return Intent(context, SharedContactDetailsActivityV2::class.java).apply {
        putExtra(KEY_CONTACT, contact)
      }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    super.onCreate(savedInstanceState, ready)

    val contact = IntentCompat.getParcelableExtra(intent, KEY_CONTACT, Contact::class.java)

    if (contact == null) {
      Log.w(TAG, "No contact supplied.")
      finish()
      return
    }

    if (savedInstanceState == null) {
      supportFragmentManager.beginTransaction()
        .replace(android.R.id.content, SharedContactDetailsFragment.create(contact))
        .commit()
    }
  }
}
