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

import android.content.Context;
import android.os.PowerManager;
import android.util.Log;

import org.thoughtcrime.securesms.jobmanager.dependencies.AggregateDependencyInjector;
import org.thoughtcrime.securesms.jobmanager.dependencies.DependencyInjector;
import org.thoughtcrime.securesms.jobmanager.persistence.JobSerializer;
import org.thoughtcrime.securesms.jobmanager.persistence.PersistentStorage;
import org.thoughtcrime.securesms.jobmanager.requirements.RequirementListener;
import org.thoughtcrime.securesms.jobmanager.requirements.RequirementProvider;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A JobManager allows you to enqueue {@link org.thoughtcrime.securesms.jobmanager.Job} tasks
 * that are executed once a Job's {@link org.thoughtcrime.securesms.jobmanager.requirements.Requirement}s
 * are met.
 */
public class JobManager implements RequirementListener {

  private final JobQueue      jobQueue           = new JobQueue();
  private final Executor      eventExecutor      = Executors.newSingleThreadExecutor();
  private final AtomicBoolean hasLoadedEncrypted = new AtomicBoolean(false);

  private final Context                     context;
  private final PersistentStorage           persistentStorage;
  private final List<RequirementProvider>   requirementProviders;
  private final AggregateDependencyInjector dependencyInjector;

  private JobManager(Context context, String name,
                     List<RequirementProvider> requirementProviders,
                     DependencyInjector dependencyInjector,
                     JobSerializer jobSerializer, int consumers)
  {
    this.context              = context;
    this.dependencyInjector   = new AggregateDependencyInjector(dependencyInjector);
    this.persistentStorage    = new PersistentStorage(context, name, jobSerializer, this.dependencyInjector);
    this.requirementProviders = requirementProviders;

    eventExecutor.execute(new LoadTask(null));

    if (requirementProviders != null && !requirementProviders.isEmpty()) {
      for (RequirementProvider provider : requirementProviders) {
        provider.setListener(this);
      }
    }

    for (int i=0;i<consumers;i++) {
      new JobConsumer("JobConsumer-" + i, jobQueue, persistentStorage).start();
    }
  }

  /**
   * @param context An Android {@link android.content.Context}.
   * @return a {@link org.thoughtcrime.securesms.jobmanager.JobManager.Builder} used to construct a JobManager.
   */
  public static Builder newBuilder(Context context) {
    return new Builder(context);
  }

  public void setEncryptionKeys(EncryptionKeys keys) {
    if (hasLoadedEncrypted.compareAndSet(false, true)) {
      eventExecutor.execute(new LoadTask(keys));
    }
  }

  /**
   * Queue a {@link org.thoughtcrime.securesms.jobmanager.Job} to be executed.
   *
   * @param job The Job to be executed.
   */
  public void add(final Job job) {
    if (job.needsWakeLock()) {
      job.setWakeLock(acquireWakeLock(context, job.toString(), job.getWakeLockTimeout()));
    }

    eventExecutor.execute(new Runnable() {
      @Override
      public void run() {
        try {
          if (job.isPersistent()) {
            persistentStorage.store(job);
          }

          dependencyInjector.injectDependencies(context, job);

          job.onAdded();
          jobQueue.add(job);
        } catch (IOException e) {
          Log.w("JobManager", e);
          job.onCanceled();
        }
      }
    });
  }

  @Override
  public void onRequirementStatusChanged() {
    eventExecutor.execute(new Runnable() {
      @Override
      public void run() {
        jobQueue.onRequirementStatusChanged();
      }
    });
  }

  private PowerManager.WakeLock acquireWakeLock(Context context, String name, long timeout) {
    PowerManager          powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    PowerManager.WakeLock wakeLock     = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, name);

    if (timeout == 0) wakeLock.acquire();
    else              wakeLock.acquire(timeout);

    return wakeLock;
  }

  private class LoadTask implements Runnable {

    private final EncryptionKeys keys;

    public LoadTask(EncryptionKeys keys) {
      this.keys = keys;
    }

    @Override
    public void run() {
      List<Job> pendingJobs;

      if (keys == null) pendingJobs = persistentStorage.getAllUnencrypted();
      else              pendingJobs = persistentStorage.getAllEncrypted(keys);

      jobQueue.addAll(pendingJobs);
    }
  }

  public static class Builder {
    private final Context                   context;
    private       String                    name;
    private       List<RequirementProvider> requirementProviders;
    private       DependencyInjector        dependencyInjector;
    private       JobSerializer             jobSerializer;
    private       int                       consumerThreads;

    Builder(Context context) {
      this.context         = context;
      this.consumerThreads = 5;
    }

    /**
     * A name for the {@link org.thoughtcrime.securesms.jobmanager.JobManager}. This is a required parameter,
     * and is linked to the durable queue used by persistent jobs.
     *
     * @param name The name for the JobManager to build.
     * @return The builder.
     */
    public Builder withName(String name) {
      this.name = name;
      return this;
    }

    /**
     * The {@link org.thoughtcrime.securesms.jobmanager.requirements.RequirementProvider}s to register with this
     * JobManager.  Optional. Each {@link org.thoughtcrime.securesms.jobmanager.requirements.Requirement} an
     * enqueued Job depends on should have a matching RequirementProvider registered here.
     *
     * @param requirementProviders The RequirementProviders
     * @return The builder.
     */
    public Builder withRequirementProviders(RequirementProvider... requirementProviders) {
      this.requirementProviders = Arrays.asList(requirementProviders);
      return this;
    }

    /**
     * The {@link org.thoughtcrime.securesms.jobmanager.dependencies.DependencyInjector} to use for injecting
     * dependencies into {@link Job}s. Optional. Injection occurs just before a Job's onAdded() callback, or
     * after deserializing a persistent job.
     *
     * @param dependencyInjector The injector to use.
     * @return The builder.
     */
    public Builder withDependencyInjector(DependencyInjector dependencyInjector) {
      this.dependencyInjector = dependencyInjector;
      return this;
    }

    /**
     * The {@link org.thoughtcrime.securesms.jobmanager.persistence.JobSerializer} to use for persistent Jobs.
     * Required if persistent Jobs are used.
     *
     * @param jobSerializer The serializer to use.
     * @return The builder.
     */
    public Builder withJobSerializer(JobSerializer jobSerializer) {
      this.jobSerializer = jobSerializer;
      return this;
    }

    /**
     * Set the number of threads dedicated to consuming Jobs from the queue and executing them.
     *
     * @param consumerThreads The number of threads.
     * @return The builder.
     */
    public Builder withConsumerThreads(int consumerThreads) {
      this.consumerThreads = consumerThreads;
      return this;
    }

    /**
     * @return A constructed JobManager.
     */
    public JobManager build() {
      if (name == null) {
        throw new IllegalArgumentException("You must specify a name!");
      }

      if (requirementProviders == null) {
        requirementProviders = new LinkedList<>();
      }

      return new JobManager(context, name, requirementProviders,
                            dependencyInjector, jobSerializer,
                            consumerThreads);
    }
  }

}
