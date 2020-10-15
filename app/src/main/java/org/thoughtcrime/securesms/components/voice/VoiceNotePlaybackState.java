package org.thoughtcrime.securesms.components.voice;

import android.net.Uri;

import androidx.annotation.NonNull;

/**
 * Domain-level state object representing the state of the currently playing voice note.
 */
public class VoiceNotePlaybackState {

  public static final VoiceNotePlaybackState NONE = new VoiceNotePlaybackState(Uri.EMPTY, 0, false);

  private final Uri     uri;
  private final long    playheadPositionMillis;
  private final boolean autoReset;

  public VoiceNotePlaybackState(@NonNull Uri uri, long playheadPositionMillis, boolean autoReset) {
    this.uri                    = uri;
    this.playheadPositionMillis = playheadPositionMillis;
    this.autoReset              = autoReset;
  }

  /**
   * @return Uri of the currently playing AudioSlide
   */
  public Uri getUri() {
    return uri;
  }

  /**
   * @return The last known playhead position
   */
  public long getPlayheadPositionMillis() {
    return playheadPositionMillis;
  }

  /**
   * @return true if we should reset the currently playing clip.
   */
  public boolean isAutoReset() {
    return autoReset;
  }
}
