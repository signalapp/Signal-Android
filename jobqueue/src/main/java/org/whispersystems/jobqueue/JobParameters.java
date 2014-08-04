package org.whispersystems.jobqueue;

import org.whispersystems.jobqueue.requirements.Requirement;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;

public class JobParameters implements Serializable {

  private transient EncryptionKeys encryptionKeys;

  private final List<Requirement> requirements;
  private final boolean           isPersistent;
  private final int               retryCount;
  private final String            groupId;

  private JobParameters(List<Requirement> requirements,
                       boolean isPersistent, String groupId,
                       EncryptionKeys encryptionKeys,
                       int retryCount)
  {
    this.requirements   = requirements;
    this.isPersistent   = isPersistent;
    this.groupId        = groupId;
    this.encryptionKeys = encryptionKeys;
    this.retryCount     = retryCount;
  }

  public List<Requirement> getRequirements() {
    return requirements;
  }

  public boolean isPersistent() {
    return isPersistent;
  }

  public EncryptionKeys getEncryptionKeys() {
    return encryptionKeys;
  }

  public void setEncryptionKeys(EncryptionKeys encryptionKeys) {
    this.encryptionKeys = encryptionKeys;
  }

  public int getRetryCount() {
    return retryCount;
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public String getGroupId() {
    return groupId;
  }

  public static class Builder {
    private List<Requirement> requirements   = new LinkedList<>();
    private boolean           isPersistent   = false;
    private EncryptionKeys    encryptionKeys = null;
    private int               retryCount     = 100;
    private String            groupId        = null;

    public Builder withRequirement(Requirement requirement) {
      this.requirements.add(requirement);
      return this;
    }

    public Builder withPersistence() {
      this.isPersistent = true;
      return this;
    }

    public Builder withEncryption(EncryptionKeys encryptionKeys) {
      this.encryptionKeys = encryptionKeys;
      return this;
    }

    public Builder withRetryCount(int retryCount) {
      this.retryCount = retryCount;
      return this;
    }

    public Builder withGroupId(String groupId) {
      this.groupId = groupId;
      return this;
    }

    public JobParameters create() {
      return new JobParameters(requirements, isPersistent, groupId, encryptionKeys, retryCount);
    }
  }
}
