/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mediasend

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Parcelable
import org.signal.core.models.media.Media
import org.signal.core.util.getParcelableExtraCompat
import org.signal.mediasend.MediaRecipientId
import org.signal.mediasend.MediaSendFlowActivityContract
import org.signal.mediasend.MediaSendRecipient
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey
import org.thoughtcrime.securesms.conversation.MessageSendType
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.mediasend.v2.MediaSelectionActivity
import org.thoughtcrime.securesms.mediasend.v3.MediaSendV3Activity
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.RemoteConfig

/**
 * Single entry point for launching the media send flow.
 *
 * Callers describe the flow they want, and this decides whether to launch the v2 [MediaSelectionActivity] or the
 * v3 [MediaSendV3Activity], translating the request into that implementation's arguments.
 *
 * Every flow is expressible by both implementations, so the choice is purely [useV3].
 */
object MediaSendLauncher {

  private val useV3: Boolean
    get() = SignalStore.internal.useNewMediaActivity

  @JvmStatic
  fun camera(context: Context): Intent {
    return camera(context, isStory = false)
  }

  @JvmStatic
  fun camera(context: Context, isStory: Boolean): Intent {
    return if (useV3) {
      v3Intent(
        context,
        MediaSendFlowActivityContract.Args(
          isCameraFirst = true,
          mode = MediaSendFlowActivityContract.Mode.ChooseAfterMediaSelection,
          isStory = isStory
        )
      )
    } else {
      MediaSelectionActivity.camera(context, isStory)
    }
  }

  @JvmStatic
  fun camera(
    context: Context,
    messageSendType: MessageSendType,
    recipientId: RecipientId,
    isReply: Boolean
  ): Intent {
    return if (useV3) {
      v3Intent(
        context,
        MediaSendFlowActivityContract.Args(
          isCameraFirst = true,
          mode = MediaSendFlowActivityContract.Mode.SingleRecipient,
          recipientId = recipientId.toMediaRecipientId(),
          isReply = isReply
        )
      )
    } else {
      MediaSelectionActivity.camera(context, messageSendType, recipientId, isReply)
    }
  }

  fun cameraForQuickRestore(context: Context): Intent {
    return if (useV3) {
      v3Intent(
        context,
        MediaSendFlowActivityContract.Args(
          isCameraFirst = true,
          mode = MediaSendFlowActivityContract.Mode.ChooseAfterMediaSelection,
          isForQuickRestore = true
        )
      )
    } else {
      MediaSelectionActivity.cameraForQuickRestore(context)
    }
  }

  fun addToGroupStory(context: Context, recipientId: RecipientId): Intent {
    return if (useV3) {
      v3Intent(
        context,
        MediaSendFlowActivityContract.Args(
          isCameraFirst = true,
          mode = MediaSendFlowActivityContract.Mode.SingleRecipient,
          recipientId = recipientId.toMediaRecipientId(),
          isStory = true,
          isAddToGroupStoryFlow = true
        )
      )
    } else {
      MediaSelectionActivity.addToGroupStory(context, recipientId)
    }
  }

  @JvmStatic
  fun gallery(
    context: Context,
    messageSendType: MessageSendType,
    media: List<Media>,
    recipientId: RecipientId,
    message: CharSequence?,
    isReply: Boolean
  ): Intent {
    return if (useV3) {
      v3Intent(
        context,
        MediaSendFlowActivityContract.Args(
          mode = MediaSendFlowActivityContract.Mode.SingleRecipient,
          recipientId = recipientId.toMediaRecipientId(),
          initialMedia = media,
          initialMessage = message,
          isReply = isReply
        )
      )
    } else {
      MediaSelectionActivity.gallery(context, messageSendType, media, recipientId, message, isReply)
    }
  }

  @JvmStatic
  fun editor(
    context: Context,
    messageSendType: MessageSendType,
    media: List<Media>,
    recipientId: RecipientId,
    message: CharSequence?
  ): Intent {
    return if (useV3) {
      v3Intent(
        context,
        MediaSendFlowActivityContract.Args(
          mode = MediaSendFlowActivityContract.Mode.SingleRecipient,
          recipientId = recipientId.toMediaRecipientId(),
          initialMedia = media,
          initialMessage = message
        )
      )
    } else {
      MediaSelectionActivity.editor(context, messageSendType, media, recipientId, message)
    }
  }

  @JvmStatic
  fun editor(context: Context, media: List<Media>): Intent {
    return if (useV3) {
      v3Intent(
        context,
        MediaSendFlowActivityContract.Args(
          mode = MediaSendFlowActivityContract.Mode.ChooseAfterMediaSelection,
          initialMedia = media
        )
      )
    } else {
      MediaSelectionActivity.editor(context, media)
    }
  }

  @JvmStatic
  fun share(
    context: Context,
    messageSendType: MessageSendType,
    media: List<Media>,
    recipientSearchKeys: List<ContactSearchKey.RecipientSearchKey>,
    message: CharSequence?,
    asTextStory: Boolean
  ): Intent {
    return if (useV3) {
      v3Intent(
        context,
        MediaSendFlowActivityContract.Args(
          mode = MediaSendFlowActivityContract.Mode.MultiRecipient,
          additionalRecipients = recipientSearchKeys.map { MediaSendRecipient(it.recipientId.toMediaRecipientId(), it.isStory) },
          initialMedia = media,
          initialMessage = message,
          isStory = recipientSearchKeys.any { it.isStory },
          asTextStory = asTextStory
        )
      )
    } else {
      MediaSelectionActivity.share(context, messageSendType, media, recipientSearchKeys, message, asTextStory)
    }
  }

  /**
   * Reads the payload the caller is expected to send itself, for either implementation.
   *
   * Returns null when the flow performed the send on its own, as it does whenever the destinations were chosen
   * inside the flow.
   */
  @JvmStatic
  fun parseResult(resultCode: Int, data: Intent?): MediaSendActivityResult? {
    if (resultCode != Activity.RESULT_OK || data == null) {
      return null
    }

    return data.getParcelableExtraCompat(MediaSendActivityResult.EXTRA_RESULT, Parcelable::class.java) as? MediaSendActivityResult
  }

  private fun v3Intent(context: Context, args: MediaSendFlowActivityContract.Args): Intent {
    return Intent(context, MediaSendV3Activity::class.java)
      .putExtra(MediaSendFlowActivityContract.EXTRA_ARGS, args.copy(maxSelection = RemoteConfig.maxAttachmentCount))
  }

  private fun RecipientId.toMediaRecipientId(): MediaRecipientId = MediaRecipientId(toLong())
}
