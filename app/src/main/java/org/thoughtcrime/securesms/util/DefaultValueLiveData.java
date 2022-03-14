package org.thoughtcrime.securesms.util;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import org.whispersystems.signalservice.api.util.Preconditions;


/**
 * Helps prevent all the @Nullable warnings when working with LiveData.
 */
public class DefaultValueLiveData<T> extends MutableLiveData<T> {

  private final T defaultValue;

  public DefaultValueLiveData(@NonNull T defaultValue) {
    super(defaultValue);
    this.defaultValue = defaultValue;
  }

  @Override
  public void postValue(@NonNull T value) {
    Preconditions.checkNotNull(value);
    super.postValue(value);
  }

  @Override
  public void setValue(@NonNull T value) {
    Preconditions.checkNotNull(value);
    super.setValue(value);
  }

  @Override
  public @NonNull T getValue() {
    T value = super.getValue();
    return value != null ? value : defaultValue;
  }
}
