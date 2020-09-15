package org.thoughtcrime.securesms.jobmanager.impl;

import android.app.job.JobInfo;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import org.thoughtcrime.securesms.jobmanager.Constraint;

/**
 * Job constraint for determining whether or not the device is actively charging.
 */
public class ChargingConstraint implements Constraint {

  public static final String KEY = "ChargingConstraint";

  private ChargingConstraint() {
  }

  @Override
  public boolean isMet() {
    return ChargingConstraintObserver.isCharging();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @RequiresApi(26)
  @Override
  public void applyToJobInfo(@NonNull JobInfo.Builder jobInfoBuilder) {
    jobInfoBuilder.setRequiresCharging(true);
  }

  public static final class Factory implements Constraint.Factory<ChargingConstraint> {

    @Override
    public ChargingConstraint create() {
      return new ChargingConstraint();
    }
  }
}
