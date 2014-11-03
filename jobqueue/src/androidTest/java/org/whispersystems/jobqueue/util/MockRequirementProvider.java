package org.whispersystems.jobqueue.util;

import org.whispersystems.jobqueue.requirements.RequirementListener;
import org.whispersystems.jobqueue.requirements.RequirementProvider;

public class MockRequirementProvider implements RequirementProvider {

  private RequirementListener listener;

  public void fireChange() {
    listener.onRequirementStatusChanged();
  }

  @Override
  public void setListener(RequirementListener listener) {
    this.listener = listener;
  }
}
