package org.thoughtcrime.securesms.registration.service;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.gcm.FcmUtil;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.push.AccountManagerFactory;
import org.thoughtcrime.securesms.registration.PushChallengeRequest;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.push.exceptions.CaptchaRequiredException;

import java.io.IOException;
import java.util.Locale;

public final class RegistrationCodeRequest {

  private static final long PUSH_REQUEST_TIMEOUT_MS = 5000L;

  private static final String TAG = Log.tag(RegistrationCodeRequest.class);

  /**
   * Request a verification code to be sent according to the specified {@param mode}.
   *
   * The request will fire asynchronously, and exactly one of the methods on the {@param callback}
   * will be called.
   */
  @SuppressLint("StaticFieldLeak")
  static void requestSmsVerificationCode(@NonNull Context context, @NonNull Credentials credentials, @Nullable String captchaToken, @NonNull Mode mode, @NonNull SmsVerificationCodeCallback callback) {
    Log.d(TAG, String.format("SMS Verification requested for %s captcha %s", credentials.getE164number(), captchaToken));

    new AsyncTask<Void, Void, VerificationRequestResult>() {
      @Override
      protected @NonNull
      VerificationRequestResult doInBackground(Void... voids) {
        try {
          markAsVerifying(context);

          Optional<String> fcmToken;

          if (mode.isFcm()) {
            fcmToken = FcmUtil.getToken();
          } else {
            fcmToken = Optional.absent();
          }

          SignalServiceAccountManager accountManager = AccountManagerFactory.createUnauthenticated(context, credentials.getE164number(), credentials.getPassword());

          Optional<String> pushChallenge = PushChallengeRequest.getPushChallengeBlocking(accountManager, fcmToken, credentials.getE164number(), PUSH_REQUEST_TIMEOUT_MS);

          if (mode == Mode.PHONE_CALL) {
            accountManager.requestVoiceVerificationCode(Locale.getDefault(), Optional.fromNullable(captchaToken), pushChallenge);
          } else {
            accountManager.requestSmsVerificationCode(mode.isSmsRetrieverSupported(), Optional.fromNullable(captchaToken), pushChallenge);
          }

          return new VerificationRequestResult(fcmToken.orNull(), Optional.absent());
        } catch (IOException e) {
          org.thoughtcrime.securesms.logging.Log.w(TAG, "Error during account registration", e);
          return new VerificationRequestResult(null, Optional.of(e));
        }
      }

      protected void onPostExecute(@NonNull VerificationRequestResult result) {
        if (result.exception.isPresent() && result.exception.get() instanceof CaptchaRequiredException) {
          callback.onNeedCaptcha();
        } else if (result.exception.isPresent()) {
          callback.onError();
        } else {
          callback.requestSent(result.fcmToken);
        }
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

  private static void markAsVerifying(Context context) {
    TextSecurePreferences.setVerifying(context, true);

    TextSecurePreferences.setPushRegistered(context, false);
  }

  private static class VerificationRequestResult {
    private final @Nullable String fcmToken;
    private final Optional<IOException> exception;

    private VerificationRequestResult(@Nullable String fcmToken, Optional<IOException> exception) {
      this.fcmToken = fcmToken;
      this.exception = exception;
    }
  }

   /**
   * The mode by which a code is being requested.
   */
  public enum Mode {

    /**
     * Device supports FCM and SMS retrieval.
     *
     * The SMS sent will be formatted for automatic SMS retrieval.
     */
    SMS_FCM_WITH_LISTENER(true, true),

    /**
     * Device supports FCM but not SMS retrieval.
     *
     * The SMS sent will be not be specially formatted for automatic SMS retrieval.
     */
    SMS_FCM_NO_LISTENER(true, false),

    /**
     * Device does not support FCM and so also not SMS retrieval.
     */
    SMS_NO_FCM(false, false),

    /**
     * Device is requesting a phone call.
     *
     * Neither FCM or SMS retrieval is relevant in this mode.
     */
    PHONE_CALL(false, false);

    private final boolean fcm;
    private final boolean smsRetrieverSupported;

    Mode(boolean fcm, boolean smsRetrieverSupported) {
      this.fcm                   = fcm;
      this.smsRetrieverSupported = smsRetrieverSupported;
    }

    public boolean isFcm() {
      return fcm;
    }

    public boolean isSmsRetrieverSupported() {
      return smsRetrieverSupported;
    }
  }

  public interface SmsVerificationCodeCallback {

    void onNeedCaptcha();

    void requestSent(@Nullable String fcmToken);

    void onError();
  }
}
