/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2

import android.Manifest
import android.app.UiAutomation
import android.os.Environment
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.InternalPlatformDsl.toArray
import okio.ByteString.Companion.toByteString
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.signal.core.util.Base64
import org.signal.libsignal.messagebackup.MessageBackup
import org.signal.libsignal.messagebackup.MessageBackupKey
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.thoughtcrime.securesms.backup.v2.proto.AccountData
import org.thoughtcrime.securesms.backup.v2.proto.BackupInfo
import org.thoughtcrime.securesms.backup.v2.proto.BodyRange
import org.thoughtcrime.securesms.backup.v2.proto.Call
import org.thoughtcrime.securesms.backup.v2.proto.CallChatUpdate
import org.thoughtcrime.securesms.backup.v2.proto.Chat
import org.thoughtcrime.securesms.backup.v2.proto.ChatItem
import org.thoughtcrime.securesms.backup.v2.proto.ChatUpdateMessage
import org.thoughtcrime.securesms.backup.v2.proto.Contact
import org.thoughtcrime.securesms.backup.v2.proto.DistributionList
import org.thoughtcrime.securesms.backup.v2.proto.ExpirationTimerChatUpdate
import org.thoughtcrime.securesms.backup.v2.proto.FilePointer
import org.thoughtcrime.securesms.backup.v2.proto.Frame
import org.thoughtcrime.securesms.backup.v2.proto.Group
import org.thoughtcrime.securesms.backup.v2.proto.GroupCallChatUpdate
import org.thoughtcrime.securesms.backup.v2.proto.IndividualCallChatUpdate
import org.thoughtcrime.securesms.backup.v2.proto.MessageAttachment
import org.thoughtcrime.securesms.backup.v2.proto.ProfileChangeChatUpdate
import org.thoughtcrime.securesms.backup.v2.proto.Quote
import org.thoughtcrime.securesms.backup.v2.proto.Reaction
import org.thoughtcrime.securesms.backup.v2.proto.Recipient
import org.thoughtcrime.securesms.backup.v2.proto.ReleaseNotes
import org.thoughtcrime.securesms.backup.v2.proto.Self
import org.thoughtcrime.securesms.backup.v2.proto.SendStatus
import org.thoughtcrime.securesms.backup.v2.proto.SessionSwitchoverChatUpdate
import org.thoughtcrime.securesms.backup.v2.proto.SimpleChatUpdate
import org.thoughtcrime.securesms.backup.v2.proto.StandardMessage
import org.thoughtcrime.securesms.backup.v2.proto.StickerPack
import org.thoughtcrime.securesms.backup.v2.proto.Text
import org.thoughtcrime.securesms.backup.v2.proto.ThreadMergeChatUpdate
import org.thoughtcrime.securesms.backup.v2.stream.EncryptedBackupReader
import org.thoughtcrime.securesms.backup.v2.stream.EncryptedBackupWriter
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.whispersystems.signalservice.api.kbs.MasterKey
import org.whispersystems.signalservice.api.push.DistributionId
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.subscriptions.SubscriberId
import org.whispersystems.signalservice.api.util.toByteArray
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.time.Duration.Companion.days

/**
 * Test the import and export of message backup frames to make sure what
 * goes in, comes out.
 */
class ImportExportTest {
  companion object {
    val SELF_ACI = ServiceId.ACI.from(UUID.fromString("77770000-b477-4f35-a824-d92987a63641"))
    val SELF_PNI = ServiceId.PNI.from(UUID.fromString("77771111-b014-41fb-bf73-05cb2ec52910"))
    const val SELF_E164 = "+10000000000"
    val SELF_PROFILE_KEY = ProfileKey(Random.nextBytes(32))
    val MASTER_KEY = Base64.decode("sHuBMP4ToZk4tcNU+S8eBUeCt8Am5EZnvuqTBJIR4Do")

    val defaultBackupInfo = BackupInfo(version = 1L, backupTimeMs = 123456L)
    val selfRecipient = Recipient(id = 1, self = Self())
    val releaseNotes = Recipient(id = 2, releaseNotes = ReleaseNotes())
    val standardAccountData = AccountData(
      profileKey = SELF_PROFILE_KEY.serialize().toByteString(),
      username = "self.01",
      usernameLink = null,
      givenName = "Peter",
      familyName = "Parker",
      avatarUrlPath = "https://example.com/",
      subscriberId = SubscriberId.generate().bytes.toByteString(),
      subscriberCurrencyCode = "USD",
      subscriptionManuallyCancelled = true,
      accountSettings = AccountData.AccountSettings(
        readReceipts = true,
        sealedSenderIndicators = true,
        typingIndicators = true,
        linkPreviews = true,
        notDiscoverableByPhoneNumber = true,
        preferContactAvatars = true,
        universalExpireTimer = 42,
        displayBadgesOnProfile = true,
        keepMutedChatsArchived = true,
        hasSetMyStoriesPrivacy = true,
        hasViewedOnboardingStory = true,
        storiesDisabled = true,
        storyViewReceiptsEnabled = true,
        hasSeenGroupStoryEducationSheet = true,
        hasCompletedUsernameOnboarding = true,
        phoneNumberSharingMode = AccountData.PhoneNumberSharingMode.EVERYBODY,
        preferredReactionEmoji = listOf("a", "b", "c")
      )
    )
    val alice = Recipient(
      id = 3,
      contact = Contact(
        aci = TestRecipientUtils.nextAci().toByteString(),
        pni = TestRecipientUtils.nextPni().toByteString(),
        username = "cool.01",
        e164 = 141255501234,
        blocked = false,
        hidden = false,
        registered = Contact.Registered.REGISTERED,
        unregisteredTimestamp = 0L,
        profileKey = TestRecipientUtils.generateProfileKey().toByteString(),
        profileSharing = true,
        profileGivenName = "Alexa",
        profileFamilyName = "Kim",
        hideStory = true
      )
    )

    /**
     * When using standardFrames you must start recipient ids at 3.
     */
    private val standardFrames = arrayOf(defaultBackupInfo, standardAccountData, selfRecipient, releaseNotes)
  }

  @JvmField
  @Rule
  var testName = TestName()

  @Before
  fun setup() {
    SignalStore.svr().setMasterKey(MasterKey(MASTER_KEY), "1234")
    SignalStore.account().setE164(SELF_E164)
    SignalStore.account().setAci(SELF_ACI)
    SignalStore.account().setPni(SELF_PNI)
    SignalStore.account().generateAciIdentityKeyIfNecessary()
    SignalStore.account().generatePniIdentityKeyIfNecessary()
  }

  @Test
  fun accountAndSelf() {
    importExport(*standardFrames)
  }

  @Test
  fun largeNumberOfRecipientsAndChats() {
    val recipients = ArrayList<Recipient>(5000)
    val chats = ArrayList<Chat>(5000)
    var id = 3L
    for (i in 0..5000) {
      val recipientId = id++
      recipients.add(
        Recipient(
          id = recipientId,
          contact = Contact(
            aci = TestRecipientUtils.nextAci().toByteString(),
            pni = TestRecipientUtils.nextPni().toByteString(),
            username = "rec$i.01",
            e164 = 14125550000 + i,
            blocked = false,
            hidden = false,
            registered = Contact.Registered.REGISTERED,
            unregisteredTimestamp = 0L,
            profileKey = TestRecipientUtils.generateProfileKey().toByteString(),
            profileSharing = true,
            profileGivenName = "Test",
            profileFamilyName = "Recipient$i",
            hideStory = false
          )
        )
      )
      chats.add(
        Chat(
          id = recipientId - 2L,
          recipientId = recipientId
        )
      )
      if (i % 10 == 0) {
        val groupId = id++
        recipients.add(
          Recipient(
            id = groupId,
            group = Group(
              masterKey = TestRecipientUtils.generateGroupMasterKey().toByteString(),
              whitelisted = true,
              hideStory = false,
              storySendMode = Group.StorySendMode.ENABLED,
              name = "Cool Group $i"
            )
          )
        )
        chats.add(
          Chat(
            id = groupId - 2L,
            recipientId = groupId
          )
        )
      }
    }
    importExport(
      *standardFrames,
      *recipients.toArray()
    )
  }

  @Test
  fun largeNumberOfMessagesAndChats() {
    val NUM_INDIVIDUAL_RECIPIENTS = 1000
    val numIndividualMessages = 500
    val numGroupMessagesPerPerson = 200

    val random = Random(1516)

    val recipients = ArrayList<Recipient>(1010)
    val chats = ArrayList<Chat>(1010)
    var id = 3L
    for (i in 0 until NUM_INDIVIDUAL_RECIPIENTS) {
      val recipientId = id++
      recipients.add(
        Recipient(
          id = recipientId,
          contact = Contact(
            aci = TestRecipientUtils.nextAci().toByteString(),
            pni = TestRecipientUtils.nextPni().toByteString(),
            username = if (random.trueWithProbability(0.2f)) "rec$i.01" else null,
            e164 = 14125550000 + i,
            blocked = random.trueWithProbability(0.1f),
            hidden = random.trueWithProbability(0.1f),
            registered = Contact.Registered.REGISTERED,
            unregisteredTimestamp = 0L,
            profileKey = TestRecipientUtils.generateProfileKey().toByteString(),
            profileSharing = random.trueWithProbability(0.9f),
            profileGivenName = "Test",
            profileFamilyName = "Recipient$i",
            hideStory = false
          )
        )
      )
      chats.add(
        Chat(
          id = recipientId - 2L,
          recipientId = recipientId
        )
      )
      if (i % 100 == 0) {
        val groupId = id++
        recipients.add(
          Recipient(
            id = groupId,
            group = Group(
              masterKey = TestRecipientUtils.generateGroupMasterKey().toByteString(),
              whitelisted = random.trueWithProbability(0.9f),
              hideStory = random.trueWithProbability(0.1f),
              storySendMode = if (random.trueWithProbability(0.9f)) Group.StorySendMode.ENABLED else Group.StorySendMode.DISABLED,
              name = "Cool Group $i"
            )
          )
        )
        chats.add(
          Chat(
            id = groupId - 2L,
            recipientId = groupId
          )
        )
      }
    }
    val chatItems = ArrayList<ChatItem>()
    var sentTime = 1L
    val groupMembers = ArrayList<Recipient>()
    var group: Recipient? = null
    for (recipient in recipients) {
      // Make another group and populate it with messages from these members
      if (recipient.group != null) {
        if (group == null) {
          group = recipient
          groupMembers.clear()
        } else {
          for (member in groupMembers) {
            for (i in 0 until numGroupMessagesPerPerson) {
              chatItems.add(
                ChatItem(
                  chatId = group.id - 2L,
                  authorId = member.id,
                  dateSent = sentTime++,
                  sms = false,
                  incoming = ChatItem.IncomingMessageDetails(
                    dateReceived = sentTime + 1,
                    dateServerSent = sentTime,
                    read = true,
                    sealedSender = true
                  ),
                  standardMessage = StandardMessage(
                    text = Text(
                      body = "Medium length message from ${member.contact?.profileGivenName} ${member.contact?.profileFamilyName} sent at $sentTime"
                    )
                  )
                )
              )
            }
          }
          for (i in 0 until numGroupMessagesPerPerson) {
            ChatItem(
              chatId = group.id - 2L,
              authorId = selfRecipient.id,
              dateSent = sentTime++,
              sms = false,
              outgoing = ChatItem.OutgoingMessageDetails(
                sendStatus = groupMembers.map { groupMember ->
                  SendStatus(recipientId = groupMember.id, deliveryStatus = if (random.trueWithProbability(0.8f)) SendStatus.Status.READ else SendStatus.Status.DELIVERED, sealedSender = true)
                }
              ),
              standardMessage = StandardMessage(
                text = Text(
                  body = "Outgoing message without much text in it just because"
                )
              )
            )
          }
        }
      } else {
        groupMembers.add(recipient)
        for (i in 0 until numIndividualMessages) {
          if (i % 2 == 0) {
            ChatItem(
              chatId = 1,
              authorId = selfRecipient.id,
              dateSent = sentTime++,
              sms = false,
              outgoing = ChatItem.OutgoingMessageDetails(
                sendStatus = listOf(
                  SendStatus(recipient.id, deliveryStatus = if (random.trueWithProbability(0.8f)) SendStatus.Status.READ else SendStatus.Status.DELIVERED, sealedSender = true)
                )
              ),
              standardMessage = StandardMessage(
                text = Text(
                  body = "Outgoing message without much text in it just because"
                )
              )
            )
          } else {
            ChatItem(
              chatId = 1,
              authorId = selfRecipient.id,
              dateSent = sentTime++,
              sms = false,
              incoming = ChatItem.IncomingMessageDetails(
                dateReceived = sentTime + 1,
                dateServerSent = sentTime,
                read = true,
                sealedSender = true
              ),
              standardMessage = StandardMessage(
                text = Text(
                  body = "Outgoing message without much text in it just because"
                )
              )
            )
          }
        }
      }
    }
    val import = exportFrames(
      *standardFrames,
      *recipients.toArray(),
      *chatItems.toArray()
    )
    outputFile(import)
  }

  @Test
  fun individualRecipients() {
    importExport(
      *standardFrames,
      Recipient(
        id = 3,
        contact = Contact(
          aci = TestRecipientUtils.nextAci().toByteString(),
          pni = TestRecipientUtils.nextPni().toByteString(),
          username = "cool.01",
          e164 = 141255501234,
          blocked = true,
          hidden = true,
          registered = Contact.Registered.REGISTERED,
          unregisteredTimestamp = 0L,
          profileKey = TestRecipientUtils.generateProfileKey().toByteString(),
          profileSharing = true,
          profileGivenName = "Alexa",
          profileFamilyName = "Kim",
          hideStory = true
        )
      ),
      Recipient(
        id = 4,
        contact = Contact(
          aci = null,
          pni = null,
          username = null,
          e164 = 141255501235,
          blocked = true,
          hidden = true,
          registered = Contact.Registered.NOT_REGISTERED,
          unregisteredTimestamp = 1234568927398L,
          profileKey = TestRecipientUtils.generateProfileKey().toByteString(),
          profileSharing = false,
          profileGivenName = "Peter",
          profileFamilyName = "Kim",
          hideStory = true
        )
      )
    )
  }

  @Test
  fun groupRecipients() {
    importExport(
      *standardFrames,
      Recipient(
        id = 3,
        group = Group(
          masterKey = TestRecipientUtils.generateGroupMasterKey().toByteString(),
          whitelisted = true,
          hideStory = true,
          storySendMode = Group.StorySendMode.ENABLED,
          name = "Cool test group"
        )
      ),
      Recipient(
        id = 4,
        group = Group(
          masterKey = TestRecipientUtils.generateGroupMasterKey().toByteString(),
          whitelisted = false,
          hideStory = false,
          storySendMode = Group.StorySendMode.DEFAULT,
          name = "Cool test group"
        )
      )
    )
  }

  @Test
  fun distributionListRecipients() {
    importExport(
      *standardFrames,
      Recipient(
        id = 3,
        contact = Contact(
          aci = TestRecipientUtils.nextAci().toByteString(),
          pni = TestRecipientUtils.nextPni().toByteString(),
          username = "cool.01",
          e164 = 141255501234,
          blocked = true,
          hidden = true,
          registered = Contact.Registered.REGISTERED,
          unregisteredTimestamp = 0L,
          profileKey = TestRecipientUtils.generateProfileKey().toByteString(),
          profileSharing = true,
          profileGivenName = "Alexa",
          profileFamilyName = "Kim",
          hideStory = true
        )
      ),
      Recipient(
        id = 4,
        contact = Contact(
          aci = null,
          pni = null,
          username = null,
          e164 = 141255501235,
          blocked = true,
          hidden = true,
          registered = Contact.Registered.REGISTERED,
          unregisteredTimestamp = 0L,
          profileKey = TestRecipientUtils.generateProfileKey().toByteString(),
          profileSharing = true,
          profileGivenName = "Peter",
          profileFamilyName = "Kim",
          hideStory = true
        )
      ),
      Recipient(
        id = 5,
        contact = Contact(
          aci = null,
          pni = null,
          username = null,
          e164 = 141255501236,
          blocked = true,
          hidden = true,
          registered = Contact.Registered.REGISTERED,
          unregisteredTimestamp = 0L,
          profileKey = TestRecipientUtils.generateProfileKey().toByteString(),
          profileSharing = true,
          profileGivenName = "Father",
          profileFamilyName = "Kim",
          hideStory = true
        )
      ),
      Recipient(
        id = 6,
        distributionList = DistributionList(
          name = "Kim Family",
          distributionId = DistributionId.create().asUuid().toByteArray().toByteString(),
          allowReplies = true,
          deletionTimestamp = 0L,
          privacyMode = DistributionList.PrivacyMode.ONLY_WITH,
          memberRecipientIds = listOf(3, 4, 5)
        )
      )
    )
  }

  @Test
  fun deletedDistributionList() {
    val alexa = Recipient(
      id = 3,
      contact = Contact(
        aci = TestRecipientUtils.nextAci().toByteString(),
        pni = TestRecipientUtils.nextPni().toByteString(),
        username = "cool.01",
        e164 = 141255501234,
        blocked = true,
        hidden = true,
        registered = Contact.Registered.REGISTERED,
        unregisteredTimestamp = 0L,
        profileKey = TestRecipientUtils.generateProfileKey().toByteString(),
        profileSharing = true,
        profileGivenName = "Alexa",
        profileFamilyName = "Kim",
        hideStory = true
      )
    )
    val importData = exportFrames(
      *standardFrames,
      alexa,
      Recipient(
        id = 6,
        distributionList = DistributionList(
          name = "Deleted list",
          distributionId = DistributionId.create().asUuid().toByteArray().toByteString(),
          allowReplies = true,
          deletionTimestamp = 12345L,
          privacyMode = DistributionList.PrivacyMode.ONLY_WITH,
          memberRecipientIds = listOf(3)
        )
      )
    )
    import(importData)
    val exported = export()
    val expected = exportFrames(
      *standardFrames,
      alexa
    )
    outputFile(importData, expected)
    compare(expected, exported)
  }

  @Test
  fun chatThreads() {
    importExport(
      *standardFrames,
      Recipient(
        id = 3,
        contact = Contact(
          aci = TestRecipientUtils.nextAci().toByteString(),
          pni = TestRecipientUtils.nextPni().toByteString(),
          username = "cool.01",
          e164 = 141255501234,
          blocked = false,
          hidden = false,
          registered = Contact.Registered.REGISTERED,
          unregisteredTimestamp = 0L,
          profileKey = TestRecipientUtils.generateProfileKey().toByteString(),
          profileSharing = true,
          profileGivenName = "Alexa",
          profileFamilyName = "Kim",
          hideStory = true
        )
      ),
      Recipient(
        id = 4,
        group = Group(
          masterKey = TestRecipientUtils.generateGroupMasterKey().toByteString(),
          whitelisted = true,
          hideStory = true,
          storySendMode = Group.StorySendMode.DEFAULT,
          name = "Cool test group"
        )
      ),
      Chat(
        id = 1,
        recipientId = 3,
        archived = true,
        pinnedOrder = 1,
        expirationTimerMs = 1.days.inWholeMilliseconds,
        muteUntilMs = System.currentTimeMillis(),
        markedUnread = true,
        dontNotifyForMentionsIfMuted = true,
        wallpaper = null
      )
    )
  }

  @Test
  fun calls() {
    val individualCalls = ArrayList<Call>()
    val groupCalls = ArrayList<Call>()
    val states = arrayOf(Call.State.MISSED, Call.State.COMPLETED, Call.State.DECLINED_BY_USER, Call.State.DECLINED_BY_NOTIFICATION_PROFILE)
    val types = arrayOf(Call.Type.VIDEO_CALL, Call.Type.AD_HOC_CALL, Call.Type.AUDIO_CALL)
    var id = 1L
    var timestamp = 12345L

    for (state in states) {
      for (type in types) {
        individualCalls.add(
          Call(
            callId = id++,
            conversationRecipientId = 3,
            type = type,
            state = state,
            timestamp = timestamp++,
            ringerRecipientId = 3,
            outgoing = true
          )
        )
        individualCalls.add(
          Call(
            callId = id++,
            conversationRecipientId = 3,
            type = type,
            state = state,
            timestamp = timestamp++,
            ringerRecipientId = selfRecipient.id,
            outgoing = false
          )
        )
      }
      groupCalls.add(
        Call(
          callId = id++,
          conversationRecipientId = 4,
          type = Call.Type.GROUP_CALL,
          state = state,
          timestamp = timestamp++,
          ringerRecipientId = 3,
          outgoing = true
        )
      )
      groupCalls.add(
        Call(
          callId = id++,
          conversationRecipientId = 4,
          type = Call.Type.GROUP_CALL,
          state = state,
          timestamp = timestamp++,
          ringerRecipientId = selfRecipient.id,
          outgoing = false
        )
      )
    }

    var sentTime = 0L
    val individualCallChatItems = individualCalls.map { call ->
      ChatItem(
        chatId = 1,
        authorId = selfRecipient.id,
        dateSent = sentTime++,
        sms = false,
        incoming = ChatItem.IncomingMessageDetails(
          dateReceived = sentTime + 1,
          dateServerSent = sentTime,
          read = true,
          sealedSender = true
        ),
        updateMessage = ChatUpdateMessage(
          callingMessage = CallChatUpdate(
            callMessage = IndividualCallChatUpdate(
              type = IndividualCallChatUpdate.Type.INCOMING_AUDIO_CALL
            )
          )
        )
      )
    }.toTypedArray()

    val startedAci = TestRecipientUtils.nextAci().toByteString()
    val groupCallChatItems = groupCalls.map { call ->
      ChatItem(
        chatId = 1,
        authorId = selfRecipient.id,
        dateSent = sentTime++,
        sms = false,
        incoming = ChatItem.IncomingMessageDetails(
          dateReceived = sentTime + 1,
          dateServerSent = sentTime,
          read = true,
          sealedSender = true
        ),
        updateMessage = ChatUpdateMessage(
          callingMessage = CallChatUpdate(
            groupCall = GroupCallChatUpdate(
              startedCallAci = startedAci,
              startedCallTimestamp = 0,
              endedCallTimestamp = 0,
              localUserJoined = GroupCallChatUpdate.LocalUserJoined.JOINED,
              inCallAcis = emptyList()
            )
          )
        )
      )
    }.toTypedArray()

    importExport(
      *standardFrames,
      Recipient(
        id = 3,
        contact = Contact(
          aci = startedAci,
          pni = TestRecipientUtils.nextPni().toByteString(),
          username = "cool.01",
          e164 = 141255501234,
          blocked = false,
          hidden = false,
          registered = Contact.Registered.REGISTERED,
          unregisteredTimestamp = 0L,
          profileKey = TestRecipientUtils.generateProfileKey().toByteString(),
          profileSharing = true,
          profileGivenName = "Alexa",
          profileFamilyName = "Kim",
          hideStory = true
        )
      ),
      Recipient(
        id = 4,
        group = Group(
          masterKey = TestRecipientUtils.generateGroupMasterKey().toByteString(),
          whitelisted = true,
          hideStory = true,
          storySendMode = Group.StorySendMode.DEFAULT,
          name = "Cool test group"
        )
      ),
      Chat(
        id = 1,
        recipientId = 3,
        archived = true,
        pinnedOrder = 1,
        expirationTimerMs = 1.days.inWholeMilliseconds,
        muteUntilMs = System.currentTimeMillis(),
        markedUnread = true,
        dontNotifyForMentionsIfMuted = true,
        wallpaper = null
      ),
      *individualCalls.toArray(),
      *groupCalls.toArray(),
      *individualCallChatItems,
      *groupCallChatItems
    )
  }

  @Test
  fun messageWithOnlyText() {
    var dateSent = System.currentTimeMillis()
    val sendStatuses = enumerateSendStatuses(alice.id)
    val incomingMessageDetails = enumerateIncomingMessageDetails(dateSent + 200)
    val outgoingMessages = ArrayList<ChatItem>()
    val incomingMessages = ArrayList<ChatItem>()
    for (sendStatus in sendStatuses) {
      outgoingMessages.add(
        ChatItem(
          chatId = 1,
          authorId = selfRecipient.id,
          dateSent = dateSent++,
          expireStartDate = dateSent + 1000,
          expiresInMs = TimeUnit.DAYS.toMillis(2),
          sms = false,
          outgoing = ChatItem.OutgoingMessageDetails(
            sendStatus = listOf(sendStatus)
          ),
          standardMessage = StandardMessage(
            text = Text(
              body = "Text only body"
            )
          )
        )
      )
    }
    dateSent++
    for (incomingDetail in incomingMessageDetails) {
      incomingMessages.add(
        ChatItem(
          chatId = 1,
          authorId = alice.id,
          dateSent = dateSent++,
          expireStartDate = dateSent + 1000,
          expiresInMs = TimeUnit.DAYS.toMillis(2),
          sms = false,
          incoming = incomingDetail,
          standardMessage = StandardMessage(
            text = Text(
              body = "Text only body"
            )
          )
        )
      )
    }

    importExport(
      *standardFrames,
      alice,
      buildChat(alice, 1),
      *outgoingMessages.toArray(),
      *incomingMessages.toArray()
    )
  }

  @Test
  fun messageWithTextMentionsBodyRangesAndReactions() {
    val time = System.currentTimeMillis()
    importExport(
      *standardFrames,
      alice,
      buildChat(alice, 1),
      ChatItem(
        chatId = 1,
        authorId = selfRecipient.id,
        dateSent = 100,
        expireStartDate = time,
        expiresInMs = TimeUnit.DAYS.toMillis(2),
        incoming = ChatItem.IncomingMessageDetails(
          dateReceived = 105,
          dateServerSent = 104,
          read = true,
          sealedSender = true
        ),
        standardMessage = StandardMessage(
          text = Text(
            body = "Hey check this out I love spans!",
            bodyRanges = listOf(
              BodyRange(
                start = 6,
                length = 3,
                style = BodyRange.Style.BOLD
              ),
              BodyRange(
                start = 10,
                length = 3,
                style = BodyRange.Style.ITALIC
              ),
              BodyRange(
                start = 14,
                length = 3,
                style = BodyRange.Style.SPOILER
              ),
              BodyRange(
                start = 18,
                length = 3,
                style = BodyRange.Style.STRIKETHROUGH
              ),
              BodyRange(
                start = 22,
                length = 3,
                style = BodyRange.Style.MONOSPACE
              ),
              BodyRange(
                start = 4,
                length = 0,
                mentionAci = alice.contact!!.aci
              )
            )
          ),
          reactions = listOf(
            Reaction(emoji = "F", authorId = selfRecipient.id, sentTimestamp = 302, receivedTimestamp = 303),
            Reaction(emoji = "F", authorId = alice.id, sentTimestamp = 301, receivedTimestamp = 302)
          )
        )
      )
    )
  }

  @Test
  fun messageWithTextAndQuotes() {
    val spans = listOf(
      BodyRange(
        start = 6,
        length = 3,
        style = BodyRange.Style.BOLD
      ),
      BodyRange(
        start = 10,
        length = 3,
        style = BodyRange.Style.ITALIC
      ),
      BodyRange(
        start = 14,
        length = 3,
        style = BodyRange.Style.SPOILER
      ),
      BodyRange(
        start = 18,
        length = 3,
        style = BodyRange.Style.STRIKETHROUGH
      ),
      BodyRange(
        start = 22,
        length = 3,
        style = BodyRange.Style.MONOSPACE
      )
    )
    val time = System.currentTimeMillis()
    importExport(
      *standardFrames,
      alice,
      buildChat(alice, 1),
      ChatItem(
        chatId = 1,
        authorId = selfRecipient.id,
        dateSent = 100,
        expireStartDate = time,
        expiresInMs = TimeUnit.DAYS.toMillis(2),
        incoming = ChatItem.IncomingMessageDetails(
          dateReceived = 105,
          dateServerSent = 104,
          read = true,
          sealedSender = true
        ),
        standardMessage = StandardMessage(
          text = Text(
            body = "Hey check this out I love spans!",
            bodyRanges = spans
          )
        )
      ),
      ChatItem(
        chatId = 1,
        authorId = selfRecipient.id,
        dateSent = 101,
        expireStartDate = time,
        expiresInMs = TimeUnit.DAYS.toMillis(2),
        incoming = ChatItem.IncomingMessageDetails(
          dateReceived = 105,
          dateServerSent = 104,
          read = true,
          sealedSender = true
        ),
        standardMessage = StandardMessage(
          text = Text(
            body = "I quoted an existing message"
          ),
          quote = Quote(
            targetSentTimestamp = 100,
            authorId = alice.id,
            type = Quote.Type.NORMAL,
            text = "Hey check this out I love spans!",
            bodyRanges = spans
          )
        )
      ),
      ChatItem(
        chatId = 1,
        authorId = selfRecipient.id,
        dateSent = 102,
        expireStartDate = time,
        expiresInMs = TimeUnit.DAYS.toMillis(2),
        incoming = ChatItem.IncomingMessageDetails(
          dateReceived = 105,
          dateServerSent = 104,
          read = true,
          sealedSender = true
        ),
        standardMessage = StandardMessage(
          text = Text(
            body = "I quoted an non-existing message"
          ),
          quote = Quote(
            targetSentTimestamp = 60,
            authorId = alice.id,
            type = Quote.Type.NORMAL,
            text = "Hey check this out I love spans!",
            bodyRanges = spans
          )
        )
      )
    )
  }

  @Test
  fun messagesNearExpirationNotExported() {
    val chat = buildChat(alice, 1)
    val expirationNotStarted = ChatItem(
      chatId = 1,
      authorId = alice.id,
      dateSent = 101,
      expireStartDate = null,
      expiresInMs = TimeUnit.DAYS.toMillis(1),
      sms = false,
      incoming = ChatItem.IncomingMessageDetails(
        dateReceived = 100,
        dateServerSent = 100,
        read = true
      ),
      standardMessage = StandardMessage(
        text = Text(
          body = "Expiration not started but less than or equal to 1 day"
        )
      )
    )
    val importData = exportFrames(
      *standardFrames,
      alice,
      chat,
      ChatItem(
        chatId = 1,
        authorId = alice.id,
        dateSent = 100,
        expireStartDate = System.currentTimeMillis(),
        expiresInMs = TimeUnit.DAYS.toMillis(1),
        sms = false,
        incoming = ChatItem.IncomingMessageDetails(
          dateReceived = 100,
          dateServerSent = 100,
          read = true
        ),
        standardMessage = StandardMessage(
          text = Text(
            body = "Near expiration"
          )
        )
      ),
      expirationNotStarted
    )
    import(importData)
    val exported = export()
    val expected = exportFrames(
      *standardFrames,
      alice,
      chat,
      expirationNotStarted
    )
    outputFile(importData, expected)
    compare(expected, exported)
  }

  @Test
  fun messageWithAttachmentsAndQuoteAttachments() {
    var dateSent = System.currentTimeMillis()
    importExport(
      *standardFrames,
      alice,
      buildChat(alice, 1),
      ChatItem(
        chatId = 1,
        authorId = selfRecipient.id,
        dateSent = dateSent++,
        sms = false,
        outgoing = ChatItem.OutgoingMessageDetails(
          sendStatus = listOf(SendStatus(alice.id, deliveryStatus = SendStatus.Status.READ, lastStatusUpdateTimestamp = -1))
        ),
        standardMessage = StandardMessage(
          attachments = listOf(
            MessageAttachment(
              pointer = FilePointer(
                attachmentLocator = FilePointer.AttachmentLocator(
                  cdnKey = "coolCdnKey",
                  cdnNumber = 2,
                  uploadTimestamp = System.currentTimeMillis(),
                  key = (1..32).map { it.toByte() }.toByteArray().toByteString(),
                  size = 12345,
                  digest = (1..32).map { it.toByte() }.toByteArray().toByteString()
                ),
                contentType = "image/png",
                fileName = "very_cool_picture.png",
                width = 100,
                height = 200,
                caption = "Love this cool picture!",
                incrementalMacChunkSize = 0
              ),
              wasDownloaded = true
            ),
            MessageAttachment(
              pointer = FilePointer(
                invalidAttachmentLocator = FilePointer.InvalidAttachmentLocator(),
                contentType = "image/png",
                width = 100,
                height = 200,
                caption = "Love this cool picture! Too bad u cant download it",
                incrementalMacChunkSize = 0
              ),
              wasDownloaded = false
            ),
            MessageAttachment(
              pointer = FilePointer(
                backupLocator = FilePointer.BackupLocator(
                  "digestherebutimlazy",
                  cdnNumber = 3,
                  key = (1..32).map { it.toByte() }.toByteArray().toByteString(),
                  digest = (1..64).map { it.toByte() }.toByteArray().toByteString(),
                  size = 12345
                ),
                contentType = "image/png",
                width = 100,
                height = 200,
                caption = "Love this cool picture! Too bad u cant download it",
                incrementalMacChunkSize = 0
              ),
              wasDownloaded = true
            )
          )
        )
      )
    )
  }

  @Test
  fun simpleChatUpdateMessage() {
    var dateSentStart = 100L
    val updateMessages = ArrayList<ChatItem>()
    for (i in 1..11) {
      updateMessages.add(
        ChatItem(
          chatId = 1,
          authorId = alice.id,
          dateSent = dateSentStart++,
          incoming = ChatItem.IncomingMessageDetails(
            dateReceived = dateSentStart,
            dateServerSent = dateSentStart,
            read = true,
            sealedSender = true
          ),
          updateMessage = ChatUpdateMessage(
            simpleUpdate = SimpleChatUpdate(
              type = SimpleChatUpdate.Type.fromValue(i)!!
            )
          )
        )
      )
    }
    importExport(
      *standardFrames,
      alice,
      buildChat(alice, 1),
      *updateMessages.toArray()
    )
  }

  @Test
  fun expirationTimerUpdateMessage() {
    var dateSentStart = 100L
    importExport(
      *standardFrames,
      alice,
      buildChat(alice, 1),
      ChatItem(
        chatId = 1,
        authorId = alice.id,
        dateSent = dateSentStart++,
        incoming = ChatItem.IncomingMessageDetails(
          dateReceived = dateSentStart,
          dateServerSent = dateSentStart,
          read = true,
          sealedSender = true
        ),
        updateMessage = ChatUpdateMessage(
          expirationTimerChange = ExpirationTimerChatUpdate(
            1000
          )
        )
      ),
      ChatItem(
        chatId = 1,
        authorId = selfRecipient.id,
        dateSent = dateSentStart++,
        outgoing = ChatItem.OutgoingMessageDetails(
          sendStatus = listOf(
            SendStatus(alice.id, deliveryStatus = SendStatus.Status.READ, sealedSender = true, lastStatusUpdateTimestamp = -1)
          )
        ),
        updateMessage = ChatUpdateMessage(
          expirationTimerChange = ExpirationTimerChatUpdate(
            0
          )
        )
      ),
      ChatItem(
        chatId = 1,
        authorId = selfRecipient.id,
        dateSent = dateSentStart++,
        outgoing = ChatItem.OutgoingMessageDetails(
          sendStatus = listOf(SendStatus(alice.id, deliveryStatus = SendStatus.Status.READ, sealedSender = true, lastStatusUpdateTimestamp = -1))
        ),
        updateMessage = ChatUpdateMessage(
          expirationTimerChange = ExpirationTimerChatUpdate(
            10000
          )
        )
      ),
      ChatItem(
        chatId = 1,
        authorId = alice.id,
        dateSent = dateSentStart++,
        incoming = ChatItem.IncomingMessageDetails(
          dateReceived = dateSentStart,
          dateServerSent = dateSentStart,
          read = true,
          sealedSender = true
        ),
        updateMessage = ChatUpdateMessage(
          expirationTimerChange = ExpirationTimerChatUpdate(
            0
          )
        )
      )
    )
  }

  @Test
  fun profileChangeChatUpdateMessage() {
    var dateSentStart = 100L
    importExport(
      *standardFrames,
      alice,
      buildChat(alice, 1),
      ChatItem(
        chatId = 1,
        authorId = alice.id,
        dateSent = dateSentStart++,
        incoming = ChatItem.IncomingMessageDetails(
          dateReceived = dateSentStart,
          dateServerSent = dateSentStart,
          read = true,
          sealedSender = true
        ),
        updateMessage = ChatUpdateMessage(
          profileChange = ProfileChangeChatUpdate(
            previousName = "Aliceee Kim",
            newName = "Alice Kim"
          )
        )
      )
    )
  }

  @Test
  fun threadMergeChatUpdate() {
    var dateSentStart = 100L
    importExport(
      *standardFrames,
      alice,
      buildChat(alice, 1),
      ChatItem(
        chatId = 1,
        authorId = alice.id,
        dateSent = dateSentStart++,
        incoming = ChatItem.IncomingMessageDetails(
          dateReceived = dateSentStart,
          dateServerSent = dateSentStart,
          read = true,
          sealedSender = true
        ),
        updateMessage = ChatUpdateMessage(
          threadMerge = ThreadMergeChatUpdate(
            previousE164 = 141255501237
          )
        )
      )
    )
  }

  @Test
  fun sessionSwitchoverChatUpdate() {
    var dateSentStart = 100L
    importExport(
      *standardFrames,
      alice,
      buildChat(alice, 1),
      ChatItem(
        chatId = 1,
        authorId = alice.id,
        dateSent = dateSentStart++,
        incoming = ChatItem.IncomingMessageDetails(
          dateReceived = dateSentStart,
          dateServerSent = dateSentStart,
          read = true,
          sealedSender = true
        ),
        updateMessage = ChatUpdateMessage(
          sessionSwitchover = SessionSwitchoverChatUpdate(
            e164 = 141255501237
          )
        )
      )
    )
  }

  fun enumerateIncomingMessageDetails(dateSent: Long): List<ChatItem.IncomingMessageDetails> {
    val details = mutableListOf<ChatItem.IncomingMessageDetails>()
    details.add(
      ChatItem.IncomingMessageDetails(
        dateReceived = dateSent + 1,
        dateServerSent = dateSent,
        read = true,
        sealedSender = true
      )
    )
    details.add(
      ChatItem.IncomingMessageDetails(
        dateReceived = dateSent + 1,
        dateServerSent = dateSent,
        read = true,
        sealedSender = false
      )
    )
    details.add(
      ChatItem.IncomingMessageDetails(
        dateReceived = dateSent + 1,
        dateServerSent = dateSent,
        read = false,
        sealedSender = true
      )
    )
    details.add(
      ChatItem.IncomingMessageDetails(
        dateReceived = dateSent + 1,
        dateServerSent = dateSent,
        read = false,
        sealedSender = false
      )
    )
    return details
  }

  fun enumerateSendStatuses(recipientId: Long): List<SendStatus> {
    val statuses = ArrayList<SendStatus>()
    val sealedSenderStates = listOf(true, false)
    for (sealedSender in sealedSenderStates) {
      statuses.add(
        SendStatus(
          recipientId = recipientId,
          deliveryStatus = SendStatus.Status.DELIVERED,
          sealedSender = sealedSender,
          lastStatusUpdateTimestamp = -1
        )
      )
      statuses.add(
        SendStatus(
          recipientId = recipientId,
          deliveryStatus = SendStatus.Status.PENDING,
          sealedSender = sealedSender,
          lastStatusUpdateTimestamp = -1,
          networkFailure = true
        )
      )
      statuses.add(
        SendStatus(
          recipientId = recipientId,
          deliveryStatus = SendStatus.Status.SENT,
          sealedSender = sealedSender,
          lastStatusUpdateTimestamp = -1
        )
      )
      statuses.add(
        SendStatus(
          recipientId = recipientId,
          deliveryStatus = SendStatus.Status.READ,
          sealedSender = sealedSender,
          lastStatusUpdateTimestamp = -1
        )
      )
      statuses.add(
        SendStatus(
          recipientId = recipientId,
          deliveryStatus = SendStatus.Status.PENDING,
          sealedSender = sealedSender,
          networkFailure = true,
          lastStatusUpdateTimestamp = -1
        )
      )
      statuses.add(
        SendStatus(
          recipientId = recipientId,
          deliveryStatus = SendStatus.Status.FAILED,
          sealedSender = sealedSender,
          identityKeyMismatch = true,
          lastStatusUpdateTimestamp = -1
        )
      )
    }
    return statuses
  }

  private fun buildChat(recipient: Recipient, id: Long): Chat {
    return Chat(
      id = id,
      recipientId = recipient.id,
      archived = false,
      pinnedOrder = 0,
      expirationTimerMs = 0,
      muteUntilMs = 0,
      markedUnread = false,
      dontNotifyForMentionsIfMuted = false,
      wallpaper = null
    )
  }

  /**
   * Export passed in frames as a backup. Does not automatically include
   * any standard frames (e.g. backup header).
   */
  private fun exportFrames(vararg objects: Any): ByteArray {
    val outputStream = ByteArrayOutputStream()
    val writer = EncryptedBackupWriter(
      key = SignalStore.svr().getOrCreateMasterKey().deriveBackupKey(),
      aci = SignalStore.account().aci!!,
      outputStream = outputStream,
      append = { mac -> outputStream.write(mac) }
    )

    writer.use {
      for (obj in objects) {
        when (obj) {
          is BackupInfo -> writer.write(obj)
          is AccountData -> writer.write(Frame(account = obj))
          is Recipient -> writer.write(Frame(recipient = obj))
          is Chat -> writer.write(Frame(chat = obj))
          is ChatItem -> writer.write(Frame(chatItem = obj))
          is Call -> writer.write(Frame(call = obj))
          is StickerPack -> writer.write(Frame(stickerPack = obj))
          else -> Assert.fail("invalid object $obj")
        }
      }
    }
    return outputStream.toByteArray()
  }

  /**
   * Exports the passed in frames as a backup and then attempts to
   * import them.
   */
  private fun import(vararg objects: Any) {
    val importData = exportFrames(*objects)
    import(importData)
  }

  private fun import(importData: ByteArray) {
    BackupRepository.import(length = importData.size.toLong(), inputStreamFactory = { ByteArrayInputStream(importData) }, selfData = BackupRepository.SelfData(SELF_ACI, SELF_PNI, SELF_E164, SELF_PROFILE_KEY))
  }

  /**
   * Export our current database as a backup.
   */
  private fun export(): ByteArray {
    val exportData = BackupRepository.export()
    return exportData
  }

  private fun validate(importData: ByteArray): MessageBackup.ValidationResult {
    val factory = { ByteArrayInputStream(importData) }
    val masterKey = SignalStore.svr().getOrCreateMasterKey()
    val key = MessageBackupKey(masterKey.serialize(), org.signal.libsignal.protocol.ServiceId.Aci.parseFromBinary(SELF_ACI.toByteArray()))

    return MessageBackup.validate(key, MessageBackup.Purpose.REMOTE_BACKUP, factory, importData.size.toLong())
  }

  /**
   * Imports the passed in frames and then exports them.
   *
   * It will do a comparison to assert that the import and export
   * are equal.
   */
  private fun importExport(vararg objects: Any) {
    val outputStream = ByteArrayOutputStream()
    val writer = EncryptedBackupWriter(
      key = SignalStore.svr().getOrCreateMasterKey().deriveBackupKey(),
      aci = SignalStore.account().aci!!,
      outputStream = outputStream,
      append = { mac -> outputStream.write(mac) }
    )

    writer.use {
      for (obj in objects) {
        when (obj) {
          is BackupInfo -> writer.write(obj)
          is AccountData -> writer.write(Frame(account = obj))
          is Recipient -> writer.write(Frame(recipient = obj))
          is Chat -> writer.write(Frame(chat = obj))
          is ChatItem -> writer.write(Frame(chatItem = obj))
          is Call -> writer.write(Frame(call = obj))
          is StickerPack -> writer.write(Frame(stickerPack = obj))
          else -> Assert.fail("invalid object $obj")
        }
      }
    }
    val importData = outputStream.toByteArray()
    outputFile(importData)
    BackupRepository.import(length = importData.size.toLong(), inputStreamFactory = { ByteArrayInputStream(importData) }, selfData = BackupRepository.SelfData(SELF_ACI, SELF_PNI, SELF_E164, SELF_PROFILE_KEY))

    val export = export()
    compare(importData, export)
  }

  private fun compare(import: ByteArray, export: ByteArray) {
    val selfData = BackupRepository.SelfData(SELF_ACI, SELF_PNI, SELF_E164, SELF_PROFILE_KEY)
    val framesImported = readAllFrames(import, selfData)
    val framesExported = readAllFrames(export, selfData)

    compareFrameList(framesImported, framesExported)
  }

  private fun compareFrameList(framesImported: List<Frame>, framesExported: List<Frame>) {
    val accountExported = ArrayList<AccountData>()
    val accountImported = ArrayList<AccountData>()
    val recipientsImported = ArrayList<Recipient>()
    val recipientsExported = ArrayList<Recipient>()
    val chatsImported = ArrayList<Chat>()
    val chatsExported = ArrayList<Chat>()
    val chatItemsImported = ArrayList<ChatItem>()
    val chatItemsExported = ArrayList<ChatItem>()
    val callsImported = ArrayList<Call>()
    val callsExported = ArrayList<Call>()
    val stickersImported = ArrayList<StickerPack>()
    val stickersExported = ArrayList<StickerPack>()

    for (f in framesImported) {
      when {
        f.account != null -> accountExported.add(f.account!!)
        f.recipient != null -> recipientsImported.add(f.recipient!!)
        f.chat != null -> chatsImported.add(f.chat!!)
        f.chatItem != null -> chatItemsImported.add(f.chatItem!!)
        f.call != null -> callsImported.add(f.call!!)
        f.stickerPack != null -> stickersImported.add(f.stickerPack!!)
      }
    }

    for (f in framesExported) {
      when {
        f.account != null -> accountImported.add(f.account!!)
        f.recipient != null -> recipientsExported.add(f.recipient!!)
        f.chat != null -> chatsExported.add(f.chat!!)
        f.chatItem != null -> chatItemsExported.add(f.chatItem!!)
        f.call != null -> callsExported.add(f.call!!)
        f.stickerPack != null -> stickersExported.add(f.stickerPack!!)
      }
    }
    prettyAssertEquals(accountImported, accountExported)
    prettyAssertEquals(recipientsImported, recipientsExported) { it.id }
    prettyAssertEquals(chatsImported, chatsExported) { it.id }
    prettyAssertEquals(chatItemsImported, chatItemsExported) { it.dateSent }
    prettyAssertEquals(callsImported, callsExported) { it.callId }
    prettyAssertEquals(stickersImported, stickersExported) { it.packId }
  }

  private fun <T> prettyAssertEquals(import: List<T>, export: List<T>) {
    Assert.assertEquals(import.size, export.size)
    import.zip(export).forEach { (a1, a2) ->
      if (a1 != a2) {
        Assert.fail("Items do not match: \n $a1 \n $a2")
      }
    }
  }

  private fun Random.trueWithProbability(prob: Float): Boolean {
    return nextFloat() < prob
  }

  private fun <T, R : Comparable<R>> prettyAssertEquals(import: List<T>, export: List<T>, selector: (T) -> R?) {
    if (import.size != export.size) {
      var msg = StringBuilder()
      for (i in import) {
        msg.append(i)
        msg.append("\n")
      }
      for (i in export) {
        msg.append(i)
        msg.append("\n")
      }
      Assert.fail(msg.toString())
    }
    Assert.assertEquals(import.size, export.size)
    val sortedImport = import.sortedBy(selector)
    val sortedExport = export.sortedBy(selector)

    prettyAssertEquals(sortedImport, sortedExport)
  }

  private fun readAllFrames(import: ByteArray, selfData: BackupRepository.SelfData): List<Frame> {
    val inputFactory = { ByteArrayInputStream(import) }
    val frameReader = EncryptedBackupReader(
      key = SignalStore.svr().getOrCreateMasterKey().deriveBackupKey(),
      aci = selfData.aci,
      streamLength = import.size.toLong(),
      dataStream = inputFactory
    )
    val frames = ArrayList<Frame>()
    while (frameReader.hasNext()) {
      frames.add(frameReader.next())
    }

    return frames
  }

  private fun outputFile(importBytes: ByteArray, resultBytes: ByteArray? = null) {
    grantPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)
    val dir = File(Environment.getExternalStorageDirectory(), "backup-tests")
    if (dir.mkdirs() || dir.exists()) {
      FileOutputStream(File(dir, testName.methodName + ".import")).use {
        it.write(importBytes)
        it.flush()
      }

      if (resultBytes != null) {
        FileOutputStream(File(dir, testName.methodName + ".result")).use {
          it.write(resultBytes)
          it.flush()
        }
      }
    }
  }

  private fun grantPermissions(vararg permissions: String?) {
    val auto: UiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
    for (perm in permissions) {
      auto.grantRuntimePermissionAsUser(InstrumentationRegistry.getInstrumentation().targetContext.packageName, perm, android.os.Process.myUserHandle())
    }
  }
}
