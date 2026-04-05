package org.thoughtcrime.securesms.conversationlist;

import android.os.Handler;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;

import org.signal.core.util.logging.Log;

import java.util.List;

public class ConversationListItemAnimator extends DefaultItemAnimator {

  private static final String TAG = Log.tag(ConversationListItemAnimator.class);

  private static final long ANIMATION_DURATION = 200;

  private boolean shouldDisable;
  private int     pendingChangeMoves;

  public ConversationListItemAnimator() {
    setMoveDuration(0);
    setAddDuration(0);
    setChangeDuration(ANIMATION_DURATION);
  }

  @Override
  public boolean animateChange(@NonNull RecyclerView.ViewHolder oldHolder, @NonNull RecyclerView.ViewHolder newHolder, int fromX, int fromY, int toX, int toY) {
    if (fromX != toX || fromY != toY) {
      pendingChangeMoves++;
      return animateMove(newHolder, fromX, fromY, toX, toY);
    }

    dispatchChangeFinished(newHolder, true);
    return false;
  }

  @Override
  public void runPendingAnimations() {
    if (pendingChangeMoves > 0) {
      pendingChangeMoves = 0;
      long previousMoveDuration = getMoveDuration();
      setMoveDuration(getChangeDuration());
      super.runPendingAnimations();
      setMoveDuration(previousMoveDuration);
    } else {
      super.runPendingAnimations();
    }
  }

  @Override
  public boolean canReuseUpdatedViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, @NonNull List<Object> payloads) {
    return true;
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

  @MainThread
  public void disableChangeAnimations() {
    setChangeDuration(0);
  }

  @MainThread
  public void enableChangeAnimations() {
    setChangeDuration(ANIMATION_DURATION);
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
