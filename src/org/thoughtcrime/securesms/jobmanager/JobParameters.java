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

import org.thoughtcrime.securesms.jobmanager.requirements.NetworkBackoffRequirement;
import org.thoughtcrime.securesms.jobmanager.requirements.NetworkRequirement;
import org.thoughtcrime.securesms.jobmanager.requirements.Requirement;
import org.thoughtcrime.securesms.jobs.requirements.NetworkOrServiceRequirement;
import org.thoughtcrime.securesms.jobs.requirements.SqlCipherMigrationRequirement;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * The set of parameters that describe a {@link org.thoughtcrime.securesms.jobmanager.Job}.
 */
public class JobParameters implements Serializable {

  private static final long serialVersionUID = 4880456378402584584L;

  private final List<Requirement> requirements;
  private final boolean           requiresNetwork;
  private final boolean           requiresSqlCipher;
  private final int               retryCount;
  private final long              retryUntil;
  private final String            groupId;
  private final boolean           ignoreDuplicates;

  private JobParameters(String groupId,
                        boolean ignoreDuplicates,
                        boolean requiresNetwork,
                        boolean requiresSqlCipher,
                        int retryCount,
                        long retryUntil)
  {
    this.groupId           = groupId;
    this.ignoreDuplicates  = ignoreDuplicates;
    this.requirements      = Collections.emptyList();
    this.requiresNetwork   = requiresNetwork;
    this.requiresSqlCipher = requiresSqlCipher;
    this.retryCount        = retryCount;
    this.retryUntil        = retryUntil;
  }

  public boolean shouldIgnoreDuplicates() {
    return ignoreDuplicates;
  }

  public boolean requiresNetwork() {
    return requiresNetwork || hasNetworkRequirement(requirements);
  }

  public boolean requiresSqlCipher() {
    return requiresSqlCipher || hasSqlCipherRequirement(requirements);
  }

  private boolean hasNetworkRequirement(List<Requirement> requirements) {
    if (requirements == null || requirements.size() == 0) return false;

    for (Requirement requirement : requirements) {
      if (requirement instanceof NetworkRequirement          ||
          requirement instanceof NetworkOrServiceRequirement ||
          requirement instanceof NetworkBackoffRequirement)
      {
        return true;
      }
    }

    return false;
  }

  private boolean hasSqlCipherRequirement(List<Requirement> requirements) {
    if (requirements == null || requirements.size() == 0) return false;

    for (Requirement requirement : requirements) {
      if (requirement instanceof SqlCipherMigrationRequirement) {
        return true;
      }
    }

    return false;
  }

  public int getRetryCount() {
    return retryCount;
  }

  public long getRetryUntil() {
    return retryUntil;
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
    private int               retryCount           = 100;
    private long              retryDuration        = 0;
    private String            groupId              = null;
    private boolean           ignoreDuplicates     = false;
    private boolean           requiresNetwork      = false;
    private boolean           requiresSqlCipher    = false;

    public Builder withNetworkRequirement() {
      requiresNetwork = true;
      return this;
    }

    @Deprecated
    public Builder withSqlCipherRequirement() {
      requiresSqlCipher = true;
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
      this.retryCount    = retryCount;
      this.retryDuration = 0;
      return this;
    }

    /**
     * Specify for how long we should keep retrying this job. Ignored if retryCount is set.
     * @param duration The duration (in ms) for how long we should keep retrying this job for.
     * @return the builder
     */
    public Builder withRetryDuration(long duration) {
      this.retryDuration = duration;
      this.retryCount    = 0;
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
     * If true, only one job with this groupId can be active at a time. If a job with the same
     * groupId is already running, then subsequent jobs will be ignored silently. Only has an effect
     * if a groupId has been specified via {@link #withGroupId(String)}.
     * <p />
     * Defaults to false.
     *
     * @param ignoreDuplicates Whether to ignore duplicates.
     * @return the builder
     */
    public Builder withDuplicatesIgnored(boolean ignoreDuplicates) {
      this.ignoreDuplicates = ignoreDuplicates;
      return this;
    }

    /**
     * @return the JobParameters instance that describes a Job.
     */
    public JobParameters create() {
      return new JobParameters(groupId, ignoreDuplicates, requiresNetwork, requiresSqlCipher, retryCount, System.currentTimeMillis() + retryDuration);
    }
  }
}
