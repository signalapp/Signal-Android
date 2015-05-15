package org.thoughtcrime.securesms.components.emoji;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.Drawable.Callback;
import android.view.View;

public class PostInvalidateCallback implements Callback {
  private final View view;

  public PostInvalidateCallback(View view) {
    this.view = view;
  }

  @Override public void invalidateDrawable(Drawable who) {
    view.postInvalidate();
  }

  @Override public void scheduleDrawable(Drawable who, Runnable what, long when) {

  }

  @Override public void unscheduleDrawable(Drawable who, Runnable what) {

  }
}
