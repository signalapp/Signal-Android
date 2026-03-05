/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import org.signal.core.models.media.Media
import org.signal.core.models.media.MediaFolder

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
   * Deletes temporary blob files for the given media.
   */
  suspend fun deleteBlobs(media: List<Media>)

  /**
   * Sends the media with the given parameters.
   *
   * @return Result indicating success or containing a send result.
   */
  suspend fun send(request: SendRequest): SendResult

  /**
   * Gets the maximum video duration in microseconds based on quality and file size limits.
   *
   * @param quality The sent media quality code.
   * @param maxFileSizeBytes Maximum file size in bytes.
   * @return Maximum duration in microseconds.
   */
  fun getMaxVideoDurationUs(quality: Int, maxFileSizeBytes: Long): Long

  /**
   * Gets the maximum video file size in bytes.
   */
  fun getVideoMaxSizeBytes(): Long

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
}

/**
 * Result of media validation/filtering.
 */
data class MediaFilterResult(
  val filteredMedia: List<Media>,
  val error: MediaFilterError?
)

/**
 * Errors that can occur during media filtering.
 */
sealed interface MediaFilterError {
  data object NoItems : MediaFilterError
  data class ItemTooLarge(val media: Media) : MediaFilterError
  data class ItemInvalidType(val media: Media) : MediaFilterError
  data class TooManyItems(val max: Int) : MediaFilterError
  data class CannotMixMediaTypes(val message: String) : MediaFilterError
  data class Other(val message: String) : MediaFilterError
}

/**
 * Request parameters for sending media.
 */
data class SendRequest(
  val selectedMedia: List<Media>,
  val editorStateMap: Map<Uri, EditorState>,
  val quality: Int,
  val message: String?,
  val isViewOnce: Boolean,
  val singleRecipientId: MediaRecipientId?,
  val recipientIds: List<MediaRecipientId>,
  val scheduledTime: Long,
  val sendType: Int,
  val isStory: Boolean
)

/**
 * Result of a send operation.
 */
sealed interface SendResult {
  data object Success : SendResult
  data class Error(val message: String) : SendResult
  data class UntrustedIdentity(val recipientIds: List<Long>) : SendResult
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
