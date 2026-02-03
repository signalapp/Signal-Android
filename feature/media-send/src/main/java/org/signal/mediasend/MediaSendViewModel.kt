/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import kotlinx.coroutines.channels.Channel
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
import org.signal.core.models.media.Media
import org.signal.core.models.media.MediaFolder
import org.signal.core.util.ContentTypeUtil
import org.signal.core.util.StringUtil
import org.signal.imageeditor.core.model.EditorElement
import org.signal.imageeditor.core.model.EditorModel
import org.signal.imageeditor.core.renderers.UriGlideRenderer
import org.signal.mediasend.preupload.PreUploadManager
import java.util.Collections
import kotlin.time.Duration.Companion.milliseconds

/**
 * Configuration-survivable state manager for the media send flow.
 *
 * Uses [SavedStateHandle] for automatic state persistence across process death.
 * [MediaSendState] is fully [Parcelable] and saved directly as a single key.
 */
class MediaSendViewModel(
  private val args: MediaSendActivityContract.Args,
  private val identityChangesSince: Long,
  isMeteredFlow: Flow<Boolean>,
  private val savedStateHandle: SavedStateHandle,
  private val repository: MediaSendRepository,
  private val preUploadManager: PreUploadManager
) : ViewModel(), MediaSendCallback {

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
          selectedMedia = if (it.selectedMediaFolder in folders) it.selectedMediaFolderItems else emptyList()
        )
      }
    }
  }

  override fun onFolderClick(mediaFolder: MediaFolder?) {
    viewModelScope.launch {
      if (mediaFolder != null) {
        val media = repository.getMedia(mediaFolder.bucketId)
        internalState.update { it.copy(selectedMediaFolder = mediaFolder, selectedMediaFolderItems = media) }
      } else {
        internalState.update { it.copy(selectedMediaFolder = null, selectedMediaFolderItems = emptyList()) }
      }
    }
  }

  override fun onMediaClick(media: Media) {
    if (media.uri in internalState.value.selectedMedia.map { it.uri }) {
      removeMedia(media)
    } else {
      addMedia(media)
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
        val maxVideoDurationUs = getMaxVideoDurationUs()
        val initializedVideoEditorStates = filterResult.filteredMedia
          .filterNot { snapshot.editorStateMap.containsKey(it.uri) }
          .filter { isNonGifVideo(it) }
          .associate { video ->
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
          copy(
            selectedMedia = filterResult.filteredMedia,
            focusedMedia = focusedMedia ?: filterResult.filteredMedia.firstOrNull(),
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
    preUploadManager.cancelUpload(media)
    preUploadManager.updateDisplayOrder(newSelection)
  }

  /**
   * Applies updates to selected media (old -> new).
   */
  fun applyMediaUpdates(oldToNew: Map<Media, Media>) {
    if (oldToNew.isEmpty()) return

    val snapshot = state.value
    val updatedSelection = snapshot.selectedMedia.map { oldToNew[it] ?: it }
    updateState { copy(selectedMedia = updatedSelection) }

    preUploadManager.applyMediaUpdates(oldToNew, snapshot.recipientId)
    preUploadManager.updateCaptions(updatedSelection)
    preUploadManager.updateDisplayOrder(updatedSelection)
  }

  /**
   * Sets the current ordering of selected media.
   */
  fun setDisplayOrder(mediaInOrder: List<Media>) {
    updateState { copy(selectedMedia = mediaInOrder) }
    preUploadManager.updateDisplayOrder(mediaInOrder)
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

    preUploadManager.startUpload(filteredPreUploadMedia, snapshot.recipientId)
    preUploadManager.updateCaptions(snapshot.selectedMedia)
    preUploadManager.updateDisplayOrder(snapshot.selectedMedia)
  }

  //endregion

  //region Quality

  /**
   * Sets the sent media quality.
   *
   * Cancels all pre-uploads and re-initializes video trim data.
   */
  fun setSentMediaQuality(sentMediaQuality: Int) {
    val snapshot = state.value
    if (snapshot.sentMediaQuality == sentMediaQuality) return

    updateState { copy(sentMediaQuality = sentMediaQuality, isPreUploadEnabled = false) }
    preUploadManager.cancelAllUploads()

    // Re-clamp video durations based on new quality
    val maxVideoDurationUs = getMaxVideoDurationUs()
    snapshot.selectedMedia.forEach { mediaItem ->
      if (isNonGifVideo(mediaItem) && repository.isVideoTranscodeAvailable()) {
        val existingData = snapshot.editorStateMap[mediaItem.uri] as? EditorState.VideoTrim
        if (existingData != null) {
          onEditVideoDuration(
            totalDurationUs = existingData.totalInputDurationUs,
            startTimeUs = existingData.startTimeUs,
            endTimeUs = existingData.endTimeUs,
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
  override fun onVideoEdited(uri: Uri, isEdited: Boolean) {
    if (!isEdited) return
    if (!editedVideoUris.add(uri)) return

    // Persist the updated set
    savedStateHandle[KEY_EDITED_VIDEO_URIS] = ArrayList(editedVideoUris)

    val media = state.value.selectedMedia.firstOrNull { it.uri == uri } ?: return
    preUploadManager.cancelUpload(media)
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
      ?: EditorState.VideoTrim(totalInputDurationUs = totalDurationUs)

    val clampedStartTime = maxOf(startTimeUs, 0)
    val unedited = !existingData.isDurationEdited
    val durationEdited = clampedStartTime > 0 || endTimeUs < totalDurationUs
    val isEntireDuration = startTimeUs == 0L && endTimeUs == totalDurationUs
    val endMoved = !isEntireDuration && existingData.endTimeUs != endTimeUs
    val maxVideoDurationUs = getMaxVideoDurationUs()
    val preserveStartTime = unedited || !endMoved

    val newData = EditorState.VideoTrim(
      isDurationEdited = durationEdited,
      totalInputDurationUs = totalDurationUs,
      startTimeUs = clampedStartTime,
      endTimeUs = endTimeUs
    ).clampToMaxDuration(maxVideoDurationUs, preserveStartTime)

    // Cancel upload on first edit
    if (unedited && durationEdited) {
      val media = snapshot.selectedMedia.firstOrNull { it.uri == uri }
      if (media != null) {
        preUploadManager.cancelUpload(media)
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

  private fun getMaxVideoDurationUs(): Long {
    val snapshot = state.value
    return repository.getMaxVideoDurationUs(
      quality = snapshot.sentMediaQuality,
      maxFileSizeBytes = repository.getVideoMaxSizeBytes()
    )
  }

  //endregion

  //region Page/Focus Management

  override fun setFocusedMedia(media: Media) {
    updateState { copy(focusedMedia = media) }
  }

  override fun onPageChanged(position: Int) {
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
    preUploadManager.updateDisplayOrder(internalState.value.selectedMedia)
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

  override fun onMessageChanged(text: CharSequence?) {
    setMessage(text?.toString())
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

  fun addCameraFirstCapture(media: Media) {
    internalState.update { it.copy(cameraFirstCapture = media) }
    addMedia(media)
  }

  fun removeCameraFirstCapture() {
    val capture = internalState.value.cameraFirstCapture ?: return
    setSuppressEmptyError(true)
    removeMedia(capture)
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

  //region HUD Commands

  fun sendCommand(command: HudCommand) {
    hudCommandChannel.trySend(command)
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
    preUploadManager.cancelAllUploads()
    preUploadManager.deleteAbandonedAttachments()
  }

  private fun shouldPreUpload(metered: Boolean): Boolean = !metered

  //endregion

  //region Factory

  class Factory(
    private val context: Context,
    private val args: MediaSendActivityContract.Args,
    private val identityChangesSince: Long = System.currentTimeMillis(),
    private val isMeteredFlow: Flow<Boolean>,
    private val repository: MediaSendRepository,
    private val preUploadCallback: PreUploadManager.Callback
  ) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
      val savedStateHandle = extras.createSavedStateHandle()
      val manager = PreUploadManager(context.applicationContext, preUploadCallback)

      return MediaSendViewModel(
        args = args,
        identityChangesSince = identityChangesSince,
        isMeteredFlow = isMeteredFlow,
        savedStateHandle = savedStateHandle,
        repository = repository,
        preUploadManager = manager
      ) as T
    }
  }

  //endregion

  companion object {
    private const val KEY_STATE = "media_send_vm_state"
    private const val KEY_EDITED_VIDEO_URIS = "media_send_vm_edited_video_uris"
  }
}
