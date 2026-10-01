/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contacts.link

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.contacts.sync.ContactDiscovery
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.profiles.AvatarHelper
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import java.io.IOException

class LinkPhoneContactViewModel(private val recipientId: RecipientId) : ViewModel() {

  companion object {
    private val TAG = Log.tag(LinkPhoneContactViewModel::class)

    /** Photos larger than this are left out of a new contact, since the intent carrying them has a size limit. */
    private const val MAX_PHOTO_BYTES = 512 * 1024
  }

  private val internalState = MutableStateFlow(LinkPhoneContactState())
  val state: StateFlow<LinkPhoneContactState> = internalState.asStateFlow()

  init {
    viewModelScope.launch {
      val recipient = withContext(Dispatchers.IO) { Recipient.resolved(recipientId) }
      internalState.update { it.copy(recipient = recipient) }
    }
  }

  /** Links the recipient to the contact at [contactUri], as returned by a contact picker or editor. */
  fun link(contactUri: Uri) {
    viewModelScope.launch {
      internalState.update { it.copy(isLinking = true) }

      val linked = withContext(Dispatchers.IO) {
        ContactDiscovery.linkSystemContact(AppDependencies.application, recipientId, contactUri)
      }

      internalState.update { it.copy(isLinking = false, result = if (linked) Result.LINKED else Result.FAILED) }
    }
  }

  fun linkFailed() {
    internalState.update { it.copy(result = Result.FAILED) }
  }

  fun permissionDenied() {
    internalState.update { it.copy(result = Result.NO_PERMISSION) }
  }

  fun clearResult() {
    internalState.update { it.copy(result = null) }
  }

  /** An intent that opens the contacts app on a new contact prefilled with what Signal shows for the recipient. */
  suspend fun createContactIntent(): Intent {
    return withContext(Dispatchers.IO) {
      val recipient = Recipient.resolved(recipientId)

      Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI).apply {
        putExtra(ContactsContract.Intents.Insert.NAME, recipient.getDisplayName(AppDependencies.application))
        putExtra("finishActivityOnSaveCompleted", true)

        if (recipient.shouldShowE164) {
          putExtra(ContactsContract.Intents.Insert.PHONE, recipient.requireE164())
        }

        val photo = readPhoto()
        if (photo != null) {
          val photoRow = ContentValues().apply {
            put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
            put(ContactsContract.CommonDataKinds.Photo.PHOTO, photo)
          }
          putParcelableArrayListExtra(ContactsContract.Intents.Insert.DATA, arrayListOf(photoRow))
        }
      }
    }
  }

  private fun readPhoto(): ByteArray? {
    val context = AppDependencies.application
    if (!AvatarHelper.hasAvatar(context, recipientId) || AvatarHelper.getAvatarLength(context, recipientId) > MAX_PHOTO_BYTES) {
      return null
    }

    return try {
      AvatarHelper.getAvatarBytes(context, recipientId)
    } catch (e: IOException) {
      Log.w(TAG, "Could not read the avatar for a new contact.", e)
      null
    }
  }

  enum class Result {
    LINKED,
    FAILED,
    NO_PERMISSION
  }
}

data class LinkPhoneContactState(
  val recipient: Recipient? = null,
  val isLinking: Boolean = false,
  val result: LinkPhoneContactViewModel.Result? = null
)
