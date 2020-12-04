package org.thoughtcrime.securesms.util;

import android.database.Cursor;
import android.database.CursorWrapper;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;

/**
 * Wraps a {@link Cursor} that will be closed automatically when the {@link Lifecycle.Event}.ON_DESTROY
 * is fired from the lifecycle this object is observing.
 */
public class LifecycleCursorWrapper extends CursorWrapper implements DefaultLifecycleObserver {

  public LifecycleCursorWrapper(Cursor cursor) {
    super(cursor);
  }

  @Override
  public void onDestroy(@NonNull LifecycleOwner owner) {
    close();
  }
}
