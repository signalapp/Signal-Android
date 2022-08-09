package org.thoughtcrime.securesms.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.util.CursorUtil
import org.signal.core.util.ThreadUtil
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.state.SessionRecord
import org.signal.libsignal.zkgroup.groups.GroupMasterKey
import org.signal.storageservice.protos.groups.local.DecryptedGroup
import org.signal.storageservice.protos.groups.local.DecryptedMember
import org.thoughtcrime.securesms.conversation.colors.AvatarColor
import org.thoughtcrime.securesms.database.model.DistributionListId
import org.thoughtcrime.securesms.database.model.DistributionListRecord
import org.thoughtcrime.securesms.database.model.Mention
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.ReactionRecord
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.jobs.RecipientChangedNumberJob
import org.thoughtcrime.securesms.keyvalue.AccountValues
import org.thoughtcrime.securesms.keyvalue.KeyValueDataSet
import org.thoughtcrime.securesms.keyvalue.KeyValueStore
import org.thoughtcrime.securesms.keyvalue.MockKeyValuePersistentStorage
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.mms.IncomingMediaMessage
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.sms.IncomingTextMessage
import org.whispersystems.signalservice.api.push.ACI
import org.whispersystems.signalservice.api.push.PNI
import org.whispersystems.signalservice.api.util.UuidUtil
import java.util.Optional
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RecipientDatabaseTest_getAndPossiblyMergeLegacy {

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
  private lateinit var notificationProfileDatabase: NotificationProfileDatabase
  private lateinit var distributionListDatabase: DistributionListDatabase

  private val localAci = ACI.from(UUID.randomUUID())
  private val localPni = PNI.from(UUID.randomUUID())

  @Before
  fun setup() {
    recipientDatabase = SignalDatabase.recipients
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
    notificationProfileDatabase = SignalDatabase.notificationProfiles
    distributionListDatabase = SignalDatabase.distributionLists

    ensureDbEmpty()

    SignalStore.account().setAci(localAci)
    SignalStore.account().setPni(localPni)
  }

  // ==============================================================
  // If both the ACI and E164 map to no one
  // ==============================================================

  /** If all you have is an ACI, you can just store that, regardless of trust level. */
  @Test
  fun getAndPossiblyMerge_aciAndE164MapToNoOne_aciOnly() {
    val recipientId: RecipientId = recipientDatabase.getAndPossiblyMergeLegacy(ACI_A, null)

    val recipient = Recipient.resolved(recipientId)
    assertEquals(ACI_A, recipient.requireServiceId())
    assertFalse(recipient.hasE164())
  }

  /** If all you have is an E164, you can just store that, regardless of trust level. */
  @Test
  fun getAndPossiblyMerge_aciAndE164MapToNoOne_e164Only() {
    val recipientId: RecipientId = recipientDatabase.getAndPossiblyMergeLegacy(null, E164_A)

    val recipient = Recipient.resolved(recipientId)
    assertEquals(E164_A, recipient.requireE164())
    assertFalse(recipient.hasServiceId())
  }

  /** With high trust, you can associate an ACI-e164 pair. */
  @Test
  fun getAndPossiblyMerge_aciAndE164MapToNoOne_aciAndE164() {
    val recipientId: RecipientId = recipientDatabase.getAndPossiblyMergeLegacy(ACI_A, E164_A)

    val recipient = Recipient.resolved(recipientId)
    assertEquals(ACI_A, recipient.requireServiceId())
    assertEquals(E164_A, recipient.requireE164())
  }

  // ==============================================================
  // If the ACI maps to an existing user, but the E164 doesn't
  // ==============================================================

  /** You can associate an e164 with an existing ACI. */
  @Test
  fun getAndPossiblyMerge_aciMapsToExistingUserButE164DoesNot_aciAndE164() {
    val existingId: RecipientId = recipientDatabase.getOrInsertFromServiceId(ACI_A)

    val retrievedId: RecipientId = recipientDatabase.getAndPossiblyMergeLegacy(ACI_A, E164_A)
    assertEquals(existingId, retrievedId)

    val retrievedRecipient = Recipient.resolved(retrievedId)
    assertEquals(ACI_A, retrievedRecipient.requireServiceId())
    assertEquals(E164_A, retrievedRecipient.requireE164())
  }

  /** Basically the ‘change number’ case. Update the existing user. */
  @Test
  fun getAndPossiblyMerge_aciMapsToExistingUserButE164DoesNot_aciAndE164_2() {
    val existingId: RecipientId = recipientDatabase.getAndPossiblyMergeLegacy(ACI_A, E164_A)

    val retrievedId: RecipientId = recipientDatabase.getAndPossiblyMergeLegacy(ACI_A, E164_B)
    assertEquals(existingId, retrievedId)

    val retrievedRecipient = Recipient.resolved(retrievedId)
    assertEquals(ACI_A, retrievedRecipient.requireServiceId())
    assertEquals(E164_B, retrievedRecipient.requireE164())
  }

  // ==============================================================
  // If the E164 maps to an existing user, but the ACI doesn't
  // ==============================================================

  /** You can associate an e164 with an existing ACI. */
  @Test
  fun getAndPossiblyMerge_e164MapsToExistingUserButAciDoesNot_aciAndE164() {
    val existingId: RecipientId = recipientDatabase.getOrInsertFromE164(E164_A)

    val retrievedId: RecipientId = recipientDatabase.getAndPossiblyMergeLegacy(ACI_A, E164_A)
    assertEquals(existingId, retrievedId)

    val retrievedRecipient = Recipient.resolved(retrievedId)
    assertEquals(ACI_A, retrievedRecipient.requireServiceId())
    assertEquals(E164_A, retrievedRecipient.requireE164())
  }

  /** We never change the ACI of an existing row. New ACI = new person. Take the e164 from the current holder. */
  @Test
  fun getAndPossiblyMerge_e164MapsToExistingUserButAciDoesNot_aciAndE164_2() {
    val existingId: RecipientId = recipientDatabase.getAndPossiblyMergeLegacy(ACI_A, E164_A)
    recipientDatabase.setPni(existingId, PNI_A)

    val retrievedId: RecipientId = recipientDatabase.getAndPossiblyMergeLegacy(ACI_B, E164_A)
    recipientDatabase.setPni(retrievedId, PNI_A)
    assertNotEquals(existingId, retrievedId)

    val retrievedRecipient = Recipient.resolved(retrievedId)
    assertEquals(ACI_B, retrievedRecipient.requireServiceId())
    assertEquals(E164_A, retrievedRecipient.requireE164())

    val existingRecipient = Recipient.resolved(existingId)
    assertEquals(ACI_A, existingRecipient.requireServiceId())
    assertFalse(existingRecipient.hasE164())
  }

  /** We never want to remove the e164 of our own contact entry. Leave the e164 alone. */
  @Test
  fun getAndPossiblyMerge_e164MapsToExistingUserButAciDoesNot_e164BelongsToLocalUser() {
    val dataSet = KeyValueDataSet().apply {
      putString(AccountValues.KEY_E164, E164_A)
      putString(AccountValues.KEY_ACI, ACI_A.toString())
    }
    SignalStore.inject(KeyValueStore(MockKeyValuePersistentStorage.withDataSet(dataSet)))

    val existingId: RecipientId = recipientDatabase.getAndPossiblyMergeLegacy(ACI_A, E164_A)

    val retrievedId: RecipientId = recipientDatabase.getAndPossiblyMergeLegacy(ACI_B, E164_A)
    assertNotEquals(existingId, retrievedId)

    val retrievedRecipient = Recipient.resolved(retrievedId)
    assertEquals(ACI_B, retrievedRecipient.requireServiceId())
    assertFalse(retrievedRecipient.hasE164())

    val existingRecipient = Recipient.resolved(existingId)
    assertEquals(ACI_A, existingRecipient.requireServiceId())
    assertEquals(E164_A, existingRecipient.requireE164())
  }

  // ==============================================================
  // If both the ACI and E164 map to an existing user
  // ==============================================================

  /** If your ACI and e164 match, you’re good. */
  @Test
  fun getAndPossiblyMerge_bothAciAndE164MapToExistingUser_aciAndE164() {
    val existingId: RecipientId = recipientDatabase.getAndPossiblyMergeLegacy(ACI_A, E164_A)

    val retrievedId: RecipientId = recipientDatabase.getAndPossiblyMergeLegacy(ACI_A, E164_A)
    assertEquals(existingId, retrievedId)

    val retrievedRecipient = Recipient.resolved(retrievedId)
    assertEquals(ACI_A, retrievedRecipient.requireServiceId())
    assertEquals(E164_A, retrievedRecipient.requireE164())
  }

  /** Merge two different users into one. You should prefer the ACI user. Not shown: merging threads, dropping e164 sessions, etc. */
  @Test
  fun getAndPossiblyMerge_bothAciAndE164MapToExistingUser_aciAndE164_merge() {
    val changeNumberListener = ChangeNumberListener()
    changeNumberListener.enqueue()

    val existingAciId: RecipientId = recipientDatabase.getAndPossiblyMergeLegacy(ACI_A, null)
    val existingE164Id: RecipientId = recipientDatabase.getAndPossiblyMergeLegacy(null, E164_A)

    val retrievedId: RecipientId = recipientDatabase.getAndPossiblyMergeLegacy(ACI_A, E164_A)
    assertEquals(existingAciId, retrievedId)

    val retrievedRecipient = Recipient.resolved(retrievedId)
    assertEquals(ACI_A, retrievedRecipient.requireServiceId())
    assertEquals(E164_A, retrievedRecipient.requireE164())

    val existingE164Recipient = Recipient.resolved(existingE164Id)
    assertEquals(retrievedId, existingE164Recipient.id)

    changeNumberListener.waitForJobManager()
    assertFalse(changeNumberListener.numberChangeWasEnqueued)
  }

  /** Same as [getAndPossiblyMerge_bothAciAndE164MapToExistingUser_aciAndE164_merge], but with a number change. */
  @Test
  fun getAndPossiblyMerge_bothAciAndE164MapToExistingUser_aciAndE164_merge_changedNumber() {
    val changeNumberListener = ChangeNumberListener()
    changeNumberListener.enqueue()

    val existingAciId: RecipientId = recipientDatabase.getAndPossiblyMergeLegacy(ACI_A, E164_B)
    val existingE164Id: RecipientId = recipientDatabase.getAndPossiblyMergeLegacy(null, E164_A)

    val retrievedId: RecipientId = recipientDatabase.getAndPossiblyMergeLegacy(ACI_A, E164_A)
    assertEquals(existingAciId, retrievedId)

    val retrievedRecipient = Recipient.resolved(retrievedId)
    assertEquals(ACI_A, retrievedRecipient.requireServiceId())
    assertEquals(E164_A, retrievedRecipient.requireE164())

    val existingE164Recipient = Recipient.resolved(existingE164Id)
    assertEquals(retrievedId, existingE164Recipient.id)

    changeNumberListener.waitForJobManager()
    assert(changeNumberListener.numberChangeWasEnqueued)
  }

  /** No new rules here, just a more complex scenario to show how different rules interact. */
  @Test
  fun getAndPossiblyMerge_bothAciAndE164MapToExistingUser_aciAndE164_complex() {
    val changeNumberListener = ChangeNumberListener()
    changeNumberListener.enqueue()

    val existingId1: RecipientId = recipientDatabase.getAndPossiblyMergeLegacy(ACI_A, E164_B)
    val existingId2: RecipientId = recipientDatabase.getAndPossiblyMergeLegacy(ACI_B, E164_A)

    val retrievedId: RecipientId = recipientDatabase.getAndPossiblyMergeLegacy(ACI_A, E164_A)
    assertEquals(existingId1, retrievedId)

    val retrievedRecipient = Recipient.resolved(retrievedId)
    assertEquals(ACI_A, retrievedRecipient.requireServiceId())
    assertEquals(E164_A, retrievedRecipient.requireE164())

    val existingRecipient2 = Recipient.resolved(existingId2)
    assertEquals(ACI_B, existingRecipient2.requireServiceId())
    assertFalse(existingRecipient2.hasE164())

    changeNumberListener.waitForJobManager()
    assert(changeNumberListener.numberChangeWasEnqueued)
  }

  /**
   * Another case that results in a merge. Nothing strictly new here, but this case is called out because it’s a merge but *also* an E164 change,
   * which clients may need to know for UX purposes.
   */
  @Test
  fun getAndPossiblyMerge_bothAciAndE164MapToExistingUser_aciAndE164_mergeAndPhoneNumberChange() {
    val existingId1: RecipientId = recipientDatabase.getAndPossiblyMergeLegacy(ACI_A, E164_B)
    val existingId2: RecipientId = recipientDatabase.getAndPossiblyMergeLegacy(null, E164_A)

    val retrievedId: RecipientId = recipientDatabase.getAndPossiblyMergeLegacy(ACI_A, E164_A)
    assertEquals(existingId1, retrievedId)

    val retrievedRecipient = Recipient.resolved(retrievedId)
    assertEquals(ACI_A, retrievedRecipient.requireServiceId())
    assertEquals(E164_A, retrievedRecipient.requireE164())

    assertFalse(recipientDatabase.getByE164(E164_B).isPresent)

    val recipientWithId2 = Recipient.resolved(existingId2)
    assertEquals(retrievedId, recipientWithId2.id)
  }

  /** We never want to remove the e164 of our own contact entry. Leave the e164 alone. */
  @Test
  fun getAndPossiblyMerge_bothAciAndE164MapToExistingUser_e164BelongsToLocalUser() {
    val dataSet = KeyValueDataSet().apply {
      putString(AccountValues.KEY_E164, E164_A)
      putString(AccountValues.KEY_ACI, ACI_B.toString())
    }
    SignalStore.inject(KeyValueStore(MockKeyValuePersistentStorage.withDataSet(dataSet)))

    val existingId1: RecipientId = recipientDatabase.getAndPossiblyMergeLegacy(ACI_B, E164_A)
    val existingId2: RecipientId = recipientDatabase.getAndPossiblyMergeLegacy(ACI_A, null)

    val retrievedId: RecipientId = recipientDatabase.getAndPossiblyMergeLegacy(ACI_A, E164_A)
    assertEquals(existingId2, retrievedId)

    val retrievedRecipient = Recipient.resolved(retrievedId)
    assertEquals(ACI_A, retrievedRecipient.requireServiceId())
    assertFalse(retrievedRecipient.hasE164())

    val recipientWithId1 = Recipient.resolved(existingId1)
    assertEquals(ACI_B, recipientWithId1.requireServiceId())
    assertEquals(E164_A, recipientWithId1.requireE164())
  }

  /** This is a case where normally we'd update the E164 of a user, but here the changeSelf flag is disabled, so we shouldn't. */
  @Test
  fun getAndPossiblyMerge_aciMapsToExistingUserButE164DoesNot_aciBelongsToLocalUser_changeSelfFalse() {
    val dataSet = KeyValueDataSet().apply {
      putString(AccountValues.KEY_E164, E164_A)
      putString(AccountValues.KEY_ACI, ACI_A.toString())
    }
    SignalStore.inject(KeyValueStore(MockKeyValuePersistentStorage.withDataSet(dataSet)))

    val existingId: RecipientId = recipientDatabase.getAndPossiblyMergeLegacy(ACI_A, E164_A)

    val retrievedId: RecipientId = recipientDatabase.getAndPossiblyMergeLegacy(ACI_A, E164_B, changeSelf = false)
    assertEquals(existingId, retrievedId)

    val retrievedRecipient = Recipient.resolved(retrievedId)
    assertEquals(ACI_A, retrievedRecipient.requireServiceId())
    assertEquals(E164_A, retrievedRecipient.requireE164())
  }

  /** This is a case where we're changing our own number, and it's allowed because changeSelf = true. */
  @Test
  fun getAndPossiblyMerge_aciMapsToExistingUserButE164DoesNot_aciBelongsToLocalUser_changeSelfTrue() {
    val dataSet = KeyValueDataSet().apply {
      putString(AccountValues.KEY_E164, E164_A)
      putString(AccountValues.KEY_ACI, ACI_A.toString())
    }
    SignalStore.inject(KeyValueStore(MockKeyValuePersistentStorage.withDataSet(dataSet)))

    val existingId: RecipientId = recipientDatabase.getAndPossiblyMergeLegacy(ACI_A, E164_A)

    val retrievedId: RecipientId = recipientDatabase.getAndPossiblyMergeLegacy(ACI_A, E164_B, changeSelf = true)
    assertEquals(existingId, retrievedId)

    val retrievedRecipient = Recipient.resolved(retrievedId)
    assertEquals(ACI_A, retrievedRecipient.requireServiceId())
    assertEquals(E164_B, retrievedRecipient.requireE164())
  }

  /** Verifying a case where a change number job is expected to be enqueued. */
  @Test
  fun getAndPossiblyMerge_aciMapsToExistingUserButE164DoesNot_changedNumber() {
    val changeNumberListener = ChangeNumberListener()
    changeNumberListener.enqueue()

    val existingId: RecipientId = recipientDatabase.getAndPossiblyMergeLegacy(ACI_A, E164_A)

    val retrievedId: RecipientId = recipientDatabase.getAndPossiblyMergeLegacy(ACI_A, E164_B)
    assertEquals(existingId, retrievedId)

    val retrievedRecipient = Recipient.resolved(retrievedId)
    assertEquals(ACI_A, retrievedRecipient.requireServiceId())
    assertEquals(E164_B, retrievedRecipient.requireE164())

    changeNumberListener.waitForJobManager()
    assert(changeNumberListener.numberChangeWasEnqueued)
  }

  /** High trust lets you merge two different users into one. You should prefer the ACI user. Not shown: merging threads, dropping e164 sessions, etc. */
  @Test
  fun getAndPossiblyMerge_merge_general() {
    // Setup
    val recipientIdAci: RecipientId = recipientDatabase.getOrInsertFromServiceId(ACI_A)
    val recipientIdE164: RecipientId = recipientDatabase.getOrInsertFromE164(E164_A)
    val recipientIdAciB: RecipientId = recipientDatabase.getOrInsertFromServiceId(ACI_B)

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

    sessionDatabase.store(localAci, SignalProtocolAddress(ACI_A.toString(), 1), SessionRecord())

    reactionDatabase.addReaction(MessageId(smsId1, false), ReactionRecord("a", recipientIdAci, 1, 1))
    reactionDatabase.addReaction(MessageId(mmsId1, true), ReactionRecord("b", recipientIdE164, 1, 1))

    val profile1: NotificationProfile = notificationProfile(name = "Test")
    val profile2: NotificationProfile = notificationProfile(name = "Test2")

    notificationProfileDatabase.addAllowedRecipient(profileId = profile1.id, recipientId = recipientIdAci)
    notificationProfileDatabase.addAllowedRecipient(profileId = profile1.id, recipientId = recipientIdE164)
    notificationProfileDatabase.addAllowedRecipient(profileId = profile2.id, recipientId = recipientIdE164)
    notificationProfileDatabase.addAllowedRecipient(profileId = profile2.id, recipientId = recipientIdAciB)

    val distributionListId: DistributionListId = distributionListDatabase.createList("testlist", listOf(recipientIdE164, recipientIdAciB))!!

    // Merge
    val retrievedId: RecipientId = recipientDatabase.getAndPossiblyMergeLegacy(ACI_A, E164_A, true)
    val retrievedThreadId: Long = threadDatabase.getThreadIdFor(retrievedId)!!
    assertEquals(recipientIdAci, retrievedId)

    // Recipient validation
    val retrievedRecipient = Recipient.resolved(retrievedId)
    assertEquals(ACI_A, retrievedRecipient.requireServiceId())
    assertEquals(E164_A, retrievedRecipient.requireE164())

    val existingE164Recipient = Recipient.resolved(recipientIdE164)
    assertEquals(retrievedId, existingE164Recipient.id)

    // Thread validation
    assertEquals(threadIdAci, retrievedThreadId)
    Assert.assertNull(threadDatabase.getThreadIdFor(recipientIdE164))
    Assert.assertNull(threadDatabase.getThreadRecord(threadIdE164))

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
    Assert.assertNull(identityDatabase.getIdentityStoreRecord(E164_A))

    // Session validation
    Assert.assertNotNull(sessionDatabase.load(localAci, SignalProtocolAddress(ACI_A.toString(), 1)))

    // Reaction validation
    val reactionsSms: List<ReactionRecord> = reactionDatabase.getReactions(MessageId(smsId1, false))
    val reactionsMms: List<ReactionRecord> = reactionDatabase.getReactions(MessageId(mmsId1, true))

    assertEquals(1, reactionsSms.size)
    assertEquals(ReactionRecord("a", recipientIdAci, 1, 1), reactionsSms[0])

    assertEquals(1, reactionsMms.size)
    assertEquals(ReactionRecord("b", recipientIdAci, 1, 1), reactionsMms[0])

    // Notification Profile validation
    val updatedProfile1: NotificationProfile = notificationProfileDatabase.getProfile(profile1.id)!!
    val updatedProfile2: NotificationProfile = notificationProfileDatabase.getProfile(profile2.id)!!

    MatcherAssert.assertThat("Notification Profile 1 should now only contain ACI $recipientIdAci", updatedProfile1.allowedMembers, Matchers.containsInAnyOrder(recipientIdAci))
    MatcherAssert.assertThat("Notification Profile 2 should now contain ACI A ($recipientIdAci) and ACI B ($recipientIdAciB)", updatedProfile2.allowedMembers, Matchers.containsInAnyOrder(recipientIdAci, recipientIdAciB))

    // Distribution List validation
    val updatedList: DistributionListRecord = distributionListDatabase.getList(distributionListId)!!

    MatcherAssert.assertThat("Distribution list should have updated $recipientIdE164 to $recipientIdAci", updatedList.members, Matchers.containsInAnyOrder(recipientIdAci, recipientIdAciB))
  }

  // ==============================================================
  // Misc
  // ==============================================================

  @Test
  fun createByE164SanityCheck() {
    // GIVEN one recipient
    val recipientId: RecipientId = recipientDatabase.getOrInsertFromE164(E164_A)

    // WHEN I retrieve one by E164
    val possible: Optional<RecipientId> = recipientDatabase.getByE164(E164_A)

    // THEN I get it back, and it has the properties I expect
    assertTrue(possible.isPresent)
    assertEquals(recipientId, possible.get())

    val recipient = Recipient.resolved(recipientId)
    assertTrue(recipient.e164.isPresent)
    assertEquals(E164_A, recipient.e164.get())
  }

  @Test
  fun createByUuidSanityCheck() {
    // GIVEN one recipient
    val recipientId: RecipientId = recipientDatabase.getOrInsertFromServiceId(ACI_A)

    // WHEN I retrieve one by UUID
    val possible: Optional<RecipientId> = recipientDatabase.getByServiceId(ACI_A)

    // THEN I get it back, and it has the properties I expect
    assertTrue(possible.isPresent)
    assertEquals(recipientId, possible.get())

    val recipient = Recipient.resolved(recipientId)
    assertTrue(recipient.serviceId.isPresent)
    assertEquals(ACI_A, recipient.serviceId.get())
  }

  @Test(expected = IllegalArgumentException::class)
  fun getAndPossiblyMerge_noArgs_invalid() {
    recipientDatabase.getAndPossiblyMergeLegacy(null, null, true)
  }

  private fun ensureDbEmpty() {
    SignalDatabase.rawDatabase.rawQuery("SELECT COUNT(*) FROM ${RecipientDatabase.TABLE_NAME} WHERE ${RecipientDatabase.DISTRIBUTION_LIST_ID} IS NULL ", null).use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals(0, cursor.getLong(0))
    }
  }

  private fun smsMessage(sender: RecipientId, time: Long = 0, body: String = "", groupId: Optional<GroupId> = Optional.empty()): IncomingTextMessage {
    return IncomingTextMessage(sender, 1, time, time, time, body, groupId, 0, true, null)
  }

  private fun mmsMessage(sender: RecipientId, time: Long = 0, body: String = "", groupId: Optional<GroupId> = Optional.empty()): IncomingMediaMessage {
    return IncomingMediaMessage(sender, groupId, body, time, time, time, emptyList(), 0, 0, false, false, true, Optional.empty())
  }

  private fun identityKey(value: Byte): IdentityKey {
    val bytes = ByteArray(33)
    bytes[0] = 0x05
    bytes[1] = value
    return IdentityKey(bytes)
  }

  private fun notificationProfile(name: String): NotificationProfile {
    return (notificationProfileDatabase.createProfile(name = name, emoji = "", color = AvatarColor.A210, System.currentTimeMillis()) as NotificationProfileDatabase.NotificationProfileChangeResult.Success).notificationProfile
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

  private class ChangeNumberListener {

    var numberChangeWasEnqueued = false
      private set

    fun waitForJobManager() {
      ApplicationDependencies.getJobManager().flush()
      ThreadUtil.sleep(500)
    }

    fun enqueue() {
      ApplicationDependencies.getJobManager().addListener(
        { job -> job.factoryKey == RecipientChangedNumberJob.KEY },
        { _, _ -> numberChangeWasEnqueued = true }
      )
    }
  }

  companion object {
    val ACI_A = ACI.from(UUID.fromString("3436efbe-5a76-47fa-a98a-7e72c948a82e"))
    val ACI_B = ACI.from(UUID.fromString("8de7f691-0b60-4a68-9cd9-ed2f8453f9ed"))

    val PNI_A = PNI.from(UUID.fromString("154b8d92-c960-4f6c-8385-671ad2ffb999"))
    val PNI_B = PNI.from(UUID.fromString("ba92b1fb-cd55-40bf-adda-c35a85375533"))

    const val E164_A = "+12221234567"
    const val E164_B = "+13331234567"
  }
}
