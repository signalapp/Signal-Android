package org.thoughtcrime.securesms.util;

import org.junit.Test;
import org.thoughtcrime.securesms.util.FeatureFlags.Change;
import org.thoughtcrime.securesms.util.FeatureFlags.UpdateResult;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class FeatureFlagsTest {

  private static final String A = "A";
  private static final String B = "B";

  @Test
  public void updateInternal_newValue_ignoreNotInRemoteCapable() {
    UpdateResult result = FeatureFlags.updateInternal(mapOf("A", true,
                                                            "B", true),
                                                      mapOf(),
                                                      mapOf(),
                                                      setOf("A"),
                                                      setOf(),
                                                      setOf());

    assertEquals(mapOf(), result.getMemory());
    assertEquals(mapOf("A", true), result.getDisk());
    assertTrue(result.getMemoryChanges().isEmpty());
  }

  @Test
  public void updateInternal_newValue() {
    UpdateResult result = FeatureFlags.updateInternal(mapOf(A, true),
                                                      mapOf(),
                                                      mapOf(),
                                                      setOf(A),
                                                      setOf(),
                                                      setOf());

    assertEquals(mapOf(), result.getMemory());
    assertEquals(mapOf(A, true), result.getDisk());
    assertTrue(result.getMemoryChanges().isEmpty());
  }

  @Test
  public void updateInternal_newValue_hotSwap() {
    UpdateResult result = FeatureFlags.updateInternal(mapOf(A, true),
                                                      mapOf(),
                                                      mapOf(),
                                                      setOf(A),
                                                      setOf(A),
                                                      setOf());

    assertEquals(mapOf(A, true), result.getMemory());
    assertEquals(mapOf(A, true), result.getDisk());
    assertEquals(Change.ENABLED, result.getMemoryChanges().get(A));
  }

  @Test
  public void updateInternal_newValue_sticky() {
    UpdateResult result = FeatureFlags.updateInternal(mapOf(A, true),
                                                      mapOf(),
                                                      mapOf(),
                                                      setOf(A),
                                                      setOf(),
                                                      setOf(A));

    assertEquals(mapOf(), result.getMemory());
    assertEquals(mapOf(A, true), result.getDisk());
    assertTrue(result.getMemoryChanges().isEmpty());
  }

  @Test
  public void updateInternal_newValue_hotSwap_sticky() {
    UpdateResult result = FeatureFlags.updateInternal(mapOf(A, true),
                                                      mapOf(),
                                                      mapOf(),
                                                      setOf(A),
                                                      setOf(A),
                                                      setOf(A));

    assertEquals(mapOf(A, true), result.getMemory());
    assertEquals(mapOf(A, true), result.getDisk());
    assertEquals(Change.ENABLED, result.getMemoryChanges().get(A));
  }

  @Test
  public void updateInternal_replaceValue() {
    UpdateResult result = FeatureFlags.updateInternal(mapOf(A, true),
                                                      mapOf(A, false),
                                                      mapOf(A, false),
                                                      setOf(A),
                                                      setOf(),
                                                      setOf());

    assertEquals(mapOf(A, false), result.getMemory());
    assertEquals(mapOf(A, true), result.getDisk());
    assertTrue(result.getMemoryChanges().isEmpty());
  }

  @Test
  public void updateInternal_replaceValue_hotSwap() {
    UpdateResult result = FeatureFlags.updateInternal(mapOf(A, true),
                                                      mapOf(A, false),
                                                      mapOf(A, false),
                                                      setOf(A),
                                                      setOf(A),
                                                      setOf());

    assertEquals(mapOf(A, true), result.getMemory());
    assertEquals(mapOf(A, true), result.getDisk());
    assertEquals(Change.ENABLED, result.getMemoryChanges().get(A));
  }

  @Test
  public void updateInternal_replaceValue_hotSwap_stickyChange() {
    UpdateResult result = FeatureFlags.updateInternal(mapOf(A, true),
                                                      mapOf(A, false),
                                                      mapOf(A, false),
                                                      setOf(A),
                                                      setOf(A),
                                                      setOf(A));

    assertEquals(mapOf(A, true), result.getMemory());
    assertEquals(mapOf(A, true), result.getDisk());
    assertEquals(Change.ENABLED, result.getMemoryChanges().get(A));
  }

  @Test
  public void updateInternal_replaceValue_hotSwap_stickyIgnore_memoryAndDisk() {
    UpdateResult result = FeatureFlags.updateInternal(mapOf(A, false),
                                                      mapOf(A, true),
                                                      mapOf(A, true),
                                                      setOf(A),
                                                      setOf(A),
                                                      setOf(A));

    assertEquals(mapOf(A, true), result.getMemory());
    assertEquals(mapOf(A, true), result.getDisk());
    assertTrue(result.getMemoryChanges().isEmpty());
  }

  @Test
  public void updateInternal_removeValue() {
    UpdateResult result = FeatureFlags.updateInternal(mapOf(),
                                                      mapOf(A, true),
                                                      mapOf(A, true),
                                                      setOf(A),
                                                      setOf(),
                                                      setOf());

    assertEquals(mapOf(A, true), result.getMemory());
    assertEquals(mapOf(), result.getDisk());
    assertTrue(result.getMemoryChanges().isEmpty());
  }

  @Test
  public void updateInternal_removeValue_notRemoteCapable() {
    UpdateResult result = FeatureFlags.updateInternal(mapOf(A, true),
                                                      mapOf(A, true),
                                                      mapOf(A, true),
                                                      setOf(),
                                                      setOf(),
                                                      setOf());

    assertEquals(mapOf(A, true), result.getMemory());
    assertEquals(mapOf(), result.getDisk());
    assertTrue(result.getMemoryChanges().isEmpty());
  }

  @Test
  public void updateInternal_removeValue_hotSwap() {
    UpdateResult result = FeatureFlags.updateInternal(mapOf(),
                                                      mapOf(A, true),
                                                      mapOf(A, true),
                                                      setOf(A),
                                                      setOf(A),
                                                      setOf());

    assertEquals(mapOf(), result.getMemory());
    assertEquals(mapOf(), result.getDisk());
    assertEquals(Change.REMOVED, result.getMemoryChanges().get(A));
  }

  @Test
  public void updateInternal_removeValue_hotSwap_notRemoteCapable() {
    UpdateResult result = FeatureFlags.updateInternal(mapOf(A, true),
                                                      mapOf(A, true),
                                                      mapOf(A, true),
                                                      setOf(),
                                                      setOf(A),
                                                      setOf());

    assertEquals(mapOf(), result.getMemory());
    assertEquals(mapOf(), result.getDisk());
    assertEquals(Change.REMOVED, result.getMemoryChanges().get(A));
  }

  @Test
  public void updateInternal_removeValue_stickyAlreadyEnabled() {
    UpdateResult result = FeatureFlags.updateInternal(mapOf(),
                                                      mapOf(A, true),
                                                      mapOf(A, true),
                                                      setOf(A),
                                                      setOf(),
                                                      setOf(A));

    assertEquals(mapOf(A, true), result.getMemory());
    assertEquals(mapOf(A, true), result.getDisk());
    assertTrue(result.getMemoryChanges().isEmpty());
  }

  @Test
  public void updateInternal_removeValue_stickyNotEnabled() {
    UpdateResult result = FeatureFlags.updateInternal(mapOf(),
                                                      mapOf(A, false),
                                                      mapOf(A, false),
                                                      setOf(A),
                                                      setOf(),
                                                      setOf(A));

    assertEquals(mapOf(A, false), result.getMemory());
    assertEquals(mapOf(), result.getDisk());
    assertTrue(result.getMemoryChanges().isEmpty());
  }

  @Test
  public void updateInternal_removeValue_stickyNotEnabled_notRemoteCapable() {
    UpdateResult result = FeatureFlags.updateInternal(mapOf(A, true),
                                                      mapOf(A, false),
                                                      mapOf(A, false),
                                                      setOf(),
                                                      setOf(),
                                                      setOf(A));

    assertEquals(mapOf(A, false), result.getMemory());
    assertEquals(mapOf(), result.getDisk());
    assertTrue(result.getMemoryChanges().isEmpty());
  }

  @Test
  public void updateInternal_removeValue_hotSwap_stickyAlreadyEnabled() {
    UpdateResult result = FeatureFlags.updateInternal(mapOf(),
                                                      mapOf(A, true),
                                                      mapOf(A, true),
                                                      setOf(A),
                                                      setOf(A),
                                                      setOf(A));

    assertEquals(mapOf(A, true), result.getMemory());
    assertEquals(mapOf(A, true), result.getDisk());
    assertTrue(result.getMemoryChanges().isEmpty());
  }

  @Test
  public void updateInternal_removeValue_hotSwap_stickyAlreadyEnabled_notRemoteCapable() {
    UpdateResult result = FeatureFlags.updateInternal(mapOf(A, true),
                                                      mapOf(A, true),
                                                      mapOf(A, true),
                                                      setOf(),
                                                      setOf(A),
                                                      setOf(A));

    assertEquals(mapOf(A, true), result.getMemory());
    assertEquals(mapOf(A, true), result.getDisk());
    assertTrue(result.getMemoryChanges().isEmpty());
  }

  @Test
  public void updateInternal_removeValue_hotSwap_stickyNotEnabled() {
    UpdateResult result = FeatureFlags.updateInternal(mapOf(),
                                                      mapOf(A, false),
                                                      mapOf(A, false),
                                                      setOf(A),
                                                      setOf(A),
                                                      setOf(A));

    assertEquals(mapOf(), result.getMemory());
    assertEquals(mapOf(), result.getDisk());
    assertEquals(Change.REMOVED, result.getMemoryChanges().get(A));
  }

  @Test
  public void updateInternal_twoNewValues() {
    UpdateResult result = FeatureFlags.updateInternal(mapOf(A, true,
                                                            B, false),
                                                      mapOf(),
                                                      mapOf(),
                                                      setOf(A, B),
                                                      setOf(),
                                                      setOf());

    assertEquals(mapOf(), result.getMemory());
    assertEquals(mapOf(A, true, B, false), result.getDisk());
    assertTrue(result.getMemoryChanges().isEmpty());
  }

  @Test
  public void updateInternal_replaceOneOfTwoValues() {
    UpdateResult result = FeatureFlags.updateInternal(mapOf(A, true,
                                                            B, false),
                                                      mapOf(A, true,
                                                            B, true),
                                                      mapOf(A, true,
                                                            B, true),
                                                      setOf(A, B),
                                                      setOf(),
                                                      setOf());

    assertEquals(mapOf(A, true, B, true), result.getMemory());
    assertEquals(mapOf(A, true, B, false), result.getDisk());
    assertTrue(result.getMemoryChanges().isEmpty());
  }

  @Test
  public void computeChanges_generic() {
    Map<String, Boolean> oldMap = new HashMap<String, Boolean>() {{
      put("a", true);
      put("b", false);
      put("c", true);
      put("d", false);
    }};

    Map<String, Boolean> newMap = new HashMap<String, Boolean>() {{
      put("a", true);
      put("b", true);
      put("c", false);
      put("e", true);
      put("f", false);
    }};

    Map<String, Change> changes = FeatureFlags.computeChanges(oldMap, newMap);

    assertFalse(changes.containsKey("a"));
    assertEquals(Change.ENABLED, changes.get("b"));
    assertEquals(Change.DISABLED, changes.get("c"));
    assertEquals(Change.REMOVED, changes.get("d"));
    assertEquals(Change.ENABLED, changes.get("e"));
    assertEquals(Change.DISABLED, changes.get("f"));
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
