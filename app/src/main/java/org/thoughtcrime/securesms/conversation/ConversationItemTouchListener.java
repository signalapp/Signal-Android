package org.thoughtcrime.securesms.conversation;

import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

final class ConversationItemTouchListener extends RecyclerView.SimpleOnItemTouchListener {

  private final Callback callback;

  ConversationItemTouchListener(Callback callback) {
    this.callback = callback;
  }

  @Override
  public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
    if (e.getAction() == MotionEvent.ACTION_DOWN) {
      callback.onDownEvent(e.getRawX(), e.getRawY());
    }
    return false;
  }

  interface Callback {
    void onDownEvent(float rawX, float rawY);
  }
}
