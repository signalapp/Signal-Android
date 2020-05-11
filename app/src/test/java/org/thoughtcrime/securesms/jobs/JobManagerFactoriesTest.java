package org.thoughtcrime.securesms.jobs;

import android.app.Application;

import org.junit.Test;
import org.thoughtcrime.securesms.jobmanager.Job;

import java.util.Map;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public final class JobManagerFactoriesTest {

  @Test
  public void PushContentReceiveJob_is_retired() {
    Map<String, Job.Factory> factories = JobManagerFactories.getJobFactories(mock(Application.class));

    assertTrue(factories.get("PushContentReceiveJob") instanceof FailingJob.Factory);
  }

  @Test
  public void AttachmentUploadJob_is_retired() {
    Map<String, Job.Factory> factories = JobManagerFactories.getJobFactories(mock(Application.class));

    assertTrue(factories.get("AttachmentUploadJob") instanceof FailingJob.Factory);
  }

  @Test
  public void MmsSendJob_is_retired() {
    Map<String, Job.Factory> factories = JobManagerFactories.getJobFactories(mock(Application.class));

    assertTrue(factories.get("MmsSendJob") instanceof FailingJob.Factory);
  }
}
