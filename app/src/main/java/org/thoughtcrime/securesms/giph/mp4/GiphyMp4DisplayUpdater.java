package org.thoughtcrime.securesms.giph.mp4;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Updates the position and size of a GiphyMp4VideoPlayer. For use with gestures which
 * move around the projectable areas videos should play back in.
 */
public interface GiphyMp4DisplayUpdater {
  void updateDisplay(@NonNull RecyclerView recyclerView, @NonNull GiphyMp4Playable holder);
}
