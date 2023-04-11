package org.thoughtcrime.securesms.messages

import android.database.Cursor
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.util.ThreadUtil
import org.signal.core.util.readToList
import org.signal.core.util.select
import org.signal.core.util.toSingleLine
import org.signal.core.util.withinTransaction
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.MessageTypes
import org.thoughtcrime.securesms.database.MessageTypes.isOutgoingMessageType
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.ThreadTable
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.testing.Entry
import org.thoughtcrime.securesms.testing.InMemoryLogger
import org.thoughtcrime.securesms.testing.MessageContentFuzzer
import org.thoughtcrime.securesms.testing.SignalActivityRule
import org.thoughtcrime.securesms.testing.assertIs
import org.whispersystems.signalservice.api.crypto.EnvelopeMetadata
import org.whispersystems.signalservice.api.messages.SignalServiceContent
import org.whispersystems.signalservice.api.messages.SignalServiceMetadata
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.internal.push.SignalServiceProtos
import org.whispersystems.signalservice.internal.serialize.SignalServiceAddressProtobufSerializer
import org.whispersystems.signalservice.internal.serialize.SignalServiceMetadataProtobufSerializer
import org.whispersystems.signalservice.internal.serialize.protos.SignalServiceContentProto
import java.util.Optional

@RunWith(AndroidJUnit4::class)
class MessageContentProcessorTestV2 {

  companion object {
    private val TAGS = listOf(MessageContentProcessor.TAG, MessageContentProcessorV2.TAG, AttachmentTable.TAG)

    private val GENERALIZE_TAG = mapOf(
      MessageContentProcessor.TAG to "MCP",
      MessageContentProcessorV2.TAG to "MCP",
      AttachmentTable.TAG to AttachmentTable.TAG
    )

    private val IGNORE_MESSAGE_COLUMNS = listOf(
      MessageTable.DATE_RECEIVED,
      MessageTable.NOTIFIED_TIMESTAMP,
      MessageTable.REACTIONS_LAST_SEEN,
      MessageTable.NOTIFIED
    )

    private val IGNORE_ATTACHMENT_COLUMNS = listOf(
      AttachmentTable.UNIQUE_ID,
      AttachmentTable.TRANSFER_FILE
    )
  }

  @get:Rule
  val harness = SignalActivityRule()

  private lateinit var processorV1: MessageContentProcessor
  private lateinit var processorV2: MessageContentProcessorV2
  private lateinit var testResult: TestResults
  private var envelopeTimestamp: Long = 0

  @Before
  fun setup() {
    processorV1 = MessageContentProcessor(harness.context)
    processorV2 = MessageContentProcessorV2(harness.context)
    envelopeTimestamp = System.currentTimeMillis()
    testResult = TestResults()
  }

  @Test
  fun textMessage() {
    var start = envelopeTimestamp

    val messages: List<TestMessage> = (0 until 100).map {
      start += 200
      TestMessage(
        envelope = MessageContentFuzzer.envelope(start),
        content = MessageContentFuzzer.fuzzTextMessage(),
        metadata = MessageContentFuzzer.envelopeMetadata(harness.others[0], harness.self.id),
        serverDeliveredTimestamp = MessageContentFuzzer.fuzzServerDeliveredTimestamp(start)
      )
    }

    testResult.runV2(messages)
    testResult.runV1(messages)

    testResult.assert()
  }

  @Test
  fun mediaMessage() {
    var start = envelopeTimestamp

    val textMessages: List<TestMessage> = (0 until 10).map {
      start += 200
      TestMessage(
        envelope = MessageContentFuzzer.envelope(start),
        content = MessageContentFuzzer.fuzzTextMessage(),
        metadata = MessageContentFuzzer.envelopeMetadata(harness.others[0], harness.self.id),
        serverDeliveredTimestamp = MessageContentFuzzer.fuzzServerDeliveredTimestamp(start)
      )
    }

    val firstBatchMediaMessages: List<TestMessage> = (0 until 10).map {
      start += 200
      TestMessage(
        envelope = MessageContentFuzzer.envelope(start),
        content = MessageContentFuzzer.fuzzMediaMessageWithBody(textMessages),
        metadata = MessageContentFuzzer.envelopeMetadata(harness.others[0], harness.self.id),
        serverDeliveredTimestamp = MessageContentFuzzer.fuzzServerDeliveredTimestamp(start)
      )
    }

    val secondBatchNoContentMediaMessages: List<TestMessage> = (0 until 10).map {
      start += 200
      TestMessage(
        envelope = MessageContentFuzzer.envelope(start),
        content = MessageContentFuzzer.fuzzMediaMessageNoContent(textMessages + firstBatchMediaMessages),
        metadata = MessageContentFuzzer.envelopeMetadata(harness.others[0], harness.self.id),
        serverDeliveredTimestamp = MessageContentFuzzer.fuzzServerDeliveredTimestamp(start)
      )
    }

    val thirdBatchNoTextMediaMessagesMessages: List<TestMessage> = (0 until 10).map {
      start += 200
      TestMessage(
        envelope = MessageContentFuzzer.envelope(start),
        content = MessageContentFuzzer.fuzzMediaMessageNoText(textMessages + firstBatchMediaMessages),
        metadata = MessageContentFuzzer.envelopeMetadata(harness.others[0], harness.self.id),
        serverDeliveredTimestamp = MessageContentFuzzer.fuzzServerDeliveredTimestamp(start)
      )
    }

    testResult.runV2(textMessages + firstBatchMediaMessages + secondBatchNoContentMediaMessages + thirdBatchNoTextMediaMessagesMessages)
    testResult.runV1(textMessages + firstBatchMediaMessages + secondBatchNoContentMediaMessages + thirdBatchNoTextMediaMessagesMessages)

    testResult.assert()
  }

  private inner class TestResults {

    private lateinit var v1Logs: List<Entry>
    private lateinit var v1Messages: List<List<Pair<String, String?>>>
    private lateinit var v1Attachments: List<List<Pair<String, String?>>>
    private lateinit var v2Logs: List<Entry>
    private lateinit var v2Messages: List<List<Pair<String, String?>>>
    private lateinit var v2Attachments: List<List<Pair<String, String?>>>

    fun runV1(messages: List<TestMessage>) {
      messages.forEach { (envelope, content, metadata, serverDeliveredTimestamp) ->
        if (content.hasDataMessage()) {
          processorV1.process(
            MessageContentProcessor.MessageState.DECRYPTED_OK,
            toSignalServiceContent(envelope, content, metadata, serverDeliveredTimestamp),
            null,
            envelope.timestamp,
            -1
          )
          ThreadUtil.sleep(1)
        }
      }

      v1Logs = harness.inMemoryLogger.logs()
      harness.inMemoryLogger.clear()

      v1Messages = dumpMessages()
      v1Attachments = dumpAttachments()
    }

    fun runV2(messages: List<TestMessage>) {
      messages.forEach { (envelope, content, metadata, serverDeliveredTimestamp) ->
        if (content.hasDataMessage()) {
          processorV2.process(
            envelope,
            content,
            metadata,
            serverDeliveredTimestamp,
            false
          )
          ThreadUtil.sleep(1)
        }
      }

      v2Logs = harness.inMemoryLogger.logs()
      harness.inMemoryLogger.clear()

      v2Messages = dumpMessages()
      v2Attachments = dumpAttachments()

      cleanup()
    }

    fun cleanup() {
      SignalDatabase.rawDatabase.withinTransaction { db ->
        SignalDatabase.threads.deleteAllConversations()
        db.execSQL("DELETE FROM sqlite_sequence WHERE name = '${MessageTable.TABLE_NAME}'")
        db.execSQL("DELETE FROM sqlite_sequence WHERE name = '${ThreadTable.TABLE_NAME}'")
        db.execSQL("DELETE FROM sqlite_sequence WHERE name = '${AttachmentTable.TABLE_NAME}'")
      }
    }

    fun assert() {
      v2Logs.zip(v1Logs)
        .forEach { (v2, v1) ->
          GENERALIZE_TAG[v2.tag]!!.assertIs(GENERALIZE_TAG[v1.tag]!!)

          if (v2.tag != AttachmentTable.TAG) {
            if (v2.message?.startsWith("[") == true && v1.message?.startsWith("[") == false) {
              v2.message!!.substring(v2.message!!.indexOf(']') + 2).assertIs(v1.message)
            } else {
              v2.message.assertIs(v1.message)
            }
          } else {
            if (v2.message?.startsWith("Inserted attachment at ID: AttachmentId::") == true) {
              v2.message!!
                .substring(0, v2.message!!.indexOf(','))
                .assertIs(
                  v1.message!!
                    .substring(0, v1.message!!.indexOf(','))
                )
            } else {
              v2.message.assertIs(v1.message)
            }
          }
          v2.throwable.assertIs(v1.throwable)
        }

      v2Messages.zip(v1Messages)
        .forEach { (v2, v1) ->
          v2.assertIs(v1)
        }

      v2Attachments.zip(v1Attachments)
        .forEach { (v2, v1) ->
          v2.assertIs(v1)
        }
    }

    private fun InMemoryLogger.logs(): List<Entry> {
      return entries()
        .filter { TAGS.contains(it.tag) }
    }

    private fun dumpMessages(): List<List<Pair<String, String?>>> {
      return dumpTable(MessageTable.TABLE_NAME)
        .map { row ->
          val newRow = row.toMutableList()
          newRow.removeIf { IGNORE_MESSAGE_COLUMNS.contains(it.first) }
          newRow
        }
    }

    private fun dumpAttachments(): List<List<Pair<String, String?>>> {
      return dumpTable(AttachmentTable.TABLE_NAME)
        .map { row ->
          val newRow = row.toMutableList()
          newRow.removeIf { IGNORE_ATTACHMENT_COLUMNS.contains(it.first) }
          newRow
        }
    }

    private fun dumpTable(table: String): List<List<Pair<String, String?>>> {
      return SignalDatabase.rawDatabase
        .select()
        .from(table)
        .run()
        .readToList { cursor ->
          val map: List<Pair<String, String?>> = cursor.columnNames.map { column ->
            val index = cursor.getColumnIndex(column)
            var data: String? = when (cursor.getType(index)) {
              Cursor.FIELD_TYPE_BLOB -> Base64.encodeToString(cursor.getBlob(index), 0)
              else -> cursor.getString(index)
            }
            if (table == MessageTable.TABLE_NAME && column == MessageTable.TYPE) {
              data = typeColumnToString(cursor.getLong(index))
            }

            column to data
          }
          map
        }
    }
  }

  private fun toSignalServiceContent(envelope: SignalServiceProtos.Envelope, content: SignalServiceProtos.Content, metadata: EnvelopeMetadata, serverDeliveredTimestamp: Long): SignalServiceContent {
    val localAddress = SignalServiceAddress(metadata.destinationServiceId, Optional.ofNullable(SignalStore.account().e164))
    val signalServiceMetadata = SignalServiceMetadata(
      SignalServiceAddress(metadata.sourceServiceId, Optional.ofNullable(metadata.sourceE164)),
      metadata.sourceDeviceId,
      envelope.timestamp,
      envelope.serverTimestamp,
      serverDeliveredTimestamp,
      metadata.sealedSender,
      envelope.serverGuid,
      Optional.ofNullable(metadata.groupId),
      metadata.destinationServiceId.toString()
    )

    val contentProto = SignalServiceContentProto.newBuilder()
      .setLocalAddress(SignalServiceAddressProtobufSerializer.toProtobuf(localAddress))
      .setMetadata(SignalServiceMetadataProtobufSerializer.toProtobuf(signalServiceMetadata))
      .setContent(content)
      .build()

    return SignalServiceContent.createFromProto(contentProto)!!
  }

  fun typeColumnToString(type: Long): String {
    return """
      isOutgoingMessageType:${isOutgoingMessageType(type)}
      isForcedSms:${type and MessageTypes.MESSAGE_FORCE_SMS_BIT != 0L}
      isDraftMessageType:${type and MessageTypes.BASE_TYPE_MASK == MessageTypes.BASE_DRAFT_TYPE}
      isFailedMessageType:${type and MessageTypes.BASE_TYPE_MASK == MessageTypes.BASE_SENT_FAILED_TYPE}
      isPendingMessageType:${type and MessageTypes.BASE_TYPE_MASK == MessageTypes.BASE_OUTBOX_TYPE || type and MessageTypes.BASE_TYPE_MASK == MessageTypes.BASE_SENDING_TYPE}
      isSentType:${type and MessageTypes.BASE_TYPE_MASK == MessageTypes.BASE_SENT_TYPE}
      isPendingSmsFallbackType:${type and MessageTypes.BASE_TYPE_MASK == MessageTypes.BASE_PENDING_INSECURE_SMS_FALLBACK || type and MessageTypes.BASE_TYPE_MASK == MessageTypes.BASE_PENDING_SECURE_SMS_FALLBACK}
      isPendingSecureSmsFallbackType:${type and MessageTypes.BASE_TYPE_MASK == MessageTypes.BASE_PENDING_SECURE_SMS_FALLBACK}
      isPendingInsecureSmsFallbackType:${type and MessageTypes.BASE_TYPE_MASK == MessageTypes.BASE_PENDING_INSECURE_SMS_FALLBACK}
      isInboxType:${type and MessageTypes.BASE_TYPE_MASK == MessageTypes.BASE_INBOX_TYPE}
      isJoinedType:${type and MessageTypes.BASE_TYPE_MASK == MessageTypes.JOINED_TYPE}
      isUnsupportedMessageType:${type and MessageTypes.BASE_TYPE_MASK == MessageTypes.UNSUPPORTED_MESSAGE_TYPE}
      isInvalidMessageType:${type and MessageTypes.BASE_TYPE_MASK == MessageTypes.INVALID_MESSAGE_TYPE}
      isBadDecryptType:${type and MessageTypes.BASE_TYPE_MASK == MessageTypes.BAD_DECRYPT_TYPE}
      isSecureType:${type and MessageTypes.SECURE_MESSAGE_BIT != 0L}
      isPushType:${type and MessageTypes.PUSH_MESSAGE_BIT != 0L}
      isEndSessionType:${type and MessageTypes.END_SESSION_BIT != 0L}
      isKeyExchangeType:${type and MessageTypes.KEY_EXCHANGE_BIT != 0L}
      isIdentityVerified:${type and MessageTypes.KEY_EXCHANGE_IDENTITY_VERIFIED_BIT != 0L}
      isIdentityDefault:${type and MessageTypes.KEY_EXCHANGE_IDENTITY_DEFAULT_BIT != 0L}
      isCorruptedKeyExchange:${type and MessageTypes.KEY_EXCHANGE_CORRUPTED_BIT != 0L}
      isInvalidVersionKeyExchange:${type and MessageTypes.KEY_EXCHANGE_INVALID_VERSION_BIT != 0L}
      isBundleKeyExchange:${type and MessageTypes.KEY_EXCHANGE_BUNDLE_BIT != 0L}
      isContentBundleKeyExchange:${type and MessageTypes.KEY_EXCHANGE_CONTENT_FORMAT != 0L}
      isIdentityUpdate:${type and MessageTypes.KEY_EXCHANGE_IDENTITY_UPDATE_BIT != 0L}
      isRateLimited:${type and MessageTypes.MESSAGE_RATE_LIMITED_BIT != 0L}
      isExpirationTimerUpdate:${type and MessageTypes.EXPIRATION_TIMER_UPDATE_BIT != 0L}
      isIncomingAudioCall:${type == MessageTypes.INCOMING_AUDIO_CALL_TYPE}
      isIncomingVideoCall:${type == MessageTypes.INCOMING_VIDEO_CALL_TYPE}
      isOutgoingAudioCall:${type == MessageTypes.OUTGOING_AUDIO_CALL_TYPE}
      isOutgoingVideoCall:${type == MessageTypes.OUTGOING_VIDEO_CALL_TYPE}
      isMissedAudioCall:${type == MessageTypes.MISSED_AUDIO_CALL_TYPE}
      isMissedVideoCall:${type == MessageTypes.MISSED_VIDEO_CALL_TYPE}
      isGroupCall:${type == MessageTypes.GROUP_CALL_TYPE}
      isGroupUpdate:${type and MessageTypes.GROUP_UPDATE_BIT != 0L}
      isGroupV2:${type and MessageTypes.GROUP_V2_BIT != 0L}
      isGroupQuit:${type and MessageTypes.GROUP_LEAVE_BIT != 0L && type and MessageTypes.GROUP_V2_BIT == 0L}
      isChatSessionRefresh:${type and MessageTypes.ENCRYPTION_REMOTE_FAILED_BIT != 0L}
      isDuplicateMessageType:${type and MessageTypes.ENCRYPTION_REMOTE_DUPLICATE_BIT != 0L}
      isDecryptInProgressType:${type and 0x40000000 != 0L}
      isNoRemoteSessionType:${type and MessageTypes.ENCRYPTION_REMOTE_NO_SESSION_BIT != 0L}
      isLegacyType:${type and MessageTypes.ENCRYPTION_REMOTE_LEGACY_BIT != 0L || type and MessageTypes.ENCRYPTION_REMOTE_BIT != 0L}
      isProfileChange:${type == MessageTypes.PROFILE_CHANGE_TYPE}
      isGroupV1MigrationEvent:${type == MessageTypes.GV1_MIGRATION_TYPE}
      isChangeNumber:${type == MessageTypes.CHANGE_NUMBER_TYPE}
      isBoostRequest:${type == MessageTypes.BOOST_REQUEST_TYPE}
      isThreadMerge:${type == MessageTypes.THREAD_MERGE_TYPE}
      isSmsExport:${type == MessageTypes.SMS_EXPORT_TYPE}
      isGroupV2LeaveOnly:${type and MessageTypes.GROUP_V2_LEAVE_BITS == MessageTypes.GROUP_V2_LEAVE_BITS}
      isSpecialType:${type and MessageTypes.SPECIAL_TYPES_MASK != 0L}
      isStoryReaction:${type and MessageTypes.SPECIAL_TYPES_MASK == MessageTypes.SPECIAL_TYPE_STORY_REACTION}
      isGiftBadge:${type and MessageTypes.SPECIAL_TYPES_MASK == MessageTypes.SPECIAL_TYPE_GIFT_BADGE}
      isPaymentsNotificaiton:${type and MessageTypes.SPECIAL_TYPES_MASK == MessageTypes.SPECIAL_TYPE_PAYMENTS_NOTIFICATION}
      isRequestToActivatePayments:${type and MessageTypes.SPECIAL_TYPES_MASK == MessageTypes.SPECIAL_TYPE_PAYMENTS_ACTIVATE_REQUEST}
      isPaymentsActivated:${type and MessageTypes.SPECIAL_TYPES_MASK == MessageTypes.SPECIAL_TYPE_PAYMENTS_ACTIVATED}
    """.trimIndent().replace(Regex("is[A-Z][A-Za-z0-9]*:false\n?"), "").toSingleLine()
  }
}
