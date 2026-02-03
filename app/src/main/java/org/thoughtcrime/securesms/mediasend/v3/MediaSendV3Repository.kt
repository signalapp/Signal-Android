/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mediasend.v3

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.rx3.asFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.signal.core.models.media.Media
import org.signal.core.models.media.MediaFolder
import org.signal.mediasend.EditorState
import org.signal.mediasend.MediaFilterError
import org.signal.mediasend.MediaFilterResult
import org.signal.mediasend.MediaRecipientId
import org.signal.mediasend.MediaSendRepository
import org.signal.mediasend.SendRequest
import org.signal.mediasend.SendResult
import org.signal.mediasend.StorySendRequirements
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey
import org.thoughtcrime.securesms.conversation.MessageSendType
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.IdentityRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.mediasend.MediaRepository
import org.thoughtcrime.securesms.mediasend.v2.MediaSelectionRepository
import org.thoughtcrime.securesms.mediasend.v2.MediaValidator
import org.thoughtcrime.securesms.mediasend.v2.videos.VideoTrimData
import org.thoughtcrime.securesms.mms.MediaConstraints
import org.thoughtcrime.securesms.mms.SentMediaQuality
import org.thoughtcrime.securesms.providers.BlobProvider
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.scribbles.ImageEditorFragment
import org.thoughtcrime.securesms.stories.Stories
import org.thoughtcrime.securesms.util.MediaUtil
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds

/**
 * App-layer implementation of [MediaSendRepository] that bridges to legacy v2 infrastructure.
 */
class MediaSendV3Repository(
  context: Context
) : MediaSendRepository {

  private val appContext = context.applicationContext
  private val legacyRepository = MediaSelectionRepository(appContext)
  private val mediaRepository = MediaRepository()

  override suspend fun getFolders(): List<MediaFolder> = suspendCancellableCoroutine { continuation ->
    mediaRepository.getFolders(appContext) { folders ->
      continuation.resume(folders)
    }
  }

  override suspend fun getMedia(bucketId: String): List<Media> = suspendCancellableCoroutine { continuation ->
    mediaRepository.getMediaInBucket(appContext, bucketId) { media ->
      continuation.resume(media)
    }
  }

  override suspend fun validateAndFilterMedia(
    media: List<Media>,
    maxSelection: Int,
    isStory: Boolean
  ): MediaFilterResult = withContext(Dispatchers.IO) {
    val populated = MediaRepository().getPopulatedMedia(appContext, media)
    val constraints = MediaConstraints.getPushMediaConstraints()
    val result = MediaValidator.filterMedia(appContext, populated, constraints, maxSelection, isStory)

    val error = mapFilterError(result.filterError, populated, constraints, maxSelection, isStory)
    MediaFilterResult(result.filteredMedia, error)
  }

  override suspend fun deleteBlobs(media: List<Media>) {
    media
      .map(Media::uri)
      .filter(BlobProvider::isAuthority)
      .forEach { BlobProvider.getInstance().delete(appContext, it) }
  }

  override suspend fun send(request: SendRequest): SendResult = withContext(Dispatchers.IO) {
    val recipients = buildRecipients(request)
    if (recipients.isEmpty()) {
      return@withContext SendResult.Error("No recipients provided.")
    }

    val singleContact = if (recipients.size == 1) recipients.first() else null
    val contacts = if (recipients.size > 1) recipients else emptyList()

    val legacyEditorStateMap = mapLegacyEditorState(request.editorStateMap)
    val quality = SentMediaQuality.fromCode(request.quality)

    return@withContext try {
      legacyRepository.send(
        selectedMedia = request.selectedMedia,
        stateMap = legacyEditorStateMap,
        quality = quality,
        message = request.message,
        isViewOnce = request.isViewOnce,
        singleContact = singleContact,
        contacts = contacts,
        mentions = emptyList(),
        bodyRanges = null,
        sendType = resolveSendType(request.sendType),
        scheduledTime = request.scheduledTime
      ).blockingGet()
      SendResult.Success
    } catch (exception: Exception) {
      SendResult.Error(exception.message ?: "Failed to send media.")
    }
  }

  override fun getMaxVideoDurationUs(quality: Int, maxFileSizeBytes: Long): Long {
    val preset = MediaConstraints.getPushMediaConstraints(SentMediaQuality.fromCode(quality)).videoTranscodingSettings
    return preset.calculateMaxVideoUploadDurationInSeconds(maxFileSizeBytes).seconds.inWholeMicroseconds
  }

  override fun getVideoMaxSizeBytes(): Long {
    return MediaConstraints.getPushMediaConstraints().videoMaxSize
  }

  override fun isVideoTranscodeAvailable(): Boolean {
    return MediaConstraints.isVideoTranscodeAvailable()
  }

  override suspend fun getStorySendRequirements(media: List<Media>): StorySendRequirements = withContext(Dispatchers.IO) {
    when (Stories.MediaTransform.getSendRequirements(media)) {
      Stories.MediaTransform.SendRequirements.VALID_DURATION -> StorySendRequirements.CAN_SEND
      Stories.MediaTransform.SendRequirements.REQUIRES_CLIP -> StorySendRequirements.REQUIRES_CROP
      Stories.MediaTransform.SendRequirements.CAN_NOT_SEND -> StorySendRequirements.CAN_NOT_SEND
    }
  }

  override suspend fun checkUntrustedIdentities(contactIds: Set<Long>, since: Long): List<Long> = withContext(Dispatchers.IO) {
    if (contactIds.isEmpty()) return@withContext emptyList<Long>()

    val recipients: List<Recipient> = contactIds
      .map { Recipient.resolved(RecipientId.from(it)) }
      .map { recipient ->
        when {
          recipient.isGroup -> Recipient.resolvedList(recipient.participantIds)
          recipient.isDistributionList -> Recipient.resolvedList(SignalDatabase.distributionLists.getMembers(recipient.distributionListId.get()))
          else -> listOf(recipient)
        }
      }
      .flatten()

    val calculatedWindow = System.currentTimeMillis() - since
    val identityRecords = AppDependencies
      .protocolStore
      .aci()
      .identities()
      .getIdentityRecords(recipients)

    val untrusted = identityRecords.getUntrustedRecords(
      calculatedWindow.coerceIn(TimeUnit.SECONDS.toMillis(5)..TimeUnit.HOURS.toMillis(1))
    )

    (untrusted + identityRecords.unverifiedRecords)
      .distinctBy(IdentityRecord::recipientId)
      .map { it.recipientId.toLong() }
  }

  override fun observeRecipientValid(recipientId: MediaRecipientId): Flow<Boolean> {
    return Recipient.observable(RecipientId.from(recipientId.id))
      .asFlow()
      .map { recipient ->
        recipient.isGroup || recipient.isDistributionList || recipient.isRegistered
      }
      .distinctUntilChanged()
  }

  private fun resolveSendType(sendType: Int): MessageSendType {
    return when (sendType) {
      else -> MessageSendType.SignalMessageSendType
    }
  }

  private fun buildRecipients(request: SendRequest): List<ContactSearchKey.RecipientSearchKey> {
    return buildList {
      request.singleRecipientId?.let { add(it) }
      addAll(request.recipientIds)
    }.distinctBy(MediaRecipientId::id).map {
      ContactSearchKey.RecipientSearchKey(RecipientId.from(it.id), request.isStory)
    }
  }

  private fun mapLegacyEditorState(editorStateMap: Map<Uri, EditorState>): Map<Uri, Any> {
    return editorStateMap.mapNotNull { (uri, state) ->
      val legacyState: Any = when (state) {
        is EditorState.Image -> ImageEditorFragment.Data().apply { writeModel(state.model) }
        is EditorState.VideoTrim -> VideoTrimData(
          isDurationEdited = state.isDurationEdited,
          totalInputDurationUs = state.totalInputDurationUs,
          startTimeUs = state.startTimeUs,
          endTimeUs = state.endTimeUs
        )
      }
      uri to legacyState
    }.toMap()
  }

  private fun mapFilterError(
    error: MediaValidator.FilterError?,
    media: List<Media>,
    constraints: MediaConstraints,
    maxSelection: Int,
    isStory: Boolean
  ): MediaFilterError? {
    return when (error) {
      is MediaValidator.FilterError.NoItems -> MediaFilterError.NoItems
      is MediaValidator.FilterError.TooManyItems -> MediaFilterError.TooManyItems(maxSelection)
      is MediaValidator.FilterError.ItemInvalidType -> {
        findFirstInvalidType(media)?.let { MediaFilterError.ItemInvalidType(it) }
          ?: MediaFilterError.Other("One or more items have an invalid type.")
      }
      is MediaValidator.FilterError.ItemTooLarge -> {
        findFirstTooLarge(media, constraints, isStory)?.let { MediaFilterError.ItemTooLarge(it) }
          ?: MediaFilterError.Other("One or more items are too large.")
      }
      MediaValidator.FilterError.None, null -> null
    }
  }

  private fun findFirstInvalidType(media: List<Media>): Media? {
    return media.firstOrNull { item ->
      val contentType = item.contentType ?: return@firstOrNull true
      !MediaUtil.isGif(contentType) &&
        !MediaUtil.isImageType(contentType) &&
        !MediaUtil.isVideoType(contentType) &&
        !MediaUtil.isDocumentType(contentType)
    }
  }

  private fun findFirstTooLarge(
    media: List<Media>,
    constraints: MediaConstraints,
    isStory: Boolean
  ): Media? {
    return media.firstOrNull { item ->
      val contentType = item.contentType ?: return@firstOrNull true
      val size = item.size

      val isTooLarge = when {
        MediaUtil.isGif(contentType) -> size > constraints.getGifMaxSize(appContext)
        MediaUtil.isVideoType(contentType) -> size > constraints.getUncompressedVideoMaxSize(appContext)
        MediaUtil.isImageType(contentType) -> size > constraints.getImageMaxSize(appContext)
        MediaUtil.isDocumentType(contentType) -> size > constraints.getDocumentMaxSize(appContext)
        else -> true
      }

      val isStoryInvalid = isStory && Stories.MediaTransform.getSendRequirements(item) == Stories.MediaTransform.SendRequirements.CAN_NOT_SEND

      isTooLarge || isStoryInvalid
    }
  }
}
