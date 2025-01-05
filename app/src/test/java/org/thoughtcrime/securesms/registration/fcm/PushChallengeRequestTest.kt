/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.thoughtcrime.securesms.registration.fcm

import android.app.Application
import android.os.AsyncTask
import io.mockk.called
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.hamcrest.Matchers.lessThan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.whispersystems.signalservice.api.SignalServiceAccountManager
import java.io.IOException
import java.util.Optional

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class PushChallengeRequestTest {
  @Test
  fun pushChallengeBlocking_returns_absent_if_times_out() {
    val signal = mockk<SignalServiceAccountManager>(relaxUnitFun = true)

    val challenge = PushChallengeRequest.getPushChallengeBlocking(signal, "session ID", Optional.of("token"), 50L)

    assertFalse(challenge.isPresent)
  }

  @Test
  fun pushChallengeBlocking_waits_for_specified_period() {
    val signal = mockk<SignalServiceAccountManager>(relaxUnitFun = true)

    val startTime = System.currentTimeMillis()
    PushChallengeRequest.getPushChallengeBlocking(signal, "session ID", Optional.of("token"), 250L)
    val duration = System.currentTimeMillis() - startTime

    assertThat(duration, greaterThanOrEqualTo(250L))
  }

  @Test
  fun pushChallengeBlocking_completes_fast_if_posted_to_event_bus() {
    val signal = mockk<SignalServiceAccountManager> {
      every {
        requestRegistrationPushChallenge("session ID", "token")
      } answers {
        AsyncTask.execute { PushChallengeRequest.postChallengeResponse("CHALLENGE") }
      }
    }

    val startTime = System.currentTimeMillis()
    val challenge = PushChallengeRequest.getPushChallengeBlocking(signal, "session ID", Optional.of("token"), 500L)
    val duration = System.currentTimeMillis() - startTime

    assertThat(duration, lessThan(500L))
    verify { signal.requestRegistrationPushChallenge("session ID", "token") }
    confirmVerified(signal)

    assertTrue(challenge.isPresent)
    assertEquals("CHALLENGE", challenge.get())
  }

  @Test
  fun pushChallengeBlocking_returns_fast_if_no_fcm_token_supplied() {
    val signal = mockk<SignalServiceAccountManager>()

    val startTime = System.currentTimeMillis()
    PushChallengeRequest.getPushChallengeBlocking(signal, "session ID", Optional.empty(), 500L)
    val duration = System.currentTimeMillis() - startTime

    assertThat(duration, lessThan(500L))
  }

  @Test
  fun pushChallengeBlocking_returns_absent_if_no_fcm_token_supplied() {
    val signal = mockk<SignalServiceAccountManager>()

    val challenge = PushChallengeRequest.getPushChallengeBlocking(signal, "session ID", Optional.empty(), 500L)

    verify { signal wasNot called }
    assertFalse(challenge.isPresent)
  }

  @Test
  fun pushChallengeBlocking_returns_absent_if_any_IOException_is_thrown() {
    val signal = mockk<SignalServiceAccountManager> {
      every { requestRegistrationPushChallenge(any(), any()) } throws IOException()
    }

    val challenge = PushChallengeRequest.getPushChallengeBlocking(signal, "session ID", Optional.of("token"), 500L)

    assertFalse(challenge.isPresent)
  }
}
