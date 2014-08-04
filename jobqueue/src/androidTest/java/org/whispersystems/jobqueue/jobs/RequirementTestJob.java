package org.whispersystems.jobqueue.jobs;

import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.jobqueue.requirements.Requirement;

public class RequirementTestJob extends TestJob {

  public RequirementTestJob(Requirement requirement) {
    super(JobParameters.newBuilder().withRequirement(requirement).create());
  }

}
