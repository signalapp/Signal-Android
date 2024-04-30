package org.thoughtcrime.securesms.keyvalue;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.util.Util;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class KeyValueDataSet implements KeyValueReader {
  private final Map<String, Object> values = new HashMap<>();
  private final Map<String, Class>  types  = new HashMap<>();

  public void putBlob(@NonNull String key, byte[] value) {
    values.put(key, value);
    types.put(key, byte[].class);
  }

  public void putBoolean(@NonNull String key, boolean value) {
    values.put(key, value);
    types.put(key, Boolean.class);
  }

  public void putFloat(@NonNull String key, float value) {
    values.put(key, value);
    types.put(key, Float.class);
  }

  public void putInteger(@NonNull String key, int value) {
    values.put(key, value);
    types.put(key, Integer.class);
  }

  public void putLong(@NonNull String key, long value) {
    values.put(key, value);
    types.put(key, Long.class);
  }

  public void putString(@NonNull String key, String value) {
    values.put(key, value);
    types.put(key, String.class);
  }

  void putAll(@NonNull KeyValueDataSet other) {
    values.putAll(other.values);
    types.putAll(other.types);
  }

  void removeAll(@NonNull Collection<String> removes) {
    for (String remove : removes) {
      values.remove(remove);
      types.remove(remove);
    }
  }

  @Override
  public byte[] getBlob(@NonNull String key, byte[] defaultValue) {
    if (containsKey(key)) {
      return readValueAsType(key, byte[].class, true);
    } else {
      return defaultValue;
    }
  }

  @Override
  public boolean getBoolean(@NonNull String key, boolean defaultValue) {
    if (containsKey(key)) {
      return readValueAsType(key, Boolean.class, false);
    } else {
      return defaultValue;
    }
  }

  @Override
  public float getFloat(@NonNull String key, float defaultValue) {
    if (containsKey(key)) {
      return readValueAsType(key, Float.class, false);
    } else {
      return defaultValue;
    }
  }

  @Override
  public int getInteger(@NonNull String key, int defaultValue) {
    if (containsKey(key)) {
      return readValueAsType(key, Integer.class, false);
    } else {
      return defaultValue;
    }
  }

  @Override
  public long getLong(@NonNull String key, long defaultValue) {
    if (containsKey(key)) {
      return readValueAsType(key, Long.class, false);
    } else {
      return defaultValue;
    }
  }

  @Override
  public String getString(@NonNull String key, String defaultValue) {
    if (containsKey(key)) {
      return readValueAsType(key, String.class, true);
    } else {
      return defaultValue;
    }
  }

  @Override
  public boolean containsKey(@NonNull String key) {
    return values.containsKey(key);
  }

  public @NonNull Map<String, Object> getValues() {
    return values;
  }

  public Class getType(@NonNull String key) {
    return types.get(key);
  }

  private <E> E readValueAsType(@NonNull String key, Class<E> expectedType, boolean nullable) {
    Object value = values.get(key);
    if (value == null && nullable) {
      return null;
    }

    if (value == null) {
      throw new IllegalArgumentException("Nullability mismatch!");
    }

    if (value.getClass() == expectedType) {
      return expectedType.cast(value);
    }

    if (expectedType == Integer.class && value instanceof Long) {
      long longValue = (long) value;
      return expectedType.cast(Util.toIntExact(longValue));
    }

    if (expectedType == Long.class && value instanceof Integer) {
      int intValue = (int) value;
      return expectedType.cast((long) intValue);
    }

    throw new IllegalArgumentException("Type mismatch!");
  }
}
