package org.thoughtcrime.securesms.jobmanager.persistence;

import androidx.annotation.NonNull;

import java.util.Objects;

public final class ConstraintSpec {

  private final String  jobSpecId;
  private final String  factoryKey;
  private final boolean memoryOnly;

  public ConstraintSpec(@NonNull String jobSpecId, @NonNull String factoryKey, boolean memoryOnly) {
    this.jobSpecId  = jobSpecId;
    this.factoryKey = factoryKey;
    this.memoryOnly = memoryOnly;
  }

  public String getJobSpecId() {
    return jobSpecId;
  }

  public String getFactoryKey() {
    return factoryKey;
  }

  public boolean isMemoryOnly() {
    return memoryOnly;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ConstraintSpec that = (ConstraintSpec) o;
    return Objects.equals(jobSpecId, that.jobSpecId)   &&
           Objects.equals(factoryKey, that.factoryKey) &&
           memoryOnly == that.memoryOnly;
  }

  @Override
  public int hashCode() {
    return Objects.hash(jobSpecId, factoryKey, memoryOnly);
  }

  @Override
  public @NonNull String toString() {
    return String.format("jobSpecId: JOB::%s | factoryKey: %s | memoryOnly: %b", jobSpecId, factoryKey, memoryOnly);
  }
}
