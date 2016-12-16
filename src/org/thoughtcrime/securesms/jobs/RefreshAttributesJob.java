package org.thoughtcrime.securesms.jobs;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;

import org.thoughtcrime.redphone.signaling.RedPhoneAccountAttributes;
import org.thoughtcrime.redphone.signaling.RedPhoneAccountManager;
import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.jobqueue.requirements.NetworkRequirement;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.push.exceptions.NetworkFailureException;

import java.io.IOException;

import javax.inject.Inject;

public class RefreshAttributesJob extends ContextJob implements InjectableType {

  public static final long serialVersionUID = 1L;

  private static final String TAG = RefreshAttributesJob.class.getSimpleName();

  @Inject transient SignalServiceAccountManager textSecureAccountManager;
  @Inject transient RedPhoneAccountManager      redPhoneAccountManager;

  public RefreshAttributesJob(Context context) {
    super(context, JobParameters.newBuilder()
                                .withPersistence()
                                .withRequirement(new NetworkRequirement(context))
                                .withWakeLock(true)
                                .create());
  }

  @Override
  public void onAdded() {}

  @Override
  public void onRun() throws IOException {
    String signalingKey      = TextSecurePreferences.getSignalingKey(context);
    String gcmRegistrationId = TextSecurePreferences.getGcmRegistrationId(context);
    int    registrationId    = TextSecurePreferences.getLocalRegistrationId(context);

    if (GooglePlayServicesUtil.isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS) {
      String token = textSecureAccountManager.getAccountVerificationToken();

      redPhoneAccountManager.createAccount(token, new RedPhoneAccountAttributes(signalingKey, gcmRegistrationId));
      textSecureAccountManager.setAccountAttributes(signalingKey, registrationId, true, false);
    } else {
      textSecureAccountManager.setAccountAttributes(signalingKey, registrationId, false, true);
    }
  }

  @Override
  public boolean onShouldRetry(Exception e) {
    return e instanceof NetworkFailureException;
  }

  @Override
  public void onCanceled() {
    Log.w(TAG, "Failed to update account attributes!");
  }
}
