package org.whispersystems.jobqueue.jobs;

import org.whispersystems.jobqueue.EncryptionKeys;
import org.whispersystems.jobqueue.Job;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.jobqueue.requirements.Requirement;
import org.whispersystems.jobqueue.util.PersistentResult;

public class PersistentTestJob extends Job {

  public PersistentTestJob(Requirement requirement) {
    super(JobParameters.newBuilder().withRequirement(requirement).withPersistence().create());
  }

  public PersistentTestJob(Requirement requirement, EncryptionKeys keys) {
    super(JobParameters.newBuilder().withRequirement(requirement).withPersistence().withEncryption(keys).create());
  }


  @Override
  public void onAdded() {
    PersistentResult.getInstance().onAdded();
  }

  @Override
  public void onRun() throws Throwable {
    PersistentResult.getInstance().onRun();
  }

  @Override
  public void onCanceled() {
    PersistentResult.getInstance().onCanceled();
  }

  @Override
  public boolean onShouldRetry(Throwable throwable) {
    return false;
  }
}
