package org.thoughtcrime.securesms.keyvalue;

import androidx.annotation.NonNull;

import java.util.Collection;

public final class MockKeyValuePersistentStorage implements KeyValuePersistentStorage {

  private final KeyValueDataSet dataSet;

  public static KeyValuePersistentStorage withDataSet(@NonNull KeyValueDataSet dataSet) {
    return new MockKeyValuePersistentStorage(dataSet);
  }

  private MockKeyValuePersistentStorage(@NonNull KeyValueDataSet dataSet) {
    this.dataSet = dataSet;
  }

  @Override
  public void writeDataSet(@NonNull KeyValueDataSet dataSet, @NonNull Collection<String> removes) {
    this.dataSet.putAll(dataSet);
    this.dataSet.removeAll(removes);
  }

  @Override
  public @NonNull KeyValueDataSet getDataSet() {
    return dataSet;
  }
}
