package org.thoughtcrime.securesms.jobmanager.impl;

import android.app.Application;
import android.app.job.JobInfo;
import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.jobmanager.Constraint;

public class NetworkOrCellServiceConstraint implements Constraint {

  public static final String KEY = "NetworkOrCellServiceConstraint";

  private final NetworkConstraint     networkConstraint;
  private final CellServiceConstraint serviceConstraint;

  public NetworkOrCellServiceConstraint(@NonNull Application application) {
    networkConstraint = new NetworkConstraint.Factory(application).create();
    serviceConstraint = new CellServiceConstraint.Factory(application).create();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public boolean isMet() {
    return networkConstraint.isMet() || serviceConstraint.isMet();
  }

  @Override
  public void applyToJobInfo(@NonNull JobInfo.Builder jobInfoBuilder) {
  }

  public static class Factory implements Constraint.Factory<NetworkOrCellServiceConstraint> {

    private final Application application;

    public Factory(@NonNull Application application) {
      this.application = application;
    }

    @Override
    public NetworkOrCellServiceConstraint create() {
      return new NetworkOrCellServiceConstraint(application);
    }
  }
}
