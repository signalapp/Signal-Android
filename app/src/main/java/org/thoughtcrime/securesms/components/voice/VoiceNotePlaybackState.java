package org.thoughtcrime.securesms.components.voice;

import android.net.Uri;

import androidx.annotation.NonNull;

/**
 * Domain-level state object representing the state of the currently playing voice note.
 */
public class VoiceNotePlaybackState {

  public static final VoiceNotePlaybackState NONE = new VoiceNotePlaybackState(Uri.EMPTY, 0);

  private final Uri  uri;
  private final long playheadPositionMillis;

  public VoiceNotePlaybackState(@NonNull Uri uri, long playheadPositionMillis) {
    this.uri                    = uri;
    this.playheadPositionMillis = playheadPositionMillis;
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
}
