package org.thoughtcrime.securesms.jobmanager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.util.LRUCache;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executor;

/**
 * Tracks the state of {@link Job}s and allows callers to listen to changes.
 */
public class JobTracker {

  private final Map<String, TrackingState> trackingStates;
  private final Executor                   listenerExecutor;

  JobTracker() {
    this.trackingStates   = new LRUCache<>(1000);
    this.listenerExecutor = SignalExecutors.BOUNDED;
  }

  /**
   * Add a listener to subscribe to job state updates. Listeners will be invoked on an arbitrary
   * background thread. You must eventually call {@link #removeListener(JobListener)} to avoid
   * memory leaks.
   */
  synchronized void addListener(@NonNull String id, @NonNull JobListener jobListener) {
    TrackingState state           = getOrCreateTrackingState(id);
    JobState      currentJobState = state.getJobState();

    state.addListener(jobListener);

    if (currentJobState != null) {
      listenerExecutor.execute(() -> jobListener.onStateChanged(currentJobState));
    }
  }

  /**
   * Unsubscribe the provided listener from all job updates.
   */
  synchronized void removeListener(@NonNull JobListener jobListener) {
    Collection<TrackingState> allTrackingState = trackingStates.values();

    for (TrackingState state : allTrackingState) {
      state.removeListener(jobListener);
    }
  }

  /**
   * Update the state of a job with the associated ID.
   */
  synchronized void onStateChange(@NonNull String id, @NonNull JobState jobState) {
    TrackingState trackingState = getOrCreateTrackingState(id);
    trackingState.setJobState(jobState);

    for (JobListener listener : trackingState.getListeners()) {
      listenerExecutor.execute(() -> listener.onStateChanged(jobState));
    }
  }

  private @NonNull TrackingState getOrCreateTrackingState(@NonNull String id) {
    TrackingState state = trackingStates.get(id);

    if (state == null) {
      state = new TrackingState();
    }

    trackingStates.put(id, state);

    return state;
  }

  public interface JobListener {
    void onStateChanged(@NonNull JobState jobState);
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

  private static class TrackingState {
    private JobState jobState;

    private final CopyOnWriteArraySet<JobListener> listeners = new CopyOnWriteArraySet<>();

    void addListener(@NonNull JobListener jobListener) {
      listeners.add(jobListener);
    }

    void removeListener(@NonNull JobListener jobListener) {
      listeners.remove(jobListener);
    }

    @NonNull Collection<JobListener> getListeners() {
      return listeners;
    }

    void setJobState(@NonNull JobState jobState) {
      this.jobState = jobState;
    }

    @Nullable JobState getJobState() {
      return jobState;
    }
  }
}
