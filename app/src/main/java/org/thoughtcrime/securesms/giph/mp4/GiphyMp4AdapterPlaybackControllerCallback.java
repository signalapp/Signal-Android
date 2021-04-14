package org.thoughtcrime.securesms.giph.mp4;

import android.util.SparseArray;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Logic for updating content and positioning of videos as the user scrolls the list of gifs.
 */
final class GiphyMp4AdapterPlaybackControllerCallback implements GiphyMp4AdapterPlaybackController.Callback {

  private final List<GiphyMp4PlayerHolder>        holders;
  private final SparseArray<GiphyMp4PlayerHolder> playing;
  private final SparseArray<GiphyMp4PlayerHolder> notPlaying;

  GiphyMp4AdapterPlaybackControllerCallback(@NonNull List<GiphyMp4PlayerHolder> holders) {
    this.holders    = holders;
    this.playing    = new SparseArray<>(holders.size());
    this.notPlaying = new SparseArray<>(holders.size());
  }

  @Override public void update(@NonNull List<GiphyMp4ViewHolder> holders,
                               @NonNull GiphyMp4PlaybackRange range)
  {
    stopAndReleaseAssignedVideos(range);

    for (final GiphyMp4ViewHolder holder : holders) {
      if (range.shouldPlayVideo(holder.getAdapterPosition())) {
        startPlayback(acquireHolderForPosition(holder.getAdapterPosition()), holder);
      } else {
        holder.show();
      }
    }

    for (final GiphyMp4ViewHolder holder : holders) {
      GiphyMp4PlayerHolder playerHolder = getCurrentHolder(holder.getAdapterPosition());
      if (playerHolder != null) {
        updateDisplay(playerHolder, holder);
      }
    }
  }

  private void stopAndReleaseAssignedVideos(@NonNull GiphyMp4PlaybackRange playbackRange) {
    List<Integer> markedForDeletion = new ArrayList<>(playing.size());
    for (int i = 0; i < playing.size(); i++) {
      if (!playbackRange.shouldPlayVideo(playing.keyAt(i))) {
        notPlaying.put(playing.keyAt(i), playing.valueAt(i));
        playing.valueAt(i).setMediaSource(null);
        playing.valueAt(i).setOnPlaybackReady(null);
        markedForDeletion.add(playing.keyAt(i));
      }
    }

    for (final Integer key : markedForDeletion) {
      playing.remove(key);
    }
  }

  private void updateDisplay(@NonNull GiphyMp4PlayerHolder holder, @NonNull GiphyMp4ViewHolder giphyMp4ViewHolder) {
    holder.getContainer().setX(giphyMp4ViewHolder.itemView.getX());
    holder.getContainer().setY(giphyMp4ViewHolder.itemView.getY());

    ViewGroup.LayoutParams params = holder.getContainer().getLayoutParams();
    if (params.width != giphyMp4ViewHolder.itemView.getWidth() || params.height != giphyMp4ViewHolder.itemView.getHeight()) {
      params.width  = giphyMp4ViewHolder.itemView.getWidth();
      params.height = giphyMp4ViewHolder.itemView.getHeight();
      holder.getContainer().setLayoutParams(params);
    }
  }

  private void startPlayback(@NonNull GiphyMp4PlayerHolder holder, @NonNull GiphyMp4ViewHolder giphyMp4ViewHolder) {
    if (!Objects.equals(holder.getMediaSource(), giphyMp4ViewHolder.getMediaSource())) {
      holder.setOnPlaybackReady(null);
      giphyMp4ViewHolder.show();

      holder.setOnPlaybackReady(giphyMp4ViewHolder::hide);
      holder.setMediaSource(giphyMp4ViewHolder.getMediaSource());
    }
  }

  private @Nullable GiphyMp4PlayerHolder getCurrentHolder(int adapterPosition) {
    if (playing.get(adapterPosition) != null) {
      return playing.get(adapterPosition);
    } else if (notPlaying.get(adapterPosition) != null) {
      return notPlaying.get(adapterPosition);
    } else {
      return null;
    }
  }

  private @NonNull GiphyMp4PlayerHolder acquireHolderForPosition(int adapterPosition) {
    GiphyMp4PlayerHolder holder = playing.get(adapterPosition);
    if (holder == null) {
      if (notPlaying.size() != 0) {
        holder = notPlaying.get(adapterPosition);
        if (holder == null) {
          int key = notPlaying.keyAt(0);
          holder = Objects.requireNonNull(notPlaying.get(key));
          notPlaying.remove(key);
        } else {
          notPlaying.remove(adapterPosition);
        }
      } else {
        holder = holders.remove(0);
      }
      playing.put(adapterPosition, holder);
    }
    return holder;
  }
}
