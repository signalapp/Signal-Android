package org.thoughtcrime.securesms.giph.mp4;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Objects;

/**
 * Object describing the range of adapter positions for which playback should begin.
 */
final class GiphyMp4PlaybackRange {
  private final int startPosition;
  private final int endPosition;

  GiphyMp4PlaybackRange(int startPosition, int endPosition) {
    this.startPosition = startPosition;
    this.endPosition   = endPosition;
  }

  boolean shouldPlayVideo(int adapterPosition) {
    if (adapterPosition == RecyclerView.NO_POSITION) return false;

    return this.startPosition <= adapterPosition && this.endPosition > adapterPosition;
  }

  @Override
  public @NonNull String toString() {
    return "PlaybackRange{" +
           "startPosition=" + startPosition +
           ", endPosition=" + endPosition +
           '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final GiphyMp4PlaybackRange that = (GiphyMp4PlaybackRange) o;
    return startPosition == that.startPosition &&
           endPosition == that.endPosition;
  }

  @Override public int hashCode() {
    return Objects.hash(startPosition, endPosition);
  }
}
