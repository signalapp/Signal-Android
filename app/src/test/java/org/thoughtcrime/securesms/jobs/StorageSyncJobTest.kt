/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.ServiceId.ACI
import org.signal.core.util.Hex
import org.signal.core.util.Util
import org.signal.core.util.logging.Log
import org.signal.core.util.withinTransaction
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.StickerPackId
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.testutil.FakeStorageServiceRule
import org.thoughtcrime.securesms.testutil.RecipientTestRule
import org.thoughtcrime.securesms.testutil.SystemOutLogger
import org.whispersystems.signalservice.api.storage.SignalStorageRecord
import org.whispersystems.signalservice.api.storage.StorageId
import org.whispersystems.signalservice.internal.storage.protos.ContactRecord
import org.whispersystems.signalservice.internal.storage.protos.StickerPackRecord
import org.whispersystems.signalservice.internal.storage.protos.StorageRecord
import java.util.UUID

/**
 * Tests for [StorageSyncJob]. Remote storage is a real encrypt/decrypt round-trip against
 * [FakeStorageServiceRule], and local storage is a real in-memory database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class StorageSyncJobTest {

  companion object {
    /** The manifest version both sides sit at once setup's initial sync has run. */
    private const val BASE_MANIFEST_VERSION = 2L
  }

  private val recipients = RecipientTestRule()
  private val remoteStorage = FakeStorageServiceRule()

  @get:Rule
  val rules: RuleChain = RuleChain
    .outerRule(recipients)
    .around(remoteStorage)

  @Before
  fun setUp() {
    Log.initialize(SystemOutLogger())
    remoteStorage.stubDefaults(recipients.signalStore)

    // We need to run an initial sync so that every test starts with local and remote already agreeing at  [BASE_MANIFEST_VERSION].
    // Without that, the default "All chats" chat folder shows up as a local-only ID in every test and muddies the assertions.
    runInitialSync()
  }

  @Test
  fun `given nothing has changed, when I run, then I leave both sides alone`() {
    val result = runJob(StorageSyncJob.forLocalChange())

    assertTrue(result.isSuccess)
    assertEquals(0, remoteStorage.writeCount)
    assertEquals(BASE_MANIFEST_VERSION, remoteStorage.manifest!!.version)
    assertEquals(BASE_MANIFEST_VERSION, localManifestVersion())
  }

  @Test
  fun `given no access to storage service, when I run, then I touch nothing`() {
    every { recipients.signalStore.svr.hasPin() } returns false
    SignalDatabase.recipients.rotateStorageId(recipients.createRecipient("Local Contact"))

    val result = runJob(StorageSyncJob.forRemoteChange())

    assertTrue(result.isSuccess)
    assertEquals(0, remoteStorage.writeCount)
    assertEquals(BASE_MANIFEST_VERSION, remoteStorage.manifest!!.version)
  }

  @Test
  fun `given storage service is disabled, when I run, then I touch nothing`() {
    every { recipients.signalStore.internal.storageServiceDisabled } returns true
    SignalDatabase.recipients.rotateStorageId(recipients.createRecipient("Local Contact"))

    val result = runJob(StorageSyncJob.forRemoteChange())

    assertTrue(result.isSuccess)
    assertEquals(0, remoteStorage.writeCount)
    assertEquals(BASE_MANIFEST_VERSION, remoteStorage.manifest!!.version)
  }

  @Test
  fun `given a remote-only contact, when I run, then I insert it locally`() {
    val aci = ACI.from(UUID.randomUUID())
    remoteStorage.addRemoteRecords(listOf(contactRecord(aci, ProfileName.fromParts("Remote", "Contact"))))

    val result = runJob(StorageSyncJob.forRemoteChange())

    assertTrue(result.isSuccess)

    val inserted = SignalDatabase.recipients.getByAci(aci)
    assertTrue(inserted.isPresent)
    assertEquals(ProfileName.fromParts("Remote", "Contact"), Recipient.resolved(inserted.get()).profileName)
  }

  @Test
  fun `given a remote-only contact, when I run, then I write nothing back and save their manifest`() {
    remoteStorage.addRemoteRecords(listOf(contactRecord(ACI.from(UUID.randomUUID()), ProfileName.fromParts("Remote", "Contact"))))

    val result = runJob(StorageSyncJob.forRemoteChange())

    assertTrue(result.isSuccess)
    assertEquals(0, remoteStorage.writeCount)
    assertEquals(BASE_MANIFEST_VERSION + 1, remoteStorage.manifest!!.version)
    assertEquals(BASE_MANIFEST_VERSION + 1, localManifestVersion())
  }

  @Test
  fun `given a local-only contact, when I run, then I write it remotely`() {
    val local = recipients.createRecipient("Local Contact")
    SignalDatabase.recipients.rotateStorageId(local)

    val result = runJob(StorageSyncJob.forLocalChange())

    assertTrue(result.isSuccess)
    assertEquals(1, remoteStorage.writeCount)

    val contacts = remoteStorage.records.mapNotNull { it.proto.contact }
    assertEquals(1, contacts.size)
    assertEquals(Recipient.resolved(local).requireAci().toByteString(), contacts[0].aciBinary)
  }

  @Test
  fun `given a local-only contact, when I run, then I save the new manifest on both sides`() {
    SignalDatabase.recipients.rotateStorageId(recipients.createRecipient("Local Contact"))

    val result = runJob(StorageSyncJob.forLocalChange())

    assertTrue(result.isSuccess)
    assertEquals(BASE_MANIFEST_VERSION + 1, remoteStorage.manifest!!.version)
    assertEquals(BASE_MANIFEST_VERSION + 1, localManifestVersion())
  }

  @Test
  fun `given the remote write hits a conflict, when I run, then I retry without changing remote state`() {
    SignalDatabase.recipients.rotateStorageId(recipients.createRecipient("Local Contact"))
    remoteStorage.failNextWriteWithConflict = true

    val result = runJob(StorageSyncJob.forLocalChange())

    assertTrue(result.isRetry)
    assertEquals(BASE_MANIFEST_VERSION, remoteStorage.manifest!!.version)
  }

  @Test
  fun `given the remote write hits a network error, when I run, then I retry without changing remote state`() {
    SignalDatabase.recipients.rotateStorageId(recipients.createRecipient("Local Contact"))
    remoteStorage.failNextWriteWithNetworkError = true

    val result = runJob(StorageSyncJob.forLocalChange())

    assertTrue(result.isRetry)
    assertEquals(BASE_MANIFEST_VERSION, remoteStorage.manifest!!.version)
  }

  @Test
  fun `given an unknown ID for a sticker pack I do not have, when I run, then I clear it out`() {
    insertUnknownStorageId(StorageId.forStickerPack(Util.getSecretBytes(16)))

    val result = runJob(StorageSyncJob.forLocalChange())

    assertTrue(result.isSuccess)
    assertTrue(SignalDatabase.unknownStorageIds.allUnknownIds.isEmpty())
    assertEquals(0, remoteStorage.writeCount)
    assertEquals(BASE_MANIFEST_VERSION, remoteStorage.manifest!!.version)
  }

  @Test
  fun `given a newer remote manifest without my unknown sticker pack ID, when I run, then I clear it out`() {
    insertUnknownStorageId(StorageId.forStickerPack(Util.getSecretBytes(16)))
    remoteStorage.addRemoteRecords(listOf(contactRecord(ACI.from(UUID.randomUUID()), ProfileName.fromParts("Remote", "Contact"))))

    val result = runJob(StorageSyncJob.forRemoteChange())

    assertTrue(result.isSuccess)
    assertTrue(SignalDatabase.unknownStorageIds.allUnknownIds.isEmpty())
    assertEquals(BASE_MANIFEST_VERSION + 1, localManifestVersion())
  }

  @Test
  fun `given an unknown ID for a sticker pack that exists remotely, when I run, then I promote it to a real sticker pack`() {
    val packId = Util.getSecretBytes(16)
    val record = stickerPackRecord(packId)

    remoteStorage.addRemoteRecords(listOf(record))
    insertUnknownStorageId(record.id)

    val result = runJob(StorageSyncJob.forRemoteChange())

    assertTrue(result.isSuccess)
    assertTrue(SignalDatabase.unknownStorageIds.allUnknownIds.isEmpty())

    val pack = SignalDatabase.stickers.getPackForStorageSync(StickerPackId(Hex.toStringCondensed(packId)))
    assertNotNull(pack)
    assertArrayEquals(record.id.raw, pack!!.storageServiceId!!.raw)
  }

  /**
   * Gets us to a steady state: remote holds our account record at version 1, then a sync pushes up everything else
   * the fresh database came with (the default chat folder), leaving both sides at [BASE_MANIFEST_VERSION].
   */
  private fun runInitialSync() {
    SignalDatabase.recipients.updateStorageId(recipients.self, StorageSyncHelper.generateKey())
    Recipient.self().live().refresh()

    val accountRecord = StorageSyncHelper.buildAccountRecord(ApplicationProvider.getApplicationContext(), Recipient.self())
    remoteStorage.setRemoteState(listOf(accountRecord))

    val result = runJob(StorageSyncJob.forLocalChange())
    check(result.isSuccess) { "Initial sync failed!" }
    check(remoteStorage.manifest!!.version == BASE_MANIFEST_VERSION) { "Initial sync left remote at ${remoteStorage.manifest!!.version}!" }

    remoteStorage.resetCounters()
  }

  private fun localManifestVersion(): Long {
    return recipients.signalStore.storageService.manifest.version
  }

  private fun contactRecord(aci: ACI, profileName: ProfileName): SignalStorageRecord {
    return SignalStorageRecord(
      id = StorageId.forContact(Util.getSecretBytes(16)),
      proto = StorageRecord(
        contact = ContactRecord(
          aciBinary = aci.toByteString(),
          givenName = profileName.givenName,
          familyName = profileName.familyName,
          whitelisted = true
        )
      )
    )
  }

  private fun stickerPackRecord(packId: ByteArray): SignalStorageRecord {
    return SignalStorageRecord(
      id = StorageId.forStickerPack(Util.getSecretBytes(16)),
      proto = StorageRecord(
        stickerPack = StickerPackRecord(
          packId = packId.toByteString(),
          packKey = Util.getSecretBytes(32).toByteString(),
          position = 1
        )
      )
    )
  }

  /** Puts [id] in the unknown ID table, as if we'd synced it before we understood its type. */
  private fun insertUnknownStorageId(id: StorageId) {
    SignalDatabase.writableDatabase.withinTransaction {
      SignalDatabase.unknownStorageIds.insert(listOf(SignalStorageRecord.forUnknown(id)))
    }
  }

  private fun runJob(job: StorageSyncJob): Job.Result {
    job.setContext(ApplicationProvider.getApplicationContext())
    return job.run()
  }
}
