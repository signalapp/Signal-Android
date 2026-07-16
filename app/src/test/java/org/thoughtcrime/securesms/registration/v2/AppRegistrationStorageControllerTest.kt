/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.v2

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import okio.ByteString.Companion.toByteString
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.AccountEntropyPool
import org.signal.core.models.MasterKey
import org.signal.core.models.ServiceId.ACI
import org.signal.core.models.ServiceId.PNI
import org.signal.core.util.contentproviders.BlobProvider
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.registration.proto.AccountData
import org.signal.registration.proto.LinkedDeviceData
import org.signal.registration.proto.RegistrationData
import org.thoughtcrime.securesms.crypto.PreKeyUtil
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.runJobBlocking
import org.thoughtcrime.securesms.jobs.DirectoryRefreshJob
import org.thoughtcrime.securesms.jobs.PreKeysSyncJob
import org.thoughtcrime.securesms.jobs.RefreshOwnProfileJob
import org.thoughtcrime.securesms.jobs.RotateCertificateJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.registration.util.RegistrationUtil
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import org.thoughtcrime.securesms.testutil.SignalDatabaseRule
import org.thoughtcrime.securesms.testutil.SignalStoreRule
import org.thoughtcrime.securesms.util.TextSecurePreferences
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE)
class AppRegistrationStorageControllerTest {

  companion object {
    private const val E164 = "+15555550101"
    private const val SERVICE_PASSWORD = "service-password"
    private const val PIN = "1234"
  }

  @get:Rule
  val signalStore = SignalStoreRule()

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  @get:Rule
  val signalDatabase = SignalDatabaseRule()

  private val context: Application = ApplicationProvider.getApplicationContext()

  private val aci = ACI.from(UUID.randomUUID())
  private val pni = PNI.from(UUID.randomUUID())
  private val aep = AccountEntropyPool.generate()
  private val aciIdentity = IdentityKeyPair.generate()
  private val pniIdentity = IdentityKeyPair.generate()
  private val aciSignedPreKey = PreKeyUtil.generateSignedPreKey(11, aciIdentity.privateKey)
  private val pniSignedPreKey = PreKeyUtil.generateSignedPreKey(12, pniIdentity.privateKey)
  private val aciLastResortKyberPreKey = PreKeyUtil.generateLastResortKyberPreKey(21, aciIdentity.privateKey)
  private val pniLastResortKyberPreKey = PreKeyUtil.generateLastResortKyberPreKey(22, pniIdentity.privateKey)

  private val blobData = mutableMapOf<Uri, ByteArray>()
  private var blobCounter = 0

  private lateinit var controller: AppRegistrationStorageController

  @Before
  fun setUp() {
    mockkStatic(RegistrationUtil::class)
    justRun { RegistrationUtil.maybeMarkRegistrationComplete() }

    mockkStatic("org.thoughtcrime.securesms.jobmanager.JobManagerExtensionsKt")
    coEvery { AppDependencies.jobManager.runJobBlocking(any(), any()) } returns null

    stubInMemoryBlobs()

    controller = AppRegistrationStorageController(context)

    // The real network restart tears down the suite-shared network module, whose CompositeDisposable can hold
    // leaked mocks from other tests. It sets no observable state, so a no-op is safe here.
    controller.restartNetwork = {}
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun `commit - new primary with pin and aep - applies all account fields`() = runBlocking<Unit> {
    seedInProgressData(
      RegistrationData(
        accountData = accountData(),
        accountEntropyPool = aep.value,
        pin = PIN,
        registrationLockEnabled = true
      )
    )

    controller.commitRegistrationData()

    assertThat(SignalStore.account.aci).isEqualTo(aci)
    assertThat(SignalStore.account.pni).isEqualTo(pni)
    assertThat(SignalStore.account.e164).isEqualTo(E164)
    assertThat(SignalStore.account.servicePassword).isEqualTo(SERVICE_PASSWORD)
    assertThat(SignalStore.account.registrationId).isEqualTo(12345)
    assertThat(SignalStore.account.pniRegistrationId).isEqualTo(54321)
    assertThat(SignalStore.account.isRegistered).isTrue()
    assertThat(SignalStore.account.isMultiDevice).isFalse()
    assertThat(SignalStore.account.aciIdentityKey.serialize()).isEqualTo(aciIdentity.serialize())
    assertThat(SignalStore.account.pniIdentityKey.serialize()).isEqualTo(pniIdentity.serialize())
    assertThat(SignalStore.account.accountEntropyPool.value).isEqualTo(aep.value)
    assertThat(SignalStore.account.restoredAccountEntropyPool).isTrue()

    assertThat(SignalStore.account.aciPreKeys.isSignedPreKeyRegistered).isTrue()
    assertThat(SignalStore.account.aciPreKeys.activeSignedPreKeyId).isEqualTo(11)
    assertThat(SignalStore.account.aciPreKeys.lastResortKyberPreKeyId).isEqualTo(21)
    assertThat(SignalStore.account.pniPreKeys.isSignedPreKeyRegistered).isTrue()
    assertThat(SignalStore.account.pniPreKeys.activeSignedPreKeyId).isEqualTo(12)
    assertThat(SignalStore.account.pniPreKeys.lastResortKyberPreKeyId).isEqualTo(22)

    assertThat(SignalStore.svr.pin).isEqualTo(PIN)
    assertThat(SignalStore.svr.isRegistrationLockEnabled).isTrue()
    assertThat(SignalStore.svr.hasOptedOut()).isFalse()

    val selfId = SignalDatabase.recipients.getByAci(aci).get()
    val selfRecord = SignalDatabase.recipients.getRecord(selfId)
    assertThat(selfRecord.profileSharing).isTrue()
    assertThat(selfRecord.registered).isEqualTo(RecipientTable.RegisteredState.REGISTERED)
    assertThat(selfRecord.e164).isEqualTo(E164)
    assertThat(selfRecord.pni).isEqualTo(pni)
    assertThat(selfRecord.profileKey).isNotNull()

    assertThat(TextSecurePreferences.hasPromptedPushRegistration(context)).isTrue()
    assertThat(TextSecurePreferences.isUnauthorizedReceived(context)).isFalse()

    assertThat(readInProgressData().accountDataCommitted).isTrue()

    verify { AppDependencies.jobManager.add(ofType<PreKeysSyncJob>()) }
    verify { AppDependencies.jobManager.add(ofType<DirectoryRefreshJob>()) }
    verify { AppDependencies.jobManager.add(ofType<RotateCertificateJob>()) }
    verify { RegistrationUtil.maybeMarkRegistrationComplete() }
  }

  @Test
  fun `commit - linked device - applies linked device fields`() = runBlocking<Unit> {
    val mediaRootBackupKey = Random.nextBytes(32)

    seedInProgressData(
      RegistrationData(
        accountData = accountData(
          linkedDeviceData = LinkedDeviceData(
            deviceId = 2,
            deviceName = "device-name",
            mediaRootBackupKey = mediaRootBackupKey.toByteString(),
            readReceipts = true
          )
        ),
        accountEntropyPool = aep.value
      )
    )

    controller.commitRegistrationData()

    assertThat(SignalStore.account.deviceId).isEqualTo(2)
    assertThat(SignalStore.account.deviceName).isEqualTo("device-name")
    assertThat(SignalStore.account.isMultiDevice).isTrue()
    assertThat(SignalStore.account.isRegistered).isTrue()
    assertThat(SignalStore.account.accountEntropyPool.value).isEqualTo(aep.value)
    assertThat(SignalStore.account.restoredAccountEntropyPool).isFalse()
    assertThat(SignalStore.account.restoredAccountEntropyPoolFromPrimary).isTrue()
    assertThat(SignalStore.backup.mediaRootBackupKey.value).isEqualTo(mediaRootBackupKey)
    assertThat(TextSecurePreferences.isReadReceiptsEnabled(context)).isTrue()

    assertThat(SignalStore.svr.pin).isNull()
    assertThat(SignalStore.svr.hasOptedOut()).isFalse()

    coVerify { AppDependencies.jobManager.runJobBlocking(ofType<RefreshOwnProfileJob>(), any()) }
    verify { AppDependencies.jobManager.add(ofType<RotateCertificateJob>()) }
    verify(exactly = 0) { AppDependencies.jobManager.add(ofType<DirectoryRefreshJob>()) }
  }

  @Test
  fun `commit - pin opted out - applies svr opt out`() = runBlocking<Unit> {
    seedInProgressData(
      RegistrationData(
        accountData = accountData(),
        pinOptedOut = true
      )
    )

    controller.commitRegistrationData()

    assertThat(SignalStore.account.isRegistered).isTrue()
    assertThat(SignalStore.svr.pin).isNull()
    assertThat(SignalStore.svr.hasOptedOut()).isTrue()
  }

  @Test
  fun `commit - called twice - only applies account data once`() = runBlocking<Unit> {
    seedInProgressData(
      RegistrationData(
        accountData = accountData(),
        accountEntropyPool = aep.value,
        pin = PIN
      )
    )

    controller.commitRegistrationData()
    controller.commitRegistrationData()

    assertThat(readInProgressData().accountDataCommitted).isTrue()
    assertThat(SignalStore.svr.pin).isEqualTo(PIN)

    verify(exactly = 1) { AppDependencies.jobManager.add(ofType<PreKeysSyncJob>()) }
    verify(exactly = 2) { RegistrationUtil.maybeMarkRegistrationComplete() }
  }

  @Test
  fun `commit - incomplete account data - applies nothing`() = runBlocking<Unit> {
    seedInProgressData(
      RegistrationData(
        accountData = accountData(servicePassword = "")
      )
    )

    controller.commitRegistrationData()

    assertThat(SignalStore.account.isRegistered).isFalse()
    assertThat(SignalStore.svr.pin).isNull()
    assertThat(SignalStore.svr.hasOptedOut()).isFalse()
    assertThat(readInProgressData().accountDataCommitted).isFalse()

    verify(exactly = 0) { AppDependencies.jobManager.add(ofType<PreKeysSyncJob>()) }
    verify { RegistrationUtil.maybeMarkRegistrationComplete() }
  }

  @Test
  fun `commit - master key for initial data restore - is applied last and survives svr updates`() = runBlocking<Unit> {
    val initialRestoreKey = MasterKey(Random.nextBytes(32))

    seedInProgressData(
      RegistrationData(
        accountData = accountData(),
        accountEntropyPool = aep.value,
        pin = PIN,
        masterKeyForInitialDataRestore = initialRestoreKey.serialize().toByteString()
      )
    )

    controller.commitRegistrationData()

    assertThat(SignalStore.svr.masterKeyForInitialDataRestore!!.serialize()).isEqualTo(initialRestoreKey.serialize())
  }

  @Test
  fun `readInProgressRegistrationData - returns persisted data`() = runBlocking<Unit> {
    val data = RegistrationData(pin = PIN, accountEntropyPool = aep.value)
    seedInProgressData(data)

    val read = controller.readInProgressRegistrationData()

    assertThat(read).isEqualTo(data)
  }

  @Test
  fun `readInProgressRegistrationData - no data - returns empty`() = runBlocking<Unit> {
    val read = controller.readInProgressRegistrationData()

    assertThat(read).isEqualTo(RegistrationData())
  }

  @Test
  fun `updateInProgressRegistrationData - applies updater, persists, and deletes previous blob`() = runBlocking<Unit> {
    seedInProgressData(RegistrationData(accountEntropyPool = aep.value))

    controller.updateInProgressRegistrationData { pin = PIN }

    val read = readInProgressData()
    assertThat(read.pin).isEqualTo(PIN)
    assertThat(read.accountEntropyPool).isEqualTo(aep.value)
    assertThat(read.lastUpdatedMillis).isGreaterThan(0L)
    assertThat(blobData.size).isEqualTo(1)
  }

  @Test
  fun `clearAllData - deletes blob and clears uri`() = runBlocking<Unit> {
    seedInProgressData(RegistrationData(pin = PIN))
    val legacyFile = File(context.cacheDir, "registration-in-progress.proto").apply { createNewFile() }

    controller.clearAllData()

    assertThat(SignalStore.registration.inProgressRegistrationDataBlobUri).isNull()
    assertThat(blobData.size).isEqualTo(0)
    assertThat(legacyFile.exists()).isFalse()
  }

  @Test
  fun `getPreExistingRegistrationData - not registered - returns null`() = runBlocking<Unit> {
    val data = controller.getPreExistingRegistrationData()

    assertThat(data).isNull()
  }

  @Test
  fun `getPreExistingRegistrationData - registered - returns account state`() = runBlocking<Unit> {
    SignalStore.account.setAci(aci)
    SignalStore.account.setPni(pni)
    SignalStore.account.setE164(E164)
    SignalStore.account.setServicePassword(SERVICE_PASSWORD)
    SignalStore.account.restoreAccountEntropyPool(aep)
    SignalStore.account.restoreAciIdentityKeyFromBackup(aciIdentity.publicKey.serialize(), aciIdentity.privateKey.serialize())
    SignalStore.account.restorePniIdentityKeyFromBackup(pniIdentity.publicKey.serialize(), pniIdentity.privateKey.serialize())
    SignalStore.account.setRegistered(true)
    SignalStore.svr.isRegistrationLockEnabled = true

    val data = controller.getPreExistingRegistrationData()!!

    assertThat(data.e164).isEqualTo(E164)
    assertThat(data.aci).isEqualTo(aci)
    assertThat(data.pni).isEqualTo(pni)
    assertThat(data.servicePassword).isEqualTo(SERVICE_PASSWORD)
    assertThat(data.aep.value).isEqualTo(aep.value)
    assertThat(data.registrationLockEnabled).isTrue()
    assertThat(data.aciIdentityKeyPair.serialize()).isEqualTo(aciIdentity.serialize())
    assertThat(data.pniIdentityKeyPair.serialize()).isEqualTo(pniIdentity.serialize())
  }

  /**
   * Backs the relaxed [AppDependencies.blobs] mock with an in-memory map so the controller's real
   * read/write/delete logic runs against inspectable storage.
   */
  private fun stubInMemoryBlobs() {
    val blobs = AppDependencies.blobs

    every { blobs.forData(any<ByteArray>()) } answers {
      val bytes = firstArg<ByteArray>()
      mockk<BlobProvider.MemoryBlobBuilder> {
        every { createForMultipleSessionsOnDisk(any()) } answers {
          val uri = Uri.parse("memoryblob://registration/${blobCounter++}")
          blobData[uri] = bytes
          uri
        }
      }
    }

    every { blobs.getStream(any(), any()) } answers {
      val uri = secondArg<Uri>()
      blobData[uri]?.let { ByteArrayInputStream(it) } ?: throw IOException("No blob for $uri")
    }

    every { blobs.delete(any(), any()) } answers {
      blobData.remove(secondArg<Uri>())
    }
  }

  private fun seedInProgressData(data: RegistrationData) {
    val uri = Uri.parse("memoryblob://registration/seed-${blobCounter++}")
    blobData[uri] = RegistrationData.ADAPTER.encode(data)
    SignalStore.registration.inProgressRegistrationDataBlobUri = uri.toString()
  }

  private fun readInProgressData(): RegistrationData = runBlocking { controller.readInProgressRegistrationData() }

  private fun accountData(servicePassword: String = SERVICE_PASSWORD, linkedDeviceData: LinkedDeviceData? = null): AccountData {
    return AccountData(
      aciIdentityKeyPair = aciIdentity.serialize().toByteString(),
      pniIdentityKeyPair = pniIdentity.serialize().toByteString(),
      aciSignedPreKey = aciSignedPreKey.serialize().toByteString(),
      pniSignedPreKey = pniSignedPreKey.serialize().toByteString(),
      aciLastResortKyberPreKey = aciLastResortKyberPreKey.serialize().toByteString(),
      pniLastResortKyberPreKey = pniLastResortKyberPreKey.serialize().toByteString(),
      aciRegistrationId = 12345,
      pniRegistrationId = 54321,
      aci = aci.toString(),
      pni = pni.toString(),
      e164 = E164,
      servicePassword = servicePassword,
      linkedDeviceData = linkedDeviceData
    )
  }
}
