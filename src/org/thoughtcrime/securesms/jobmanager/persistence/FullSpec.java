package org.thoughtcrime.securesms.jobmanager.persistence;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Objects;

public final class FullSpec {

  private final JobSpec              jobSpec;
  private final List<ConstraintSpec> constraintSpecs;
  private final List<DependencySpec> dependencySpecs;

  public FullSpec(@NonNull JobSpec jobSpec,
                  @NonNull List<ConstraintSpec> constraintSpecs,
                  @NonNull List<DependencySpec> dependencySpecs)
  {
    this.jobSpec = jobSpec;
    this.constraintSpecs = constraintSpecs;
    this.dependencySpecs = dependencySpecs;
  }

  public @NonNull JobSpec getJobSpec() {
    return jobSpec;
  }

  public @NonNull List<ConstraintSpec> getConstraintSpecs() {
    return constraintSpecs;
  }

  public @NonNull List<DependencySpec> getDependencySpecs() {
    return dependencySpecs;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    FullSpec fullSpec = (FullSpec) o;
    return Objects.equals(jobSpec, fullSpec.jobSpec) &&
           Objects.equals(constraintSpecs, fullSpec.constraintSpecs) &&
           Objects.equals(dependencySpecs, fullSpec.dependencySpecs);
  }

  @Override
  public int hashCode() {
    return Objects.hash(jobSpec, constraintSpecs, dependencySpecs);
  }
}
