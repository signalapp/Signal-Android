package org.thoughtcrime.securesms.util.views;


import android.view.ViewStub;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

public class NullableStub<T> {

  private ViewStub viewStub;
  private T view;

  public NullableStub(@Nullable ViewStub viewStub) {
    this.viewStub = viewStub;
  }

  private @Nullable T get() {
    if (viewStub != null && view == null) {
      view = (T) viewStub.inflate();
      viewStub = null;
    }

    return view;
  }

  public @NonNull T require() {
    return Objects.requireNonNull(get());
  }

  public boolean isResolvable() {
    return viewStub != null || resolved();
  }

  public boolean resolved() {
    return view != null;
  }
}
