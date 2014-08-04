package org.whispersystems.jobqueue.util;

import org.whispersystems.jobqueue.requirements.Requirement;

public class PersistentMockRequirement implements Requirement {
  @Override
  public boolean isPresent() {
    return PersistentRequirement.getInstance().isPresent();
  }
}
