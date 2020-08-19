package org.thoughtcrime.securesms.util;

import androidx.lifecycle.MutableLiveData;
import android.database.ContentObserver;
import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.database.ObservableContent;

import java.io.Closeable;

/**
 * Implementation of {@link androidx.lifecycle.LiveData} that will handle closing the contained
 * {@link Closeable} when the value changes.
 */
public class ObservingLiveData<E extends ObservableContent> extends MutableLiveData<E> {

  private ContentObserver observer;

  @Override
  public void setValue(E value) {
    E previous = getValue();

    if (previous != null) {
      previous.unregisterContentObserver(observer);
      Util.close(previous);
    }

    value.registerContentObserver(observer);

    super.setValue(value);
  }

  public void close() {
    E value = getValue();

    if (value != null) {
      value.unregisterContentObserver(observer);
      Util.close(value);
    }
  }

  public void registerContentObserver(@NonNull ContentObserver observer) {
    this.observer = observer;
  }
}
