package org.thoughtcrime.securesms.database

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.storageservice.protos.groups.local.DecryptedGroup
import org.signal.storageservice.protos.groups.local.DecryptedMember
import org.signal.zkgroup.groups.GroupMasterKey
import org.thoughtcrime.securesms.database.model.Mention
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.ReactionRecord
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.mms.IncomingMediaMessage
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.sms.IncomingTextMessage
import org.thoughtcrime.securesms.util.CursorUtil
import org.whispersystems.libsignal.IdentityKey
import org.whispersystems.libsignal.SignalProtocolAddress
import org.whispersystems.libsignal.state.SessionRecord
import org.whispersystems.libsignal.util.guava.Optional
import org.whispersystems.signalservice.api.push.ACI
import org.whispersystems.signalservice.api.util.UuidUtil
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RecipientDatabaseTest_merges {

  private lateinit var recipientDatabase: RecipientDatabase
  private lateinit var identityDatabase: IdentityDatabase
  private lateinit var groupReceiptDatabase: GroupReceiptDatabase
  private lateinit var groupDatabase: GroupDatabase
  private lateinit var threadDatabase: ThreadDatabase
  private lateinit var smsDatabase: MessageDatabase
  private lateinit var mmsDatabase: MessageDatabase
  private lateinit var sessionDatabase: SessionDatabase
  private lateinit var mentionDatabase: MentionDatabase
  private lateinit var reactionDatabase: ReactionDatabase

  @Before
  fun setup() {
    recipientDatabase = SignalDatabase.recipients
    identityDatabase = SignalDatabase.identities
    groupReceiptDatabase = SignalDatabase.groupReceipts
    groupDatabase = SignalDatabase.groups
    threadDatabase = SignalDatabase.threads
    smsDatabase = SignalDatabase.sms
    mmsDatabase = SignalDatabase.mms
    sessionDatabase = SignalDatabase.sessions
    mentionDatabase = SignalDatabase.mentions
    reactionDatabase = SignalDatabase.reactions

    ensureDbEmpty()
  }

  /** High trust lets you merge two different users into one. You should prefer the ACI user. Not shown: merging threads, dropping e164 sessions, etc. */
  @Test
  fun getAndPossiblyMerge_general() {
    // Setup
    val recipientIdAci: RecipientId = recipientDatabase.getOrInsertFromAci(ACI_A)
    val recipientIdE164: RecipientId = recipientDatabase.getOrInsertFromE164(E164_A)

    val smsId1: Long = smsDatabase.insertMessageInbox(smsMessage(sender = recipientIdAci, time = 0, body = "0")).get().messageId
    val smsId2: Long = smsDatabase.insertMessageInbox(smsMessage(sender = recipientIdE164, time = 1, body = "1")).get().messageId
    val smsId3: Long = smsDatabase.insertMessageInbox(smsMessage(sender = recipientIdAci, time = 2, body = "2")).get().messageId

    val mmsId1: Long = mmsDatabase.insertSecureDecryptedMessageInbox(mmsMessage(sender = recipientIdAci, time = 3, body = "3"), -1).get().messageId
    val mmsId2: Long = mmsDatabase.insertSecureDecryptedMessageInbox(mmsMessage(sender = recipientIdE164, time = 4, body = "4"), -1).get().messageId
    val mmsId3: Long = mmsDatabase.insertSecureDecryptedMessageInbox(mmsMessage(sender = recipientIdAci, time = 5, body = "5"), -1).get().messageId

    val threadIdAci: Long = threadDatabase.getThreadIdFor(recipientIdAci)!!
    val threadIdE164: Long = threadDatabase.getThreadIdFor(recipientIdE164)!!
    assertNotEquals(threadIdAci, threadIdE164)

    mentionDatabase.insert(threadIdAci, mmsId1, listOf(Mention(recipientIdE164, 0, 1)))
    mentionDatabase.insert(threadIdE164, mmsId2, listOf(Mention(recipientIdAci, 0, 1)))

    groupReceiptDatabase.insert(listOf(recipientIdAci, recipientIdE164), mmsId1, 0, 3)

    val identityKeyAci: IdentityKey = identityKey(1)
    val identityKeyE164: IdentityKey = identityKey(2)

    identityDatabase.saveIdentity(ACI_A.toString(), recipientIdAci, identityKeyAci, IdentityDatabase.VerifiedStatus.VERIFIED, false, 0, false)
    identityDatabase.saveIdentity(E164_A, recipientIdE164, identityKeyE164, IdentityDatabase.VerifiedStatus.VERIFIED, false, 0, false)

    sessionDatabase.store(SignalProtocolAddress(ACI_A.toString(), 1), SessionRecord())

    reactionDatabase.addReaction(MessageId(smsId1, false), ReactionRecord("a", recipientIdAci, 1, 1))
    reactionDatabase.addReaction(MessageId(mmsId1, true), ReactionRecord("b", recipientIdE164, 1, 1))

    // Merge
    val retrievedId: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_A, E164_A, true)
    val retrievedThreadId: Long = threadDatabase.getThreadIdFor(retrievedId)!!
    assertEquals(recipientIdAci, retrievedId)

    // Recipient validation
    val retrievedRecipient = Recipient.resolved(retrievedId)
    assertEquals(ACI_A, retrievedRecipient.requireAci())
    assertEquals(E164_A, retrievedRecipient.requireE164())

    val existingE164Recipient = Recipient.resolved(recipientIdE164)
    assertEquals(retrievedId, existingE164Recipient.id)

    // Thread validation
    assertEquals(threadIdAci, retrievedThreadId)
    assertNull(threadDatabase.getThreadIdFor(recipientIdE164))
    assertNull(threadDatabase.getThreadRecord(threadIdE164))

    // SMS validation
    val sms1: MessageRecord = smsDatabase.getMessageRecord(smsId1)!!
    val sms2: MessageRecord = smsDatabase.getMessageRecord(smsId2)!!
    val sms3: MessageRecord = smsDatabase.getMessageRecord(smsId3)!!

    assertEquals(retrievedId, sms1.recipient.id)
    assertEquals(retrievedId, sms2.recipient.id)
    assertEquals(retrievedId, sms3.recipient.id)

    assertEquals(retrievedThreadId, sms1.threadId)
    assertEquals(retrievedThreadId, sms2.threadId)
    assertEquals(retrievedThreadId, sms3.threadId)

    // MMS validation
    val mms1: MessageRecord = mmsDatabase.getMessageRecord(mmsId1)!!
    val mms2: MessageRecord = mmsDatabase.getMessageRecord(mmsId2)!!
    val mms3: MessageRecord = mmsDatabase.getMessageRecord(mmsId3)!!

    assertEquals(retrievedId, mms1.recipient.id)
    assertEquals(retrievedId, mms2.recipient.id)
    assertEquals(retrievedId, mms3.recipient.id)

    assertEquals(retrievedThreadId, mms1.threadId)
    assertEquals(retrievedThreadId, mms2.threadId)
    assertEquals(retrievedThreadId, mms3.threadId)

    // Mention validation
    val mention1: MentionModel = getMention(mmsId1)
    assertEquals(retrievedId, mention1.recipientId)
    assertEquals(retrievedThreadId, mention1.threadId)

    val mention2: MentionModel = getMention(mmsId2)
    assertEquals(retrievedId, mention2.recipientId)
    assertEquals(retrievedThreadId, mention2.threadId)

    // Group receipt validation
    val groupReceipts: List<GroupReceiptDatabase.GroupReceiptInfo> = groupReceiptDatabase.getGroupReceiptInfo(mmsId1)
    assertEquals(retrievedId, groupReceipts[0].recipientId)
    assertEquals(retrievedId, groupReceipts[1].recipientId)

    // Identity validation
    assertEquals(identityKeyAci, identityDatabase.getIdentityStoreRecord(ACI_A.toString())!!.identityKey)
    assertNull(identityDatabase.getIdentityStoreRecord(E164_A))

    // Session validation
    assertNotNull(sessionDatabase.load(SignalProtocolAddress(ACI_A.toString(), 1)))

    // Reaction validation
    val reactionsSms: List<ReactionRecord> = reactionDatabase.getReactions(MessageId(smsId1, false))
    val reactionsMms: List<ReactionRecord> = reactionDatabase.getReactions(MessageId(mmsId1, true))

    assertEquals(1, reactionsSms.size)
    assertEquals(ReactionRecord("a", recipientIdAci, 1, 1), reactionsSms[0])

    assertEquals(1, reactionsMms.size)
    assertEquals(ReactionRecord("b", recipientIdAci, 1, 1), reactionsMms[0])
  }

  private val context: Application
    get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application

  private fun ensureDbEmpty() {
    SignalDatabase.rawDatabase.rawQuery("SELECT COUNT(*) FROM ${RecipientDatabase.TABLE_NAME}", null).use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals(0, cursor.getLong(0))
    }
  }

  private fun smsMessage(sender: RecipientId, time: Long = 0, body: String = "", groupId: Optional<GroupId> = Optional.absent()): IncomingTextMessage {
    return IncomingTextMessage(sender, 1, time, time, time, body, groupId, 0, true, null)
  }

  private fun mmsMessage(sender: RecipientId, time: Long = 0, body: String = "", groupId: Optional<GroupId> = Optional.absent()): IncomingMediaMessage {
    return IncomingMediaMessage(sender, groupId, body, time, time, time, emptyList(), 0, 0, false, false, true, Optional.absent())
  }

  private fun identityKey(value: Byte): IdentityKey {
    val bytes = ByteArray(33)
    bytes[0] = 0x05
    bytes[1] = value
    return IdentityKey(bytes)
  }

  private fun groupMasterKey(value: Byte): GroupMasterKey {
    val bytes = ByteArray(32)
    bytes[0] = value
    return GroupMasterKey(bytes)
  }

  private fun decryptedGroup(members: Collection<UUID>): DecryptedGroup {
    return DecryptedGroup.newBuilder()
      .addAllMembers(members.map { DecryptedMember.newBuilder().setUuid(UuidUtil.toByteString(it)).build() })
      .build()
  }

  private fun getMention(messageId: Long): MentionModel {
    SignalDatabase.rawDatabase.rawQuery("SELECT * FROM ${MentionDatabase.TABLE_NAME} WHERE ${MentionDatabase.MESSAGE_ID} = $messageId").use { cursor ->
      cursor.moveToFirst()
      return MentionModel(
        recipientId = RecipientId.from(CursorUtil.requireLong(cursor, MentionDatabase.RECIPIENT_ID)),
        threadId = CursorUtil.requireLong(cursor, MentionDatabase.THREAD_ID)
      )
    }
  }

  /** The normal mention model doesn't have a threadId, so we need to do it ourselves for the test */
  data class MentionModel(
    val recipientId: RecipientId,
    val threadId: Long
  )

  companion object {
    val ACI_A = ACI.from(UUID.fromString("3436efbe-5a76-47fa-a98a-7e72c948a82e"))
    val ACI_B = ACI.from(UUID.fromString("8de7f691-0b60-4a68-9cd9-ed2f8453f9ed"))

    val E164_A = "+12221234567"
    val E164_B = "+13331234567"
  }
}
