package org.thoughtcrime.securesms.jobmanager;

import android.app.job.JobInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

public interface Constraint {

  boolean isMet();

  @NonNull String getFactoryKey();

  @RequiresApi(26)
  void applyToJobInfo(@NonNull JobInfo.Builder jobInfoBuilder);

  /**
   * If you do something in {@link #applyToJobInfo} you should return something here.
   * <p>
   * It is sorted and concatenated with other constraints key parts to form a unique job id.
   */
  default @Nullable String getJobSchedulerKeyPart() {
    return null;
  }

  interface Factory<T extends Constraint> {
    T create();
  }
}
