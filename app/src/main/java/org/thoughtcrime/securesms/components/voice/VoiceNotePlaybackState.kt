package org.thoughtcrime.securesms.components.voice

import android.net.Uri
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Domain-level state object representing the state of the currently playing voice note.
 */
data class VoiceNotePlaybackState(
  /**
   * @return Uri of the currently playing AudioSlide
   */
  val uri: Uri,

  /**
   * @return The last known playhead position
   */
  val playheadPositionMillis: Long,

  /**
   * @return The track duration in ms
   */
  val trackDuration: Long,

  /**
   * @return true if we should reset the currently playing clip.
   */
  val isAutoReset: Boolean,

  /**
   * @return The current playback speed factor
   */
  val speed: Float,

  /**
   * @return Whether we are playing or paused
   */
  val isPlaying: Boolean,

  /**
   * @return Information about the type this clip represents.
   */
  val clipType: ClipType
) {
  companion object {
    @JvmField
    val NONE = VoiceNotePlaybackState(Uri.EMPTY, 0, 0, false, 1f, false, ClipType.Idle)
  }

  fun asPaused(): VoiceNotePlaybackState {
    return copy(isPlaying = false)
  }

  sealed class ClipType {
    data class Message(
      val messageId: Long,
      val senderId: RecipientId,
      val threadRecipientId: RecipientId,
      val messagePosition: Long,
      val threadId: Long,
      val timestamp: Long
    ) : ClipType()
    object Draft : ClipType()
    object Idle : ClipType()
  }
}
