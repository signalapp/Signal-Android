/**
 * Copyright (C) 2014 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.whispersystems.jobqueue;

import org.whispersystems.jobqueue.persistence.PersistentStorage;

public class JobConsumer extends Thread {

  private final JobQueue          jobQueue;
  private final PersistentStorage persistentStorage;

  public JobConsumer(String name, JobQueue jobQueue, PersistentStorage persistentStorage) {
    super(name);
    this.jobQueue          = jobQueue;
    this.persistentStorage = persistentStorage;
  }

  @Override
  public void run() {
    while (true) {
      Job job = jobQueue.getNext();

      if (!runJob(job)) {
        job.onCanceled();
      }

      if (job.isPersistent()) {
        persistentStorage.remove(job.getPersistentId());
      }

      if (job.getGroupId() != null) {
        jobQueue.setGroupIdAvailable(job.getGroupId());
      }
    }
  }

  private boolean runJob(Job job) {
    int retryCount = job.getRetryCount();

    for (int i=retryCount;i>0;i--) {
      try {
        job.onRun();
        return true;
      } catch (Throwable throwable) {
        if (!job.onShouldRetry(throwable)) {
          return false;
        }
      }
    }

    return false;
  }

}
