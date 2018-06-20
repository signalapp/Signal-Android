/**
 * Copyright (C) 2014 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.jobmanager;

import android.os.PowerManager;

import org.thoughtcrime.securesms.jobmanager.requirements.Requirement;

import java.io.Serializable;
import java.util.List;

/**
 * An abstract class representing a unit of work that can be scheduled with
 * the JobManager. This should be extended to implement tasks.
 */
public abstract class Job implements Serializable {

  private final JobParameters parameters;

  private transient long                  persistentId;
  private transient int                   runIteration;
  private transient long                  lastRunTime;
  private transient PowerManager.WakeLock wakeLock;

  public Job(JobParameters parameters) {
    this.parameters = parameters;
  }

  public List<Requirement> getRequirements() {
    return parameters.getRequirements();
  }

  public boolean isRequirementsMet() {
    for (Requirement requirement : parameters.getRequirements()) {
      if (!requirement.isPresent(this)) return false;
    }

    return true;
  }

  public String getGroupId() {
    return parameters.getGroupId();
  }

  public boolean isPersistent() {
    return parameters.isPersistent();
  }

  public EncryptionKeys getEncryptionKeys() {
    return parameters.getEncryptionKeys();
  }

  public void setEncryptionKeys(EncryptionKeys keys) {
    parameters.setEncryptionKeys(keys);
  }

  public int getRetryCount() {
    return parameters.getRetryCount();
  }

  public long getRetryUntil() {
    return parameters.getRetryUntil();
  }

  public long getLastRunTime() {
    return lastRunTime;
  }

  public void resetRunStats() {
    runIteration = 0;
    lastRunTime  = 0;
  }

  public void setPersistentId(long persistentId) {
    this.persistentId = persistentId;
  }

  public long getPersistentId() {
    return persistentId;
  }

  public int getRunIteration() {
    return runIteration;
  }

  public boolean needsWakeLock() {
    return parameters.needsWakeLock();
  }

  public long getWakeLockTimeout() {
    return parameters.getWakeLockTimeout();
  }

  public void setWakeLock(PowerManager.WakeLock wakeLock) {
    this.wakeLock = wakeLock;
  }

  public PowerManager.WakeLock getWakeLock() {
    return this.wakeLock;
  }

  public void onRetry() {
    runIteration++;
    lastRunTime = System.currentTimeMillis();

    for (Requirement requirement : parameters.getRequirements()) {
      requirement.onRetry(this);
    }
  }

  /**
   * Called after a job has been added to the JobManager queue.  If it's a persistent job,
   * the state has been persisted to disk before this method is called.
   */
  public abstract void onAdded();

  /**
   * Called to actually execute the job.
   * @throws Exception
   */
  protected abstract void onRun() throws Exception;

  /**
   * If onRun() throws an exception, this method will be called to determine whether the
   * job should be retried.
   *
   * @param exception The exception onRun() threw.
   * @return true if onRun() should be called again, false otherwise.
   */
  public abstract boolean onShouldRetry(Exception exception);

  /**
   * Called if a job fails to run (onShouldRetry returned false, or the number of retries exceeded
   * the job's configured retry count.
   */
  public abstract void onCanceled();



}
