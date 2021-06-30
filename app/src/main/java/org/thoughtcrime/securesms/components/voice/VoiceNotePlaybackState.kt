package org.thoughtcrime.securesms.components.voice

import android.net.Uri

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
  val speed: Float

) {
  companion object {
    @JvmField
    val NONE = VoiceNotePlaybackState(Uri.EMPTY, 0, 0, false, 1f)
  }
}
