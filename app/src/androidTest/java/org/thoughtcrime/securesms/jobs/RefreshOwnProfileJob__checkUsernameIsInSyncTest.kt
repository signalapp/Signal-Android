package org.thoughtcrime.securesms.jobs

import androidx.test.ext.junit.runners.AndroidJUnit4
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.libsignal.usernames.Username
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.InstrumentationApplicationDependencyProvider
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.testing.Get
import org.thoughtcrime.securesms.testing.Put
import org.thoughtcrime.securesms.testing.SignalActivityRule
import org.thoughtcrime.securesms.testing.failure
import org.thoughtcrime.securesms.testing.success
import org.whispersystems.signalservice.internal.push.ReserveUsernameResponse
import org.whispersystems.signalservice.internal.push.WhoAmIResponse
import org.whispersystems.util.Base64UrlSafe

@Suppress("ClassName")
@RunWith(AndroidJUnit4::class)
class RefreshOwnProfileJob__checkUsernameIsInSyncTest {

  @get:Rule
  val harness = SignalActivityRule()

  @After
  fun tearDown() {
    InstrumentationApplicationDependencyProvider.clearHandlers()
    SignalStore.phoneNumberPrivacy().clearUsernameOutOfSync()
  }

  @Test
  fun givenNoLocalUsername_whenICheckUsernameIsInSync_thenIExpectNoFailures() {
    // WHEN
    RefreshOwnProfileJob.checkUsernameIsInSync()
  }

  @Test
  fun givenLocalUsernameDoesNotMatchServerUsername_whenICheckUsernameIsInSync_thenIExpectRetry() {
    // GIVEN
    var didReserve = false
    var didConfirm = false
    val username = "hello.32"
    val serverUsername = "hello.3232"
    SignalDatabase.recipients.setUsername(harness.self.id, username)
    InstrumentationApplicationDependencyProvider.addMockWebRequestHandlers(
      Get("/v1/accounts/whoami") { r ->
        MockResponse().success(
          WhoAmIResponse().apply {
            usernameHash = Base64UrlSafe.encodeBytesWithoutPadding(Username.hash(serverUsername))
          }
        )
      },
      Put("/v1/accounts/username_hash/reserve") { r ->
        didReserve = true
        MockResponse().success(ReserveUsernameResponse(Base64UrlSafe.encodeBytesWithoutPadding(Username.hash(username))))
      },
      Put("/v1/accounts/username_hash/confirm") { r ->
        didConfirm = true
        MockResponse().success()
      }
    )

    // WHEN
    RefreshOwnProfileJob.checkUsernameIsInSync()

    // THEN
    assertTrue(didReserve)
    assertTrue(didConfirm)
    assertFalse(SignalStore.phoneNumberPrivacy().isUsernameOutOfSync)
  }

  @Test
  fun givenLocalAndNoServer_whenICheckUsernameIsInSync_thenIExpectRetry() {
    // GIVEN
    var didReserve = false
    var didConfirm = false
    val username = "hello.32"
    SignalDatabase.recipients.setUsername(harness.self.id, username)
    InstrumentationApplicationDependencyProvider.addMockWebRequestHandlers(
      Get("/v1/accounts/whoami") { r ->
        MockResponse().success(WhoAmIResponse())
      },
      Put("/v1/accounts/username_hash/reserve") { r ->
        didReserve = true
        MockResponse().success(ReserveUsernameResponse(Base64UrlSafe.encodeBytesWithoutPadding(Username.hash(username))))
      },
      Put("/v1/accounts/username_hash/confirm") { r ->
        didConfirm = true
        MockResponse().success()
      }
    )

    // WHEN
    RefreshOwnProfileJob.checkUsernameIsInSync()

    // THEN
    assertTrue(didReserve)
    assertTrue(didConfirm)
    assertFalse(SignalStore.phoneNumberPrivacy().isUsernameOutOfSync)
  }

  @Test
  fun givenLocalAndServerMatch_whenICheckUsernameIsInSync_thenIExpectNoRetry() {
    // GIVEN
    var didReserve = false
    var didConfirm = false
    val username = "hello.32"
    SignalDatabase.recipients.setUsername(harness.self.id, username)
    InstrumentationApplicationDependencyProvider.addMockWebRequestHandlers(
      Get("/v1/accounts/whoami") { r ->
        MockResponse().success(
          WhoAmIResponse().apply {
            usernameHash = Base64UrlSafe.encodeBytesWithoutPadding(Username.hash(username))
          }
        )
      },
      Put("/v1/accounts/username_hash/reserve") { r ->
        didReserve = true
        MockResponse().success(ReserveUsernameResponse(Base64UrlSafe.encodeBytesWithoutPadding(Username.hash(username))))
      },
      Put("/v1/accounts/username_hash/confirm") { r ->
        didConfirm = true
        MockResponse().success()
      }
    )

    // WHEN
    RefreshOwnProfileJob.checkUsernameIsInSync()

    // THEN
    assertFalse(didReserve)
    assertFalse(didConfirm)
    assertFalse(SignalStore.phoneNumberPrivacy().isUsernameOutOfSync)
  }

  @Test
  fun givenMismatchAndReservationFails_whenICheckUsernameIsInSync_thenIExpectNoConfirm() {
    // GIVEN
    var didReserve = false
    var didConfirm = false
    val username = "hello.32"
    SignalDatabase.recipients.setUsername(harness.self.id, username)
    InstrumentationApplicationDependencyProvider.addMockWebRequestHandlers(
      Get("/v1/accounts/whoami") { r ->
        MockResponse().success(
          WhoAmIResponse().apply {
            usernameHash = Base64UrlSafe.encodeBytesWithoutPadding(Username.hash("${username}23"))
          }
        )
      },
      Put("/v1/accounts/username_hash/reserve") { r ->
        didReserve = true
        MockResponse().failure(418)
      },
      Put("/v1/accounts/username_hash/confirm") { r ->
        didConfirm = true
        MockResponse().success()
      }
    )

    // WHEN
    RefreshOwnProfileJob.checkUsernameIsInSync()

    // THEN
    assertTrue(didReserve)
    assertFalse(didConfirm)
    assertTrue(SignalStore.phoneNumberPrivacy().isUsernameOutOfSync)
  }
}
