/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.content.IntentCompat
import androidx.fragment.app.Fragment
import org.thoughtcrime.securesms.contactshare.Contact
import org.thoughtcrime.securesms.contactshare.ContactShareEditActivity
import org.thoughtcrime.securesms.conversation.MessageSendType
import org.thoughtcrime.securesms.conversation.colors.ChatColors
import org.thoughtcrime.securesms.mediasend.Media
import org.thoughtcrime.securesms.mediasend.MediaSendActivityResult
import org.thoughtcrime.securesms.mediasend.v2.MediaSelectionActivity
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * This encapsulates the logic for interacting with other activities used throughout a conversation. The gist
 * is to use this to launch the various activities and use the [Callbacks] to provide strongly-typed results.
 * It is intended to replace the need for [android.app.Activity.onActivityResult].
 *
 * Note, not all activity results will live here but this should handle most of the basic cases. More advance
 * usages like [AddToContactsContract] can be split out into their own [ActivityResultContract] implementations.
 */
class ConversationActivityResultContracts(fragment: Fragment, private val callbacks: Callbacks) {

  private val contactShareLauncher = fragment.registerForActivityResult(ContactShareEditor) { contacts -> callbacks.onSendContacts(contacts) }
  private val mediaSelectionLauncher = fragment.registerForActivityResult(MediaSelection) { result -> callbacks.onMediaSend(result) }

  fun launchContactShareEditor(uri: Uri, chatColors: ChatColors) {
    contactShareLauncher.launch(uri to chatColors)
  }

  fun launchMediaEditor(mediaList: List<Media>, recipientId: RecipientId, text: CharSequence?) {
    mediaSelectionLauncher.launch(MediaSelectionInput(mediaList, recipientId, text))
  }

  private object MediaSelection : ActivityResultContract<MediaSelectionInput, MediaSendActivityResult>() {
    override fun createIntent(context: Context, input: MediaSelectionInput): Intent {
      val (media, recipientId, text) = input
      return MediaSelectionActivity.editor(context, MessageSendType.SignalMessageSendType, media, recipientId, text)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): MediaSendActivityResult {
      return MediaSendActivityResult.fromData(intent!!)
    }
  }

  private object ContactShareEditor : ActivityResultContract<Pair<Uri, ChatColors>, List<Contact>>() {
    override fun createIntent(context: Context, input: Pair<Uri, ChatColors>): Intent {
      val (uri, chatColors) = input
      return ContactShareEditActivity.getIntent(context, listOf(uri), chatColors.asSingleColor())
    }

    override fun parseResult(resultCode: Int, intent: Intent?): List<Contact> {
      return intent?.let { IntentCompat.getParcelableArrayListExtra(intent, ContactShareEditActivity.KEY_CONTACTS, Contact::class.java) } ?: emptyList()
    }
  }

  private data class MediaSelectionInput(val media: List<Media>, val recipientId: RecipientId, val text: CharSequence?)

  interface Callbacks {
    fun onSendContacts(contacts: List<Contact>)
    fun onMediaSend(result: MediaSendActivityResult)
  }
}
