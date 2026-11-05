package org.thoughtcrime.securesms.util.views;


import android.view.ViewStub;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.ui.view.Stub;

import java.util.Objects;

public class NullableStub<T> {

  private ViewStub      viewStub;
  private Stub.Inflater inflater;
  private T             view;

  public NullableStub(@Nullable ViewStub viewStub) {
    this(viewStub, null);
  }

  public NullableStub(@Nullable ViewStub viewStub, @Nullable Stub.Inflater inflater) {
    this.viewStub = viewStub;
    this.inflater = inflater;
  }

  private @Nullable T get() {
    if (viewStub != null && view == null) {
      view     = (T) (inflater != null ? inflater.inflate(viewStub) : viewStub.inflate());
      viewStub = null;
      inflater = null;
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
