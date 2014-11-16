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

import android.content.Context;
import android.util.Log;

import org.whispersystems.jobqueue.dependencies.DependencyInjector;
import org.whispersystems.jobqueue.persistence.JobSerializer;
import org.whispersystems.jobqueue.persistence.PersistentStorage;
import org.whispersystems.jobqueue.requirements.RequirementListener;
import org.whispersystems.jobqueue.requirements.RequirementProvider;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A JobManager allows you to enqueue {@link org.whispersystems.jobqueue.Job} tasks
 * that are executed once a Job's {@link org.whispersystems.jobqueue.requirements.Requirement}s
 * are met.
 */
public class JobManager implements RequirementListener {

  private final JobQueue      jobQueue           = new JobQueue();
  private final Executor      eventExecutor      = Executors.newSingleThreadExecutor();
  private final AtomicBoolean hasLoadedEncrypted = new AtomicBoolean(false);

  private final PersistentStorage         persistentStorage;
  private final List<RequirementProvider> requirementProviders;
  private final DependencyInjector        dependencyInjector;

  private JobManager(Context context, String name,
                     List<RequirementProvider> requirementProviders,
                     DependencyInjector dependencyInjector,
                     JobSerializer jobSerializer, int consumers)
  {
    this.persistentStorage    = new PersistentStorage(context, name, jobSerializer, dependencyInjector);
    this.requirementProviders = requirementProviders;
    this.dependencyInjector   = dependencyInjector;

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
   * @return a {@link org.whispersystems.jobqueue.JobManager.Builder} used to construct a JobManager.
   */
  public static Builder newBuilder(Context context) {
    return new Builder(context);
  }

  /**
   * Returns a {@link org.whispersystems.jobqueue.requirements.RequirementProvider} registered with
   * the JobManager by name.
   *
   * @param name The name of the registered {@link org.whispersystems.jobqueue.requirements.RequirementProvider}
   * @return The RequirementProvider, or null if no provider is registered with that name.
   */
  public RequirementProvider getRequirementProvider(String name) {
    for (RequirementProvider provider : requirementProviders) {
      if (provider.getName().equals(name)) {
        return provider;
      }
    }

    return null;
  }

  public void setEncryptionKeys(EncryptionKeys keys) {
    if (hasLoadedEncrypted.compareAndSet(false, true)) {
      eventExecutor.execute(new LoadTask(keys));
    }
  }

  /**
   * Queue a {@link org.whispersystems.jobqueue.Job} to be executed.
   *
   * @param job The Job to be executed.
   */
  public void add(final Job job) {
    eventExecutor.execute(new Runnable() {
      @Override
      public void run() {
        try {
          if (job.isPersistent()) {
            persistentStorage.store(job);
          }

          if (dependencyInjector != null) {
            dependencyInjector.injectDependencies(job);
          }

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
     * A name for the {@link org.whispersystems.jobqueue.JobManager}. This is a required parameter,
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
     * The {@link org.whispersystems.jobqueue.requirements.RequirementProvider}s to register with this
     * JobManager.  Optional. Each {@link org.whispersystems.jobqueue.requirements.Requirement} an
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
     * The {@link org.whispersystems.jobqueue.dependencies.DependencyInjector} to use for injecting
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
     * The {@link org.whispersystems.jobqueue.persistence.JobSerializer} to use for persistent Jobs.
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
