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
import java.util.LinkedList;
import java.util.List;

/**
 * The set of parameters that describe a {@link org.whispersystems.jobqueue.Job}.
 */
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

  /**
   * @return a builder used to construct JobParameters.
   */
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

    /**
     * Specify a {@link org.whispersystems.jobqueue.requirements.Requirement }that must be met
     * before the Job is executed.  May be called multiple times to register multiple requirements.
     * @param requirement The Requirement that must be met.
     * @return the builder.
     */
    public Builder withRequirement(Requirement requirement) {
      this.requirements.add(requirement);
      return this;
    }

    /**
     * Specify that the Job should be durably persisted to disk, so that it remains in the queue
     * across application restarts.
     * @return The builder.
     */
    public Builder withPersistence() {
      this.isPersistent = true;
      return this;
    }

    /**
     * Specify that the job should use encryption when durably persisted to disk.
     * @param encryptionKeys The keys to encrypt the serialized job with before persisting.
     * @return the builder.
     */
    public Builder withEncryption(EncryptionKeys encryptionKeys) {
      this.encryptionKeys = encryptionKeys;
      return this;
    }

    /**
     * Specify how many times the job should be retried if execution fails but onShouldRetry() returns
     * true.
     *
     * @param retryCount The number of times the job should be retried.
     * @return the builder.
     */
    public Builder withRetryCount(int retryCount) {
      this.retryCount = retryCount;
      return this;
    }

    /**
     * Specify a groupId the job should belong to.  Jobs with the same groupId are guaranteed to be
     * executed serially.
     *
     * @param groupId The job's groupId.
     * @return the builder.
     */
    public Builder withGroupId(String groupId) {
      this.groupId = groupId;
      return this;
    }

    /**
     * @return the JobParameters instance that describes a Job.
     */
    public JobParameters create() {
      return new JobParameters(requirements, isPersistent, groupId, encryptionKeys, retryCount);
    }
  }
}
