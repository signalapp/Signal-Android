package org.whispersystems.jobqueue.jobs;

import org.whispersystems.jobqueue.Job;
import org.whispersystems.jobqueue.JobParameters;

public class TestJob extends Job {

  private   final Object ADDED_LOCK    = new Object();
  protected final Object RAN_LOCK      = new Object();
  private   final Object CANCELED_LOCK = new Object();

  private   boolean added    = false;
  protected boolean ran      = false;
  private   boolean canceled = false;

  private Runnable runnable;

  public TestJob() {
    this(JobParameters.newBuilder().create());
  }

  public TestJob(JobParameters parameters) {
    super(parameters);
  }

  public TestJob(JobParameters parameters, Runnable runnable) {
    super(parameters);
    this.runnable = runnable;
  }

  @Override
  public void onAdded() {
    synchronized (ADDED_LOCK) {
      this.added = true;
      this.ADDED_LOCK.notifyAll();
    }
  }

  @Override
  public void onRun() throws Throwable {
    synchronized (RAN_LOCK) {
      this.ran = true;
    }

    if (runnable != null)
      runnable.run();
  }

  @Override
  public void onCanceled() {
    synchronized (CANCELED_LOCK) {
      this.canceled = true;
    }
  }

  @Override
  public boolean onShouldRetry(Throwable throwable) {
    return false;
  }

  public boolean isAdded() throws InterruptedException {
    synchronized (ADDED_LOCK) {
      if (!added) ADDED_LOCK.wait(1000);
      return added;
    }
  }

  public boolean isRan() throws InterruptedException {
    synchronized (RAN_LOCK) {
      if (!ran) RAN_LOCK.wait(1000);
      return ran;
    }
  }

  public boolean isCanceled() throws InterruptedException {
    synchronized (CANCELED_LOCK) {
      if (!canceled) CANCELED_LOCK.wait(1000);
      return canceled;
    }
  }
}
