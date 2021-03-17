package org.thoughtcrime.securesms.keyvalue;

import androidx.annotation.NonNull;

interface KeyValueReader {
  byte[] getBlob(@NonNull String key, byte[] defaultValue);
  boolean getBoolean(@NonNull String key, boolean defaultValue);
  float getFloat(@NonNull String key, float defaultValue);
  int getInteger(@NonNull String key, int defaultValue);
  long getLong(@NonNull String key, long defaultValue);
  String getString(@NonNull String key, String defaultValue);
  boolean containsKey(@NonNull String key);
}
