package org.thoughtcrime.securesms.jobmanager.persistence;

import androidx.annotation.NonNull;

import java.util.Objects;

public final class DependencySpec {

  private final String jobId;
  private final String dependsOnJobId;

  public DependencySpec(@NonNull String jobId, @NonNull String dependsOnJobId) {
    this.jobId          = jobId;
    this.dependsOnJobId = dependsOnJobId;
  }

  public @NonNull String getJobId() {
    return jobId;
  }

  public @NonNull String getDependsOnJobId() {
    return dependsOnJobId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DependencySpec that = (DependencySpec) o;
    return Objects.equals(jobId, that.jobId) &&
           Objects.equals(dependsOnJobId, that.dependsOnJobId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(jobId, dependsOnJobId);
  }

  @Override
  public @NonNull String toString() {
    return String.format("jobSpecId: %s | dependsOnJobSpecId: %s", jobId, dependsOnJobId);
  }
}
