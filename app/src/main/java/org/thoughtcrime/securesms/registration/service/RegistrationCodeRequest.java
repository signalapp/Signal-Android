package org.thoughtcrime.securesms.registration.service;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.gcm.FcmUtil;
import org.thoughtcrime.securesms.push.AccountManagerFactory;
import org.thoughtcrime.securesms.registration.PushChallengeRequest;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.push.exceptions.CaptchaRequiredException;
import org.whispersystems.signalservice.api.push.exceptions.RateLimitException;

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
    Log.d(TAG, "SMS Verification requested");

    new AsyncTask<Void, Void, VerificationRequestResult>() {
      @Override
      protected @NonNull
      VerificationRequestResult doInBackground(Void... voids) {
        try {
          markAsVerifying(context);

          Optional<String> fcmToken = FcmUtil.getToken();

          SignalServiceAccountManager accountManager = AccountManagerFactory.createUnauthenticated(context, credentials.getE164number(), credentials.getPassword());

          Optional<String> pushChallenge = PushChallengeRequest.getPushChallengeBlocking(accountManager, fcmToken, credentials.getE164number(), PUSH_REQUEST_TIMEOUT_MS);

          if (mode == Mode.PHONE_CALL) {
            accountManager.requestVoiceVerificationCode(Locale.getDefault(), Optional.fromNullable(captchaToken), pushChallenge);
          } else {
            accountManager.requestSmsVerificationCode(mode.isSmsRetrieverSupported(), Optional.fromNullable(captchaToken), pushChallenge);
          }

          return new VerificationRequestResult(fcmToken.orNull(), Optional.absent());
        } catch (IOException e) {
          org.signal.core.util.logging.Log.w(TAG, "Error during account registration", e);
          return new VerificationRequestResult(null, Optional.of(e));
        }
      }

      protected void onPostExecute(@NonNull VerificationRequestResult result) {
        if (isCaptchaRequired(result)) {
          callback.onNeedCaptcha();
        } else if (isRateLimited(result)) {
          callback.onRateLimited();
        } else if (result.exception.isPresent()) {
          callback.onError();
        } else {
          callback.requestSent(result.fcmToken);
        }
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

  private static void markAsVerifying(Context context) {
    TextSecurePreferences.setPushRegistered(context, false);
  }

  private static boolean isCaptchaRequired(@NonNull VerificationRequestResult result) {
    return result.exception.isPresent() && result.exception.get() instanceof CaptchaRequiredException;
  }

  private static boolean isRateLimited(@NonNull VerificationRequestResult result) {
    return result.exception.isPresent() && result.exception.get() instanceof RateLimitException;
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
     * Device is requesting an SMS and supports SMS retrieval.
     *
     * The SMS sent will be formatted for automatic SMS retrieval.
     */
    SMS_WITH_LISTENER(true),

    /**
     * Device is requesting an SMS and does not support SMS retrieval.
     *
     * The SMS sent will be not be specially formatted for automatic SMS retrieval.
     */
    SMS_WITHOUT_LISTENER(false),

    /**
     * Device is requesting a phone call.
     */
    PHONE_CALL(false);

    private final boolean smsRetrieverSupported;

    Mode(boolean smsRetrieverSupported) {
      this.smsRetrieverSupported = smsRetrieverSupported;
    }

    public boolean isSmsRetrieverSupported() {
      return smsRetrieverSupported;
    }
  }

  public interface SmsVerificationCodeCallback {

    void onNeedCaptcha();

    void requestSent(@Nullable String fcmToken);

    void onRateLimited();

    void onError();
  }
}
