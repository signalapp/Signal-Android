package org.thoughtcrime.securesms.jobs;

import android.content.Context;
import android.support.annotation.NonNull;

import org.thoughtcrime.securesms.crypto.ProfileKeyUtil;
import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.thoughtcrime.securesms.jobmanager.JobParameters;
import org.thoughtcrime.securesms.jobmanager.SafeData;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.service.IncomingMessageObserver;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceMessagePipe;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.crypto.ProfileCipher;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;

import javax.inject.Inject;

import androidx.work.Data;
import androidx.work.WorkerParameters;

public class RefreshUnidentifiedDeliveryAbilityJob extends ContextJob implements InjectableType {

  private static final String TAG = RefreshUnidentifiedDeliveryAbilityJob.class.getSimpleName();

  @Inject transient SignalServiceMessageReceiver receiver;

  public RefreshUnidentifiedDeliveryAbilityJob(@NonNull Context context, @NonNull WorkerParameters workerParameters) {
    super(context, workerParameters);
  }

  public RefreshUnidentifiedDeliveryAbilityJob(Context context) {
    super(context, new JobParameters.Builder()
                                    .withNetworkRequirement()
                                    .create());
  }

  @Override
  protected void initialize(@NonNull SafeData data) { }

  @Override
  protected @NonNull Data serialize(@NonNull Data.Builder dataBuilder) {
    return dataBuilder.build();
  }

  @Override
  public void onRun() throws Exception {
    byte[]               profileKey = ProfileKeyUtil.getProfileKey(context);
    SignalServiceProfile profile    = retrieveProfile(TextSecurePreferences.getLocalNumber(context));

    boolean enabled = profile.getUnidentifiedAccess() != null && isValidVerifier(profileKey, profile.getUnidentifiedAccess());

    TextSecurePreferences.setIsUnidentifiedDeliveryEnabled(context, enabled);
    Log.i(TAG, "Set UD status to: " + enabled);
  }

  @Override
  protected void onCanceled() {

  }

  @Override
  protected boolean onShouldRetry(Exception exception) {
    return exception instanceof PushNetworkException;
  }

  private SignalServiceProfile retrieveProfile(@NonNull String number) throws IOException {
    SignalServiceMessagePipe pipe = IncomingMessageObserver.getPipe();

    if (pipe != null) {
      try {
        return pipe.getProfile(new SignalServiceAddress(number), Optional.absent());
      } catch (IOException e) {
        Log.w(TAG, e);
      }
    }

    return receiver.retrieveProfile(new SignalServiceAddress(number), Optional.absent());
  }

  private boolean isValidVerifier(@NonNull byte[] profileKey, @NonNull String verifier) {
    ProfileCipher profileCipher = new ProfileCipher(profileKey);
    try {
      return profileCipher.verifyUnidentifiedAccess(Base64.decode(verifier));
    } catch (IOException e) {
      Log.w(TAG, e);
      return false;
    }
  }
}
