package org.thoughtcrime.securesms.jobmanager.persistence;

import android.annotation.SuppressLint;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

public final class JobSpec {

  private final String  id;
  private final String  factoryKey;
  private final String  queueKey;
  private final long    createTime;
  private final long    nextRunAttemptTime;
  private final int     runAttempt;
  private final int     maxAttempts;
  private final long    maxBackoff;
  private final long    lifespan;
  private final int     maxInstances;
  private final String  serializedData;
  private final boolean isRunning;

  public JobSpec(@NonNull String id,
                 @NonNull String factoryKey,
                 @Nullable String queueKey,
                 long createTime,
                 long nextRunAttemptTime,
                 int runAttempt,
                 int maxAttempts,
                 long maxBackoff,
                 long lifespan,
                 int maxInstances,
                 @NonNull String serializedData,
                 boolean isRunning)
  {
    this.id                 = id;
    this.factoryKey         = factoryKey;
    this.queueKey           = queueKey;
    this.createTime         = createTime;
    this.nextRunAttemptTime = nextRunAttemptTime;
    this.maxBackoff         = maxBackoff;
    this.runAttempt         = runAttempt;
    this.maxAttempts        = maxAttempts;
    this.lifespan           = lifespan;
    this.maxInstances       = maxInstances;
    this.serializedData     = serializedData;
    this.isRunning          = isRunning;
  }

  public @NonNull String getId() {
    return id;
  }

  public @NonNull String getFactoryKey() {
    return factoryKey;
  }

  public @Nullable String getQueueKey() {
    return queueKey;
  }

  public long getCreateTime() {
    return createTime;
  }

  public long getNextRunAttemptTime() {
    return nextRunAttemptTime;
  }

  public int getRunAttempt() {
    return runAttempt;
  }

  public int getMaxAttempts() {
    return maxAttempts;
  }

  public long getMaxBackoff() {
    return maxBackoff;
  }

  public int getMaxInstances() {
    return maxInstances;
  }

  public long getLifespan() {
    return lifespan;
  }

  public @NonNull String getSerializedData() {
    return serializedData;
  }

  public boolean isRunning() {
    return isRunning;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    JobSpec jobSpec = (JobSpec) o;
    return createTime == jobSpec.createTime &&
           nextRunAttemptTime == jobSpec.nextRunAttemptTime &&
           runAttempt == jobSpec.runAttempt &&
           maxAttempts == jobSpec.maxAttempts &&
           maxBackoff == jobSpec.maxBackoff &&
           lifespan == jobSpec.lifespan &&
           maxInstances == jobSpec.maxInstances &&
           isRunning == jobSpec.isRunning &&
           Objects.equals(id, jobSpec.id) &&
           Objects.equals(factoryKey, jobSpec.factoryKey) &&
           Objects.equals(queueKey, jobSpec.queueKey) &&
           Objects.equals(serializedData, jobSpec.serializedData);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, factoryKey, queueKey, createTime, nextRunAttemptTime, runAttempt, maxAttempts, maxBackoff, lifespan, maxInstances, serializedData, isRunning);
  }

  @SuppressLint("DefaultLocale")
  @Override
  public @NonNull String toString() {
    return String.format("id: %s | factoryKey: %s | queueKey: %s | createTime: %d | nextRunAttemptTime: %d | runAttempt: %d | maxAttempts: %d | maxBackoff: %d | maxInstances: %d | lifespan: %d | isRunning: %b | data: %s",
                         id, factoryKey, queueKey, createTime, nextRunAttemptTime, runAttempt, maxAttempts, maxBackoff, maxInstances, lifespan, isRunning, serializedData);
  }
}
