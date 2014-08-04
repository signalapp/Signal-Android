package org.whispersystems.jobqueue;

import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

public class JobQueue {

  private final LinkedList<Job> jobQueue = new LinkedList<>();

  public synchronized void onRequirementStatusChanged() {
    notifyAll();
  }

  public synchronized void add(Job job) {
    jobQueue.add(job);
    notifyAll();
  }

  public synchronized void addAll(List<Job> jobs) {
    jobQueue.addAll(jobs);
    notifyAll();
  }

  public synchronized Job getNext() {
    try {
      Job nextAvailableJob;

      while ((nextAvailableJob = getNextAvailableJob()) == null) {
        wait();
      }

      return nextAvailableJob;
    } catch (InterruptedException e) {
      throw new AssertionError(e);
    }
  }

  private Job getNextAvailableJob() {
    if (jobQueue.isEmpty()) return null;

    ListIterator<Job> iterator = jobQueue.listIterator();
    while (iterator.hasNext()) {
      Job job = iterator.next();

      if (job.isRequirementsMet()) {
        iterator.remove();
        return job;
      }
    }

    return null;
  }
}
