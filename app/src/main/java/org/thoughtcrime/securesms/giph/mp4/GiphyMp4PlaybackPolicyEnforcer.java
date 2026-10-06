package org.thoughtcrime.securesms.giph.mp4;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.util.AnimationPlaybackPolicy;

/**
 * Enforces a video player to play back a specified number of loops given
 * video length and device policy.
 */
public final class GiphyMp4PlaybackPolicyEnforcer {

  private final Callback callback;

  private long loopsRemaining = -1;

  public GiphyMp4PlaybackPolicyEnforcer(@NonNull Callback callback) {
    this.callback = callback;
  }

  void setMediaDuration(long duration) {
    loopsRemaining = AnimationPlaybackPolicy.loopsOfSinglePlayback(duration);
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
