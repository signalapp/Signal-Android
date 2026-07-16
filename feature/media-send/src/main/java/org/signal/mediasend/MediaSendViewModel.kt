/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend

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
import kotlinx.coroutines.withContext
import org.signal.core.models.media.Media
import org.signal.core.models.media.MediaFolder
import org.signal.core.ui.compose.DialogController
import org.signal.core.ui.compose.DialogResult
import org.signal.core.util.ContentTypeUtil
import org.signal.core.util.StringUtil
import org.signal.imageeditor.core.model.EditorElement
import org.signal.imageeditor.core.model.EditorModel
import org.signal.imageeditor.core.renderers.UriGlideRenderer
import org.signal.mediasend.capture.CameraXScreenEvent
import org.signal.mediasend.capture.MediaCaptureScreenEvent
import org.signal.mediasend.edit.MediaEditScreenEvent
import org.signal.mediasend.edit.video.VideoTrimData
import org.signal.mediasend.preupload.PreUploadController
import org.signal.mediasend.select.MediaSelectScreenEvent
import org.thoughtcrime.securesms.video.videoconverter.utils.VideoConstants
import java.io.FileInputStream
import java.io.IOException
import java.util.Collections
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
    mode = args.mode,
    isStory = args.isStory,
    isReply = args.isReply,
    isAddToGroupStoryFlow = args.isAddToGroupStoryFlow,
    maxSelection = args.maxSelection,
    message = args.initialMessage,
    isContactSelectionRequired = args.mode == MediaSendActivityContract.Mode.ChooseAfterMediaSelection,
    sendType = args.sendType
  )

  val backStack: NavBackStack<NavKey> by savedStateHandle.saved(
    serializer = NavBackStackSerializer(NavKeySerializer()),
    key = KEY_BACK_STACK
  ) {
    NavBackStack(if (args.isCameraFirst) MediaSendNavKey.Capture.Camera else MediaSendNavKey.Select.Folders)
  }

  private val internalSnackbarEvents: Channel<SnackbarEvent> = Channel(Channel.BUFFERED)
  internal val snackbarEvents: Flow<SnackbarEvent> = internalSnackbarEvents.receiveAsFlow()

  internal val usernameScannedDialog = DialogController<String>()
  internal val linkedDeviceScannedDialog = DialogController<Unit>()

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

  /** Tracks drag state for media reordering. */
  private var lastMediaDrag: Pair<Int, Int> = Pair(0, 0)

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
      MediaSelectScreenEvent.NavigateToEdit -> backStack.goToEdit()
    }
  }

  override fun onMediaCaptureScreenEvent(mediaCaptureScreenEvent: MediaCaptureScreenEvent) {
    when (mediaCaptureScreenEvent) {
      MediaCaptureScreenEvent.ShowCamera -> backStack.goToCamera()
      MediaCaptureScreenEvent.ShowTextStory -> backStack.goToTextStory()
      is MediaCaptureScreenEvent.Camera -> onCameraXScreenEvent(mediaCaptureScreenEvent.event)
    }
  }

  private fun onCameraXScreenEvent(event: CameraXScreenEvent) {
    when (event) {
      CameraXScreenEvent.CameraCountButtonClicked -> backStack.goToEdit()
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
      MediaEditScreenEvent.NavigateToSend -> backStack.goToSend()
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
    }
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
          FileInputStream(videoCaptured.fd).use { stream ->
            val length = stream.channel.size()
            val uri = MediaSendDependencies.blobs
              .forData(stream, length)
              .withMimeType(VideoConstants.RECORDED_VIDEO_CONTENT_TYPE)
              .createForSingleSessionOnDisk(MediaSendDependencies.application)

            buildCapturedMedia(uri, VideoConstants.RECORDED_VIDEO_CONTENT_TYPE, 0, 0, length)
          }
        } catch (e: IOException) {
          null
        }
      }

      if (media != null) {
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
        // Initialize video trim states for new videos
        val initializedVideoEditorStates = filterResult.filteredMedia
          .filterNot { snapshot.editorStateMap.containsKey(it.uri) }
          .filter { isNonGifVideo(it) }
          .associate { video ->
            val maxVideoDurationUs = getMaxVideoDurationUs(video.duration.milliseconds)
            val durationUs = video.duration.milliseconds.inWholeMicroseconds
            video.uri to EditorState.VideoTrim.forVideo(durationUs, maxVideoDurationUs)
          }

        val initializedImageEditorStates = filterResult.filteredMedia
          .filterNot { snapshot.editorStateMap.containsKey(it.uri) }
          .filter { ContentTypeUtil.isImageType(it.contentType) }
          .associate { image ->
            // TODO - this should likely be in a repository?
            val editorModel = EditorModel.create(0x0)
            val element = EditorElement(
              UriGlideRenderer(
                image.uri,
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
            image.uri to EditorState.Image(editorModel)
          }

        updateState {
          val newFocus = if (focusNewlyAdded) {
            filterResult.filteredMedia.lastOrNull { it in media } ?: focusedMedia ?: filterResult.filteredMedia.firstOrNull()
          } else {
            focusedMedia ?: filterResult.filteredMedia.firstOrNull()
          }

          copy(
            selectedMedia = filterResult.filteredMedia,
            focusedMedia = newFocus,
            editorStateMap = editorStateMap + initializedVideoEditorStates + initializedImageEditorStates
          )
        }

        // Update story requirements
        updateStorySendRequirements(filterResult.filteredMedia)

        // Start pre-uploads for new media
        val newMedia = filterResult.filteredMedia.toSet().intersect(media).toList()
        startUpload(newMedia)
      }

      if (filterResult.error != null) {
        _mediaErrors.emit(filterResult.error)
      }
    }
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
    snapshot.selectedMedia.forEach { mediaItem ->
      if (isNonGifVideo(mediaItem) && repository.isVideoTranscodeAvailable()) {
        val existingData = snapshot.editorStateMap[mediaItem.uri] as? EditorState.VideoTrim
        if (existingData != null) {
          val maxVideoDurationUs = getMaxVideoDurationUs(existingData.videoTrimData.totalInputDurationUs.microseconds)
          onEditVideoDuration(
            totalDurationUs = existingData.videoTrimData.totalInputDurationUs,
            startTimeUs = existingData.videoTrimData.startTimeUs,
            endTimeUs = existingData.videoTrimData.endTimeUs,
            touchEnabled = true,
            uri = mediaItem.uri
          )
        }
      }
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
    ).let { EditorState.VideoTrim(it) }.clampToMaxDuration(maxVideoDurationUs, preserveStartTime)

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
      maxFileSizeBytes = repository.getVideoMaxSizeBytes(),
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

  fun swapMedia(originalStart: Int, end: Int): Boolean {
    var start = originalStart

    if (lastMediaDrag.first == start && lastMediaDrag.second == end) {
      return true
    } else if (lastMediaDrag.first == start) {
      start = lastMediaDrag.second
    }

    val snapshot = state.value

    if (end >= snapshot.selectedMedia.size ||
      end < 0 ||
      start >= snapshot.selectedMedia.size ||
      start < 0
    ) {
      return false
    }

    lastMediaDrag = Pair(originalStart, end)

    val newMediaList = snapshot.selectedMedia.toMutableList()

    if (start < end) {
      for (i in start until end) {
        Collections.swap(newMediaList, i, i + 1)
      }
    } else {
      for (i in start downTo end + 1) {
        Collections.swap(newMediaList, i, i - 1)
      }
    }

    updateState { copy(selectedMedia = newMediaList) }
    return true
  }

  fun isValidMediaDragPosition(position: Int): Boolean {
    return position >= 0 && position < internalState.value.selectedMedia.size
  }

  private fun isNonGifVideo(media: Media): Boolean {
    return ContentTypeUtil.isVideo(media.contentType) && !media.isVideoGif
  }

  fun onMediaDragFinished() {
    lastMediaDrag = Pair(0, 0)
    preUploadController.updateDisplayOrder(internalState.value.selectedMedia)
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

  fun setMessage(text: String?) {
    updateState { copy(message = text) }
  }

  private fun onMessageChange(message: String) {
    setMessage(message)
  }

  //endregion

  //region Story

  fun isStory(): Boolean = state.value.isStory

  fun getStorySendRequirements(): StorySendRequirements = state.value.storySendRequirements

  private suspend fun updateStorySendRequirements(media: List<Media>) {
    if (!state.value.isStory) return
    val requirements = repository.getStorySendRequirements(media)
    updateState { copy(storySendRequirements = requirements) }
  }

  //endregion

  //region Recipients

  fun setAdditionalRecipients(recipientIds: List<MediaRecipientId>) {
    updateState { copy(additionalRecipientIds = recipientIds) }
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
   * Sends the media with current state.
   *
   * @return Result of the send operation.
   */
  suspend fun send(): SendResult {
    val snapshot = state.value

    // Check for untrusted identities
    val allRecipientIds = buildSet {
      snapshot.recipientId?.let { add(it.id) }
      addAll(snapshot.additionalRecipientIds.map { it.id })
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
      recipientIds = snapshot.additionalRecipientIds,
      scheduledTime = snapshot.scheduledTime,
      sendType = snapshot.sendType,
      isStory = snapshot.isStory
    )

    val result = repository.send(request)

    if (result is SendResult.Success) {
      updateState { copy(isSent = true) }
    }

    return result
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
    preUploadController.cancelAllUploads()
    preUploadController.deleteAbandonedAttachments()
  }

  private fun shouldPreUpload(metered: Boolean): Boolean = !metered

  //endregion

  companion object {
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
