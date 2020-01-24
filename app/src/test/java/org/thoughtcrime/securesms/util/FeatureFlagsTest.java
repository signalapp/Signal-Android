package org.thoughtcrime.securesms.util;

import org.junit.Test;
import org.thoughtcrime.securesms.util.FeatureFlags.UpdateResult;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class FeatureFlagsTest {

  private static final String A = key("a");
  private static final String B = key("b");

  @Test
  public void updateInternal_newValue_ignoreMissingPrefix() {
    UpdateResult result = FeatureFlags.updateInternal(mapOf("noprefix", true),
                                                      mapOf(),
                                                      mapOf(),
                                                      setOf(),
                                                      setOf());

    assertEquals(mapOf(), result.getMemory());
    assertEquals(mapOf(), result.getDisk());
  }

  @Test
  public void updateInternal_newValue() {
    UpdateResult result = FeatureFlags.updateInternal(mapOf(A, true),
                                                      mapOf(),
                                                      mapOf(),
                                                      setOf(),
                                                      setOf());

    assertEquals(mapOf(), result.getMemory());
    assertEquals(mapOf(A, true), result.getDisk());
  }

  @Test
  public void updateInternal_newValue_hotSwap() {
    UpdateResult result = FeatureFlags.updateInternal(mapOf(A, true),
                                                      mapOf(),
                                                      mapOf(),
                                                      setOf(A),
                                                      setOf());

    assertEquals(mapOf(A, true), result.getMemory());
    assertEquals(mapOf(A, true), result.getDisk());
  }

  @Test
  public void updateInternal_newValue_sticky() {
    UpdateResult result = FeatureFlags.updateInternal(mapOf(A, true),
                                                      mapOf(),
                                                      mapOf(),
                                                      setOf(),
                                                      setOf(A));

    assertEquals(mapOf(), result.getMemory());
    assertEquals(mapOf(A, true), result.getDisk());
  }

  @Test
  public void updateInternal_newValue_hotSwap_sticky() {
    UpdateResult result = FeatureFlags.updateInternal(mapOf(A, true),
                                                      mapOf(),
                                                      mapOf(),
                                                      setOf(A),
                                                      setOf(A));

    assertEquals(mapOf(A, true), result.getMemory());
    assertEquals(mapOf(A, true), result.getDisk());
  }

  @Test
  public void updateInternal_replaceValue() {
    UpdateResult result = FeatureFlags.updateInternal(mapOf(A, true),
        mapOf(A, false),
        mapOf(A, false),
                                                      setOf(),
                                                      setOf());

    assertEquals(mapOf(A, false), result.getMemory());
    assertEquals(mapOf(A, true), result.getDisk());
  }

  @Test
  public void updateInternal_replaceValue_hotSwap() {
    UpdateResult result = FeatureFlags.updateInternal(mapOf(A, true),
                                                      mapOf(A, false),
                                                      mapOf(A, false),
                                                      setOf(A),
                                                      setOf());

    assertEquals(mapOf(A, true), result.getMemory());
    assertEquals(mapOf(A, true), result.getDisk());
  }

  @Test
  public void updateInternal_replaceValue_hotSwap_stickyChange() {
    UpdateResult result = FeatureFlags.updateInternal(mapOf(A, true),
                                                      mapOf(A, false),
                                                      mapOf(A, false),
                                                      setOf(A),
                                                      setOf(A));

    assertEquals(mapOf(A, true), result.getMemory());
    assertEquals(mapOf(A, true), result.getDisk());
  }

  @Test
  public void updateInternal_replaceValue_hotSwap_stickyIgnore_memoryAndDisk() {
    UpdateResult result = FeatureFlags.updateInternal(mapOf(A, false),
                                                      mapOf(A, true),
                                                      mapOf(A, true),
                                                      setOf(A),
                                                      setOf(A));

    assertEquals(mapOf(A, true), result.getMemory());
    assertEquals(mapOf(A, true), result.getDisk());
  }

  @Test
  public void updateInternal_removeValue() {
    UpdateResult result = FeatureFlags.updateInternal(mapOf(),
        mapOf(A, true),
                                                      mapOf(A, true),
                                                      setOf(),
                                                      setOf());

    assertEquals(mapOf(A, true), result.getMemory());
    assertEquals(mapOf(), result.getDisk());
  }

  @Test
  public void updateInternal_removeValue_hotSwap() {
    UpdateResult result = FeatureFlags.updateInternal(mapOf(),
                                                      mapOf(A, true),
                                                      mapOf(A, true),
                                                      setOf(A),
                                                      setOf());

    assertEquals(mapOf(), result.getMemory());
    assertEquals(mapOf(), result.getDisk());
  }

  @Test
  public void updateInternal_removeValue_stickyAlreadyEnabled() {
    UpdateResult result = FeatureFlags.updateInternal(mapOf(),
        mapOf(A, true),
                                                      mapOf(A, true),
                                                      setOf(),
                                                      setOf(A));

    assertEquals(mapOf(A, true), result.getMemory());
    assertEquals(mapOf(A, true), result.getDisk());
  }

  @Test
  public void updateInternal_removeValue_stickyNotEnabled() {
    UpdateResult result = FeatureFlags.updateInternal(mapOf(),
                                                      mapOf(A, false),
                                                      mapOf(A, false),
                                                      setOf(),
                                                      setOf(A));

    assertEquals(mapOf(A, false), result.getMemory());
    assertEquals(mapOf(), result.getDisk());
  }

  @Test
  public void updateInternal_removeValue_hotSwap_stickyAlreadyEnabled() {
    UpdateResult result = FeatureFlags.updateInternal(mapOf(),
                                                      mapOf(A, true),
                                                      mapOf(A, true),
                                                      setOf(A),
                                                      setOf(A));

    assertEquals(mapOf(A, true), result.getMemory());
    assertEquals(mapOf(A, true), result.getDisk());
  }

  @Test
  public void updateInternal_removeValue_hotSwap_stickyNotEnabled() {
    UpdateResult result = FeatureFlags.updateInternal(mapOf(),
                                                      mapOf(A, false),
                                                      mapOf(A, false),
                                                      setOf(A),
                                                      setOf(A));

    assertEquals(mapOf(), result.getMemory());
    assertEquals(mapOf(), result.getDisk());
  }

  @Test
  public void updateInternal_twoNewValues() {
    UpdateResult result = FeatureFlags.updateInternal(mapOf(A, true,
                                                            B, false),
                                                      mapOf(),
                                                      mapOf(),
                                                      setOf(),
                                                      setOf());

    assertEquals(mapOf(), result.getMemory());
    assertEquals(mapOf(A, true, B, false), result.getDisk());
  }

  @Test
  public void updateInternal_replaceOneOfTwoValues() {
    UpdateResult result = FeatureFlags.updateInternal(mapOf(A, true,
                                                            B, false),
                                                      mapOf(A, true,
                                                            B, true),
                                                      mapOf(A, true,
                                                            B, true),
                                                      setOf(),
                                                      setOf());

    assertEquals(mapOf(A, true, B, true), result.getMemory());
    assertEquals(mapOf(A, true, B, false), result.getDisk());
  }

  private static String key(String s) {
    return "android." + s;
  }

  private static <V> Set<V> setOf(V... values) {
    return new HashSet<>(Arrays.asList(values));
  }

  private static <K, V> Map<K, V> mapOf() {
    return new HashMap<>();
  }

  private static <K, V> Map<K, V> mapOf(K k, V v) {
    return new HashMap<K, V>() {{
      put(k, v);
    }};
  }

  private static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2) {
    return new HashMap<K, V>() {{
      put(k1, v1);
      put(k2, v2);
    }};
  }
}
