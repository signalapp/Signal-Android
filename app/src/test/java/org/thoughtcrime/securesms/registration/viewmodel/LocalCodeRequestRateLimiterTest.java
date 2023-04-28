package org.thoughtcrime.securesms.registration.viewmodel;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.thoughtcrime.securesms.registration.VerifyAccountRepository.Mode;

public final class LocalCodeRequestRateLimiterTest {

  @Test
  public void initially_can_request() {
    LocalCodeRequestRateLimiter limiter = new LocalCodeRequestRateLimiter(60_000);

    assertTrue(limiter.canRequest(Mode.SMS_WITHOUT_LISTENER, "+155512345678", 1000));
  }

  @Test
  public void cant_request_within_same_time_period() {
    LocalCodeRequestRateLimiter limiter = new LocalCodeRequestRateLimiter(60_000);

    assertTrue(limiter.canRequest(Mode.SMS_WITHOUT_LISTENER, "+155512345678", 1000));

    limiter.onSuccessfulRequest(Mode.SMS_WITHOUT_LISTENER, "+155512345678", 1000);

    assertFalse(limiter.canRequest(Mode.SMS_WITHOUT_LISTENER, "+155512345678", 1000 + 59_000));
  }

  @Test
  public void can_request_within_same_time_period_if_different_number() {
    LocalCodeRequestRateLimiter limiter = new LocalCodeRequestRateLimiter(60_000);

    assertTrue(limiter.canRequest(Mode.SMS_WITHOUT_LISTENER, "+155512345678", 1000));

    limiter.onSuccessfulRequest(Mode.SMS_WITHOUT_LISTENER, "+155512345678", 1000);

    assertTrue(limiter.canRequest(Mode.SMS_WITHOUT_LISTENER, "+15559874566", 1000 + 59_000));
  }

  @Test
  public void can_request_within_same_time_period_if_different_mode() {
    LocalCodeRequestRateLimiter limiter = new LocalCodeRequestRateLimiter(60_000);

    assertTrue(limiter.canRequest(Mode.SMS_WITH_LISTENER, "+155512345678", 1000));

    limiter.onSuccessfulRequest(Mode.SMS_WITH_LISTENER, "+155512345678", 1000);

    assertTrue(limiter.canRequest(Mode.SMS_WITHOUT_LISTENER, "+155512345678", 1000 + 59_000));
  }

  @Test
  public void can_request_after_time_period() {
    LocalCodeRequestRateLimiter limiter = new LocalCodeRequestRateLimiter(60_000);

    assertTrue(limiter.canRequest(Mode.SMS_WITH_LISTENER, "+155512345678", 1000));

    limiter.onSuccessfulRequest(Mode.SMS_WITH_LISTENER, "+155512345678", 1000);

    assertTrue(limiter.canRequest(Mode.SMS_WITH_LISTENER, "+155512345678", 1000 + 60_001));
  }

  @Test
  public void can_request_within_same_time_period_if_an_unsuccessful_request_is_seen() {
    LocalCodeRequestRateLimiter limiter = new LocalCodeRequestRateLimiter(60_000);

    assertTrue(limiter.canRequest(Mode.SMS_WITH_LISTENER, "+155512345678", 1000));

    limiter.onSuccessfulRequest(Mode.SMS_WITH_LISTENER, "+155512345678", 1000);

    limiter.onUnsuccessfulRequest();

    assertTrue(limiter.canRequest(Mode.SMS_WITH_LISTENER, "+155512345678", 1000 + 59_000));
  }
}
