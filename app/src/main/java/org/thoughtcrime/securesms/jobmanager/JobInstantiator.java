package org.thoughtcrime.securesms.jobmanager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

class JobInstantiator {

  private final Map<String, Job.Factory> jobFactories;

  JobInstantiator(@NonNull Map<String, Job.Factory> jobFactories) {
    this.jobFactories = new HashMap<>(jobFactories);
  }

  public @NonNull Job instantiate(@NonNull String jobFactoryKey, @NonNull Job.Parameters parameters, @Nullable byte[] data) {
    Job.Factory factory = jobFactories.get(jobFactoryKey);
    if (factory != null) {
      Job job = factory.create(parameters, data);

      if (!job.getId().equals(parameters.getId())) {
        throw new AssertionError("Parameters not supplied to job during creation");
      }

      return job;
    } else {
      throw new IllegalStateException("Tried to instantiate a job with key '" + jobFactoryKey + "', but no matching factory was found.");
    }
  }
}
