package org.thoughtcrime.securesms.conversationlist;

import android.os.Handler;

import androidx.annotation.MainThread;
import androidx.recyclerview.widget.DefaultItemAnimator;

import org.signal.core.util.logging.Log;

public class ConversationListItemAnimator extends DefaultItemAnimator {

  private static final String TAG = Log.tag(ConversationListItemAnimator.class);

  private static final long ANIMATION_DURATION = 200;

  private boolean shouldDisable;

  public ConversationListItemAnimator() {
    setSupportsChangeAnimations(false);
    setMoveDuration(0);
    setAddDuration(0);
  }

  @MainThread
  public void enable() {
    setMoveDuration(ANIMATION_DURATION);
    shouldDisable = false;
  }

  @MainThread
  public void disable() {
    setMoveDuration(0);
  }


  /**
   * We need to reasonably ensure that the animation has started before we disable things here, so we add a slight delay.
   */
  @MainThread
  public void postDisable(Handler handler) {
    shouldDisable = true;
    handler.postDelayed(() -> {
      if (shouldDisable) {
        setMoveDuration(0);
      } else {
        Log.w(TAG, "Disable was canceled by an enable.");
      }
    }, 50);
  }
}
