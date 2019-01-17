package org.thoughtcrime.securesms.jobmanager;

import android.content.Context;
import android.support.annotation.NonNull;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.logging.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkContinuation;
import androidx.work.WorkManager;

public class JobManager {

  private static final String TAG = JobManager.class.getSimpleName();

  private static final Constraints NETWORK_CONSTRAINT = new Constraints.Builder()
                                                                       .setRequiredNetworkType(NetworkType.CONNECTED)
                                                                       .build();

  private final Executor executor = Executors.newSingleThreadExecutor();

  private final Context     context;
  private final WorkManager workManager;

  public JobManager(@NonNull Context context, @NonNull WorkManager workManager) {
    this.context     = context;
    this.workManager = workManager;
  }

  public Chain startChain(@NonNull Job job) {
    return startChain(Collections.singletonList(job));
  }

  public Chain startChain(@NonNull List<? extends Job> jobs) {
    return new Chain(jobs);
  }

  public void add(Job job) {
    JobParameters jobParameters = job.getJobParameters();

    if (jobParameters == null) {
      throw new IllegalStateException("Jobs must have JobParameters at this stage. (" + job.getClass().getSimpleName() + ")");
    }

    startChain(job).enqueue(jobParameters.getSoloChainParameters());
  }

  private void enqueueChain(@NonNull Chain chain, @NonNull ChainParameters chainParameters) {
    executor.execute(() -> {
      try {
        workManager.pruneWork().getResult().get();
      } catch (ExecutionException | InterruptedException e) {
        Log.w(TAG, "Failed to prune work.", e);
      }

      List<List<Job>>                jobListChain     = chain.getJobListChain();
      List<List<OneTimeWorkRequest>> requestListChain = Stream.of(jobListChain)
                                                              .filter(jobList -> !jobList.isEmpty())
                                                              .map(jobList -> Stream.of(jobList).map(this::toWorkRequest).toList())
                                                              .toList();

      if (jobListChain.isEmpty()) {
        throw new IllegalStateException("Enqueued an empty chain.");
      }

      for (int i = 0; i < jobListChain.size(); i++) {
        for (int j = 0; j < jobListChain.get(i).size(); j++) {
          jobListChain.get(i).get(j).onSubmit(context, requestListChain.get(i).get(j).getId());
        }
      }

      WorkContinuation continuation;

      if (chainParameters.getGroupId().isPresent()) {
        ExistingWorkPolicy policy = chainParameters.shouldIgnoreDuplicates() ? ExistingWorkPolicy.KEEP : ExistingWorkPolicy.APPEND;
        continuation = workManager.beginUniqueWork(chainParameters.getGroupId().get(), policy, requestListChain.get(0));
      } else {
        continuation = workManager.beginWith(requestListChain.get(0));
      }

      for (int i = 1; i < requestListChain.size(); i++) {
        continuation = continuation.then(requestListChain.get(i));
      }

      continuation.enqueue();
    });

  }

  private OneTimeWorkRequest toWorkRequest(@NonNull Job job) {
    JobParameters jobParameters = job.getJobParameters();

    if (jobParameters == null) {
      throw new IllegalStateException("Jobs must have JobParameters at this stage. (" + job.getClass().getSimpleName() + ")");
    }

    Data.Builder dataBuilder = new Data.Builder().putInt(Job.KEY_RETRY_COUNT, jobParameters.getRetryCount())
                                                 .putLong(Job.KEY_RETRY_UNTIL, jobParameters.getRetryUntil())
                                                 .putLong(Job.KEY_SUBMIT_TIME, System.currentTimeMillis())
                                                 .putBoolean(Job.KEY_REQUIRES_NETWORK, jobParameters.requiresNetwork())
                                                 .putBoolean(Job.KEY_REQUIRES_SQLCIPHER, jobParameters.requiresSqlCipher());
    Data data = job.serialize(dataBuilder);

    OneTimeWorkRequest.Builder requestBuilder = new OneTimeWorkRequest.Builder(job.getClass())
                                                                      .setInputData(data)
                                                                      .setBackoffCriteria(BackoffPolicy.LINEAR, OneTimeWorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS);

    if (jobParameters.requiresNetwork()) {
      requestBuilder.setConstraints(NETWORK_CONSTRAINT);
    }

    return requestBuilder.build();
  }

  public class Chain {

    private final List<List<Job>> jobs = new LinkedList<>();

    private Chain(@NonNull List<? extends Job> jobs) {
      this.jobs.add(new ArrayList<>(jobs));
    }

    public Chain then(@NonNull Job job) {
      return then(Collections.singletonList(job));
    }

    public Chain then(@NonNull List<Job> jobs) {
      this.jobs.add(new ArrayList<>(jobs));
      return this;
    }

    public void enqueue(@NonNull ChainParameters chainParameters) {
      enqueueChain(this, chainParameters);
    }

    private List<List<Job>> getJobListChain() {
      return jobs;
    }
  }
}
