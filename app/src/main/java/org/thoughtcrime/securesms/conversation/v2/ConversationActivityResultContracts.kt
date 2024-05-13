/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.content.IntentCompat
import androidx.fragment.app.Fragment
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.location.SignalPlace
import org.thoughtcrime.securesms.contactshare.Contact
import org.thoughtcrime.securesms.contactshare.ContactShareEditActivity
import org.thoughtcrime.securesms.conversation.MessageSendType
import org.thoughtcrime.securesms.conversation.colors.ChatColors
import org.thoughtcrime.securesms.conversation.v2.ConversationActivityResultContracts.Callbacks
import org.thoughtcrime.securesms.giph.ui.GiphyActivity
import org.thoughtcrime.securesms.maps.PlacePickerActivity
import org.thoughtcrime.securesms.mediasend.Media
import org.thoughtcrime.securesms.mediasend.MediaSendActivityResult
import org.thoughtcrime.securesms.mediasend.camerax.CameraXUtil
import org.thoughtcrime.securesms.mediasend.v2.MediaSelectionActivity
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * This encapsulates the logic for interacting with other activities used throughout a conversation. The gist
 * is to use this to launch the various activities and use the [Callbacks] to provide strongly-typed results.
 * It is intended to replace the need for [android.app.Activity.onActivityResult].
 *
 * Note, not all activity results will live here but this should handle most of the basic cases. More advance
 * usages like [AddToContactsContract] can be split out into their own [ActivityResultContract] implementations.
 */
class ConversationActivityResultContracts(private val fragment: Fragment, private val callbacks: Callbacks) {

  companion object {
    private val TAG = Log.tag(ConversationActivityResultContracts::class.java)
  }

  private val contactShareLauncher = fragment.registerForActivityResult(ContactShareEditor) { contacts -> callbacks.onSendContacts(contacts) }
  private val selectContactLauncher = fragment.registerForActivityResult(SelectContact) { uri -> callbacks.onContactSelect(uri) }
  private val mediaSelectionLauncher = fragment.registerForActivityResult(MediaSelection) { result -> callbacks.onMediaSend(result) }
  private val gifSearchLauncher = fragment.registerForActivityResult(GifSearch) { result -> callbacks.onMediaSend(result) }
  private val mediaGalleryLauncher = fragment.registerForActivityResult(MediaGallery) { result -> callbacks.onMediaSend(result) }
  private val selectLocationLauncher = fragment.registerForActivityResult(SelectLocation) { result -> callbacks.onLocationSelected(result?.place, result?.uri) }
  private val selectFileLauncher = fragment.registerForActivityResult(SelectFile) { result -> callbacks.onFileSelected(result) }
  private val cameraLauncher = fragment.registerForActivityResult(MediaCapture) { result -> callbacks.onMediaSend(result) }

  fun launchContactShareEditor(uri: Uri, chatColors: ChatColors) {
    contactShareLauncher.launch(uri to chatColors)
  }

  fun launchSelectContact() {
    Permissions
      .with(fragment)
      .request(Manifest.permission.READ_CONTACTS)
      .ifNecessary()
      .withPermanentDenialDialog(fragment.getString(R.string.AttachmentManager_signal_requires_contacts_permission_in_order_to_attach_contact_information))
      .onAllGranted { selectContactLauncher.launch(Unit) }
      .execute()
  }

  fun launchGallery(recipientId: RecipientId, text: CharSequence?, isReply: Boolean) {
    mediaGalleryLauncher.launch(MediaSelectionInput(emptyList(), recipientId, text, isReply))
  }

  fun launchCamera(recipientId: RecipientId, isReply: Boolean) {
    if (CameraXUtil.isSupported()) {
      cameraLauncher.launch(MediaSelectionInput(emptyList(), recipientId, null, isReply))
      fragment.requireActivity().overridePendingTransition(R.anim.camera_slide_from_bottom, R.anim.stationary)
    } else {
      Permissions.with(fragment)
        .request(Manifest.permission.CAMERA)
        .ifNecessary()
        .withRationaleDialog(fragment.getString(R.string.CameraXFragment_allow_access_camera), fragment.getString(R.string.CameraXFragment_to_capture_photos_and_video_allow_camera), R.drawable.symbol_camera_24)
        .withPermanentDenialDialog(fragment.getString(R.string.CameraXFragment_signal_needs_camera_access_capture_photos), null, R.string.CameraXFragment_allow_access_camera, R.string.CameraXFragment_to_capture_photos_videos, fragment.parentFragmentManager)
        .onAllGranted {
          cameraLauncher.launch(MediaSelectionInput(emptyList(), recipientId, null, isReply))
          fragment.requireActivity().overridePendingTransition(R.anim.camera_slide_from_bottom, R.anim.stationary)
        }
        .onAnyDenied { Toast.makeText(fragment.requireContext(), R.string.CameraXFragment_signal_needs_camera_access_capture_photos, Toast.LENGTH_LONG).show() }
        .execute()
    }
  }

  fun launchMediaEditor(mediaList: List<Media>, recipientId: RecipientId, text: CharSequence?) {
    mediaSelectionLauncher.launch(MediaSelectionInput(mediaList, recipientId, text))
  }

  fun launchGifSearch(recipientId: RecipientId, text: CharSequence?) {
    gifSearchLauncher.launch(GifSearchInput(recipientId, text))
  }

  fun launchSelectLocation(chatColors: ChatColors) {
    if (Permissions.hasAny(fragment.requireContext(), Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) {
      selectLocationLauncher.launch(chatColors)
    } else {
      Permissions.with(fragment)
        .request(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        .ifNecessary()
        .withRationaleDialog(fragment.getString(R.string.AttachmentManager_signal_allow_access_location), fragment.getString(R.string.AttachmentManager_signal_allow_signal_access_location), R.drawable.symbol_location_white_24)
        .withPermanentDenialDialog(fragment.getString(R.string.AttachmentManager_signal_requires_location_information_in_order_to_attach_a_location), null, R.string.AttachmentManager_signal_allow_access_location, R.string.AttachmentManager_signal_to_send_location, fragment.parentFragmentManager)
        .onAnyDenied { Toast.makeText(fragment.requireContext(), R.string.AttachmentManager_signal_needs_location_access, Toast.LENGTH_LONG).show() }
        .onSomeGranted { selectLocationLauncher.launch(chatColors) }
        .execute()
    }
  }

  fun launchSelectFile(): Boolean {
    try {
      selectFileLauncher.launch(SelectFile.SelectFileMode.DOCUMENT)
      return true
    } catch (e: ActivityNotFoundException) {
      Log.w(TAG, "couldn't complete ACTION_OPEN_DOCUMENT, no activity found. falling back.")
    }

    try {
      selectFileLauncher.launch(SelectFile.SelectFileMode.CONTENT)
      return true
    } catch (e: ActivityNotFoundException) {
      Log.w(TAG, "couldn't complete ACTION_GET_CONTENT intent, no activity found. falling back.")
    }

    return false
  }

  private object MediaSelection : ActivityResultContract<MediaSelectionInput, MediaSendActivityResult?>() {
    override fun createIntent(context: Context, input: MediaSelectionInput): Intent {
      val (media, recipientId, text) = input
      return MediaSelectionActivity.editor(context, MessageSendType.SignalMessageSendType, media, recipientId, text)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): MediaSendActivityResult? {
      return if (resultCode == Activity.RESULT_OK) {
        intent?.let { MediaSendActivityResult.fromData(intent) }
      } else {
        null
      }
    }
  }

  private object MediaCapture : ActivityResultContract<MediaSelectionInput, MediaSendActivityResult?>() {
    override fun createIntent(context: Context, input: MediaSelectionInput): Intent {
      val (_, recipientId, _, isReply) = input
      return MediaSelectionActivity.camera(context, MessageSendType.SignalMessageSendType, recipientId, isReply)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): MediaSendActivityResult? {
      return if (resultCode == Activity.RESULT_OK) {
        intent?.let { MediaSendActivityResult.fromData(intent) }
      } else {
        null
      }
    }
  }

  private object MediaGallery : ActivityResultContract<MediaSelectionInput, MediaSendActivityResult?>() {
    override fun createIntent(context: Context, input: MediaSelectionInput): Intent {
      val (media, recipientId, text, isReply) = input
      return MediaSelectionActivity.gallery(context, MessageSendType.SignalMessageSendType, media, recipientId, text, isReply)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): MediaSendActivityResult? {
      return if (resultCode == Activity.RESULT_OK) {
        intent?.let { MediaSendActivityResult.fromData(intent) }
      } else {
        null
      }
    }
  }

  private object ContactShareEditor : ActivityResultContract<Pair<Uri, ChatColors>, List<Contact>>() {
    override fun createIntent(context: Context, input: Pair<Uri, ChatColors>): Intent {
      val (uri, chatColors) = input
      return ContactShareEditActivity.getIntent(context, listOf(uri), chatColors.asSingleColor())
    }

    override fun parseResult(resultCode: Int, intent: Intent?): List<Contact> {
      return if (resultCode == Activity.RESULT_OK) {
        intent?.let { IntentCompat.getParcelableArrayListExtra(intent, ContactShareEditActivity.KEY_CONTACTS, Contact::class.java) } ?: emptyList()
      } else {
        emptyList()
      }
    }
  }

  private object SelectContact : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit): Intent {
      return Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
      return if (resultCode == Activity.RESULT_OK) {
        intent?.data
      } else {
        null
      }
    }
  }

  private object GifSearch : ActivityResultContract<GifSearchInput, MediaSendActivityResult?>() {
    override fun createIntent(context: Context, input: GifSearchInput): Intent {
      return Intent(context, GiphyActivity::class.java).apply {
        putExtra(GiphyActivity.EXTRA_IS_MMS, false)
        putExtra(GiphyActivity.EXTRA_RECIPIENT_ID, input.recipientId)
        putExtra(GiphyActivity.EXTRA_TRANSPORT, MessageSendType.SignalMessageSendType)
        putExtra(GiphyActivity.EXTRA_TEXT, input.text)
      }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): MediaSendActivityResult? {
      return if (resultCode == Activity.RESULT_OK) {
        intent?.let { MediaSendActivityResult.fromData(intent) }
      } else {
        null
      }
    }
  }

  private object SelectLocation : ActivityResultContract<ChatColors, SelectLocationOutput?>() {
    override fun createIntent(context: Context, input: ChatColors): Intent {
      return Intent(context, PlacePickerActivity::class.java)
        .putExtra(PlacePickerActivity.KEY_CHAT_COLOR, input.asSingleColor())
    }

    override fun parseResult(resultCode: Int, intent: Intent?): SelectLocationOutput? {
      return if (resultCode == Activity.RESULT_OK) {
        intent?.data?.let { uri -> SelectLocationOutput(SignalPlace(PlacePickerActivity.addressFromData(intent)), uri) }
      } else {
        null
      }
    }
  }

  private object SelectFile : ActivityResultContract<SelectFile.SelectFileMode, Uri?>() {
    override fun createIntent(context: Context, input: SelectFileMode): Intent {
      return Intent().apply {
        type = "*/*"

        action = when (input) {
          SelectFileMode.DOCUMENT -> Intent.ACTION_OPEN_DOCUMENT
          SelectFileMode.CONTENT -> Intent.ACTION_GET_CONTENT
        }
      }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
      return if (resultCode == Activity.RESULT_OK) {
        intent?.data
      } else {
        null
      }
    }

    enum class SelectFileMode {
      DOCUMENT,
      CONTENT
    }
  }

  private data class MediaSelectionInput(val media: List<Media>, val recipientId: RecipientId, val text: CharSequence?, val isReply: Boolean = false)

  private data class GifSearchInput(val recipientId: RecipientId, val text: CharSequence?)

  private data class SelectLocationOutput(val place: SignalPlace, val uri: Uri)

  interface Callbacks {
    fun onSendContacts(contacts: List<Contact>)
    fun onMediaSend(result: MediaSendActivityResult?)
    fun onContactSelect(uri: Uri?)
    fun onLocationSelected(place: SignalPlace?, uri: Uri?)
    fun onFileSelected(uri: Uri?)
  }
}
