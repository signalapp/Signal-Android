/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit

import android.net.Uri
import org.signal.core.models.media.Media
import org.signal.mediasend.EditorState
import org.signal.mediasend.MediaRecipientId
import org.signal.mediasend.MediaSendFlowState
import org.signal.mediasend.SentMediaQuality
import org.signal.mediasend.screens.edit.video.VideoTrimData
import org.thoughtcrime.securesms.video.TranscodingConfig

/**
 * What the edit screen renders. Everything it edits belongs to the flow, so all of it is a copy kept current through
 * [MediaEditScreenEvents.ParentStateChanged] -- apart from [isSavingMedia], which is this screen's own work.
 */
internal data class MediaEditState(
  val selectedMedia: List<Media> = emptyList(),
  val focusedMedia: Media? = null,
  val editorStateMap: Map<Uri, EditorState> = emptyMap(),
  /** The camera-first capture, which backing out of the editor discards rather than keeps. */
  val cameraFirstCapture: Media? = null,
  /** The single recipient this media is headed to, or null when the destination is still to be chosen. */
  val recipientId: MediaRecipientId? = null,
  val message: CharSequence? = null,
  val sentMediaQuality: SentMediaQuality = SentMediaQuality.STANDARD,
  val videoTranscodingTiers: List<TranscodingConfig.QualityTier> = emptyList(),
  val isStory: Boolean = false,
  val isReply: Boolean = false,
  val isSending: Boolean = false,
  val isTouchEnabled: Boolean = true,
  val isMuteVideoAudioEnabled: Boolean = false,
  val isViewOnceAvailable: Boolean = false,
  val isViewOnceEnabled: Boolean = false,
  /** Whether the focused image is currently being written out to shared storage. */
  val isSavingMedia: Boolean = false
) {

  val focusedEditorState: EditorState?
    get() = focusedMedia?.uri?.let { editorStateMap[it] }

  /**
   * Whether the selection is nothing but the capture that opened this editor. Backing out then discards it and returns
   * to the camera, rather than leaving an editor with nothing to edit behind.
   */
  val isOnlyCameraFirstCapture: Boolean
    get() = cameraFirstCapture != null && selectedMedia.size == 1 && selectedMedia.firstOrNull() == cameraFirstCapture

  fun getOrCreateVideoTrimData(uri: Uri): VideoTrimData {
    return (editorStateMap[uri] as? EditorState.VideoTrim)?.videoTrimData ?: VideoTrimData()
  }

  /** Everything the flow reports, merged in. [isSavingMedia] is left alone: the flow knows nothing about it. */
  fun withParentState(parentState: MediaSendFlowState): MediaEditState = copy(
    selectedMedia = parentState.selectedMedia,
    focusedMedia = parentState.focusedMedia,
    editorStateMap = parentState.editorStateMap,
    cameraFirstCapture = parentState.cameraFirstCapture,
    recipientId = parentState.recipientId,
    message = parentState.message,
    sentMediaQuality = parentState.sentMediaQuality,
    videoTranscodingTiers = parentState.videoTranscodingTiers,
    isStory = parentState.isStory,
    isReply = parentState.isReply,
    isSending = parentState.isSending,
    isTouchEnabled = parentState.isTouchEnabled,
    isMuteVideoAudioEnabled = parentState.isMuteVideoAudioEnabled,
    isViewOnceAvailable = parentState.isViewOnceAvailable,
    isViewOnceEnabled = parentState.isViewOnceEnabled
  )
}
