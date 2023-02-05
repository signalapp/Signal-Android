package org.thoughtcrime.securesms.util.views;


import android.view.View;
import android.view.ViewStub;

import androidx.annotation.NonNull;

public class Stub<T extends View> {

  private ViewStub viewStub;
  private T        view;

  public Stub(@NonNull ViewStub viewStub) {
    this.viewStub = viewStub;
  }

  public T get() {
    if (view == null) {
      //noinspection unchecked
      view     = (T) viewStub.inflate();
      viewStub = null;
    }

    return view;
  }

  public boolean resolved() {
    return view != null;
  }

  public void setVisibility(int visibility) {
    if (resolved() || visibility == View.VISIBLE) {
      get().setVisibility(visibility);
    }
  }

  public int getVisibility() {
    if (resolved()) {
      return get().getVisibility();
    } else {
      return View.GONE;
    }
  }

}
