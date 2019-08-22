package org.thoughtcrime.securesms.jobmanager;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.List;

class CompositeScheduler implements Scheduler {

  private final List<Scheduler> schedulers;

  CompositeScheduler(@NonNull Scheduler... schedulers) {
    this.schedulers = Arrays.asList(schedulers);
  }

  @Override
  public void schedule(long delay, @NonNull List<Constraint> constraints) {
    for (Scheduler scheduler : schedulers) {
      scheduler.schedule(delay, constraints);
    }
  }
}
