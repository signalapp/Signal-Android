package org.whispersystems.jobqueue.requirements;

import java.io.Serializable;

public interface Requirement extends Serializable {
  public boolean isPresent();
}
