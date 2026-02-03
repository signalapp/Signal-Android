/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Parcelable
import androidx.activity.result.contract.ActivityResultContract
import kotlinx.parcelize.Parcelize
import org.signal.core.models.media.Media

/**
 * Well-defined entry/exit contract for the media sending flow.
 *
 * This intentionally supports a "multi-tier" outcome:
 * - Return a payload to be sent by the caller (single-recipient / conversation-owned send pipeline).
 * - Or send immediately in the flow and return an acknowledgement (multi-recipient / broadcast paths).
 *
 * Since [MediaSendActivity] is abstract, app-layer implementations should either:
 * 1. Extend this contract and override [createIntent] to use their concrete activity class
 * 2. Use the constructor that takes an activity class parameter
 *
 * Example:
 * ```kotlin
 * class MyMediaSendContract : MediaSendActivityContract(MyMediaSendActivity::class.java)
 * ```
 */
open class MediaSendActivityContract(
  private val activityClass: Class<out MediaSendActivity>? = null
) : ActivityResultContract<MediaSendActivityContract.Args, MediaSendActivityContract.Result?>() {

  /**
   * Creates the intent to launch the media send activity.
   *
   * Subclasses should override this if not using the constructor parameter.
   */
  override fun createIntent(context: Context, input: Args): Intent {
    val clazz = activityClass
      ?: throw IllegalStateException(
        "MediaSendActivityContract requires either a concrete activity class in the constructor " +
          "or an overridden createIntent() method. MediaSendActivity is abstract and cannot be launched directly."
      )
    return MediaSendActivity.createIntent(context, clazz, input)
  }

  override fun parseResult(resultCode: Int, intent: Intent?): Result? {
    if (resultCode != Activity.RESULT_OK || intent == null) return null
    return intent.getParcelableExtra(EXTRA_RESULT)
  }

  @Parcelize
  data class Args(
    val isCameraFirst: Boolean = false,
    /**
     * Optional recipient identifier for single-recipient flows.
     */
    val recipientId: MediaRecipientId? = null,
    val mode: Mode = Mode.SingleRecipient,
    /**
     * Initial media to populate the selection.
     * For gallery/editor flows, this is the pre-selected media.
     * For camera-first flows, this is typically empty.
     */
    val initialMedia: List<Media> = emptyList(),
    /**
     * Initial message/caption text.
     */
    val initialMessage: String? = null,
    /**
     * Whether this is a reply flow (affects UI/constraints).
     */
    val isReply: Boolean = false,
    /**
     * Whether this is a story send flow.
     */
    val isStory: Boolean = false,
    /**
     * Whether this is specifically the "add to group story" flow.
     */
    val isAddToGroupStoryFlow: Boolean = false,
    /**
     * Maximum number of media items that can be selected.
     */
    val maxSelection: Int = 32,
    /**
     * Send type identifier (app-layer enum ordinal).
     */
    val sendType: Int = 0
  ) : Parcelable {
    companion object {
      fun fromIntent(intent: Intent): Args {
        return intent.getParcelableExtra(EXTRA_ARGS) ?: Args()
      }
    }
  }

  /**
   * High-level mode of operation for the flow.
   *
   * Note: We keep this free of app/database types. Callers can wrap their own identifiers as needed.
   */
  sealed interface Mode : Parcelable {
    /** Single known recipient — returns result for caller to send. */
    @Parcelize
    data object SingleRecipient : Mode

    /** Multiple known recipients — sends immediately, returns [Result.Sent]. */
    @Parcelize
    data object MultiRecipient : Mode

    /** User will select contacts during the flow — sends immediately, returns [Result.Sent]. */
    @Parcelize
    data object ChooseAfterMediaSelection : Mode
  }

  /**
   * Result returned when the flow completes successfully.
   *
   * - [ReadyToSend] mirrors the historical "return a result to Conversation to send" pattern.
   * - [Sent] mirrors the historical "broadcast paths send immediately" behavior.
   */
  sealed interface Result : Parcelable {

    /**
     * Caller should send via its canonical pipeline (e.g., conversation-owned send path).
     */
    @Parcelize
    data class ReadyToSend(
      val recipientId: MediaRecipientId,
      val body: String,
      val isViewOnce: Boolean,
      val scheduledTime: Long = -1,
      val payload: Payload
    ) : Result

    /**
     * The flow already performed the send (e.g., multi-recipient broadcast), so there is no payload.
     */
    @Parcelize
    data object Sent : Result
  }

  sealed interface Payload : Parcelable {
    /**
     * Pre-upload handles + dependencies. This is intentionally "handle-only" so the feature module
     * does not need to understand DB details; the app layer can interpret these primitives.
     */
    @Parcelize
    data class PreUploaded(
      val items: List<PreUploadHandle>
    ) : Payload

    /**
     * Local media that the caller should upload/send as part of the normal pipeline.
     */
    @Parcelize
    data class LocalMedia(
      val items: List<SelectedMedia>
    ) : Payload
  }

  /**
   * Mirrors the minimum data the legacy send pipeline needs for pre-upload sends:
   * - an attachment row identifier
   * - job dependency ids that the send jobs should wait on
   *
   * The feature module does not create or interpret these; it only transports them.
   */
  @Parcelize
  data class PreUploadHandle(
    val attachmentId: Long,
    val jobIds: List<String>,
    val mediaUri: Uri
  ) : Parcelable

  /**
   * Feature-level representation of selected media when the caller is responsible for upload/send.
   * This intentionally avoids coupling to app-layer `Media` / `TransformProperties`.
   */
  @Parcelize
  data class SelectedMedia(
    val uri: Uri,
    val contentType: String?,
    val width: Int = 0,
    val height: Int = 0,
    val size: Long = 0,
    val duration: Long = 0,
    val isBorderless: Boolean = false,
    val isVideoGif: Boolean = false,
    val caption: String? = null,
    val fileName: String? = null,
    val transform: Transform? = null
  ) : Parcelable

  /**
   * Feature-level transform model for media edits/quality.
   * Kept Parcelable for the Activity result boundary; a pure JVM equivalent can live in `core-models`.
   */
  @Parcelize
  data class Transform(
    val skipTransform: Boolean = false,
    val videoTrim: Boolean = false,
    val videoTrimStartTimeUs: Long = 0,
    val videoTrimEndTimeUs: Long = 0,
    val sentMediaQuality: Int = 0,
    val mp4FastStart: Boolean = false
  ) : Parcelable

  companion object {
    const val EXTRA_ARGS = "org.signal.mediasend.args"
    const val EXTRA_RESULT = "result"

    fun toResultIntent(result: Result): Intent {
      return Intent().apply {
        putExtra(EXTRA_RESULT, result)
      }
    }
  }
}
