package org.thoughtcrime.securesms.jobmanager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Create a subclass of this to perform a migration on persisted {@link Job}s. A migration targets
 * a specific end version, and the assumption is that it can migrate jobs to that end version from
 * the previous version. The class will be provided a bundle of job data for each persisted job and
 * give back an updated version (if applicable).
 */
public abstract class JobMigration {

  private final int endVersion;

  protected JobMigration(int endVersion) {
    this.endVersion   = endVersion;
  }

  /**
   * Given a bundle of job data, return a bundle of job data that should be used in place of it.
   * You may obviously return the same object if you don't wish to change it.
   */
  protected abstract @NonNull JobData migrate(@NonNull JobData jobData);

  int getEndVersion() {
    return endVersion;
  }

  public static class JobData {

    private final String factoryKey;
    private final String queueKey;
    private final byte[] data;

    public JobData(@NonNull String factoryKey, @Nullable String queueKey, @Nullable byte[] data) {
      this.factoryKey = factoryKey;
      this.queueKey   = queueKey;
      this.data       = data;
    }

    public @NonNull JobData withFactoryKey(@NonNull String newFactoryKey) {
      return new JobData(newFactoryKey, queueKey, data);
    }

    public @NonNull JobData withQueueKey(@Nullable String newQueueKey) {
      return new JobData(factoryKey, newQueueKey, data);
    }

    public @NonNull JobData withData(@Nullable byte[] newData) {
      return new JobData(factoryKey, queueKey, newData);
    }

    public @NonNull String getFactoryKey() {
      return factoryKey;
    }

    public @Nullable String getQueueKey() {
      return queueKey;
    }

    public @NonNull byte[] getData() {
      return data;
    }
  }
}
