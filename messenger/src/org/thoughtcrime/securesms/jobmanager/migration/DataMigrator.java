package org.thoughtcrime.securesms.jobmanager.migration;

import androidx.annotation.NonNull;

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
        } else if (type == Integer.class || type == int.class) {
          builder.putInt(entry.getKey(), (int) value);
        } else if (type == Integer[].class || type == int[].class) {
          builder.putIntArray(entry.getKey(), convertToIntArray(value, type));
        } else if (type == Long.class || type == long.class) {
          builder.putLong(entry.getKey(), (long) value);
        } else if (type == Long[].class || type == long[].class) {
          builder.putLongArray(entry.getKey(), convertToLongArray(value, type));
        } else if (type == Float.class || type == float.class) {
          builder.putFloat(entry.getKey(), (float) value);
        } else if (type == Float[].class || type == float[].class) {
          builder.putFloatArray(entry.getKey(), convertToFloatArray(value, type));
        } else if (type == Double.class || type == double.class) {
          builder.putDouble(entry.getKey(), (double) value);
        } else if (type == Double[].class || type == double[].class) {
          builder.putDoubleArray(entry.getKey(), convertToDoubleArray(value, type));
        } else if (type == Boolean.class || type == boolean.class) {
          builder.putBoolean(entry.getKey(), (boolean) value);
        } else if (type == Boolean[].class || type == boolean[].class) {
          builder.putBooleanArray(entry.getKey(), convertToBooleanArray(value, type));
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

  private static int[] convertToIntArray(Object value, Class type) {
    if (type == int[].class) {
      return (int[]) value;
    }

    Integer[] casted = (Integer[]) value;
    int[]     output = new int[casted.length];

    for (int i = 0; i < casted.length; i++) {
      output[i] = casted[i];
    }

    return output;
  }

  private static long[] convertToLongArray(Object value, Class type) {
    if (type == long[].class) {
      return (long[]) value;
    }

    Long[] casted = (Long[]) value;
    long[] output = new long[casted.length];

    for (int i = 0; i < casted.length; i++) {
      output[i] = casted[i];
    }

    return output;
  }

  private static float[] convertToFloatArray(Object value, Class type) {
    if (type == float[].class) {
      return (float[]) value;
    }

    Float[] casted = (Float[]) value;
    float[] output = new float[casted.length];

    for (int i = 0; i < casted.length; i++) {
      output[i] = casted[i];
    }

    return output;
  }

  private static double[] convertToDoubleArray(Object value, Class type) {
    if (type == double[].class) {
      return (double[]) value;
    }

    Double[] casted = (Double[]) value;
    double[] output = new double[casted.length];

    for (int i = 0; i < casted.length; i++) {
      output[i] = casted[i];
    }

    return output;
  }

  private static boolean[] convertToBooleanArray(Object value, Class type) {
    if (type == boolean[].class) {
      return (boolean[]) value;
    }

    Boolean[] casted = (Boolean[]) value;
    boolean[] output = new boolean[casted.length];

    for (int i = 0; i < casted.length; i++) {
      output[i] = casted[i];
    }

    return output;
  }
}

