package org.thoughtcrime.securesms.jobs.requirements;

import android.content.Context;

import org.thoughtcrime.securesms.jobmanager.dependencies.ContextDependent;
import org.thoughtcrime.securesms.jobmanager.requirements.Requirement;
import org.thoughtcrime.securesms.jobmanager.requirements.SimpleRequirement;
import org.thoughtcrime.securesms.service.KeyCachingService;

public class MasterSecretRequirement extends SimpleRequirement implements ContextDependent {

  private transient Context context;

  public MasterSecretRequirement(Context context) {
    this.context = context;
  }

  @Override
  public boolean isPresent() {
    return KeyCachingService.getMasterSecret(context) != null;
  }

  @Override
  public void setContext(Context context) {
    this.context = context;
  }
}
