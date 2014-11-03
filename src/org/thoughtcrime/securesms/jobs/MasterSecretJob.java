package org.thoughtcrime.securesms.jobs;

import android.content.Context;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.whispersystems.jobqueue.JobParameters;

public abstract class MasterSecretJob extends ContextJob {

  public MasterSecretJob(Context context, JobParameters parameters) {
    super(context, parameters);
  }

  protected MasterSecret getMasterSecret() throws RequirementNotMetException {
    MasterSecret masterSecret = KeyCachingService.getMasterSecret(context);

    if (masterSecret == null) throw new RequirementNotMetException();
    else                      return masterSecret;
  }

  protected static class RequirementNotMetException extends Exception {}

}
