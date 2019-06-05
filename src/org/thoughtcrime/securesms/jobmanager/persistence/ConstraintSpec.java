package org.thoughtcrime.securesms.jobmanager.persistence;

import androidx.annotation.NonNull;

import java.util.Objects;

public final class ConstraintSpec {

  private final String jobSpecId;
  private final String factoryKey;

  public ConstraintSpec(@NonNull String jobSpecId, @NonNull String factoryKey) {
    this.jobSpecId  = jobSpecId;
    this.factoryKey = factoryKey;
  }

  public String getJobSpecId() {
    return jobSpecId;
  }

  public String getFactoryKey() {
    return factoryKey;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ConstraintSpec that = (ConstraintSpec) o;
    return Objects.equals(jobSpecId, that.jobSpecId) &&
           Objects.equals(factoryKey, that.factoryKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(jobSpecId, factoryKey);
  }

  @Override
  public @NonNull String toString() {
    return String.format("jobSpecId: %s | factoryKey: %s", jobSpecId, factoryKey);
  }
}
