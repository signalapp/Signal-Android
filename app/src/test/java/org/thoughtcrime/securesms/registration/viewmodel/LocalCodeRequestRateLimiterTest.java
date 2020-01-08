package org.thoughtcrime.securesms.registration.viewmodel;

import org.junit.Test;
import org.thoughtcrime.securesms.registration.service.RegistrationCodeRequest;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class LocalCodeRequestRateLimiterTest {

  @Test
  public void initially_can_request() {
    LocalCodeRequestRateLimiter limiter = new LocalCodeRequestRateLimiter(60_000);

    assertTrue(limiter.canRequest(RegistrationCodeRequest.Mode.SMS_NO_FCM, "+155512345678", 1000));
  }

  @Test
  public void cant_request_within_same_time_period() {
    LocalCodeRequestRateLimiter limiter = new LocalCodeRequestRateLimiter(60_000);

    assertTrue(limiter.canRequest(RegistrationCodeRequest.Mode.SMS_NO_FCM, "+155512345678", 1000));

    limiter.onSuccessfulRequest(RegistrationCodeRequest.Mode.SMS_NO_FCM, "+155512345678", 1000);

    assertFalse(limiter.canRequest(RegistrationCodeRequest.Mode.SMS_NO_FCM, "+155512345678", 1000 + 59_000));
  }

  @Test
  public void can_request_within_same_time_period_if_different_number() {
    LocalCodeRequestRateLimiter limiter = new LocalCodeRequestRateLimiter(60_000);

    assertTrue(limiter.canRequest(RegistrationCodeRequest.Mode.SMS_NO_FCM, "+155512345678", 1000));

    limiter.onSuccessfulRequest(RegistrationCodeRequest.Mode.SMS_NO_FCM, "+155512345678", 1000);

    assertTrue(limiter.canRequest(RegistrationCodeRequest.Mode.SMS_NO_FCM, "+15559874566", 1000 + 59_000));
  }

  @Test
  public void can_request_within_same_time_period_if_different_mode() {
    LocalCodeRequestRateLimiter limiter = new LocalCodeRequestRateLimiter(60_000);

    assertTrue(limiter.canRequest(RegistrationCodeRequest.Mode.SMS_NO_FCM, "+155512345678", 1000));

    limiter.onSuccessfulRequest(RegistrationCodeRequest.Mode.SMS_NO_FCM, "+155512345678", 1000);

    assertTrue(limiter.canRequest(RegistrationCodeRequest.Mode.SMS_FCM_NO_LISTENER, "+155512345678", 1000 + 59_000));
  }

  @Test
  public void can_request_after_time_period() {
    LocalCodeRequestRateLimiter limiter = new LocalCodeRequestRateLimiter(60_000);

    assertTrue(limiter.canRequest(RegistrationCodeRequest.Mode.SMS_NO_FCM, "+155512345678", 1000));

    limiter.onSuccessfulRequest(RegistrationCodeRequest.Mode.SMS_NO_FCM, "+155512345678", 1000);

    assertTrue(limiter.canRequest(RegistrationCodeRequest.Mode.SMS_NO_FCM, "+155512345678", 1000 + 60_001));
  }

  @Test
  public void can_request_within_same_time_period_if_an_unsuccessful_request_is_seen() {
    LocalCodeRequestRateLimiter limiter = new LocalCodeRequestRateLimiter(60_000);

    assertTrue(limiter.canRequest(RegistrationCodeRequest.Mode.SMS_NO_FCM, "+155512345678", 1000));

    limiter.onSuccessfulRequest(RegistrationCodeRequest.Mode.SMS_NO_FCM, "+155512345678", 1000);

    limiter.onUnsuccessfulRequest();

    assertTrue(limiter.canRequest(RegistrationCodeRequest.Mode.SMS_NO_FCM, "+155512345678", 1000 + 59_000));
  }
}