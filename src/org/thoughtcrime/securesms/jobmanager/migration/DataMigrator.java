package org.thoughtcrime.securesms.jobmanager.migration;

import android.support.annotation.NonNull;

import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.logging.Log;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Takes a persisted data blob stored by WorkManager and converts it to our {@link Data} class.
 */
final class DataMigrator {

  private static final String TAG = Log.tag(DataMigrator.class);

  static final Data convert(@NonNull byte[] workManagerData) {
    Map<String, Object> values = parseWorkManagerDataMap(workManagerData);

    Data.Builder builder = new Data.Builder();

    for (Map.Entry<String, Object> entry : values.entrySet()) {
      Object value = entry.getValue();

      if (value == null) {
        builder.putString(entry.getKey(), null);
      } else {
        Class type = value.getClass();

        if (type == String.class) {
          builder.putString(entry.getKey(), (String) value);
        } else if (type == String[].class) {
          builder.putStringArray(entry.getKey(), (String[]) value);
        } else if (type == Integer.class) {
          builder.putInt(entry.getKey(), (int) value);
        } else if (type == int[].class) {
          builder.putIntArray(entry.getKey(), (int[]) value);
        } else if (type == Long.class) {
          builder.putLong(entry.getKey(), (long) value);
        } else if (type == long[].class) {
          builder.putLongArray(entry.getKey(), (long[]) value);
        } else if (type == Float.class) {
          builder.putFloat(entry.getKey(), (float) value);
        } else if (type == float[].class) {
          builder.putFloatArray(entry.getKey(), (float[]) value);
        } else if (type == Double.class) {
          builder.putDouble(entry.getKey(), (double) value);
        } else if (type == double[].class) {
          builder.putDoubleArray(entry.getKey(), (double[]) value);
        } else if (type == Boolean.class) {
          builder.putBoolean(entry.getKey(), (boolean) value);
        } else if (type == boolean[].class) {
          builder.putBooleanArray(entry.getKey(), (boolean[]) value);
        } else {
          Log.w(TAG, "Encountered unexpected type '" + type + "'. Skipping.");
        }
      }
    }

    return builder.build();
  }

  private static @NonNull Map<String, Object> parseWorkManagerDataMap(@NonNull byte[] bytes) throws IllegalStateException {
    Map<String, Object>  map               = new HashMap<>();
    ByteArrayInputStream inputStream       = new ByteArrayInputStream(bytes);
    ObjectInputStream    objectInputStream = null;

    try {
      objectInputStream = new ObjectInputStream(inputStream);

      for (int i = objectInputStream.readInt(); i > 0; i--) {
        map.put(objectInputStream.readUTF(), objectInputStream.readObject());
      }
    } catch (IOException | ClassNotFoundException e) {
      Log.w(TAG, "Failed to read WorkManager data.", e);
    } finally {
      try {
        inputStream.close();

        if (objectInputStream != null) {
          objectInputStream.close();
        }
      } catch (IOException e) {
        Log.e(TAG, "Failed to close streams after reading WorkManager data.", e);
      }
    }
    return map;
  }
}

