package org.thoughtcrime.securesms.storage

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.signal.core.models.ServiceId
import org.signal.core.models.ServiceId.ACI
import org.signal.core.models.ServiceId.PNI
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.jobs.RetrieveProfileJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testutil.EmptyLogger
import org.whispersystems.signalservice.api.storage.SignalContactRecord
import org.whispersystems.signalservice.api.storage.StorageId
import org.whispersystems.signalservice.internal.storage.protos.ContactRecord
import org.whispersystems.signalservice.internal.storage.protos.OptionalBool
import java.util.UUID

class ContactRecordProcessorTest {

  lateinit var recipientTable: RecipientTable

  @Before
  fun setup() {
    mockkObject(SignalStore)
    every { SignalStore.account.isPrimaryDevice } returns true
    every { SignalStore.account.e164 } returns "+11234567890"

    recipientTable = mockk(relaxed = true)
  }

  @After
  fun tearDown() {
    unmockkObject(SignalStore)
    unmockkObject(Recipient.Companion)
    unmockkObject(RetrieveProfileJob.Companion)
    unmockkObject(SignalDatabase.Companion)
  }

  @Test
  fun `isInvalid, normal, false`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable, mutableSetOf())

    val record = buildRecord(
      record = ContactRecord(
        aciBinary = ACI_B.toByteString(),
        pniBinary = PNI_B.toByteStringWithoutPrefix(),
        e164 = E164_B
      )
    )

    // WHEN
    val result = subject.isInvalid(record)

    // THEN
    assertFalse(result)
  }

  @Test
  fun `isInvalid, missing ACI and PNI, true`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable, mutableSetOf())

    val record = buildRecord(
      record = ContactRecord(
        e164 = E164_B
      )
    )

    // WHEN
    val result = subject.isInvalid(record)

    // THEN
    assertTrue(result)
  }

  @Test
  fun `isInvalid, unknown ACI and PNI, true`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable, mutableSetOf())

    val record = buildRecord(
      record = ContactRecord(
        aciBinary = ACI.UNKNOWN.toByteString(),
        pniBinary = PNI.UNKNOWN.toByteString(),
        e164 = E164_B
      )
    )

    // WHEN
    val result = subject.isInvalid(record)

    // THEN
    assertTrue(result)
  }

  @Test
  fun `isInvalid, e164 matches self, true`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable, mutableSetOf())

    val record = buildRecord(
      record = ContactRecord(
        aciBinary = ACI_B.toByteString(),
        e164 = E164_A
      )
    )

    // WHEN
    val result = subject.isInvalid(record)

    // THEN
    assertTrue(result)
  }

  @Test
  fun `isInvalid, aci matches self, true`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable, mutableSetOf())

    val record = buildRecord(
      record = ContactRecord(
        aciBinary = ACI_A.toByteString()
      )
    )

    // WHEN
    val result = subject.isInvalid(record)

    // THEN
    assertTrue(result)
  }

  @Test
  fun `isInvalid, pni matches self as pni, true`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable, mutableSetOf())

    val record = buildRecord(
      record = ContactRecord(
        aciBinary = ACI_B.toByteString(),
        pniBinary = PNI_A.toByteStringWithoutPrefix()
      )
    )

    // WHEN
    val result = subject.isInvalid(record)

    // THEN
    assertTrue(result)
  }

  @Test
  fun `isInvalid, valid E164, true`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable, mutableSetOf())

    val record = buildRecord(
      record = ContactRecord(
        aciBinary = ACI_B.toByteString(),
        e164 = E164_B
      )
    )

    // WHEN
    val result = subject.isInvalid(record)

    // THEN
    assertFalse(result)
  }

  @Test
  fun `isInvalid, invalid E164 (missing +), true`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable, mutableSetOf())

    val record = buildRecord(
      record = ContactRecord(
        aciBinary = ACI_B.toByteString(),
        e164 = "15551234567"
      )
    )

    // WHEN
    val result = subject.isInvalid(record)

    // THEN
    assertTrue(result)
  }

  @Test
  fun `isInvalid, invalid E164 (contains letters), true`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable, mutableSetOf())

    val record = buildRecord(
      record = ContactRecord(
        aciBinary = ACI_B.toByteString(),
        e164 = "+1555ABC4567"
      )
    )

    // WHEN
    val result = subject.isInvalid(record)

    // THEN
    assertTrue(result)
  }

  @Test
  fun `isInvalid, invalid E164 (no numbers), true`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable, mutableSetOf())

    val record = buildRecord(
      record = ContactRecord(
        aciBinary = ACI_B.toByteString(),
        e164 = "+"
      )
    )

    // WHEN
    val result = subject.isInvalid(record)

    // THEN
    assertTrue(result)
  }

  @Test
  fun `isInvalid, invalid E164 (too many numbers), true`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable, mutableSetOf())

    val record = buildRecord(
      record = ContactRecord(
        aciBinary = ACI_B.toByteString(),
        e164 = "+12345678901234567890"
      )
    )

    // WHEN
    val result = subject.isInvalid(record)

    // THEN
    assertTrue(result)
  }

  @Test
  fun `isInvalid, invalid E164 (starts with zero), true`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable, mutableSetOf())

    val record = buildRecord(
      record = ContactRecord(
        aciBinary = ACI_B.toByteString(),
        e164 = "+05551234567"
      )
    )

    // WHEN
    val result = subject.isInvalid(record)

    // THEN
    assertTrue(result)
  }

  @Test
  fun `merge, e164MatchesButPnisDont pnpEnabled, keepLocal`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable, mutableSetOf())

    val local = buildRecord(
      STORAGE_ID_A,
      record = ContactRecord(
        aciBinary = ACI_A.toByteString(),
        e164 = E164_A,
        pniBinary = PNI_A.toByteStringWithoutPrefix()
      )
    )

    val remote = buildRecord(
      STORAGE_ID_B,
      record = ContactRecord(
        aciBinary = ACI_A.toByteString(),
        e164 = E164_A,
        pniBinary = PNI_B.toByteStringWithoutPrefix()
      )
    )

    // WHEN
    val result = subject.merge(remote, local, TestKeyGenerator(STORAGE_ID_C))

    // THEN
    assertEquals(ServiceId.parseOrNull(local.proto.aci, local.proto.aciBinary), ServiceId.parseOrNull(result.proto.aci, result.proto.aciBinary))
    assertEquals(local.proto.e164, result.proto.e164)
    assertEquals(ServiceId.parseOrNull(local.proto.pni, local.proto.pniBinary), ServiceId.parseOrNull(result.proto.pni, result.proto.pniBinary))
  }

  @Test
  fun `merge, pnisMatchButE164sDont pnpEnabled, keepLocal`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable, mutableSetOf())

    val local = buildRecord(
      STORAGE_ID_A,
      record = ContactRecord(
        aciBinary = ACI_A.toByteString(),
        e164 = E164_A,
        pniBinary = PNI_A.toByteStringWithoutPrefix()
      )
    )

    val remote = buildRecord(
      STORAGE_ID_B,
      record = ContactRecord(
        aciBinary = ACI_A.toByteString(),
        e164 = E164_B,
        pniBinary = PNI_A.toByteStringWithoutPrefix()
      )
    )

    // WHEN
    val result = subject.merge(remote, local, TestKeyGenerator(STORAGE_ID_C))

    // THEN
    assertEquals(ServiceId.parseOrNull(local.proto.aci, local.proto.aciBinary), ServiceId.parseOrNull(result.proto.aci, result.proto.aciBinary))
    assertEquals(local.proto.e164, result.proto.e164)
    assertEquals(ServiceId.parseOrNull(local.proto.pni, local.proto.pniBinary), ServiceId.parseOrNull(result.proto.pni, result.proto.pniBinary))
  }

  @Test
  fun `merge, e164AndPniChange pnpEnabled, useRemote`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable, mutableSetOf())

    val local = buildRecord(
      STORAGE_ID_A,
      record = ContactRecord(
        aciBinary = ACI_A.toByteString(),
        e164 = E164_A,
        pniBinary = PNI_A.toByteStringWithoutPrefix()
      )
    )

    val remote = buildRecord(
      STORAGE_ID_B,
      record = ContactRecord(
        aciBinary = ACI_A.toByteString(),
        e164 = E164_B,
        pniBinary = PNI_B.toByteStringWithoutPrefix()
      )
    )

    // WHEN
    val result = subject.merge(remote, local, TestKeyGenerator(STORAGE_ID_C))

    // THEN
    assertEquals(ServiceId.parseOrNull(remote.proto.aci, remote.proto.aciBinary), ServiceId.parseOrNull(result.proto.aci, result.proto.aciBinary))
    assertEquals(remote.proto.e164, result.proto.e164)
    assertEquals(ServiceId.parseOrNull(remote.proto.pni, remote.proto.pniBinary), ServiceId.parseOrNull(result.proto.pni, result.proto.pniBinary))
  }

  @Test
  fun `merge, nickname change, useRemote`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable, mutableSetOf())

    val local = buildRecord(
      STORAGE_ID_A,
      record = ContactRecord(
        aciBinary = ACI_A.toByteString(),
        e164 = E164_A
      )
    )

    val remote = buildRecord(
      STORAGE_ID_B,
      record = ContactRecord(
        aciBinary = ACI_A.toByteString(),
        e164 = E164_A,
        nickname = ContactRecord.Name(given = "Ghost", family = "Spider"),
        note = "Spidey Friend"
      )
    )

    // WHEN
    val result = subject.merge(remote, local, TestKeyGenerator(STORAGE_ID_C))

    // THEN
    assertEquals("Ghost", result.proto.nickname?.given)
    assertEquals("Spider", result.proto.nickname?.family)
    assertEquals("Spidey Friend", result.proto.note)
  }

  @Test
  fun `merge, identityKeys conflict on primary, keepLocal`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable, mutableSetOf())

    val local = buildRecord(
      STORAGE_ID_A,
      record = ContactRecord(
        pniBinary = PNI_B.toByteStringWithoutPrefix(),
        e164 = E164_B,
        identityKey = IDENTITY_KEY_A
      )
    )

    val remote = buildRecord(
      STORAGE_ID_B,
      record = ContactRecord(
        pniBinary = PNI_B.toByteStringWithoutPrefix(),
        e164 = E164_B,
        identityKey = IDENTITY_KEY_B
      )
    )

    // WHEN
    val result = subject.merge(remote, local, TestKeyGenerator(STORAGE_ID_C))

    // THEN
    assertEquals(IDENTITY_KEY_A, result.proto.identityKey)
  }

  @Test
  fun `merge, identityKeys conflict on linked device, useRemote`() {
    // GIVEN
    every { SignalStore.account.isPrimaryDevice } returns false
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable, mutableSetOf())

    val local = buildRecord(
      STORAGE_ID_A,
      record = ContactRecord(
        pniBinary = PNI_B.toByteStringWithoutPrefix(),
        e164 = E164_B,
        identityKey = IDENTITY_KEY_A
      )
    )

    val remote = buildRecord(
      STORAGE_ID_B,
      record = ContactRecord(
        pniBinary = PNI_B.toByteStringWithoutPrefix(),
        e164 = E164_B,
        identityKey = IDENTITY_KEY_B
      )
    )

    // WHEN
    val result = subject.merge(remote, local, TestKeyGenerator(STORAGE_ID_C))

    // THEN
    assertEquals(IDENTITY_KEY_B, result.proto.identityKey)
  }

  @Test
  fun `merge, identityKeys conflict on linked device but has ACI, keepLocal`() {
    // GIVEN
    every { SignalStore.account.isPrimaryDevice } returns false
    mockkObject(Recipient.Companion)
    mockkObject(RetrieveProfileJob.Companion)
    mockkObject(SignalDatabase.Companion)
    every { Recipient.trustedPush(any(), any(), any()) } returns mockk(relaxed = true)
    every { RetrieveProfileJob.enqueueToResolveIdentityKeyConflict(any<RecipientId>()) } returns Unit
    every { SignalDatabase.runPostSuccessfulTransaction(any<Runnable>()) } answers { firstArg<Runnable>().run() }

    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable, mutableSetOf())

    val local = buildRecord(
      STORAGE_ID_A,
      record = ContactRecord(
        aciBinary = ACI_B.toByteString(),
        e164 = E164_B,
        identityKey = IDENTITY_KEY_A
      )
    )

    val remote = buildRecord(
      STORAGE_ID_B,
      record = ContactRecord(
        aciBinary = ACI_B.toByteString(),
        e164 = E164_B,
        identityKey = IDENTITY_KEY_B
      )
    )

    // WHEN
    val result = subject.merge(remote, local, TestKeyGenerator(STORAGE_ID_C))

    // THEN the profile fetch can repair this, so we keep our own key rather than deferring
    assertEquals(IDENTITY_KEY_A, result.proto.identityKey)
    verify { RetrieveProfileJob.enqueueToResolveIdentityKeyConflict(any<RecipientId>()) }
    assertEquals(setOf(STORAGE_ID_A), subject.identityConflictsPendingRepair)
  }

  @Test
  fun `merge, identityKeys conflict with ACI, defersPushingOurRecord`() {
    // GIVEN
    mockkObject(Recipient.Companion)
    mockkObject(RetrieveProfileJob.Companion)
    mockkObject(SignalDatabase.Companion)
    every { Recipient.trustedPush(any(), any(), any()) } returns mockk(relaxed = true)
    every { RetrieveProfileJob.enqueueToResolveIdentityKeyConflict(any<RecipientId>()) } returns Unit
    every { SignalDatabase.runPostSuccessfulTransaction(any<Runnable>()) } answers { firstArg<Runnable>().run() }

    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable, mutableSetOf())

    val local = buildRecord(
      STORAGE_ID_A,
      record = ContactRecord(
        aciBinary = ACI_B.toByteString(),
        e164 = E164_B,
        identityKey = IDENTITY_KEY_A
      )
    )

    val remote = buildRecord(
      STORAGE_ID_B,
      record = ContactRecord(
        aciBinary = ACI_B.toByteString(),
        e164 = E164_B,
        identityKey = IDENTITY_KEY_B
      )
    )

    // WHEN
    val result = subject.merge(remote, local, TestKeyGenerator(STORAGE_ID_C))

    // THEN we keep our key, but flag our id so the caller holds the write until the fetch resolves
    assertEquals(IDENTITY_KEY_A, result.proto.identityKey)
    assertEquals(STORAGE_ID_A, result.id)
    assertEquals(setOf(STORAGE_ID_A), subject.identityConflictsPendingRepair)
  }

  @Test
  fun `merge, identityKeys conflict with ACI, addsToCallersExistingSet`() {
    // GIVEN
    mockkObject(Recipient.Companion)
    mockkObject(RetrieveProfileJob.Companion)
    mockkObject(SignalDatabase.Companion)
    every { Recipient.trustedPush(any(), any(), any()) } returns mockk(relaxed = true)
    every { RetrieveProfileJob.enqueueToResolveIdentityKeyConflict(any<RecipientId>()) } returns Unit
    every { SignalDatabase.runPostSuccessfulTransaction(any<Runnable>()) } answers { firstArg<Runnable>().run() }

    val callerSet = mutableSetOf(STORAGE_ID_C)
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable, callerSet)

    val local = buildRecord(
      STORAGE_ID_A,
      record = ContactRecord(
        aciBinary = ACI_B.toByteString(),
        e164 = E164_B,
        identityKey = IDENTITY_KEY_A
      )
    )

    val remote = buildRecord(
      STORAGE_ID_B,
      record = ContactRecord(
        aciBinary = ACI_B.toByteString(),
        e164 = E164_B,
        identityKey = IDENTITY_KEY_B
      )
    )

    // WHEN
    subject.merge(remote, local, TestKeyGenerator(STORAGE_ID_C))

    // THEN the caller accumulates across processors, so a pre-existing entry has to survive
    assertEquals(setOf(STORAGE_ID_C, STORAGE_ID_A), callerSet)
  }

  @Test
  fun `merge, identityKeys conflict without ACI, doesNotDefer`() {
    // GIVEN
    val callerSet = mutableSetOf(STORAGE_ID_C)
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable, callerSet)

    val local = buildRecord(
      STORAGE_ID_A,
      record = ContactRecord(
        pniBinary = PNI_B.toByteStringWithoutPrefix(),
        e164 = E164_B,
        identityKey = IDENTITY_KEY_A
      )
    )

    val remote = buildRecord(
      STORAGE_ID_B,
      record = ContactRecord(
        pniBinary = PNI_B.toByteStringWithoutPrefix(),
        e164 = E164_B,
        identityKey = IDENTITY_KEY_B
      )
    )

    // WHEN
    subject.merge(remote, local, TestKeyGenerator(STORAGE_ID_C))

    // THEN no profile fetch is possible, so deferring would stall forever
    assertEquals(setOf(STORAGE_ID_C), callerSet)
  }

  @Test
  fun `merge, identityKeys match, doesNotDefer`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable, mutableSetOf())

    val local = buildRecord(
      STORAGE_ID_A,
      record = ContactRecord(
        aciBinary = ACI_B.toByteString(),
        e164 = E164_B,
        identityKey = IDENTITY_KEY_A
      )
    )

    val remote = buildRecord(
      STORAGE_ID_B,
      record = ContactRecord(
        aciBinary = ACI_B.toByteString(),
        e164 = E164_B,
        identityKey = IDENTITY_KEY_A
      )
    )

    // WHEN
    subject.merge(remote, local, TestKeyGenerator(STORAGE_ID_C))

    // THEN
    assertTrue(subject.identityConflictsPendingRepair.isEmpty())
  }

  @Test
  fun `merge, identityKeys match on linked device, keepLocal`() {
    // GIVEN
    every { SignalStore.account.isPrimaryDevice } returns false
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable, mutableSetOf())

    val local = buildRecord(
      STORAGE_ID_A,
      record = ContactRecord(
        aciBinary = ACI_B.toByteString(),
        e164 = E164_B,
        identityKey = IDENTITY_KEY_A
      )
    )

    val remote = buildRecord(
      STORAGE_ID_B,
      record = ContactRecord(
        aciBinary = ACI_B.toByteString(),
        e164 = E164_B,
        identityKey = IDENTITY_KEY_A
      )
    )

    // WHEN
    val result = subject.merge(remote, local, TestKeyGenerator(STORAGE_ID_C))

    // THEN
    assertEquals(IDENTITY_KEY_A, result.proto.identityKey)
  }

  @Test
  fun `merge, local identityKey missing on primary, useRemote`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable, mutableSetOf())

    val local = buildRecord(
      STORAGE_ID_A,
      record = ContactRecord(
        aciBinary = ACI_B.toByteString(),
        e164 = E164_B
      )
    )

    val remote = buildRecord(
      STORAGE_ID_B,
      record = ContactRecord(
        aciBinary = ACI_B.toByteString(),
        e164 = E164_B,
        identityKey = IDENTITY_KEY_B
      )
    )

    // WHEN
    val result = subject.merge(remote, local, TestKeyGenerator(STORAGE_ID_C))

    // THEN
    assertEquals(IDENTITY_KEY_B, result.proto.identityKey)
  }

  @Test
  fun `merge, remote identityKey missing on linked device, keepLocal`() {
    // GIVEN
    every { SignalStore.account.isPrimaryDevice } returns false
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable, mutableSetOf())

    val local = buildRecord(
      STORAGE_ID_A,
      record = ContactRecord(
        aciBinary = ACI_B.toByteString(),
        e164 = E164_B,
        identityKey = IDENTITY_KEY_A
      )
    )

    val remote = buildRecord(
      STORAGE_ID_B,
      record = ContactRecord(
        aciBinary = ACI_B.toByteString(),
        e164 = E164_B
      )
    )

    // WHEN
    val result = subject.merge(remote, local, TestKeyGenerator(STORAGE_ID_C))

    // THEN
    assertEquals(IDENTITY_KEY_A, result.proto.identityKey)
  }

  @Test
  fun `merge, pniSignatureVerified but no PNI, clearsFlag`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable, mutableSetOf())

    val local = buildRecord(
      STORAGE_ID_A,
      record = ContactRecord(
        aciBinary = ACI_B.toByteString(),
        e164 = E164_B
      )
    )

    val remote = buildRecord(
      STORAGE_ID_B,
      record = ContactRecord(
        aciBinary = ACI_B.toByteString(),
        e164 = E164_B,
        pniSignatureVerified = true
      )
    )

    // WHEN
    val result = subject.merge(remote, local, TestKeyGenerator(STORAGE_ID_C))

    // THEN a verified PNI signature is meaningless without a PNI, so it must not be propagated
    assertFalse(result.proto.pniSignatureVerified)
  }

  @Test
  fun `merge, pniSignatureVerified with PNI, keepsFlag`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable, mutableSetOf())

    val local = buildRecord(
      STORAGE_ID_A,
      record = ContactRecord(
        aciBinary = ACI_B.toByteString(),
        pniBinary = PNI_B.toByteStringWithoutPrefix(),
        e164 = E164_B
      )
    )

    val remote = buildRecord(
      STORAGE_ID_B,
      record = ContactRecord(
        aciBinary = ACI_B.toByteString(),
        pniBinary = PNI_B.toByteStringWithoutPrefix(),
        e164 = E164_B,
        pniSignatureVerified = true
      )
    )

    // WHEN
    val result = subject.merge(remote, local, TestKeyGenerator(STORAGE_ID_C))

    // THEN
    assertTrue(result.proto.pniSignatureVerified)
  }

  @Test
  fun `merge, notifyForCallsIfMuted set remotely and locally, useRemote`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable, mutableSetOf())

    val local = buildRecord(
      STORAGE_ID_A,
      record = ContactRecord(
        aciBinary = ACI_A.toByteString(),
        e164 = E164_A,
        notifyForCallsIfMuted = OptionalBool.DISABLED
      )
    )

    val remote = buildRecord(
      STORAGE_ID_B,
      record = ContactRecord(
        aciBinary = ACI_A.toByteString(),
        e164 = E164_A,
        notifyForCallsIfMuted = OptionalBool.ENABLED
      )
    )

    // WHEN
    val result = subject.merge(remote, local, TestKeyGenerator(STORAGE_ID_C))

    // THEN
    assertEquals(OptionalBool.ENABLED, result.proto.notifyForCallsIfMuted)
  }

  @Test
  fun `merge, notifyForCallsIfMuted unset remotely, keepLocal`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable, mutableSetOf())

    val local = buildRecord(
      STORAGE_ID_A,
      record = ContactRecord(
        aciBinary = ACI_A.toByteString(),
        e164 = E164_A,
        notifyForCallsIfMuted = OptionalBool.ENABLED
      )
    )

    val remote = buildRecord(
      STORAGE_ID_B,
      record = ContactRecord(
        aciBinary = ACI_A.toByteString(),
        e164 = E164_A,
        notifyForCallsIfMuted = OptionalBool.UNSET
      )
    )

    // WHEN
    val result = subject.merge(remote, local, TestKeyGenerator(STORAGE_ID_C))

    // THEN
    assertEquals(OptionalBool.ENABLED, result.proto.notifyForCallsIfMuted)
  }

  @Test
  fun `merge, showUnreadReminders set remotely and locally, useRemote`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable, mutableSetOf())

    val local = buildRecord(
      STORAGE_ID_A,
      record = ContactRecord(
        aciBinary = ACI_A.toByteString(),
        e164 = E164_A,
        showUnreadReminders = OptionalBool.ENABLED
      )
    )

    val remote = buildRecord(
      STORAGE_ID_B,
      record = ContactRecord(
        aciBinary = ACI_A.toByteString(),
        e164 = E164_A,
        showUnreadReminders = OptionalBool.DISABLED
      )
    )

    // WHEN
    val result = subject.merge(remote, local, TestKeyGenerator(STORAGE_ID_C))

    // THEN
    assertEquals(OptionalBool.DISABLED, result.proto.showUnreadReminders)
  }

  @Test
  fun `merge, showUnreadReminders unset remotely, keepLocal`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable, mutableSetOf())

    val local = buildRecord(
      STORAGE_ID_A,
      record = ContactRecord(
        aciBinary = ACI_A.toByteString(),
        e164 = E164_A,
        showUnreadReminders = OptionalBool.DISABLED
      )
    )

    val remote = buildRecord(
      STORAGE_ID_B,
      record = ContactRecord(
        aciBinary = ACI_A.toByteString(),
        e164 = E164_A,
        showUnreadReminders = OptionalBool.UNSET
      )
    )

    // WHEN
    val result = subject.merge(remote, local, TestKeyGenerator(STORAGE_ID_C))

    // THEN
    assertEquals(OptionalBool.DISABLED, result.proto.showUnreadReminders)
  }

  private fun buildRecord(id: StorageId = STORAGE_ID_A, record: ContactRecord): SignalContactRecord {
    return SignalContactRecord(id, record)
  }

  private class TestKeyGenerator(private val value: StorageId) : StorageKeyGenerator {
    override fun generate(): ByteArray {
      return value.raw
    }
  }

  companion object {
    val STORAGE_ID_A: StorageId = StorageId.forContact(byteArrayOf(1, 2, 3, 4))
    val STORAGE_ID_B: StorageId = StorageId.forContact(byteArrayOf(5, 6, 7, 8))
    val STORAGE_ID_C: StorageId = StorageId.forContact(byteArrayOf(9, 10, 11, 12))

    val ACI_A = ACI.from(UUID.fromString("3436efbe-5a76-47fa-a98a-7e72c948a82e"))
    val ACI_B = ACI.from(UUID.fromString("8de7f691-0b60-4a68-9cd9-ed2f8453f9ed"))

    val PNI_A = PNI.from(UUID.fromString("154b8d92-c960-4f6c-8385-671ad2ffb999"))
    val PNI_B = PNI.from(UUID.fromString("ba92b1fb-cd55-40bf-adda-c35a85375533"))

    const val E164_A = "+12221234567"
    const val E164_B = "+13331234567"

    val IDENTITY_KEY_A: ByteString = byteArrayOf(1, 1, 1, 1).toByteString()
    val IDENTITY_KEY_B: ByteString = byteArrayOf(2, 2, 2, 2).toByteString()

    @JvmStatic
    @BeforeClass
    fun setUpClass() {
      Log.initialize(EmptyLogger())
    }
  }
}
