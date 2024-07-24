/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2

import android.Manifest
import android.app.UiAutomation
import android.content.Context
import android.os.Environment
import androidx.test.platform.app.InstrumentationRegistry
import okio.ByteString.Companion.toByteString
import org.junit.Assert
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.signal.core.util.Base64
import org.signal.core.util.test.getObjectDiff
import org.signal.libsignal.messagebackup.MessageBackup
import org.signal.libsignal.messagebackup.MessageBackupKey
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.thoughtcrime.securesms.backup.v2.proto.AccountData
import org.thoughtcrime.securesms.backup.v2.proto.AdHocCall
import org.thoughtcrime.securesms.backup.v2.proto.BackupInfo
import org.thoughtcrime.securesms.backup.v2.proto.BodyRange
import org.thoughtcrime.securesms.backup.v2.proto.Chat
import org.thoughtcrime.securesms.backup.v2.proto.ChatItem
import org.thoughtcrime.securesms.backup.v2.proto.ChatUpdateMessage
import org.thoughtcrime.securesms.backup.v2.proto.Contact
import org.thoughtcrime.securesms.backup.v2.proto.ContactAttachment
import org.thoughtcrime.securesms.backup.v2.proto.ContactMessage
import org.thoughtcrime.securesms.backup.v2.proto.DistributionList
import org.thoughtcrime.securesms.backup.v2.proto.DistributionListItem
import org.thoughtcrime.securesms.backup.v2.proto.ExpirationTimerChatUpdate
import org.thoughtcrime.securesms.backup.v2.proto.FilePointer
import org.thoughtcrime.securesms.backup.v2.proto.Frame
import org.thoughtcrime.securesms.backup.v2.proto.GiftBadge
import org.thoughtcrime.securesms.backup.v2.proto.Group
import org.thoughtcrime.securesms.backup.v2.proto.IndividualCall
import org.thoughtcrime.securesms.backup.v2.proto.LinkPreview
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
import org.thoughtcrime.securesms.backup.v2.stream.BackupExportWriter
import org.thoughtcrime.securesms.backup.v2.stream.EncryptedBackupReader
import org.thoughtcrime.securesms.backup.v2.stream.EncryptedBackupWriter
import org.thoughtcrime.securesms.backup.v2.stream.PlainTextBackupWriter
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.Util
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
@Ignore("Deprecated")
class ImportExportTest {
  companion object {
    /**
     * Output the frames as a plaintext .binproto for sharing tests
     *
     * This only seems to work on API 28 emulators, You can find the generated files
     * at /sdcard/backup-tests/
     * */
    val OUTPUT_FILES = false

    val SELF_ACI = ServiceId.ACI.from(UUID.fromString("77770000-b477-4f35-a824-d92987a63641"))
    val SELF_PNI = ServiceId.PNI.from(UUID.fromString("77771111-b014-41fb-bf73-05cb2ec52910"))
    const val SELF_E164 = "+10000000000"
    val SELF_PROFILE_KEY = ProfileKey(Random.nextBytes(32))
    val MASTER_KEY = Base64.decode("sHuBMP4ToZk4tcNU+S8eBUeCt8Am5EZnvuqTBJIR4Do")

    val defaultBackupInfo = BackupInfo(version = 1L, backupTimeMs = 123456L)
    val selfRecipient = Recipient(id = 1, self = Self())
    val myStory = Recipient(
      id = 2,
      distributionList = DistributionListItem(
        distributionId = DistributionId.MY_STORY.asUuid().toByteArray().toByteString(),
        distributionList = DistributionList(
          name = DistributionId.MY_STORY.toString(),
          privacyMode = DistributionList.PrivacyMode.ALL
        )
      )
    )
    val releaseNotes = Recipient(id = 3, releaseNotes = ReleaseNotes())
    val standardAccountData = AccountData(
      profileKey = SELF_PROFILE_KEY.serialize().toByteString(),
      username = "self.01",
      usernameLink = null,
      givenName = "Peter",
      familyName = "Parker",
      avatarUrlPath = "https://example.com/",
      donationSubscriberData = AccountData.SubscriberData(
        subscriberId = SubscriberId.generate().bytes.toByteString(),
        currencyCode = "USD",
        manuallyCancelled = true
      ),
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
      id = 4,
      contact = Contact(
        aci = TestRecipientUtils.nextAci().toByteString(),
        pni = TestRecipientUtils.nextPni().toByteString(),
        username = "cool.01",
        e164 = 141255501234,
        blocked = false,
        visibility = Contact.Visibility.VISIBLE,
        registered = Contact.Registered(),
        profileKey = TestRecipientUtils.generateProfileKey().toByteString(),
        profileSharing = true,
        profileGivenName = "Alexa",
        profileFamilyName = "Kim",
        hideStory = true
      )
    )

    /**
     * When using standardFrames you must start recipient ids at 4.
     */
    private val standardFrames = arrayOf(defaultBackupInfo, standardAccountData, selfRecipient, myStory, releaseNotes)
  }

  private val context: Context
    get() = InstrumentationRegistry.getInstrumentation().targetContext

  @JvmField
  @Rule
  var testName = TestName()

  @Before
  fun setup() {
    SignalStore.svr.setMasterKey(MasterKey(MASTER_KEY), "1234")
    SignalStore.account.setE164(SELF_E164)
    SignalStore.account.setAci(SELF_ACI)
    SignalStore.account.setPni(SELF_PNI)
    SignalStore.account.generateAciIdentityKeyIfNecessary()
    SignalStore.account.generatePniIdentityKeyIfNecessary()
  }

  @Test
  fun accountAndSelf() {
    importExport(*standardFrames)
  }

  @Test
  fun largeNumberOfRecipientsAndChats() {
    val recipients = ArrayList<Recipient>(5000)
    val chats = ArrayList<Chat>(5000)
    var id = 4L
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
            visibility = Contact.Visibility.VISIBLE,
            registered = Contact.Registered(),
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
              storySendMode = Group.StorySendMode.ENABLED
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
    val numIndividualRecipients = 1000
    val numIndividualMessages = 500
    val numGroupMessagesPerPerson = 200

    val random = Random(1516)

    val recipients = ArrayList<Recipient>(1010)
    val chats = ArrayList<Chat>(1010)
    var id = 3L
    for (i in 0 until numIndividualRecipients) {
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
            visibility = if (random.trueWithProbability(0.1f)) Contact.Visibility.HIDDEN else Contact.Visibility.VISIBLE,
            registered = Contact.Registered(),
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
              storySendMode = if (random.trueWithProbability(0.9f)) Group.StorySendMode.ENABLED else Group.StorySendMode.DISABLED
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

    exportFrames(
      *standardFrames,
      *recipients.toArray(),
      *chatItems.toArray()
    )
  }

  @Test
  fun individualRecipients() {
    importExport(
      *standardFrames,
      Recipient(
        id = 4,
        contact = Contact(
          aci = TestRecipientUtils.nextAci().toByteString(),
          pni = TestRecipientUtils.nextPni().toByteString(),
          username = "cool.01",
          e164 = 141255501234,
          blocked = true,
          visibility = Contact.Visibility.VISIBLE,
          registered = Contact.Registered(),
          profileKey = TestRecipientUtils.generateProfileKey().toByteString(),
          profileSharing = true,
          profileGivenName = "Alexa",
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
          e164 = 141255501235,
          blocked = true,
          visibility = Contact.Visibility.HIDDEN,
          notRegistered = Contact.NotRegistered(unregisteredTimestamp = 1234568927398L),
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
        id = 4,
        group = Group(
          masterKey = TestRecipientUtils.generateGroupMasterKey().toByteString(),
          whitelisted = true,
          hideStory = true,
          storySendMode = Group.StorySendMode.ENABLED,
          snapshot = Group.GroupSnapshot(
            title = Group.GroupAttributeBlob(title = "Group Cool"),
            description = Group.GroupAttributeBlob(descriptionText = "Description"),
            version = 10,
            disappearingMessagesTimer = Group.GroupAttributeBlob(disappearingMessagesDuration = 1500000)
          )
        )
      ),
      Recipient(
        id = 5,
        group = Group(
          masterKey = TestRecipientUtils.generateGroupMasterKey().toByteString(),
          whitelisted = false,
          hideStory = false,
          storySendMode = Group.StorySendMode.DEFAULT,
          snapshot = Group.GroupSnapshot(
            title = Group.GroupAttributeBlob(title = "Group Cool"),
            description = Group.GroupAttributeBlob(descriptionText = "Description"),
            version = 10,
            disappearingMessagesTimer = Group.GroupAttributeBlob(disappearingMessagesDuration = 1500000)
          )
        )
      )
    )
  }

  @Test
  fun distributionListRecipients() {
    importExport(
      *standardFrames,
      Recipient(
        id = 4,
        contact = Contact(
          aci = TestRecipientUtils.nextAci().toByteString(),
          pni = TestRecipientUtils.nextPni().toByteString(),
          username = "cool.01",
          e164 = 141255501234,
          blocked = true,
          visibility = Contact.Visibility.HIDDEN,
          registered = Contact.Registered(),
          profileKey = TestRecipientUtils.generateProfileKey().toByteString(),
          profileSharing = true,
          profileGivenName = "Alexa",
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
          e164 = 141255501235,
          blocked = true,
          visibility = Contact.Visibility.HIDDEN,
          registered = Contact.Registered(),
          profileKey = TestRecipientUtils.generateProfileKey().toByteString(),
          profileSharing = true,
          profileGivenName = "Peter",
          profileFamilyName = "Kim",
          hideStory = true
        )
      ),
      Recipient(
        id = 6,
        contact = Contact(
          aci = null,
          pni = null,
          username = null,
          e164 = 141255501236,
          blocked = true,
          visibility = Contact.Visibility.HIDDEN,
          registered = Contact.Registered(),
          profileKey = TestRecipientUtils.generateProfileKey().toByteString(),
          profileSharing = true,
          profileGivenName = "Father",
          profileFamilyName = "Kim",
          hideStory = true
        )
      ),
      Recipient(
        id = 7,
        distributionList = DistributionListItem(
          distributionId = DistributionId.create().asUuid().toByteArray().toByteString(),
          distributionList = DistributionList(
            name = "Kim Family",
            allowReplies = true,
            privacyMode = DistributionList.PrivacyMode.ONLY_WITH,
            memberRecipientIds = listOf(3, 4, 5)
          )
        )
      )
    )
  }

  @Test
  fun deletedDistributionList() {
    val alexa = Recipient(
      id = 4,
      contact = Contact(
        aci = TestRecipientUtils.nextAci().toByteString(),
        pni = TestRecipientUtils.nextPni().toByteString(),
        username = "cool.01",
        e164 = 141255501234,
        blocked = true,
        visibility = Contact.Visibility.HIDDEN,
        registered = Contact.Registered(),
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
        distributionList = DistributionListItem(
          distributionId = DistributionId.create().asUuid().toByteArray().toByteString(),
          deletionTimestamp = 12345L
        )
      )
    )
    import(importData)
    val exported = BackupRepository.debugExport()
    val expected = exportFrames(
      *standardFrames,
      alexa
    )

    compare(expected, exported)
  }

  @Test
  fun chatThreads() {
    importExport(
      *standardFrames,
      Recipient(
        id = 4,
        contact = Contact(
          aci = TestRecipientUtils.nextAci().toByteString(),
          pni = TestRecipientUtils.nextPni().toByteString(),
          username = "cool.01",
          e164 = 141255501234,
          blocked = false,
          visibility = Contact.Visibility.VISIBLE,
          registered = Contact.Registered(),
          profileKey = TestRecipientUtils.generateProfileKey().toByteString(),
          profileSharing = true,
          profileGivenName = "Alexa",
          profileFamilyName = "Kim",
          hideStory = true
        )
      ),
      Recipient(
        id = 5,
        group = Group(
          masterKey = TestRecipientUtils.generateGroupMasterKey().toByteString(),
          whitelisted = true,
          hideStory = true,
          storySendMode = Group.StorySendMode.DEFAULT
        )
      ),
      Chat(
        id = 1,
        recipientId = 4,
        archived = true,
        pinnedOrder = 1,
        expirationTimerMs = 1.days.inWholeMilliseconds,
        muteUntilMs = System.currentTimeMillis(),
        markedUnread = true,
        dontNotifyForMentionsIfMuted = true
      )
    )
  }

  @Test
  fun individualCalls() {
    val individualCalls = ArrayList<ChatItem>()
    val states = arrayOf(IndividualCall.State.ACCEPTED, IndividualCall.State.NOT_ACCEPTED, IndividualCall.State.MISSED, IndividualCall.State.MISSED_NOTIFICATION_PROFILE)
    val oldStates = arrayOf(IndividualCall.State.ACCEPTED, IndividualCall.State.MISSED)
    val types = arrayOf(IndividualCall.Type.VIDEO_CALL, IndividualCall.Type.AUDIO_CALL)
    val directions = arrayOf(IndividualCall.Direction.OUTGOING, IndividualCall.Direction.INCOMING)
    var sentTime = 0L
    var callId = 1L
    val startedAci = TestRecipientUtils.nextAci().toByteString()
    for (state in states) {
      for (type in types) {
        for (direction in directions) {
          // With call id
          individualCalls.add(
            ChatItem(
              chatId = 1,
              authorId = selfRecipient.id,
              dateSent = sentTime++,
              sms = false,
              directionless = ChatItem.DirectionlessMessageDetails(),
              updateMessage = ChatUpdateMessage(
                individualCall = IndividualCall(
                  callId = callId++,
                  type = type,
                  state = state,
                  direction = direction
                )
              )
            )
          )
        }
      }
    }
    for (state in oldStates) {
      for (type in types) {
        for (direction in directions) {
          if (state == IndividualCall.State.MISSED && direction == IndividualCall.Direction.OUTGOING) continue
          // Without call id
          individualCalls.add(
            ChatItem(
              chatId = 1,
              authorId = selfRecipient.id,
              dateSent = sentTime++,
              sms = false,
              directionless = ChatItem.DirectionlessMessageDetails(),
              updateMessage = ChatUpdateMessage(
                individualCall = IndividualCall(
                  callId = null,
                  type = type,
                  state = state,
                  direction = direction
                )
              )
            )
          )
        }
      }
    }
    importExport(
      *standardFrames,
      Recipient(
        id = 4,
        contact = Contact(
          aci = startedAci,
          pni = TestRecipientUtils.nextPni().toByteString(),
          username = "cool.01",
          e164 = 141255501234,
          blocked = false,
          visibility = Contact.Visibility.VISIBLE,
          registered = Contact.Registered(),
          profileKey = TestRecipientUtils.generateProfileKey().toByteString(),
          profileSharing = true,
          profileGivenName = "Alexa",
          profileFamilyName = "Kim",
          hideStory = true
        )
      ),
      Recipient(
        id = 5,
        group = Group(
          masterKey = TestRecipientUtils.generateGroupMasterKey().toByteString(),
          whitelisted = true,
          hideStory = true,
          storySendMode = Group.StorySendMode.DEFAULT
        )
      ),
      Chat(
        id = 1,
        recipientId = 4,
        archived = true,
        pinnedOrder = 1,
        expirationTimerMs = 1.days.inWholeMilliseconds,
        muteUntilMs = System.currentTimeMillis(),
        markedUnread = true,
        dontNotifyForMentionsIfMuted = true
      ),
      *individualCalls.toArray()
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
      expireStartDate = 0,
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
    val exported = BackupRepository.debugExport()
    val expected = exportFrames(
      *standardFrames,
      alice,
      chat,
      expirationNotStarted
    )
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
            )
          )
        )
      )
    )
  }

  @Test
  fun linkPreviewMessages() {
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
            ),
            linkPreview = listOf(
              LinkPreview(
                url = "https://signal.org/",
                title = "Signal Messenger: Speak Freely",
                description = "Say \"hello\" to a different messaging experience. An unexpected focus on privacy, combined with all the features you expect.",
                date = System.currentTimeMillis(),
                image = FilePointer(
                  invalidAttachmentLocator = FilePointer.InvalidAttachmentLocator(),
                  contentType = "image/png",
                  width = 100,
                  height = 200,
                  caption = "Love this cool picture! Too bad u cant download it",
                  incrementalMacChunkSize = 0
                )
              )
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
            ),
            linkPreview = listOf(
              LinkPreview(
                url = "https://signal.org/",
                title = "Signal Messenger: Speak Freely",
                description = "Say \"hello\" to a different messaging experience. An unexpected focus on privacy, combined with all the features you expect.",
                date = System.currentTimeMillis(),
                image = FilePointer(
                  invalidAttachmentLocator = FilePointer.InvalidAttachmentLocator(),
                  contentType = "image/png",
                  width = 100,
                  height = 200,
                  caption = "Love this cool picture! Too bad u cant download it",
                  incrementalMacChunkSize = 0
                )
              )
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
  fun contactMessageWithAllFields() {
    importExport(
      *standardFrames,
      alice,
      buildChat(alice, 1),
      ChatItem(
        chatId = 1,
        authorId = selfRecipient.id,
        dateSent = 150L,
        sms = false,
        outgoing = ChatItem.OutgoingMessageDetails(
          sendStatus = listOf(SendStatus(alice.id, deliveryStatus = SendStatus.Status.READ, lastStatusUpdateTimestamp = -1))
        ),
        contactMessage = ContactMessage(
          contact = listOf(
            ContactAttachment(
              name = ContactAttachment.Name(
                givenName = "Given",
                familyName = "Family",
                prefix = "Prefix",
                suffix = "Suffix",
                middleName = "Middle",
                displayName = "Display Name"
              ),
              organization = "Organization",
              email = listOf(
                ContactAttachment.Email(
                  value_ = "coolemail@gmail.com",
                  label = "Label",
                  type = ContactAttachment.Email.Type.HOME
                ),
                ContactAttachment.Email(
                  value_ = "coolemail2@gmail.com",
                  label = "Label2",
                  type = ContactAttachment.Email.Type.MOBILE
                )
              ),
              address = listOf(
                ContactAttachment.PostalAddress(
                  type = ContactAttachment.PostalAddress.Type.HOME,
                  label = "Label",
                  street = "Street",
                  pobox = "POBOX",
                  neighborhood = "Neighborhood",
                  city = "City",
                  region = "Region",
                  postcode = "15213",
                  country = "United States"
                )
              ),
              number = listOf(
                ContactAttachment.Phone(
                  value_ = "+14155551234",
                  type = ContactAttachment.Phone.Type.CUSTOM,
                  label = "Label"
                )
              ),
              avatar = FilePointer(
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
              )
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

  @Test
  fun giftBadgeMessage() {
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
        giftBadge = GiftBadge(
          receiptCredentialPresentation = Util.getSecretBytes(32).toByteString(),
          state = GiftBadge.State.OPENED
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
        giftBadge = GiftBadge(
          receiptCredentialPresentation = Util.getSecretBytes(32).toByteString(),
          state = GiftBadge.State.FAILED
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
        giftBadge = GiftBadge(
          receiptCredentialPresentation = Util.getSecretBytes(32).toByteString(),
          state = GiftBadge.State.REDEEMED
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
        giftBadge = GiftBadge(
          receiptCredentialPresentation = Util.getSecretBytes(32).toByteString(),
          state = GiftBadge.State.UNOPENED
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
      dontNotifyForMentionsIfMuted = false
    )
  }

  /**
   * Export passed in frames as a backup. Does not automatically include
   * any standard frames (e.g. backup header).
   */
  private fun exportFrames(vararg objects: Any): ByteArray {
    outputBinProto(*objects)
    val outputStream = ByteArrayOutputStream()
    val writer = EncryptedBackupWriter(
      key = SignalStore.svr.getOrCreateMasterKey().deriveBackupKey(),
      aci = SignalStore.account.aci!!,
      outputStream = outputStream,
      append = { mac -> outputStream.write(mac) }
    )

    writer.use {
      writer.writeFrames(*objects)
    }
    return outputStream.toByteArray()
  }

  private fun import(importData: ByteArray) {
    BackupRepository.import(length = importData.size.toLong(), inputStreamFactory = { ByteArrayInputStream(importData) }, selfData = BackupRepository.SelfData(SELF_ACI, SELF_PNI, SELF_E164, SELF_PROFILE_KEY))
  }

  private fun validate(importData: ByteArray): MessageBackup.ValidationResult {
    val factory = { ByteArrayInputStream(importData) }
    val masterKey = SignalStore.svr.getOrCreateMasterKey()
    val key = MessageBackupKey(masterKey.serialize(), org.signal.libsignal.protocol.ServiceId.Aci.parseFromBinary(SELF_ACI.toByteArray()))

    return MessageBackup.validate(key, MessageBackup.Purpose.REMOTE_BACKUP, factory, importData.size.toLong())
  }

  /**
   * Given some [Frame]s, this will do the following:
   *
   * 1. Write the frames using an [EncryptedBackupWriter] and keep the result in memory (A).
   * 2. Import those frames back into the local database.
   * 3. Export the state of the local database and keep the result in memory (B).
   * 4. Assert that (A) and (B) are identical. Or, in other words, assert that importing and exporting again results in the original backup data.
   */
  private fun importExport(vararg objects: Any) {
    val originalBackupData = exportFrames(*objects)

    import(originalBackupData)

    val generatedBackupData = BackupRepository.debugExport()
    compare(originalBackupData, generatedBackupData)
  }

  private fun BackupExportWriter.writeFrames(vararg objects: Any) {
    for (obj in objects) {
      when (obj) {
        is BackupInfo -> write(obj)
        is AccountData -> write(Frame(account = obj))
        is Recipient -> write(Frame(recipient = obj))
        is Chat -> write(Frame(chat = obj))
        is ChatItem -> write(Frame(chatItem = obj))
        is AdHocCall -> write(Frame(adHocCall = obj))
        is StickerPack -> write(Frame(stickerPack = obj))
        else -> Assert.fail("invalid object $obj")
      }
    }
  }

  private fun outputBinProto(vararg objects: Any) {
    if (!OUTPUT_FILES) return

    val outputStream = ByteArrayOutputStream()
    val plaintextWriter = PlainTextBackupWriter(
      outputStream = outputStream
    )

    plaintextWriter.use {
      it.writeFrames(*objects)
    }

    grantPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)
    val dir = File(Environment.getExternalStorageDirectory(), "backup-tests")
    if (dir.mkdirs() || dir.exists()) {
      FileOutputStream(File(dir, testName.methodName + ".binproto")).use {
        it.write(outputStream.toByteArray())
        it.flush()
      }
    }
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
    val callsImported = ArrayList<AdHocCall>()
    val callsExported = ArrayList<AdHocCall>()
    val stickersImported = ArrayList<StickerPack>()
    val stickersExported = ArrayList<StickerPack>()

    for (f in framesImported) {
      when {
        f.account != null -> accountExported.add(f.account!!)
        f.recipient != null -> recipientsImported.add(f.recipient!!)
        f.chat != null -> chatsImported.add(f.chat!!)
        f.chatItem != null -> chatItemsImported.add(f.chatItem!!)
        f.adHocCall != null -> callsImported.add(f.adHocCall!!)
        f.stickerPack != null -> stickersImported.add(f.stickerPack!!)
      }
    }

    for (f in framesExported) {
      when {
        f.account != null -> accountImported.add(f.account!!)
        f.recipient != null -> {
          val frameRecipient = f.recipient!!
          if (frameRecipient.distributionList != null && frameRecipient.distributionList!!.distributionId == DistributionId.MY_STORY.asUuid().toByteArray().toByteString()) {
            recipientsExported.add(frameRecipient.copy(distributionList = frameRecipient.distributionList!!.copyWithoutMembers()))
          } else {
            recipientsExported.add(f.recipient!!)
          }
        }
        f.chat != null -> chatsExported.add(f.chat!!)
        f.chatItem != null -> chatItemsExported.add(f.chatItem!!)
        f.adHocCall != null -> callsExported.add(f.adHocCall!!)
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

  private fun DistributionListItem.copyWithoutMembers(): DistributionListItem {
    return this.copy(
      distributionList = this.distributionList?.copy(
        memberRecipientIds = emptyList()
      )
    )
  }

  private inline fun <reified T : Any> prettyAssertEquals(import: List<T>, export: List<T>) {
    Assert.assertEquals(import.size, export.size)
    import.zip(export).forEach { (a1, a2) ->
      if (a1 != a2) {
        Assert.fail("Items do not match:\n\n-- Pretty diff\n${getObjectDiff(a1, a2)}\n-- Full objects\n$a1\n$a2")
      }
    }
  }

  private fun Random.trueWithProbability(prob: Float): Boolean {
    return nextFloat() < prob
  }

  private inline fun <reified T : Any, R : Comparable<R>> prettyAssertEquals(import: List<T>, export: List<T>, crossinline selector: (T) -> R?) {
    if (import.size != export.size) {
      val msg = StringBuilder()
      msg.append("There's a different number of items in the lists!\n\n")

      msg.append("Imported:\n")
      for (i in import) {
        msg.append(i)
        msg.append("\n")
      }
      if (import.isEmpty()) {
        msg.append("<None>")
      }
      msg.append("\n")
      msg.append("Exported:\n")
      for (i in export) {
        msg.append(i)
        msg.append("\n")
      }
      if (export.isEmpty()) {
        msg.append("<None>")
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
      key = SignalStore.svr.getOrCreateMasterKey().deriveBackupKey(),
      aci = selfData.aci,
      length = import.size.toLong(),
      dataStream = inputFactory
    )
    val frames = ArrayList<Frame>()
    while (frameReader.hasNext()) {
      frames.add(frameReader.next())
    }

    return frames
  }

  private fun grantPermissions(vararg permissions: String?) {
    if (!OUTPUT_FILES) return

    val auto: UiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
    for (perm in permissions) {
      auto.grantRuntimePermissionAsUser(InstrumentationRegistry.getInstrumentation().targetContext.packageName, perm, android.os.Process.myUserHandle())
    }
  }
}
