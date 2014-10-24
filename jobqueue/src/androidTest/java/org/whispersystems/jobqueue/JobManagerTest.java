package org.whispersystems.jobqueue;

import android.test.AndroidTestCase;

import org.whispersystems.jobqueue.jobs.PersistentTestJob;
import org.whispersystems.jobqueue.jobs.RequirementDeferringTestJob;
import org.whispersystems.jobqueue.jobs.RequirementTestJob;
import org.whispersystems.jobqueue.jobs.TestJob;
import org.whispersystems.jobqueue.persistence.JavaJobSerializer;
import org.whispersystems.jobqueue.util.MockRequirement;
import org.whispersystems.jobqueue.util.MockRequirementProvider;
import org.whispersystems.jobqueue.util.PersistentMockRequirement;
import org.whispersystems.jobqueue.util.PersistentRequirement;
import org.whispersystems.jobqueue.util.PersistentResult;
import org.whispersystems.jobqueue.util.RunnableThrowable;

import java.io.IOException;

public class JobManagerTest extends AndroidTestCase {

  public void testTransientJobExecution() throws InterruptedException {
    TestJob testJob    = new TestJob();
    JobManager       jobManager = new JobManager(getContext(), "transient-test", null, null, 1);

    jobManager.add(testJob);

    assertTrue(testJob.isAdded());
    assertTrue(testJob.isRan());
  }

  public void testTransientRequirementJobExecution() throws InterruptedException {
    MockRequirementProvider provider = new MockRequirementProvider();
    MockRequirement requirement  = new MockRequirement(false);
    TestJob             testJob      = new RequirementTestJob(requirement);
    JobManager          jobManager   = new JobManager(getContext(), "transient-requirement-test", provider, null, 1);

    jobManager.add(testJob);

    assertTrue(testJob.isAdded());
    assertTrue(!testJob.isRan());

    requirement.setPresent(true);
    provider.fireChange();

    assertTrue(testJob.isRan());

  }

  public void testTransientRequirementDeferringJobExecution() throws InterruptedException {
    final Object lock = new Object();

    RunnableThrowable waitRunnable = new RunnableThrowable() {
      public Boolean shouldThrow = false;

      @Override
      public void run() throws Exception {
        try {
          synchronized (lock) {
            lock.wait();

            if (shouldThrow) {
              throw new Exception();
            }
          }
        } catch (InterruptedException e) {
          throw new AssertionError(e);
        }
      }
      @Override
      public void shouldThrow(Boolean value) {
        shouldThrow = value;
      }
    };

    MockRequirementProvider     provider    = new MockRequirementProvider();
    MockRequirement             requirement = new MockRequirement(false);
    RequirementDeferringTestJob testJob     = new RequirementDeferringTestJob(requirement, 5, waitRunnable);
    JobManager                  jobManager  = new JobManager(getContext(), "transient-requirement-test", provider, null, 1);

    jobManager.add(testJob);

    waitRunnable.shouldThrow(true);
    requirement.setPresent(true);
    provider.fireChange();

    assertTrue(testJob.isRan());
    assertTrue(!testJob.isFinished());
    synchronized (lock) { lock.notifyAll(); }
    assertTrue(!testJob.isFinished());

    requirement.setPresent(false);
    provider.fireChange();
    assertTrue(!testJob.isFinished());
    synchronized (lock) { lock.notifyAll(); }
    assertTrue(!testJob.isFinished());

    waitRunnable.shouldThrow(false);
    requirement.setPresent(true);
    provider.fireChange();
    assertTrue(!testJob.isFinished());
    synchronized (lock) { lock.notifyAll(); }
    assertTrue(testJob.isFinished());
  }

  public void testPersistentJobExecuton() throws InterruptedException {
    PersistentMockRequirement requirement = new PersistentMockRequirement();
    PersistentTestJob         testJob     = new PersistentTestJob(requirement);
    JobManager                jobManager  = new JobManager(getContext(), "persistent-requirement-test3", null, new JavaJobSerializer(getContext()), 1);

    PersistentResult.getInstance().reset();
    PersistentRequirement.getInstance().setPresent(false);

    jobManager.add(testJob);

    assertTrue(PersistentResult.getInstance().isAdded());
    assertTrue(!PersistentResult.getInstance().isRan());

    PersistentRequirement.getInstance().setPresent(true);
    jobManager = new JobManager(getContext(), "persistent-requirement-test3", null, new JavaJobSerializer(getContext()), 1);

    assertTrue(PersistentResult.getInstance().isRan());
  }

  public void testEncryptedJobExecuton() throws InterruptedException {
    EncryptionKeys            keys        = new EncryptionKeys(new byte[30]);
    PersistentMockRequirement requirement = new PersistentMockRequirement();
    PersistentTestJob         testJob     = new PersistentTestJob(requirement, keys);
    JobManager                jobManager  = new JobManager(getContext(), "persistent-requirement-test4", null, new JavaJobSerializer(getContext()), 1);
    jobManager.setEncryptionKeys(keys);

    PersistentResult.getInstance().reset();
    PersistentRequirement.getInstance().setPresent(false);

    jobManager.add(testJob);

    assertTrue(PersistentResult.getInstance().isAdded());
    assertTrue(!PersistentResult.getInstance().isRan());

    PersistentRequirement.getInstance().setPresent(true);
    jobManager = new JobManager(getContext(), "persistent-requirement-test4", null, new JavaJobSerializer(getContext()), 1);

    assertTrue(!PersistentResult.getInstance().isRan());

    jobManager.setEncryptionKeys(keys);

    assertTrue(PersistentResult.getInstance().isRan());
  }

  public void testGroupIdExecution() throws InterruptedException {
    final Object lock = new Object();

    Runnable waitRunnable = new Runnable() {
      @Override
      public void run() {
        try {
          synchronized (lock) {
            lock.wait();
          }
        } catch (InterruptedException e) {
          throw new AssertionError(e);
        }
      }
    };

    TestJob    testJobOne   = new TestJob(JobParameters.newBuilder().withGroupId("foo").create(), waitRunnable);
    TestJob    testJobTwo   = new TestJob(JobParameters.newBuilder().withGroupId("foo").create());
    TestJob    testJobThree = new TestJob(JobParameters.newBuilder().withGroupId("bar").create());
    JobManager jobManager   = new JobManager(getContext(), "transient-test", null, null, 3);

    jobManager.add(testJobOne);
    jobManager.add(testJobTwo);
    jobManager.add(testJobThree);

    assertTrue(testJobOne.isAdded());
    assertTrue(testJobTwo.isAdded());
    assertTrue(testJobThree.isAdded());

    assertTrue(testJobOne.isRan());
    assertTrue(!testJobTwo.isRan());
    assertTrue(testJobThree.isRan());

    synchronized (lock) {
      lock.notifyAll();
    }

    assertTrue(testJobTwo.isRan());
  }


}
