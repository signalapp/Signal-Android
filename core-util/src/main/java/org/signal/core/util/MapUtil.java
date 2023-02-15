package org.signal.core.util;

import android.os.Build;

import androidx.annotation.NonNull;

import java.util.Map;
import java.util.function.Function;

public final class MapUtil {

  private MapUtil() {}

  @NonNull
  public static <K, V> V getOrDefault(@NonNull Map<K, V> map, @NonNull K key, @NonNull V defaultValue) {
    if (Build.VERSION.SDK_INT >= 24) {
      //noinspection ConstantConditions
      return map.getOrDefault(key, defaultValue);
    } else {
      V v = map.get(key);
      return v == null ? defaultValue : v;
    }
  }

  @NonNull
  public static <K, V, M> M mapOrDefault(@NonNull Map<K, V> map, @NonNull K key, @NonNull Function<V, M> mapper, @NonNull M defaultValue) {
    V v = map.get(key);
    return v == null ? defaultValue : mapper.apply(v);
  }
}
