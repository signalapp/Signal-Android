package org.whispersystems.jobqueue.jobs;

  import org.whispersystems.jobqueue.JobParameters;
  import org.whispersystems.jobqueue.requirements.Requirement;
  import org.whispersystems.jobqueue.util.RunnableThrowable;

  import java.io.IOException;

public class RequirementDeferringTestJob extends TestJob {

  private final Object FINISHED_LOCK = new Object();

  private boolean finished = false;

  private RunnableThrowable runnable;

  public RequirementDeferringTestJob(Requirement requirement, int retryCount, RunnableThrowable runnable) {
    super(JobParameters.newBuilder().withRequirement(requirement).withRetryCount(retryCount).create());
    this.runnable = runnable;
  }

  @Override
  public void onRun() throws Throwable {
    synchronized (RAN_LOCK) {
      this.ran = true;
    }

    if (runnable != null)
      runnable.run();

    synchronized (FINISHED_LOCK) {
      this.finished = true;
    }
  }

  @Override
  public boolean onShouldRetry(Throwable throwable) {
    if (throwable instanceof Exception) {
      return true;
    }
    return false;
  }

  public boolean isFinished() throws InterruptedException {
    synchronized (FINISHED_LOCK) {
      if (!finished) FINISHED_LOCK.wait(1000);
      return finished;
    }
  }

}