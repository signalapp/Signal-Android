package org.thoughtcrime.securesms.components.settings.app.changenumber

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.FlakyTest
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.signal.core.util.ThreadUtil
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.dependencies.InstrumentationApplicationDependencyProvider
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.pin.KbsRepository
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.registration.VerifyAccountRepository
import org.thoughtcrime.securesms.registration.VerifyResponseProcessor
import org.thoughtcrime.securesms.testing.Get
import org.thoughtcrime.securesms.testing.MockProvider
import org.thoughtcrime.securesms.testing.Post
import org.thoughtcrime.securesms.testing.Put
import org.thoughtcrime.securesms.testing.SignalActivityRule
import org.thoughtcrime.securesms.testing.assertIs
import org.thoughtcrime.securesms.testing.assertIsNot
import org.thoughtcrime.securesms.testing.assertIsNotNull
import org.thoughtcrime.securesms.testing.assertIsNull
import org.thoughtcrime.securesms.testing.assertIsSize
import org.thoughtcrime.securesms.testing.connectionFailure
import org.thoughtcrime.securesms.testing.failure
import org.thoughtcrime.securesms.testing.parsedRequestBody
import org.thoughtcrime.securesms.testing.success
import org.thoughtcrime.securesms.testing.timeout
import org.whispersystems.signalservice.api.account.ChangePhoneNumberRequest
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.internal.push.MismatchedDevices
import org.whispersystems.signalservice.internal.push.PreKeyState
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ChangeNumberViewModelTest {

  @get:Rule
  val harness = SignalActivityRule()

  private lateinit var viewModel: ChangeNumberViewModel
  private lateinit var kbsRepository: KbsRepository

  @Before
  fun setUp() {
    ApplicationDependencies.getSignalServiceAccountManager().setSoTimeoutMillis(1000)
    kbsRepository = mock()
    ThreadUtil.runOnMainSync {
      viewModel = ChangeNumberViewModel(
        localNumber = harness.self.requireE164(),
        changeNumberRepository = ChangeNumberRepository(),
        savedState = SavedStateHandle(),
        password = SignalStore.account().servicePassword!!,
        verifyAccountRepository = VerifyAccountRepository(harness.application),
        kbsRepository = kbsRepository
      )

      viewModel.setNewCountry(1)
      viewModel.setNewNationalNumber("5555550102")
    }
  }

  @After
  fun tearDown() {
    InstrumentationApplicationDependencyProvider.clearHandlers()
  }

  @Test
  fun testChangeNumber_givenOnlyPrimaryAndNoRegLock() {
    // GIVEN
    val aci = Recipient.self().requireServiceId()
    val newPni = ServiceId.from(UUID.randomUUID())
    lateinit var changeNumberRequest: ChangePhoneNumberRequest
    lateinit var setPreKeysRequest: PreKeyState

    InstrumentationApplicationDependencyProvider.addMockWebRequestHandlers(
      Post("/v1/verification/session") { MockResponse().success(MockProvider.sessionMetadataJson.copy(verified = false)) },
      Put("/v1/verification/session/${MockProvider.sessionMetadataJson.id}/code") { MockResponse().success(MockProvider.sessionMetadataJson) },
      Get("/v1/devices") { MockResponse().success(MockProvider.primaryOnlyDeviceList) },
      Put("/v2/accounts/number") { r ->
        changeNumberRequest = r.parsedRequestBody()
        MockResponse().success(MockProvider.createVerifyAccountResponse(aci, newPni))
      },
      Put("/v2/keys") { r ->
        setPreKeysRequest = r.parsedRequestBody()
        MockResponse().success()
      },
      Get("/v1/certificate/delivery") { MockResponse().success(MockProvider.senderCertificate) }
    )

    // WHEN
    viewModel.requestVerificationCode(VerifyAccountRepository.Mode.SMS_WITHOUT_LISTENER, null, null).blockingGet().resultOrThrow
    viewModel.verifyCodeWithoutRegistrationLock("123456").blockingGet().resultOrThrow

    // THEN
    assertSuccess(newPni, changeNumberRequest, setPreKeysRequest)
  }

  /**
   * If we encounter a server error, this means the server ack our request and rejected it. In this
   * case we know the change *did not* take on the server and can reset to a clean state.
   */
  @Test
  fun testChangeNumber_givenServerFailedApiCall() {
    // GIVEN
    val oldPni = Recipient.self().requirePni()
    val oldE164 = Recipient.self().requireE164()

    InstrumentationApplicationDependencyProvider.addMockWebRequestHandlers(
      Post("/v1/verification/session") { MockResponse().success(MockProvider.sessionMetadataJson.copy(verified = false)) },
      Put("/v1/verification/session/${MockProvider.sessionMetadataJson.id}/code") { MockResponse().success(MockProvider.sessionMetadataJson) },
      Get("/v1/devices") { MockResponse().success(MockProvider.primaryOnlyDeviceList) },
      Put("/v2/accounts/number") { MockResponse().failure(500) }
    )

    // WHEN
    viewModel.requestVerificationCode(VerifyAccountRepository.Mode.SMS_WITHOUT_LISTENER, null, null).blockingGet().resultOrThrow
    val processor: VerifyResponseProcessor = viewModel.verifyCodeWithoutRegistrationLock("123456").blockingGet()

    // THEN
    processor.isServerSentError() assertIs true
    Recipient.self().requireE164() assertIs oldE164
    Recipient.self().requirePni() assertIs oldPni
    SignalStore.misc().pendingChangeNumberMetadata.assertIsNull()
  }

  /**
   * If we encounter a non-server error like a timeout or bad SSL, we do not know the state of our change
   * number on the server side. We have to do a whoami call to query the server for our details and then
   * respond accordingly.
   *
   * In this case, the whoami is our old details, so we can know the change *did not* take on the server
   * and can reset to a clean state.
   */
  @Test
  fun testChangeNumber_givenNetworkFailedApiCallEnRouteToServer() {
    // GIVEN
    val aci = Recipient.self().requireServiceId()
    val oldPni = Recipient.self().requirePni()
    val oldE164 = Recipient.self().requireE164()

    InstrumentationApplicationDependencyProvider.addMockWebRequestHandlers(
      Post("/v1/verification/session") { MockResponse().success(MockProvider.sessionMetadataJson.copy(verified = false)) },
      Put("/v1/verification/session/${MockProvider.sessionMetadataJson.id}/code") { MockResponse().success(MockProvider.sessionMetadataJson) },
      Get("/v1/devices") { MockResponse().success(MockProvider.primaryOnlyDeviceList) },
      Put("/v2/accounts/number") { MockResponse().connectionFailure() },
      Get("/v1/accounts/whoami") { MockResponse().success(MockProvider.createWhoAmIResponse(aci, oldPni, oldE164)) }
    )

    // WHEN
    viewModel.requestVerificationCode(VerifyAccountRepository.Mode.SMS_WITHOUT_LISTENER, null, null).blockingGet().resultOrThrow
    val processor: VerifyResponseProcessor = viewModel.verifyCodeWithoutRegistrationLock("123456").blockingGet()

    // THEN
    processor.isServerSentError() assertIs false
    Recipient.self().requireE164() assertIs oldE164
    Recipient.self().requirePni() assertIs oldPni
    SignalStore.misc().isChangeNumberLocked assertIs false
    SignalStore.misc().pendingChangeNumberMetadata.assertIsNull()
  }

  /**
   * If we encounter a non-server error like a timeout or bad SSL, we do not know the state of our change
   * number on the server side. We have to do a whoami call to query the server for our details and then
   * respond accordingly.
   *
   * In this case, the whoami is our new details, so we can know the change *did* take on the server
   * and need to keep the app in a locked state. The test then uses the ChangeNumberLockActivity to unlock
   * and apply the pending state after confirming the change on the server.
   */
  @Test
  @FlakyTest
  @Ignore("Test sometimes requires manual intervention to continue.")
  fun testChangeNumber_givenNetworkFailedApiCallEnRouteToClient() {
    // GIVEN
    val aci = Recipient.self().requireServiceId()
    val oldPni = Recipient.self().requirePni()
    val oldE164 = Recipient.self().requireE164()
    val newPni = ServiceId.from(UUID.randomUUID())

    lateinit var changeNumberRequest: ChangePhoneNumberRequest
    lateinit var setPreKeysRequest: PreKeyState

    InstrumentationApplicationDependencyProvider.addMockWebRequestHandlers(
      Post("/v1/verification/session") { MockResponse().success(MockProvider.sessionMetadataJson.copy(verified = false)) },
      Put("/v1/verification/session/${MockProvider.sessionMetadataJson.id}/code") { MockResponse().success(MockProvider.sessionMetadataJson) },
      Get("/v1/devices") { MockResponse().success(MockProvider.primaryOnlyDeviceList) },
      Put("/v2/accounts/number") { r ->
        changeNumberRequest = r.parsedRequestBody()
        MockResponse().timeout()
      },
      Get("/v1/accounts/whoami") { MockResponse().success(MockProvider.createWhoAmIResponse(aci, newPni, "+15555550102")) },
      Put("/v2/keys") { r ->
        setPreKeysRequest = r.parsedRequestBody()
        MockResponse().success()
      },
      Get("/v1/certificate/delivery") { MockResponse().success(MockProvider.senderCertificate) }
    )

    // WHEN
    viewModel.requestVerificationCode(VerifyAccountRepository.Mode.SMS_WITHOUT_LISTENER, null, null).blockingGet().resultOrThrow
    val processor: VerifyResponseProcessor = viewModel.verifyCodeWithoutRegistrationLock("123456").blockingGet()

    // THEN
    processor.isServerSentError() assertIs false
    Recipient.self().requireE164() assertIs oldE164
    Recipient.self().requirePni() assertIs oldPni
    SignalStore.misc().isChangeNumberLocked assertIs true
    SignalStore.misc().pendingChangeNumberMetadata.assertIsNotNull()

    // WHEN AGAIN Processing lock
    val scenario = harness.launchActivity<ChangeNumberLockActivity>()
    scenario.onActivity {}
    ThreadUtil.sleep(500)

    // THEN AGAIN
    assertSuccess(newPni, changeNumberRequest, setPreKeysRequest)
  }

  @Test
  fun testChangeNumber_givenOnlyPrimaryAndRegistrationLock() {
    // GIVEN
    val aci = Recipient.self().requireServiceId()
    val newPni = ServiceId.from(UUID.randomUUID())

    lateinit var changeNumberRequest: ChangePhoneNumberRequest
    lateinit var setPreKeysRequest: PreKeyState

    MockProvider.mockGetRegistrationLockStringFlow(kbsRepository)

    InstrumentationApplicationDependencyProvider.addMockWebRequestHandlers(
      Post("/v1/verification/session") { MockResponse().success(MockProvider.sessionMetadataJson.copy(verified = false)) },
      Put("/v1/verification/session/${MockProvider.sessionMetadataJson.id}/code") { MockResponse().success(MockProvider.sessionMetadataJson) },
      Get("/v1/devices") { MockResponse().success(MockProvider.primaryOnlyDeviceList) },
      Put("/v2/accounts/number") { r ->
        changeNumberRequest = r.parsedRequestBody()
        if (changeNumberRequest.registrationLock.isNullOrEmpty()) {
          MockResponse().failure(423, MockProvider.lockedFailure)
        } else {
          MockResponse().success(MockProvider.createVerifyAccountResponse(aci, newPni))
        }
      },
      Put("/v2/keys") { r ->
        setPreKeysRequest = r.parsedRequestBody()
        MockResponse().success()
      },
      Get("/v1/certificate/delivery") { MockResponse().success(MockProvider.senderCertificate) }
    )

    // WHEN
    viewModel.requestVerificationCode(VerifyAccountRepository.Mode.SMS_WITHOUT_LISTENER, null, null).blockingGet().resultOrThrow
    viewModel.verifyCodeWithoutRegistrationLock("123456").blockingGet().also { processor ->
      processor.registrationLock() assertIs true
      Recipient.self().requirePni() assertIsNot newPni
      SignalStore.misc().pendingChangeNumberMetadata.assertIsNull()
    }

    viewModel.verifyCodeAndRegisterAccountWithRegistrationLock("pin").blockingGet().resultOrThrow

    // THEN
    assertSuccess(newPni, changeNumberRequest, setPreKeysRequest)
  }

  @Test
  fun testChangeNumber_givenMismatchedDevicesOnFirstCall() {
    // GIVEN
    val aci = Recipient.self().requireServiceId()
    val newPni = ServiceId.from(UUID.randomUUID())
    lateinit var changeNumberRequest: ChangePhoneNumberRequest
    lateinit var setPreKeysRequest: PreKeyState

    InstrumentationApplicationDependencyProvider.addMockWebRequestHandlers(
      Post("/v1/verification/session") { MockResponse().success(MockProvider.sessionMetadataJson.copy(verified = false)) },
      Put("/v1/verification/session/${MockProvider.sessionMetadataJson.id}/code") { MockResponse().success(MockProvider.sessionMetadataJson) },
      Get("/v1/devices") { MockResponse().success(MockProvider.primaryOnlyDeviceList) },
      Put("/v2/accounts/number") { r ->
        changeNumberRequest = r.parsedRequestBody()
        if (changeNumberRequest.deviceMessages.isEmpty()) {
          MockResponse().failure(
            409,
            MismatchedDevices().apply {
              missingDevices = listOf(2)
              extraDevices = emptyList()
            }
          )
        } else {
          MockResponse().success(MockProvider.createVerifyAccountResponse(aci, newPni))
        }
      },
      Get("/v2/keys/$aci/2") {
        MockResponse().success(MockProvider.createPreKeyResponse(deviceId = 2))
      },
      Put("/v2/keys") { r ->
        setPreKeysRequest = r.parsedRequestBody()
        MockResponse().success()
      },
      Get("/v1/certificate/delivery") { MockResponse().success(MockProvider.senderCertificate) }
    )

    // WHEN
    viewModel.requestVerificationCode(VerifyAccountRepository.Mode.SMS_WITHOUT_LISTENER, null, null).blockingGet().resultOrThrow
    viewModel.verifyCodeWithoutRegistrationLock("123456").blockingGet().resultOrThrow

    // THEN
    assertSuccess(newPni, changeNumberRequest, setPreKeysRequest)
  }

  @Test
  fun testChangeNumber_givenRegLockAndMismatchedDevicesOnFirstTwoCalls() {
    // GIVEN
    val aci = Recipient.self().requireServiceId()
    val newPni = ServiceId.from(UUID.randomUUID())

    lateinit var changeNumberRequest: ChangePhoneNumberRequest
    lateinit var setPreKeysRequest: PreKeyState

    MockProvider.mockGetRegistrationLockStringFlow(kbsRepository)

    InstrumentationApplicationDependencyProvider.addMockWebRequestHandlers(
      Post("/v1/verification/session") { MockResponse().success(MockProvider.sessionMetadataJson.copy(verified = false)) },
      Put("/v1/verification/session/${MockProvider.sessionMetadataJson.id}/code") { MockResponse().success(MockProvider.sessionMetadataJson) },
      Put("/v2/accounts/number") { r ->
        changeNumberRequest = r.parsedRequestBody()
        if (changeNumberRequest.registrationLock.isNullOrEmpty()) {
          MockResponse().failure(423, MockProvider.lockedFailure)
        } else if (changeNumberRequest.deviceMessages.isEmpty()) {
          MockResponse().failure(
            409,
            MismatchedDevices().apply {
              missingDevices = listOf(2)
              extraDevices = emptyList()
            }
          )
        } else if (changeNumberRequest.deviceMessages.size == 1) {
          MockResponse().failure(
            409,
            MismatchedDevices().apply {
              missingDevices = listOf(2, 3)
              extraDevices = emptyList()
            }
          )
        } else {
          MockResponse().success(MockProvider.createVerifyAccountResponse(aci, newPni))
        }
      },
      Get("/v2/keys/$aci/2") {
        MockResponse().success(MockProvider.createPreKeyResponse(deviceId = 2))
      },
      Get("/v2/keys/$aci/3") {
        MockResponse().success(MockProvider.createPreKeyResponse(deviceId = 3))
      },
      Put("/v2/keys") { r ->
        setPreKeysRequest = r.parsedRequestBody()
        MockResponse().success()
      },
      Get("/v1/certificate/delivery") { MockResponse().success(MockProvider.senderCertificate) }
    )

    // WHEN
    viewModel.requestVerificationCode(VerifyAccountRepository.Mode.SMS_WITHOUT_LISTENER, null, null).blockingGet().resultOrThrow
    viewModel.verifyCodeWithoutRegistrationLock("123456").blockingGet().also { processor ->
      processor.registrationLock() assertIs true
      Recipient.self().requirePni() assertIsNot newPni
      SignalStore.misc().pendingChangeNumberMetadata.assertIsNull()
    }

    viewModel.verifyCodeAndRegisterAccountWithRegistrationLock("pin").blockingGet().resultOrThrow

    // THEN
    assertSuccess(newPni, changeNumberRequest, setPreKeysRequest)
  }

  private fun assertSuccess(newPni: ServiceId, changeNumberRequest: ChangePhoneNumberRequest, setPreKeysRequest: PreKeyState) {
    val pniProtocolStore = ApplicationDependencies.getProtocolStore().pni()
    val pniMetadataStore = SignalStore.account().pniPreKeys

    Recipient.self().requireE164() assertIs "+15555550102"
    Recipient.self().requirePni() assertIs newPni

    SignalStore.account().pniRegistrationId assertIs changeNumberRequest.pniRegistrationIds["1"]!!
    SignalStore.account().pniIdentityKey.publicKey assertIs changeNumberRequest.pniIdentityKey
    pniMetadataStore.activeSignedPreKeyId assertIs changeNumberRequest.devicePniSignedPrekeys["1"]!!.keyId

    val activeSignedPreKey: SignedPreKeyRecord = pniProtocolStore.loadSignedPreKey(pniMetadataStore.activeSignedPreKeyId)
    activeSignedPreKey.keyPair.publicKey assertIs changeNumberRequest.devicePniSignedPrekeys["1"]!!.publicKey
    activeSignedPreKey.signature assertIs changeNumberRequest.devicePniSignedPrekeys["1"]!!.signature

    setPreKeysRequest.signedPreKey.publicKey assertIs activeSignedPreKey.keyPair.publicKey
    setPreKeysRequest.preKeys assertIsSize 100

    SignalStore.misc().pendingChangeNumberMetadata.assertIsNull()
  }
}
