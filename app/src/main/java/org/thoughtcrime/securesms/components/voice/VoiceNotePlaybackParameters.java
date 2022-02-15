package org.thoughtcrime.securesms.components.voice;

import android.os.Bundle;
import android.support.v4.media.session.MediaSessionCompat;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.PlaybackParameters;

import org.signal.core.util.logging.Log;

public final class VoiceNotePlaybackParameters {

  private final MediaSessionCompat mediaSessionCompat;

  VoiceNotePlaybackParameters(@NonNull MediaSessionCompat mediaSessionCompat) {
    this.mediaSessionCompat = mediaSessionCompat;
  }

  @NonNull PlaybackParameters getParameters() {
    float speed = getSpeed();
    return new PlaybackParameters(speed);
  }

  void setSpeed(float speed) {
    Bundle extras = new Bundle();
    extras.putFloat(VoiceNotePlaybackService.ACTION_NEXT_PLAYBACK_SPEED, speed);

    mediaSessionCompat.setExtras(extras);
  }

  private float getSpeed() {
    Bundle extras = mediaSessionCompat.getController().getExtras();

    if (extras == null) {
      return 1f;
    } else {
      return extras.getFloat(VoiceNotePlaybackService.ACTION_NEXT_PLAYBACK_SPEED, 1f);
    }
  }
}
