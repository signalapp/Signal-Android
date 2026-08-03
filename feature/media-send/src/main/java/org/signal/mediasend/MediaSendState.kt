/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import org.signal.camera.CameraDependencies
import org.signal.core.models.media.Media
import org.signal.core.models.media.MediaFolder
import org.signal.core.models.parcelers.NullableCharSequenceParceler
import org.signal.core.util.ContentTypeUtil
import org.signal.mediasend.edit.image.BrushWidths
import org.signal.mediasend.edit.video.VideoTrimData
import kotlin.time.Duration

/**
 * The collective state of the media send flow.
 *
 * Fully [Parcelable] for [SavedStateHandle] persistence — no separate serialization needed.
 */
@Parcelize
data class MediaSendState(
  val isCameraFirst: Boolean = false,
  /**
   * Optional recipient identifier for single-recipient flows.
   */
  val recipientId: MediaRecipientId? = null,
  /**
   * Mode of operation — determines whether we return a result or send immediately.
   */
  val mode: MediaSendActivityContract.Mode = MediaSendActivityContract.Mode.SingleRecipient,
  val selectedMedia: List<Media> = emptyList(),
  /**
   * The currently focused/visible media item in the pager.
   */
  val focusedMedia: Media? = null,
  val isMeteredConnection: Boolean = false,
  val isPreUploadEnabled: Boolean = false,
  val sentMediaQuality: @WriteWith<TransientSentMediaQualityParceler> SentMediaQuality = MediaSendDependencies.mediaSendRepository.sentMediaQuality,
  /**
   * Per-media editor state keyed by URI (video trim data, image editor data, etc.).
   */
  val editorStateMap: Map<Uri, EditorState> = emptyMap(),
  /**
   * View-once toggle state. Cycles: OFF -> ONCE -> OFF.
   */
  val viewOnceToggleState: ViewOnceToggleState = ViewOnceToggleState.OFF,
  /**
   * Optional message/caption text to accompany the media. Retains mention and styling spans.
   */
  val message: @WriteWith<NullableCharSequenceParceler> CharSequence? = null,
  /**
   * If non-null, this media was the first capture from the camera and may be
   * removed if the user backs out of camera-first flow.
   */
  val cameraFirstCapture: Media? = null,
  /**
   * Whether touch interactions are enabled (disabled during animations/transitions).
   */
  val isTouchEnabled: Boolean = true,
  /**
   * Whether a send is currently in flight (prevents duplicate sends).
   */
  val isSending: Boolean = false,
  /**
   * Whether the media has been sent (prevents duplicate sends).
   */
  val isSent: Boolean = false,
  /**
   * Whether the focused media is currently being written out to the device's shared storage.
   */
  val isSavingMedia: @WriteWith<TransientInFlightFlagParceler> Boolean = false,
  /**
   * Whether this is a story send flow.
   */
  val isStory: Boolean = false,
  /**
   * Send type code (SMS vs Signal). Conventionally 0 == SignalMessageSendType.
   */
  val sendType: Int = 0,
  /**
   * Story send requirements based on current media selection.
   */
  val storySendRequirements: StorySendRequirements = StorySendRequirements.CAN_NOT_SEND,
  /**
   * Maximum number of media items that can be selected.
   */
  val maxSelection: Int = 32,
  /**
   * Whether contact selection is required (for choose-after-media flows).
   */
  val isContactSelectionRequired: Boolean = false,
  /**
   * Whether this is a reply to an existing message.
   */
  val isReply: Boolean = false,
  /**
   * Whether this is the "add to group story" flow.
   */
  val isAddToGroupStoryFlow: Boolean = false,
  /**
   * Additional recipients for multi-recipient sends.
   */
  val additionalRecipients: List<MediaSendRecipient> = emptyList(),
  /**
   * Scheduled send time (-1 for immediate).
   */
  val scheduledTime: Long = -1,

  /**
   * The [MediaFolder] list available on the system
   */
  val mediaFolders: @WriteWith<TransientMediaFolderListParceler> List<MediaFolder> = emptyList(),

  /**
   * The selected [MediaFolder] for which to display content in the Select screen
   */
  val selectedMediaFolder: @WriteWith<TransientMediaFolderParceler> MediaFolder? = null,

  /**
   * The media content for a given selected [MediaFolder]
   */
  val selectedMediaFolderItems: @WriteWith<TransientMediaListParceler> List<Media> = emptyList(),

  val mediaConstraints: @WriteWith<TransientMediaConstraintsParceler> MediaConstraints = MediaSendDependencies.mediaSendRepository.getMediaConstraints(),

  val storiesEnabled: Boolean = CameraDependencies.isStoriesFeatureEnabled(),

  val storyMaxVideoDuration: Duration = MediaSendDependencies.mediaSendRepository.storyMaxVideoDuration,

  /**
   * The image editor's per-tool brush widths. Seeded from storage and written back as the user adjusts them.
   */
  val brushWidths: BrushWidths = MediaSendDependencies.mediaSendRepository.brushWidths
) : Parcelable {

  /**
   * View-once only makes sense for a single, non-document attachment that isn't headed for a story.
   */
  val isViewOnceAvailable: Boolean
    get() = selectedMedia.size == 1 && !isStory && !ContentTypeUtil.isDocumentType(focusedMedia?.contentType)

  /**
   * Whether the current selection will actually be sent as view-once. [viewOnceToggleState] is sticky, so it can
   * outlive the conditions that allowed it to be set, and must always be read alongside [isViewOnceAvailable].
   */
  val isViewOnceEnabled: Boolean
    get() = isViewOnceAvailable && viewOnceToggleState == ViewOnceToggleState.ONCE

  fun getOrCreateVideoTrimData(uri: Uri): VideoTrimData {
    return (editorStateMap[uri] as? EditorState.VideoTrim)?.videoTrimData ?: VideoTrimData()
  }

  /**
   * No-op parcelers for fields that are re-loaded on init and should not
   * contribute to the saved-state bundle size.
   */
  private object TransientMediaFolderListParceler : Parceler<List<MediaFolder>> {
    override fun create(parcel: Parcel): List<MediaFolder> = emptyList()
    override fun List<MediaFolder>.write(parcel: Parcel, flags: Int) = Unit
  }

  private object TransientMediaFolderParceler : Parceler<MediaFolder?> {
    override fun create(parcel: Parcel): MediaFolder? = null
    override fun MediaFolder?.write(parcel: Parcel, flags: Int) = Unit
  }

  private object TransientMediaListParceler : Parceler<List<Media>> {
    override fun create(parcel: Parcel): List<Media> = emptyList()
    override fun List<Media>.write(parcel: Parcel, flags: Int) = Unit
  }

  /**
   * No-op parceler for flags tracking work that cannot outlive the process that started it.
   */
  private object TransientInFlightFlagParceler : Parceler<Boolean> {
    override fun create(parcel: Parcel): Boolean = false
    override fun Boolean.write(parcel: Parcel, flags: Int) = Unit
  }

  private object TransientMediaConstraintsParceler : Parceler<MediaConstraints> {
    override fun create(parcel: Parcel): MediaConstraints = MediaSendDependencies.mediaSendRepository.getMediaConstraints()
    override fun MediaConstraints.write(parcel: Parcel, flags: Int) = Unit
  }

  /**
   * The repository is the source of truth: every toggle writes through to it, so the value is re-read rather than saved.
   */
  private object TransientSentMediaQualityParceler : Parceler<SentMediaQuality> {
    override fun create(parcel: Parcel): SentMediaQuality = MediaSendDependencies.mediaSendRepository.sentMediaQuality
    override fun SentMediaQuality.write(parcel: Parcel, flags: Int) = Unit
  }

  enum class ViewOnceToggleState(val code: Int) {
    OFF(0),
    ONCE(1);

    fun next(): ViewOnceToggleState = when (this) {
      OFF -> ONCE
      ONCE -> OFF
    }

    companion object {
      fun fromCode(code: Int): ViewOnceToggleState = entries.firstOrNull { it.code == code } ?: OFF
    }
  }
}
