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
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.signal.core.models.media.Media
import org.signal.core.ui.compose.DialogController
import org.signal.core.ui.compose.DialogResult
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.util.ContentTypeUtil
import org.signal.core.util.StringUtil
import org.signal.core.util.logging.Log
import org.signal.imageeditor.core.Renderer
import org.signal.imageeditor.core.model.EditorElement
import org.signal.imageeditor.core.model.EditorModel
import org.signal.imageeditor.core.renderers.UriGlideRenderer
import org.signal.mediasend.preupload.PreUploadController
import org.signal.mediasend.preupload.PreUploadResult
import org.signal.mediasend.screens.edit.ImageController
import org.signal.mediasend.screens.edit.ScheduleSendOption
import org.signal.mediasend.screens.edit.image.BrushTool
import org.signal.mediasend.screens.edit.image.BrushWidthsState
import org.signal.mediasend.screens.edit.video.VideoTrimData
import org.signal.mediasend.util.MeteredConnectivity
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Configuration-survivable state manager for the media send flow.
 *
 * Uses [SavedStateHandle] for automatic state persistence across process death.
 * [MediaSendFlowState] is fully [Parcelable] and saved directly as a single key.
 */
class MediaSendFlowViewModel(
  private val savedStateHandle: SavedStateHandle,
  private val repository: MediaSendRepository,
  private val preUploadController: PreUploadController,
  isMeteredFlow: Flow<Boolean>
) : ViewModel() {

  private val args: MediaSendFlowActivityContract.Args = savedStateHandle[KEY_ARGS]
    ?: throw IllegalStateException("MediaSendViewModel requires args in SavedStateHandle. Use Factory to create.")

  private val identityChangesSince: Long = savedStateHandle[KEY_IDENTITY_CHANGES_SINCE]
    ?: throw IllegalStateException("MediaSendViewModel requires identityChangesSince in SavedStateHandle. Use Factory to create.")

  private val defaultState = MediaSendFlowState(
    isCameraFirst = args.isCameraFirst,
    recipientId = args.recipientId,
    additionalRecipients = args.additionalRecipients,
    mode = args.mode,
    isStory = args.isStory,
    isReply = args.isReply,
    isAddToGroupStoryFlow = args.isAddToGroupStoryFlow,
    maxSelection = args.maxSelection,
    message = if (args.asTextStory) null else normalizeMessageBody(args.initialMessage),
    isContactSelectionRequired = args.mode == MediaSendFlowActivityContract.Mode.ChooseAfterMediaSelection,
    sendType = args.sendType
  )

  val backStack: NavBackStack<NavKey> by savedStateHandle.saved(
    serializer = NavBackStackSerializer(NavKeySerializer()),
    key = KEY_BACK_STACK
  ) {
    val startKey = when {
      args.asTextStory -> MediaSendRoute.Capture.TextStory
      args.isCameraFirst -> MediaSendRoute.Capture.Camera
      args.initialMedia.isNotEmpty() -> MediaSendRoute.Edit
      else -> MediaSendRoute.Select.Folders
    }

    NavBackStack(startKey)
  }

  private val internalSnackbarEvents: Channel<SnackbarEvent> = Channel(Channel.BUFFERED)
  internal val snackbarEvents: Flow<SnackbarEvent> = internalSnackbarEvents.receiveAsFlow()

  private val internalToastEvents: Channel<ToastEvent> = Channel(Channel.BUFFERED)
  internal val toastEvents: Flow<ToastEvent> = internalToastEvents.receiveAsFlow()

  /**
   * The media that has most recently landed in the selection, for the screens that follow the selection as it grows.
   * Only this knows which that is: what the user picked is not necessarily what survived validation.
   */
  private val internalSelectionAdditions: Channel<Media> = Channel(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  internal val selectionAdditions: Flow<Media> = internalSelectionAdditions.receiveAsFlow()

  internal val usernameScannedDialog = DialogController<String>()
  internal val linkedDeviceScannedDialog = DialogController<Unit>()
  internal val discardMediaDialog = DialogController<Unit>()
  internal val addToGroupStoryDialog = DialogController<MediaRecipientId>()

  private val qrCheckRequest: Channel<String> = Channel(Channel.RENDEZVOUS)

  /**
   * Main UI state. Backed by [SavedStateHandle] for automatic process death survival.
   * Writes to this flow are automatically persisted.
   */
  private val internalState: MutableStateFlow<MediaSendFlowState> = savedStateHandle.getMutableStateFlow(KEY_STATE, defaultState)
  val state: StateFlow<MediaSendFlowState> = internalState.asStateFlow()

  private val editedVideoUris: MutableSet<Uri> = mutableSetOf<Uri>().apply {
    addAll(savedStateHandle[KEY_EDITED_VIDEO_URIS] ?: emptyList())
  }

  /** One-shot HUD commands exposed as a Flow. */
  private val hudCommandChannel = Channel<MediaSendFlowHudCommand>(Channel.BUFFERED)
  val hudCommands: Flow<MediaSendFlowHudCommand> = hudCommandChannel.receiveAsFlow()

  /** Per-image editor controllers, held here so results arriving from outside the flow can be applied immediately. */
  internal val imageControllers = ImageController.Container(BrushWidthsState(internalState.value.brushWidths))

  /**
   * Serializes changes to the selection. Adding validates the whole selection off the main thread, so the read of the
   * current selection and the write of the new one straddle a suspension point; without this, anything that changed the
   * selection in between would be overwritten. A drag across the gallery grid produces exactly that traffic.
   *
   * Uncontended locking does not suspend, so a lone change still lands within the caller's frame.
   */
  private val selectionMutex = Mutex()

  /** Character count for the message field. */
  val messageCharacterCount: Flow<Int> = state
    .map { it.message?.let { msg -> StringUtil.getGraphemeCount(msg) } ?: 0 }
    .distinctUntilChanged()

  init {
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
              DialogResult.POSITIVE -> sendHudCommand(MediaSendFlowHudCommand.GoToLinkedDevices)
              else -> Unit
            }
          }
          MediaSendQrRepository.QrCheckResult.None -> Unit
          is MediaSendQrRepository.QrCheckResult.ReRegistration -> sendHudCommand(MediaSendFlowHudCommand.GoToQuickTransfer(qrData))
          is MediaSendQrRepository.QrCheckResult.Username -> {
            when (usernameScannedDialog.show(result.username)) {
              DialogResult.POSITIVE -> sendHudCommand(MediaSendFlowHudCommand.GoToConversation(result.recipientId))
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
  }

  /** Updates state atomically — automatically persisted via SavedStateHandle-backed MutableStateFlow. */
  private inline fun updateState(crossinline transform: MediaSendFlowState.() -> MediaSendFlowState) {
    internalState.update { it.transform() }
  }

  /**
   * Runs [mutation] with the selection held still, so that it is the only thing changing
   * [MediaSendFlowState.selectedMedia] while it runs. See [selectionMutex].
   */
  private fun mutateSelection(mutation: suspend () -> Unit) {
    viewModelScope.launch {
      selectionMutex.withLock {
        mutation()
      }
    }
  }

  //region Media Selection

  /** Applies a change a screen has asked for to the flow's own state. See [MediaSendFlowEvent]. */
  internal fun onEvent(event: MediaSendFlowEvent) {
    when (event) {
      is MediaSendFlowEvent.AddMedia -> addMedia(event.media)
      is MediaSendFlowEvent.RemoveMedia -> removeMedia(event.media)
      is MediaSendFlowEvent.SetFocusedMedia -> setFocusedMedia(event.media)
      is MediaSendFlowEvent.ReorderSelectedMedia -> reorderMedia(event.fromIndex, event.toIndex)
      is MediaSendFlowEvent.ShowSnackbar -> internalSnackbarEvents.trySend(event.snackbar)
      MediaSendFlowEvent.SelectionRejectionShown -> updateState { copy(isSelectionRejected = false) }
      is MediaSendFlowEvent.MediaCaptured -> onMediaCaptured(event.media, event.recordingDuration)
      is MediaSendFlowEvent.QrCodeScanned -> qrCheckRequest.trySend(event.data)
      MediaSendFlowEvent.CloseRequested -> onCloseRequested()

      is MediaSendFlowEvent.SetMediaQuality -> setSentMediaQuality(event.quality)
      is MediaSendFlowEvent.SetBrushWidth -> setBrushWidth(event.tool, event.fraction)
      is MediaSendFlowEvent.SetBlurFacesEnabled -> setBlurFacesEnabled(event.enabled)
      is MediaSendFlowEvent.VideoTrimChanged -> onEditVideoDuration(
        totalDurationUs = event.videoTrimData.totalInputDurationUs,
        startTimeUs = event.videoTrimData.startTimeUs,
        endTimeUs = event.videoTrimData.endTimeUs,
        touchEnabled = event.editingComplete
      )
      MediaSendFlowEvent.ToggleViewOnce -> toggleViewOnce()
      MediaSendFlowEvent.ToggleVideoMuted -> toggleVideoMuted()

      is MediaSendFlowEvent.AddMessageRequested -> onAddMessageRequested(event.startWithEmojiKeyboard)
      is MediaSendFlowEvent.ScheduleSendRequested -> onScheduleSendClick(event.option)
      MediaSendFlowEvent.StickerRequested -> sendHudCommand(MediaSendFlowHudCommand.SelectSticker)
      MediaSendFlowEvent.NextRequested -> onNextClick()

      is MediaSendFlowEvent.NavigateToFiles -> backStack.goToFiles(event.mediaFolder)
      MediaSendFlowEvent.NavigateToFolders -> backStack.goToFolders()
      MediaSendFlowEvent.NavigateToEdit -> backStack.goToEdit()
      MediaSendFlowEvent.NavigateToCamera -> backStack.goToCamera()
      MediaSendFlowEvent.NavigateToTextStory -> backStack.goToTextStory()
      MediaSendFlowEvent.NavigateBackFromSelect -> onPopFromSelect()
      MediaSendFlowEvent.NavigateBackFromEdit -> onPopFromEdit()
    }
  }

  /** Opens the message field, which is a dialog the flow's host owns rather than anything on a screen. */
  private fun onAddMessageRequested(startWithEmojiKeyboard: Boolean) {
    val snapshot: MediaSendFlowState = state.value

    sendHudCommand(
      MediaSendFlowHudCommand.ShowAddAMessageDialog(
        message = snapshot.message ?: "",
        startWithEmojiKeyboard = startWithEmojiKeyboard,
        isViewOnceAvailable = snapshot.isViewOnceAvailable
      )
    )
  }

  /**
   * Leaves the flow at the user's request, confirming first if that would throw a selection away. Closing for reasons
   * of our own emits [MediaSendFlowHudCommand.CloseScreen] directly instead.
   */
  internal fun onCloseRequested() {
    if (state.value.selectedMedia.isEmpty()) {
      sendHudCommand(MediaSendFlowHudCommand.CloseScreen)
      return
    }

    viewModelScope.launch {
      if (discardMediaDialog.show(Unit) == DialogResult.POSITIVE) {
        sendHudCommand(MediaSendFlowHudCommand.CloseScreen)
      }
    }
  }

  /**
   * Backs out of a select screen while nothing is selected, stepping over an editor that would have nothing to edit
   * and no toolbar to leave by. Decided here rather than when the selection empties, so that re-selecting something
   * still returns the user to their editor.
   */
  private fun onPopFromSelect() {
    val destination = backStack.dropLast(1).dropLastWhile { it == MediaSendRoute.Edit }

    if (destination.isEmpty()) {
      onCloseRequested()
      return
    }

    while (backStack.size > destination.size) {
      backStack.pop()
    }
  }

  /**
   * Result of the picker opened for [MediaSendFlowHudCommand.SelectSticker], applied to the focused image. A null [renderer] means
   * the picker was dismissed.
   */
  fun onStickerSelected(renderer: Renderer?) {
    val controller = focusedImageController() ?: return

    if (renderer != null) {
      controller.insertSticker(renderer)
    } else {
      controller.cancelStickerInsertion()
    }
  }

  private fun focusedImageController(): ImageController? {
    val snapshot = state.value
    val uri = snapshot.focusedMedia?.uri ?: return null
    val editorState = snapshot.editorStateMap[uri] as? EditorState.Image ?: return null

    return imageControllers.getOrCreate(uri, editorState.model)
  }

  /**
   * Masks or unmasks the faces in the focused image. Enabling this is what triggers the detection itself, which the
   * editor's own state reports on, so that the work outlives the composition it was requested from.
   */
  private fun setBlurFacesEnabled(enabled: Boolean) {
    val controller = focusedImageController() ?: return

    if (enabled) {
      viewModelScope.launch {
        controller.blurFaces(MediaSendDependencies.application)
      }
    } else {
      controller.clearFaceBlurs()
    }
  }

  private fun setBrushWidth(tool: BrushTool, fraction: Float) {
    val brushWidths = state.value.brushWidths.with(tool, fraction)

    updateState { copy(brushWidths = brushWidths) }
    repository.brushWidths = brushWidths
  }

  /**
   * Takes on media the camera captured and moves the user along to edit it.
   *
   * @param recordingDuration How long the capture ran, for the captures that were recorded.
   */
  private fun onMediaCaptured(media: Media, recordingDuration: Duration?) {
    recordingDuration?.let { onVideoRecorded(it) }

    if (args.isCameraFirst && internalState.value.cameraFirstCapture == null) {
      addCameraFirstCapture(media)
    } else {
      addMedia(setOf(media), focusNewlyAdded = true)
    }

    backStack.goToEdit()
  }

  private fun sendHudCommand(hudCommand: MediaSendFlowHudCommand) {
    viewModelScope.launch {
      hudCommandChannel.send(hudCommand)
    }
  }

  /**
   * Adds [media] to the selection, preserving insertion order and uniqueness by URI.
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
   * Focus is updated within the same atomic state write that adds the media, so [MediaSendFlowState.focusedMedia]
   * is never left pointing at an item that is not yet present in [MediaSendFlowState.selectedMedia].
   */
  private fun addMedia(media: Set<Media>, focusNewlyAdded: Boolean) {
    mutateSelection {
      val snapshot = state.value
      val selectedUris: Set<Uri> = snapshot.selectedMedia.mapTo(mutableSetOf()) { it.uri }

      // Anything already selected keeps the instance we hold rather than the one being handed to us: ours has been
      // through population, and may carry a caption or a file name the caller's copy does not know about.
      val newSelectionList: List<Media> = snapshot.selectedMedia + media
        .filterNot { it.uri in selectedUris }
        .distinctBy { it.uri }

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
            // Re-bind to the populated instance by URI, so the capture we hold is the one that is actually selected:
            // population fills in a video's 0x0 dimensions, producing a new Media that no longer equals the
            // pre-population capture. Cleared once more than the capture is selected.
            cameraFirstCapture = if (updatedMedia.size > 1) {
              null
            } else {
              cameraFirstCapture?.let { capture -> updatedMedia.find { it.uri == capture.uri } ?: capture }
            }
          )
        }

        updatedMedia.lastOrNull { item -> media.any { it.uri == item.uri } }?.let { internalSelectionAdditions.trySend(it) }

        if (initializedEditorStates.values.any { it is EditorState.VideoTrim && it.videoTrimData.isDurationEdited }) {
          internalSnackbarEvents.trySend(SnackbarEvent(message = R.string.MediaSendViewModel__video_trimmed_to_fit))
        }

        // Update story requirements
        val storySendRequirements = updateStorySendRequirements(updatedMedia)

        // Start pre-uploads for new media
        val newMedia = updatedMedia.filter { item -> media.any { it.uri == item.uri } }
        startUpload(newMedia, storySendRequirements)
      }

      filterResult.error?.let { onMediaFilterError(it, isSelectionEmpty = filterResult.filteredMedia.isEmpty()) }
    }
  }

  /**
   * Tells the user why media they picked did not make it into the selection.
   *
   * When nothing survived there is no editor to show the message on top of, so the flow falls back to whichever screen
   * the user can pick again from instead of leaving them on an empty one.
   */
  private fun onMediaFilterError(error: MediaFilterError, isSelectionEmpty: Boolean) {
    val message = when (error) {
      is MediaFilterError.ItemTooLarge -> R.string.MediaSendViewModel__one_or_more_items_were_too_large
      is MediaFilterError.ItemInvalidType -> R.string.MediaSendViewModel__one_or_more_items_were_invalid
      is MediaFilterError.TooManyItems -> R.string.MediaSendViewModel__too_many_items_selected
    }

    internalSnackbarEvents.trySend(SnackbarEvent(message = message))
    updateState { copy(isSelectionRejected = true) }

    if (isSelectionEmpty && backStack.lastOrNull() == MediaSendRoute.Edit) {
      backStack.resetTo(if (state.value.isCameraFirst) MediaSendRoute.Capture.Camera else MediaSendRoute.Select.Folders)
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
    // Bounded to what we would ever send, since an unbounded decode of something like a 50MP photo produces a bitmap
    // too large for a Canvas to draw.
    val constraints = repository.getMediaConstraints(SentMediaQuality.HIGH)

    val editorModel = EditorModel.create(0x0)
    val element = EditorElement(
      UriGlideRenderer(
        media.uri,
        true,
        constraints.imageMaxWidth,
        constraints.imageMaxHeight,
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
    mutateSelection {
      val snapshot = state.value
      val removedUris: Set<Uri> = media.mapTo(mutableSetOf()) { it.uri }

      // Removal is by URI, not by equality. Population and editing both replace a selected item with a new, non-equal
      // Media for the same URI, so the instance a caller is holding is rarely the one we have.
      val newSelection = snapshot.selectedMedia.filterNot { it.uri in removedUris }

      val newFocus = when {
        newSelection.isEmpty() -> null
        snapshot.focusedMedia?.uri in removedUris -> {
          val oldFocusIndex = snapshot.selectedMedia.indexOfFirst { it.uri == snapshot.focusedMedia?.uri }
          newSelection[oldFocusIndex.coerceIn(0, newSelection.size - 1)]
        }

        else -> snapshot.focusedMedia
      }

      val newCameraFirstCapture = if (snapshot.cameraFirstCapture?.uri in removedUris) null else snapshot.cameraFirstCapture

      updateState {
        copy(
          selectedMedia = newSelection,
          focusedMedia = newFocus,
          editorStateMap = editorStateMap - removedUris,
          cameraFirstCapture = newCameraFirstCapture
        )
      }

      removedUris.forEach { imageControllers.remove(it) }

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
  }

  /**
   * Sets the current ordering of selected media.
   */
  fun setDisplayOrder(mediaInOrder: List<Media>) {
    mutateSelection {
      updateState { copy(selectedMedia = mediaInOrder) }
      preUploadController.updateDisplayOrder(mediaInOrder)
    }
  }

  //endregion

  //region Pre-Upload Management

  /**
   * @param storySendRequirements Requirements for the current selection, so a story destination can skip media that
   *   will be clipped at send time and uploaded as a different asset.
   */
  private fun startUpload(media: List<Media>, storySendRequirements: Map<Uri, StorySendRequirements>) {
    val snapshot = state.value
    if (!snapshot.isPreUploadEnabled) return

    val isChatDestination = (snapshot.mode is MediaSendFlowActivityContract.Mode.SingleRecipient && !snapshot.isStory) || !snapshot.storiesEnabled

    val filteredPreUploadMedia = if (isChatDestination) {
      media.filter { !ContentTypeUtil.isDocumentType(it.contentType) }
    } else {
      media.filter { storySendRequirements[it.uri] != StorySendRequirements.REQUIRES_CROP }
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

    updateState {
      copy(
        sentMediaQuality = sentMediaQuality,
        videoTranscodingTiers = repository.getVideoTranscodingTiers(sentMediaQuality),
        isPreUploadEnabled = false
      )
    }
    repository.sentMediaQuality = sentMediaQuality
    preUploadController.cancelAllUploads()

    // Confirmed from here rather than from the picker so that the fallback in onVideoRecorded is reported too.
    qualityToastEvent(sentMediaQuality, snapshot.selectedMedia)?.let { internalToastEvents.trySend(it) }

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

  /**
   * Copy confirming a move to [quality], which names the media itself when there is only one item and counts them
   * otherwise. Null for a lone attachment that is neither a video nor an image, which there is nothing to say about.
   */
  private fun qualityToastEvent(quality: SentMediaQuality, media: List<Media>): ToastEvent? {
    if (media.isEmpty()) {
      return null
    }

    val isHigh = quality == SentMediaQuality.HIGH
    val single = media.singleOrNull()

    val message = when {
      single == null -> ToastMessage.Quantity(
        id = if (isHigh) R.plurals.MediaReviewFragment__items_set_to_high_quality else R.plurals.MediaReviewFragment__items_set_to_standard_quality,
        count = media.size
      )

      isNonGifVideo(single) -> ToastMessage.Text(
        if (isHigh) R.string.MediaReviewFragment__video_set_to_high_quality else R.string.MediaReviewFragment__video_set_to_standard_quality
      )

      ContentTypeUtil.isImageType(single.contentType) -> ToastMessage.Text(
        if (isHigh) R.string.MediaReviewFragment__photo_set_to_high_quality else R.string.MediaReviewFragment__photo_set_to_standard_quality
      )

      else -> {
        Log.i(TAG, "No quality confirmation for an attachment of type: ${single.contentType}")
        return null
      }
    }

    return ToastEvent(
      icon = if (isHigh) SignalIcons.QualityHigh else SignalIcons.QualityHighSlash,
      message = message
    )
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
   * Toggles whether the focused video's audio track is stripped when it is sent.
   */
  private fun toggleVideoMuted() {
    val snapshot = state.value
    val uri = snapshot.focusedMedia?.uri ?: return
    val existing = snapshot.editorStateMap[uri] as? EditorState.VideoTrim ?: return
    val isNowMuted = !existing.videoTrimData.isMuted
    val updated = existing.copy(videoTrimData = existing.videoTrimData.copy(isMuted = isNowMuted))

    updateState { copy(editorStateMap = editorStateMap + (uri to updated)) }

    if (isNowMuted) {
      internalToastEvents.trySend(
        ToastEvent(
          icon = SignalIcons.SpeakerSlash,
          message = ToastMessage.Text(R.string.MediaSendViewModel__video_will_be_sent_without_audio)
        )
      )
    }

    snapshot.selectedMedia.firstOrNull { it.uri == uri }?.let { preUploadController.cancelUpload(it) }
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

    val newData = existingData.videoTrimData.copy(
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
    mutateSelection {
      val selectedMedia = state.value.selectedMedia

      if (fromIndex == toIndex || fromIndex !in selectedMedia.indices || toIndex !in selectedMedia.indices) {
        return@mutateSelection
      }

      val reordered = selectedMedia.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }

      updateState { copy(selectedMedia = reordered) }
      preUploadController.updateDisplayOrder(reordered)
    }
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

  //region View Once

  fun isViewOnceEnabled(): Boolean {
    return internalState.value.isViewOnceEnabled
  }

  /**
   * Flips view-once. Turning it on drops any message the user had already typed, since a view-once send cannot carry
   * a body, and confirms the change with a toast.
   */
  fun toggleViewOnce() {
    updateState { copy(viewOnceToggleState = viewOnceToggleState.next()) }

    if (!internalState.value.isViewOnceEnabled) {
      return
    }

    setMessage(null)

    val focusedMedia = internalState.value.focusedMedia
    val isVideo = focusedMedia != null &&
      ContentTypeUtil.isVideoType(focusedMedia.contentType) &&
      !focusedMedia.isVideoGif

    internalToastEvents.trySend(
      ToastEvent(
        icon = SignalIcons.ViewOnce,
        message = ToastMessage.Text(
          if (isVideo) {
            R.string.MediaSendViewModel__video_set_to_view_once
          } else {
            R.string.MediaSendViewModel__photo_set_to_view_once
          }
        )
      )
    )
  }

  //endregion

  //region Message

  fun setMessage(text: CharSequence?) {
    updateState { copy(message = normalizeMessageBody(text)) }
  }

  //endregion

  //region Story

  fun isStory(): Boolean = state.value.isStory

  fun getStorySendRequirements(): StorySendRequirements = state.value.storySendRequirements

  /**
   * Computed for every flow, not just story flows: the contact picker consults this before allowing a story
   * to be selected, so leaving it at its default would strip story selections made mid-flow.
   */
  private suspend fun updateStorySendRequirements(media: List<Media>): Map<Uri, StorySendRequirements> {
    val requirements = repository.getStorySendRequirements(media)
    updateState { copy(storySendRequirements = requirements.values.strictest()) }
    return requirements
  }

  //endregion

  //region Recipients

  fun setAdditionalRecipients(recipients: List<MediaSendRecipient>) {
    updateState { copy(additionalRecipients = recipients) }
  }

  //endregion

  //region Scheduled Send

  private fun onScheduleSendClick(option: ScheduleSendOption) {
    when (option) {
      is ScheduleSendOption.PresetTime -> sendHudCommand(MediaSendFlowHudCommand.ConfirmScheduledSend(option.timeMs))
      ScheduleSendOption.PickTime -> sendHudCommand(MediaSendFlowHudCommand.PickScheduledSendTime)
    }
  }

  /**
   * A time chosen in the picker opened for [MediaSendFlowHudCommand.PickScheduledSendTime]. It still has to clear the app's
   * scheduling prerequisites, just like a time picked straight from the menu.
   */
  fun onScheduledSendTimeSelected(scheduledTime: Long) {
    sendHudCommand(MediaSendFlowHudCommand.ConfirmScheduledSend(scheduledTime))
  }

  /**
   * The app's scheduling prerequisites are cleared, so the flow can carry on with the send scheduled for
   * [scheduledTime].
   */
  fun onScheduledSendConfirmed(scheduledTime: Long) {
    updateState { copy(scheduledTime = scheduledTime) }
    onNextClick()
  }

  //endregion

  //region Camera First Capture

  private fun addCameraFirstCapture(media: Media) {
    internalState.update { it.copy(cameraFirstCapture = media) }
    addMedia(setOf(media), focusNewlyAdded = true)
  }

  private fun removeCameraFirstCapture() {
    val capture = internalState.value.cameraFirstCapture ?: return
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

  //region Touch

  fun setTouchEnabled(isEnabled: Boolean) {
    updateState { copy(isTouchEnabled = isEnabled) }
  }

  //endregion

  //region Send

  /**
   * Advances out of the editor: either on to contact selection, or into the send itself.
   *
   * The add-to-group-story flow has no review step of its own, so the send is gated behind a confirmation naming the
   * group. Denying it leaves the editor exactly as it was — nothing is marked as sending until the user confirms.
   */
  private fun onNextClick() {
    val snapshot = state.value

    if (snapshot.isContactSelectionRequired) {
      backStack.goToSend()
      return
    }

    val recipientId = snapshot.recipientId
    if (snapshot.isAddToGroupStoryFlow && recipientId != null) {
      viewModelScope.launch {
        if (addToGroupStoryDialog.show(recipientId) == DialogResult.POSITIVE) {
          performSend()
        }
      }
    } else {
      performSend()
    }
  }

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
        is SendResult.ReadyToSend -> sendHudCommand(MediaSendFlowHudCommand.FinishWithResult(result.payload))
        is SendResult.Success -> sendHudCommand(MediaSendFlowHudCommand.FinishWithoutResult)

        is SendResult.UntrustedIdentity -> {
          updateState { copy(isSending = false) }
          sendHudCommand(MediaSendFlowHudCommand.ResolveUntrustedIdentities(result.recipientIds))
        }

        is SendResult.Error -> {
          Log.w(TAG, "Send failed: ${result.message}")
          updateState { copy(isSending = false) }
          sendHudCommand(MediaSendFlowHudCommand.CloseScreen)
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

  /**
   * A send that picks its destination from the forward sheet finishes the activity as soon as it hands us the
   * selection, so the send is usually still running when we get here. Cleaning up in that case would cancel the
   * uploads it depends on and sweep away any attachment it hasn't finished attributing to a message yet, so we leave
   * it alone and let the send do its own cleanup. Anything a failed send leaves behind is swept on next app start.
   */
  override fun onCleared() {
    if (internalState.value.isSent || internalState.value.isSending) {
      return
    }

    preUploadController.cancelAllUploads()
    preUploadController.deleteAbandonedAttachments()
  }

  /**
   * A flow that picks its destination mid-flight has nothing to attribute an upload to yet, so it waits for the send.
   */
  private fun MediaSendFlowState.shouldPreUpload(metered: Boolean): Boolean {
    return !metered && !isContactSelectionRequired
  }

  //endregion

  companion object {
    private val TAG = Log.tag(MediaSendFlowViewModel::class)

    private const val KEY_ARGS = "media_send_vm_args"
    private const val KEY_IDENTITY_CHANGES_SINCE = "media_send_vm_identity_changes_since"
    private const val KEY_STATE = "media_send_vm_state"
    private const val KEY_EDITED_VIDEO_URIS = "media_send_vm_edited_video_uris"
    private const val KEY_BACK_STACK = "media_send_vm_back_stack"

    /**
     * Trims a body the way the chat compose field does before a send, preserving spans like mentions and styling, and
     * collapses a whitespace-only body to null so it is indistinguishable from a body the user never typed.
     */
    private fun normalizeMessageBody(text: CharSequence?): CharSequence? {
      return text?.let { StringUtil.trimSequence(it) }?.takeIf { it.isNotEmpty() }
    }
  }

  /**
   * Factory that creates [MediaSendFlowViewModel] from a [SavedStateHandle] and static dependencies.
   *
   * On first creation, [args] and [identityChangesSince] are written into the [SavedStateHandle].
   * On process death restoration, the [SavedStateHandle] already contains the persisted values
   * and the constructor parameters are ignored.
   */
  class Factory(
    private val args: MediaSendFlowActivityContract.Args,
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

      return MediaSendFlowViewModel(
        savedStateHandle = savedStateHandle,
        repository = repository,
        preUploadController = PreUploadController(),
        isMeteredFlow = isMeteredFlow
      ) as T
    }
  }
}
