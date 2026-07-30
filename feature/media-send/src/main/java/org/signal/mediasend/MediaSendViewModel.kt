/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend

import android.Manifest
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.serialization.saved
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import androidx.navigation3.runtime.serialization.NavKeySerializer
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.signal.core.models.media.Media
import org.signal.core.models.media.MediaFolder
import org.signal.core.ui.compose.DialogController
import org.signal.core.ui.compose.DialogResult
import org.signal.core.ui.compose.PermissionController
import org.signal.core.ui.util.StorageUtil
import org.signal.core.util.ContentTypeUtil
import org.signal.core.util.StringUtil
import org.signal.core.util.logging.Log
import org.signal.core.util.next
import org.signal.imageeditor.core.model.EditorElement
import org.signal.imageeditor.core.model.EditorModel
import org.signal.imageeditor.core.renderers.UriGlideRenderer
import org.signal.mediasend.capture.CameraXScreenEvent
import org.signal.mediasend.capture.MediaCaptureScreenEvent
import org.signal.mediasend.edit.MediaEditScreenEvent
import org.signal.mediasend.edit.image.BrushTool
import org.signal.mediasend.edit.video.VideoTrimData
import org.signal.mediasend.preupload.PreUploadController
import org.signal.mediasend.preupload.PreUploadResult
import org.signal.mediasend.select.MediaSelectScreenEvent
import org.thoughtcrime.securesms.video.videoconverter.utils.VideoConstants
import java.io.FileInputStream
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Configuration-survivable state manager for the media send flow.
 *
 * Uses [SavedStateHandle] for automatic state persistence across process death.
 * [MediaSendState] is fully [Parcelable] and saved directly as a single key.
 */
class MediaSendViewModel(
  private val savedStateHandle: SavedStateHandle,
  private val repository: MediaSendRepository,
  private val preUploadController: PreUploadController,
  isMeteredFlow: Flow<Boolean>
) : ViewModel(), MediaSendEventHandler {

  private val args: MediaSendActivityContract.Args = savedStateHandle[KEY_ARGS]
    ?: throw IllegalStateException("MediaSendViewModel requires args in SavedStateHandle. Use Factory to create.")

  private val identityChangesSince: Long = savedStateHandle[KEY_IDENTITY_CHANGES_SINCE]
    ?: throw IllegalStateException("MediaSendViewModel requires identityChangesSince in SavedStateHandle. Use Factory to create.")

  private val defaultState = MediaSendState(
    isCameraFirst = args.isCameraFirst,
    recipientId = args.recipientId,
    additionalRecipients = args.additionalRecipients,
    mode = args.mode,
    isStory = args.isStory,
    isReply = args.isReply,
    isAddToGroupStoryFlow = args.isAddToGroupStoryFlow,
    maxSelection = args.maxSelection,
    message = if (args.asTextStory) null else args.initialMessage,
    isContactSelectionRequired = args.mode == MediaSendActivityContract.Mode.ChooseAfterMediaSelection,
    sendType = args.sendType
  )

  val backStack: NavBackStack<NavKey> by savedStateHandle.saved(
    serializer = NavBackStackSerializer(NavKeySerializer()),
    key = KEY_BACK_STACK
  ) {
    val startKey = when {
      args.asTextStory -> MediaSendNavKey.Capture.TextStory
      args.isCameraFirst -> MediaSendNavKey.Capture.Camera
      args.initialMedia.isNotEmpty() -> MediaSendNavKey.Edit
      else -> MediaSendNavKey.Select.Folders
    }

    NavBackStack(startKey)
  }

  private val internalSnackbarEvents: Channel<SnackbarEvent> = Channel(Channel.BUFFERED)
  internal val snackbarEvents: Flow<SnackbarEvent> = internalSnackbarEvents.receiveAsFlow()

  internal val usernameScannedDialog = DialogController<String>()
  internal val linkedDeviceScannedDialog = DialogController<Unit>()
  internal val saveToStorageDialog = DialogController<Unit>()

  internal val writeStoragePermission = PermissionController(
    permission = Manifest.permission.WRITE_EXTERNAL_STORAGE,
    permanentDenialMessage = R.string.MediaSendViewModel__signal_needs_the_storage_permission
  )

  private val qrCheckRequest: Channel<String> = Channel(Channel.RENDEZVOUS)

  /**
   * Main UI state. Backed by [SavedStateHandle] for automatic process death survival.
   * Writes to this flow are automatically persisted.
   */
  private val internalState: MutableStateFlow<MediaSendState> = savedStateHandle.getMutableStateFlow(KEY_STATE, defaultState)
  val state: StateFlow<MediaSendState> = internalState.asStateFlow()

  private val editedVideoUris: MutableSet<Uri> = mutableSetOf<Uri>().apply {
    addAll(savedStateHandle[KEY_EDITED_VIDEO_URIS] ?: emptyList())
  }

  /** One-shot HUD commands exposed as a Flow. */
  private val hudCommandChannel = Channel<HudCommand>(Channel.BUFFERED)
  val hudCommands: Flow<HudCommand> = hudCommandChannel.receiveAsFlow()

  /** Media filter errors. */
  private val _mediaErrors = MutableSharedFlow<MediaFilterError>(replay = 1)
  val mediaErrors: SharedFlow<MediaFilterError> = _mediaErrors.asSharedFlow()

  /** Character count for the message field. */
  val messageCharacterCount: Flow<Int> = state
    .map { it.message?.let { msg -> StringUtil.getGraphemeCount(msg) } ?: 0 }
    .distinctUntilChanged()

  init {
    // Matches legacy behavior: VM subscribes to connectivity updates and derives
    // isPreUploadEnabled from metered state.
    viewModelScope.launch {
      isMeteredFlow.collect { metered ->
        updateState { copy(isMeteredConnection = metered, isPreUploadEnabled = shouldPreUpload(metered)) }
      }
    }

    viewModelScope.launch(Dispatchers.Default) {
      for (qrData in qrCheckRequest) {
        if (qrData.isEmpty()) {
          continue
        }

        val result = MediaSendDependencies.qrRepository.checkQrData(qrData)
        if (result == MediaSendQrRepository.QrCheckResult.None) {
          continue
        }

        when (result) {
          MediaSendQrRepository.QrCheckResult.LinkDevice -> {
            when (linkedDeviceScannedDialog.show(Unit)) {
              DialogResult.POSITIVE -> sendHudCommand(HudCommand.GoToLinkedDevices)
              else -> Unit
            }
          }
          MediaSendQrRepository.QrCheckResult.None -> Unit
          is MediaSendQrRepository.QrCheckResult.ReRegistration -> sendHudCommand(HudCommand.GoToQuickTransfer(qrData))
          is MediaSendQrRepository.QrCheckResult.Username -> {
            when (usernameScannedDialog.show(result.username)) {
              DialogResult.POSITIVE -> sendHudCommand(HudCommand.GoToConversation(result.recipientId))
              else -> Unit
            }
          }
        }

        delay(5.seconds)
      }
    }

    // Observe recipient validity for pre-upload eligibility
    args.recipientId?.let { recipientId ->
      viewModelScope.launch {
        repository.observeRecipientValid(recipientId).collect { isValid ->
          if (isValid) {
            updateState { copy(isPreUploadEnabled = shouldPreUpload(isMeteredConnection)) }
          }
        }
      }
    }

    // Add initial media if provided
    if (args.initialMedia.isNotEmpty()) {
      addMedia(args.initialMedia.toSet())
    }

    refreshMediaFolders()
  }

  /** Updates state atomically — automatically persisted via SavedStateHandle-backed MutableStateFlow. */
  private inline fun updateState(crossinline transform: MediaSendState.() -> MediaSendState) {
    internalState.update { it.transform() }
  }

  //region Media Selection

  fun refreshMediaFolders() {
    viewModelScope.launch {
      val folders = repository.getFolders()
      internalState.update {
        it.copy(
          mediaFolders = folders,
          selectedMediaFolder = if (it.selectedMediaFolder in folders) it.selectedMediaFolder else null,
          selectedMediaFolderItems = if (it.selectedMediaFolder in folders) it.selectedMediaFolderItems else emptyList()
        )
      }
    }
  }

  override fun onMediaSelectScreenEvent(mediaSelectScreenEvent: MediaSelectScreenEvent) {
    when (mediaSelectScreenEvent) {
      is MediaSelectScreenEvent.FolderClick -> onFolderClick(mediaSelectScreenEvent.mediaFolder)
      is MediaSelectScreenEvent.MediaClick -> onMediaClick(mediaSelectScreenEvent.media)
      is MediaSelectScreenEvent.SetFocusedMedia -> setFocusedMedia(mediaSelectScreenEvent.media)
      is MediaSelectScreenEvent.ReorderSelectedMedia -> reorderMedia(mediaSelectScreenEvent.fromIndex, mediaSelectScreenEvent.toIndex)
      MediaSelectScreenEvent.NavigateToEdit -> backStack.goToEdit()
      MediaSelectScreenEvent.NavigateToCamera -> backStack.goToCamera()
    }
  }

  override fun onMediaCaptureScreenEvent(mediaCaptureScreenEvent: MediaCaptureScreenEvent) {
    when (mediaCaptureScreenEvent) {
      MediaCaptureScreenEvent.ShowCamera -> backStack.goToCamera()
      MediaCaptureScreenEvent.ShowTextStory -> backStack.goToTextStory()
      is MediaCaptureScreenEvent.Camera -> onCameraXScreenEvent(mediaCaptureScreenEvent.event)
      MediaCaptureScreenEvent.NextClicked -> backStack.goToEdit()
      MediaCaptureScreenEvent.CycleTextStoryBackgroundColor -> error("Handled directly in the fragment.")
      MediaCaptureScreenEvent.AddLinkToTextStory -> error("Handled directly in the fragment.")
    }
  }

  private fun onCameraXScreenEvent(event: CameraXScreenEvent) {
    when (event) {
      CameraXScreenEvent.CameraCloseClicked -> sendHudCommand(HudCommand.CloseScreen)
      CameraXScreenEvent.GalleryClicked -> backStack.goToFolders()
      is CameraXScreenEvent.ImageCaptured -> handleImageCaptured(event)
      is CameraXScreenEvent.VideoCaptured -> handleVideoCaptured(event)
      is CameraXScreenEvent.QrCodeFound -> qrCheckRequest.trySend(event.data)
      CameraXScreenEvent.VideoCaptureError -> {
        internalSnackbarEvents.trySend(SnackbarEvent(message = R.string.MediaSendViewModel__error_recording_video))
      }
    }
  }

  override fun onMediaEditScreenEvent(mediaEditScreenEvent: MediaEditScreenEvent) {
    when (mediaEditScreenEvent) {
      is MediaEditScreenEvent.FocusedMediaChanged -> setFocusedMedia(mediaEditScreenEvent.media)
      is MediaEditScreenEvent.ReorderSelectedMedia -> reorderMedia(mediaEditScreenEvent.fromIndex, mediaEditScreenEvent.toIndex)
      MediaEditScreenEvent.NextClick -> {
        if (state.value.isContactSelectionRequired) {
          backStack.goToSend()
        } else {
          performSend()
        }
      }
      MediaEditScreenEvent.NavigateBack -> onPopFromEdit()
      is MediaEditScreenEvent.VideoTrimChanged -> onEditVideoDuration(
        totalDurationUs = mediaEditScreenEvent.videoTrimData.totalInputDurationUs,
        startTimeUs = mediaEditScreenEvent.videoTrimData.startTimeUs,
        endTimeUs = mediaEditScreenEvent.videoTrimData.endTimeUs,
        touchEnabled = mediaEditScreenEvent.editingComplete
      )

      is MediaEditScreenEvent.VideoSeek -> error("VideoSeek is routed to the video player bus by MediaEditScreen and must not reach the view-model.")
      is MediaEditScreenEvent.AddMessageClick -> {
        val snapshot: MediaSendState = state.value

        sendHudCommand(
          HudCommand.ShowAddAMessageDialog(
            message = snapshot.message ?: "",
            startWithEmojiKeyboard = mediaEditScreenEvent.startWithEmojiKeyboard,
            isViewOnceAvailable = snapshot.selectedMedia.size == 1 && !snapshot.isStory && !ContentTypeUtil.isDocumentType(snapshot.focusedMedia?.contentType)
          )
        )
      }

      MediaEditScreenEvent.NavigateToGallery -> {
        backStack.goToFolders()
      }

      MediaEditScreenEvent.ToggleMediaQuality -> {
        setSentMediaQuality(state.value.sentMediaQuality.next())
      }

      MediaEditScreenEvent.SaveMedia -> {
        saveFocusedMediaToStorage()
      }

      is MediaEditScreenEvent.RemoveMedia -> {
        removeMedia(mediaEditScreenEvent.media)
      }

      is MediaEditScreenEvent.BrushWidthChanged -> {
        setBrushWidth(mediaEditScreenEvent.tool, mediaEditScreenEvent.fraction)
      }
    }
  }

  private fun setBrushWidth(tool: BrushTool, fraction: Float) {
    val brushWidths = state.value.brushWidths.with(tool, fraction)

    updateState { copy(brushWidths = brushWidths) }
    repository.brushWidths = brushWidths
  }

  private fun handleImageCaptured(imageCaptured: CameraXScreenEvent.ImageCaptured) {
    viewModelScope.launch {
      val media: Media? = withContext(Dispatchers.IO) {
        try {
          val length = imageCaptured.data.size.toLong()
          val uri = MediaSendDependencies.blobs
            .forData(imageCaptured.data)
            .withMimeType(ContentTypeUtil.IMAGE_JPEG)
            .createForSingleSessionOnDisk(MediaSendDependencies.application)

          buildCapturedMedia(uri, ContentTypeUtil.IMAGE_JPEG, imageCaptured.width, imageCaptured.height, length)
        } catch (e: IOException) {
          null
        }
      }

      if (media != null) {
        onMediaRendered(media)
      } else {
        internalSnackbarEvents.trySend(SnackbarEvent(message = R.string.MediaSendViewModel__error_taking_photo))
      }
    }
  }

  private fun handleVideoCaptured(videoCaptured: CameraXScreenEvent.VideoCaptured) {
    viewModelScope.launch {
      val media: Media? = withContext(Dispatchers.IO) {
        try {
          videoCaptured.fd.use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { stream ->
              val length = stream.channel.size()
              val uri = MediaSendDependencies.blobs
                .forData(stream, length)
                .withMimeType(VideoConstants.RECORDED_VIDEO_CONTENT_TYPE)
                .createForSingleSessionOnDisk(MediaSendDependencies.application)

              buildCapturedMedia(uri, VideoConstants.RECORDED_VIDEO_CONTENT_TYPE, 0, 0, length)
            }
          }
        } catch (e: IOException) {
          null
        }
      }

      if (media != null) {
        onVideoRecorded(videoCaptured.durationMs.milliseconds)
        onMediaRendered(media)
      } else {
        internalSnackbarEvents.trySend(SnackbarEvent(message = R.string.MediaSendViewModel__error_recording_video))
      }
    }
  }

  private fun buildCapturedMedia(uri: Uri, mimeType: String, width: Int, height: Int, size: Long): Media {
    return Media(
      uri = uri,
      contentType = mimeType,
      date = System.currentTimeMillis(),
      width = width,
      height = height,
      size = size,
      duration = 0,
      isBorderless = false,
      isVideoGif = false,
      bucketId = Media.ALL_MEDIA_BUCKET_ID,
      caption = null,
      transformProperties = null,
      fileName = null
    )
  }

  private fun onMediaRendered(media: Media) {
    if (args.isCameraFirst && internalState.value.cameraFirstCapture == null) {
      addCameraFirstCapture(media)
    } else {
      addMedia(setOf(media), focusNewlyAdded = true)
    }

    backStack.goToEdit()
  }

  private fun onFolderClick(mediaFolder: MediaFolder?) {
    if (mediaFolder != null) {
      backStack.goToFiles(mediaFolder)
    }

    viewModelScope.launch {
      if (mediaFolder != null) {
        val media = repository.getMedia(mediaFolder.bucketId)
        internalState.update { it.copy(selectedMediaFolder = mediaFolder, selectedMediaFolderItems = media) }
      } else {
        internalState.update { it.copy(selectedMediaFolder = null, selectedMediaFolderItems = emptyList()) }
      }
    }
  }

  private fun onMediaClick(media: Media) {
    if (media.uri in internalState.value.selectedMedia.map { it.uri }) {
      removeMedia(media)
    } else {
      addMedia(media)
    }
  }

  private fun sendHudCommand(hudCommand: HudCommand) {
    viewModelScope.launch {
      hudCommandChannel.send(hudCommand)
    }
  }

  /**
   * Adds [media] to the selection, preserving insertion order and uniqueness by equality.
   *
   * Validates against constraints and starts pre-uploads for newly added items.
   *
   * @param media Media items to add.
   */
  fun addMedia(media: Set<Media>) {
    addMedia(media, focusNewlyAdded = false)
  }

  /**
   * Adds [media] to the selection, optionally moving focus to the newly added item.
   *
   * Focus is updated within the same atomic state write that adds the media, so [MediaSendState.focusedMedia]
   * is never left pointing at an item that is not yet present in [MediaSendState.selectedMedia].
   */
  private fun addMedia(media: Set<Media>, focusNewlyAdded: Boolean) {
    viewModelScope.launch {
      val snapshot = state.value
      val newSelectionList: List<Media> = linkedSetOf<Media>().apply {
        addAll(snapshot.selectedMedia)
        addAll(media)
      }.toList()

      // Validate and filter through repository
      val filterResult = repository.validateAndFilterMedia(
        media = newSelectionList,
        maxSelection = snapshot.maxSelection,
        isStory = snapshot.isStory
      )

      if (filterResult.filteredMedia.isNotEmpty()) {
        val initializedEditorStates: Map<Uri, EditorState> = filterResult.filteredMedia
          .filterNot { snapshot.editorStateMap.containsKey(it.uri) }
          .mapNotNull { item -> createEditorState(item)?.let { item.uri to it } }
          .toMap()

        // A document's name is only known once its info has been read, and it has to ride along on the media itself
        // because that is what the send builds the attachment's file name from.
        val updatedMedia: List<Media> = filterResult.filteredMedia.map { item ->
          val documentFileName = (initializedEditorStates[item.uri] as? EditorState.Document)?.fileName
          if (documentFileName != null) item.copy(fileName = documentFileName) else item
        }

        updateState {
          val newFocus = if (focusNewlyAdded) {
            updatedMedia.lastOrNull { item -> media.any { it.uri == item.uri } } ?: focusedMedia ?: updatedMedia.firstOrNull()
          } else {
            focusedMedia ?: updatedMedia.firstOrNull()
          }

          copy(
            selectedMedia = updatedMedia,
            focusedMedia = newFocus,
            editorStateMap = editorStateMap + initializedEditorStates,
            // Re-bind to the populated instance by URI: population fills in a video's 0x0 dimensions, producing a new
            // Media that no longer equals the pre-population capture, which would otherwise leak past equality-based
            // removal on back. Cleared once more than the capture is selected.
            cameraFirstCapture = if (updatedMedia.size > 1) {
              null
            } else {
              cameraFirstCapture?.let { capture -> updatedMedia.find { it.uri == capture.uri } ?: capture }
            }
          )
        }

        if (initializedEditorStates.values.any { it is EditorState.VideoTrim && it.videoTrimData.isDurationEdited }) {
          internalSnackbarEvents.trySend(SnackbarEvent(message = R.string.MediaSendViewModel__video_trimmed_to_fit))
        }

        // Update story requirements
        updateStorySendRequirements(updatedMedia)

        // Start pre-uploads for new media
        val newMedia = updatedMedia.filter { item -> media.any { it.uri == item.uri } }
        startUpload(newMedia)
      }

      if (filterResult.error != null) {
        _mediaErrors.emit(filterResult.error)
      }
    }
  }

  /**
   * Builds the initial editor state for a newly selected [media] item, which decides how the Edit screen displays it.
   * Null for anything we have no way to display, which validation should already have filtered out.
   */
  private suspend fun createEditorState(media: Media): EditorState? {
    return when {
      isNonGifVideo(media) -> {
        val durationUs = media.duration.milliseconds.inWholeMicroseconds
        val maxVideoDurationUs = if (repository.isVideoTranscodeAvailable()) getMaxVideoDurationUs(media.duration.milliseconds) else durationUs
        EditorState.VideoTrim.forVideo(durationUs, maxVideoDurationUs)
      }

      isGifVideo(media) -> EditorState.VideoGif

      ContentTypeUtil.isGif(media.contentType) -> EditorState.Gif

      ContentTypeUtil.isDocumentType(media.contentType) -> {
        val documentInfo = repository.getDocumentInfo(media)
        EditorState.Document(
          fileName = documentInfo?.fileName,
          fileSize = documentInfo?.fileSize ?: media.size,
          extension = documentInfo?.extension ?: ""
        )
      }

      ContentTypeUtil.isImageType(media.contentType) -> EditorState.Image(createImageEditorModel(media))

      else -> {
        Log.w(TAG, "Nothing can display '${media.contentType}'.")
        null
      }
    }
  }

  // TODO - this should likely be in a repository?
  private fun createImageEditorModel(media: Media): EditorModel {
    val editorModel = EditorModel.create(0x0)
    val element = EditorElement(
      UriGlideRenderer(
        media.uri,
        true,
        0,
        0,
        UriGlideRenderer.STRONG_BLUR,
        object : RequestListener<Bitmap> {
          override fun onResourceReady(resource: Bitmap?, model: Any?, target: Target<Bitmap?>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
            return false
          }

          override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Bitmap?>?, isFirstResource: Boolean): Boolean {
            return false
          }
        }
      )
    )
    element.flags.setSelectable(false).persist()
    editorModel.addElement(element)

    return editorModel
  }

  /**
   * Adds a single [media] item to the selection.
   */
  fun addMedia(media: Media) {
    addMedia(setOf(media))
  }

  /**
   * Removes a single [media] item from the selection.
   */
  fun removeMedia(media: Media) {
    removeMedia(setOf(media))
  }

  /**
   * Removes [media] from the selection.
   *
   * Cancels any pre-uploads for the removed items.
   *
   * @param media Media items to remove.
   */
  fun removeMedia(media: Set<Media>) {
    val snapshot = state.value
    val newSelection = snapshot.selectedMedia - media

    val newFocus = when {
      newSelection.isEmpty() -> null
      snapshot.focusedMedia in media -> {
        val oldFocusIndex = snapshot.selectedMedia.indexOf(snapshot.focusedMedia)
        newSelection[oldFocusIndex.coerceIn(0, newSelection.size - 1)]
      }

      else -> snapshot.focusedMedia
    }

    val newCameraFirstCapture = if (snapshot.cameraFirstCapture in media) null else snapshot.cameraFirstCapture

    updateState {
      copy(
        selectedMedia = newSelection,
        focusedMedia = newFocus,
        editorStateMap = editorStateMap - media.map { it.uri }.toSet(),
        cameraFirstCapture = newCameraFirstCapture
      )
    }

    if (newSelection.isEmpty() && !snapshot.suppressEmptyError) {
      viewModelScope.launch {
        _mediaErrors.emit(MediaFilterError.NoItems)
      }
    }

    // Update story requirements
    viewModelScope.launch {
      updateStorySendRequirements(newSelection)
    }

    // Delete blobs and cancel uploads
    viewModelScope.launch {
      repository.deleteBlobs(media.toList())
    }
    preUploadController.cancelUpload(media)
    preUploadController.updateDisplayOrder(newSelection)
  }

  /**
   * Applies updates to selected media (old -> new).
   */
  fun applyMediaUpdates(oldToNew: Map<Media, Media>) {
    if (oldToNew.isEmpty()) return

    val snapshot = state.value
    val updatedSelection = snapshot.selectedMedia.map { oldToNew[it] ?: it }
    updateState { copy(selectedMedia = updatedSelection) }

    preUploadController.applyMediaUpdates(oldToNew, snapshot.recipientId)
    preUploadController.updateCaptions(updatedSelection)
    preUploadController.updateDisplayOrder(updatedSelection)
  }

  /**
   * Sets the current ordering of selected media.
   */
  fun setDisplayOrder(mediaInOrder: List<Media>) {
    updateState { copy(selectedMedia = mediaInOrder) }
    preUploadController.updateDisplayOrder(mediaInOrder)
  }

  //endregion

  //region Pre-Upload Management

  private fun startUpload(media: List<Media>) {
    val snapshot = state.value
    if (!snapshot.isPreUploadEnabled) return

    val filteredPreUploadMedia = if (snapshot.mode is MediaSendActivityContract.Mode.SingleRecipient) {
      media.filter { !ContentTypeUtil.isDocumentType(it.contentType) }
    } else {
      media.filter { ContentTypeUtil.isStorySupportedType(it.contentType) }
    }

    preUploadController.startUpload(filteredPreUploadMedia, snapshot.recipientId)
    preUploadController.updateCaptions(snapshot.selectedMedia)
    preUploadController.updateDisplayOrder(snapshot.selectedMedia)
  }

  //endregion

  //region Quality

  /**
   * A recording is assumed to be wanted in its entirety, so if it is longer than high quality allows we fall back to
   * standard quality rather than have the editor truncate it to fit.
   */
  private fun onVideoRecorded(duration: Duration) {
    if (state.value.sentMediaQuality != SentMediaQuality.HIGH) {
      return
    }

    val maxDuration = repository.getMaxVideoDurationUs(SentMediaQuality.HIGH, duration).microseconds
    if (duration > maxDuration) {
      Log.i(TAG, "Recording of $duration exceeds the $maxDuration allowed at high quality. Falling back to standard quality.")
      setSentMediaQuality(SentMediaQuality.STANDARD)
    }
  }

  /**
   * Sets the sent media quality.
   *
   * Cancels all pre-uploads and re-initializes video trim data.
   */
  fun setSentMediaQuality(sentMediaQuality: SentMediaQuality) {
    val snapshot = state.value
    if (snapshot.sentMediaQuality == sentMediaQuality) return

    updateState { copy(sentMediaQuality = sentMediaQuality, isPreUploadEnabled = false) }
    preUploadController.cancelAllUploads()

    // Re-clamp video durations based on new quality
    var videoTrimmed = false
    snapshot.selectedMedia.forEach { mediaItem ->
      if (isNonGifVideo(mediaItem) && repository.isVideoTranscodeAvailable()) {
        val existingData = snapshot.editorStateMap[mediaItem.uri] as? EditorState.VideoTrim
        if (existingData != null) {
          onEditVideoDuration(
            totalDurationUs = existingData.videoTrimData.totalInputDurationUs,
            startTimeUs = existingData.videoTrimData.startTimeUs,
            endTimeUs = existingData.videoTrimData.endTimeUs,
            touchEnabled = true,
            uri = mediaItem.uri
          )
          val updatedData = state.value.editorStateMap[mediaItem.uri] as? EditorState.VideoTrim
          if (updatedData != null && updatedData.videoTrimData.getDuration() < existingData.videoTrimData.getDuration()) {
            videoTrimmed = true
          }
        }
      }
    }

    if (videoTrimmed) {
      internalSnackbarEvents.trySend(SnackbarEvent(message = R.string.MediaSendViewModel__video_trimmed_to_fit))
    }
  }

  //endregion

  //region Video Editing

  /**
   * Notifies the view-model that a video's trim/duration has been edited.
   */
  private fun onVideoEdited(uri: Uri, isEdited: Boolean) {
    if (!isEdited) return
    if (!editedVideoUris.add(uri)) return

    // Persist the updated set
    savedStateHandle[KEY_EDITED_VIDEO_URIS] = ArrayList(editedVideoUris)

    val media = state.value.selectedMedia.firstOrNull { it.uri == uri } ?: return
    preUploadController.cancelUpload(media)
  }

  /**
   * Updates video trim duration.
   */
  fun onEditVideoDuration(
    totalDurationUs: Long,
    startTimeUs: Long,
    endTimeUs: Long,
    touchEnabled: Boolean,
    uri: Uri? = state.value.focusedMedia?.uri
  ) {
    if (uri == null) return
    if (!repository.isVideoTranscodeAvailable()) return

    val snapshot = state.value
    val existingData = snapshot.editorStateMap[uri] as? EditorState.VideoTrim
      ?: EditorState.VideoTrim(VideoTrimData(totalInputDurationUs = totalDurationUs))

    val clampedStartTime = maxOf(startTimeUs, 0)
    val unedited = !existingData.videoTrimData.isDurationEdited
    val durationEdited = clampedStartTime > 0 || endTimeUs < totalDurationUs
    val isEntireDuration = startTimeUs == 0L && endTimeUs == totalDurationUs
    val endMoved = !isEntireDuration && existingData.videoTrimData.endTimeUs != endTimeUs
    val maxVideoDurationUs = getMaxVideoDurationUs(existingData.videoTrimData.totalInputDurationUs.microseconds)
    val preserveStartTime = unedited || !endMoved

    val newData = VideoTrimData(
      isDurationEdited = durationEdited,
      totalInputDurationUs = totalDurationUs,
      startTimeUs = clampedStartTime,
      endTimeUs = endTimeUs
    ).let { EditorState.VideoTrim(it, maxVideoDurationUs) }.clampToMaxDuration(maxVideoDurationUs, preserveStartTime)

    // Cancel upload on first edit
    if (unedited && durationEdited) {
      val media = snapshot.selectedMedia.firstOrNull { it.uri == uri }
      if (media != null) {
        preUploadController.cancelUpload(media)
      }
    }

    if (newData != existingData) {
      updateState {
        copy(
          isTouchEnabled = touchEnabled,
          editorStateMap = editorStateMap + (uri to newData)
        )
      }
    } else {
      updateState { copy(isTouchEnabled = touchEnabled) }
    }
  }

  private fun getMaxVideoDurationUs(duration: Duration): Long {
    val snapshot = state.value
    return repository.getMaxVideoDurationUs(
      quality = snapshot.sentMediaQuality,
      duration = duration
    )
  }

  //endregion

  //region Page/Focus Management

  private fun setFocusedMedia(media: Media) {
    updateState { copy(focusedMedia = media) }
  }

  private fun onPageChanged(position: Int) {
    val snapshot = state.value
    val focused = if (position >= snapshot.selectedMedia.size) null else snapshot.selectedMedia[position]
    updateState { copy(focusedMedia = focused) }
  }

  //endregion

  //region Drag/Reordering

  /** Moves the media at [fromIndex] to [toIndex]. Called once per drag, once the item has been dropped. */
  private fun reorderMedia(fromIndex: Int, toIndex: Int) {
    val selectedMedia = state.value.selectedMedia

    if (fromIndex == toIndex || fromIndex !in selectedMedia.indices || toIndex !in selectedMedia.indices) {
      return
    }

    val reordered = selectedMedia.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }

    updateState { copy(selectedMedia = reordered) }
    preUploadController.updateDisplayOrder(reordered)
  }

  private fun isNonGifVideo(media: Media): Boolean {
    return ContentTypeUtil.isVideoType(media.contentType) && !media.isVideoGif
  }

  private fun isGifVideo(media: Media): Boolean {
    return ContentTypeUtil.isVideoType(media.contentType) && media.isVideoGif
  }

  //endregion

  //region Editor State

  fun getEditorState(uri: Uri): EditorState? {
    return internalState.value.editorStateMap[uri]
  }

  fun setEditorState(uri: Uri, state: EditorState) {
    updateState { copy(editorStateMap = editorStateMap + (uri to state)) }
  }

  //endregion

  //region Save To Storage

  /**
   * Writes the focused image, edits included, out to the device's shared storage.
   */
  private fun saveFocusedMediaToStorage() {
    val focusedUri = state.value.focusedMedia?.uri ?: return
    val editorState = state.value.editorStateMap[focusedUri] as? EditorState.Image ?: return

    viewModelScope.launch {
      if (!repository.hasDismissedSaveToStorageWarning && saveToStorageDialog.show(Unit) != DialogResult.POSITIVE) {
        return@launch
      }

      if (!StorageUtil.canWriteToMediaStore() && !writeStoragePermission.request()) {
        internalSnackbarEvents.trySend(SnackbarEvent(message = R.string.MediaSendViewModel__unable_to_save_without_storage_permission))
        return@launch
      }

      if (state.value.isSavingMedia) {
        return@launch
      }

      updateState { copy(isSavingMedia = true) }
      val result = try {
        repository.saveImageToStorage(editorState.model)
      } finally {
        updateState { copy(isSavingMedia = false) }
      }

      internalSnackbarEvents.trySend(
        SnackbarEvent(
          message = when (result) {
            SaveToStorageResult.SUCCESS -> R.string.MediaSendViewModel__media_saved
            SaveToStorageResult.FAILURE -> R.string.MediaSendViewModel__error_saving_media
            SaveToStorageResult.NO_WRITE_ACCESS -> R.string.MediaSendViewModel__unable_to_save_without_storage_permission
          }
        )
      )
    }
  }

  fun markSaveToStorageWarningDismissed() {
    repository.markSaveToStorageWarningDismissed()
  }

  //endregion

  //region View Once

  fun incrementViewOnceState() {
    updateState { copy(viewOnceToggleState = viewOnceToggleState.next()) }
  }

  fun isViewOnceEnabled(): Boolean {
    val snapshot = internalState.value
    return snapshot.selectedMedia.size == 1 &&
      snapshot.viewOnceToggleState == MediaSendState.ViewOnceToggleState.ONCE
  }

  //endregion

  //region Message

  fun setMessage(text: CharSequence?) {
    updateState { copy(message = text) }
  }

  private fun onMessageChange(message: String) {
    setMessage(message)
  }

  //endregion

  //region Story

  fun isStory(): Boolean = state.value.isStory

  fun getStorySendRequirements(): StorySendRequirements = state.value.storySendRequirements

  /**
   * Computed for every flow, not just story flows: the contact picker consults this before allowing a story
   * to be selected, so leaving it at its default would strip story selections made mid-flow.
   */
  private suspend fun updateStorySendRequirements(media: List<Media>) {
    val requirements = repository.getStorySendRequirements(media)
    updateState { copy(storySendRequirements = requirements) }
  }

  //endregion

  //region Recipients

  fun setAdditionalRecipients(recipients: List<MediaSendRecipient>) {
    updateState { copy(additionalRecipients = recipients) }
  }

  fun setScheduledTime(time: Long) {
    updateState { copy(scheduledTime = time) }
  }

  //endregion

  //region Camera First Capture

  private fun addCameraFirstCapture(media: Media) {
    internalState.update { it.copy(cameraFirstCapture = media) }
    addMedia(setOf(media), focusNewlyAdded = true)
  }

  private fun removeCameraFirstCapture() {
    val capture = internalState.value.cameraFirstCapture ?: return
    setSuppressEmptyError(true)
    removeMedia(capture)
  }

  /**
   * Handles a back press out of the edit screen during a camera-first flow where the only selected media is the
   * camera-first capture. Discards that capture and returns to the camera, matching the legacy review behavior.
   */
  private fun onPopFromEdit() {
    removeCameraFirstCapture()
    backStack.goToCamera()
  }

  //endregion

  //region Touch & Error Suppression

  fun setTouchEnabled(isEnabled: Boolean) {
    updateState { copy(isTouchEnabled = isEnabled) }
  }

  fun setSuppressEmptyError(isSuppressed: Boolean) {
    updateState { copy(suppressEmptyError = isSuppressed) }
  }

  fun clearMediaErrors() {
    viewModelScope.launch {
      _mediaErrors.resetReplayCache()
    }
  }

  //endregion

  //region Send

  /**
   * Completes the flow for destinations that are already known, either by sending in place or by handing the
   * payload back to whoever launched us. Safe to call again after the user resolves a safety number change.
   */
  fun performSend() {
    if (internalState.value.isSending || internalState.value.isSent) {
      return
    }

    updateState { copy(isSending = true) }

    viewModelScope.launch {
      when (val result = send()) {
        is SendResult.ReadyToSend -> sendHudCommand(HudCommand.FinishWithResult(result.payload))
        is SendResult.Success -> sendHudCommand(HudCommand.FinishWithoutResult)

        is SendResult.UntrustedIdentity -> {
          updateState { copy(isSending = false) }
          sendHudCommand(HudCommand.ResolveUntrustedIdentities(result.recipientIds))
        }

        is SendResult.Error -> {
          Log.w(TAG, "Send failed: ${result.message}")
          updateState { copy(isSending = false) }
          sendHudCommand(HudCommand.CloseScreen)
        }
      }
    }
  }

  /**
   * Sends the media with current state.
   *
   * @return Result of the send operation.
   */
  suspend fun send(): SendResult {
    val snapshot = state.value

    // Check for untrusted identities
    val allRecipientIds = buildSet {
      snapshot.recipientId?.let { add(it.id) }
      addAll(snapshot.additionalRecipients.map { it.id.id })
    }

    if (allRecipientIds.isNotEmpty()) {
      val untrusted = repository.checkUntrustedIdentities(allRecipientIds, identityChangesSince)
      if (untrusted.isNotEmpty()) {
        return SendResult.UntrustedIdentity(untrusted)
      }
    }

    val request = SendRequest(
      selectedMedia = snapshot.selectedMedia,
      editorStateMap = snapshot.editorStateMap,
      quality = snapshot.sentMediaQuality,
      message = snapshot.message,
      isViewOnce = isViewOnceEnabled(),
      singleRecipientId = snapshot.recipientId,
      recipients = snapshot.additionalRecipients,
      scheduledTime = snapshot.scheduledTime,
      sendType = snapshot.sendType,
      isStory = snapshot.isStory,
      preUploadResults = awaitPreUploadResults()
    )

    val result = repository.send(request)

    if (result is SendResult.Success || result is SendResult.ReadyToSend) {
      updateState { copy(isSent = true) }
    }

    return result
  }

  private suspend fun awaitPreUploadResults(): List<PreUploadResult> = suspendCancellableCoroutine { continuation ->
    preUploadController.getPreUploadResults { results ->
      continuation.resume(results.toList())
    }
  }

  //endregion

  //region Query Methods

  fun hasSelectedMedia(): Boolean = internalState.value.selectedMedia.isNotEmpty()

  fun isSelectedMediaEmpty(): Boolean = internalState.value.selectedMedia.isEmpty()

  fun kick() {
    internalState.update { it }
  }

  //endregion

  //region Lifecycle

  override fun onCleared() {
    if (internalState.value.isSent) {
      return
    }

    preUploadController.cancelAllUploads()
    preUploadController.deleteAbandonedAttachments()
  }

  private fun shouldPreUpload(metered: Boolean): Boolean = !metered

  //endregion

  companion object {
    private val TAG = Log.tag(MediaSendViewModel::class)

    private const val KEY_ARGS = "media_send_vm_args"
    private const val KEY_IDENTITY_CHANGES_SINCE = "media_send_vm_identity_changes_since"
    private const val KEY_STATE = "media_send_vm_state"
    private const val KEY_EDITED_VIDEO_URIS = "media_send_vm_edited_video_uris"
    private const val KEY_BACK_STACK = "media_send_vm_back_stack"
  }

  /**
   * Factory that creates [MediaSendViewModel] from a [SavedStateHandle] and static dependencies.
   *
   * On first creation, [args] and [identityChangesSince] are written into the [SavedStateHandle].
   * On process death restoration, the [SavedStateHandle] already contains the persisted values
   * and the constructor parameters are ignored.
   */
  class Factory(
    private val args: MediaSendActivityContract.Args,
    private val identityChangesSince: Long = System.currentTimeMillis(),
    private val repository: MediaSendRepository = MediaSendDependencies.mediaSendRepository,
    private val isMeteredFlow: Flow<Boolean> = MeteredConnectivity.isMetered(MediaSendDependencies.application)
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
      val savedStateHandle = extras.createSavedStateHandle()

      if (!savedStateHandle.contains(KEY_ARGS)) {
        savedStateHandle[KEY_ARGS] = args
      }
      if (!savedStateHandle.contains(KEY_IDENTITY_CHANGES_SINCE)) {
        savedStateHandle[KEY_IDENTITY_CHANGES_SINCE] = identityChangesSince
      }

      return MediaSendViewModel(
        savedStateHandle = savedStateHandle,
        repository = repository,
        preUploadController = PreUploadController(),
        isMeteredFlow = isMeteredFlow
      ) as T
    }
  }
}
