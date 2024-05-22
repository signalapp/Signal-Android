package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.jobmanager.JsonJobData;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.whispersystems.signalservice.internal.push.http.ResumableUploadSpec;

import java.io.IOException;

/**
 * No longer used. Functionality has been merged into {@link AttachmentUploadJob}.
 */
@Deprecated
public class ResumableUploadSpecJob extends BaseJob {

  private static final String TAG = Log.tag(ResumableUploadSpecJob.class);

  static final String KEY_RESUME_SPEC = "resume_spec";

  public static final String KEY = "ResumableUploadSpecJob";

  private ResumableUploadSpecJob(@NonNull Parameters parameters) {
    super(parameters);
  }

  @Override
  protected void onRun() throws Exception {
    ResumableUploadSpec resumableUploadSpec = AppDependencies.getSignalServiceMessageSender()
                                                             .getResumableUploadSpec();

    setOutputData(new JsonJobData.Builder()
                          .putString(KEY_RESUME_SPEC, resumableUploadSpec.serialize())
                          .serialize());
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    return e instanceof IOException;
  }

  @Override
  public @Nullable byte[] serialize() {
    return null;
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onFailure() {

  }

  public static class Factory implements Job.Factory<ResumableUploadSpecJob> {

    @Override
    public @NonNull ResumableUploadSpecJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      return new ResumableUploadSpecJob(parameters);
    }
  }
}
