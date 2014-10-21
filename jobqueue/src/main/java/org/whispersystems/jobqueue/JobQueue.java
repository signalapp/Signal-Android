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

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

public class JobQueue {

  private final Set<String>     activeGroupIds = new HashSet<>();
  private final LinkedList<Job> jobQueue       = new LinkedList<>();

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

  public synchronized void setGroupIdAvailable(String groupId) {
    if (groupId != null) {
      activeGroupIds.remove(groupId);
      notifyAll();
    }
  }

  private Job getNextAvailableJob() {
    if (jobQueue.isEmpty()) return null;

    ListIterator<Job> iterator = jobQueue.listIterator();
    while (iterator.hasNext()) {
      Job job = iterator.next();

      if (job.isRequirementsMet() && isGroupIdAvailable(job.getGroupId())) {
        iterator.remove();
        setGroupIdUnavailable(job.getGroupId());
        return job;
      }
    }

    return null;
  }

  private boolean isGroupIdAvailable(String groupId) {
    return groupId == null || !activeGroupIds.contains(groupId);
  }

  private void setGroupIdUnavailable(String groupId) {
    if (groupId != null) {
      activeGroupIds.add(groupId);
    }
  }
}
