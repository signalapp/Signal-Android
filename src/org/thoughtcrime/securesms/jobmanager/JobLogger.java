package org.thoughtcrime.securesms.jobmanager;

import androidx.annotation.NonNull;
import android.text.TextUtils;

public class JobLogger {

  public static String format(@NonNull Job job, @NonNull String event) {
    return format(job, "", event);
  }

  public static String format(@NonNull Job job, @NonNull String extraTag, @NonNull String event) {
    String id                  = job.getId();
    String tag                 = TextUtils.isEmpty(extraTag) ? "" : "[" + extraTag + "]";
    long   timeSinceSubmission = System.currentTimeMillis() - job.getParameters().getCreateTime();
    int    runAttempt          = job.getRunAttempt() + 1;
    String maxAttempts         = job.getParameters().getMaxAttempts() == Job.Parameters.UNLIMITED ? "Unlimited"
                                                                                                  : String.valueOf(job.getParameters().getMaxAttempts());
    String lifespan            = job.getParameters().getLifespan() == Job.Parameters.IMMORTAL ? "Immortal"
                                                                                              : String.valueOf(job.getParameters().getLifespan()) + " ms";
    return String.format("[%s][%s]%s %s (Time Since Submission: %d ms, Lifespan: %s, Run Attempt: %d/%s)",
                         id, job.getClass().getSimpleName(), tag, event, timeSinceSubmission, lifespan, runAttempt, maxAttempts);
  }
}
