package org.thoughtcrime.securesms.components.voice;

import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.session.MediaSessionCompat;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ext.mediasession.TimelineQueueNavigator;

/**
 * Navigator to help support seek forward and back.
 */
final class VoiceNoteQueueNavigator extends TimelineQueueNavigator {
  private static final MediaDescriptionCompat EMPTY = new MediaDescriptionCompat.Builder().build();

  public VoiceNoteQueueNavigator(@NonNull MediaSessionCompat mediaSession) {
    super(mediaSession);
  }

  @Override
  public @NonNull MediaDescriptionCompat getMediaDescription(@NonNull Player player, int windowIndex) {
    MediaItem mediaItem = windowIndex >= 0 && windowIndex < player.getMediaItemCount() ? player.getMediaItemAt(windowIndex) : null;

    if (mediaItem == null || mediaItem.playbackProperties == null) {
      return EMPTY;
    }

    MediaDescriptionCompat mediaDescriptionCompat = (MediaDescriptionCompat) mediaItem.playbackProperties.tag;
    if (mediaDescriptionCompat == null) {
      return EMPTY;
    }

    return mediaDescriptionCompat;
  }
}
