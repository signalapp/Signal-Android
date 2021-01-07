package org.thoughtcrime.securesms.util;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class FeatureFlags_ConsistencyTest {

  /**
   * Ensures developer makes decision on whether a flag should or should not be remote capable.
   */
  @Test
  public void no_flags_are_in_both_lists() {
    Set<String> intersection = SetUtil.intersection(FeatureFlags.REMOTE_CAPABLE,
                                                    FeatureFlags.NOT_REMOTE_CAPABLE);

    assertTrue(intersection.isEmpty());
  }

  /**
   * Ensures developer makes decision on whether a flag should or should not be remote capable.
   */
  @Test
  public void all_flags_are_in_one_list_or_another() {
    Set<String> flagsByReflection = Stream.of(FeatureFlags.class.getDeclaredFields())
                                          .filter(f -> f.getType() == String.class)
                                          .filter(f -> !f.getName().equals("TAG"))
                                          .map(f -> {
                                            try {
                                              f.setAccessible(true);
                                              return (String) f.get(null);
                                            } catch (IllegalAccessException e) {
                                              throw new AssertionError(e);
                                            }
                                          })
                                          .collect(Collectors.toSet());

    Set<String> flagsInBothSets = SetUtil.union(FeatureFlags.REMOTE_CAPABLE,
                                                FeatureFlags.NOT_REMOTE_CAPABLE);

    assertEquals(flagsInBothSets, flagsByReflection);
  }

  /**
   * Ensures we don't leave old feature flag values in the hot swap list.
   */
  @Test
  public void all_hot_swap_values_are_defined_capable_or_not() {
    Set<String> flagsInBothSets = SetUtil.union(FeatureFlags.REMOTE_CAPABLE,
                                                FeatureFlags.NOT_REMOTE_CAPABLE);

    assertTrue(flagsInBothSets.containsAll(FeatureFlags.HOT_SWAPPABLE));
  }

  /**
   * Ensures we don't leave old feature flag values in the sticky list.
   */
  @Test
  public void all_sticky_values_are_defined_capable_or_not() {
    Set<String> flagsInBothSets = SetUtil.union(FeatureFlags.REMOTE_CAPABLE,
                                                FeatureFlags.NOT_REMOTE_CAPABLE);

    assertTrue(flagsInBothSets.containsAll(FeatureFlags.STICKY));
  }

  /**
   * Ensures we don't release with forced values which is intended for local development only.
   */
  @Test
  public void no_values_are_forced() {
    assertTrue(FeatureFlags.FORCED_VALUES.isEmpty());
  }
}
