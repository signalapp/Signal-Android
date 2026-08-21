/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mediasend.v3

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.rx3.asFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.signal.core.models.database.AttachmentId
import org.signal.core.models.media.Media
import org.signal.core.models.media.MediaFolder
import org.signal.core.util.asListContains
import org.signal.core.util.logging.Log
import org.signal.core.util.optionalLong
import org.signal.core.util.optionalString
import org.signal.core.util.orNull
import org.signal.imageeditor.core.model.EditorModel
import org.signal.mediasend.DocumentInfo
import org.signal.mediasend.EditorState
import org.signal.mediasend.MediaConstraints
import org.signal.mediasend.MediaFilterError
import org.signal.mediasend.MediaFilterResult
import org.signal.mediasend.MediaRecipientId
import org.signal.mediasend.MediaSendRecipient
import org.signal.mediasend.MediaSendRepository
import org.signal.mediasend.MediaValidator
import org.signal.mediasend.SaveToStorageResult
import org.signal.mediasend.SendRequest
import org.signal.mediasend.SendResult
import org.signal.mediasend.SentMediaQuality
import org.signal.mediasend.StorySendRequirements
import org.signal.mediasend.preupload.PreUploadResult
import org.signal.mediasend.screens.edit.image.BrushWidths
import org.thoughtcrime.securesms.components.mention.MentionAnnotation
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey
import org.thoughtcrime.securesms.conversation.MessageSendType
import org.thoughtcrime.securesms.conversation.MessageStyler
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.IdentityRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.mediasend.MediaRepository
import org.thoughtcrime.securesms.mediasend.v2.MediaSelectionRepository
import org.thoughtcrime.securesms.mms.PartAuthority
import org.thoughtcrime.securesms.mms.PushMediaConstraints
import org.thoughtcrime.securesms.mms.TranscodingConfigProvider
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.scribbles.ImageEditorFragment
import org.thoughtcrime.securesms.sms.MessageSender
import org.thoughtcrime.securesms.stories.Stories
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.RemoteConfig
import org.thoughtcrime.securesms.util.SaveAttachmentUtil
import org.thoughtcrime.securesms.util.SaveAttachmentUtil.SaveAttachmentsResult
import org.thoughtcrime.securesms.video.TranscodingConfig
import java.io.IOException
import java.io.InputStream
import java.util.Optional
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.math.roundToInt
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

  override var sentMediaQuality: SentMediaQuality
    get() = SignalStore.settings.sentMediaQuality
    set(value) {
      SignalStore.settings.sentMediaQuality = value
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
    val result = MediaValidator.filterMedia(populated, constraints, maxSelection, isStory) {
      Stories.MediaTransform.getSendRequirements(it) != Stories.MediaTransform.SendRequirements.CAN_NOT_SEND
    }

    MediaFilterResult(result.filteredMedia, mapFilterError(result.filterError, maxSelection))
  }

  override suspend fun getDocumentInfo(media: Media): DocumentInfo? = withContext(Dispatchers.IO) {
    val uri = media.uri

    val fileInfo: Pair<String?, Long>? = try {
      if (PartAuthority.isLocalUri(uri)) {
        readLocalFileInfo(uri)
      } else {
        readContentResolverFileInfo(uri) ?: readLocalFileInfo(uri)
      }
    } catch (e: IOException) {
      Log.w(TAG, "Unable to read document info for $uri", e)
      null
    }

    if (fileInfo == null) {
      return@withContext null
    }

    val (fileName, fileSize) = fileInfo
    DocumentInfo(
      fileName = fileName,
      fileSize = fileSize,
      extension = MediaUtil.getFileType(appContext, Optional.ofNullable(fileName), uri).orElse("")
    )
  }

  @Throws(IOException::class)
  private fun readLocalFileInfo(uri: Uri): Pair<String?, Long> {
    val isLocal = PartAuthority.isLocalUri(uri)
    val fileName = if (isLocal) PartAuthority.getAttachmentFileName(appContext, uri) else null
    val fileSize = (if (isLocal) PartAuthority.getAttachmentSize(appContext, uri) else null) ?: MediaUtil.getMediaSize(appContext, uri)

    return fileName to fileSize
  }

  private fun readContentResolverFileInfo(uri: Uri): Pair<String?, Long>? {
    return appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
      if (!cursor.moveToFirst()) {
        return@use null
      }

      val fileSize = cursor.optionalLong(OpenableColumns.SIZE).orNull() ?: return@use null

      cursor.optionalString(OpenableColumns.DISPLAY_NAME).orNull() to fileSize
    }
  }

  override suspend fun deleteBlobs(media: List<Media>) {
    media
      .map(Media::uri)
      .filter { AppDependencies.blobs.isAuthority(it) }
      .forEach { AppDependencies.blobs.delete(appContext, it) }
  }

  override val hasDismissedSaveToStorageWarning: Boolean
    get() = SignalStore.uiHints.hasDismissedSaveStorageWarning()

  override fun markSaveToStorageWarningDismissed() {
    SignalStore.uiHints.markDismissedSaveStorageWarning()
  }

  override suspend fun saveImageToStorage(editorModel: EditorModel): SaveToStorageResult = withContext(Dispatchers.IO) {
    val blobUri: Uri = try {
      ImageEditorFragment.renderToSingleUseBlob(appContext, editorModel)
    } catch (exception: Exception) {
      Log.w(TAG, "Failed to render the edited image.", exception)
      return@withContext SaveToStorageResult.FAILURE
    }

    try {
      val attachment = SaveAttachmentUtil.SaveAttachment(blobUri, MediaUtil.IMAGE_JPEG, System.currentTimeMillis(), null)

      when (SaveAttachmentUtil.saveAttachments(setOf(attachment))) {
        is SaveAttachmentsResult.Success -> SaveToStorageResult.SUCCESS
        SaveAttachmentsResult.ErrorNoWriteAccess, SaveAttachmentsResult.WriteStoragePermissionDenied -> SaveToStorageResult.NO_WRITE_ACCESS
        else -> SaveToStorageResult.FAILURE
      }
    } catch (exception: Exception) {
      Log.w(TAG, "Failed to write the edited image to storage.", exception)
      SaveToStorageResult.FAILURE
    } finally {
      // The in-memory blob is only evicted on read, so a save that never got that far would hold the image until death.
      AppDependencies.blobs.delete(appContext, blobUri)
    }
  }

  override suspend fun send(request: SendRequest): SendResult = withContext(Dispatchers.IO) {
    val singleContact = buildSingleContact(request)
    val recipients = if (singleContact != null) emptyList() else buildRecipients(request)
    if (singleContact == null && recipients.isEmpty()) {
      return@withContext SendResult.Error("No recipients provided.")
    }

    val legacyEditorStateMap = mapLegacyEditorState(request.editorStateMap)

    legacyRepository.uploadRepository.setPreUploadResults(request.preUploadResults.map { it.toLegacyPreUploadResult() })

    return@withContext try {
      val result = legacyRepository.send(
        selectedMedia = request.selectedMedia,
        stateMap = legacyEditorStateMap,
        quality = request.quality,
        message = request.message,
        isViewOnce = request.isViewOnce,
        singleContact = singleContact,
        contacts = recipients,
        mentions = MentionAnnotation.getMentionsFromAnnotations(request.message),
        bodyRanges = MessageStyler.getStyling(request.message),
        sendType = resolveSendType(request.sendType),
        scheduledTime = request.scheduledTime
      ).blockingGet()

      if (result != null) SendResult.ReadyToSend(result) else SendResult.Success
    } catch (exception: Exception) {
      SendResult.Error(exception.message ?: "Failed to send media.")
    }
  }

  override fun getMaxVideoDurationUs(quality: SentMediaQuality, duration: Duration): Long {
    val config = PushMediaConstraints(quality).videoTranscodingSettings
    return TranscodingConfig.calculateMaxVideoUploadDurationInSeconds(config, duration).seconds.inWholeMicroseconds
  }

  override fun getMaxVideoRecordDurationSeconds(): Int {
    return TranscodingConfigProvider.getMaxVideoDurationSeconds()
  }

  override fun getVideoTranscodingTiers(quality: SentMediaQuality): List<TranscodingConfig.QualityTier> {
    return TranscodingConfigProvider.getConfigsForMediaQuality(quality)
  }

  override fun isVideoTranscodeAvailable(): Boolean {
    return MediaConstraints.isVideoTranscodeAvailable()
  }

  override suspend fun getStorySendRequirements(media: List<Media>): Map<Uri, StorySendRequirements> = withContext(Dispatchers.IO) {
    media.associate { it.uri to Stories.MediaTransform.getSendRequirements(it).toFeatureSendRequirements() }
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

  override fun getMediaConstraints(quality: SentMediaQuality?): MediaConstraints {
    return PushMediaConstraints(quality)
  }

  override var storyMaxVideoDuration: Duration = Stories.MAX_VIDEO_DURATION_MILLIS.milliseconds

  /**
   * Stored as whole percentages so that the v2 and v3 editors stay in sync.
   */
  override var brushWidths: BrushWidths
    get() = BrushWidths(
      marker = SignalStore.imageEditor.getMarkerPercentage() / 100f,
      highlighter = SignalStore.imageEditor.getHighlighterPercentage() / 100f,
      blur = SignalStore.imageEditor.getBlurPercentage() / 100f
    )
    set(value) {
      SignalStore.imageEditor.setMarkerPercentage((value.marker * 100).roundToInt())
      SignalStore.imageEditor.setHighlighterPercentage((value.highlighter * 100).roundToInt())
      SignalStore.imageEditor.setBlurPercentage((value.blur * 100).roundToInt())
    }

  private fun PreUploadResult.toLegacyPreUploadResult(): MessageSender.PreUploadResult {
    return MessageSender.PreUploadResult(media, AttachmentId(attachmentId), jobIds)
  }

  private fun resolveSendType(sendType: Int): MessageSendType {
    return when (sendType) {
      else -> MessageSendType.SignalMessageSendType
    }
  }

  /**
   * A known destination with nobody else in the mix means the launching caller owns the send, and the legacy
   * repository hands us back a [org.thoughtcrime.securesms.mediasend.MediaSendActivityResult] for it.
   */
  private fun buildSingleContact(request: SendRequest): ContactSearchKey.RecipientSearchKey? {
    return request.singleRecipientId
      ?.takeIf { request.recipients.isEmpty() }
      ?.let { ContactSearchKey.RecipientSearchKey(RecipientId.from(it.id), request.isStory) }
  }

  private fun buildRecipients(request: SendRequest): List<ContactSearchKey.RecipientSearchKey> {
    return buildList {
      request.singleRecipientId?.let { add(MediaSendRecipient(it, request.isStory)) }
      addAll(request.recipients)
    }.distinct().map {
      ContactSearchKey.RecipientSearchKey(RecipientId.from(it.id.id), it.isStory)
    }
  }

  private fun mapLegacyEditorState(editorStateMap: Map<Uri, EditorState>): Map<Uri, Any> {
    return editorStateMap.mapNotNull { (uri, state) ->
      val legacyState: Any? = when (state) {
        is EditorState.Image -> ImageEditorFragment.Data().apply { writeModel(state.model) }
        is EditorState.VideoTrim -> state.videoTrimData
        is EditorState.Document, EditorState.VideoGif, EditorState.Gif -> null
      }
      legacyState?.let { uri to it }
    }.toMap()
  }

  /**
   * [MediaValidator.FilterError.NoItems] wraps the reason nothing survived, and it is that reason the user needs. The
   * emptiness itself travels back as an empty [MediaFilterResult.filteredMedia].
   */
  private fun mapFilterError(error: MediaValidator.FilterError?, maxSelection: Int): MediaFilterError? {
    return when (error) {
      is MediaValidator.FilterError.NoItems -> mapFilterError(error.cause, maxSelection)
      is MediaValidator.FilterError.TooManyItems -> MediaFilterError.TooManyItems(maxSelection)
      is MediaValidator.FilterError.ItemInvalidType -> MediaFilterError.ItemInvalidType(error.media)
      is MediaValidator.FilterError.ItemTooLarge -> MediaFilterError.ItemTooLarge(error.media)
      MediaValidator.FilterError.None, null -> null
    }
  }
}
