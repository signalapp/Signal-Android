package org.thoughtcrime.securesms.jobmanager.impl;

import org.thoughtcrime.securesms.jobs.MinimalJobSpec;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * A {@link Predicate} that will only run jobs with the provided factory keys.
 */
public final class FactoryJobPredicate implements Predicate<MinimalJobSpec> {

  private final Set<String> factories;

  public FactoryJobPredicate(String... factories) {
    this.factories = new HashSet<>(Arrays.asList(factories));
  }

  @Override
  public boolean test(MinimalJobSpec minimalJobSpec) {
    return factories.contains(minimalJobSpec.getFactoryKey());
  }
}