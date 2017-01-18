package org.thoughtcrime.securesms.util.views;


import android.view.ViewStub;

public class Stub<T> {

  private final ViewStub viewStub;
  private T view;

  public Stub(ViewStub viewStub) {
    this.viewStub = viewStub;
  }

  public T get() {
    if (view == null) {
      view = (T)viewStub.inflate();
    }

    return view;
  }

  public boolean resolved() {
    return view != null;
  }

}
