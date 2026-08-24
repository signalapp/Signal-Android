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
import org.thoughtcrime.securesms.mediasend.v3.MediaSendV3Activity
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.RemoteConfig

/**
 * Single entry point for launching the media send flow.
 */
object MediaSendLauncher {

  @JvmStatic
  fun camera(context: Context): Intent {
    return camera(context, isStory = false)
  }

  @JvmStatic
  fun camera(context: Context, isStory: Boolean): Intent {
    return v3Intent(
      context,
      MediaSendFlowActivityContract.Args(
        isCameraFirst = true,
        mode = MediaSendFlowActivityContract.Mode.ChooseAfterMediaSelection,
        isStory = isStory
      )
    )
  }

  @JvmStatic
  fun camera(
    context: Context,
    recipientId: RecipientId,
    isReply: Boolean
  ): Intent {
    return v3Intent(
      context,
      MediaSendFlowActivityContract.Args(
        isCameraFirst = true,
        mode = MediaSendFlowActivityContract.Mode.SingleRecipient,
        recipientId = recipientId.toMediaRecipientId(),
        isReply = isReply
      )
    )
  }

  fun cameraForQuickRestore(context: Context): Intent {
    return v3Intent(
      context,
      MediaSendFlowActivityContract.Args(
        isCameraFirst = true,
        mode = MediaSendFlowActivityContract.Mode.ChooseAfterMediaSelection,
        isForQuickRestore = true
      )
    )
  }

  fun addToGroupStory(context: Context, recipientId: RecipientId): Intent {
    return v3Intent(
      context,
      MediaSendFlowActivityContract.Args(
        isCameraFirst = true,
        mode = MediaSendFlowActivityContract.Mode.SingleRecipient,
        recipientId = recipientId.toMediaRecipientId(),
        isStory = true,
        isAddToGroupStoryFlow = true
      )
    )
  }

  @JvmStatic
  fun gallery(
    context: Context,
    media: List<Media>,
    recipientId: RecipientId,
    message: CharSequence?,
    isReply: Boolean
  ): Intent {
    return v3Intent(
      context,
      MediaSendFlowActivityContract.Args(
        mode = MediaSendFlowActivityContract.Mode.SingleRecipient,
        recipientId = recipientId.toMediaRecipientId(),
        initialMedia = media,
        initialMessage = message,
        isReply = isReply
      )
    )
  }

  @JvmStatic
  fun editor(
    context: Context,
    media: List<Media>,
    recipientId: RecipientId,
    message: CharSequence?
  ): Intent {
    return v3Intent(
      context,
      MediaSendFlowActivityContract.Args(
        mode = MediaSendFlowActivityContract.Mode.SingleRecipient,
        recipientId = recipientId.toMediaRecipientId(),
        initialMedia = media,
        initialMessage = message
      )
    )
  }

  @JvmStatic
  fun editor(context: Context, media: List<Media>): Intent {
    return v3Intent(
      context,
      MediaSendFlowActivityContract.Args(
        mode = MediaSendFlowActivityContract.Mode.ChooseAfterMediaSelection,
        initialMedia = media
      )
    )
  }

  @JvmStatic
  fun share(
    context: Context,
    media: List<Media>,
    recipientSearchKeys: List<ContactSearchKey.RecipientSearchKey>,
    message: CharSequence?,
    asTextStory: Boolean
  ): Intent {
    return v3Intent(
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
