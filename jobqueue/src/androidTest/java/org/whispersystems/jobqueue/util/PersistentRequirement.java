package org.whispersystems.jobqueue.util;

import java.util.concurrent.atomic.AtomicBoolean;

public class PersistentRequirement {

  private AtomicBoolean present = new AtomicBoolean(false);

  private static final PersistentRequirement instance = new PersistentRequirement();

  public static PersistentRequirement getInstance() {
    return instance;
  }

  public void setPresent(boolean present) {
    this.present.set(present);
  }

  public boolean isPresent() {
    return present.get();
  }
}
