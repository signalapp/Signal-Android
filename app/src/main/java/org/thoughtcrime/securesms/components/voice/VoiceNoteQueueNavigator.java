package org.thoughtcrime.securesms.components.voice;

import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.session.MediaSessionCompat;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ext.mediasession.TimelineQueueEditor;
import com.google.android.exoplayer2.ext.mediasession.TimelineQueueNavigator;

/**
 * Navigator to help support seek forward and back.
 */
final class VoiceNoteQueueNavigator extends TimelineQueueNavigator {

  private final TimelineQueueEditor.QueueDataAdapter queueDataAdapter;

  public VoiceNoteQueueNavigator(@NonNull MediaSessionCompat mediaSession, @NonNull TimelineQueueEditor.QueueDataAdapter queueDataAdapter) {
    super(mediaSession);
    this.queueDataAdapter = queueDataAdapter;
  }

  @Override
  public MediaDescriptionCompat getMediaDescription(Player player, int windowIndex) {
    return queueDataAdapter.getMediaDescription(windowIndex);
  }
}
