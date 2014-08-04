package org.whispersystems.jobqueue.util;

import org.whispersystems.jobqueue.requirements.Requirement;

import java.util.concurrent.atomic.AtomicBoolean;

public class MockRequirement implements Requirement {

  private AtomicBoolean present;

  public MockRequirement(boolean present) {
    this.present = new AtomicBoolean(present);
  }

  public void setPresent(boolean present) {
    this.present.set(present);
  }

  @Override
  public boolean isPresent() {
    return present.get();
  }
}
