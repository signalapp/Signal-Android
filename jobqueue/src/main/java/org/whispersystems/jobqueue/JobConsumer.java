package org.whispersystems.jobqueue;

import org.whispersystems.jobqueue.persistence.PersistentStorage;

public class JobConsumer extends Thread {

  private final JobQueue          jobQueue;
  private final PersistentStorage persistentStorage;

  public JobConsumer(String name, JobQueue jobQueue, PersistentStorage persistentStorage) {
    super(name);
    this.jobQueue          = jobQueue;
    this.persistentStorage = persistentStorage;
  }

  @Override
  public void run() {
    while (true) {
      Job job = jobQueue.getNext();

      if (!runJob(job)) {
        job.onCanceled();
      }

      if (job.isPersistent()) {
        persistentStorage.remove(job.getPersistentId());
      }

      if (job.getGroupId() != null) {
        jobQueue.setGroupIdAvailable(job.getGroupId());
      }
    }
  }

  private boolean runJob(Job job) {
    int retryCount = job.getRetryCount();

    for (int i=retryCount;i>0;i--) {
      try {
        job.onRun();
        return true;
      } catch (Throwable throwable) {
        if (!job.onShouldRetry(throwable)) {
          return false;
        }
      }
    }

    return false;
  }

}
