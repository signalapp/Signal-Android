package org.whispersystems.jobqueue;

import org.whispersystems.jobqueue.requirements.Requirement;

import java.io.Serializable;
import java.util.List;

public abstract class Job implements Serializable {

  private final JobParameters parameters;

  private transient long persistentId;

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

  public abstract void onAdded();
  public abstract void onRun() throws Throwable;
  public abstract void onCanceled();
  public abstract boolean onShouldRetry(Throwable throwable);


}
