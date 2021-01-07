package org.thoughtcrime.securesms.util;

import org.junit.Test;
import org.thoughtcrime.securesms.BaseUnitTest;
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
import static org.thoughtcrime.securesms.testutil.TestHelpers.mapOf;

public class FeatureFlagsTest extends BaseUnitTest {

  private static final String A = "A";
  private static final String B = "B";

  @Test
  public void updateInternal_newValue_ignoreNotInRemoteCapable() {
    UpdateResult result = FeatureFlags.updateInternal(mapOf(A, true,
                                                            B, true),
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
  public void updateInternal_newValue_hotSwap_integer() {
    UpdateResult result = FeatureFlags.updateInternal(mapOf(A, 1),
                                                      mapOf(),
                                                      mapOf(),
                                                      setOf(A),
                                                      setOf(A),
                                                      setOf());

    assertEquals(mapOf(A, 1), result.getMemory());
    assertEquals(mapOf(A, 1), result.getDisk());
    assertEquals(Change.CHANGED, result.getMemoryChanges().get(A));
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
  public void updateInternal_replaceValue_integer() {
    UpdateResult result = FeatureFlags.updateInternal(mapOf(A, 2),
                                                      mapOf(A, 1),
                                                      mapOf(A, 1),
                                                      setOf(A),
                                                      setOf(),
                                                      setOf());

    assertEquals(mapOf(A, 1), result.getMemory());
    assertEquals(mapOf(A, 2), result.getDisk());
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
  public void updateInternal_replaceValue_hotSwa_integer() {
    UpdateResult result = FeatureFlags.updateInternal(mapOf(A, 2),
                                                      mapOf(A, 1),
                                                      mapOf(A, 1),
                                                      setOf(A),
                                                      setOf(A),
                                                      setOf());

    assertEquals(mapOf(A, 2), result.getMemory());
    assertEquals(mapOf(A, 2), result.getDisk());
    assertEquals(Change.CHANGED, result.getMemoryChanges().get(A));
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
  public void updateInternal_removeValue_typeMismatch_hotSwap() {
    UpdateResult result = FeatureFlags.updateInternal(mapOf(A, "5"),
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
    Map<String, Object> oldMap = new HashMap<String, Object>() {{
      put("a", true);
      put("b", false);
      put("c", true);
      put("d", false);
      put("g", 5);
      put("h", 5);
      put("i", new String("parker")); // Need to use new String to avoid interning string constants
      put("j", "gwen");
    }};

    Map<String, Object> newMap = new HashMap<String, Object>() {{
      put("a", true);
      put("b", true);
      put("c", false);
      put("e", true);
      put("f", false);
      put("h", 7);
      put("i", new String("parker")); // Need to use new String to avoid interning string constants
      put("j", "stacy");
    }};

    Map<String, Change> changes = FeatureFlags.computeChanges(oldMap, newMap);

    assertFalse(changes.containsKey("a"));
    assertEquals(Change.ENABLED, changes.get("b"));
    assertEquals(Change.DISABLED, changes.get("c"));
    assertEquals(Change.REMOVED, changes.get("d"));
    assertEquals(Change.ENABLED, changes.get("e"));
    assertEquals(Change.DISABLED, changes.get("f"));
    assertEquals(Change.CHANGED, changes.get("h"));
    assertFalse(changes.containsKey("i"));
    assertEquals(Change.CHANGED, changes.get("j"));
  }

  private static <V> Set<V> setOf(V... values) {
    return new HashSet<>(Arrays.asList(values));
  }
}
