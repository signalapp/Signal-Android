/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import android.app.Application
import assertk.assertThat
import assertk.assertions.isTrue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.ServiceId
import org.signal.libsignal.net.PreKeyCounts
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.network.api.KeysApiV2
import org.signal.remoteconfig.RemoteConfig
import org.thoughtcrime.securesms.crypto.PreKeyUtil
import org.thoughtcrime.securesms.crypto.storage.PreKeyMetadataStore
import org.thoughtcrime.securesms.crypto.storage.SignalServiceAccountDataStoreImpl
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.MiscellaneousValues
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import org.thoughtcrime.securesms.testutil.MockSignalStoreRule
import java.io.IOException
import java.util.UUID
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import org.signal.libsignal.protocol.ServiceId as LibSignalServiceId

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class PreKeysSyncJobTest {

  @get:Rule
  val mockSignalStore = MockSignalStoreRule()

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  private val misc: MiscellaneousValues = mockk(relaxUnitFun = true)
  private val aciMetadataStore: PreKeyMetadataStore = mockk(relaxUnitFun = true)
  private val pniMetadataStore: PreKeyMetadataStore = mockk(relaxUnitFun = true)
  private val aciProtocolStore: SignalServiceAccountDataStoreImpl = mockk(relaxed = true)
  private val pniProtocolStore: SignalServiceAccountDataStoreImpl = mockk(relaxed = true)

  @Before
  fun setUp() {
    every { SignalStore.misc } returns misc

    every { mockSignalStore.account.isRegistered } returns true
    every { mockSignalStore.account.aci } returns ServiceId.ACI.from(UUID.randomUUID())
    every { mockSignalStore.account.pni } returns ServiceId.PNI.from(UUID.randomUUID())
    every { mockSignalStore.account.aciPreKeys } returns aciMetadataStore
    every { mockSignalStore.account.pniPreKeys } returns pniMetadataStore

    // Default metadata: everything is fresh and registered, so absent a force, no rotation triggers.
    listOf(aciMetadataStore, pniMetadataStore).forEach {
      every { it.isSignedPreKeyRegistered } returns true
      every { it.activeSignedPreKeyId } returns 1
      every { it.lastResortKyberPreKeyId } returns 1
      every { it.lastSignedPreKeyRotationTime } returns System.currentTimeMillis()
      every { it.lastResortKyberPreKeyRotationTime } returns System.currentTimeMillis()
    }

    every { misc.lastForcedPreKeyRefresh } returns 0L
    every { misc.forcePniSignedPreKeyRotation } returns false

    // Read outside of `every` blocks: the first read lazily builds the network module, which touches other mocks and would corrupt MockK's recording.
    val protocolStore = AppDependencies.protocolStore
    val keysApiV2 = AppDependencies.keysApiV2

    every { protocolStore.aci() } returns aciProtocolStore
    every { protocolStore.pni() } returns pniProtocolStore
    every { protocolStore.pniOrNull() } returns pniProtocolStore

    val identityKeyPair = IdentityKeyPair.generate()
    every { aciProtocolStore.identityKeyPair } returns identityKeyPair
    every { pniProtocolStore.identityKeyPair } returns identityKeyPair

    // Counts well above ONE_TIME_PREKEY_MINIMUM (10) so we don't generate one-time keys unless forced.
    coEvery { keysApiV2.getPreKeyCounts() } returns RequestResult.Success(PreKeyCounts(100, 100, 100, 100))
    coEvery { keysApiV2.setSignedEcPreKey(any(), any()) } returns RequestResult.Success(Unit)
    coEvery { keysApiV2.setLastResortKemPreKey(any(), any()) } returns RequestResult.Success(Unit)
    coEvery { keysApiV2.setOneTimeEcPreKeys(any(), any()) } returns RequestResult.Success(Unit)
    coEvery { keysApiV2.setOneTimeKemPreKeys(any(), any()) } returns RequestResult.Success(Unit)
    // Consistency check (only reached when forceRotationRequested=true) returns "everything matches".
    coEvery { keysApiV2.checkRepeatedUseKeysConsistency(any(), any(), any(), any(), any(), any()) } returns RequestResult.Success(Unit)

    mockkObject(RemoteConfig)
    every { RemoteConfig.preKeyForceRefreshInterval } returns 1.hours.inWholeMilliseconds
    // Used by BaseJob's retry-backoff path when a syncPreKeys call throws a retryable IOException.
    every { RemoteConfig.defaultMaxBackoff } returns 1.hours.inWholeMilliseconds

    mockkStatic(PreKeyUtil::class)
    every { PreKeyUtil.generateAndStoreSignedPreKey(any(), any()) } answers { fakeSignedPreKey() }
    every { PreKeyUtil.generateAndStoreOneTimeEcPreKeys(any(), any()) } returns emptyList()
    every { PreKeyUtil.generateAndStoreLastResortKyberPreKey(any(), any()) } answers { fakeKyberPreKey() }
    every { PreKeyUtil.generateAndStoreOneTimeKyberPreKeys(any(), any()) } returns emptyList()
    every { PreKeyUtil.cleanSignedPreKeys(any(), any()) } returns Unit
    every { PreKeyUtil.cleanLastResortKyberPreKeys(any(), any()) } returns Unit
    every { PreKeyUtil.cleanOneTimePreKeys(any()) } returns Unit
  }

  @After
  fun tearDown() {
    unmockkObject(RemoteConfig)
    unmockkStatic(PreKeyUtil::class)
  }

  @Test
  fun `when forcePniSignedPreKeyRotation flag set, PNI sync runs forced and ACI does not`() {
    every { misc.forcePniSignedPreKeyRotation } returns true

    val result = PreKeysSyncJob.create(forceRotationRequested = false).run()

    assertThat(result.isSuccess).isTrue()

    verify(exactly = 1) { PreKeyUtil.generateAndStoreSignedPreKey(pniProtocolStore, pniMetadataStore) }
    verify(exactly = 1) { PreKeyUtil.generateAndStoreLastResortKyberPreKey(pniProtocolStore, pniMetadataStore) }
    verify(exactly = 1) { PreKeyUtil.generateAndStoreOneTimeEcPreKeys(pniProtocolStore, pniMetadataStore) }
    verify(exactly = 1) { PreKeyUtil.generateAndStoreOneTimeKyberPreKeys(pniProtocolStore, pniMetadataStore) }
    verify(exactly = 0) { PreKeyUtil.generateAndStoreSignedPreKey(aciProtocolStore, aciMetadataStore) }
    verify(exactly = 0) { PreKeyUtil.generateAndStoreLastResortKyberPreKey(aciProtocolStore, aciMetadataStore) }
    verify(exactly = 0) { PreKeyUtil.generateAndStoreOneTimeEcPreKeys(aciProtocolStore, aciMetadataStore) }
    verify(exactly = 0) { PreKeyUtil.generateAndStoreOneTimeKyberPreKeys(aciProtocolStore, aciMetadataStore) }
    coVerify(exactly = 1) { AppDependencies.keysApiV2.setSignedEcPreKey(LibSignalServiceId.Kind.PNI, any()) }
    coVerify(exactly = 1) { AppDependencies.keysApiV2.setLastResortKemPreKey(LibSignalServiceId.Kind.PNI, any()) }
    coVerify(exactly = 1) { AppDependencies.keysApiV2.setOneTimeEcPreKeys(LibSignalServiceId.Kind.PNI, any()) }
    coVerify(exactly = 1) { AppDependencies.keysApiV2.setOneTimeKemPreKeys(LibSignalServiceId.Kind.PNI, any()) }
    coVerify(exactly = 0) { AppDependencies.keysApiV2.setSignedEcPreKey(LibSignalServiceId.Kind.ACI, any()) }
    coVerify(exactly = 0) { AppDependencies.keysApiV2.setLastResortKemPreKey(LibSignalServiceId.Kind.ACI, any()) }
    coVerify(exactly = 0) { AppDependencies.keysApiV2.setOneTimeEcPreKeys(LibSignalServiceId.Kind.ACI, any()) }
    coVerify(exactly = 0) { AppDependencies.keysApiV2.setOneTimeKemPreKeys(LibSignalServiceId.Kind.ACI, any()) }
    verify(exactly = 1) { misc.forcePniSignedPreKeyRotation = false }
  }

  @Test
  fun `when forcePniSignedPreKeyRotation flag set but uploads fail, flag is preserved`() {
    every { misc.forcePniSignedPreKeyRotation } returns true
    // Fail PNI's upload so the job retries before the flag-clear runs.
    coEvery { AppDependencies.keysApiV2.setSignedEcPreKey(any(), any()) } returns RequestResult.RetryableNetworkError(IOException("simulated"))

    val result = PreKeysSyncJob.create(forceRotationRequested = false).run()

    assertThat(result.isRetry).isTrue()

    verify(exactly = 0) { misc.forcePniSignedPreKeyRotation = false }
  }

  @Test
  fun `when fetching prekey counts fails with a network error, job retries without generating or uploading keys`() {
    every { misc.forcePniSignedPreKeyRotation } returns true
    coEvery { AppDependencies.keysApiV2.getPreKeyCounts() } returns RequestResult.RetryableNetworkError(IOException("simulated"))

    val result = PreKeysSyncJob.create(forceRotationRequested = false).run()

    assertThat(result.isRetry).isTrue()

    verify(exactly = 0) { PreKeyUtil.generateAndStoreSignedPreKey(any(), any()) }
    verify(exactly = 0) { PreKeyUtil.generateAndStoreOneTimeEcPreKeys(any(), any()) }
    coVerify(exactly = 0) { AppDependencies.keysApiV2.setSignedEcPreKey(any(), any()) }
    coVerify(exactly = 0) { AppDependencies.keysApiV2.setOneTimeEcPreKeys(any(), any()) }
    verify(exactly = 0) { misc.lastFullPrekeyRefreshTime = any() }
    verify(exactly = 0) { misc.forcePniSignedPreKeyRotation = false }
  }

  @Test
  fun `when a one-time upload fails, repeated-use keys are not uploaded`() {
    every { misc.forcePniSignedPreKeyRotation } returns true
    coEvery { AppDependencies.keysApiV2.setOneTimeKemPreKeys(any(), any()) } returns RequestResult.RetryableNetworkError(IOException("simulated"))

    val result = PreKeysSyncJob.create(forceRotationRequested = false).run()

    assertThat(result.isRetry).isTrue()

    coVerify(exactly = 1) { AppDependencies.keysApiV2.setOneTimeEcPreKeys(LibSignalServiceId.Kind.PNI, any()) }
    coVerify(exactly = 0) { AppDependencies.keysApiV2.setSignedEcPreKey(any(), any()) }
    coVerify(exactly = 0) { AppDependencies.keysApiV2.setLastResortKemPreKey(any(), any()) }
    verify(exactly = 0) { pniMetadataStore.activeSignedPreKeyId = any() }
    verify(exactly = 0) { pniMetadataStore.lastResortKyberPreKeyId = any() }
    verify(exactly = 0) { misc.forcePniSignedPreKeyRotation = false }
  }

  @Test
  fun `when a later upload fails, keys uploaded before it are still recorded as registered`() {
    every { misc.forcePniSignedPreKeyRotation } returns true
    coEvery { AppDependencies.keysApiV2.setLastResortKemPreKey(any(), any()) } returns RequestResult.RetryableNetworkError(IOException("simulated"))

    val result = PreKeysSyncJob.create(forceRotationRequested = false).run()

    assertThat(result.isRetry).isTrue()

    verify(exactly = 1) { pniMetadataStore.activeSignedPreKeyId = 42 }
    verify(exactly = 0) { pniMetadataStore.lastResortKyberPreKeyId = any() }
    verify(exactly = 0) { misc.forcePniSignedPreKeyRotation = false }
  }

  @Test
  fun `when forced rotation requested and ACI keys don't match the service, both identities rotate`() {
    coEvery { AppDependencies.keysApiV2.checkRepeatedUseKeysConsistency(LibSignalServiceId.Kind.ACI, any(), any(), any(), any(), any()) } returns RequestResult.NonSuccess(KeysApiV2.CheckRepeatedUseKeysError.Mismatch)

    val result = PreKeysSyncJob.create(forceRotationRequested = true).run()

    assertThat(result.isSuccess).isTrue()
    verify(exactly = 1) { PreKeyUtil.generateAndStoreSignedPreKey(aciProtocolStore, aciMetadataStore) }
    verify(exactly = 1) { PreKeyUtil.generateAndStoreSignedPreKey(pniProtocolStore, pniMetadataStore) }
    coVerify(exactly = 0) { AppDependencies.keysApiV2.checkRepeatedUseKeysConsistency(LibSignalServiceId.Kind.PNI, any(), any(), any(), any(), any()) }
  }

  @Test
  fun `when flag not set, no PNI force and flag write is skipped`() {
    every { misc.forcePniSignedPreKeyRotation } returns false

    val result = PreKeysSyncJob.create(forceRotationRequested = false).run()

    assertThat(result.isSuccess).isTrue()

    verify(exactly = 0) { PreKeyUtil.generateAndStoreSignedPreKey(pniProtocolStore, pniMetadataStore) }
    verify(exactly = 0) { PreKeyUtil.generateAndStoreSignedPreKey(aciProtocolStore, aciMetadataStore) }
    verify(exactly = 0) { misc.forcePniSignedPreKeyRotation = false }
  }

  @Test
  fun `flag set forces PNI rotation even when consistency check passes and time gate would skip`() {
    every { misc.forcePniSignedPreKeyRotation } returns true
    // forceRotationRequested=true + consistency checks pass + recent forced refresh (well within
    // preKeyForceRefreshInterval=1h) → without the flag, the existing logic would skip rotation.
    every { misc.lastForcedPreKeyRefresh } returns System.currentTimeMillis() - 1.minutes.inWholeMilliseconds

    val result = PreKeysSyncJob.create(forceRotationRequested = true).run()

    assertThat(result.isSuccess).isTrue()

    verify(exactly = 1) { PreKeyUtil.generateAndStoreSignedPreKey(pniProtocolStore, pniMetadataStore) }
    verify(exactly = 1) { PreKeyUtil.generateAndStoreLastResortKyberPreKey(pniProtocolStore, pniMetadataStore) }
    verify(exactly = 0) { PreKeyUtil.generateAndStoreSignedPreKey(aciProtocolStore, aciMetadataStore) }
    verify(exactly = 0) { PreKeyUtil.generateAndStoreLastResortKyberPreKey(aciProtocolStore, aciMetadataStore) }
    verify(exactly = 1) { misc.forcePniSignedPreKeyRotation = false }
  }

  private fun fakeSignedPreKey(): SignedPreKeyRecord = mockk(relaxed = true) {
    every { id } returns 42
  }

  private fun fakeKyberPreKey(): KyberPreKeyRecord = mockk(relaxed = true) {
    every { id } returns 42
  }
}
