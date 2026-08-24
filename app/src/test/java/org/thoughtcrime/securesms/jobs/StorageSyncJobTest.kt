/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import okio.ByteString.Companion.toByteString
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
import org.signal.core.util.update
import org.signal.core.util.withinTransaction
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.database.IssueReporter
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.IssuePriority
import org.thoughtcrime.securesms.database.model.StickerPackId
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobs.StorageSyncJobTest.Companion.BASE_MANIFEST_VERSION
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.testutil.FakeStorageServiceRule
import org.thoughtcrime.securesms.testutil.RecipientTestRule
import org.thoughtcrime.securesms.testutil.SystemOutLogger
import org.thoughtcrime.securesms.util.RemoteConfig
import org.whispersystems.signalservice.api.storage.SignalStorageRecord
import org.whispersystems.signalservice.api.storage.StorageId
import org.whispersystems.signalservice.internal.storage.protos.AccountRecord
import org.whispersystems.signalservice.internal.storage.protos.ContactRecord
import org.whispersystems.signalservice.internal.storage.protos.GroupV1Record
import org.whispersystems.signalservice.internal.storage.protos.StickerPackRecord
import org.whispersystems.signalservice.internal.storage.protos.StorageRecord
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

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

    mockkObject()
    every { RemoteConfig.defaultMaxBackoff } returns 1.minutes.inWholeMilliseconds

    // We need to run an initial sync so that every test starts with local and remote already agreeing at  [BASE_MANIFEST_VERSION].
    // Without that, the default "All chats" chat folder shows up as a local-only ID in every test and muddies the assertions.
    runInitialSync()
  }

  @After
  fun tearDown() {
    unmockkObject(RemoteConfig)
    unmockkObject(IssueReporter)
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

  @Test
  fun `given a remote GV1 record, when I run, then I keep it in the manifest`() {
    val record = groupV1Record()
    remoteStorage.addRemoteRecords(listOf(record))

    val result = runJob(StorageSyncJob.forRemoteChange())

    assertTrue(result.isSuccess)
    assertEquals(0, remoteStorage.writeCount)
    assertTrue(remoteStorage.manifest!!.storageIds.contains(record.id))
    assertTrue(SignalDatabase.unknownStorageIds.allUnknownIds.contains(record.id))
  }

  @Test
  fun `given a remote GV1 record, when I run, then I do not apply it locally`() {
    val groupId = GroupId.v1(Util.getSecretBytes(16))
    remoteStorage.addRemoteRecords(listOf(groupV1Record(groupId)))

    val result = runJob(StorageSyncJob.forRemoteChange())

    assertTrue(result.isSuccess)
    assertFalse(SignalDatabase.recipients.getByGroupId(groupId).isPresent)
  }

  @Test
  fun `given a GV1 recipient with a storage ID, when I run, then I ignore it entirely`() {
    val storageId = StorageId.forGroupV1(Util.getSecretBytes(16))
    val recipientId = SignalDatabase.recipients.getOrInsertFromGroupId(GroupId.v1(Util.getSecretBytes(16)))
    SignalDatabase.recipients.updateStorageId(recipientId, storageId.raw)

    val result = runJob(StorageSyncJob.forLocalChange())

    assertTrue(result.isSuccess)
    assertEquals(0, remoteStorage.writeCount)
    assertFalse(remoteStorage.manifest!!.storageIds.contains(storageId))
  }

  @Test
  fun `given a newer remote manifest without my GV1 ID, when I run, then I stop tracking it`() {
    val record = groupV1Record()
    remoteStorage.addRemoteRecords(listOf(record))
    check(runJob(StorageSyncJob.forRemoteChange()).isSuccess)
    check(SignalDatabase.unknownStorageIds.allUnknownIds.contains(record.id))

    val withoutGroupV1 = remoteStorage.records.filterNot { it.id == record.id }
    remoteStorage.setRemoteState(withoutGroupV1, version = remoteStorage.manifest!!.version + 1)

    val result = runJob(StorageSyncJob.forRemoteChange())

    assertTrue(result.isSuccess)
    assertFalse(SignalDatabase.unknownStorageIds.allUnknownIds.contains(record.id))
  }

  @Test
  fun `given a local-only unknown ID and a local contact, when I run, then I write only the contact and drop the unknown ID`() {
    val strandedId = StorageId.forGroupV1(Util.getSecretBytes(16))
    insertUnknownStorageId(strandedId)
    SignalDatabase.recipients.rotateStorageId(recipients.createRecipient("Local Contact"))

    val result = runJob(StorageSyncJob.forLocalChange())

    assertTrue(result.isSuccess)
    assertEquals(1, remoteStorage.writeCount)
    assertEquals(1, remoteStorage.records.count { it.proto.contact?.givenName == "Local" })
    assertFalse(remoteStorage.manifest!!.storageIds.contains(strandedId))
    assertFalse(SignalDatabase.unknownStorageIds.allUnknownIds.contains(strandedId))
  }

  @Test
  fun `given self was unregistered long ago, when I run, then I keep our storage id`() {
    markUnregisteredLongAgo(recipients.self)
    val selfStorageId = storageIdOf(recipients.self)!!

    val result = runJob(StorageSyncJob.forLocalChange())

    assertTrue(result.isSuccess)
    assertArrayEquals(selfStorageId, storageIdOf(recipients.self))
    assertTrue(remoteStorage.manifest!!.storageIds.contains(StorageId.forAccount(selfStorageId)))
  }

  @Test
  fun `given self was unregistered long ago, when I run again, then I do not rotate our storage id`() {
    markUnregisteredLongAgo(recipients.self)
    check(runJob(StorageSyncJob.forLocalChange()).isSuccess)

    val selfStorageId = storageIdOf(recipients.self)!!
    remoteStorage.resetCounters()

    val result = runJob(StorageSyncJob.forLocalChange())

    assertTrue(result.isSuccess)
    assertArrayEquals(selfStorageId, storageIdOf(recipients.self))
    assertEquals(0, remoteStorage.writeCount)
  }

  @Test
  fun `given self was unregistered long ago and our storage id is local-only, when I run, then I keep our storage id`() {
    markUnregisteredLongAgo(recipients.self)
    val selfStorageId = storageIdOf(recipients.self)!!

    remoteStorage.setRemoteState(remoteStorage.records.filter { it.proto.account == null }, version = remoteStorage.manifest!!.version + 1)

    val result = runJob(StorageSyncJob.forRemoteChange())

    assertTrue(result.isSuccess)
    assertArrayEquals(selfStorageId, storageIdOf(recipients.self))
    assertEquals(1, remoteStorage.records.count { it.proto.account != null })
    assertTrue(remoteStorage.manifest!!.storageIds.contains(StorageId.forAccount(selfStorageId)))
  }

  @Test
  fun `given self has no storage id, when I run, then I generate one and write our account record`() {
    clearStorageId(recipients.self)

    val result = runJob(StorageSyncJob.forLocalChange())

    assertTrue(result.isSuccess)

    val selfStorageId = storageIdOf(recipients.self)
    assertNotNull(selfStorageId)
    assertEquals(1, remoteStorage.records.count { it.proto.account != null })
    assertTrue(remoteStorage.manifest!!.storageIds.contains(StorageId.forAccount(selfStorageId!!)))
  }

  @Test
  fun `given a linked device with a tier, when I write our account record, then I include our tier`() {
    stubLinkedDevice()
    every { recipients.signalStore.backup.areBackupsEnabled } returns true
    every { recipients.signalStore.backup.backupTier } returns MessageBackupTier.PAID

    markSelfNeedsSync()

    val result = runJob(StorageSyncJob.forLocalChange())

    assertTrue(result.isSuccess)
    assertEquals(MessageBackupTier.PAID.toBackupLevel(), remoteAccountRecord().backupTier)
  }

  @Test
  fun `given a linked device and a remote record with a higher tier, when I run, then I apply it`() {
    stubLinkedDevice()

    remoteStorage.setRemoteState(listOf(accountRecordWithBackupTier(MessageBackupTier.PAID.toBackupLevel())), version = BASE_MANIFEST_VERSION + 1)

    val jobManager = AppDependencies.jobManager
    val result = runJob(StorageSyncJob.forRemoteChange())

    assertTrue(result.isSuccess)
    verify { recipients.signalStore.backup.backupTier = MessageBackupTier.PAID }
    verify(exactly = 0) { jobManager.add(ofType<BackupTierDowngradeCheckJob>()) }
  }

  @Test
  fun `given a linked device and a remote record without a tier, when I run, then I keep our tier and confirm with the service`() {
    stubLinkedDevice()
    every { recipients.signalStore.backup.areBackupsEnabled } returns true
    every { recipients.signalStore.backup.backupTier } returns MessageBackupTier.PAID

    remoteStorage.setRemoteState(listOf(accountRecordWithBackupTier(null)), version = BASE_MANIFEST_VERSION + 1)

    val jobManager = AppDependencies.jobManager
    val result = runJob(StorageSyncJob.forRemoteChange())

    assertTrue(result.isSuccess)
    verify(exactly = 0) { recipients.signalStore.backup.backupTier = null }
    verify { jobManager.add(ofType<BackupTierDowngradeCheckJob>()) }
  }

  @Test
  fun `given a linked device on paid and a remote record on free, when I run, then I keep our tier and confirm with the service`() {
    stubLinkedDevice()
    every { recipients.signalStore.backup.areBackupsEnabled } returns true
    every { recipients.signalStore.backup.backupTier } returns MessageBackupTier.PAID

    remoteStorage.setRemoteState(listOf(accountRecordWithBackupTier(MessageBackupTier.FREE.toBackupLevel())), version = BASE_MANIFEST_VERSION + 1)

    val jobManager = AppDependencies.jobManager
    val result = runJob(StorageSyncJob.forRemoteChange())

    assertTrue(result.isSuccess)
    verify(exactly = 0) { recipients.signalStore.backup.backupTier = MessageBackupTier.FREE }
    verify { jobManager.add(ofType<BackupTierDowngradeCheckJob>()) }
  }

  @Test
  fun `given a primary with a tier and a remote record without one, when I run, then I write our tier back and confirm nothing`() {
    every { recipients.signalStore.backup.areBackupsEnabled } returns true
    every { recipients.signalStore.backup.backupTier } returns MessageBackupTier.PAID

    remoteStorage.setRemoteState(listOf(accountRecordWithBackupTier(null)), version = BASE_MANIFEST_VERSION + 1)

    val jobManager = AppDependencies.jobManager
    val result = runJob(StorageSyncJob.forRemoteChange())

    assertTrue(result.isSuccess)
    assertEquals(MessageBackupTier.PAID.toBackupLevel(), remoteAccountRecord().backupTier)
    verify(exactly = 0) { jobManager.add(ofType<BackupTierDowngradeCheckJob>()) }
  }

  @Test
  fun `given a contact was unregistered long ago, when I run, then I remove their storage id`() {
    val contact = recipients.createRecipient("Local Contact")
    SignalDatabase.recipients.rotateStorageId(contact)
    check(runJob(StorageSyncJob.forLocalChange()).isSuccess)
    check(remoteStorage.records.count { it.proto.contact != null } == 1)

    markUnregisteredLongAgo(contact)
    remoteStorage.resetCounters()

    val result = runJob(StorageSyncJob.forLocalChange())

    assertTrue(result.isSuccess)
    assertNull(storageIdOf(contact))
    assertEquals(0, remoteStorage.records.count { it.proto.contact != null })
  }

  @Test
  fun `given a contact was unregistered recently, when I run, then I keep their storage id`() {
    val contact = recipients.createRecipient("Local Contact")
    SignalDatabase.recipients.rotateStorageId(contact)
    check(runJob(StorageSyncJob.forLocalChange()).isSuccess)
    check(remoteStorage.records.count { it.proto.contact != null } == 1)

    SignalDatabase.recipients.markUnregistered(contact)
    Recipient.live(contact).refresh()
    remoteStorage.resetCounters()

    val result = runJob(StorageSyncJob.forLocalChange())

    assertTrue(result.isSuccess)
    assertNotNull(storageIdOf(contact))
    assertEquals(1, remoteStorage.records.count { it.proto.contact != null })
  }

  @Test
  fun `given another device keeps undoing my write, when I run again, then I stop writing`() {
    stubIssueReporter()

    val throttledRun = loopWritesUntilThrottled()

    assertTrue(throttledRun.isSuccess)
    assertEquals(0, remoteStorage.writeCount)
    assertEquals(0, remoteStorage.records.count { it.proto.contact?.givenName == "Loop" })
  }

  @Test
  fun `given another device keeps undoing my write, when I throttle it, then I report it at high priority`() {
    val priority = stubIssueReporter()

    loopWritesUntilThrottled()

    verify { IssueReporter.report(any(), any(), any(), any(), any(), any()) }
    assertEquals(IssuePriority.HIGH, priority.captured)
  }

  @Test
  fun `given a local-only contact the other device leaves alone, when I run repeatedly, then I keep writing`() {
    stubIssueReporter()
    every { recipients.signalStore.account.isMultiDevice } returns true
    SignalDatabase.recipients.rotateStorageId(recipients.createRecipient("Local Contact"))

    check(runJob(StorageSyncJob.forLocalChange()).isSuccess)

    repeat(4) {
      SignalDatabase.recipients.rotateStorageId(recipients.createRecipient("Local Contact ${it + 2}"))
      remoteStorage.resetCounters()

      assertTrue(runJob(StorageSyncJob.forLocalChange()).isSuccess)
      assertEquals(1, remoteStorage.writeCount)
    }
  }

  @Test
  fun `given I just rotated my profile key, when a newer remote manifest still has the old one, then I keep mine`() {
    val rotated = rotateSelfProfileKey()

    bumpRemoteManifestWithoutTouchingAccountRecord()

    assertTrue(runJob(StorageSyncJob.forRemoteChange()).isSuccess)
    assertArrayEquals(rotated.serialize(), SignalDatabase.recipients.getRecord(recipients.self).profileKey)
  }

  @Test
  fun `given I just rotated my profile key, when a newer remote manifest still has the old one, then I publish mine`() {
    val rotated = rotateSelfProfileKey()

    bumpRemoteManifestWithoutTouchingAccountRecord()

    assertTrue(runJob(StorageSyncJob.forRemoteChange()).isSuccess)

    val accounts = remoteStorage.records.mapNotNull { it.proto.account }
    assertEquals(1, accounts.size)
    assertArrayEquals(rotated.serialize(), accounts[0].profileKey.toByteArray())
    assertNull(recipients.signalStore.account.notSyncedRotatedSelfProfileKey)
  }

  @Test
  fun `given no rotation of mine is pending, when remote has a different profile key, then I take theirs`() {
    val remoteKey = SignalDatabase.recipients.getRecord(recipients.self).profileKey

    // Registration always leaves us a locally generated key, so this must still defer to remote.
    SignalDatabase.recipients.setProfileKey(recipients.self, ProfileKey(Util.getSecretBytes(32)))
    Recipient.self().live().refresh()

    bumpRemoteManifestWithoutTouchingAccountRecord()

    assertTrue(runJob(StorageSyncJob.forRemoteChange()).isSuccess)
    assertArrayEquals(remoteKey, SignalDatabase.recipients.getRecord(recipients.self).profileKey)
  }

  @Test
  fun `given a recorded rotation that no longer matches my profile key, when remote has a different one, then I take theirs`() {
    recipients.signalStore.account.notSyncedRotatedSelfProfileKey = Util.getSecretBytes(32)

    val remoteKey = SignalDatabase.recipients.getRecord(recipients.self).profileKey
    SignalDatabase.recipients.setProfileKey(recipients.self, ProfileKey(Util.getSecretBytes(32)))
    Recipient.self().live().refresh()

    bumpRemoteManifestWithoutTouchingAccountRecord()

    assertTrue(runJob(StorageSyncJob.forRemoteChange()).isSuccess)
    assertArrayEquals(remoteKey, SignalDatabase.recipients.getRecord(recipients.self).profileKey)
  }

  @Test
  fun `given I just rotated my profile key, when their account record has an avatar path, then I do not fetch it`() {
    SignalDatabase.recipients.setProfileAvatar(recipients.self, "avatar-path-for-the-old-key")
    Recipient.self().live().refresh()
    check(runJob(StorageSyncJob.forLocalChange()).isSuccess)
    check(remoteStorage.records.mapNotNull { it.proto.account }.single().avatarUrlPath == "avatar-path-for-the-old-key")

    SignalDatabase.recipients.setProfileAvatar(recipients.self, null)
    rotateSelfProfileKey()

    bumpRemoteManifestWithoutTouchingAccountRecord()

    val jobManager = AppDependencies.jobManager

    assertTrue(runJob(StorageSyncJob.forRemoteChange()).isSuccess)
    verify(exactly = 0) { jobManager.add(ofType(RetrieveProfileAvatarJob::class)) }
  }

  /**
   * Writes the same contact, with the other device deleting it again after each, until a run gets throttled. Returns
   * that run, leaving [FakeStorageServiceRule.writeCount] counting only it. Driven by observation rather than a fixed
   * count because the record's payload settles after its first sync, so the number of passes isn't fixed.
   */
  private fun loopWritesUntilThrottled(): Job.Result {
    every { recipients.signalStore.account.isMultiDevice } returns true
    SignalDatabase.recipients.rotateStorageId(recipients.createRecipient("Loop Contact"))

    repeat(12) { pass ->
      remoteStorage.resetCounters()

      val result = runJob(StorageSyncJob.forRemoteChange())
      check(result.isSuccess) { "Loop write ${pass + 1} failed!" }

      if (remoteStorage.writeCount == 0) {
        return result
      }

      val withoutLoopContact = remoteStorage.records.filterNot { record -> record.proto.contact?.givenName == "Loop" }
      remoteStorage.setRemoteState(withoutLoopContact, version = remoteStorage.manifest!!.version + 1)
    }

    throw AssertionError("Writes were never throttled!")
  }

  private fun stubIssueReporter(): CapturingSlot<IssuePriority> {
    val priority = slot<IssuePriority>()

    mockkObject(IssueReporter)
    every { IssueReporter.report(any(), any(), any(), capture(priority), any(), any()) } returns Unit

    return priority
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

  private fun storageIdOf(id: RecipientId): ByteArray? {
    return SignalDatabase.recipients.getRecord(id).storageId
  }

  private fun clearStorageId(id: RecipientId) {
    SignalDatabase.writableDatabase
      .update(RecipientTable.TABLE_NAME)
      .values(RecipientTable.STORAGE_SERVICE_ID to null)
      .where("${RecipientTable.ID} = ?", id.toLong())
      .run()

    Recipient.live(id).refresh()
  }

  /** Marks [id] unregistered further back than [RemoteConfig.messageQueueTime], making its storageId eligible for cleanup. */
  private fun markUnregisteredLongAgo(id: RecipientId) {
    SignalDatabase.recipients.markUnregistered(id)

    SignalDatabase.writableDatabase
      .update(RecipientTable.TABLE_NAME)
      .values(RecipientTable.UNREGISTERED_TIMESTAMP to System.currentTimeMillis() - RemoteConfig.messageQueueTime - 1)
      .where("${RecipientTable.ID} = ?", id.toLong())
      .run()

    Recipient.live(id).refresh()
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

  private fun groupV1Record(groupId: GroupId.V1 = GroupId.v1(Util.getSecretBytes(16)), storageId: StorageId = StorageId.forGroupV1(Util.getSecretBytes(16))): SignalStorageRecord {
    return SignalStorageRecord(
      id = storageId,
      proto = StorageRecord(
        groupV1 = GroupV1Record(
          id = groupId.decodedId.toByteString(),
          blocked = true,
          whitelisted = true
        )
      )
    )
  }

  private fun runJob(job: StorageSyncJob): Job.Result {
    job.setContext(ApplicationProvider.getApplicationContext())
    return job.run()
  }

  /** Rotates our own profile key the way [RotateProfileKeyJob] does, and returns the new key. */
  private fun rotateSelfProfileKey(): ProfileKey {
    val rotated = ProfileKey(Util.getSecretBytes(32))

    recipients.signalStore.account.notSyncedRotatedSelfProfileKey = rotated.serialize()
    SignalDatabase.recipients.setProfileKey(recipients.self, rotated)
    Recipient.self().live().refresh()

    return rotated
  }

  /** Mimics another device writing an unrelated record, which bumps the manifest but leaves our account record stale. */
  private fun bumpRemoteManifestWithoutTouchingAccountRecord() {
    remoteStorage.addRemoteRecords(listOf(contactRecord(ACI.from(UUID.randomUUID()), ProfileName.fromParts("Remote", "Contact"))))
  }

  private fun markSelfNeedsSync() {
    SignalDatabase.recipients.markNeedsSync(recipients.self)
    Recipient.self().live().refresh()
  }

  private fun remoteAccountRecord(): AccountRecord {
    return remoteStorage.records.mapNotNull { it.proto.account }.single()
  }

  private fun stubLinkedDevice() {
    every { recipients.signalStore.account.isLinkedDevice } returns true
    every { recipients.signalStore.account.isPrimaryDevice } returns false
    every { recipients.signalStore.account.restoredAccountEntropyPoolFromPrimary } returns true
  }

  /** Our account record as another device would have rewritten it: same contents, [backupTier] swapped in, under a fresh storage id so that we see it as remote-only. */
  private fun accountRecordWithBackupTier(backupTier: Long?): SignalStorageRecord {
    val record = StorageSyncHelper.buildAccountRecord(ApplicationProvider.getApplicationContext(), Recipient.self())

    return record.copy(
      id = StorageId.forAccount(StorageSyncHelper.generateKey()),
      proto = record.proto.copy(account = record.proto.account!!.copy(backupTier = backupTier))
    )
  }
}
