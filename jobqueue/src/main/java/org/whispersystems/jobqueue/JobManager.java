package org.whispersystems.jobqueue;

import android.content.Context;
import android.util.Log;

import org.whispersystems.jobqueue.persistence.JobSerializer;
import org.whispersystems.jobqueue.persistence.PersistentStorage;
import org.whispersystems.jobqueue.requirements.RequirementListener;
import org.whispersystems.jobqueue.requirements.RequirementProvider;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class JobManager implements RequirementListener {

  private final JobQueue      jobQueue           = new JobQueue();
  private final Executor      eventExecutor      = Executors.newSingleThreadExecutor();
  private final AtomicBoolean hasLoadedEncrypted = new AtomicBoolean(false);

  private final PersistentStorage  persistentStorage;

  public JobManager(Context context, String name,
                    RequirementProvider requirementProvider,
                    JobSerializer jobSerializer, int consumers)
  {
    this.persistentStorage = new PersistentStorage(context, name, jobSerializer);
    eventExecutor.execute(new LoadTask(null));

    if (requirementProvider != null) {
      requirementProvider.setListener(this);
    }

    for (int i=0;i<consumers;i++) {
      new JobConsumer("JobConsumer-" + i, jobQueue, persistentStorage).start();
    }
  }

  public void setEncryptionKeys(EncryptionKeys keys) {
    if (hasLoadedEncrypted.compareAndSet(false, true)) {
      eventExecutor.execute(new LoadTask(keys));
    }
  }

  public void add(final Job job) {
    eventExecutor.execute(new Runnable() {
      @Override
      public void run() {
        try {
          if (job.isPersistent()) {
            persistentStorage.store(job);
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

}
