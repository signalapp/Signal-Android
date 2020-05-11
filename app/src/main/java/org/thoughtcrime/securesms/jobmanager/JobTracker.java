package org.thoughtcrime.securesms.jobmanager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.util.LRUCache;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Tracks the state of {@link Job}s and allows callers to listen to changes.
 */
public class JobTracker {

  private final Map<String, JobInfo> jobInfos;
  private final List<ListenerInfo>   jobListeners;
  private final Executor             listenerExecutor;

  JobTracker() {
    this.jobInfos         = new LRUCache<>(1000);
    this.jobListeners     = new ArrayList<>();
    this.listenerExecutor = SignalExecutors.BOUNDED;
  }

  /**
   * Add a listener to subscribe to job state updates. Listeners will be invoked on an arbitrary
   * background thread. You must eventually call {@link #removeListener(JobListener)} to avoid
   * memory leaks.
   */
  synchronized void addListener(@NonNull JobFilter filter, @NonNull JobListener listener) {
    jobListeners.add(new ListenerInfo(filter, listener));

    Stream.of(jobInfos.values())
          .filter(info -> info.getJobState() != null)
          .filter(info -> filter.matches(info.getJob()))
          .forEach(state-> {
            //noinspection ConstantConditions We already filter for nulls above
            listenerExecutor.execute(() -> listener.onStateChanged(state.getJob(), state.getJobState()));
          });
  }

  /**
   * Unsubscribe the provided listener from all job updates.
   */
  synchronized void removeListener(@NonNull JobListener listener) {
    Iterator<ListenerInfo> iter = jobListeners.iterator();

    while (iter.hasNext()) {
      if (listener.equals(iter.next().getListener())) {
        iter.remove();
      }
    }
  }

  /**
   * Update the state of a job with the associated ID.
   */
  synchronized void onStateChange(@NonNull Job job, @NonNull JobState state) {
    getOrCreateJobInfo(job).setJobState(state);

    Stream.of(jobListeners)
          .filter(info -> info.getFilter().matches(job))
          .map(ListenerInfo::getListener)
          .forEach(listener -> {
            listenerExecutor.execute(() -> listener.onStateChanged(job, state));
          });
  }

  private @NonNull JobInfo getOrCreateJobInfo(@NonNull Job job) {
    JobInfo jobInfo = jobInfos.get(job.getId());

    if (jobInfo == null) {
      jobInfo = new JobInfo(job);
    }

    jobInfos.put(job.getId(), jobInfo);

    return jobInfo;
  }

  public interface JobFilter {
    boolean matches(@NonNull Job job);
  }

  public interface JobListener {
    void onStateChanged(@NonNull Job job, @NonNull JobState jobState);
  }

  public enum JobState {
    PENDING(false), RUNNING(false), SUCCESS(true), FAILURE(true), IGNORED(true);

    private final boolean complete;

    JobState(boolean complete) {
      this.complete = complete;
    }

    public boolean isComplete() {
      return complete;
    }
  }

  private static class ListenerInfo {
    private final JobFilter   filter;
    private final JobListener listener;

    private ListenerInfo(JobFilter filter, JobListener listener) {
      this.filter = filter;
      this.listener = listener;
    }

     @NonNull JobFilter getFilter() {
      return filter;
    }

    @NonNull JobListener getListener() {
      return listener;
    }
  }

  private static class JobInfo {
    private final Job      job;
    private       JobState jobState;

    private JobInfo(Job job) {
      this.job = job;
    }

    @NonNull Job getJob() {
      return job;
    }

    void setJobState(@NonNull JobState jobState) {
      this.jobState = jobState;
    }

    @Nullable JobState getJobState() {
      return jobState;
    }
  }
}
