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
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Hosts [ContactShareEditFragment].
 */
class ContactShareEditActivityV2 : PassphraseRequiredActivity() {

  companion object {
    private val TAG = Log.tag(ContactShareEditActivityV2::class)

    const val KEY_CONTACTS = "contacts"
    private const val KEY_SOURCE = "source"
    private const val KEY_RECIPIENT_ID = "recipient_id"

    /**
     * @param source the contact being shared, which may have no address book entry at all.
     * @param recipientId the conversation being sent to, not the contact being shared.
     */
    @JvmStatic
    fun getIntent(context: Context, source: SharedContactSource, recipientId: RecipientId): Intent {
      return Intent(context, ContactShareEditActivityV2::class.java).apply {
        putExtra(KEY_SOURCE, source)
        putExtra(KEY_RECIPIENT_ID, recipientId)
      }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    super.onCreate(savedInstanceState, ready)

    val source = IntentCompat.getParcelableExtra(intent, KEY_SOURCE, SharedContactSource::class.java)
    val recipientId = IntentCompat.getParcelableExtra(intent, KEY_RECIPIENT_ID, RecipientId::class.java)

    if (source == null || recipientId == null) {
      Log.w(TAG, "Nothing to share was supplied.")
      finish()
      return
    }

    if (savedInstanceState == null) {
      supportFragmentManager.beginTransaction()
        .replace(android.R.id.content, ContactShareEditFragment.create(source, recipientId))
        .commit()
    }
  }
}
