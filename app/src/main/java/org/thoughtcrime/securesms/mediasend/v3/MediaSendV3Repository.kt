/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mediasend.v3

import android.content.Context
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.rx3.asFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.signal.core.models.media.Media
import org.signal.core.models.media.MediaFolder
import org.signal.core.util.asListContains
import org.signal.core.util.logging.Log
import org.signal.mediasend.EditorState
import org.signal.mediasend.MediaConstraints
import org.signal.mediasend.MediaFilterError
import org.signal.mediasend.MediaFilterResult
import org.signal.mediasend.MediaRecipientId
import org.signal.mediasend.MediaSendRepository
import org.signal.mediasend.SendRequest
import org.signal.mediasend.SendResult
import org.signal.mediasend.SentMediaQuality
import org.signal.mediasend.StorySendRequirements
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey
import org.thoughtcrime.securesms.conversation.MessageSendType
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.IdentityRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.mediasend.MediaRepository
import org.thoughtcrime.securesms.mediasend.v2.MediaSelectionRepository
import org.thoughtcrime.securesms.mediasend.v2.MediaValidator
import org.thoughtcrime.securesms.mms.PartAuthority
import org.thoughtcrime.securesms.mms.PushMediaConstraints
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.scribbles.ImageEditorFragment
import org.thoughtcrime.securesms.stories.Stories
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.RemoteConfig
import org.thoughtcrime.securesms.video.TranscodingConfig
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * App-layer implementation of [MediaSendRepository] that bridges to legacy v2 infrastructure.
 */
object MediaSendV3Repository : MediaSendRepository {

  private val TAG = Log.tag(MediaSendV3Repository::class.java)
  private val appContext = AppDependencies.application
  private val legacyRepository = MediaSelectionRepository(appContext)
  private val mediaRepository = MediaRepository()

  override var isCameraFacingFront: Boolean
    get() = SignalStore.misc.isCameraFacingFront
    set(value) {
      SignalStore.misc.isCameraFacingFront = value
    }

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
    val constraints = PushMediaConstraints(null)
    val result = MediaValidator.filterMedia(populated, constraints, maxSelection, isStory)

    val error = mapFilterError(result.filterError, populated, constraints, maxSelection, isStory)
    MediaFilterResult(result.filteredMedia, error)
  }

  override suspend fun deleteBlobs(media: List<Media>) {
    media
      .map(Media::uri)
      .filter { AppDependencies.blobs.isAuthority(it) }
      .forEach { AppDependencies.blobs.delete(appContext, it) }
  }

  override suspend fun send(request: SendRequest): SendResult = withContext(Dispatchers.IO) {
    val recipients = buildRecipients(request)
    if (recipients.isEmpty()) {
      return@withContext SendResult.Error("No recipients provided.")
    }

    val legacyEditorStateMap = mapLegacyEditorState(request.editorStateMap)

    return@withContext try {
      legacyRepository.send(
        selectedMedia = request.selectedMedia,
        stateMap = legacyEditorStateMap,
        quality = request.quality,
        message = request.message,
        isViewOnce = request.isViewOnce,
        singleContact = null,
        contacts = recipients,
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

  override fun getMaxVideoDurationUs(quality: SentMediaQuality, maxFileSizeBytes: Long, duration: Duration): Long {
    val config = PushMediaConstraints(quality).videoTranscodingSettings
    return TranscodingConfig.calculateMaxVideoUploadDurationInSeconds(config, duration, maxFileSizeBytes).seconds.inWholeMicroseconds
  }

  override fun getVideoMaxSizeBytes(): Long {
    return PushMediaConstraints(null).getEditorVideoMaxSize()
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

  override suspend fun checkUntrustedIdentities(contactIds: Set<Long>, since: Long): List<Long> = withContext(Dispatchers.Default) {
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

  override fun getAttachmentStream(context: Context, uri: Uri): InputStream {
    return PartAuthority.getAttachmentStream(context, uri)
  }

  override fun isMixedModeAvailable(): Boolean {
    return !RemoteConfig.cameraXMixedModelBlocklist.asListContains(Build.MODEL)
  }

  override fun getMediaConstraints(): MediaConstraints {
    return PushMediaConstraints(null)
  }

  override var storyMaxVideoDuration: Duration = Stories.MAX_VIDEO_DURATION_MILLIS.milliseconds

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
        is EditorState.VideoTrim -> state.videoTrimData
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
        MediaUtil.isGif(contentType) -> size > constraints.getGifMaxSize()
        MediaUtil.isVideoType(contentType) -> size > constraints.getUncompressedVideoMaxSize()
        MediaUtil.isImageType(contentType) -> size > constraints.getImageMaxSize()
        MediaUtil.isDocumentType(contentType) -> size > constraints.getDocumentMaxSize()
        else -> true
      }

      val isStoryInvalid = isStory && Stories.MediaTransform.getSendRequirements(item) == Stories.MediaTransform.SendRequirements.CAN_NOT_SEND

      isTooLarge || isStoryInvalid
    }
  }
}
