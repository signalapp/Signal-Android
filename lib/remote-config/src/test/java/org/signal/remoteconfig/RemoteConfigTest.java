package org.signal.remoteconfig;

import org.junit.Test;
import org.signal.remoteconfig.RemoteConfig.ConfigChange;
import org.signal.remoteconfig.RemoteConfig.UpdateResult;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RemoteConfigTest {

  private static final String A = "A";
  private static final String B = "B";

  @Test
  public void updateInternal_newValue_ignoreNotInRemoteCapable() {
    UpdateResult result = RemoteConfig.updateInternal(Map.of(A, true,
                                                            B, true),
                                                      Map.of(),
                                                      Map.of(),
                                                      Set.of(A),
                                                      Set.of(),
                                                      Set.of());

    assertEquals(Map.of(), result.getMemory());
    assertEquals(Map.of(A, true), result.getDisk());
    assertTrue(result.getMemoryChanges().isEmpty());
  }

  @Test
  public void updateInternal_newValue() {
    UpdateResult result = RemoteConfig.updateInternal(Map.of(A, true),
                                                      Map.of(),
                                                      Map.of(),
                                                      Set.of(A),
                                                      Set.of(),
                                                      Set.of());

    assertEquals(Map.of(), result.getMemory());
    assertEquals(Map.of(A, true), result.getDisk());
    assertTrue(result.getMemoryChanges().isEmpty());
  }

  @Test
  public void updateInternal_newValue_hotSwap() {
    UpdateResult result = RemoteConfig.updateInternal(Map.of(A, true),
                                                      Map.of(),
                                                      Map.of(),
                                                      Set.of(A),
                                                      Set.of(A),
                                                      Set.of());

    assertEquals(Map.of(A, true), result.getMemory());
    assertEquals(Map.of(A, true), result.getDisk());
    assertEquals(new ConfigChange(null, true), result.getMemoryChanges().get(A));
  }

  @Test
  public void updateInternal_newValue_hotSwap_integer() {
    UpdateResult result = RemoteConfig.updateInternal(Map.of(A, 1),
                                                      Map.of(),
                                                      Map.of(),
                                                      Set.of(A),
                                                      Set.of(A),
                                                      Set.of());

    assertEquals(Map.of(A, 1), result.getMemory());
    assertEquals(Map.of(A, 1), result.getDisk());
    assertEquals(new ConfigChange(null, 1), result.getMemoryChanges().get(A));
  }

  @Test
  public void updateInternal_newValue_sticky() {
    UpdateResult result = RemoteConfig.updateInternal(Map.of(A, true),
                                                      Map.of(),
                                                      Map.of(),
                                                      Set.of(A),
                                                      Set.of(),
                                                      Set.of(A));

    assertEquals(Map.of(), result.getMemory());
    assertEquals(Map.of(A, true), result.getDisk());
    assertTrue(result.getMemoryChanges().isEmpty());
  }

  @Test
  public void updateInternal_newValue_hotSwap_sticky() {
    UpdateResult result = RemoteConfig.updateInternal(Map.of(A, true),
                                                      Map.of(),
                                                      Map.of(),
                                                      Set.of(A),
                                                      Set.of(A),
                                                      Set.of(A));

    assertEquals(Map.of(A, true), result.getMemory());
    assertEquals(Map.of(A, true), result.getDisk());
    assertEquals(new ConfigChange(null, true), result.getMemoryChanges().get(A));
  }

  @Test
  public void updateInternal_replaceValue() {
    UpdateResult result = RemoteConfig.updateInternal(Map.of(A, true),
                                                      Map.of(A, false),
                                                      Map.of(A, false),
                                                      Set.of(A),
                                                      Set.of(),
                                                      Set.of());

    assertEquals(Map.of(A, false), result.getMemory());
    assertEquals(Map.of(A, true), result.getDisk());
    assertTrue(result.getMemoryChanges().isEmpty());
  }

  @Test
  public void updateInternal_replaceValue_integer() {
    UpdateResult result = RemoteConfig.updateInternal(Map.of(A, 2),
                                                      Map.of(A, 1),
                                                      Map.of(A, 1),
                                                      Set.of(A),
                                                      Set.of(),
                                                      Set.of());

    assertEquals(Map.of(A, 1), result.getMemory());
    assertEquals(Map.of(A, 2), result.getDisk());
    assertTrue(result.getMemoryChanges().isEmpty());
  }

  @Test
  public void updateInternal_replaceValue_hotSwap() {
    UpdateResult result = RemoteConfig.updateInternal(Map.of(A, true),
                                                      Map.of(A, false),
                                                      Map.of(A, false),
                                                      Set.of(A),
                                                      Set.of(A),
                                                      Set.of());

    assertEquals(Map.of(A, true), result.getMemory());
    assertEquals(Map.of(A, true), result.getDisk());
    assertEquals(new ConfigChange(false, true), result.getMemoryChanges().get(A));
  }

  @Test
  public void updateInternal_replaceValue_hotSwa_integer() {
    UpdateResult result = RemoteConfig.updateInternal(Map.of(A, 2),
                                                      Map.of(A, 1),
                                                      Map.of(A, 1),
                                                      Set.of(A),
                                                      Set.of(A),
                                                      Set.of());

    assertEquals(Map.of(A, 2), result.getMemory());
    assertEquals(Map.of(A, 2), result.getDisk());
    assertEquals(new ConfigChange(1, 2), result.getMemoryChanges().get(A));
  }

  @Test
  public void updateInternal_replaceValue_hotSwap_stickyChange() {
    UpdateResult result = RemoteConfig.updateInternal(Map.of(A, true),
                                                      Map.of(A, false),
                                                      Map.of(A, false),
                                                      Set.of(A),
                                                      Set.of(A),
                                                      Set.of(A));

    assertEquals(Map.of(A, true), result.getMemory());
    assertEquals(Map.of(A, true), result.getDisk());
    assertEquals(new ConfigChange(false, true), result.getMemoryChanges().get(A));
  }

  @Test
  public void updateInternal_replaceValue_hotSwap_stickyIgnore_memoryAndDisk() {
    UpdateResult result = RemoteConfig.updateInternal(Map.of(A, false),
                                                      Map.of(A, true),
                                                      Map.of(A, true),
                                                      Set.of(A),
                                                      Set.of(A),
                                                      Set.of(A));

    assertEquals(Map.of(A, true), result.getMemory());
    assertEquals(Map.of(A, true), result.getDisk());
    assertTrue(result.getMemoryChanges().isEmpty());
  }

  @Test
  public void updateInternal_removeValue() {
    UpdateResult result = RemoteConfig.updateInternal(Map.of(),
                                                      Map.of(A, true),
                                                      Map.of(A, true),
                                                      Set.of(A),
                                                      Set.of(),
                                                      Set.of());

    assertEquals(Map.of(A, true), result.getMemory());
    assertEquals(Map.of(), result.getDisk());
    assertTrue(result.getMemoryChanges().isEmpty());
  }

  @Test
  public void updateInternal_removeValue_notRemoteCapable() {
    UpdateResult result = RemoteConfig.updateInternal(Map.of(A, true),
                                                      Map.of(A, true),
                                                      Map.of(A, true),
                                                      Set.of(),
                                                      Set.of(),
                                                      Set.of());

    assertEquals(Map.of(A, true), result.getMemory());
    assertEquals(Map.of(), result.getDisk());
    assertTrue(result.getMemoryChanges().isEmpty());
  }

  @Test
  public void updateInternal_removeValue_hotSwap() {
    UpdateResult result = RemoteConfig.updateInternal(Map.of(),
                                                      Map.of(A, true),
                                                      Map.of(A, true),
                                                      Set.of(A),
                                                      Set.of(A),
                                                      Set.of());

    assertEquals(Map.of(), result.getMemory());
    assertEquals(Map.of(), result.getDisk());
    assertEquals(new ConfigChange(true, null), result.getMemoryChanges().get(A));
  }

  @Test
  public void updateInternal_removeValue_hotSwap_notRemoteCapable() {
    UpdateResult result = RemoteConfig.updateInternal(Map.of(A, true),
                                                      Map.of(A, true),
                                                      Map.of(A, true),
                                                      Set.of(),
                                                      Set.of(A),
                                                      Set.of());

    assertEquals(Map.of(), result.getMemory());
    assertEquals(Map.of(), result.getDisk());
    assertEquals(new ConfigChange(true, null), result.getMemoryChanges().get(A));
  }

  @Test
  public void updateInternal_removeValue_stickyAlreadyEnabled() {
    UpdateResult result = RemoteConfig.updateInternal(Map.of(),
                                                      Map.of(A, true),
                                                      Map.of(A, true),
                                                      Set.of(A),
                                                      Set.of(),
                                                      Set.of(A));

    assertEquals(Map.of(A, true), result.getMemory());
    assertEquals(Map.of(A, true), result.getDisk());
    assertTrue(result.getMemoryChanges().isEmpty());
  }

  @Test
  public void updateInternal_removeValue_stickyNotEnabled() {
    UpdateResult result = RemoteConfig.updateInternal(Map.of(),
                                                      Map.of(A, false),
                                                      Map.of(A, false),
                                                      Set.of(A),
                                                      Set.of(),
                                                      Set.of(A));

    assertEquals(Map.of(A, false), result.getMemory());
    assertEquals(Map.of(), result.getDisk());
    assertTrue(result.getMemoryChanges().isEmpty());
  }

  @Test
  public void updateInternal_removeValue_stickyNotEnabled_notRemoteCapable() {
    UpdateResult result = RemoteConfig.updateInternal(Map.of(A, true),
                                                      Map.of(A, false),
                                                      Map.of(A, false),
                                                      Set.of(),
                                                      Set.of(),
                                                      Set.of(A));

    assertEquals(Map.of(A, false), result.getMemory());
    assertEquals(Map.of(), result.getDisk());
    assertTrue(result.getMemoryChanges().isEmpty());
  }

  @Test
  public void updateInternal_removeValue_hotSwap_stickyAlreadyEnabled() {
    UpdateResult result = RemoteConfig.updateInternal(Map.of(),
                                                      Map.of(A, true),
                                                      Map.of(A, true),
                                                      Set.of(A),
                                                      Set.of(A),
                                                      Set.of(A));

    assertEquals(Map.of(A, true), result.getMemory());
    assertEquals(Map.of(A, true), result.getDisk());
    assertTrue(result.getMemoryChanges().isEmpty());
  }

  @Test
  public void updateInternal_removeValue_hotSwap_stickyAlreadyEnabled_notRemoteCapable() {
    UpdateResult result = RemoteConfig.updateInternal(Map.of(A, true),
                                                      Map.of(A, true),
                                                      Map.of(A, true),
                                                      Set.of(),
                                                      Set.of(A),
                                                      Set.of(A));

    assertEquals(Map.of(A, true), result.getMemory());
    assertEquals(Map.of(A, true), result.getDisk());
    assertTrue(result.getMemoryChanges().isEmpty());
  }

  @Test
  public void updateInternal_removeValue_hotSwap_stickyNotEnabled() {
    UpdateResult result = RemoteConfig.updateInternal(Map.of(),
                                                      Map.of(A, false),
                                                      Map.of(A, false),
                                                      Set.of(A),
                                                      Set.of(A),
                                                      Set.of(A));

    assertEquals(Map.of(), result.getMemory());
    assertEquals(Map.of(), result.getDisk());
    assertEquals(new ConfigChange(false, null), result.getMemoryChanges().get(A));
  }

  @Test
  public void updateInternal_removeValue_typeMismatch_hotSwap() {
    UpdateResult result = RemoteConfig.updateInternal(Map.of(A, "5"),
                                                      Map.of(A, true),
                                                      Map.of(A, true),
                                                      Set.of(A),
                                                      Set.of(A),
                                                      Set.of());

    assertEquals(Map.of(), result.getMemory());
    assertEquals(Map.of(), result.getDisk());
    assertEquals(new ConfigChange(true, null), result.getMemoryChanges().get(A));
  }

  @Test
  public void updateInternal_twoNewValues() {
    UpdateResult result = RemoteConfig.updateInternal(Map.of(A, true,
                                                            B, false),
                                                      Map.of(),
                                                      Map.of(),
                                                      Set.of(A, B),
                                                      Set.of(),
                                                      Set.of());

    assertEquals(Map.of(), result.getMemory());
    assertEquals(Map.of(A, true, B, false), result.getDisk());
    assertTrue(result.getMemoryChanges().isEmpty());
  }

  @Test
  public void updateInternal_replaceOneOfTwoValues() {
    UpdateResult result = RemoteConfig.updateInternal(Map.of(A, true,
                                                            B, false),
                                                      Map.of(A, true,
                                                            B, true),
                                                      Map.of(A, true,
                                                            B, true),
                                                      Set.of(A, B),
                                                      Set.of(),
                                                      Set.of());

    assertEquals(Map.of(A, true, B, true), result.getMemory());
    assertEquals(Map.of(A, true, B, false), result.getDisk());
    assertTrue(result.getMemoryChanges().isEmpty());
  }

  /** @noinspection StringOperationCanBeSimplified*/
  @Test
  public void computeChanges_generic() {
    Map<String, Object> oldMap = new HashMap<>() {{
      put("a", true);
      put("b", false);
      put("c", true);
      put("d", false);
      put("g", 5);
      put("h", 5);
      put("i", new String("parker")); // Need to use new String to avoid interning string constants
      put("j", "gwen");
    }};

    Map<String, Object> newMap = new HashMap<>() {{
      put("a", true);
      put("b", true);
      put("c", false);
      put("e", true);
      put("f", false);
      put("h", 7);
      put("i", new String("parker")); // Need to use new String to avoid interning string constants
      put("j", "stacy");
    }};

    Map<String, ConfigChange> changes = RemoteConfig.computeChanges(oldMap, newMap);

    assertFalse(changes.containsKey("a"));
    assertEquals(new ConfigChange(false, true), changes.get("b"));
    assertEquals(new ConfigChange(true, false), changes.get("c"));
    assertEquals(new ConfigChange(false, null), changes.get("d"));
    assertEquals(new ConfigChange(null, true), changes.get("e"));
    assertEquals(new ConfigChange(null, false), changes.get("f"));
    assertEquals(new ConfigChange(5, 7), changes.get("h"));
    assertFalse(changes.containsKey("i"));
    assertEquals(new ConfigChange("gwen", "stacy"), changes.get("j"));
  }
}
