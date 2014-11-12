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
package org.whispersystems.jobqueue;

import org.whispersystems.jobqueue.requirements.Requirement;

import java.io.Serializable;
import java.util.List;

public abstract class Job implements Serializable {

  private final JobParameters parameters;

  private transient long persistentId;
  private transient int  runIteration;

  public Job(JobParameters parameters) {
    this.parameters = parameters;
  }

  public List<Requirement> getRequirements() {
    return parameters.getRequirements();
  }

  public boolean isRequirementsMet() {
    for (Requirement requirement : parameters.getRequirements()) {
      if (!requirement.isPresent()) return false;
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

  public void setPersistentId(long persistentId) {
    this.persistentId = persistentId;
  }

  public long getPersistentId() {
    return persistentId;
  }

  public int getRunIteration() {
    return runIteration;
  }

  public void setRunIteration(int runIteration) {
    this.runIteration = runIteration;
  }

  public abstract void onAdded();
  public abstract void onRun() throws Exception;
  public abstract boolean onShouldRetry(Exception exception);
  public abstract void onCanceled();



}
