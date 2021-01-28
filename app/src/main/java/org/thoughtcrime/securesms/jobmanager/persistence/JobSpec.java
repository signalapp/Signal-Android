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
  private final String  serializedData;
  private final String  serializedInputData;
  private final boolean isRunning;
  private final boolean memoryOnly;

  public JobSpec(@NonNull String id,
                 @NonNull String factoryKey,
                 @Nullable String queueKey,
                 long createTime,
                 long nextRunAttemptTime,
                 int runAttempt,
                 int maxAttempts,
                 long maxBackoff,
                 long lifespan,
                 @NonNull String serializedData,
                 @Nullable String serializedInputData,
                 boolean isRunning,
                 boolean memoryOnly)
  {
    this.id                  = id;
    this.factoryKey          = factoryKey;
    this.queueKey            = queueKey;
    this.createTime          = createTime;
    this.nextRunAttemptTime  = nextRunAttemptTime;
    this.maxBackoff          = maxBackoff;
    this.runAttempt          = runAttempt;
    this.maxAttempts         = maxAttempts;
    this.lifespan            = lifespan;
    this.serializedData      = serializedData;
    this.serializedInputData = serializedInputData;
    this.isRunning           = isRunning;
    this.memoryOnly          = memoryOnly;
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

  public long getLifespan() {
    return lifespan;
  }

  public @NonNull String getSerializedData() {
    return serializedData;
  }

  public @Nullable String getSerializedInputData() {
    return serializedInputData;
  }

  public boolean isRunning() {
    return isRunning;
  }

  public boolean isMemoryOnly() {
    return memoryOnly;
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
           isRunning == jobSpec.isRunning &&
           memoryOnly == jobSpec.memoryOnly &&
           Objects.equals(id, jobSpec.id) &&
           Objects.equals(factoryKey, jobSpec.factoryKey) &&
           Objects.equals(queueKey, jobSpec.queueKey) &&
           Objects.equals(serializedData, jobSpec.serializedData) &&
           Objects.equals(serializedInputData, jobSpec.serializedInputData);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, factoryKey, queueKey, createTime, nextRunAttemptTime, runAttempt, maxAttempts, maxBackoff, lifespan, serializedData, serializedInputData, isRunning, memoryOnly);
  }

  @SuppressLint("DefaultLocale")
  @Override
  public @NonNull String toString() {
    return String.format("id: JOB::%s | factoryKey: %s | queueKey: %s | createTime: %d | nextRunAttemptTime: %d | runAttempt: %d | maxAttempts: %d | maxBackoff: %d | lifespan: %d | isRunning: %b | memoryOnly: %b",
                         id, factoryKey, queueKey, createTime, nextRunAttemptTime, runAttempt, maxAttempts, maxBackoff, lifespan, isRunning, memoryOnly);
  }
}
