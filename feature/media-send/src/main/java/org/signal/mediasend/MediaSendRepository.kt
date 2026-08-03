/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend

import android.content.Context
import android.net.Uri
import android.os.Parcelable
import kotlinx.coroutines.flow.Flow
import org.signal.core.models.media.Media
import org.signal.core.models.media.MediaFolder
import org.signal.imageeditor.core.model.EditorModel
import org.signal.mediasend.edit.image.BrushWidths
import org.signal.mediasend.preupload.PreUploadResult
import java.io.InputStream
import kotlin.time.Duration

/**
 * Repository interface for media send operations that require app-layer implementation.
 *
 * This allows the feature module to remain decoupled from database, recipient,
 * and constraint implementations while still supporting full functionality.
 */
interface MediaSendRepository {

  /**
   * Retrieves the top-level folders which contain media
   */
  suspend fun getFolders(): List<MediaFolder>

  /**
   * Retrieves media for a given bucketId (folder)
   */
  suspend fun getMedia(bucketId: String): List<Media>

  /**
   * Validates and filters media against constraints.
   *
   * @param media The media items to validate.
   * @param maxSelection Maximum number of items allowed.
   * @param isStory Whether this is for a story (may have different constraints).
   * @return Result containing filtered media and any validation errors.
   */
  suspend fun validateAndFilterMedia(
    media: List<Media>,
    maxSelection: Int,
    isStory: Boolean
  ): MediaFilterResult

  /**
   * Reads the display information for a document, or null if it could not be read.
   */
  suspend fun getDocumentInfo(media: Media): DocumentInfo?

  /**
   * Deletes temporary blob files for the given media.
   */
  suspend fun deleteBlobs(media: List<Media>)

  /**
   * User's sent-media-quality setting, backed by persistent storage.
   */
  var sentMediaQuality: SentMediaQuality

  /**
   * Whether the user has opted out of the warning shown before media is written to shared device storage.
   */
  val hasDismissedSaveToStorageWarning: Boolean

  fun markSaveToStorageWarningDismissed()

  /**
   * Renders [editorModel], edits included, and writes the result to the device's shared media storage.
   */
  suspend fun saveImageToStorage(editorModel: EditorModel): SaveToStorageResult

  /**
   * Sends the media with the given parameters.
   *
   * @return Result indicating success or containing a send result.
   */
  suspend fun send(request: SendRequest): SendResult

  /**
   * Gets the maximum video duration in microseconds based on quality.
   *
   * @param quality The sent media quality.
   * @param duration Duration of the video.
   * @return Maximum duration in microseconds.
   */
  fun getMaxVideoDurationUs(quality: SentMediaQuality, duration: Duration): Long

  /**
   * Gets the maximum allowed duration in seconds for in-app video recording, based on the longest
   * duration allowed by any transcoding quality tier.
   */
  fun getMaxVideoRecordDurationSeconds(): Int

  /**
   * Checks if video transcoding is available on this device.
   */
  fun isVideoTranscodeAvailable(): Boolean

  /**
   * Gets story send requirements for the given media.
   */
  suspend fun getStorySendRequirements(media: List<Media>): StorySendRequirements

  /**
   * Checks for untrusted identity records among the given contacts.
   *
   * @param contactIds Contact identifiers to check.
   * @param since Timestamp to check identity changes since.
   * @return List of contacts with bad identity records, empty if all trusted.
   */
  suspend fun checkUntrustedIdentities(
    contactIds: Set<Long>,
    since: Long
  ): List<Long>

  /**
   * Provides a flow of recipient "exists" state for determining pre-upload eligibility.
   * Emits true if the recipient is valid and can receive pre-uploads.
   *
   * @param recipientId The recipient to observe.
   * @return Flow that emits whenever recipient validity changes.
   */
  fun observeRecipientValid(recipientId: MediaRecipientId): Flow<Boolean>

  fun getAttachmentStream(context: Context, uri: Uri): InputStream

  fun isMixedModeAvailable(): Boolean

  var isCameraFacingFront: Boolean

  fun getMediaConstraints(): MediaConstraints

  var storyMaxVideoDuration: Duration

  /**
   * The image editor's per-tool brush widths, shared with the v2 editor.
   */
  var brushWidths: BrushWidths
}

/**
 * Result of media validation/filtering.
 */
data class MediaFilterResult(
  val filteredMedia: List<Media>,
  val error: MediaFilterError?
)

/**
 * Everything needed to describe a document to the user.
 *
 * @param fileName The document's name, or null if the platform did not give us one.
 * @param fileSize The document's size in bytes.
 * @param extension The document's file type, e.g. "pdf". Empty when it could not be determined.
 */
data class DocumentInfo(
  val fileName: String?,
  val fileSize: Long,
  val extension: String
)

/**
 * Outcome of writing media out to the device's shared storage.
 */
enum class SaveToStorageResult {
  SUCCESS,
  FAILURE,
  NO_WRITE_ACCESS
}

/**
 * Reasons media handed to [MediaSendRepository.validateAndFilterMedia] did not survive filtering.
 *
 * There is deliberately no "nothing selected" case: an empty selection is a fact the caller already has from
 * [MediaFilterResult.filteredMedia], while this type only answers why something was dropped.
 *
 * @property media The first item that was rejected, or null when the filtering could not pin one down.
 */
sealed interface MediaFilterError {
  data class ItemTooLarge(val media: Media?) : MediaFilterError
  data class ItemInvalidType(val media: Media?) : MediaFilterError
  data class TooManyItems(val max: Int) : MediaFilterError
}

/**
 * Request parameters for sending media.
 */
data class SendRequest(
  val selectedMedia: List<Media>,
  val editorStateMap: Map<Uri, EditorState>,
  val quality: SentMediaQuality,
  val message: CharSequence?,
  val isViewOnce: Boolean,
  val singleRecipientId: MediaRecipientId?,
  val recipients: List<MediaSendRecipient>,
  val scheduledTime: Long,
  val sendType: Int,
  val isStory: Boolean,
  /**
   * Media already pre-uploaded by the flow, so the send does not have to upload it again.
   */
  val preUploadResults: List<PreUploadResult> = emptyList()
)

/**
 * Result of a send operation.
 */
sealed interface SendResult {
  data object Success : SendResult
  data class Error(val message: String) : SendResult
  data class UntrustedIdentity(val recipientIds: List<Long>) : SendResult

  /**
   * Nothing was sent: the caller that launched the flow owns the send and is handed [payload], which is
   * opaque to this module.
   */
  data class ReadyToSend(val payload: Parcelable) : SendResult
}

/**
 * Story send requirements based on media content.
 */
enum class StorySendRequirements {
  /** Can send to stories. */
  CAN_SEND,

  /** Cannot send to stories. */
  CAN_NOT_SEND,

  /** Requires cropping before sending to stories. */
  REQUIRES_CROP
}
