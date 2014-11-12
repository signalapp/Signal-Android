package org.thoughtcrime.securesms.jobs;

import android.content.Context;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.whispersystems.jobqueue.JobParameters;

public abstract class MasterSecretJob extends ContextJob {

  public MasterSecretJob(Context context, JobParameters parameters) {
    super(context, parameters);
  }

  @Override
  public void onRun() throws Throwable {
    MasterSecret masterSecret = getMasterSecret();
    onRun(masterSecret);
  }

  @Override
  public boolean onShouldRetry(Throwable throwable) {
    if (throwable instanceof RequirementNotMetException) return true;
    return onShouldRetryThrowable(throwable);
  }

  public abstract void onRun(MasterSecret masterSecret) throws Throwable;
  public abstract boolean onShouldRetryThrowable(Throwable throwable);

  private MasterSecret getMasterSecret() throws RequirementNotMetException {
    MasterSecret masterSecret = KeyCachingService.getMasterSecret(context);

    if (masterSecret == null) throw new RequirementNotMetException();
    else                      return masterSecret;
  }

  protected static class RequirementNotMetException extends Exception {}

}
