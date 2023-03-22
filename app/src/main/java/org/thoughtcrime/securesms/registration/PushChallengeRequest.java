package org.thoughtcrime.securesms.registration;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;

import java.io.IOException;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class PushChallengeRequest {

  private static final String TAG = Log.tag(PushChallengeRequest.class);

  /**
   * Requests a push challenge and waits for the response.
   * <p>
   * Blocks the current thread for up to {@param timeoutMs} milliseconds.
   *
   * @param accountManager Account manager to request the push from.
   * @param fcmToken       Optional FCM token. If not present will return absent.
   * @param sessionId      Local number.
   * @param timeoutMs      Timeout in milliseconds
   * @return Either returns a challenge, or absent.
   */
  @WorkerThread
  public static Optional<String> getPushChallengeBlocking(@NonNull SignalServiceAccountManager accountManager,
                                                          @NonNull String sessionId,
                                                          @NonNull Optional<String> fcmToken,
                                                          long timeoutMs)
  {
    if (fcmToken.isEmpty() || fcmToken.get().isEmpty()) {
      Log.w(TAG, "Push challenge not requested, as no FCM token was present");
      return Optional.empty();
    }

    long startTime = System.currentTimeMillis();
    Log.i(TAG, "Requesting a push challenge");
    Request request = new Request(accountManager, fcmToken.get(), sessionId, timeoutMs);

    Optional<String> challenge = request.requestAndReceiveChallengeBlocking();

    long duration = System.currentTimeMillis() - startTime;

    if (challenge.isPresent()) {
      Log.i(TAG, String.format(Locale.US, "Received a push challenge \"%s\" in %d ms", challenge.get(), duration));
    } else {
      Log.w(TAG, String.format(Locale.US, "Did not received a push challenge in %d ms", duration));
    }
    return challenge;
  }

  public static void postChallengeResponse(@NonNull String challenge) {
    EventBus.getDefault().post(new PushChallengeEvent(challenge));
  }

  public static class Request {

    private final CountDownLatch              latch;
    private final AtomicReference<String>     challenge;
    private final SignalServiceAccountManager accountManager;
    private final String                      fcmToken;
    private final String                      sessionId;
    private final long                        timeoutMs;

    private Request(@NonNull SignalServiceAccountManager accountManager,
                    @NonNull String fcmToken,
                    @NonNull String sessionId,
                    long timeoutMs)
    {
      this.latch          = new CountDownLatch(1);
      this.challenge      = new AtomicReference<>();
      this.accountManager = accountManager;
      this.fcmToken       = fcmToken;
      this.sessionId      = sessionId;
      this.timeoutMs      = timeoutMs;
    }

    @WorkerThread
    private Optional<String> requestAndReceiveChallengeBlocking() {
      EventBus eventBus = EventBus.getDefault();

      eventBus.register(this);
      try {
        accountManager.requestRegistrationPushChallenge(sessionId, fcmToken);

        latch.await(timeoutMs, TimeUnit.MILLISECONDS);

        return Optional.ofNullable(challenge.get());
      } catch (InterruptedException | IOException e) {
        Log.w(TAG, "Error getting push challenge", e);
        return Optional.empty();
      } finally {
        eventBus.unregister(this);
      }
    }

    @Subscribe(threadMode = ThreadMode.POSTING)
    public void onChallengeEvent(@NonNull PushChallengeEvent pushChallengeEvent) {
      challenge.set(pushChallengeEvent.challenge);
      latch.countDown();
    }
  }

  static class PushChallengeEvent {
    private final String challenge;

    PushChallengeEvent(String challenge) {
      this.challenge = challenge;
    }

    public String getChallenge() {
      return challenge;
    }
  }
}
