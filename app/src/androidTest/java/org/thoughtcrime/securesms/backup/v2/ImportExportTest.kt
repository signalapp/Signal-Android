/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2

import okio.ByteString.Companion.toByteString
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.thoughtcrime.securesms.backup.v2.proto.AccountData
import org.thoughtcrime.securesms.backup.v2.proto.BackupInfo
import org.thoughtcrime.securesms.backup.v2.proto.Call
import org.thoughtcrime.securesms.backup.v2.proto.Chat
import org.thoughtcrime.securesms.backup.v2.proto.ChatItem
import org.thoughtcrime.securesms.backup.v2.proto.Contact
import org.thoughtcrime.securesms.backup.v2.proto.DistributionList
import org.thoughtcrime.securesms.backup.v2.proto.Frame
import org.thoughtcrime.securesms.backup.v2.proto.Group
import org.thoughtcrime.securesms.backup.v2.proto.Recipient
import org.thoughtcrime.securesms.backup.v2.proto.ReleaseNotes
import org.thoughtcrime.securesms.backup.v2.proto.Self
import org.thoughtcrime.securesms.backup.v2.proto.StickerPack
import org.thoughtcrime.securesms.backup.v2.stream.EncryptedBackupReader
import org.thoughtcrime.securesms.backup.v2.stream.EncryptedBackupWriter
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.whispersystems.signalservice.api.push.DistributionId
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.subscriptions.SubscriberId
import org.whispersystems.signalservice.api.util.toByteArray
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.ArrayList
import java.util.UUID
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

    val defaultBackupInfo = BackupInfo(version = 1L, backupTimeMs = 123456L)
    val selfRecipient = Recipient(id = 1, self = Self())
    val releaseNotes = Recipient(id = 2, releaseNotes = ReleaseNotes())
    val standardAccountData = AccountData(
      profileKey = SELF_PROFILE_KEY.serialize().toByteString(),
      username = "testusername",
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

    /**
     * When using standardFrames you must start recipient ids at 3.
     */
    private val standardFrames = arrayOf(defaultBackupInfo, standardAccountData, selfRecipient, releaseNotes)
  }

  @Before
  fun setup() {
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
  fun individualRecipients() {
    importExport(
      *standardFrames,
      Recipient(
        id = 3,
        contact = Contact(
          aci = TestRecipientUtils.nextAci().toByteString(),
          pni = TestRecipientUtils.nextPni().toByteString(),
          username = "coolusername",
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
          username = "coolusername",
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
        username = "coolusername",
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
    import(
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
    val exported = export()
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
        id = 3,
        contact = Contact(
          aci = TestRecipientUtils.nextAci().toByteString(),
          pni = TestRecipientUtils.nextPni().toByteString(),
          username = "coolusername",
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

    importExport(
      *standardFrames,
      Recipient(
        id = 3,
        contact = Contact(
          aci = TestRecipientUtils.nextAci().toByteString(),
          pni = TestRecipientUtils.nextPni().toByteString(),
          username = "coolusername",
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
      *individualCalls.toArray(),
      *groupCalls.toArray()
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
    BackupRepository.import(length = importData.size.toLong(), inputStreamFactory = { ByteArrayInputStream(importData) }, selfData = BackupRepository.SelfData(SELF_ACI, SELF_PNI, SELF_E164, SELF_PROFILE_KEY))
  }

  /**
   * Export our current database as a backup.
   */
  private fun export() = BackupRepository.export()

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
    prettyAssertEquals(chatItemsImported, chatItemsExported)
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
}
