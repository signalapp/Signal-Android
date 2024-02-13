/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2

import android.content.ContentValues
import android.database.Cursor
import androidx.core.content.contentValuesOf
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.Before
import org.junit.Test
import org.signal.core.util.Hex
import org.signal.core.util.SqlUtil
import org.signal.core.util.insertInto
import org.signal.core.util.readToList
import org.signal.core.util.readToSingleObject
import org.signal.core.util.requireBlob
import org.signal.core.util.requireLong
import org.signal.core.util.requireString
import org.signal.core.util.select
import org.signal.core.util.toInt
import org.signal.core.util.withinTransaction
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.thoughtcrime.securesms.backup.v2.database.clearAllDataForBackupRestore
import org.thoughtcrime.securesms.database.CallTable
import org.thoughtcrime.securesms.database.EmojiSearchTable
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.MessageTypes
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.keyvalue.PhoneNumberPrivacyValues
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.mms.QuoteModel
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.subscription.Subscriber
import org.thoughtcrime.securesms.testing.assertIs
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import org.whispersystems.signalservice.api.push.ServiceId.PNI
import org.whispersystems.signalservice.api.subscriptions.SubscriberId
import java.io.ByteArrayInputStream
import java.util.UUID
import kotlin.random.Random

typealias DatabaseData = Map<String, List<Map<String, Any?>>>

class BackupTest {
  companion object {
    val SELF_ACI = ACI.from(UUID.fromString("77770000-b477-4f35-a824-d92987a63641"))
    val SELF_PNI = PNI.from(UUID.fromString("77771111-b014-41fb-bf73-05cb2ec52910"))
    const val SELF_E164 = "+10000000000"
    val SELF_PROFILE_KEY = ProfileKey(Random.nextBytes(32))

    val ALICE_ACI = ACI.from(UUID.fromString("aaaa0000-5a76-47fa-a98a-7e72c948a82e"))
    val ALICE_PNI = PNI.from(UUID.fromString("aaaa1111-c960-4f6c-8385-671ad2ffb999"))
    val ALICE_E164 = "+12222222222"

    /** Columns that we don't need to check equality of */
    private val IGNORED_COLUMNS: Map<String, Set<String>> = mapOf(
      RecipientTable.TABLE_NAME to setOf(RecipientTable.STORAGE_SERVICE_ID),
      MessageTable.TABLE_NAME to setOf(MessageTable.FROM_DEVICE_ID)
    )

    /** Tables we don't need to check equality of */
    private val IGNORED_TABLES: Set<String> = setOf(
      EmojiSearchTable.TABLE_NAME,
      "sqlite_sequence",
      "message_fts_data",
      "message_fts_idx",
      "message_fts_docsize"
    )
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
  fun emptyDatabase() {
    backupTest { }
  }

  @Test
  fun noteToSelf() {
    backupTest {
      individualChat(aci = SELF_ACI, givenName = "Note to Self") {
        standardMessage(outgoing = true, body = "A")
        standardMessage(outgoing = true, body = "B")
        standardMessage(outgoing = true, body = "C")
      }
    }
  }

  @Test
  fun individualChat() {
    backupTest {
      individualChat(aci = ALICE_ACI, givenName = "Alice") {
        val m1 = standardMessage(outgoing = true, body = "Outgoing 1")
        val m2 = standardMessage(outgoing = false, body = "Incoming 1", read = true)
        standardMessage(outgoing = true, body = "Outgoing 2", quotes = m2)
        standardMessage(outgoing = false, body = "Incoming 2", quotes = m1, quoteTargetMissing = true, read = false)
        standardMessage(outgoing = true, body = "Outgoing 3, with mention", randomMention = true)
        standardMessage(outgoing = false, body = "Incoming 3, with style", read = false, randomStyling = true)
        remoteDeletedMessage(outgoing = true)
        remoteDeletedMessage(outgoing = false)
      }
    }
  }

  @Test
  fun individualRecipients() {
    backupTest {
      // Comprehensive example
      individualRecipient(
        aci = ALICE_ACI,
        pni = ALICE_PNI,
        e164 = ALICE_E164,
        givenName = "Alice",
        familyName = "Smith",
        username = "alice.99",
        hidden = false,
        registeredState = RecipientTable.RegisteredState.REGISTERED,
        profileKey = ProfileKey(Random.nextBytes(32)),
        profileSharing = true,
        hideStory = false
      )

      // Trying to get coverage of all the various values
      individualRecipient(aci = ACI.from(UUID.randomUUID()), registeredState = RecipientTable.RegisteredState.NOT_REGISTERED)
      individualRecipient(aci = ACI.from(UUID.randomUUID()), registeredState = RecipientTable.RegisteredState.UNKNOWN)
      individualRecipient(pni = PNI.from(UUID.randomUUID()))
      individualRecipient(e164 = "+15551234567")
      individualRecipient(aci = ACI.from(UUID.randomUUID()), givenName = "Bob")
      individualRecipient(aci = ACI.from(UUID.randomUUID()), familyName = "Smith")
      individualRecipient(aci = ACI.from(UUID.randomUUID()), profileSharing = false)
      individualRecipient(aci = ACI.from(UUID.randomUUID()), hideStory = true)
      individualRecipient(aci = ACI.from(UUID.randomUUID()), hidden = true)
    }
  }

  @Test
  fun individualCallLogs() {
    backupTest {
      val aliceId = individualRecipient(
        aci = ALICE_ACI,
        pni = ALICE_PNI,
        e164 = ALICE_E164,
        givenName = "Alice",
        familyName = "Smith",
        username = "alice.99",
        hidden = false,
        registeredState = RecipientTable.RegisteredState.REGISTERED,
        profileKey = ProfileKey(Random.nextBytes(32)),
        profileSharing = true,
        hideStory = false
      )
      insertOneToOneCallVariations(1, 1, aliceId)
    }
  }

  private fun insertOneToOneCallVariations(callId: Long, timestamp: Long, id: RecipientId): Long {
    val directions = arrayOf(CallTable.Direction.INCOMING, CallTable.Direction.OUTGOING)
    val callTypes = arrayOf(CallTable.Type.AUDIO_CALL, CallTable.Type.VIDEO_CALL)
    val events = arrayOf(
      CallTable.Event.MISSED,
      CallTable.Event.OUTGOING_RING,
      CallTable.Event.ONGOING,
      CallTable.Event.ACCEPTED,
      CallTable.Event.NOT_ACCEPTED
    )
    var callTimestamp: Long = timestamp
    var currentCallId = callId
    for (direction in directions) {
      for (event in events) {
        for (type in callTypes) {
          insertOneToOneCall(callId = currentCallId, callTimestamp, id, type, direction, event)
          callTimestamp++
          currentCallId++
        }
      }
    }

    return currentCallId
  }

  private fun insertOneToOneCall(callId: Long, timestamp: Long, peer: RecipientId, type: CallTable.Type, direction: CallTable.Direction, event: CallTable.Event) {
    val messageType: Long = CallTable.Call.getMessageType(type, direction, event)

    SignalDatabase.rawDatabase.withinTransaction {
      val recipient = Recipient.resolved(peer)
      val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(recipient)
      val outgoing = direction == CallTable.Direction.OUTGOING

      val messageValues = contentValuesOf(
        MessageTable.FROM_RECIPIENT_ID to if (outgoing) Recipient.self().id.serialize() else peer.serialize(),
        MessageTable.FROM_DEVICE_ID to 1,
        MessageTable.TO_RECIPIENT_ID to if (outgoing) peer.serialize() else Recipient.self().id.serialize(),
        MessageTable.DATE_RECEIVED to timestamp,
        MessageTable.DATE_SENT to timestamp,
        MessageTable.READ to 1,
        MessageTable.TYPE to messageType,
        MessageTable.THREAD_ID to threadId
      )

      val messageId = SignalDatabase.rawDatabase.insert(MessageTable.TABLE_NAME, null, messageValues)

      val values = contentValuesOf(
        CallTable.CALL_ID to callId,
        CallTable.MESSAGE_ID to messageId,
        CallTable.PEER to peer.serialize(),
        CallTable.TYPE to CallTable.Type.serialize(type),
        CallTable.DIRECTION to CallTable.Direction.serialize(direction),
        CallTable.EVENT to CallTable.Event.serialize(event),
        CallTable.TIMESTAMP to timestamp
      )

      SignalDatabase.rawDatabase.insert(CallTable.TABLE_NAME, null, values)

      SignalDatabase.threads.update(threadId, true)
    }
  }

  @Test
  fun accountData() {
    val context = ApplicationDependencies.getApplication()

    backupTest(validateKeyValue = true) {
      val self = Recipient.self()

      // TODO note-to-self archived
      // TODO note-to-self unread

      SignalStore.account().setAci(SELF_ACI)
      SignalStore.account().setPni(SELF_PNI)
      SignalStore.account().setE164(SELF_E164)
      SignalStore.account().generateAciIdentityKeyIfNecessary()
      SignalStore.account().generatePniIdentityKeyIfNecessary()

      SignalDatabase.recipients.setProfileKey(self.id, ProfileKey(Random.nextBytes(32)))
      SignalDatabase.recipients.setProfileName(self.id, ProfileName.fromParts("Peter", "Parker"))
      SignalDatabase.recipients.setProfileAvatar(self.id, "https://example.com/")

      SignalStore.donationsValues().markUserManuallyCancelled()
      SignalStore.donationsValues().setSubscriber(Subscriber(SubscriberId.generate(), "USD"))
      SignalStore.donationsValues().setDisplayBadgesOnProfile(false)

      SignalStore.phoneNumberPrivacy().phoneNumberDiscoverabilityMode = PhoneNumberPrivacyValues.PhoneNumberDiscoverabilityMode.NOT_DISCOVERABLE
      SignalStore.phoneNumberPrivacy().phoneNumberSharingMode = PhoneNumberPrivacyValues.PhoneNumberSharingMode.NOBODY

      SignalStore.settings().isLinkPreviewsEnabled = false
      SignalStore.settings().isPreferSystemContactPhotos = true
      SignalStore.settings().universalExpireTimer = 42
      SignalStore.settings().setKeepMutedChatsArchived(true)

      SignalStore.storyValues().viewedReceiptsEnabled = false
      SignalStore.storyValues().userHasViewedOnboardingStory = true
      SignalStore.storyValues().isFeatureDisabled = false
      SignalStore.storyValues().userHasBeenNotifiedAboutStories = true
      SignalStore.storyValues().userHasSeenGroupStoryEducationSheet = true

      SignalStore.emojiValues().reactions = listOf("a", "b", "c")

      TextSecurePreferences.setTypingIndicatorsEnabled(context, false)
      TextSecurePreferences.setReadReceiptsEnabled(context, false)
      TextSecurePreferences.setShowUnidentifiedDeliveryIndicatorsEnabled(context, true)
    }

    // Have to check TextSecurePreferences ourselves, since they're not in a database
    TextSecurePreferences.isTypingIndicatorsEnabled(context) assertIs false
    TextSecurePreferences.isReadReceiptsEnabled(context) assertIs false
    TextSecurePreferences.isShowUnidentifiedDeliveryIndicatorsEnabled(context) assertIs true
  }

  /**
   * Sets up the database, then executes your setup code, then compares snapshots of the database
   * before an after an import to ensure that no data was lost/changed.
   *
   * @param validateKeyValue If true, this will also validate the KeyValueDatabase. You only want to do this if you
   *    intend on setting most of the values. Otherwise stuff tends to not match since values are lazily written.
   */
  private fun backupTest(validateKeyValue: Boolean = false, content: () -> Unit) {
    // Under normal circumstances, My Story ends up being the first recipient in the table, and is added automatically.
    // This screws with the tests by offsetting all the recipientIds in the initial state.
    // Easiest way to get around this is to make the DB a true clean slate by clearing everything.
    // (We only really need to clear Recipient/dlists, but doing everything to be consistent.)
    SignalDatabase.distributionLists.clearAllDataForBackupRestore()
    SignalDatabase.recipients.clearAllDataForBackupRestore()
    SignalDatabase.messages.clearAllDataForBackupRestore()
    SignalDatabase.threads.clearAllDataForBackupRestore()

    // Again, for comparison purposes, because we always import self first, we want to ensure it's the first item
    // in the table when we export.
    individualRecipient(
      aci = SELF_ACI,
      pni = SELF_PNI,
      e164 = SELF_E164,
      profileKey = SELF_PROFILE_KEY,
      profileSharing = true
    )

    content()

    val startingMainData: DatabaseData = SignalDatabase.rawDatabase.readAllContents()
    val startingKeyValueData: DatabaseData = if (validateKeyValue) SignalDatabase.rawDatabase.readAllContents() else emptyMap()

    val exported: ByteArray = BackupRepository.export()
    BackupRepository.import(length = exported.size.toLong(), inputStreamFactory = { ByteArrayInputStream(exported) }, selfData = BackupRepository.SelfData(SELF_ACI, SELF_PNI, SELF_E164, SELF_PROFILE_KEY))

    val endingData: DatabaseData = SignalDatabase.rawDatabase.readAllContents()
    val endingKeyValueData: DatabaseData = if (validateKeyValue) SignalDatabase.rawDatabase.readAllContents() else emptyMap()

    assertDatabaseMatches(startingMainData, endingData)
    assertDatabaseMatches(startingKeyValueData, endingKeyValueData)
  }

  private fun individualChat(aci: ACI, givenName: String, familyName: String? = null, init: IndividualChatCreator.() -> Unit) {
    val recipientId = individualRecipient(aci = aci, givenName = givenName, familyName = familyName, profileSharing = true)

    val threadId: Long = SignalDatabase.threads.getOrCreateThreadIdFor(recipientId, false)

    IndividualChatCreator(SignalDatabase.rawDatabase, recipientId, threadId).init()

    SignalDatabase.threads.update(threadId, false)
  }

  private fun individualRecipient(
    aci: ACI? = null,
    pni: PNI? = null,
    e164: String? = null,
    givenName: String? = null,
    familyName: String? = null,
    username: String? = null,
    hidden: Boolean = false,
    registeredState: RecipientTable.RegisteredState = RecipientTable.RegisteredState.UNKNOWN,
    profileKey: ProfileKey? = null,
    profileSharing: Boolean = false,
    hideStory: Boolean = false
  ): RecipientId {
    check(aci != null || pni != null || e164 != null)

    val recipientId = SignalDatabase.recipients.getAndPossiblyMerge(aci, pni, e164, pniVerified = true, changeSelf = true)

    if (givenName != null || familyName != null) {
      SignalDatabase.recipients.setProfileName(recipientId, ProfileName.fromParts(givenName, familyName))
    }

    if (username != null) {
      SignalDatabase.recipients.setUsername(recipientId, username)
    }

    if (registeredState == RecipientTable.RegisteredState.REGISTERED) {
      SignalDatabase.recipients.markRegistered(recipientId, aci ?: pni!!)
    } else if (registeredState == RecipientTable.RegisteredState.NOT_REGISTERED) {
      SignalDatabase.recipients.markUnregistered(recipientId)
    }

    if (profileKey != null) {
      SignalDatabase.recipients.setProfileKey(recipientId, profileKey)
    }

    SignalDatabase.recipients.setProfileSharing(recipientId, profileSharing)
    SignalDatabase.recipients.setHideStory(recipientId, hideStory)

    if (hidden) {
      SignalDatabase.recipients.markHidden(recipientId)
    }

    return recipientId
  }

  private inner class IndividualChatCreator(
    private val db: SQLiteDatabase,
    private val recipientId: RecipientId,
    private val threadId: Long
  ) {
    fun standardMessage(
      outgoing: Boolean,
      sentTimestamp: Long = System.currentTimeMillis(),
      receivedTimestamp: Long = if (outgoing) sentTimestamp else sentTimestamp + 1,
      serverTimestamp: Long = sentTimestamp,
      body: String? = null,
      read: Boolean = true,
      quotes: Long? = null,
      quoteTargetMissing: Boolean = false,
      randomMention: Boolean = false,
      randomStyling: Boolean = false
    ): Long {
      return db.insertMessage(
        from = if (outgoing) Recipient.self().id else recipientId,
        to = if (outgoing) recipientId else Recipient.self().id,
        outgoing = outgoing,
        threadId = threadId,
        sentTimestamp = sentTimestamp,
        receivedTimestamp = receivedTimestamp,
        serverTimestamp = serverTimestamp,
        body = body,
        read = read,
        quotes = quotes,
        quoteTargetMissing = quoteTargetMissing,
        randomMention = randomMention,
        randomStyling = randomStyling
      )
    }

    fun remoteDeletedMessage(
      outgoing: Boolean,
      sentTimestamp: Long = System.currentTimeMillis(),
      receivedTimestamp: Long = if (outgoing) sentTimestamp else sentTimestamp + 1,
      serverTimestamp: Long = sentTimestamp
    ): Long {
      return db.insertMessage(
        from = if (outgoing) Recipient.self().id else recipientId,
        to = if (outgoing) recipientId else Recipient.self().id,
        outgoing = outgoing,
        threadId = threadId,
        sentTimestamp = sentTimestamp,
        receivedTimestamp = receivedTimestamp,
        serverTimestamp = serverTimestamp,
        remoteDeleted = true
      )
    }
  }

  private fun SQLiteDatabase.insertMessage(
    from: RecipientId,
    to: RecipientId,
    outgoing: Boolean,
    threadId: Long,
    sentTimestamp: Long = System.currentTimeMillis(),
    receivedTimestamp: Long = if (outgoing) sentTimestamp else sentTimestamp + 1,
    serverTimestamp: Long = sentTimestamp,
    body: String? = null,
    read: Boolean = true,
    quotes: Long? = null,
    quoteTargetMissing: Boolean = false,
    randomMention: Boolean = false,
    randomStyling: Boolean = false,
    remoteDeleted: Boolean = false
  ): Long {
    val type = if (outgoing) {
      MessageTypes.BASE_SENT_TYPE
    } else {
      MessageTypes.BASE_INBOX_TYPE
    } or MessageTypes.SECURE_MESSAGE_BIT or MessageTypes.PUSH_MESSAGE_BIT

    val contentValues = ContentValues()
    contentValues.put(MessageTable.DATE_SENT, sentTimestamp)
    contentValues.put(MessageTable.DATE_RECEIVED, receivedTimestamp)
    contentValues.put(MessageTable.FROM_RECIPIENT_ID, from.serialize())
    contentValues.put(MessageTable.TO_RECIPIENT_ID, to.serialize())
    contentValues.put(MessageTable.THREAD_ID, threadId)
    contentValues.put(MessageTable.BODY, body)
    contentValues.put(MessageTable.TYPE, type)
    contentValues.put(MessageTable.READ, if (read) 1 else 0)

    if (!outgoing) {
      contentValues.put(MessageTable.DATE_SERVER, serverTimestamp)
    }

    if (remoteDeleted) {
      contentValues.put(MessageTable.REMOTE_DELETED, 1)
      return this
        .insertInto(MessageTable.TABLE_NAME)
        .values(contentValues)
        .run()
    }

    if (quotes != null) {
      val quoteDetails = this.getQuoteDetailsFor(quotes)
      contentValues.put(MessageTable.QUOTE_ID, if (quoteTargetMissing) MessageTable.QUOTE_TARGET_MISSING_ID else quoteDetails.quotedSentTimestamp)
      contentValues.put(MessageTable.QUOTE_AUTHOR, quoteDetails.authorId.serialize())
      contentValues.put(MessageTable.QUOTE_BODY, quoteDetails.body)
      contentValues.put(MessageTable.QUOTE_BODY_RANGES, quoteDetails.bodyRanges)
      contentValues.put(MessageTable.QUOTE_TYPE, quoteDetails.type)
      contentValues.put(MessageTable.QUOTE_MISSING, quoteTargetMissing.toInt())
    }

    if (body != null && (randomMention || randomStyling)) {
      val ranges: MutableList<BodyRangeList.BodyRange> = mutableListOf()

      if (randomMention) {
        ranges += BodyRangeList.BodyRange(
          start = 0,
          length = Random.nextInt(body.length),
          mentionUuid = if (outgoing) Recipient.resolved(to).requireAci().toString() else Recipient.resolved(from).requireAci().toString()
        )
      }

      if (randomStyling) {
        ranges += BodyRangeList.BodyRange(
          start = 0,
          length = Random.nextInt(body.length),
          style = BodyRangeList.BodyRange.Style.fromValue(Random.nextInt(BodyRangeList.BodyRange.Style.values().size))
        )
      }

      contentValues.put(MessageTable.MESSAGE_RANGES, BodyRangeList(ranges = ranges).encode())
    }

    return this
      .insertInto(MessageTable.TABLE_NAME)
      .values(contentValues)
      .run()
  }

  private fun assertDatabaseMatches(expected: DatabaseData, actual: DatabaseData) {
    assert(expected.keys.size == actual.keys.size) { "Mismatched table count! Expected: ${expected.keys} || Actual: ${actual.keys}" }
    assert(expected.keys.containsAll(actual.keys)) { "Table names differ! Expected: ${expected.keys} || Actual: ${actual.keys}" }

    val tablesToCheck = expected.keys.filter { !IGNORED_TABLES.contains(it) }

    for (table in tablesToCheck) {
      val expectedTable: List<Map<String, Any?>> = expected[table]!!
      val actualTable: List<Map<String, Any?>> = actual[table]!!

      assert(expectedTable.size == actualTable.size) { "Mismatched number of rows for table '$table'! Expected: ${expectedTable.size} || Actual: ${actualTable.size}\n $actualTable" }

      val expectedFiltered: List<Map<String, Any?>> = expectedTable.withoutExcludedColumns(IGNORED_COLUMNS[table])
      val actualFiltered: List<Map<String, Any?>> = actualTable.withoutExcludedColumns(IGNORED_COLUMNS[table])

      assert(contentEquals(expectedFiltered, actualFiltered)) { "Data did not match for table '$table'!\n${prettyDiff(expectedFiltered, actualFiltered)}" }
    }
  }

  private fun contentEquals(expectedRows: List<Map<String, Any?>>, actualRows: List<Map<String, Any?>>): Boolean {
    if (expectedRows == actualRows) {
      return true
    }

    assert(expectedRows.size == actualRows.size)

    for (i in expectedRows.indices) {
      val expectedRow = expectedRows[i]
      val actualRow = actualRows[i]

      for (key in expectedRow.keys) {
        val expectedValue = expectedRow[key]
        val actualValue = actualRow[key]

        if (!contentEquals(expectedValue, actualValue)) {
          return false
        }
      }
    }

    return true
  }

  private fun contentEquals(lhs: Any?, rhs: Any?): Boolean {
    return if (lhs is ByteArray && rhs is ByteArray) {
      lhs.contentEquals(rhs)
    } else {
      lhs == rhs
    }
  }

  private fun prettyDiff(expectedRows: List<Map<String, Any?>>, actualRows: List<Map<String, Any?>>): String {
    val builder = StringBuilder()

    assert(expectedRows.size == actualRows.size)

    for (i in expectedRows.indices) {
      val expectedRow = expectedRows[i]
      val actualRow = actualRows[i]
      var describedRow = false

      for (key in expectedRow.keys) {
        val expectedValue = expectedRow[key]
        val actualValue = actualRow[key]

        if (!contentEquals(expectedValue, actualValue)) {
          if (!describedRow) {
            builder.append("-- ROW ${i + 1}\n")
            describedRow = true
          }
          builder.append("  [$key] Expected: ${expectedValue.prettyPrint()} || Actual: ${actualValue.prettyPrint()} \n")
        }
      }

      if (describedRow) {
        builder.append("\n")
        builder.append("Expected: $expectedRow\n")
        builder.append("Actual: $actualRow\n")
      }
    }

    return builder.toString()
  }

  private fun Any?.prettyPrint(): String {
    return when (this) {
      is ByteArray -> "Bytes(${Hex.toString(this)})"
      else -> this.toString()
    }
  }

  private fun List<Map<String, Any?>>.withoutExcludedColumns(ignored: Set<String>?): List<Map<String, Any?>> {
    return if (ignored != null) {
      this.map { row ->
        row.filterKeys { !ignored.contains(it) }
      }
    } else {
      this
    }
  }

  private fun SQLiteDatabase.getQuoteDetailsFor(messageId: Long): QuoteDetails {
    return this
      .select(
        MessageTable.DATE_SENT,
        MessageTable.FROM_RECIPIENT_ID,
        MessageTable.BODY,
        MessageTable.MESSAGE_RANGES
      )
      .from(MessageTable.TABLE_NAME)
      .where("${MessageTable.ID} = ?", messageId)
      .run()
      .readToSingleObject { cursor ->
        QuoteDetails(
          quotedSentTimestamp = cursor.requireLong(MessageTable.DATE_SENT),
          authorId = RecipientId.from(cursor.requireLong(MessageTable.FROM_RECIPIENT_ID)),
          body = cursor.requireString(MessageTable.BODY),
          bodyRanges = cursor.requireBlob(MessageTable.MESSAGE_RANGES),
          type = QuoteModel.Type.NORMAL.code
        )
      }!!
  }

  private fun SQLiteDatabase.readAllContents(): DatabaseData {
    return SqlUtil.getAllTables(this).associateWith { table -> this.getAllTableData(table) }
  }

  private fun SQLiteDatabase.getAllTableData(table: String): List<Map<String, Any?>> {
    return this
      .select()
      .from(table)
      .run()
      .readToList { cursor ->
        val map: MutableMap<String, Any?> = mutableMapOf()

        for (i in 0 until cursor.columnCount) {
          val column = cursor.getColumnName(i)

          when (cursor.getType(i)) {
            Cursor.FIELD_TYPE_INTEGER -> map[column] = cursor.getInt(i)
            Cursor.FIELD_TYPE_FLOAT -> map[column] = cursor.getFloat(i)
            Cursor.FIELD_TYPE_STRING -> map[column] = cursor.getString(i)
            Cursor.FIELD_TYPE_BLOB -> map[column] = cursor.getBlob(i)
            Cursor.FIELD_TYPE_NULL -> map[column] = null
          }
        }

        map
      }
  }

  private data class QuoteDetails(
    val quotedSentTimestamp: Long,
    val authorId: RecipientId,
    val body: String?,
    val bodyRanges: ByteArray?,
    val type: Int
  )
}
