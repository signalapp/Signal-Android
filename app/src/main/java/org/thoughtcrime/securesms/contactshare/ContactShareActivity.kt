/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contactshare

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.core.content.IntentCompat
import org.signal.core.models.recipients.RecipientId
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.PassphraseRequiredActivity

/**
 * Hosts [ContactShareNavHost], returning the shared [Contact] under [KEY_CONTACTS].
 */
class ContactShareActivity : PassphraseRequiredActivity() {

  companion object {
    private val TAG = Log.tag(ContactShareActivity::class)

    const val KEY_CONTACTS = "contacts"
    private const val KEY_SOURCE = "source"
    private const val KEY_RECIPIENT_ID = "recipient_id"

    @JvmStatic
    fun createSelectContactIntent(context: Context, recipientId: RecipientId): Intent {
      return Intent(context, ContactShareActivity::class.java)
        .putExtra(KEY_RECIPIENT_ID, recipientId)
    }

    @JvmStatic
    fun createShareIntent(context: Context, source: SharedContactSource, recipientId: RecipientId): Intent {
      return createSelectContactIntent(context, recipientId)
        .putExtra(KEY_SOURCE, source)
    }
  }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    super.onCreate(savedInstanceState, ready)

    val recipientId = IntentCompat.getParcelableExtra(intent, KEY_RECIPIENT_ID, RecipientId::class.java)
    val source = IntentCompat.getParcelableExtra(intent, KEY_SOURCE, SharedContactSource::class.java)

    if (recipientId == null) {
      Log.w(TAG, "No conversation was supplied.")
      finish()
      return
    }

    val startRoute = if (source != null) ContactShareRoute.Share(source) else ContactShareRoute.SelectContact

    setContent {
      SignalTheme {
        ContactShareNavHost(
          startRoute = startRoute,
          recipientId = recipientId,
          onSend = { contact ->
            setResult(Activity.RESULT_OK, Intent().putExtra(KEY_CONTACTS, arrayListOf(contact)))
            finish()
          }
        )
      }
    }
  }
}
