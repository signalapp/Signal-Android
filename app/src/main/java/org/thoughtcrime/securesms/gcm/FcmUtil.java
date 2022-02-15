package org.thoughtcrime.securesms.gcm;

import android.text.TextUtils;

import androidx.annotation.WorkerThread;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.messaging.FirebaseMessaging;

import org.signal.core.util.logging.Log;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.concurrent.ExecutionException;

public final class FcmUtil {

  private static final String TAG = Log.tag(FcmUtil.class);

  /**
   * Retrieves the current FCM token. If one isn't available, it'll be generated.
   */
  @WorkerThread
  public static Optional<String> getToken() {
    String token = null;
    try {
      token = Tasks.await(FirebaseMessaging.getInstance().getToken());
    } catch (InterruptedException e) {
      Log.w(TAG, "Was interrupted while waiting for the token.");
    } catch (ExecutionException e) {
      Log.w(TAG, "Failed to get the token.", e.getCause());
    }

    return Optional.fromNullable(TextUtils.isEmpty(token) ? null : token);
  }
}
