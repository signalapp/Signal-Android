package org.thoughtcrime.securesms.components.voice;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultControlDispatcher;
import com.google.android.exoplayer2.Player;

public class VoiceNoteNotificationControlDispatcher extends DefaultControlDispatcher {

  private final VoiceNoteQueueDataAdapter dataAdapter;

  public VoiceNoteNotificationControlDispatcher(@NonNull VoiceNoteQueueDataAdapter dataAdapter) {
    this.dataAdapter = dataAdapter;
  }

  @Override
  public boolean dispatchSeekTo(Player player, int windowIndex, long positionMs) {
    boolean isQueueToneIndex = windowIndex % 2 == 1;
    boolean isSeekingToStart = positionMs == C.TIME_UNSET;

    if (isQueueToneIndex && isSeekingToStart) {
      int nextVoiceNoteWindowIndex = player.getCurrentWindowIndex() < windowIndex ? windowIndex + 1 : windowIndex - 1;

      if (dataAdapter.size() <= nextVoiceNoteWindowIndex) {
        return super.dispatchSeekTo(player, windowIndex, positionMs);
      } else {
        return super.dispatchSeekTo(player, nextVoiceNoteWindowIndex, positionMs);
      }
    } else {
      return super.dispatchSeekTo(player, windowIndex, positionMs);
    }
  }
}
