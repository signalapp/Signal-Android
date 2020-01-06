package org.thoughtcrime.securesms.jobmanager;

import android.app.job.JobInfo;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

public interface Constraint {

  boolean isMet();

  @NonNull String getFactoryKey();

  @RequiresApi(26)
  void applyToJobInfo(@NonNull JobInfo.Builder jobInfoBuilder);

  interface Factory<T extends Constraint> {
    T create();
  }
}
