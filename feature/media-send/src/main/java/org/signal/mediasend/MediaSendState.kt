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
import org.signal.core.models.media.Media
import org.signal.core.models.media.MediaFolder

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
  /**
   * Int code to avoid depending on app-layer enums. Conventionally 0 == STANDARD.
   */
  val sentMediaQuality: Int = 0,
  /**
   * Per-media editor state keyed by URI (video trim data, image editor data, etc.).
   */
  val editorStateMap: Map<Uri, EditorState> = emptyMap(),
  /**
   * View-once toggle state. Cycles: OFF -> ONCE -> OFF.
   */
  val viewOnceToggleState: ViewOnceToggleState = ViewOnceToggleState.OFF,
  /**
   * Optional message/caption text to accompany the media.
   */
  val message: String? = null,
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
   * When true, suppresses the "no items" error when selection becomes empty.
   * Used during camera-first flow exit.
   */
  val suppressEmptyError: Boolean = false,
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
   * Additional recipient IDs for multi-recipient sends.
   */
  val additionalRecipientIds: List<MediaRecipientId> = emptyList(),
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
  val selectedMediaFolderItems: @WriteWith<TransientMediaListParceler> List<Media> = emptyList()
) : Parcelable {

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
