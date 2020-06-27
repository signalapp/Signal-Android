package org.thoughtcrime.securesms.jobmanager.persistence;

import androidx.annotation.NonNull;

import java.util.Objects;

public final class DependencySpec {

  private final String  jobId;
  private final String  dependsOnJobId;
  private final boolean memoryOnly;

  public DependencySpec(@NonNull String jobId, @NonNull String dependsOnJobId, boolean memoryOnly) {
    this.jobId          = jobId;
    this.dependsOnJobId = dependsOnJobId;
    this.memoryOnly     = memoryOnly;
  }

  public @NonNull String getJobId() {
    return jobId;
  }

  public @NonNull String getDependsOnJobId() {
    return dependsOnJobId;
  }

  public boolean isMemoryOnly() {
    return memoryOnly;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DependencySpec that = (DependencySpec) o;
    return Objects.equals(jobId, that.jobId)                   &&
           Objects.equals(dependsOnJobId, that.dependsOnJobId) &&
           memoryOnly == that.memoryOnly;
  }

  @Override
  public int hashCode() {
    return Objects.hash(jobId, dependsOnJobId, memoryOnly);
  }

  @Override
  public @NonNull String toString() {
    return String.format("jobSpecId: JOB::%s | dependsOnJobSpecId: JOB::%s | memoryOnly: %b", jobId, dependsOnJobId, memoryOnly);
  }
}
