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
import org.signal.core.models.parcelers.NullableCharSequenceParceler
import org.signal.core.util.ContentTypeUtil
import org.signal.mediasend.screens.edit.image.BrushWidths
import org.thoughtcrime.securesms.video.TranscodingConfig
import kotlin.time.Duration

/**
 * The collective state of the media send flow.
 *
 * Fully [Parcelable] for [SavedStateHandle] persistence — no separate serialization needed.
 */
@Parcelize
data class MediaSendFlowState(
  val isCameraFirst: Boolean = false,
  /**
   * Optional recipient identifier for single-recipient flows.
   */
  val recipientId: MediaRecipientId? = null,
  /**
   * Mode of operation — determines whether we return a result or send immediately.
   */
  val mode: MediaSendFlowActivityContract.Mode = MediaSendFlowActivityContract.Mode.SingleRecipient,
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
   * Whether media the user picked has just been refused. Read by whichever screen is picking, so a gesture that keeps
   * handing us items can stop on the first refusal, and cleared by it through
   * [MediaSendFlowViewModel.selectionRejectionShown].
   */
  val isSelectionRejected: @WriteWith<TransientInFlightFlagParceler> Boolean = false,

  val mediaConstraints: @WriteWith<TransientMediaConstraintsParceler> MediaConstraints = MediaSendDependencies.mediaSendRepository.getMediaConstraints(),

  val storiesEnabled: Boolean = CameraDependencies.isStoriesFeatureEnabled(),

  val storyMaxVideoDuration: Duration = MediaSendDependencies.mediaSendRepository.storyMaxVideoDuration,

  /**
   * The transcoding tiers that apply at [sentMediaQuality], which an estimate of a video's upload size is derived from.
   * Re-read whenever the quality changes.
   */
  val videoTranscodingTiers: @WriteWith<TransientVideoTranscodingTiersParceler> List<TranscodingConfig.QualityTier> =
    MediaSendDependencies.mediaSendRepository.getVideoTranscodingTiers(sentMediaQuality),

  /**
   * The image editor's per-tool brush widths. Seeded from storage and written back as the user adjusts them.
   */
  val brushWidths: BrushWidths = MediaSendDependencies.mediaSendRepository.brushWidths,

  /**
   * Whether the control for stripping a video's audio track is available.
   */
  val isMuteVideoAudioEnabled: Boolean = MediaConstraints.isMuteVideoAudioAvailable()
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

  /**
   * Derived from the quality the repository restores, so it is re-read alongside it rather than saved.
   */
  private object TransientVideoTranscodingTiersParceler : Parceler<List<TranscodingConfig.QualityTier>> {
    override fun create(parcel: Parcel): List<TranscodingConfig.QualityTier> {
      return MediaSendDependencies.mediaSendRepository.getVideoTranscodingTiers(MediaSendDependencies.mediaSendRepository.sentMediaQuality)
    }

    override fun List<TranscodingConfig.QualityTier>.write(parcel: Parcel, flags: Int) = Unit
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
