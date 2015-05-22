package org.thoughtcrime.securesms.components.emoji;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.Drawable.Callback;
import android.view.View;

import org.thoughtcrime.securesms.util.Util;

public class PostInvalidateCallback implements Callback {
  private final View view;

  public PostInvalidateCallback(View view) {
    this.view = view;
  }

  @Override public void invalidateDrawable(Drawable who) {
    Util.runOnMain(new Runnable() {
      @Override public void run() {
        view.invalidate();
      }
    });
  }

  @Override public void scheduleDrawable(Drawable who, Runnable what, long when) {

  }

  @Override public void unscheduleDrawable(Drawable who, Runnable what) {

  }
}
