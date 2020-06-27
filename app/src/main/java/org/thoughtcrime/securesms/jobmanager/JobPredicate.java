package org.thoughtcrime.securesms.jobmanager;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.jobmanager.persistence.JobSpec;

public interface JobPredicate {
  JobPredicate NONE = jobSpec -> true;

  boolean shouldRun(@NonNull JobSpec jobSpec);
}
