package org.whispersystems.jobqueue.util;

public class PersistentResult {

  private final Object ADDED_LOCK    = new Object();
  private final Object RAN_LOCK      = new Object();
  private final Object CANCELED_LOCK = new Object();

  private boolean added    = false;
  private boolean ran      = false;
  private boolean canceled = false;

  private static final PersistentResult instance = new PersistentResult();

  public static PersistentResult getInstance() {
    return instance;
  }

  public void onAdded() {
    synchronized (ADDED_LOCK) {
      this.added = true;
      this.ADDED_LOCK.notifyAll();
    }
  }

  public void onRun() throws Exception {
    synchronized (RAN_LOCK) {
      this.ran = true;
    }
  }

  public void onCanceled() {
    synchronized (CANCELED_LOCK) {
      this.canceled = true;
    }
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

  public void reset() {
    synchronized (ADDED_LOCK) {
      this.added = false;
    }

    synchronized (RAN_LOCK) {
      this.ran = false;
    }

    synchronized (CANCELED_LOCK) {
      this.canceled = false;
    }
  }

}
