package org.thoughtcrime.securesms.keyvalue;

import androidx.annotation.NonNull;

import org.greenrobot.eventbus.EventBus;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.ratelimit.RecaptchaRequiredEvent;

import java.util.Collections;
import java.util.List;

public final class RateLimitValues extends SignalStoreValues {

  private static final String TAG = Log.tag(RateLimitValues.class);

  private static final String KEY_NEEDS_RECAPTCHA = "ratelimit.needs_recaptcha";
  private static final String KEY_CHALLENGE       = "ratelimit.token";

  RateLimitValues(@NonNull KeyValueStore store) {
    super(store);
  }

  @Override
  void onFirstEverAppLaunch() {
  }

  @Override
  @NonNull List<String> getKeysToIncludeInBackup() {
    return Collections.emptyList();
  }

  /**
   * @param challenge The token associated with the rate limit response.
   */
  public void markNeedsRecaptcha(@NonNull String challenge) {
    Log.i(TAG, "markNeedsRecaptcha()");
    putBoolean(KEY_NEEDS_RECAPTCHA, true);
    putString(KEY_CHALLENGE, challenge);
    EventBus.getDefault().post(new RecaptchaRequiredEvent());
  }

  public void onProofAccepted() {
    Log.i(TAG, "onProofAccepted()", new Throwable());
    putBoolean(KEY_NEEDS_RECAPTCHA, false);
    remove(KEY_CHALLENGE);
  }

  public boolean needsRecaptcha() {
    return getBoolean(KEY_NEEDS_RECAPTCHA, false);
  }

  public @NonNull String getChallenge() {
    return getString(KEY_CHALLENGE, "");
  }
}
