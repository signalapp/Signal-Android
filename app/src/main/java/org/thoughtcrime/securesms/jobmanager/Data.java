package org.thoughtcrime.securesms.jobmanager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;

public class Data {

  public static final Data EMPTY = new Data.Builder().build();

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

  public Data(@JsonProperty("strings")       @NonNull Map<String, String>    strings,
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

    public Builder putString(@NonNull String key, @Nullable String value) {
      strings.put(key, value);
      return this;
    }

    public Builder putStringArray(@NonNull String key, @NonNull String[] value) {
      stringArrays.put(key, value);
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

    public Data build() {
      return new Data(strings,
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
  }

  public interface Serializer {
    @NonNull String serialize(@NonNull Data data);
    @NonNull Data deserialize(@NonNull String serialized);
  }
}
