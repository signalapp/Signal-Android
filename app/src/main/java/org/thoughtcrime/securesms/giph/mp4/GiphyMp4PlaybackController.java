package org.thoughtcrime.securesms.giph.mp4;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Controls playback of gifs in a {@link RecyclerView}. The maximum number of gifs that will play back at any one
 * time is determined by the passed parameter, and the exact gifs that play back is algorithmically determined, starting
 * with the center-most gifs.
 * <p>
 * This algorithm is devised to play back only those gifs which the user is most likely looking at.
 */
public final class GiphyMp4PlaybackController extends RecyclerView.OnScrollListener implements View.OnLayoutChangeListener {

  private final int      maxSimultaneousPlayback;
  private final Callback callback;

  private GiphyMp4PlaybackController(@NonNull Callback callback, int maxSimultaneousPlayback) {
    this.maxSimultaneousPlayback = maxSimultaneousPlayback;
    this.callback                = callback;
  }

  public static void attach(@NonNull RecyclerView recyclerView, @NonNull Callback callback, int maxSimultaneousPlayback) {
    GiphyMp4PlaybackController controller = new GiphyMp4PlaybackController(callback, maxSimultaneousPlayback);

    recyclerView.addOnScrollListener(controller);
    recyclerView.addOnLayoutChangeListener(controller);
  }

  @Override
  public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
    performPlaybackUpdate(recyclerView);
  }

  @Override
  public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
    RecyclerView recyclerView = (RecyclerView) v;
    performPlaybackUpdate(recyclerView);
  }

  private void performPlaybackUpdate(@NonNull RecyclerView recyclerView) {
    RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();

    if (layoutManager == null) {
      return;
    }

    List<GiphyMp4Playable> playables         = new LinkedList<>();
    Set<Integer>           playablePositions = new HashSet<>();

    for (int i = 0; i < recyclerView.getChildCount(); i++) {
      RecyclerView.ViewHolder holder = recyclerView.getChildViewHolder(recyclerView.getChildAt(i));

      if (holder instanceof GiphyMp4Playable) {
        GiphyMp4Playable playable = (GiphyMp4Playable) holder;
        playables.add(playable);

        if (playable.canPlayContent()) {
          playablePositions.add(playable.getAdapterPosition());
        }
      }
    }

    int[]        firstVisiblePositions = findFirstVisibleItemPositions(layoutManager);
    int[]        lastVisiblePositions  = findLastVisibleItemPositions(layoutManager);
    Set<Integer> playbackSet           = getPlaybackSetForMaximumDistance(playablePositions, firstVisiblePositions, lastVisiblePositions);

    callback.update(recyclerView, playables, playbackSet);
  }

  private @NonNull Set<Integer> getPlaybackSetForMaximumDistance(@NonNull Set<Integer> playablePositions, int[] firstVisiblePositions, int[] lastVisiblePositions) {
    int firstVisiblePosition = Integer.MAX_VALUE;
    int lastVisiblePosition  = Integer.MIN_VALUE;

    for (int i = 0; i < firstVisiblePositions.length; i++) {
      firstVisiblePosition = Math.min(firstVisiblePosition, firstVisiblePositions[i]);
      lastVisiblePosition  = Math.max(lastVisiblePosition, lastVisiblePositions[i]);
    }

    return getPlaybackSet(playablePositions, firstVisiblePosition, lastVisiblePosition);
  }

  private @NonNull Set<Integer> getPlaybackSet(@NonNull Set<Integer> playablePositions, int firstVisiblePosition, int lastVisiblePosition) {
    return Stream.rangeClosed(firstVisiblePosition, lastVisiblePosition)
                 .sorted(new RangeComparator(firstVisiblePosition, lastVisiblePosition))
                 .filter(playablePositions::contains)
                 .limit(maxSimultaneousPlayback)
                 .collect(Collectors.toSet());
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

  public interface Callback {
    void update(@NonNull RecyclerView recyclerView, @NonNull List<GiphyMp4Playable> holders, @NonNull Set<Integer> playbackSet);
    void updateVideoDisplayPositionAndSize(@NonNull RecyclerView recyclerView, @NonNull GiphyMp4Playable holder);
  }

  @VisibleForTesting
  static final class RangeComparator implements Comparator<Integer> {

    private final int center;

    RangeComparator(int firstVisiblePosition, int lastVisiblePosition) {
      int delta = lastVisiblePosition - firstVisiblePosition;

      center = firstVisiblePosition + (delta / 2);
    }

    @Override
    public int compare(Integer o1, Integer o2) {
      int distance1 = Math.abs(o1 - center);
      int distance2 = Math.abs(o2 - center);
      int comp      = Integer.compare(distance1, distance2);

      if (comp == 0) {
        return Integer.compare(o1, o2);
      }

      return comp;
    }
  }
}
