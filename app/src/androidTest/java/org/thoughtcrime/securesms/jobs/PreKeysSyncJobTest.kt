package org.thoughtcrime.securesms.jobs

import androidx.test.ext.junit.runners.AndroidJUnit4
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.libsignal.protocol.ecc.Curve
import org.thoughtcrime.securesms.crypto.storage.PreKeyMetadataStore
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.dependencies.InstrumentationApplicationDependencyProvider
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.testing.Get
import org.thoughtcrime.securesms.testing.Put
import org.thoughtcrime.securesms.testing.SignalActivityRule
import org.thoughtcrime.securesms.testing.assertIs
import org.thoughtcrime.securesms.testing.assertIsNot
import org.thoughtcrime.securesms.testing.parsedRequestBody
import org.thoughtcrime.securesms.testing.success
import org.whispersystems.signalservice.api.push.SignedPreKeyEntity
import org.whispersystems.signalservice.internal.push.PreKeyState
import org.whispersystems.signalservice.internal.push.PreKeyStatus

@RunWith(AndroidJUnit4::class)
class PreKeysSyncJobTest {

  @get:Rule
  val harness = SignalActivityRule()

  private val aciPreKeyMeta: PreKeyMetadataStore
    get() = SignalStore.account().aciPreKeys

  private val pniPreKeyMeta: PreKeyMetadataStore
    get() = SignalStore.account().pniPreKeys

  private lateinit var job: PreKeysSyncJob

  @Before
  fun setUp() {
    job = PreKeysSyncJob()
  }

  @After
  fun tearDown() {
    InstrumentationApplicationDependencyProvider.clearHandlers()
  }

  /**
   * Create signed prekeys for both identities when both do not have registered prekeys according
   * to our local state.
   */
  @Test
  fun runWithoutRegisteredKeysForBothIdentities() {
    // GIVEN
    aciPreKeyMeta.isSignedPreKeyRegistered = false
    pniPreKeyMeta.isSignedPreKeyRegistered = false

    lateinit var aciSignedPreKey: SignedPreKeyEntity
    lateinit var pniSignedPreKey: SignedPreKeyEntity

    InstrumentationApplicationDependencyProvider.addMockWebRequestHandlers(
      Put("/v2/keys/signed?identity=aci") { r ->
        aciSignedPreKey = r.parsedRequestBody()
        MockResponse().success()
      },
      Put("/v2/keys/signed?identity=pni") { r ->
        pniSignedPreKey = r.parsedRequestBody()
        MockResponse().success()
      }
    )

    // WHEN
    val result: Job.Result = job.run()

    // THEN
    result.isSuccess assertIs true

    aciPreKeyMeta.isSignedPreKeyRegistered assertIs true
    pniPreKeyMeta.isSignedPreKeyRegistered assertIs true

    val aciVerifySignatureResult = Curve.verifySignature(
      ApplicationDependencies.getProtocolStore().aci().identityKeyPair.publicKey.publicKey,
      aciSignedPreKey.publicKey.serialize(),
      aciSignedPreKey.signature
    )
    aciVerifySignatureResult assertIs true

    val pniVerifySignatureResult = Curve.verifySignature(
      ApplicationDependencies.getProtocolStore().pni().identityKeyPair.publicKey.publicKey,
      pniSignedPreKey.publicKey.serialize(),
      pniSignedPreKey.signature
    )
    pniVerifySignatureResult assertIs true
  }

  /**
   * With 100 prekeys registered for each identity, do nothing.
   */
  @Test
  fun runWithRegisteredKeysForBothIdentities() {
    // GIVEN
    val currentAciKeyId = aciPreKeyMeta.activeSignedPreKeyId
    val currentPniKeyId = pniPreKeyMeta.activeSignedPreKeyId

    InstrumentationApplicationDependencyProvider.addMockWebRequestHandlers(
      Get("/v2/keys?identity=aci") { MockResponse().success(PreKeyStatus(100)) },
      Get("/v2/keys?identity=pni") { MockResponse().success(PreKeyStatus(100)) }
    )

    // WHEN
    val result: Job.Result = job.run()

    // THEN
    result.isSuccess assertIs true

    aciPreKeyMeta.activeSignedPreKeyId assertIs currentAciKeyId
    pniPreKeyMeta.activeSignedPreKeyId assertIs currentPniKeyId
  }

  /**
   * With 100 prekeys registered for ACI, but no PNI prekeys registered according to local state,
   * do nothing for ACI but create PNI prekeys and update local state.
   */
  @Test
  fun runWithRegisteredKeysForAciIdentityOnly() {
    // GIVEN
    pniPreKeyMeta.isSignedPreKeyRegistered = false

    val currentAciKeyId = aciPreKeyMeta.activeSignedPreKeyId
    val currentPniKeyId = pniPreKeyMeta.activeSignedPreKeyId

    InstrumentationApplicationDependencyProvider.addMockWebRequestHandlers(
      Get("/v2/keys?identity=aci") { MockResponse().success(PreKeyStatus(100)) },
      Put("/v2/keys/signed?identity=pni") { MockResponse().success() }
    )

    // WHEN
    val result: Job.Result = job.run()

    // THEN
    result.isSuccess assertIs true

    pniPreKeyMeta.isSignedPreKeyRegistered assertIs true
    aciPreKeyMeta.activeSignedPreKeyId assertIs currentAciKeyId
    pniPreKeyMeta.activeSignedPreKeyId assertIsNot currentPniKeyId
  }

  /**
   * With <10 prekeys registered for each identity, upload new.
   */
  @Test
  fun runWithLowNumberOfRegisteredKeysForBothIdentities() {
    // GIVEN
    val currentAciKeyId = aciPreKeyMeta.activeSignedPreKeyId
    val currentPniKeyId = pniPreKeyMeta.activeSignedPreKeyId

    val currentNextAciPreKeyId = aciPreKeyMeta.nextOneTimePreKeyId
    val currentNextPniPreKeyId = pniPreKeyMeta.nextOneTimePreKeyId

    lateinit var aciPreKeyStateRequest: PreKeyState
    lateinit var pniPreKeyStateRequest: PreKeyState

    InstrumentationApplicationDependencyProvider.addMockWebRequestHandlers(
      Get("/v2/keys?identity=aci") { MockResponse().success(PreKeyStatus(5)) },
      Get("/v2/keys?identity=pni") { MockResponse().success(PreKeyStatus(5)) },
      Put("/v2/keys/?identity=aci") { r ->
        aciPreKeyStateRequest = r.parsedRequestBody()
        MockResponse().success()
      },
      Put("/v2/keys/?identity=pni") { r ->
        pniPreKeyStateRequest = r.parsedRequestBody()
        MockResponse().success()
      }
    )

    // WHEN
    val result: Job.Result = job.run()

    // THEN
    result.isSuccess assertIs true
    aciPreKeyMeta.activeSignedPreKeyId assertIsNot currentAciKeyId
    pniPreKeyMeta.activeSignedPreKeyId assertIsNot currentPniKeyId

    aciPreKeyMeta.nextOneTimePreKeyId assertIsNot currentNextAciPreKeyId
    pniPreKeyMeta.nextOneTimePreKeyId assertIsNot currentNextPniPreKeyId

    ApplicationDependencies.getProtocolStore().aci().identityKeyPair.publicKey.let { aciIdentityKey ->
      aciPreKeyStateRequest.identityKey assertIs aciIdentityKey

      val verifySignatureResult = Curve.verifySignature(
        aciIdentityKey.publicKey,
        aciPreKeyStateRequest.signedPreKey.publicKey.serialize(),
        aciPreKeyStateRequest.signedPreKey.signature
      )
      verifySignatureResult assertIs true
    }

    ApplicationDependencies.getProtocolStore().pni().identityKeyPair.publicKey.let { pniIdentityKey ->
      pniPreKeyStateRequest.identityKey assertIs pniIdentityKey

      val verifySignatureResult = Curve.verifySignature(
        pniIdentityKey.publicKey,
        pniPreKeyStateRequest.signedPreKey.publicKey.serialize(),
        pniPreKeyStateRequest.signedPreKey.signature
      )
      verifySignatureResult assertIs true
    }
  }
}
