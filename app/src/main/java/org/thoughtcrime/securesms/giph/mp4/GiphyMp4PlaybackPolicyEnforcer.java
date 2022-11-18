package org.thoughtcrime.securesms.giph.mp4;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

/**
 * Enforces a video player to play back a specified number of loops given
 * video length and device policy.
 */
public final class GiphyMp4PlaybackPolicyEnforcer {

  private final Callback callback;
  private final long     maxDurationOfSinglePlayback;
  private final long     maxRepeatsOfSinglePlayback;

  private long loopsRemaining = -1;

  public GiphyMp4PlaybackPolicyEnforcer(@NonNull Callback callback) {
    this(callback,
         GiphyMp4PlaybackPolicy.maxDurationOfSinglePlayback(),
         GiphyMp4PlaybackPolicy.maxRepeatsOfSinglePlayback());
  }

  @VisibleForTesting
  GiphyMp4PlaybackPolicyEnforcer(@NonNull Callback callback,
                                 long maxDurationOfSinglePlayback,
                                 long maxRepeatsOfSinglePlayback)
  {
    this.callback                    = callback;
    this.maxDurationOfSinglePlayback = maxDurationOfSinglePlayback;
    this.maxRepeatsOfSinglePlayback  = maxRepeatsOfSinglePlayback;
  }

  void setMediaDuration(long duration) {
    long maxLoopsByDuration = Math.max(1, maxDurationOfSinglePlayback / duration);

    loopsRemaining = Math.min(maxLoopsByDuration, maxRepeatsOfSinglePlayback);
  }

  public boolean endPlayback() {
    if (loopsRemaining < 0) throw new IllegalStateException("Must call setMediaDuration before calling this method.");
    else if (loopsRemaining == 0) return true;
    else {
      loopsRemaining--;
      if (loopsRemaining == 0) {
        callback.onPlaybackWillEnd();
        return true;
      } else {
        return false;
      }
    }
  }


  public interface Callback {
    void onPlaybackWillEnd();
  }
}
