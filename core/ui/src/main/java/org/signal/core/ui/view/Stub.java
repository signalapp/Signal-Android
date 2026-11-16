package org.signal.core.ui.view;


import android.view.View;
import android.view.ViewStub;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class Stub<T extends View> {

  private ViewStub viewStub;
  private T        view;
  private Inflater inflater;

  public Stub(@NonNull ViewStub viewStub) {
    this(viewStub, null);
  }

  /**
   * @param inflater Replaces {@link ViewStub#inflate()} when the stub is first resolved, so callers can hand out a
   *                 view that was inflated ahead of time.
   */
  public Stub(@NonNull ViewStub viewStub, @Nullable Inflater inflater) {
    this.viewStub = viewStub;
    this.inflater = inflater;
  }

  public int getId() {
    return (viewStub != null) ? viewStub.getId() : view.getId();
  }

  public T get() {
    if (view == null) {
      //noinspection unchecked
      view     = (T) (inflater != null ? inflater.inflate(viewStub) : viewStub.inflate());
      viewStub = null;
      inflater = null;
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

  public boolean isVisible() {
    return getVisibility() == View.VISIBLE;
  }

  public interface Inflater {
    @NonNull View inflate(@NonNull ViewStub viewStub);
  }
}
