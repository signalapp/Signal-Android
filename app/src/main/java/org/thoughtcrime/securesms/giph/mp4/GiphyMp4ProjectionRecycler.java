package org.thoughtcrime.securesms.giph.mp4;

import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Logic for updating content and positioning of videos as the user scrolls the list of gifs.
 */
public final class GiphyMp4ProjectionRecycler implements GiphyMp4PlaybackController.Callback, GiphyMp4DisplayUpdater {

  private final List<GiphyMp4ProjectionPlayerHolder>        holders;
  private final SparseArray<GiphyMp4ProjectionPlayerHolder> playing;
  private final SparseArray<GiphyMp4ProjectionPlayerHolder> notPlaying;

  public GiphyMp4ProjectionRecycler(@NonNull List<GiphyMp4ProjectionPlayerHolder> holders) {
    this.holders    = holders;
    this.playing    = new SparseArray<>(holders.size());
    this.notPlaying = new SparseArray<>(holders.size());
  }

  @Override
  public void update(@NonNull RecyclerView recyclerView,
                     @NonNull List<GiphyMp4Playable> holders,
                     @NonNull Set<Integer> playbackSet)
  {
    stopAndReleaseAssignedVideos(playbackSet);

    for (final GiphyMp4Playable holder : holders) {
      if (playbackSet.contains(holder.getAdapterPosition())) {
        startPlayback(acquireHolderForPosition(holder.getAdapterPosition()), holder);
      } else {
        holder.showProjectionArea();
      }
    }

    for (final GiphyMp4Playable holder : holders) {
      updateDisplay(recyclerView, holder);
    }
  }

  @Override
  public void updateDisplay(@NonNull RecyclerView recyclerView, @NonNull GiphyMp4Playable holder) {
    GiphyMp4ProjectionPlayerHolder playerHolder = getCurrentHolder(holder.getAdapterPosition());
    if (playerHolder != null) {
      updateDisplay(recyclerView, playerHolder, holder);
    }
  }

  public @Nullable View getVideoPlayerAtAdapterPosition(int adapterPosition) {
    GiphyMp4ProjectionPlayerHolder holder = getCurrentHolder(adapterPosition);

    if (holder != null) return holder.getContainer();
    else                return null;
  }

  private void stopAndReleaseAssignedVideos(@NonNull Set<Integer> playbackSet) {
    List<Integer> markedForDeletion = new ArrayList<>(playing.size());
    for (int i = 0; i < playing.size(); i++) {
      if (!playbackSet.contains(playing.keyAt(i))) {
        notPlaying.put(playing.keyAt(i), playing.valueAt(i));
        playing.valueAt(i).clearMedia();
        playing.valueAt(i).setOnPlaybackReady(null);
        markedForDeletion.add(playing.keyAt(i));
      }
    }

    for (final Integer key : markedForDeletion) {
      playing.remove(key);
    }
  }

  private void updateDisplay(@NonNull RecyclerView recyclerView, @NonNull GiphyMp4ProjectionPlayerHolder holder, @NonNull GiphyMp4Playable giphyMp4Playable) {
    GiphyMp4Projection projection = giphyMp4Playable.getProjection(recyclerView);

    holder.getContainer().setX(projection.getX());
    holder.getContainer().setY(projection.getY());

    ViewGroup.LayoutParams params = holder.getContainer().getLayoutParams();
    if (params.width != projection.getWidth() || params.height != projection.getHeight()) {
      params.width  = projection.getWidth();
      params.height = projection.getHeight();
      holder.getContainer().setLayoutParams(params);
    }

    holder.setCornerMask(projection.getCornerMask());
  }

  private void startPlayback(@NonNull GiphyMp4ProjectionPlayerHolder holder, @NonNull GiphyMp4Playable giphyMp4Playable) {
    if (!Objects.equals(holder.getMediaSource(), giphyMp4Playable.getMediaSource())) {
      holder.setOnPlaybackReady(null);
      giphyMp4Playable.showProjectionArea();

      holder.setOnPlaybackReady(giphyMp4Playable::hideProjectionArea);
      holder.playContent(giphyMp4Playable.getMediaSource(), giphyMp4Playable.getPlaybackPolicyEnforcer());
    }
  }

  private @Nullable GiphyMp4ProjectionPlayerHolder getCurrentHolder(int adapterPosition) {
    if (playing.get(adapterPosition) != null) {
      return playing.get(adapterPosition);
    } else if (notPlaying.get(adapterPosition) != null) {
      return notPlaying.get(adapterPosition);
    } else {
      return null;
    }
  }

  private @NonNull GiphyMp4ProjectionPlayerHolder acquireHolderForPosition(int adapterPosition) {
    GiphyMp4ProjectionPlayerHolder holder = playing.get(adapterPosition);
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
