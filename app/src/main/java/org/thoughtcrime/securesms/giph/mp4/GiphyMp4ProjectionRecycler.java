package org.thoughtcrime.securesms.giph.mp4;

import android.util.SparseArray;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.util.Projection;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Logic for updating content and positioning of videos as the user scrolls the list of gifs.
 */
public final class GiphyMp4ProjectionRecycler implements GiphyMp4PlaybackController.Callback {

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
        startPlayback(recyclerView, acquireHolderForPosition(holder.getAdapterPosition()), holder);
      } else {
        holder.showProjectionArea();
      }
    }

    for (final GiphyMp4Playable holder : holders) {
      updateVideoDisplayPositionAndSize(recyclerView, holder);
    }
  }

  @Override
  public void updateVideoDisplayPositionAndSize(@NonNull RecyclerView recyclerView, @NonNull GiphyMp4Playable holder) {
    GiphyMp4ProjectionPlayerHolder playerHolder = getCurrentHolder(holder.getAdapterPosition());
    if (playerHolder != null) {
      updateVideoDisplayPositionAndSize(recyclerView, playerHolder, holder);
    }
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

    for (int i = 0; i < notPlaying.size(); i++) {
      notPlaying.valueAt(i).hide();
    }
  }

  private void updateVideoDisplayPositionAndSize(@NonNull RecyclerView recyclerView,
                                                 @NonNull GiphyMp4ProjectionPlayerHolder holder,
                                                 @NonNull GiphyMp4Playable giphyMp4Playable)
  {
    if (!giphyMp4Playable.canPlayContent()) {
      return;
    }

    Projection projection = giphyMp4Playable.getGiphyMp4PlayableProjection(recyclerView);

    holder.getContainer().setX(projection.getX());
    holder.getContainer().setY(projection.getY() + recyclerView.getTranslationY());

    ViewGroup.LayoutParams params = holder.getContainer().getLayoutParams();
    if (params.width != projection.getWidth() || params.height != projection.getHeight()) {
      params.width  = projection.getWidth();
      params.height = projection.getHeight();
      holder.getContainer().setLayoutParams(params);
    }

    holder.setCorners(projection.getCorners());

    projection.release();
  }

  private void startPlayback(@NonNull RecyclerView parent, @NonNull GiphyMp4ProjectionPlayerHolder holder, @NonNull GiphyMp4Playable giphyMp4Playable) {
    if (!Objects.equals(holder.getMediaItem(), giphyMp4Playable.getMediaItem())) {
      holder.setOnPlaybackReady(null);
      giphyMp4Playable.showProjectionArea();

      holder.show();
      holder.setOnPlaybackReady(() -> {
        giphyMp4Playable.hideProjectionArea();
        parent.invalidate();
      });
      holder.playContent(giphyMp4Playable.getMediaItem(), giphyMp4Playable.getPlaybackPolicyEnforcer());
    } else {
      giphyMp4Playable.showProjectionArea();

      holder.setOnPlaybackReady(() -> {
        holder.show();
        giphyMp4Playable.hideProjectionArea();
        parent.invalidate();
      });
    }
  }

  public @Nullable GiphyMp4ProjectionPlayerHolder getCurrentHolder(int adapterPosition) {
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
