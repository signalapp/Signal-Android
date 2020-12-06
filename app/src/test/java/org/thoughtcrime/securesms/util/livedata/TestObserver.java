package org.thoughtcrime.securesms.util.livedata;

import androidx.lifecycle.Observer;

import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TestObserver<T> implements Observer<T> {

  private final Collection<T> values = new ConcurrentLinkedQueue<>();

  @Override
  public void onChanged(T t) {
    values.add(t);
  }

  public @NonNull Collection<T> getValues() {
    return values;
  }
}
