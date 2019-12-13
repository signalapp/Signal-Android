package org.thoughtcrime.securesms.util;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

public class DefaultValueLiveData<T> extends MutableLiveData<T> {

  private final T defaultValue;

  public DefaultValueLiveData(@NonNull T defaultValue) {
    this.defaultValue = defaultValue;
  }

  @Override
  public @NonNull T getValue() {
    T value = super.getValue();
    return value != null ? value : defaultValue;
  }
}
