package org.thoughtcrime.securesms.giph.mp4;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import java.util.LinkedList;
import java.util.List;

/**
 * Controls playback of gifs in a {@link GiphyMp4Adapter}. The maximum number of gifs that will play back at any one
 * time is determined by the passed parameter, and the exact gifs that play back is algorithmically determined, starting
 * with the center-most gifs.
 * <p>
 * This algorithm is devised to play back only those gifs which the user is most likely looking at.
 */
final class GiphyMp4AdapterPlaybackController extends RecyclerView.OnScrollListener implements View.OnLayoutChangeListener {

  private final int      maxSimultaneousPlayback;
  private final Callback callback;

  private GiphyMp4AdapterPlaybackController(@NonNull Callback callback, int maxSimultaneousPlayback) {
    this.maxSimultaneousPlayback = maxSimultaneousPlayback;
    this.callback                = callback;
  }

  public static void attach(@NonNull RecyclerView recyclerView, @NonNull Callback callback, int maxSimultaneousPlayback) {
    GiphyMp4AdapterPlaybackController controller = new GiphyMp4AdapterPlaybackController(callback, maxSimultaneousPlayback);

    recyclerView.addOnScrollListener(controller);
    recyclerView.addOnLayoutChangeListener(controller);
  }

  @Override
  public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
    enqueuePlaybackUpdate(recyclerView);
  }

  @Override
  public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
    RecyclerView recyclerView = (RecyclerView) v;
    enqueuePlaybackUpdate(recyclerView);
  }

  private void enqueuePlaybackUpdate(@NonNull RecyclerView recyclerView) {
    performPlaybackUpdate(recyclerView);
  }

  private void performPlaybackUpdate(@NonNull RecyclerView recyclerView) {
    RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();

    if (layoutManager == null) {
      return;
    }

    int[] firstVisiblePositions = findFirstVisibleItemPositions(layoutManager);
    int[] lastVisiblePositions  = findLastVisibleItemPositions(layoutManager);

    GiphyMp4PlaybackRange playbackRange = getPlaybackRangeForMaximumDistance(firstVisiblePositions, lastVisiblePositions);

    if (playbackRange != null) {
      List<GiphyMp4ViewHolder> holders = new LinkedList<>();

      for (int i = 0; i < recyclerView.getChildCount(); i++) {
        GiphyMp4ViewHolder viewHolder = (GiphyMp4ViewHolder) recyclerView.getChildViewHolder(recyclerView.getChildAt(i));
        holders.add(viewHolder);
      }

      callback.update(holders, playbackRange);
    }
  }

  private @Nullable GiphyMp4PlaybackRange getPlaybackRangeForMaximumDistance(int[] firstVisiblePositions, int[] lastVisiblePositions) {
    int firstVisiblePosition = Integer.MAX_VALUE;
    int lastVisiblePosition  = Integer.MIN_VALUE;

    for (int i = 0; i < firstVisiblePositions.length; i++) {
      firstVisiblePosition = Math.min(firstVisiblePosition, firstVisiblePositions[i]);
      lastVisiblePosition  = Math.max(lastVisiblePosition, lastVisiblePositions[i]);
    }

    return getPlaybackRange(firstVisiblePosition, lastVisiblePosition);
  }

  private @Nullable GiphyMp4PlaybackRange getPlaybackRange(int firstVisiblePosition, int lastVisiblePosition) {
    int distance = lastVisiblePosition - firstVisiblePosition;

    if (maxSimultaneousPlayback == 0) {
      return null;
    }

    if (distance <= maxSimultaneousPlayback) {
      return new GiphyMp4PlaybackRange(firstVisiblePosition, lastVisiblePosition);
    } else {
      int center = (distance / 2) + firstVisiblePosition;
      if (maxSimultaneousPlayback == 1) {
        return new GiphyMp4PlaybackRange(center, center);
      } else {
        int first = Math.max(center - maxSimultaneousPlayback / 2, firstVisiblePosition);
        int last  = Math.min(first + maxSimultaneousPlayback, lastVisiblePosition);
        return new GiphyMp4PlaybackRange(first, last);
      }
    }
  }

  private static int[] findFirstVisibleItemPositions(@NonNull RecyclerView.LayoutManager layoutManager) {
    if (layoutManager instanceof LinearLayoutManager) {
      return new int[]{((LinearLayoutManager) layoutManager).findFirstVisibleItemPosition()};
    } else if (layoutManager instanceof StaggeredGridLayoutManager) {
      return ((StaggeredGridLayoutManager) layoutManager).findFirstVisibleItemPositions(null);
    } else {
      throw new IllegalStateException("Unsupported type: " + layoutManager.getClass().getName());
    }
  }

  private static int[] findLastVisibleItemPositions(@NonNull RecyclerView.LayoutManager layoutManager) {
    if (layoutManager instanceof LinearLayoutManager) {
      return new int[]{((LinearLayoutManager) layoutManager).findLastVisibleItemPosition()};
    } else if (layoutManager instanceof StaggeredGridLayoutManager) {
      return ((StaggeredGridLayoutManager) layoutManager).findLastVisibleItemPositions(null);
    } else {
      throw new IllegalStateException("Unsupported type: " + layoutManager.getClass().getName());
    }
  }

  interface Callback {
    void update(@NonNull List<GiphyMp4ViewHolder> holders, @NonNull GiphyMp4PlaybackRange range);
  }
}
