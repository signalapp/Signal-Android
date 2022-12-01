package org.thoughtcrime.securesms.database

import androidx.core.content.contentValuesOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.util.CursorUtil
import org.signal.core.util.SqlUtil
import org.signal.core.util.select
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.state.SessionRecord
import org.thoughtcrime.securesms.conversation.colors.AvatarColor
import org.thoughtcrime.securesms.database.model.DistributionListId
import org.thoughtcrime.securesms.database.model.DistributionListRecord
import org.thoughtcrime.securesms.database.model.Mention
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.ReactionRecord
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.mms.IncomingMediaMessage
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.sms.IncomingEncryptedMessage
import org.thoughtcrime.securesms.sms.IncomingTextMessage
import org.whispersystems.signalservice.api.push.ACI
import org.whispersystems.signalservice.api.push.PNI
import org.whispersystems.signalservice.api.push.ServiceId
import java.util.Optional
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RecipientTableTest_getAndPossiblyMerge {

  @Before
  fun setup() {
    SignalStore.account().setE164(E164_SELF)
    SignalStore.account().setAci(ACI_SELF)
    SignalStore.account().setPni(PNI_SELF)
  }

  @Test
  fun allSimpleTests() {
    test("no match, e164-only") {
      process(E164_A, null, null)
      expect(E164_A, null, null)
    }

    test("no match, e164 and pni") {
      process(E164_A, PNI_A, null)
      expect(E164_A, PNI_A, null)
    }

    test("no match, aci-only") {
      process(null, null, ACI_A)
      expect(null, null, ACI_A)
    }

    test("no match, e164 and aci") {
      process(E164_A, null, ACI_A)
      expect(E164_A, null, ACI_A)
    }

    test("no match, no data", exception = java.lang.IllegalArgumentException::class.java) {
      process(null, null, null)
    }

    test("no match, all fields") {
      process(E164_A, PNI_A, ACI_A)
      expect(E164_A, PNI_A, ACI_A)
    }

    test("full match") {
      given(E164_A, PNI_A, ACI_A)
      process(E164_A, PNI_A, ACI_A)
      expect(E164_A, PNI_A, ACI_A)
    }

    test("e164 matches, all fields provided") {
      given(E164_A, null, null)
      process(E164_A, PNI_A, ACI_A)
      expect(E164_A, PNI_A, ACI_A)
    }

    test("e164 matches, e164 and aci provided") {
      given(E164_A, null, null)
      process(E164_A, null, ACI_A)
      expect(E164_A, null, ACI_A)
    }

    test("e164 matches, all provided, different aci") {
      given(E164_A, null, ACI_B)

      process(E164_A, PNI_A, ACI_A)

      expect(null, null, ACI_B)
      expect(E164_A, PNI_A, ACI_A)
    }

    test("e164 matches, e164 and aci provided, different aci") {
      given(E164_A, null, ACI_A)

      process(E164_A, null, ACI_B)

      expect(null, null, ACI_A)
      expect(E164_A, null, ACI_B)
    }

    test("e164 and pni matches, all provided, new aci") {
      given(E164_A, PNI_A, null)
      process(E164_A, PNI_A, ACI_A)
      expect(E164_A, PNI_A, ACI_A)
    }

    test("e164 and aci matches, all provided, new pni") {
      given(E164_A, null, ACI_A)
      process(E164_A, PNI_A, ACI_A)
      expect(E164_A, PNI_A, ACI_A)
    }

    test("pni matches, all provided, new e164 and aci") {
      given(null, PNI_A, null)
      process(E164_A, PNI_A, ACI_A)
      expect(E164_A, PNI_A, ACI_A)
    }

    test("pni and aci matches, all provided, new e164") {
      given(null, PNI_A, ACI_A)
      process(E164_A, PNI_A, ACI_A)
      expect(E164_A, PNI_A, ACI_A)
    }

    test("e164 and aci matches, e164 and aci provided, nothing new") {
      given(E164_A, null, ACI_A)
      process(E164_A, null, ACI_A)
      expect(E164_A, null, ACI_A)
    }

    test("aci matches, all provided, new e164 and pni") {
      given(null, null, ACI_A)
      process(E164_A, PNI_A, ACI_A)
      expect(E164_A, PNI_A, ACI_A)
    }

    test("aci matches, e164 and aci provided") {
      given(null, null, ACI_A)
      process(E164_A, null, ACI_A)
      expect(E164_A, null, ACI_A)
    }

    test("aci matches, local user, changeSelf=false") {
      given(E164_SELF, PNI_SELF, ACI_SELF)

      process(E164_SELF, null, ACI_B)

      expect(E164_SELF, PNI_SELF, ACI_SELF)
      expect(null, null, ACI_B)
    }

    test("e164 matches, e164 and pni provided, pni changes, no pni session") {
      given(E164_A, PNI_B, null)
      process(E164_A, PNI_A, null)
      expect(E164_A, PNI_A, null)
    }

    test("e164 and pni matches, all provided, no existing session") {
      given(E164_A, PNI_A, null)
      process(E164_A, PNI_A, ACI_A)
      expect(E164_A, PNI_A, ACI_A)
    }

    test("pni matches, all provided, no existing session") {
      given(null, PNI_A, null)
      process(E164_A, PNI_A, ACI_A)
      expect(E164_A, PNI_A, ACI_A)
    }

    // This test, I could go either way. We decide to change the E164 on the existing row rather than create a new one.
    // But it's an "unstable E164->PNI mapping" case, which we don't expect, so as long as there's a user-visible impact that should be fine.
    test("pni matches, no existing pni session, changes number") {
      given(E164_B, PNI_A, null)
      process(E164_A, PNI_A, ACI_A)
      expect(E164_A, PNI_A, ACI_A)

      expectChangeNumberEvent()
    }

    // This test, I could go either way. We decide to change the E164 on the existing row rather than create a new one.
    // But it's an "unstable E164->PNI mapping" case, which we don't expect, so as long as there's a user-visible impact that should be fine.
    test("pni and aci matches, change number") {
      given(E164_B, PNI_A, ACI_A)
      process(E164_A, PNI_A, ACI_A)
      expect(E164_A, PNI_A, ACI_A)

      expectChangeNumberEvent()
    }

    test("aci matches, all provided, change number") {
      given(E164_B, null, ACI_A)
      process(E164_A, PNI_A, ACI_A)
      expect(E164_A, PNI_A, ACI_A)

      expectChangeNumberEvent()
    }

    test("aci matches, e164 and aci provided, change number") {
      given(E164_B, null, ACI_A)
      process(E164_A, null, ACI_A)
      expect(E164_A, null, ACI_A)

      expectChangeNumberEvent()
    }

    test("steal, e164+pni & e164+pni, no aci provided, no sessions") {
      given(E164_A, PNI_B, null)
      given(E164_B, PNI_A, null)

      process(E164_A, PNI_A, null)

      expect(E164_A, PNI_A, null)
      expect(E164_B, null, null)
    }

    test("steal, e164+pni & aci, e164 record has separate e164") {
      given(E164_B, PNI_A, null)
      given(null, null, ACI_A)

      process(E164_A, PNI_A, ACI_A)

      expect(E164_B, null, null)
      expect(E164_A, PNI_A, ACI_A)
    }

    test("steal, e164+aci & e164+aci, change number") {
      given(E164_B, null, ACI_A)
      given(E164_A, null, ACI_B)

      process(E164_A, null, ACI_A)

      expect(E164_A, null, ACI_A)
      expect(null, null, ACI_B)

      expectChangeNumberEvent()
    }

    test("merge, e164 & pni & aci, all provided") {
      given(E164_A, null, null)
      given(null, PNI_A, null)
      given(null, null, ACI_A)

      process(E164_A, PNI_A, ACI_A)

      expectDeleted()
      expectDeleted()
      expect(E164_A, PNI_A, ACI_A)
    }

    test("merge, e164 & pni, no aci provided") {
      given(E164_A, null, null)
      given(null, PNI_A, null)

      process(E164_A, PNI_A, null)

      expect(E164_A, PNI_A, null)
      expectDeleted()
    }

    test("merge, e164 & pni, aci provided but no aci record") {
      given(E164_A, null, null)
      given(null, PNI_A, null)

      process(E164_A, PNI_A, ACI_A)

      expect(E164_A, PNI_A, ACI_A)
      expectDeleted()
    }

    test("merge, e164 & pni+e164, no aci provided") {
      given(E164_A, null, null)
      given(E164_B, PNI_A, null)

      process(E164_A, PNI_A, null)

      expect(E164_A, PNI_A, null)
      expect(E164_B, null, null)
    }

    test("merge, e164+pni & pni, no aci provided") {
      given(E164_A, PNI_B, null)
      given(null, PNI_A, null)

      process(E164_A, PNI_A, null)

      expect(E164_A, PNI_A, null)
      expectDeleted()
    }

    test("merge, e164+pni & aci") {
      given(E164_A, PNI_A, null)
      given(null, null, ACI_A)

      process(E164_A, PNI_A, ACI_A)

      expectDeleted()
      expect(E164_A, PNI_A, ACI_A)
    }

    test("merge, e164+pni & e164+pni+aci, change number") {
      given(E164_A, PNI_A, null)
      given(E164_B, PNI_B, ACI_A)

      process(E164_A, PNI_A, ACI_A)

      expectDeleted()
      expect(E164_A, PNI_A, ACI_A)

      expectChangeNumberEvent()
    }

    test("merge, e164+pni & e164+aci, change number") {
      given(E164_A, PNI_A, null)
      given(E164_B, null, ACI_A)

      process(E164_A, PNI_A, ACI_A)

      expectDeleted()
      expect(E164_A, PNI_A, ACI_A)

      expectChangeNumberEvent()
    }

    test("merge, e164 & aci") {
      given(E164_A, null, null)
      given(null, null, ACI_A)

      process(E164_A, null, ACI_A)

      expectDeleted()
      expect(E164_A, null, ACI_A)
    }

    test("merge, e164 & e164+aci, change number") {
      given(E164_A, null, null)
      given(E164_B, null, ACI_A)

      process(E164_A, null, ACI_A)

      expectDeleted()
      expect(E164_A, null, ACI_A)

      expectChangeNumberEvent()
    }

    test("local user, local e164 and aci provided, changeSelf=false, leave e164 alone") {
      given(E164_SELF, null, ACI_SELF)
      given(null, null, ACI_A)

      process(E164_SELF, null, ACI_A)

      expect(E164_SELF, null, ACI_SELF)
      expect(null, null, ACI_A)
    }

    test("local user, e164 and aci provided, changeSelf=false, leave e164 alone") {
      given(E164_SELF, null, ACI_SELF)
      process(E164_A, null, ACI_SELF)
      expect(E164_SELF, null, ACI_SELF)
    }

    test("local user, e164 and aci provided, changeSelf=true, change e164") {
      given(E164_SELF, null, ACI_SELF)
      process(E164_A, null, ACI_SELF, changeSelf = true)
      expect(E164_A, null, ACI_SELF)
    }
  }

  /**
   * Somewhat exhaustive test of verifying all the data that gets merged.
   */
  @Test
  fun getAndPossiblyMerge_merge_general() {
    // Setup
    val recipientIdAci: RecipientId = SignalDatabase.recipients.getOrInsertFromServiceId(ACI_A)
    val recipientIdE164: RecipientId = SignalDatabase.recipients.getOrInsertFromE164(E164_A)
    val recipientIdAciB: RecipientId = SignalDatabase.recipients.getOrInsertFromServiceId(ACI_B)

    val smsId1: Long = SignalDatabase.sms.insertMessageInbox(smsMessage(sender = recipientIdAci, time = 0, body = "0")).get().messageId
    val smsId2: Long = SignalDatabase.sms.insertMessageInbox(smsMessage(sender = recipientIdE164, time = 1, body = "1")).get().messageId
    val smsId3: Long = SignalDatabase.sms.insertMessageInbox(smsMessage(sender = recipientIdAci, time = 2, body = "2")).get().messageId

    val mmsId1: Long = SignalDatabase.mms.insertSecureDecryptedMessageInbox(mmsMessage(sender = recipientIdAci, time = 3, body = "3"), -1).get().messageId
    val mmsId2: Long = SignalDatabase.mms.insertSecureDecryptedMessageInbox(mmsMessage(sender = recipientIdE164, time = 4, body = "4"), -1).get().messageId
    val mmsId3: Long = SignalDatabase.mms.insertSecureDecryptedMessageInbox(mmsMessage(sender = recipientIdAci, time = 5, body = "5"), -1).get().messageId

    val threadIdAci: Long = SignalDatabase.threads.getThreadIdFor(recipientIdAci)!!
    val threadIdE164: Long = SignalDatabase.threads.getThreadIdFor(recipientIdE164)!!
    Assert.assertNotEquals(threadIdAci, threadIdE164)

    SignalDatabase.mentions.insert(threadIdAci, mmsId1, listOf(Mention(recipientIdE164, 0, 1)))
    SignalDatabase.mentions.insert(threadIdE164, mmsId2, listOf(Mention(recipientIdAci, 0, 1)))

    SignalDatabase.groupReceipts.insert(listOf(recipientIdAci, recipientIdE164), mmsId1, 0, 3)

    val identityKeyAci: IdentityKey = identityKey(1)
    val identityKeyE164: IdentityKey = identityKey(2)

    SignalDatabase.identities.saveIdentity(ACI_A.toString(), recipientIdAci, identityKeyAci, IdentityTable.VerifiedStatus.VERIFIED, false, 0, false)
    SignalDatabase.identities.saveIdentity(E164_A, recipientIdE164, identityKeyE164, IdentityTable.VerifiedStatus.VERIFIED, false, 0, false)

    SignalDatabase.sessions.store(ACI_SELF, SignalProtocolAddress(ACI_A.toString(), 1), SessionRecord())

    SignalDatabase.reactions.addReaction(MessageId(smsId1, false), ReactionRecord("a", recipientIdAci, 1, 1))
    SignalDatabase.reactions.addReaction(MessageId(mmsId1, true), ReactionRecord("b", recipientIdE164, 1, 1))

    val profile1: NotificationProfile = notificationProfile(name = "Test")
    val profile2: NotificationProfile = notificationProfile(name = "Test2")

    SignalDatabase.notificationProfiles.addAllowedRecipient(profileId = profile1.id, recipientId = recipientIdAci)
    SignalDatabase.notificationProfiles.addAllowedRecipient(profileId = profile1.id, recipientId = recipientIdE164)
    SignalDatabase.notificationProfiles.addAllowedRecipient(profileId = profile2.id, recipientId = recipientIdE164)
    SignalDatabase.notificationProfiles.addAllowedRecipient(profileId = profile2.id, recipientId = recipientIdAciB)

    val distributionListId: DistributionListId = SignalDatabase.distributionLists.createList("testlist", listOf(recipientIdE164, recipientIdAciB))!!

    // Merge
    val retrievedId: RecipientId = SignalDatabase.recipients.getAndPossiblyMerge(ACI_A, E164_A, true)
    val retrievedThreadId: Long = SignalDatabase.threads.getThreadIdFor(retrievedId)!!
    assertEquals(recipientIdAci, retrievedId)

    // Recipient validation
    val retrievedRecipient = Recipient.resolved(retrievedId)
    assertEquals(ACI_A, retrievedRecipient.requireServiceId())
    assertEquals(E164_A, retrievedRecipient.requireE164())

    val existingE164Recipient = Recipient.resolved(recipientIdE164)
    assertEquals(retrievedId, existingE164Recipient.id)

    // Thread validation
    assertEquals(threadIdAci, retrievedThreadId)
    Assert.assertNull(SignalDatabase.threads.getThreadIdFor(recipientIdE164))
    Assert.assertNull(SignalDatabase.threads.getThreadRecord(threadIdE164))

    // SMS validation
    val sms1: MessageRecord = SignalDatabase.sms.getMessageRecord(smsId1)!!
    val sms2: MessageRecord = SignalDatabase.sms.getMessageRecord(smsId2)!!
    val sms3: MessageRecord = SignalDatabase.sms.getMessageRecord(smsId3)!!

    assertEquals(retrievedId, sms1.recipient.id)
    assertEquals(retrievedId, sms2.recipient.id)
    assertEquals(retrievedId, sms3.recipient.id)

    assertEquals(retrievedThreadId, sms1.threadId)
    assertEquals(retrievedThreadId, sms2.threadId)
    assertEquals(retrievedThreadId, sms3.threadId)

    // MMS validation
    val mms1: MessageRecord = SignalDatabase.mms.getMessageRecord(mmsId1)!!
    val mms2: MessageRecord = SignalDatabase.mms.getMessageRecord(mmsId2)!!
    val mms3: MessageRecord = SignalDatabase.mms.getMessageRecord(mmsId3)!!

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
    val groupReceipts: List<GroupReceiptTable.GroupReceiptInfo> = SignalDatabase.groupReceipts.getGroupReceiptInfo(mmsId1)
    assertEquals(retrievedId, groupReceipts[0].recipientId)
    assertEquals(retrievedId, groupReceipts[1].recipientId)

    // Identity validation
    assertEquals(identityKeyAci, SignalDatabase.identities.getIdentityStoreRecord(ACI_A.toString())!!.identityKey)
    Assert.assertNull(SignalDatabase.identities.getIdentityStoreRecord(E164_A))

    // Session validation
    Assert.assertNotNull(SignalDatabase.sessions.load(ACI_SELF, SignalProtocolAddress(ACI_A.toString(), 1)))

    // Reaction validation
    val reactionsSms: List<ReactionRecord> = SignalDatabase.reactions.getReactions(MessageId(smsId1, false))
    val reactionsMms: List<ReactionRecord> = SignalDatabase.reactions.getReactions(MessageId(mmsId1, true))

    assertEquals(1, reactionsSms.size)
    assertEquals(ReactionRecord("a", recipientIdAci, 1, 1), reactionsSms[0])

    assertEquals(1, reactionsMms.size)
    assertEquals(ReactionRecord("b", recipientIdAci, 1, 1), reactionsMms[0])

    // Notification Profile validation
    val updatedProfile1: NotificationProfile = SignalDatabase.notificationProfiles.getProfile(profile1.id)!!
    val updatedProfile2: NotificationProfile = SignalDatabase.notificationProfiles.getProfile(profile2.id)!!

    MatcherAssert.assertThat("Notification Profile 1 should now only contain ACI $recipientIdAci", updatedProfile1.allowedMembers, Matchers.containsInAnyOrder(recipientIdAci))
    MatcherAssert.assertThat("Notification Profile 2 should now contain ACI A ($recipientIdAci) and ACI B ($recipientIdAciB)", updatedProfile2.allowedMembers, Matchers.containsInAnyOrder(recipientIdAci, recipientIdAciB))

    // Distribution List validation
    val updatedList: DistributionListRecord = SignalDatabase.distributionLists.getList(distributionListId)!!

    MatcherAssert.assertThat("Distribution list should have updated $recipientIdE164 to $recipientIdAci", updatedList.members, Matchers.containsInAnyOrder(recipientIdAci, recipientIdAciB))
  }

  private fun smsMessage(sender: RecipientId, time: Long = 0, body: String = "", groupId: Optional<GroupId> = Optional.empty()): IncomingTextMessage {
    return IncomingTextMessage(sender, 1, time, time, time, body, groupId, 0, true, null)
  }

  private fun mmsMessage(sender: RecipientId, time: Long = 0, body: String = "", groupId: Optional<GroupId> = Optional.empty()): IncomingMediaMessage {
    return IncomingMediaMessage(sender, groupId, body, time, time, time, emptyList(), 0, 0, false, false, true, Optional.empty(), false, false)
  }

  private fun identityKey(value: Byte): IdentityKey {
    val bytes = ByteArray(33)
    bytes[0] = 0x05
    bytes[1] = value
    return IdentityKey(bytes)
  }

  private fun notificationProfile(name: String): NotificationProfile {
    return (SignalDatabase.notificationProfiles.createProfile(name = name, emoji = "", color = AvatarColor.A210, System.currentTimeMillis()) as NotificationProfileDatabase.NotificationProfileChangeResult.Success).notificationProfile
  }

  private fun getMention(messageId: Long): MentionModel {
    SignalDatabase.rawDatabase.rawQuery("SELECT * FROM ${MentionTable.TABLE_NAME} WHERE ${MentionTable.MESSAGE_ID} = $messageId").use { cursor ->
      cursor.moveToFirst()
      return MentionModel(
        recipientId = RecipientId.from(CursorUtil.requireLong(cursor, MentionTable.RECIPIENT_ID)),
        threadId = CursorUtil.requireLong(cursor, MentionTable.THREAD_ID)
      )
    }
  }

  /** The normal mention model doesn't have a threadId, so we need to do it ourselves for the test */
  data class MentionModel(
    val recipientId: RecipientId,
    val threadId: Long
  )

  /**
   * Baby DSL for making tests readable.
   */
  private fun test(name: String, init: TestCase.() -> Unit): TestCase {
    // Weird issue with generics wouldn't let me make the exception an arg with default value -- had to do an actual overload
    return test(name, null as Class<Throwable>?, init)
  }

  /**
   * Baby DSL for making tests readable.
   */
  private fun <E> test(name: String, exception: Class<E>?, init: TestCase.() -> Unit): TestCase where E : Throwable {
    val test = TestCase()
    try {
      test.init()

      if (exception != null) {
        throw java.lang.AssertionError("Expected $exception, but none was thrown.")
      }

      if (!test.changeNumberExpected) {
        test.expectNoChangeNumberEvent()
      }
    } catch (e: Throwable) {
      if (e.javaClass != exception) {
        val error = java.lang.AssertionError("[$name] ${e.message}")
        error.stackTrace = e.stackTrace
        throw error
      }
    }

    return test
  }

  private inner class TestCase {
    private val generatedIds: LinkedHashSet<RecipientId> = LinkedHashSet()
    private var expectCount = 0
    private lateinit var outputRecipientId: RecipientId

    var changeNumberExpected = false

    init {
      // Need to delete these first to prevent foreign key crash
      SignalDatabase.rawDatabase.execSQL("DELETE FROM distribution_list")
      SignalDatabase.rawDatabase.execSQL("DELETE FROM distribution_list_member")

      SqlUtil.getAllTables(SignalDatabase.rawDatabase)
        .filterNot { it.contains("sqlite") || it.contains("fts") || it.startsWith("emoji_search_") } // If we delete these we'll corrupt the DB
        .sorted()
        .forEach { table ->
          SignalDatabase.rawDatabase.execSQL("DELETE FROM $table")
        }

      ApplicationDependencies.getRecipientCache().clear()
      RecipientId.clearCache()
    }

    fun given(
      e164: String?,
      pni: PNI?,
      aci: ACI?,
      createThread: Boolean = true,
      sms: List<String> = emptyList(),
      mms: List<String> = emptyList()
    ) {
      val id = insert(e164, pni, aci)
      generatedIds += id
      if (createThread) {
        // Create a thread and throw a dummy message in it so it doesn't get automatically deleted
        SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(id))
        SignalDatabase.sms.insertMessageInbox(IncomingEncryptedMessage(IncomingTextMessage(id, 1, 0, 0, 0, "", Optional.empty(), 0, false, ""), ""))
      }
    }

    fun process(e164: String?, pni: PNI?, aci: ACI?, changeSelf: Boolean = false) {
      outputRecipientId = SignalDatabase.recipients.getAndPossiblyMerge(serviceId = aci ?: pni, pni = pni, e164 = e164, pniVerified = false, changeSelf = changeSelf)
      generatedIds += outputRecipientId
    }

    fun expect(e164: String?, pni: PNI?, aci: ACI?) {
      expect(generatedIds.elementAt(expectCount++), e164, pni, aci)
    }

    fun expect(id: RecipientId, e164: String?, pni: PNI?, aci: ACI?) {
      val recipient = Recipient.resolved(id)
      val expected = RecipientTuple(
        e164 = e164,
        pni = pni,
        serviceId = aci ?: pni
      )
      val actual = RecipientTuple(
        e164 = recipient.e164.orElse(null),
        pni = recipient.pni.orElse(null),
        serviceId = recipient.serviceId.orElse(null)
      )

      assertEquals(expected, actual)
    }

    fun expectDeleted() {
      expectDeleted(generatedIds.elementAt(expectCount++))
    }

    fun expectDeleted(id: RecipientId) {
      SignalDatabase.rawDatabase
        .select("1")
        .from(RecipientTable.TABLE_NAME)
        .where("${RecipientTable.ID} = ?", id)
        .run()
        .use { !it.moveToFirst() }
    }

    fun expectChangeNumberEvent() {
      assertEquals(1, SignalDatabase.sms.getChangeNumberMessageCount(outputRecipientId))
      changeNumberExpected = true
    }

    fun expectNoChangeNumberEvent() {
      assertEquals(0, SignalDatabase.sms.getChangeNumberMessageCount(outputRecipientId))
      changeNumberExpected = false
    }

    private fun insert(e164: String?, pni: PNI?, aci: ACI?): RecipientId {
      val serviceIdString: String? = (aci ?: pni)?.toString()
      val pniString: String? = pni?.toString()

      val id: Long = SignalDatabase.rawDatabase.insert(
        RecipientTable.TABLE_NAME,
        null,
        contentValuesOf(
          RecipientTable.PHONE to e164,
          RecipientTable.SERVICE_ID to serviceIdString,
          RecipientTable.PNI_COLUMN to pniString,
          RecipientTable.REGISTERED to RecipientTable.RegisteredState.REGISTERED.id
        )
      )

      assertTrue("Failed to insert! E164: $e164, ServiceId: $serviceIdString, PNI: $pniString", id > 0)

      return RecipientId.from(id)
    }
  }

  data class RecipientTuple(
    val e164: String?,
    val pni: PNI?,
    val serviceId: ServiceId?
  ) {

    /**
     * The intent here is to give nice diffs with the name of the constants rather than the values.
     */
    override fun toString(): String {
      return "(${e164.e164String()}, ${pni.pniString()}, ${serviceId.serviceIdString()})"
    }

    private fun String?.e164String(): String {
      return this?.let {
        when (it) {
          E164_A -> "E164_A"
          E164_B -> "E164_B"
          else -> it
        }
      } ?: "null"
    }

    private fun PNI?.pniString(): String {
      return this?.let {
        when (it) {
          PNI_A -> "PNI_A"
          PNI_B -> "PNI_B"
          PNI_SELF -> "PNI_SELF"
          else -> it.toString()
        }
      } ?: "null"
    }

    private fun ServiceId?.serviceIdString(): String {
      return this?.let {
        when (it) {
          PNI_A -> "PNI_A"
          PNI_B -> "PNI_B"
          PNI_SELF -> "PNI_SELF"
          ACI_A -> "ACI_A"
          ACI_B -> "ACI_B"
          ACI_SELF -> "ACI_SELF"
          else -> it.toString()
        }
      } ?: "null"
    }
  }

  companion object {
    val ACI_A = ACI.from(UUID.fromString("aaaa0000-5a76-47fa-a98a-7e72c948a82e"))
    val ACI_B = ACI.from(UUID.fromString("bbbb0000-0b60-4a68-9cd9-ed2f8453f9ed"))
    val ACI_SELF = ACI.from(UUID.fromString("77770000-b477-4f35-a824-d92987a63641"))

    val PNI_A = PNI.from(UUID.fromString("aaaa1111-c960-4f6c-8385-671ad2ffb999"))
    val PNI_B = PNI.from(UUID.fromString("bbbb1111-cd55-40bf-adda-c35a85375533"))
    val PNI_SELF = PNI.from(UUID.fromString("77771111-b014-41fb-bf73-05cb2ec52910"))

    const val E164_A = "+12222222222"
    const val E164_B = "+13333333333"
    const val E164_SELF = "+10000000000"
  }
}
