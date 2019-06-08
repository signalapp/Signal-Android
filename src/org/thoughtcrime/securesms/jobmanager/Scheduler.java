package org.thoughtcrime.securesms.jobmanager;

import android.support.annotation.NonNull;

import java.util.List;

public interface Scheduler {
  void schedule(long delay, @NonNull List<Constraint> constraints);
}
