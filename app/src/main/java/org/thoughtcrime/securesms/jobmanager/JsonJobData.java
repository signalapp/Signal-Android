package org.thoughtcrime.securesms.jobmanager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.signal.core.util.logging.Log;
import org.signal.core.util.Base64;
import org.thoughtcrime.securesms.util.JsonUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class JsonJobData {

  public static final String TAG = Log.tag(JsonJobData.class);

  public static final JsonJobData EMPTY = new JsonJobData.Builder().build();

  @JsonProperty private final Map<String, String>    strings;
  @JsonProperty private final Map<String, String[]>  stringArrays;
  @JsonProperty private final Map<String, Integer>   integers;
  @JsonProperty private final Map<String, int[]>     integerArrays;
  @JsonProperty private final Map<String, Long>      longs;
  @JsonProperty private final Map<String, long[]>    longArrays;
  @JsonProperty private final Map<String, Float>     floats;
  @JsonProperty private final Map<String, float[]>   floatArrays;
  @JsonProperty private final Map<String, Double>    doubles;
  @JsonProperty private final Map<String, double[]>  doubleArrays;
  @JsonProperty private final Map<String, Boolean>   booleans;
  @JsonProperty private final Map<String, boolean[]> booleanArrays;

  public static @NonNull JsonJobData deserialize(@Nullable byte[] data) {
    if (data == null) {
      return EMPTY;
    }

    try {
      return JsonUtils.fromJson(data, JsonJobData.class);
    } catch (IOException e) {
      Log.e(TAG, "Failed to deserialize JSON.", e);
      throw new AssertionError(e);
    }
  }

  public static @Nullable JsonJobData deserializeOrNull(@Nullable byte[] data) {
    if (data == null) {
      return null;
    }

    try {
      return JsonUtils.fromJson(data, JsonJobData.class);
    } catch (IOException e) {
      return null;
    }
  }

  private JsonJobData(@JsonProperty("strings")       @NonNull Map<String, String>    strings,
                      @JsonProperty("stringArrays")  @NonNull Map<String, String[]>  stringArrays,
                      @JsonProperty("integers")      @NonNull Map<String, Integer>   integers,
                      @JsonProperty("integerArrays") @NonNull Map<String, int[]>     integerArrays,
                      @JsonProperty("longs")         @NonNull Map<String, Long>      longs,
                      @JsonProperty("longArrays")    @NonNull Map<String, long[]>    longArrays,
                      @JsonProperty("floats")        @NonNull Map<String, Float>     floats,
                      @JsonProperty("floatArrays")   @NonNull Map<String, float[]>   floatArrays,
                      @JsonProperty("doubles")       @NonNull Map<String, Double>    doubles,
                      @JsonProperty("doubleArrays")  @NonNull Map<String, double[]>  doubleArrays,
                      @JsonProperty("booleans")      @NonNull Map<String, Boolean>   booleans,
                      @JsonProperty("booleanArrays") @NonNull Map<String, boolean[]> booleanArrays)
  {
    this.strings       = strings;
    this.stringArrays  = stringArrays;
    this.integers      = integers;
    this.integerArrays = integerArrays;
    this.longs         = longs;
    this.longArrays    = longArrays;
    this.floats        = floats;
    this.floatArrays   = floatArrays;
    this.doubles       = doubles;
    this.doubleArrays  = doubleArrays;
    this.booleans      = booleans;
    this.booleanArrays = booleanArrays;
  }

  public boolean hasString(@NonNull String key) {
    return strings.containsKey(key);
  }

  public String getString(@NonNull String key) {
    throwIfAbsent(strings, key);
    return strings.get(key);
  }

  public byte[] getStringAsBlob(@NonNull String key) {
    String raw = getString(key);
    return raw != null ? Base64.decodeOrThrow(raw) : null;
  }

  public String getStringOrDefault(@NonNull String key, String defaultValue) {
    if (hasString(key)) return getString(key);
    else                return defaultValue;
  }


  public boolean hasStringArray(@NonNull String key) {
    return stringArrays.containsKey(key);
  }

  public String[] getStringArray(@NonNull String key) {
    throwIfAbsent(stringArrays, key);
    return stringArrays.get(key);
  }

  /**
   * Helper method for {@link #getStringArray(String)} that returns the value as a list.
   */
  public List<String> getStringArrayAsList(@NonNull String key) {
    throwIfAbsent(stringArrays, key);
    return Arrays.asList(stringArrays.get(key));
  }


  public boolean hasInt(@NonNull String key) {
    return integers.containsKey(key);
  }

  public int getInt(@NonNull String key) {
    throwIfAbsent(integers, key);
    return integers.get(key);
  }

  public int getIntOrDefault(@NonNull String key, int defaultValue) {
    if (hasInt(key)) return getInt(key);
    else             return defaultValue;
  }


  public boolean hasIntegerArray(@NonNull String key) {
    return integerArrays.containsKey(key);
  }

  public int[] getIntegerArray(@NonNull String key) {
    throwIfAbsent(integerArrays, key);
    return integerArrays.get(key);
  }

  public List<Integer> getIntegerArrayAsList(@NonNull String key) {
    throwIfAbsent(integerArrays, key);

    int[]         array = Objects.requireNonNull(integerArrays.get(key));
    List<Integer> ints  = new ArrayList<>(array.length);

    for (int l : array) {
      ints.add(l);
    }

    return ints;
  }

  public boolean hasLong(@NonNull String key) {
    return longs.containsKey(key);
  }

  public long getLong(@NonNull String key) {
    throwIfAbsent(longs, key);
    return longs.get(key);
  }

  public long getLongOrDefault(@NonNull String key, long defaultValue) {
    if (hasLong(key)) return getLong(key);
    else              return defaultValue;
  }


  public boolean hasLongArray(@NonNull String key) {
    return longArrays.containsKey(key);
  }

  public long[] getLongArray(@NonNull String key) {
    throwIfAbsent(longArrays, key);
    return longArrays.get(key);
  }

  public List<Long> getLongArrayAsList(@NonNull String key) {
    throwIfAbsent(longArrays, key);

    long[]     array = Objects.requireNonNull(longArrays.get(key));
    List<Long> longs = new ArrayList<>(array.length);

    for (long l : array) {
      longs.add(l);
    }

    return longs;
  }


  public boolean hasFloat(@NonNull String key) {
    return floats.containsKey(key);
  }

  public float getFloat(@NonNull String key) {
    throwIfAbsent(floats, key);
    return floats.get(key);
  }

  public float getFloatOrDefault(@NonNull String key, float defaultValue) {
    if (hasFloat(key)) return getFloat(key);
    else               return defaultValue;
  }


  public boolean hasFloatArray(@NonNull String key) {
    return floatArrays.containsKey(key);
  }

  public float[] getFloatArray(@NonNull String key) {
    throwIfAbsent(floatArrays, key);
    return floatArrays.get(key);
  }


  public boolean hasDouble(@NonNull String key) {
    return doubles.containsKey(key);
  }

  public double getDouble(@NonNull String key) {
    throwIfAbsent(doubles, key);
    return doubles.get(key);
  }

  public double getDoubleOrDefault(@NonNull String key, double defaultValue) {
    if (hasDouble(key)) return getDouble(key);
    else                return defaultValue;
  }


  public boolean hasDoubleArray(@NonNull String key) {
    return floatArrays.containsKey(key);
  }

  public double[] getDoubleArray(@NonNull String key) {
    throwIfAbsent(doubleArrays, key);
    return doubleArrays.get(key);
  }


  public boolean hasBoolean(@NonNull String key) {
    return booleans.containsKey(key);
  }

  public boolean getBoolean(@NonNull String key) {
    throwIfAbsent(booleans, key);
    return booleans.get(key);
  }

  public boolean getBooleanOrDefault(@NonNull String key, boolean defaultValue) {
    if (hasBoolean(key)) return getBoolean(key);
    else                 return defaultValue;
  }


  public boolean hasBooleanArray(@NonNull String key) {
    return booleanArrays.containsKey(key);
  }

  public boolean[] getBooleanArray(@NonNull String key) {
    throwIfAbsent(booleanArrays, key);
    return booleanArrays.get(key);
  }


  private void throwIfAbsent(@NonNull Map map, @NonNull String key) {
    if (!map.containsKey(key)) {
      throw new IllegalStateException("Tried to retrieve a value with key '" + key + "', but it wasn't present.");
    }
  }

  public Builder buildUpon() {
    return new Builder(this);
  }

  public boolean isEmpty() {
    return strings.isEmpty() &&
        stringArrays.isEmpty() &&
        integers.isEmpty() &&
        integerArrays.isEmpty() &&
        longs.isEmpty() &&
        longArrays.isEmpty() &&
        floats.isEmpty() &&
        floatArrays.isEmpty() &&
        doubles.isEmpty() &&
        doubleArrays.isEmpty() &&
        booleans.isEmpty() &&
        booleanArrays.isEmpty();
  }

  public @Nullable byte[] serialize() {
    if (isEmpty()) {
      return null;
    } else {
      try {
        return JsonUtils.toJson(this).getBytes(StandardCharsets.UTF_8);
      } catch (IOException e) {
        Log.e(TAG, "Failed to serialize to JSON.", e);
        throw new AssertionError(e);
      }
    }
  }


  public static class Builder {

    private final Map<String, String>    strings       = new HashMap<>();
    private final Map<String, String[]>  stringArrays  = new HashMap<>();
    private final Map<String, Integer>   integers      = new HashMap<>();
    private final Map<String, int[]>     integerArrays = new HashMap<>();
    private final Map<String, Long>      longs         = new HashMap<>();
    private final Map<String, long[]>    longArrays    = new HashMap<>();
    private final Map<String, Float>     floats        = new HashMap<>();
    private final Map<String, float[]>   floatArrays   = new HashMap<>();
    private final Map<String, Double>    doubles       = new HashMap<>();
    private final Map<String, double[]>  doubleArrays  = new HashMap<>();
    private final Map<String, Boolean>   booleans      = new HashMap<>();
    private final Map<String, boolean[]> booleanArrays = new HashMap<>();

    public Builder() { }

    private Builder(@NonNull JsonJobData oldData) {
      strings.putAll(oldData.strings);
      stringArrays.putAll(oldData.stringArrays);
      integers.putAll(oldData.integers);
      integerArrays.putAll(oldData.integerArrays);
      longs.putAll(oldData.longs);
      longArrays.putAll(oldData.longArrays);
      floats.putAll(oldData.floats);
      floatArrays.putAll(oldData.floatArrays);
      doubles.putAll(oldData.doubles);
      doubleArrays.putAll(oldData.doubleArrays);
      booleans.putAll(oldData.booleans);
      booleanArrays.putAll(oldData.booleanArrays);
    }

    public Builder putString(@NonNull String key, @Nullable String value) {
      strings.put(key, value);
      return this;
    }

    public Builder putStringArray(@NonNull String key, @NonNull String[] value) {
      stringArrays.put(key, value);
      return this;
    }

    /**
     * Helper method for {@link #putStringArray(String, String[])} that takes a list.
     */
    public Builder putStringListAsArray(@NonNull String key, @NonNull List<String> value) {
      stringArrays.put(key, value.toArray(new String[0]));
      return this;
    }

    public Builder putIntegerListAsArray(@NonNull String key, @NonNull List<Integer> value) {
      int[] ints = new int[value.size()];

      for (int i = 0; i < value.size(); i++) {
        ints[i] = value.get(i);
      }

      integerArrays.put(key, ints);
      return this;
    }

    public Builder putInt(@NonNull String key, int value) {
      integers.put(key, value);
      return this;
    }

    public Builder putIntArray(@NonNull String key, @NonNull int[] value) {
      integerArrays.put(key, value);
      return this;
    }

    public Builder putLong(@NonNull String key, long value) {
      longs.put(key, value);
      return this;
    }

    public Builder putLongArray(@NonNull String key, @NonNull long[] value) {
      longArrays.put(key, value);
      return this;
    }

    public Builder putLongListAsArray(@NonNull String key, @NonNull List<Long> value) {
      long[] longs = new long[value.size()];

      for (int i = 0; i < value.size(); i++) {
        longs[i] = value.get(i);
      }

      longArrays.put(key, longs);
      return this;
    }

    public Builder putFloat(@NonNull String key, float value) {
      floats.put(key, value);
      return this;
    }

    public Builder putFloatArray(@NonNull String key, @NonNull float[] value) {
      floatArrays.put(key, value);
      return this;
    }

    public Builder putDouble(@NonNull String key, double value) {
      doubles.put(key, value);
      return this;
    }

    public Builder putDoubleArray(@NonNull String key, @NonNull double[] value) {
      doubleArrays.put(key, value);
      return this;
    }

    public Builder putBoolean(@NonNull String key, boolean value) {
      booleans.put(key, value);
      return this;
    }

    public Builder putBooleanArray(@NonNull String key, @NonNull boolean[] value) {
      booleanArrays.put(key, value);
      return this;
    }

    public Builder putBlobAsString(@NonNull String key, @Nullable byte[] value) {
      String serialized = value != null ? Base64.encodeWithPadding(value) : null;
      strings.put(key, serialized);
      return this;
    }

    public JsonJobData build() {
      return new JsonJobData(strings,
                             stringArrays,
                             integers,
                             integerArrays,
                             longs,
                             longArrays,
                             floats,
                             floatArrays,
                             doubles,
                             doubleArrays,
                             booleans,
                             booleanArrays);
    }

    public @Nullable byte[] serialize() {
      return build().serialize();
    }
  }

  public interface Serializer {
    @NonNull String serialize(@NonNull JsonJobData data);
    @NonNull JsonJobData deserialize(@NonNull String serialized);
  }
}
