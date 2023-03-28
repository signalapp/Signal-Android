/**
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http:></http:>//www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.text.SpannableString
import android.text.TextUtils
import androidx.annotation.VisibleForTesting
import androidx.core.content.contentValuesOf
import com.google.android.mms.pdu_alt.NotificationInd
import com.google.android.mms.pdu_alt.PduHeaders
import com.google.protobuf.InvalidProtocolBufferException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.signal.core.util.CursorUtil
import org.signal.core.util.SqlUtil
import org.signal.core.util.SqlUtil.appendArg
import org.signal.core.util.SqlUtil.buildArgs
import org.signal.core.util.SqlUtil.buildCustomCollectionQuery
import org.signal.core.util.SqlUtil.buildSingleCollectionQuery
import org.signal.core.util.SqlUtil.buildTrueUpdateQuery
import org.signal.core.util.SqlUtil.getNextAutoIncrementId
import org.signal.core.util.delete
import org.signal.core.util.emptyIfNull
import org.signal.core.util.exists
import org.signal.core.util.forEach
import org.signal.core.util.insertInto
import org.signal.core.util.logging.Log
import org.signal.core.util.readToList
import org.signal.core.util.readToSet
import org.signal.core.util.readToSingleInt
import org.signal.core.util.readToSingleLong
import org.signal.core.util.readToSingleObject
import org.signal.core.util.requireBlob
import org.signal.core.util.requireBoolean
import org.signal.core.util.requireInt
import org.signal.core.util.requireLong
import org.signal.core.util.requireNonNullString
import org.signal.core.util.requireString
import org.signal.core.util.select
import org.signal.core.util.toOptional
import org.signal.core.util.toSingleLine
import org.signal.core.util.update
import org.signal.core.util.withinTransaction
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.util.Pair
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.attachments.AttachmentId
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.attachments.DatabaseAttachment.DisplayOrderComparator
import org.thoughtcrime.securesms.attachments.MmsNotificationAttachment
import org.thoughtcrime.securesms.contactshare.Contact
import org.thoughtcrime.securesms.conversation.MessageStyler
import org.thoughtcrime.securesms.database.EarlyReceiptCache.Receipt
import org.thoughtcrime.securesms.database.MentionUtil.UpdatedBodyAndMentions
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.attachments
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.distributionLists
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.groupReceipts
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.groups
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.mentions
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.messageLog
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.messages
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.reactions
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.recipients
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.storySends
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.threads
import org.thoughtcrime.securesms.database.documents.Document
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatch
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatchSet
import org.thoughtcrime.securesms.database.documents.NetworkFailure
import org.thoughtcrime.securesms.database.documents.NetworkFailureSet
import org.thoughtcrime.securesms.database.model.GroupCallUpdateDetailsUtil
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord
import org.thoughtcrime.securesms.database.model.Mention
import org.thoughtcrime.securesms.database.model.MessageExportStatus
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.database.model.NotificationMmsMessageRecord
import org.thoughtcrime.securesms.database.model.ParentStoryId
import org.thoughtcrime.securesms.database.model.ParentStoryId.DirectReply
import org.thoughtcrime.securesms.database.model.ParentStoryId.GroupReply
import org.thoughtcrime.securesms.database.model.Quote
import org.thoughtcrime.securesms.database.model.StoryResult
import org.thoughtcrime.securesms.database.model.StoryType
import org.thoughtcrime.securesms.database.model.StoryType.Companion.fromCode
import org.thoughtcrime.securesms.database.model.StoryViewState
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList
import org.thoughtcrime.securesms.database.model.databaseprotos.GiftBadge
import org.thoughtcrime.securesms.database.model.databaseprotos.GroupCallUpdateDetails
import org.thoughtcrime.securesms.database.model.databaseprotos.MessageExportState
import org.thoughtcrime.securesms.database.model.databaseprotos.ProfileChangeDetails
import org.thoughtcrime.securesms.database.model.databaseprotos.SessionSwitchoverEvent
import org.thoughtcrime.securesms.database.model.databaseprotos.ThreadMergeEvent
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.groups.GroupMigrationMembershipChange
import org.thoughtcrime.securesms.insights.InsightsConstants
import org.thoughtcrime.securesms.jobs.OptimizeMessageSearchIndexJob
import org.thoughtcrime.securesms.jobs.TrimThreadJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.linkpreview.LinkPreview
import org.thoughtcrime.securesms.mms.IncomingMediaMessage
import org.thoughtcrime.securesms.mms.MessageGroupContext
import org.thoughtcrime.securesms.mms.MmsException
import org.thoughtcrime.securesms.mms.OutgoingMessage
import org.thoughtcrime.securesms.mms.QuoteModel
import org.thoughtcrime.securesms.mms.SlideDeck
import org.thoughtcrime.securesms.notifications.v2.DefaultMessageNotifier.StickyThread
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.revealable.ViewOnceExpirationInfo
import org.thoughtcrime.securesms.revealable.ViewOnceUtil
import org.thoughtcrime.securesms.sms.IncomingGroupUpdateMessage
import org.thoughtcrime.securesms.sms.IncomingTextMessage
import org.thoughtcrime.securesms.stories.Stories.isFeatureEnabled
import org.thoughtcrime.securesms.util.Base64
import org.thoughtcrime.securesms.util.FeatureFlags
import org.thoughtcrime.securesms.util.JsonUtils
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.Util
import org.thoughtcrime.securesms.util.isStory
import org.whispersystems.signalservice.api.messages.multidevice.ReadMessage
import org.whispersystems.signalservice.api.push.ServiceId
import java.io.Closeable
import java.io.IOException
import java.util.LinkedList
import java.util.Optional
import java.util.UUID
import java.util.function.Function
import kotlin.math.max
import kotlin.math.min

open class MessageTable(context: Context?, databaseHelper: SignalDatabase) : DatabaseTable(context, databaseHelper), MessageTypes, RecipientIdDatabaseReference, ThreadIdDatabaseReference {

  companion object {
    private val TAG = Log.tag(MessageTable::class.java)
    const val TABLE_NAME = "message"
    const val ID = "_id"
    const val DATE_SENT = "date_sent"
    const val DATE_RECEIVED = "date_received"
    const val TYPE = "type"
    const val DATE_SERVER = "date_server"
    const val THREAD_ID = "thread_id"
    const val READ = "read"
    const val BODY = "body"
    const val RECIPIENT_ID = "recipient_id"
    const val RECIPIENT_DEVICE_ID = "recipient_device_id"
    const val DELIVERY_RECEIPT_COUNT = "delivery_receipt_count"
    const val READ_RECEIPT_COUNT = "read_receipt_count"
    const val VIEWED_RECEIPT_COUNT = "viewed_receipt_count"
    const val MISMATCHED_IDENTITIES = "mismatched_identities"
    const val SMS_SUBSCRIPTION_ID = "subscription_id"
    const val EXPIRES_IN = "expires_in"
    const val EXPIRE_STARTED = "expire_started"
    const val NOTIFIED = "notified"
    const val NOTIFIED_TIMESTAMP = "notified_timestamp"
    const val UNIDENTIFIED = "unidentified"
    const val REACTIONS_UNREAD = "reactions_unread"
    const val REACTIONS_LAST_SEEN = "reactions_last_seen"
    const val REMOTE_DELETED = "remote_deleted"
    const val SERVER_GUID = "server_guid"
    const val RECEIPT_TIMESTAMP = "receipt_timestamp"
    const val EXPORT_STATE = "export_state"
    const val EXPORTED = "exported"
    const val MMS_CONTENT_LOCATION = "ct_l"
    const val MMS_EXPIRY = "exp"
    const val MMS_MESSAGE_TYPE = "m_type"
    const val MMS_MESSAGE_SIZE = "m_size"
    const val MMS_STATUS = "st"
    const val MMS_TRANSACTION_ID = "tr_id"
    const val NETWORK_FAILURES = "network_failures"
    const val QUOTE_ID = "quote_id"
    const val QUOTE_AUTHOR = "quote_author"
    const val QUOTE_BODY = "quote_body"
    const val QUOTE_MISSING = "quote_missing"
    const val QUOTE_BODY_RANGES = "quote_mentions"
    const val QUOTE_TYPE = "quote_type"
    const val SHARED_CONTACTS = "shared_contacts"
    const val LINK_PREVIEWS = "link_previews"
    const val MENTIONS_SELF = "mentions_self"
    const val MESSAGE_RANGES = "message_ranges"
    const val VIEW_ONCE = "view_once"
    const val STORY_TYPE = "story_type"
    const val PARENT_STORY_ID = "parent_story_id"
    const val SCHEDULED_DATE = "scheduled_date"

    const val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY AUTOINCREMENT,
        $DATE_SENT INTEGER NOT NULL,
        $DATE_RECEIVED INTEGER NOT NULL,
        $DATE_SERVER INTEGER DEFAULT -1,
        $THREAD_ID INTEGER NOT NULL REFERENCES ${ThreadTable.TABLE_NAME} (${ThreadTable.ID}) ON DELETE CASCADE,
        $RECIPIENT_ID INTEGER NOT NULL REFERENCES ${RecipientTable.TABLE_NAME} (${RecipientTable.ID}) ON DELETE CASCADE,
        $RECIPIENT_DEVICE_ID INTEGER,
        $TYPE INTEGER NOT NULL,
        $BODY TEXT,
        $READ INTEGER DEFAULT 0,
        $MMS_CONTENT_LOCATION TEXT,
        $MMS_EXPIRY INTEGER,
        $MMS_MESSAGE_TYPE INTEGER,
        $MMS_MESSAGE_SIZE INTEGER,
        $MMS_STATUS INTEGER,
        $MMS_TRANSACTION_ID TEXT,
        $SMS_SUBSCRIPTION_ID INTEGER DEFAULT -1, 
        $RECEIPT_TIMESTAMP INTEGER DEFAULT -1, 
        $DELIVERY_RECEIPT_COUNT INTEGER DEFAULT 0, 
        $READ_RECEIPT_COUNT INTEGER DEFAULT 0, 
        $VIEWED_RECEIPT_COUNT INTEGER DEFAULT 0,
        $MISMATCHED_IDENTITIES TEXT DEFAULT NULL,
        $NETWORK_FAILURES TEXT DEFAULT NULL,
        $EXPIRES_IN INTEGER DEFAULT 0,
        $EXPIRE_STARTED INTEGER DEFAULT 0,
        $NOTIFIED INTEGER DEFAULT 0,
        $QUOTE_ID INTEGER DEFAULT 0,
        $QUOTE_AUTHOR INTEGER DEFAULT 0,
        $QUOTE_BODY TEXT DEFAULT NULL,
        $QUOTE_MISSING INTEGER DEFAULT 0,
        $QUOTE_BODY_RANGES BLOB DEFAULT NULL,
        $QUOTE_TYPE INTEGER DEFAULT 0,
        $SHARED_CONTACTS TEXT DEFAULT NULL,
        $UNIDENTIFIED INTEGER DEFAULT 0,
        $LINK_PREVIEWS TEXT DEFAULT NULL,
        $VIEW_ONCE INTEGER DEFAULT 0,
        $REACTIONS_UNREAD INTEGER DEFAULT 0,
        $REACTIONS_LAST_SEEN INTEGER DEFAULT -1,
        $REMOTE_DELETED INTEGER DEFAULT 0,
        $MENTIONS_SELF INTEGER DEFAULT 0,
        $NOTIFIED_TIMESTAMP INTEGER DEFAULT 0,
        $SERVER_GUID TEXT DEFAULT NULL,
        $MESSAGE_RANGES BLOB DEFAULT NULL,
        $STORY_TYPE INTEGER DEFAULT 0,
        $PARENT_STORY_ID INTEGER DEFAULT 0,
        $EXPORT_STATE BLOB DEFAULT NULL,
        $EXPORTED INTEGER DEFAULT 0,
        $SCHEDULED_DATE INTEGER DEFAULT -1
      )
    """

    private const val INDEX_THREAD_DATE = "mms_thread_date_index"
    private const val INDEX_THREAD_STORY_SCHEDULED_DATE = "mms_thread_story_parent_story_scheduled_date_index"

    @JvmField
    val CREATE_INDEXS = arrayOf(
      "CREATE INDEX IF NOT EXISTS mms_read_and_notified_and_thread_id_index ON $TABLE_NAME ($READ, $NOTIFIED, $THREAD_ID)",
      "CREATE INDEX IF NOT EXISTS mms_type_index ON $TABLE_NAME ($TYPE)",
      "CREATE INDEX IF NOT EXISTS mms_date_sent_index ON $TABLE_NAME ($DATE_SENT, $RECIPIENT_ID, $THREAD_ID)",
      "CREATE INDEX IF NOT EXISTS mms_date_server_index ON $TABLE_NAME ($DATE_SERVER)",
      "CREATE INDEX IF NOT EXISTS $INDEX_THREAD_DATE ON $TABLE_NAME ($THREAD_ID, $DATE_RECEIVED);",
      "CREATE INDEX IF NOT EXISTS mms_reactions_unread_index ON $TABLE_NAME ($REACTIONS_UNREAD);",
      "CREATE INDEX IF NOT EXISTS mms_story_type_index ON $TABLE_NAME ($STORY_TYPE);",
      "CREATE INDEX IF NOT EXISTS mms_parent_story_id_index ON $TABLE_NAME ($PARENT_STORY_ID);",
      "CREATE INDEX IF NOT EXISTS $INDEX_THREAD_STORY_SCHEDULED_DATE ON $TABLE_NAME ($THREAD_ID, $DATE_RECEIVED, $STORY_TYPE, $PARENT_STORY_ID, $SCHEDULED_DATE);",
      "CREATE INDEX IF NOT EXISTS message_quote_id_quote_author_scheduled_date_index ON $TABLE_NAME ($QUOTE_ID, $QUOTE_AUTHOR, $SCHEDULED_DATE);",
      "CREATE INDEX IF NOT EXISTS mms_exported_index ON $TABLE_NAME ($EXPORTED);",
      "CREATE INDEX IF NOT EXISTS mms_id_type_payment_transactions_index ON $TABLE_NAME ($ID,$TYPE) WHERE $TYPE & ${MessageTypes.SPECIAL_TYPE_PAYMENTS_NOTIFICATION} != 0;"
    )

    private val MMS_PROJECTION_BASE = arrayOf(
      "$TABLE_NAME.$ID AS $ID",
      THREAD_ID,
      DATE_SENT,
      DATE_RECEIVED,
      DATE_SERVER,
      TYPE,
      READ,
      MMS_CONTENT_LOCATION,
      MMS_EXPIRY,
      MMS_MESSAGE_TYPE,
      MMS_MESSAGE_SIZE,
      MMS_STATUS,
      MMS_TRANSACTION_ID,
      BODY,
      RECIPIENT_ID,
      RECIPIENT_DEVICE_ID,
      DELIVERY_RECEIPT_COUNT,
      READ_RECEIPT_COUNT,
      MISMATCHED_IDENTITIES,
      NETWORK_FAILURES,
      SMS_SUBSCRIPTION_ID,
      EXPIRES_IN,
      EXPIRE_STARTED,
      NOTIFIED,
      QUOTE_ID,
      QUOTE_AUTHOR,
      QUOTE_BODY,
      QUOTE_TYPE,
      QUOTE_MISSING,
      QUOTE_BODY_RANGES,
      SHARED_CONTACTS,
      LINK_PREVIEWS,
      UNIDENTIFIED,
      VIEW_ONCE,
      REACTIONS_UNREAD,
      REACTIONS_LAST_SEEN,
      REMOTE_DELETED,
      MENTIONS_SELF,
      NOTIFIED_TIMESTAMP,
      VIEWED_RECEIPT_COUNT,
      RECEIPT_TIMESTAMP,
      MESSAGE_RANGES,
      STORY_TYPE,
      PARENT_STORY_ID,
      SCHEDULED_DATE
    )

    private val MMS_PROJECTION: Array<String> = MMS_PROJECTION_BASE + "NULL AS ${AttachmentTable.ATTACHMENT_JSON_ALIAS}"

    private val MMS_PROJECTION_WITH_ATTACHMENTS: Array<String> = MMS_PROJECTION_BASE +
      """
        json_group_array(
          json_object(
            '${AttachmentTable.ROW_ID}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.ROW_ID}, 
            '${AttachmentTable.UNIQUE_ID}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.UNIQUE_ID}, 
            '${AttachmentTable.MMS_ID}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.MMS_ID},
            '${AttachmentTable.SIZE}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.SIZE}, 
            '${AttachmentTable.FILE_NAME}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.FILE_NAME}, 
            '${AttachmentTable.DATA}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.DATA}, 
            '${AttachmentTable.CONTENT_TYPE}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.CONTENT_TYPE}, 
            '${AttachmentTable.CDN_NUMBER}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.CDN_NUMBER}, 
            '${AttachmentTable.CONTENT_LOCATION}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.CONTENT_LOCATION}, 
            '${AttachmentTable.FAST_PREFLIGHT_ID}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.FAST_PREFLIGHT_ID},
            '${AttachmentTable.VOICE_NOTE}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.VOICE_NOTE},
            '${AttachmentTable.BORDERLESS}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.BORDERLESS},
            '${AttachmentTable.VIDEO_GIF}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.VIDEO_GIF},
            '${AttachmentTable.WIDTH}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.WIDTH},
            '${AttachmentTable.HEIGHT}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.HEIGHT},
            '${AttachmentTable.QUOTE}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.QUOTE},
            '${AttachmentTable.CONTENT_DISPOSITION}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.CONTENT_DISPOSITION},
            '${AttachmentTable.NAME}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.NAME},
            '${AttachmentTable.TRANSFER_STATE}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.TRANSFER_STATE},
            '${AttachmentTable.CAPTION}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.CAPTION},
            '${AttachmentTable.STICKER_PACK_ID}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.STICKER_PACK_ID},
            '${AttachmentTable.STICKER_PACK_KEY}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.STICKER_PACK_KEY},
            '${AttachmentTable.STICKER_ID}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.STICKER_ID},
            '${AttachmentTable.STICKER_EMOJI}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.STICKER_EMOJI},
            '${AttachmentTable.VISUAL_HASH}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.VISUAL_HASH},
            '${AttachmentTable.TRANSFORM_PROPERTIES}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.TRANSFORM_PROPERTIES},
            '${AttachmentTable.DISPLAY_ORDER}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.DISPLAY_ORDER},
            '${AttachmentTable.UPLOAD_TIMESTAMP}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.UPLOAD_TIMESTAMP}
          )
        ) AS ${AttachmentTable.ATTACHMENT_JSON_ALIAS}
      """.toSingleLine()

    private const val IS_STORY_CLAUSE = "$STORY_TYPE > 0 AND $REMOTE_DELETED = 0"
    private const val RAW_ID_WHERE = "$TABLE_NAME.$ID = ?"

    private val SNIPPET_QUERY =
      """
        SELECT 
          $ID,
          $TYPE,
          $DATE_RECEIVED
        FROM 
          $TABLE_NAME 
        WHERE 
          $THREAD_ID = ? AND 
          $TYPE & ${MessageTypes.GROUP_V2_LEAVE_BITS} != ${MessageTypes.GROUP_V2_LEAVE_BITS} AND 
          $STORY_TYPE = 0 AND 
          $PARENT_STORY_ID <= 0 AND
          $SCHEDULED_DATE = -1 AND 
          $TYPE NOT IN (
            ${MessageTypes.PROFILE_CHANGE_TYPE}, 
             ${MessageTypes.GV1_MIGRATION_TYPE},
             ${MessageTypes.CHANGE_NUMBER_TYPE},
             ${MessageTypes.BOOST_REQUEST_TYPE},
             ${MessageTypes.SMS_EXPORT_TYPE}
           ) 
         ORDER BY $DATE_RECEIVED DESC LIMIT 1
       """.toSingleLine()

    private val IS_CALL_TYPE_CLAUSE = """(
      ($TYPE = ${MessageTypes.INCOMING_AUDIO_CALL_TYPE})
      OR
      ($TYPE = ${MessageTypes.INCOMING_VIDEO_CALL_TYPE})
      OR
      ($TYPE = ${MessageTypes.OUTGOING_AUDIO_CALL_TYPE})
      OR
      ($TYPE = ${MessageTypes.OUTGOING_VIDEO_CALL_TYPE})
      OR
      ($TYPE = ${MessageTypes.MISSED_AUDIO_CALL_TYPE})
      OR
      ($TYPE = ${MessageTypes.MISSED_VIDEO_CALL_TYPE})
    )""".toSingleLine()

    @JvmStatic
    fun mmsReaderFor(cursor: Cursor): MmsReader {
      return MmsReader(cursor)
    }

    private fun getSharedContacts(cursor: Cursor, attachments: List<DatabaseAttachment>): List<Contact> {
      val serializedContacts: String? = cursor.requireString(SHARED_CONTACTS)

      if (serializedContacts.isNullOrEmpty()) {
        return emptyList()
      }

      val attachmentIdMap: Map<AttachmentId, DatabaseAttachment> = attachments.associateBy { it.attachmentId }

      try {
        val contacts: MutableList<Contact> = LinkedList()
        val jsonContacts = JSONArray(serializedContacts)

        for (i in 0 until jsonContacts.length()) {
          val contact: Contact = Contact.deserialize(jsonContacts.getJSONObject(i).toString())

          if (contact.avatar != null && contact.avatar!!.attachmentId != null) {
            val attachment = attachmentIdMap[contact.avatar!!.attachmentId]

            val updatedAvatar = Contact.Avatar(
              contact.avatar!!.attachmentId,
              attachment,
              contact.avatar!!.isProfile
            )

            contacts += Contact(contact, updatedAvatar)
          } else {
            contacts += contact
          }
        }

        return contacts
      } catch (e: JSONException) {
        Log.w(TAG, "Failed to parse shared contacts.", e)
      } catch (e: IOException) {
        Log.w(TAG, "Failed to parse shared contacts.", e)
      }

      return emptyList()
    }

    private fun getLinkPreviews(cursor: Cursor, attachments: List<DatabaseAttachment>): List<LinkPreview> {
      val serializedPreviews: String? = cursor.requireString(LINK_PREVIEWS)

      if (serializedPreviews.isNullOrEmpty()) {
        return emptyList()
      }

      val attachmentIdMap: Map<AttachmentId, DatabaseAttachment> = attachments.associateBy { it.attachmentId }

      try {
        val previews: MutableList<LinkPreview> = LinkedList()
        val jsonPreviews = JSONArray(serializedPreviews)

        for (i in 0 until jsonPreviews.length()) {
          val preview = LinkPreview.deserialize(jsonPreviews.getJSONObject(i).toString())

          if (preview.attachmentId != null) {
            val attachment = attachmentIdMap[preview.attachmentId]

            if (attachment != null) {
              previews += LinkPreview(preview.url, preview.title, preview.description, preview.date, attachment)
            } else {
              previews += preview
            }
          } else {
            previews += preview
          }
        }

        return previews
      } catch (e: JSONException) {
        Log.w(TAG, "Failed to parse shared contacts.", e)
      } catch (e: IOException) {
        Log.w(TAG, "Failed to parse shared contacts.", e)
      }

      return emptyList()
    }

    private fun parseQuoteMentions(cursor: Cursor): List<Mention> {
      val data: ByteArray? = cursor.requireBlob(QUOTE_BODY_RANGES)

      val bodyRanges: BodyRangeList? = if (data != null) {
        try {
          BodyRangeList.parseFrom(data)
        } catch (e: InvalidProtocolBufferException) {
          Log.w(TAG, "Unable to parse quote body ranges", e)
          null
        }
      } else {
        null
      }

      return MentionUtil.bodyRangeListToMentions(bodyRanges)
    }

    private fun parseQuoteBodyRanges(cursor: Cursor): BodyRangeList? {
      val data: ByteArray? = cursor.requireBlob(QUOTE_BODY_RANGES)

      if (data != null) {
        try {
          val bodyRanges = BodyRangeList
            .parseFrom(data)
            .rangesList
            .filter { bodyRange -> bodyRange.associatedValueCase != BodyRangeList.BodyRange.AssociatedValueCase.MENTIONUUID }

          return BodyRangeList.newBuilder().addAllRanges(bodyRanges).build()
        } catch (e: InvalidProtocolBufferException) {
          Log.w(TAG, "Unable to parse quote body ranges", e)
        }
      }
      return null
    }
  }

  private val earlyDeliveryReceiptCache = EarlyReceiptCache("MmsDelivery")

  private fun getOldestGroupUpdateSender(threadId: Long, minimumDateReceived: Long): RecipientId? {
    val type = MessageTypes.SECURE_MESSAGE_BIT or MessageTypes.PUSH_MESSAGE_BIT or MessageTypes.GROUP_UPDATE_BIT or MessageTypes.BASE_INBOX_TYPE

    return readableDatabase
      .select(RECIPIENT_ID)
      .from(TABLE_NAME)
      .where("$THREAD_ID = ? AND $TYPE & ? AND $DATE_RECEIVED >= ?", threadId.toString(), type.toString(), minimumDateReceived.toString())
      .limit(1)
      .run()
      .readToSingleObject { RecipientId.from(it.requireLong(RECIPIENT_ID)) }
  }

  fun getExpirationStartedMessages(): Cursor {
    val where = "$EXPIRE_STARTED > 0"
    return rawQueryWithAttachments(where, null)
  }

  fun getMessageCursor(messageId: Long): Cursor {
    return internalGetMessage(messageId)
  }

  fun hasReceivedAnyCallsSince(threadId: Long, timestamp: Long): Boolean {
    return readableDatabase
      .exists(TABLE_NAME)
      .where(
        "$THREAD_ID = ? AND $DATE_RECEIVED > ? AND ($TYPE = ? OR $TYPE = ? OR $TYPE = ? OR $TYPE =?)",
        threadId,
        timestamp,
        MessageTypes.INCOMING_AUDIO_CALL_TYPE,
        MessageTypes.INCOMING_VIDEO_CALL_TYPE,
        MessageTypes.MISSED_AUDIO_CALL_TYPE,
        MessageTypes.MISSED_VIDEO_CALL_TYPE
      )
      .run()
  }

  fun markAsEndSession(id: Long) {
    updateTypeBitmask(id, MessageTypes.KEY_EXCHANGE_MASK, MessageTypes.END_SESSION_BIT)
  }

  fun markAsInvalidVersionKeyExchange(id: Long) {
    updateTypeBitmask(id, 0, MessageTypes.KEY_EXCHANGE_INVALID_VERSION_BIT)
  }

  fun markAsDecryptFailed(id: Long) {
    updateTypeBitmask(id, MessageTypes.ENCRYPTION_MASK, MessageTypes.ENCRYPTION_REMOTE_FAILED_BIT)
  }

  fun markAsNoSession(id: Long) {
    updateTypeBitmask(id, MessageTypes.ENCRYPTION_MASK, MessageTypes.ENCRYPTION_REMOTE_NO_SESSION_BIT)
  }

  fun markAsUnsupportedProtocolVersion(id: Long) {
    updateTypeBitmask(id, MessageTypes.BASE_TYPE_MASK, MessageTypes.UNSUPPORTED_MESSAGE_TYPE)
  }

  fun markAsInvalidMessage(id: Long) {
    updateTypeBitmask(id, MessageTypes.BASE_TYPE_MASK, MessageTypes.INVALID_MESSAGE_TYPE)
  }

  fun markAsLegacyVersion(id: Long) {
    updateTypeBitmask(id, MessageTypes.ENCRYPTION_MASK, MessageTypes.ENCRYPTION_REMOTE_LEGACY_BIT)
  }

  fun markAsMissedCall(id: Long, isVideoOffer: Boolean) {
    updateTypeBitmask(id, MessageTypes.TOTAL_MASK, if (isVideoOffer) MessageTypes.MISSED_VIDEO_CALL_TYPE else MessageTypes.MISSED_AUDIO_CALL_TYPE)
  }

  fun markSmsStatus(id: Long, status: Int) {
    Log.i(TAG, "Updating ID: $id to status: $status")

    writableDatabase
      .update(TABLE_NAME)
      .values(MMS_STATUS to status)
      .where("$ID = ?", id)
      .run()

    val threadId = getThreadIdForMessage(id)
    threads.update(threadId, false)
    notifyConversationListeners(threadId)
  }

  private fun updateTypeBitmask(id: Long, maskOff: Long, maskOn: Long) {
    writableDatabase.withinTransaction { db ->
      db.execSQL(
        """
          UPDATE $TABLE_NAME 
          SET $TYPE = ($TYPE & ${MessageTypes.TOTAL_MASK - maskOff} | $maskOn ) 
          WHERE $ID = ?
        """.toSingleLine(),
        buildArgs(id)
      )

      val threadId = getThreadIdForMessage(id)
      threads.updateSnippetTypeSilently(threadId)
    }

    ApplicationDependencies.getDatabaseObserver().notifyMessageUpdateObservers(MessageId(id))
    ApplicationDependencies.getDatabaseObserver().notifyConversationListListeners()
  }

  private fun updateMessageBodyAndType(messageId: Long, body: String, maskOff: Long, maskOn: Long): InsertResult {
    writableDatabase.execSQL(
      """
        UPDATE $TABLE_NAME
        SET
          $BODY = ?,
          $TYPE = ($TYPE & ${MessageTypes.TOTAL_MASK - maskOff} | $maskOn) 
        WHERE $ID = ?
      """.toSingleLine(),
      arrayOf(body, messageId.toString() + "")
    )

    val threadId = getThreadIdForMessage(messageId)
    threads.update(threadId, true)
    notifyConversationListeners(threadId)

    return InsertResult(messageId, threadId)
  }

  fun updateBundleMessageBody(messageId: Long, body: String): InsertResult {
    val type = MessageTypes.BASE_INBOX_TYPE or MessageTypes.SECURE_MESSAGE_BIT or MessageTypes.PUSH_MESSAGE_BIT
    return updateMessageBodyAndType(messageId, body, MessageTypes.TOTAL_MASK, type)
  }

  fun getViewedIncomingMessages(threadId: Long): List<MarkedMessageInfo> {
    return readableDatabase
      .select(ID, RECIPIENT_ID, DATE_SENT, TYPE, THREAD_ID, STORY_TYPE)
      .from(TABLE_NAME)
      .where("$THREAD_ID = ? AND $VIEWED_RECEIPT_COUNT > 0 AND $TYPE & ${MessageTypes.BASE_INBOX_TYPE} = ${MessageTypes.BASE_INBOX_TYPE}", threadId)
      .run()
      .readToList { it.toMarkedMessageInfo() }
  }

  fun setIncomingMessageViewed(messageId: Long): MarkedMessageInfo? {
    val results = setIncomingMessagesViewed(listOf(messageId))
    return if (results.isEmpty()) {
      null
    } else {
      results[0]
    }
  }

  fun setIncomingMessagesViewed(messageIds: List<Long>): List<MarkedMessageInfo> {
    if (messageIds.isEmpty()) {
      return emptyList()
    }

    val results: List<MarkedMessageInfo> = readableDatabase
      .select(ID, RECIPIENT_ID, DATE_SENT, TYPE, THREAD_ID, STORY_TYPE)
      .from(TABLE_NAME)
      .where("$ID IN (${Util.join(messageIds, ",")}) AND $VIEWED_RECEIPT_COUNT = 0")
      .run()
      .readToList { cursor ->
        val type = cursor.requireLong(TYPE)

        if (MessageTypes.isSecureType(type) && MessageTypes.isInboxType(type)) {
          cursor.toMarkedMessageInfo()
        } else {
          null
        }
      }
      .filterNotNull()

    val currentTime = System.currentTimeMillis()
    SqlUtil
      .buildCollectionQuery(ID, results.map { it.messageId.id })
      .forEach { query ->
        writableDatabase
          .update(TABLE_NAME)
          .values(
            VIEWED_RECEIPT_COUNT to 1,
            RECEIPT_TIMESTAMP to currentTime
          )
          .where(query.where, query.whereArgs)
          .run()
      }

    val threadsUpdated: Set<Long> = results
      .map { it.threadId }
      .toSet()

    val storyRecipientsUpdated: Set<RecipientId> = results
      .filter { it.storyType.isStory }
      .mapNotNull { threads.getRecipientIdForThreadId(it.threadId) }
      .toSet()

    notifyConversationListeners(threadsUpdated)
    notifyConversationListListeners()
    ApplicationDependencies.getDatabaseObserver().notifyStoryObservers(storyRecipientsUpdated)

    return results
  }

  fun setOutgoingGiftsRevealed(messageIds: List<Long>): List<MarkedMessageInfo> {
    val results: List<MarkedMessageInfo> = readableDatabase
      .select(ID, RECIPIENT_ID, DATE_SENT, THREAD_ID, STORY_TYPE)
      .from(TABLE_NAME)
      .where("""$ID IN (${Util.join(messageIds, ",")}) AND (${getOutgoingTypeClause()}) AND ($TYPE & ${MessageTypes.SPECIAL_TYPES_MASK} = ${MessageTypes.SPECIAL_TYPE_GIFT_BADGE}) AND $VIEWED_RECEIPT_COUNT = 0""")
      .run()
      .readToList { it.toMarkedMessageInfo() }

    val currentTime = System.currentTimeMillis()
    SqlUtil
      .buildCollectionQuery(ID, results.map { it.messageId.id })
      .forEach { query ->
        writableDatabase
          .update(TABLE_NAME)
          .values(
            VIEWED_RECEIPT_COUNT to 1,
            RECEIPT_TIMESTAMP to currentTime
          )
          .where(query.where, query.whereArgs)
          .run()
      }

    val threadsUpdated = results
      .map { it.threadId }
      .toSet()

    notifyConversationListeners(threadsUpdated)
    return results
  }

  fun insertCallLog(recipientId: RecipientId, type: Long, timestamp: Long): InsertResult {
    val unread = MessageTypes.isMissedAudioCall(type) || MessageTypes.isMissedVideoCall(type)
    val recipient = Recipient.resolved(recipientId)
    val threadId = threads.getOrCreateThreadIdFor(recipient)

    val values = contentValuesOf(
      RECIPIENT_ID to recipientId.serialize(),
      RECIPIENT_DEVICE_ID to 1,
      DATE_RECEIVED to System.currentTimeMillis(),
      DATE_SENT to timestamp,
      READ to if (unread) 0 else 1,
      TYPE to type,
      THREAD_ID to threadId
    )

    val messageId = writableDatabase.insert(TABLE_NAME, null, values)

    if (unread) {
      threads.incrementUnread(threadId, 1, 0)
    }

    threads.update(threadId, true)

    notifyConversationListeners(threadId)
    TrimThreadJob.enqueueAsync(threadId)

    return InsertResult(messageId, threadId)
  }

  fun updateCallLog(messageId: Long, type: Long) {
    val unread = MessageTypes.isMissedAudioCall(type) || MessageTypes.isMissedVideoCall(type)

    writableDatabase
      .update(TABLE_NAME)
      .values(
        TYPE to type,
        READ to if (unread) 0 else 1
      )
      .where("$ID = ?", messageId)
      .run()

    val threadId = getThreadIdForMessage(messageId)

    if (unread) {
      threads.incrementUnread(threadId, 1, 0)
    }

    threads.update(threadId, true)

    notifyConversationListeners(threadId)
    ApplicationDependencies.getDatabaseObserver().notifyMessageUpdateObservers(MessageId(messageId))
  }

  fun insertOrUpdateGroupCall(
    groupRecipientId: RecipientId,
    sender: RecipientId,
    timestamp: Long,
    peekGroupCallEraId: String?,
    peekJoinedUuids: Collection<UUID>,
    isCallFull: Boolean
  ) {
    val recipient = Recipient.resolved(groupRecipientId)
    val threadId = threads.getOrCreateThreadIdFor(recipient)
    val peerEraIdSameAsPrevious = updatePreviousGroupCall(threadId, peekGroupCallEraId, peekJoinedUuids, isCallFull)

    writableDatabase.withinTransaction { db ->
      if (!peerEraIdSameAsPrevious && !Util.isEmpty(peekGroupCallEraId)) {
        val self = Recipient.self()
        val markRead = peekJoinedUuids.contains(self.requireServiceId().uuid()) || self.id == sender
        val updateDetails = GroupCallUpdateDetails.newBuilder()
          .setEraId(peekGroupCallEraId.emptyIfNull())
          .setStartedCallUuid(Recipient.resolved(sender).requireServiceId().toString())
          .setStartedCallTimestamp(timestamp)
          .addAllInCallUuids(peekJoinedUuids.map { it.toString() }.toList())
          .setIsCallFull(isCallFull)
          .build()
          .toByteArray()

        val values = contentValuesOf(
          RECIPIENT_ID to sender.serialize(),
          RECIPIENT_DEVICE_ID to 1,
          DATE_RECEIVED to timestamp,
          DATE_SENT to timestamp,
          READ to if (markRead) 1 else 0,
          BODY to Base64.encodeBytes(updateDetails),
          TYPE to MessageTypes.GROUP_CALL_TYPE,
          THREAD_ID to threadId
        )

        db.insert(TABLE_NAME, null, values)

        threads.incrementUnread(threadId, 1, 0)
      }

      threads.update(threadId, true)
    }

    notifyConversationListeners(threadId)
    TrimThreadJob.enqueueAsync(threadId)
  }

  fun insertOrUpdateGroupCall(
    groupRecipientId: RecipientId,
    sender: RecipientId,
    timestamp: Long,
    messageGroupCallEraId: String?
  ) {
    val threadId = writableDatabase.withinTransaction { db ->
      val recipient = Recipient.resolved(groupRecipientId)
      val threadId = threads.getOrCreateThreadIdFor(recipient)

      val cursor = db
        .select(*MMS_PROJECTION)
        .from(TABLE_NAME)
        .where("$TYPE = ? AND $THREAD_ID = ?", MessageTypes.GROUP_CALL_TYPE, threadId)
        .orderBy("$DATE_RECEIVED DESC")
        .limit(1)
        .run()

      var sameEraId = false

      MmsReader(cursor).use { reader ->
        val record: MessageRecord? = reader.firstOrNull()

        if (record != null) {
          val groupCallUpdateDetails = GroupCallUpdateDetailsUtil.parse(record.body)
          sameEraId = groupCallUpdateDetails.eraId == messageGroupCallEraId && !Util.isEmpty(messageGroupCallEraId)

          if (!sameEraId) {
            db.update(TABLE_NAME)
              .values(BODY to GroupCallUpdateDetailsUtil.createUpdatedBody(groupCallUpdateDetails, emptyList(), false))
              .where("$ID = ?", record.id)
              .run()
          }
        }
      }

      if (!sameEraId && !Util.isEmpty(messageGroupCallEraId)) {
        val updateDetails = GroupCallUpdateDetails.newBuilder()
          .setEraId(Util.emptyIfNull(messageGroupCallEraId))
          .setStartedCallUuid(Recipient.resolved(sender).requireServiceId().toString())
          .setStartedCallTimestamp(timestamp)
          .addAllInCallUuids(emptyList())
          .setIsCallFull(false)
          .build()
          .toByteArray()

        val values = contentValuesOf(
          RECIPIENT_ID to sender.serialize(),
          RECIPIENT_DEVICE_ID to 1,
          DATE_RECEIVED to timestamp,
          DATE_SENT to timestamp,
          READ to 0,
          BODY to Base64.encodeBytes(updateDetails),
          TYPE to MessageTypes.GROUP_CALL_TYPE,
          THREAD_ID to threadId
        )

        db.insert(TABLE_NAME, null, values)
        threads.incrementUnread(threadId, 1, 0)
      }

      threads.update(threadId, true)

      threadId
    }

    notifyConversationListeners(threadId)
    TrimThreadJob.enqueueAsync(threadId)
  }

  fun updatePreviousGroupCall(threadId: Long, peekGroupCallEraId: String?, peekJoinedUuids: Collection<UUID>, isCallFull: Boolean): Boolean {
    return writableDatabase.withinTransaction { db ->
      val cursor = db
        .select(*MMS_PROJECTION)
        .from(TABLE_NAME)
        .where("$TYPE = ? AND $THREAD_ID = ?", MessageTypes.GROUP_CALL_TYPE, threadId)
        .orderBy("$DATE_RECEIVED DESC")
        .limit(1)
        .run()

      MmsReader(cursor).use { reader ->
        val record = reader.getNext() ?: return@withinTransaction false
        val groupCallUpdateDetails = GroupCallUpdateDetailsUtil.parse(record.body)
        val containsSelf = peekJoinedUuids.contains(SignalStore.account().requireAci().uuid())
        val sameEraId = groupCallUpdateDetails.eraId == peekGroupCallEraId && !Util.isEmpty(peekGroupCallEraId)

        val inCallUuids = if (sameEraId) {
          peekJoinedUuids.map { it.toString() }.toList()
        } else {
          emptyList()
        }

        val contentValues = contentValuesOf(
          BODY to GroupCallUpdateDetailsUtil.createUpdatedBody(groupCallUpdateDetails, inCallUuids, isCallFull)
        )

        if (sameEraId && containsSelf) {
          contentValues.put(READ, 1)
        }

        val query = buildTrueUpdateQuery(ID_WHERE, buildArgs(record.id), contentValues)
        val updated = db.update(TABLE_NAME, contentValues, query.where, query.whereArgs) > 0

        if (updated) {
          notifyConversationListeners(threadId)
        }

        sameEraId
      }
    }
  }

  @JvmOverloads
  fun insertMessageInbox(message: IncomingTextMessage, type: Long = MessageTypes.BASE_INBOX_TYPE): Optional<InsertResult> {
    var type = type
    var tryToCollapseJoinRequestEvents = false

    if (message.isJoined) {
      type = type and MessageTypes.TOTAL_MASK - MessageTypes.BASE_TYPE_MASK or MessageTypes.JOINED_TYPE
    } else if (message.isPreKeyBundle) {
      type = type or (MessageTypes.KEY_EXCHANGE_BIT or MessageTypes.KEY_EXCHANGE_BUNDLE_BIT)
    } else if (message.isSecureMessage) {
      type = type or MessageTypes.SECURE_MESSAGE_BIT
    } else if (message.isGroup) {
      val incomingGroupUpdateMessage = message as IncomingGroupUpdateMessage
      type = type or MessageTypes.SECURE_MESSAGE_BIT
      if (incomingGroupUpdateMessage.isGroupV2) {
        type = type or (MessageTypes.GROUP_V2_BIT or MessageTypes.GROUP_UPDATE_BIT)
        if (incomingGroupUpdateMessage.isJustAGroupLeave) {
          type = type or MessageTypes.GROUP_LEAVE_BIT
        } else if (incomingGroupUpdateMessage.isCancelJoinRequest) {
          tryToCollapseJoinRequestEvents = true
        }
      } else if (incomingGroupUpdateMessage.isUpdate) {
        type = type or MessageTypes.GROUP_UPDATE_BIT
      } else if (incomingGroupUpdateMessage.isQuit) {
        type = type or MessageTypes.GROUP_LEAVE_BIT
      }
    } else if (message.isEndSession) {
      type = type or MessageTypes.SECURE_MESSAGE_BIT
      type = type or MessageTypes.END_SESSION_BIT
    }

    if (message.isPush) {
      type = type or MessageTypes.PUSH_MESSAGE_BIT
    }

    if (message.isIdentityUpdate) {
      type = type or MessageTypes.KEY_EXCHANGE_IDENTITY_UPDATE_BIT
    }

    if (message.isContentPreKeyBundle) {
      type = type or MessageTypes.KEY_EXCHANGE_CONTENT_FORMAT
    }

    if (message.isIdentityVerified) {
      type = type or MessageTypes.KEY_EXCHANGE_IDENTITY_VERIFIED_BIT
    } else if (message.isIdentityDefault) {
      type = type or MessageTypes.KEY_EXCHANGE_IDENTITY_DEFAULT_BIT
    }

    val recipient = Recipient.resolved(message.sender)

    val groupRecipient: Recipient? = if (message.groupId == null) {
      null
    } else {
      val id = recipients.getOrInsertFromPossiblyMigratedGroupId(message.groupId!!)
      Recipient.resolved(id)
    }

    val silent = message.isIdentityUpdate ||
      message.isIdentityVerified ||
      message.isIdentityDefault ||
      message.isJustAGroupLeave || type and MessageTypes.GROUP_UPDATE_BIT > 0

    val unread = !silent && (
      message.isSecureMessage ||
        message.isGroup ||
        message.isPreKeyBundle ||
        Util.isDefaultSmsProvider(context)
      )

    val threadId: Long = if (groupRecipient == null) threads.getOrCreateThreadIdFor(recipient) else threads.getOrCreateThreadIdFor(groupRecipient)

    if (tryToCollapseJoinRequestEvents) {
      val result = collapseJoinRequestEventsIfPossible(threadId, message as IncomingGroupUpdateMessage)
      if (result.isPresent) {
        return result
      }
    }

    val values = ContentValues()
    values.put(RECIPIENT_ID, message.sender.serialize())
    values.put(RECIPIENT_DEVICE_ID, message.senderDeviceId)
    values.put(DATE_RECEIVED, message.receivedTimestampMillis)
    values.put(DATE_SENT, message.sentTimestampMillis)
    values.put(DATE_SERVER, message.serverTimestampMillis)
    values.put(READ, if (unread) 0 else 1)
    values.put(SMS_SUBSCRIPTION_ID, message.subscriptionId)
    values.put(EXPIRES_IN, message.expiresIn)
    values.put(UNIDENTIFIED, message.isUnidentified)
    values.put(BODY, message.messageBody)
    values.put(TYPE, type)
    values.put(THREAD_ID, threadId)
    values.put(SERVER_GUID, message.serverGuid)

    return if (message.isPush && isDuplicate(message, threadId)) {
      Log.w(TAG, "Duplicate message (" + message.sentTimestampMillis + "), ignoring...")
      Optional.empty()
    } else {
      val messageId = writableDatabase.insert(TABLE_NAME, null, values)

      if (unread) {
        threads.incrementUnread(threadId, 1, 0)
      }

      if (!silent) {
        threads.update(threadId, true)
        TrimThreadJob.enqueueAsync(threadId)
      }

      if (message.subscriptionId != -1) {
        recipients.setDefaultSubscriptionId(recipient.id, message.subscriptionId)
      }

      notifyConversationListeners(threadId)

      Optional.of(InsertResult(messageId, threadId))
    }
  }

  fun insertProfileNameChangeMessages(recipient: Recipient, newProfileName: String, previousProfileName: String) {
    writableDatabase.withinTransaction { db ->
      val groupRecords = groups.getGroupsContainingMember(recipient.id, false)
      val profileChangeDetails = ProfileChangeDetails.newBuilder()
        .setProfileNameChange(
          ProfileChangeDetails.StringChange.newBuilder()
            .setNew(newProfileName)
            .setPrevious(previousProfileName)
        )
        .build()
        .toByteArray()

      val threadIdsToUpdate = mutableListOf<Long?>().apply {
        add(threads.getThreadIdFor(recipient.id))
        addAll(
          groupRecords
            .filter { it.isActive }
            .map { threads.getThreadIdFor(it.recipientId) }
        )
      }

      threadIdsToUpdate
        .filterNotNull()
        .forEach { threadId ->
          val values = contentValuesOf(
            RECIPIENT_ID to recipient.id.serialize(),
            RECIPIENT_DEVICE_ID to 1,
            DATE_RECEIVED to System.currentTimeMillis(),
            DATE_SENT to System.currentTimeMillis(),
            READ to 1,
            TYPE to MessageTypes.PROFILE_CHANGE_TYPE,
            THREAD_ID to threadId,
            BODY to Base64.encodeBytes(profileChangeDetails)
          )
          db.insert(TABLE_NAME, null, values)
          notifyConversationListeners(threadId)
          TrimThreadJob.enqueueAsync(threadId)
        }
    }
  }

  fun insertGroupV1MigrationEvents(recipientId: RecipientId, threadId: Long, membershipChange: GroupMigrationMembershipChange) {
    insertGroupV1MigrationNotification(recipientId, threadId)
    if (!membershipChange.isEmpty) {
      insertGroupV1MigrationMembershipChanges(recipientId, threadId, membershipChange)
    }
    notifyConversationListeners(threadId)
    TrimThreadJob.enqueueAsync(threadId)
  }

  private fun insertGroupV1MigrationNotification(recipientId: RecipientId, threadId: Long) {
    insertGroupV1MigrationMembershipChanges(recipientId, threadId, GroupMigrationMembershipChange.empty())
  }

  private fun insertGroupV1MigrationMembershipChanges(recipientId: RecipientId, threadId: Long, membershipChange: GroupMigrationMembershipChange) {
    val values = contentValuesOf(
      RECIPIENT_ID to recipientId.serialize(),
      RECIPIENT_DEVICE_ID to 1,
      DATE_RECEIVED to System.currentTimeMillis(),
      DATE_SENT to System.currentTimeMillis(),
      READ to 1,
      TYPE to MessageTypes.GV1_MIGRATION_TYPE,
      THREAD_ID to threadId
    )

    if (!membershipChange.isEmpty) {
      values.put(BODY, membershipChange.serialize())
    }

    databaseHelper.signalWritableDatabase.insert(TABLE_NAME, null, values)
  }

  fun insertNumberChangeMessages(recipientId: RecipientId) {
    val groupRecords = groups.getGroupsContainingMember(recipientId, false)

    writableDatabase.withinTransaction { db ->
      val threadIdsToUpdate = mutableListOf<Long?>().apply {
        add(threads.getThreadIdFor(recipientId))
        addAll(
          groupRecords
            .filter { it.isActive }
            .map { threads.getThreadIdFor(it.recipientId) }
        )
      }

      threadIdsToUpdate
        .filterNotNull()
        .forEach { threadId: Long ->
          val values = contentValuesOf(
            RECIPIENT_ID to recipientId.serialize(),
            RECIPIENT_DEVICE_ID to 1,
            DATE_RECEIVED to System.currentTimeMillis(),
            DATE_SENT to System.currentTimeMillis(),
            READ to 1,
            TYPE to MessageTypes.CHANGE_NUMBER_TYPE,
            THREAD_ID to threadId,
            BODY to null
          )

          db.insert(TABLE_NAME, null, values)
          threads.update(threadId, true)

          TrimThreadJob.enqueueAsync(threadId)
          notifyConversationListeners(threadId)
        }
    }
  }

  fun insertBoostRequestMessage(recipientId: RecipientId, threadId: Long) {
    writableDatabase
      .insertInto(TABLE_NAME)
      .values(
        RECIPIENT_ID to recipientId.serialize(),
        RECIPIENT_DEVICE_ID to 1,
        DATE_RECEIVED to System.currentTimeMillis(),
        DATE_SENT to System.currentTimeMillis(),
        READ to 1,
        TYPE to MessageTypes.BOOST_REQUEST_TYPE,
        THREAD_ID to threadId,
        BODY to null
      )
      .run()
  }

  fun insertThreadMergeEvent(recipientId: RecipientId, threadId: Long, event: ThreadMergeEvent) {
    writableDatabase
      .insertInto(TABLE_NAME)
      .values(
        RECIPIENT_ID to recipientId.serialize(),
        RECIPIENT_DEVICE_ID to 1,
        DATE_RECEIVED to System.currentTimeMillis(),
        DATE_SENT to System.currentTimeMillis(),
        READ to 1,
        TYPE to MessageTypes.THREAD_MERGE_TYPE,
        THREAD_ID to threadId,
        BODY to Base64.encodeBytes(event.toByteArray())
      )
      .run()
    ApplicationDependencies.getDatabaseObserver().notifyConversationListeners(threadId)
  }

  fun insertSessionSwitchoverEvent(recipientId: RecipientId, threadId: Long, event: SessionSwitchoverEvent) {
    check(FeatureFlags.phoneNumberPrivacy()) { "Should not occur in a non-PNP world!" }
    writableDatabase
      .insertInto(TABLE_NAME)
      .values(
        RECIPIENT_ID to recipientId.serialize(),
        RECIPIENT_DEVICE_ID to 1,
        DATE_RECEIVED to System.currentTimeMillis(),
        DATE_SENT to System.currentTimeMillis(),
        READ to 1,
        TYPE to MessageTypes.SESSION_SWITCHOVER_TYPE,
        THREAD_ID to threadId,
        BODY to Base64.encodeBytes(event.toByteArray())
      )
      .run()
    ApplicationDependencies.getDatabaseObserver().notifyConversationListeners(threadId)
  }

  fun insertSmsExportMessage(recipientId: RecipientId, threadId: Long) {
    val updated = writableDatabase.withinTransaction { db ->
      if (messages.hasSmsExportMessage(threadId)) {
        false
      } else {
        db.insertInto(TABLE_NAME)
          .values(
            RECIPIENT_ID to recipientId.serialize(),
            RECIPIENT_DEVICE_ID to 1,
            DATE_RECEIVED to System.currentTimeMillis(),
            DATE_SENT to System.currentTimeMillis(),
            READ to 1,
            TYPE to MessageTypes.SMS_EXPORT_TYPE,
            THREAD_ID to threadId,
            BODY to null
          )
          .run()
        true
      }
    }

    if (updated) {
      ApplicationDependencies.getDatabaseObserver().notifyConversationListeners(threadId)
    }
  }

  fun endTransaction(database: SQLiteDatabase) {
    database.endTransaction()
  }

  fun ensureMigration() {
    databaseHelper.signalWritableDatabase
  }

  fun isStory(messageId: Long): Boolean {
    return readableDatabase
      .exists(TABLE_NAME)
      .where("$IS_STORY_CLAUSE AND $ID = ?", messageId)
      .run()
  }

  fun getOutgoingStoriesTo(recipientId: RecipientId): Reader {
    val recipient = Recipient.resolved(recipientId)
    val threadId: Long? = if (recipient.isGroup) {
      threads.getThreadIdFor(recipientId)
    } else {
      null
    }

    var where = "$IS_STORY_CLAUSE AND (${getOutgoingTypeClause()})"
    val whereArgs: Array<String>

    if (threadId == null) {
      where += " AND $RECIPIENT_ID = ?"
      whereArgs = buildArgs(recipientId)
    } else {
      where += " AND $THREAD_ID = ?"
      whereArgs = buildArgs(threadId)
    }

    return MmsReader(rawQueryWithAttachments(where, whereArgs))
  }

  fun getAllOutgoingStories(reverse: Boolean, limit: Int): Reader {
    val where = "$IS_STORY_CLAUSE AND (${getOutgoingTypeClause()})"
    return MmsReader(rawQueryWithAttachments(where, null, reverse, limit.toLong()))
  }

  fun markAllIncomingStoriesRead(): List<MarkedMessageInfo> {
    val where = "$IS_STORY_CLAUSE AND NOT (${getOutgoingTypeClause()}) AND $READ = 0"
    val markedMessageInfos = setMessagesRead(where, null)
    notifyConversationListListeners()
    return markedMessageInfos
  }

  fun markAllFailedStoriesNotified() {
    val where = "$IS_STORY_CLAUSE AND (${getOutgoingTypeClause()}) AND $NOTIFIED = 0 AND ($TYPE & ${MessageTypes.BASE_TYPE_MASK}) = ${MessageTypes.BASE_SENT_FAILED_TYPE}"

    writableDatabase
      .update("$TABLE_NAME INDEXED BY $INDEX_THREAD_DATE")
      .values(NOTIFIED to 1)
      .where(where)
      .run()
    notifyConversationListListeners()
  }

  fun markOnboardingStoryRead() {
    val recipientId = SignalStore.releaseChannelValues().releaseChannelRecipientId ?: return
    val where = "$IS_STORY_CLAUSE AND NOT (${getOutgoingTypeClause()}) AND $READ = 0 AND $RECIPIENT_ID = ?"
    val markedMessageInfos = setMessagesRead(where, buildArgs(recipientId))

    if (markedMessageInfos.isNotEmpty()) {
      notifyConversationListListeners()
    }
  }

  fun getAllStoriesFor(recipientId: RecipientId, limit: Int): Reader {
    val threadId = threads.getThreadIdIfExistsFor(recipientId)
    val where = "$IS_STORY_CLAUSE AND $THREAD_ID = ?"
    val whereArgs = buildArgs(threadId)
    val cursor = rawQueryWithAttachments(where, whereArgs, false, limit.toLong())
    return MmsReader(cursor)
  }

  fun getUnreadStories(recipientId: RecipientId, limit: Int): Reader {
    val threadId = threads.getThreadIdIfExistsFor(recipientId)
    val query = "$IS_STORY_CLAUSE AND NOT (${getOutgoingTypeClause()}) AND $THREAD_ID = ? AND $VIEWED_RECEIPT_COUNT = ?"
    val args = buildArgs(threadId, 0)
    return MmsReader(rawQueryWithAttachments(query, args, false, limit.toLong()))
  }

  fun getUnreadMisedCallCount(): Long {
    return readableDatabase
      .select("COUNT(*)")
      .from(TABLE_NAME)
      .where(
        "($TYPE = ? OR $TYPE = ?) AND $READ = ?",
        MessageTypes.MISSED_AUDIO_CALL_TYPE,
        MessageTypes.MISSED_VIDEO_CALL_TYPE,
        0
      )
      .run()
      .readToSingleLong(0L)
  }

  fun getParentStoryIdForGroupReply(messageId: Long): GroupReply? {
    return readableDatabase
      .select(PARENT_STORY_ID)
      .from(TABLE_NAME)
      .where("$ID = ?", messageId)
      .run()
      .readToSingleObject { cursor ->
        val parentStoryId: ParentStoryId? = ParentStoryId.deserialize(cursor.requireLong(PARENT_STORY_ID))
        if (parentStoryId != null && parentStoryId.isGroupReply()) {
          parentStoryId as GroupReply
        } else {
          null
        }
      }
  }

  fun getStoryViewState(recipientId: RecipientId): StoryViewState {
    if (!isFeatureEnabled()) {
      return StoryViewState.NONE
    }
    val threadId = threads.getThreadIdIfExistsFor(recipientId)
    return getStoryViewState(threadId)
  }

  /**
   * Synchronizes whether we've viewed a recipient's story based on incoming sync messages.
   */
  fun updateViewedStories(syncMessageIds: Set<SyncMessageId>) {
    val timestamps: String = syncMessageIds
      .map { it.timetamp }
      .joinToString(",")

    writableDatabase.withinTransaction { db ->
      db.select(RECIPIENT_ID)
        .from(TABLE_NAME)
        .where("$IS_STORY_CLAUSE AND $DATE_SENT IN ($timestamps) AND NOT (${getOutgoingTypeClause()}) AND $VIEWED_RECEIPT_COUNT > 0")
        .run()
        .readToList { cursor -> RecipientId.from(cursor.requireLong(RECIPIENT_ID)) }
        .forEach { id -> recipients.updateLastStoryViewTimestamp(id) }
    }
  }

  @VisibleForTesting
  fun getStoryViewState(threadId: Long): StoryViewState {
    val hasStories = readableDatabase
      .exists(TABLE_NAME)
      .where("$IS_STORY_CLAUSE AND $THREAD_ID = ?", threadId)
      .run()

    if (!hasStories) {
      return StoryViewState.NONE
    }

    val hasUnviewedStories = readableDatabase
      .exists(TABLE_NAME)
      .where("$IS_STORY_CLAUSE AND $THREAD_ID = ? AND $VIEWED_RECEIPT_COUNT = ? AND NOT (${getOutgoingTypeClause()})", threadId, 0)
      .run()

    return if (hasUnviewedStories) {
      StoryViewState.UNVIEWED
    } else {
      StoryViewState.VIEWED
    }
  }

  fun isOutgoingStoryAlreadyInDatabase(recipientId: RecipientId, sentTimestamp: Long): Boolean {
    return readableDatabase
      .exists(TABLE_NAME)
      .where("$RECIPIENT_ID = ? AND $STORY_TYPE > 0 AND $DATE_SENT = ? AND (${getOutgoingTypeClause()})", recipientId, sentTimestamp)
      .run()
  }

  @Throws(NoSuchMessageException::class)
  fun getStoryId(authorId: RecipientId, sentTimestamp: Long): MessageId {
    return readableDatabase
      .select(ID, RECIPIENT_ID)
      .from(TABLE_NAME)
      .where("$IS_STORY_CLAUSE AND $DATE_SENT = ?", sentTimestamp)
      .run()
      .readToSingleObject { cursor ->
        val rowRecipientId = RecipientId.from(cursor.getLong(cursor.getColumnIndexOrThrow(RECIPIENT_ID)))
        if (Recipient.self().id == authorId || rowRecipientId == authorId) {
          MessageId(CursorUtil.requireLong(cursor, ID))
        } else {
          null
        }
      } ?: throw NoSuchMessageException("No story sent at $sentTimestamp")
  }

  fun getUnreadStoryThreadRecipientIds(): List<RecipientId> {
    val query = """
      SELECT DISTINCT ${ThreadTable.TABLE_NAME}.${ThreadTable.RECIPIENT_ID}
      FROM $TABLE_NAME 
        JOIN ${ThreadTable.TABLE_NAME} ON $TABLE_NAME.$THREAD_ID = ${ThreadTable.TABLE_NAME}.${ThreadTable.ID}
      WHERE 
        $IS_STORY_CLAUSE AND 
        (${getOutgoingTypeClause()}) = 0 AND 
        $VIEWED_RECEIPT_COUNT = 0 AND 
        $TABLE_NAME.$READ = 0
      """.toSingleLine()

    return readableDatabase
      .rawQuery(query, null)
      .readToList { RecipientId.from(it.getLong(0)) }
  }

  fun hasFailedOutgoingStory(): Boolean {
    val where = "$IS_STORY_CLAUSE AND (${getOutgoingTypeClause()}) AND $NOTIFIED = 0 AND ($TYPE & ${MessageTypes.BASE_TYPE_MASK}) = ${MessageTypes.BASE_SENT_FAILED_TYPE}"
    return readableDatabase.exists(TABLE_NAME).where(where).run()
  }

  fun getOrderedStoryRecipientsAndIds(isOutgoingOnly: Boolean): List<StoryResult> {
    val query = """
      SELECT
        $TABLE_NAME.$DATE_SENT AS sent_timestamp,
        $TABLE_NAME.$ID AS mms_id,
        ${ThreadTable.TABLE_NAME}.${ThreadTable.RECIPIENT_ID},
        (${getOutgoingTypeClause()}) AS is_outgoing,
        $VIEWED_RECEIPT_COUNT,
        $TABLE_NAME.$DATE_SENT,
        $RECEIPT_TIMESTAMP,
        (${getOutgoingTypeClause()}) = 0 AND $VIEWED_RECEIPT_COUNT = 0 AS is_unread
        FROM $TABLE_NAME 
          JOIN ${ThreadTable.TABLE_NAME} ON $TABLE_NAME.$THREAD_ID = ${ThreadTable.TABLE_NAME}.${ThreadTable.ID}
        WHERE
          $STORY_TYPE > 0 AND 
          $REMOTE_DELETED = 0
          ${if (isOutgoingOnly) " AND is_outgoing != 0" else ""}
        ORDER BY
          is_unread DESC,
          CASE
            WHEN is_outgoing = 0 AND $VIEWED_RECEIPT_COUNT = 0 THEN $TABLE_NAME.$DATE_SENT
            WHEN is_outgoing = 0 AND viewed_receipt_count > 0 THEN $RECEIPT_TIMESTAMP
            WHEN is_outgoing = 1 THEN $TABLE_NAME.$DATE_SENT
          END DESC
      """.toSingleLine()

    return readableDatabase
      .rawQuery(query, null)
      .readToList { cursor ->
        StoryResult(
          RecipientId.from(CursorUtil.requireLong(cursor, ThreadTable.RECIPIENT_ID)),
          CursorUtil.requireLong(cursor, "mms_id"),
          CursorUtil.requireLong(cursor, "sent_timestamp"),
          CursorUtil.requireBoolean(cursor, "is_outgoing")
        )
      }
  }

  fun getStoryReplies(parentStoryId: Long): Cursor {
    val where = "$PARENT_STORY_ID = ?"
    val whereArgs = buildArgs(parentStoryId)
    return rawQueryWithAttachments(where, whereArgs, false, 0)
  }

  fun getNumberOfStoryReplies(parentStoryId: Long): Int {
    return readableDatabase
      .select("COUNT(*)")
      .from(TABLE_NAME)
      .where("$PARENT_STORY_ID = ?", parentStoryId)
      .run()
      .readToSingleInt()
  }

  fun containsStories(threadId: Long): Boolean {
    return readableDatabase
      .exists(TABLE_NAME)
      .where("$THREAD_ID = ? AND $STORY_TYPE > 0", threadId)
      .run()
  }

  fun hasSelfReplyInStory(parentStoryId: Long): Boolean {
    return readableDatabase
      .exists(TABLE_NAME)
      .where("$PARENT_STORY_ID = ? AND (${getOutgoingTypeClause()})", -parentStoryId)
      .run()
  }

  fun hasGroupReplyOrReactionInStory(parentStoryId: Long): Boolean {
    return hasSelfReplyInStory(-parentStoryId)
  }

  fun getOldestStorySendTimestamp(hasSeenReleaseChannelStories: Boolean): Long? {
    val releaseChannelThreadId = getReleaseChannelThreadId(hasSeenReleaseChannelStories)

    return readableDatabase
      .select(DATE_SENT)
      .from(TABLE_NAME)
      .where("$IS_STORY_CLAUSE AND $THREAD_ID != ?", releaseChannelThreadId)
      .limit(1)
      .orderBy("$DATE_SENT ASC")
      .run()
      .readToSingleObject { it.getLong(0) }
  }

  @VisibleForTesting
  fun deleteGroupStoryReplies(parentStoryId: Long) {
    writableDatabase
      .delete(TABLE_NAME)
      .where("$PARENT_STORY_ID = ?", parentStoryId)
      .run()
  }

  fun deleteStoriesOlderThan(timestamp: Long, hasSeenReleaseChannelStories: Boolean): Int {
    return writableDatabase.withinTransaction { db ->
      val releaseChannelThreadId = getReleaseChannelThreadId(hasSeenReleaseChannelStories)
      val storiesBeforeTimestampWhere = "$IS_STORY_CLAUSE AND $DATE_SENT < ? AND $THREAD_ID != ?"
      val sharedArgs = buildArgs(timestamp, releaseChannelThreadId)

      val deleteStoryRepliesQuery = """
        DELETE FROM $TABLE_NAME 
        WHERE 
          $PARENT_STORY_ID > 0 AND 
          $PARENT_STORY_ID IN (
            SELECT $ID 
            FROM $TABLE_NAME 
            WHERE $storiesBeforeTimestampWhere
          )
        """.toSingleLine()

      val disassociateQuoteQuery = """
        UPDATE $TABLE_NAME 
        SET 
          $QUOTE_MISSING = 1, 
          $QUOTE_BODY = '' 
        WHERE 
          $PARENT_STORY_ID < 0 AND 
          ABS($PARENT_STORY_ID) IN (
            SELECT $ID 
            FROM $TABLE_NAME 
            WHERE $storiesBeforeTimestampWhere
          )
        """.toSingleLine()

      db.execSQL(deleteStoryRepliesQuery, sharedArgs)
      db.execSQL(disassociateQuoteQuery, sharedArgs)

      db.select(RECIPIENT_ID)
        .from(TABLE_NAME)
        .where(storiesBeforeTimestampWhere, sharedArgs)
        .run()
        .readToList { RecipientId.from(it.requireLong(RECIPIENT_ID)) }
        .forEach { id -> ApplicationDependencies.getDatabaseObserver().notifyStoryObservers(id) }

      val deletedStoryCount = db.select(ID)
        .from(TABLE_NAME)
        .where(storiesBeforeTimestampWhere, sharedArgs)
        .run()
        .use { cursor ->
          while (cursor.moveToNext()) {
            deleteMessage(cursor.requireLong(ID))
          }

          cursor.count
        }

      if (deletedStoryCount > 0) {
        OptimizeMessageSearchIndexJob.enqueue()
      }

      deletedStoryCount
    }
  }

  private fun disassociateStoryQuotes(storyId: Long) {
    writableDatabase
      .update(TABLE_NAME)
      .values(
        QUOTE_MISSING to 1,
        QUOTE_BODY to null
      )
      .where("$PARENT_STORY_ID = ?", DirectReply(storyId).serialize())
      .run()
  }

  fun isGroupQuitMessage(messageId: Long): Boolean {
    val type = MessageTypes.getOutgoingEncryptedMessageType() or MessageTypes.GROUP_LEAVE_BIT

    return readableDatabase
      .exists(TABLE_NAME)
      .where("$ID = ? AND $TYPE & $type = $type AND $TYPE & ${MessageTypes.GROUP_V2_BIT} = 0", messageId)
      .run()
  }

  fun getLatestGroupQuitTimestamp(threadId: Long, quitTimeBarrier: Long): Long {
    val type = MessageTypes.getOutgoingEncryptedMessageType() or MessageTypes.GROUP_LEAVE_BIT

    return readableDatabase
      .select(DATE_SENT)
      .from(TABLE_NAME)
      .where("$THREAD_ID = ? AND $TYPE & $type = $type AND $TYPE & ${MessageTypes.GROUP_V2_BIT} = 0 AND $DATE_SENT < ?", threadId, quitTimeBarrier)
      .orderBy("$DATE_SENT DESC")
      .limit(1)
      .run()
      .readToSingleLong(-1)
  }

  fun getScheduledMessageCountForThread(threadId: Long): Int {
    return readableDatabase
      .select("COUNT(*)")
      .from("$TABLE_NAME INDEXED BY $INDEX_THREAD_STORY_SCHEDULED_DATE")
      .where("$THREAD_ID = ? AND $STORY_TYPE = ? AND $PARENT_STORY_ID <= ? AND $SCHEDULED_DATE != ?", threadId, 0, 0, -1)
      .run()
      .readToSingleInt()
  }

  fun getMessageCountForThread(threadId: Long): Int {
    return readableDatabase
      .select("COUNT(*)")
      .from("$TABLE_NAME INDEXED BY $INDEX_THREAD_STORY_SCHEDULED_DATE")
      .where("$THREAD_ID = ? AND $STORY_TYPE = ? AND $PARENT_STORY_ID <= ? AND $SCHEDULED_DATE = ?", threadId, 0, 0, -1)
      .run()
      .readToSingleInt()
  }

  fun getMessageCountForThread(threadId: Long, beforeTime: Long): Int {
    return readableDatabase
      .select("COUNT(*)")
      .from("$TABLE_NAME INDEXED BY $INDEX_THREAD_STORY_SCHEDULED_DATE")
      .where("$THREAD_ID = ? AND $DATE_RECEIVED < ? AND $STORY_TYPE = ? AND $PARENT_STORY_ID <= ? AND $SCHEDULED_DATE = ?", threadId, beforeTime, 0, 0, -1)
      .run()
      .readToSingleInt()
  }

  fun canSetUniversalTimer(threadId: Long): Boolean {
    if (threadId == -1L) {
      return true
    }

    val meaningfulQuery = buildMeaningfulMessagesQuery(threadId)
    val isNotJoinedType = SqlUtil.buildQuery("$TYPE & ${MessageTypes.BASE_TYPE_MASK} != ${MessageTypes.JOINED_TYPE}")

    val query = meaningfulQuery and isNotJoinedType
    val hasMeaningfulMessages = readableDatabase
      .exists(TABLE_NAME)
      .where(query.where, query.whereArgs)
      .run()

    return !hasMeaningfulMessages
  }

  fun hasMeaningfulMessage(threadId: Long): Boolean {
    if (threadId == -1L) {
      return false
    }

    val query = buildMeaningfulMessagesQuery(threadId)
    return readableDatabase
      .exists(TABLE_NAME)
      .where(query.where, query.whereArgs)
      .run()
  }

  fun getIncomingMeaningfulMessageCountSince(threadId: Long, afterTime: Long): Int {
    val meaningfulMessagesQuery = buildMeaningfulMessagesQuery(threadId)
    val where = "${meaningfulMessagesQuery.where} AND $DATE_RECEIVED >= ? AND NOT (${getOutgoingTypeClause()})"
    val whereArgs = appendArg(meaningfulMessagesQuery.whereArgs, afterTime.toString())

    return readableDatabase
      .select("COUNT(*)")
      .from(TABLE_NAME)
      .where(where, whereArgs)
      .run()
      .readToSingleInt()
  }

  private fun buildMeaningfulMessagesQuery(threadId: Long): SqlUtil.Query {
    val query = """
      $THREAD_ID = ? AND
      $STORY_TYPE = 0 AND
      $PARENT_STORY_ID <= 0 AND
      (
        NOT $TYPE & ${MessageTypes.IGNORABLE_TYPESMASK_WHEN_COUNTING} AND
        $TYPE != ${MessageTypes.PROFILE_CHANGE_TYPE} AND
        $TYPE != ${MessageTypes.CHANGE_NUMBER_TYPE} AND
        $TYPE != ${MessageTypes.SMS_EXPORT_TYPE} AND
        $TYPE != ${MessageTypes.BOOST_REQUEST_TYPE} AND
        $TYPE & ${MessageTypes.GROUP_V2_LEAVE_BITS} != ${MessageTypes.GROUP_V2_LEAVE_BITS}
      )
    """.toSingleLine()

    return SqlUtil.buildQuery(query, threadId)
  }

  fun setNetworkFailures(messageId: Long, failures: Set<NetworkFailure?>?) {
    try {
      setDocument(databaseHelper.signalWritableDatabase, messageId, NETWORK_FAILURES, NetworkFailureSet(failures))
    } catch (e: IOException) {
      Log.w(TAG, e)
    }
  }

  fun getThreadIdForMessage(id: Long): Long {
    return readableDatabase
      .select(THREAD_ID)
      .from(TABLE_NAME)
      .where("$ID = ?", id)
      .run()
      .readToSingleLong(-1)
  }

  private fun getThreadIdFor(retrieved: IncomingMediaMessage): Long {
    return if (retrieved.groupId != null) {
      val groupRecipientId = recipients.getOrInsertFromPossiblyMigratedGroupId(retrieved.groupId)
      val groupRecipients = Recipient.resolved(groupRecipientId)
      threads.getOrCreateThreadIdFor(groupRecipients)
    } else {
      val sender = Recipient.resolved(retrieved.from!!)
      threads.getOrCreateThreadIdFor(sender)
    }
  }

  private fun getThreadIdFor(notification: NotificationInd): Long {
    val fromString = if (notification.from != null && notification.from.textString != null) {
      Util.toIsoString(notification.from.textString)
    } else {
      ""
    }

    val recipient = Recipient.external(context, fromString)
    return threads.getOrCreateThreadIdFor(recipient)
  }

  private fun rawQueryWithAttachments(where: String, arguments: Array<String>?, reverse: Boolean = false, limit: Long = 0): Cursor {
    return rawQueryWithAttachments(MMS_PROJECTION_WITH_ATTACHMENTS, where, arguments, reverse, limit)
  }

  private fun rawQueryWithAttachments(projection: Array<String>, where: String, arguments: Array<String>?, reverse: Boolean, limit: Long): Cursor {
    val database = databaseHelper.signalReadableDatabase
    var rawQueryString = """
      SELECT 
        ${Util.join(projection, ",")} 
      FROM 
        $TABLE_NAME LEFT OUTER JOIN ${AttachmentTable.TABLE_NAME} ON ($TABLE_NAME.$ID = ${AttachmentTable.TABLE_NAME}.${AttachmentTable.MMS_ID}) 
      WHERE 
        $where 
      GROUP BY 
        $TABLE_NAME.$ID
    """.toSingleLine()

    if (reverse) {
      rawQueryString += " ORDER BY $TABLE_NAME.$ID DESC"
    }

    if (limit > 0) {
      rawQueryString += " LIMIT $limit"
    }

    return database.rawQuery(rawQueryString, arguments)
  }

  private fun internalGetMessage(messageId: Long): Cursor {
    return rawQueryWithAttachments(RAW_ID_WHERE, buildArgs(messageId))
  }

  @Throws(NoSuchMessageException::class)
  fun getMessageRecord(messageId: Long): MessageRecord {
    rawQueryWithAttachments(RAW_ID_WHERE, arrayOf(messageId.toString() + "")).use { cursor ->
      return MmsReader(cursor).getNext() ?: throw NoSuchMessageException("No message for ID: $messageId")
    }
  }

  fun getMessageRecordOrNull(messageId: Long): MessageRecord? {
    rawQueryWithAttachments(RAW_ID_WHERE, buildArgs(messageId)).use { cursor ->
      return MmsReader(cursor).firstOrNull()
    }
  }

  fun getMessages(messageIds: Collection<Long?>): MmsReader {
    val ids = TextUtils.join(",", messageIds)
    return mmsReaderFor(rawQueryWithAttachments("$TABLE_NAME.$ID IN ($ids)", null))
  }

  private fun updateMailboxBitmask(id: Long, maskOff: Long, maskOn: Long, threadId: Optional<Long>) {
    writableDatabase.withinTransaction { db ->
      db.execSQL(
        """
          UPDATE $TABLE_NAME 
          SET $TYPE = ($TYPE & ${MessageTypes.TOTAL_MASK - maskOff} | $maskOn ) 
          WHERE $ID = ?
        """.toSingleLine(),
        buildArgs(id)
      )

      if (threadId.isPresent) {
        threads.updateSnippetTypeSilently(threadId.get())
      }
    }
  }

  fun markAsOutbox(messageId: Long) {
    val threadId = getThreadIdForMessage(messageId)
    updateMailboxBitmask(messageId, MessageTypes.BASE_TYPE_MASK, MessageTypes.BASE_OUTBOX_TYPE, Optional.of(threadId))
  }

  fun markAsForcedSms(messageId: Long) {
    val threadId = getThreadIdForMessage(messageId)
    updateMailboxBitmask(messageId, MessageTypes.PUSH_MESSAGE_BIT, MessageTypes.MESSAGE_FORCE_SMS_BIT, Optional.of(threadId))
    ApplicationDependencies.getDatabaseObserver().notifyMessageUpdateObservers(MessageId(messageId))
  }

  fun markAsRateLimited(messageId: Long) {
    val threadId = getThreadIdForMessage(messageId)
    updateMailboxBitmask(messageId, 0, MessageTypes.MESSAGE_RATE_LIMITED_BIT, Optional.of(threadId))
    ApplicationDependencies.getDatabaseObserver().notifyMessageUpdateObservers(MessageId(messageId))
  }

  fun clearRateLimitStatus(ids: Collection<Long>) {
    writableDatabase.withinTransaction {
      for (id in ids) {
        val threadId = getThreadIdForMessage(id)
        updateMailboxBitmask(id, MessageTypes.MESSAGE_RATE_LIMITED_BIT, 0, Optional.of(threadId))
      }
    }
  }

  fun markAsPendingInsecureSmsFallback(messageId: Long) {
    val threadId = getThreadIdForMessage(messageId)
    updateMailboxBitmask(messageId, MessageTypes.BASE_TYPE_MASK, MessageTypes.BASE_PENDING_INSECURE_SMS_FALLBACK, Optional.of(threadId))
    ApplicationDependencies.getDatabaseObserver().notifyMessageUpdateObservers(MessageId(messageId))
  }

  fun markAsSending(messageId: Long) {
    val threadId = getThreadIdForMessage(messageId)
    updateMailboxBitmask(messageId, MessageTypes.BASE_TYPE_MASK, MessageTypes.BASE_SENDING_TYPE, Optional.of(threadId))
    ApplicationDependencies.getDatabaseObserver().notifyMessageUpdateObservers(MessageId(messageId))
    ApplicationDependencies.getDatabaseObserver().notifyConversationListListeners()
  }

  fun markAsSentFailed(messageId: Long) {
    val threadId = getThreadIdForMessage(messageId)
    updateMailboxBitmask(messageId, MessageTypes.BASE_TYPE_MASK, MessageTypes.BASE_SENT_FAILED_TYPE, Optional.of(threadId))
    ApplicationDependencies.getDatabaseObserver().notifyMessageUpdateObservers(MessageId(messageId))
    ApplicationDependencies.getDatabaseObserver().notifyConversationListListeners()
  }

  fun markAsSent(messageId: Long, secure: Boolean) {
    val threadId = getThreadIdForMessage(messageId)
    updateMailboxBitmask(messageId, MessageTypes.BASE_TYPE_MASK, MessageTypes.BASE_SENT_TYPE or if (secure) MessageTypes.PUSH_MESSAGE_BIT or MessageTypes.SECURE_MESSAGE_BIT else 0, Optional.of(threadId))
    ApplicationDependencies.getDatabaseObserver().notifyMessageUpdateObservers(MessageId(messageId))
    ApplicationDependencies.getDatabaseObserver().notifyConversationListListeners()
  }

  fun markAsRemoteDelete(messageId: Long) {
    var deletedAttachments = false
    writableDatabase.withinTransaction { db ->
      db.update(TABLE_NAME)
        .values(
          REMOTE_DELETED to 1,
          BODY to null,
          QUOTE_BODY to null,
          QUOTE_AUTHOR to null,
          QUOTE_TYPE to null,
          QUOTE_ID to null,
          LINK_PREVIEWS to null,
          SHARED_CONTACTS to null
        )
        .where("$ID = ?", messageId)
        .run()

      deletedAttachments = attachments.deleteAttachmentsForMessage(messageId)
      mentions.deleteMentionsForMessage(messageId)
      messageLog.deleteAllRelatedToMessage(messageId)
      reactions.deleteReactions(MessageId(messageId))
      deleteGroupStoryReplies(messageId)
      disassociateStoryQuotes(messageId)

      val threadId = getThreadIdForMessage(messageId)
      threads.update(threadId, false)
    }

    OptimizeMessageSearchIndexJob.enqueue()
    ApplicationDependencies.getDatabaseObserver().notifyMessageUpdateObservers(MessageId(messageId))
    ApplicationDependencies.getDatabaseObserver().notifyConversationListListeners()

    if (deletedAttachments) {
      ApplicationDependencies.getDatabaseObserver().notifyAttachmentObservers()
    }
  }

  fun markDownloadState(messageId: Long, state: Long) {
    writableDatabase
      .update(TABLE_NAME)
      .values(MMS_STATUS to state)
      .where("$ID = ?", messageId)
      .run()

    ApplicationDependencies.getDatabaseObserver().notifyMessageUpdateObservers(MessageId(messageId))
  }

  fun clearScheduledStatus(threadId: Long, messageId: Long, expiresIn: Long): Boolean {
    val rowsUpdated = writableDatabase
      .update(TABLE_NAME)
      .values(
        SCHEDULED_DATE to -1,
        DATE_SENT to System.currentTimeMillis(),
        DATE_RECEIVED to System.currentTimeMillis(),
        EXPIRES_IN to expiresIn
      )
      .where("$ID = ? AND $SCHEDULED_DATE != ?", messageId, -1)
      .run()

    ApplicationDependencies.getDatabaseObserver().notifyMessageInsertObservers(threadId, MessageId(messageId))
    ApplicationDependencies.getDatabaseObserver().notifyScheduledMessageObservers(threadId)

    return rowsUpdated > 0
  }

  fun rescheduleMessage(threadId: Long, messageId: Long, time: Long) {
    val rowsUpdated = writableDatabase
      .update(TABLE_NAME)
      .values(SCHEDULED_DATE to time)
      .where("$ID = ? AND $SCHEDULED_DATE != ?", messageId, -1)
      .run()

    ApplicationDependencies.getDatabaseObserver().notifyScheduledMessageObservers(threadId)
    ApplicationDependencies.getScheduledMessageManager().scheduleIfNecessary()

    if (rowsUpdated == 0) {
      Log.w(TAG, "Failed to reschedule messageId=$messageId to new time $time. may have been sent already")
    }
  }

  fun markAsInsecure(messageId: Long) {
    updateMailboxBitmask(messageId, MessageTypes.SECURE_MESSAGE_BIT, 0, Optional.empty())
  }

  fun markUnidentified(messageId: Long, unidentified: Boolean) {
    writableDatabase
      .update(TABLE_NAME)
      .values(UNIDENTIFIED to if (unidentified) 1 else 0)
      .where("$ID = ?", messageId)
      .run()
  }

  @JvmOverloads
  fun markExpireStarted(id: Long, startedTimestamp: Long = System.currentTimeMillis()) {
    markExpireStarted(setOf(id), startedTimestamp)
  }

  fun markExpireStarted(ids: Collection<Long>, startedAtTimestamp: Long) {
    var threadId: Long = -1
    writableDatabase.withinTransaction { db ->
      for (id in ids) {
        db.update(TABLE_NAME)
          .values(EXPIRE_STARTED to startedAtTimestamp)
          .where("$ID = ? AND ($EXPIRE_STARTED = 0 OR $EXPIRE_STARTED > ?)", id, startedAtTimestamp)
          .run()

        if (threadId < 0) {
          threadId = getThreadIdForMessage(id)
        }
      }

      threads.update(threadId, false)
    }

    notifyConversationListeners(threadId)
  }

  fun markAsNotified(id: Long) {
    writableDatabase
      .update(TABLE_NAME)
      .values(
        NOTIFIED to 1,
        REACTIONS_LAST_SEEN to System.currentTimeMillis()
      )
      .where("$ID = ?", id)
      .run()
  }

  fun markAsNotNotified(id: Long) {
    writableDatabase
      .update(TABLE_NAME)
      .values(NOTIFIED to 0)
      .where("$ID = ?", id)
      .run()
  }

  fun setMessagesReadSince(threadId: Long, sinceTimestamp: Long): List<MarkedMessageInfo> {
    var query = """
      $THREAD_ID = ? AND 
      $STORY_TYPE = 0 AND 
      $PARENT_STORY_ID <= 0 AND 
      (
        $READ = 0 OR 
        (
          $REACTIONS_UNREAD = 1 AND 
          (${getOutgoingTypeClause()})
        )
      )
      """.toSingleLine()

    val args = mutableListOf(threadId.toString())

    if (sinceTimestamp >= 0L) {
      query += " AND $DATE_RECEIVED <= ?"
      args += sinceTimestamp.toString()
    }

    return setMessagesRead(query, args.toTypedArray())
  }

  fun setGroupStoryMessagesReadSince(threadId: Long, groupStoryId: Long, sinceTimestamp: Long): List<MarkedMessageInfo> {
    var query = """
      $THREAD_ID = ? AND 
      $STORY_TYPE = 0 AND 
      $PARENT_STORY_ID = ? AND 
      (
        $READ = 0 OR 
        (
          $REACTIONS_UNREAD = 1 AND 
          (${getOutgoingTypeClause()})
        )
      )
      """.toSingleLine()

    val args = mutableListOf(threadId.toString(), groupStoryId.toString())

    if (sinceTimestamp >= 0L) {
      query += " AND $DATE_RECEIVED <= ?"
      args += sinceTimestamp.toString()
    }

    return setMessagesRead(query, args.toTypedArray())
  }

  fun getStoryTypes(messageIds: List<MessageId>): List<StoryType> {
    if (messageIds.isEmpty()) {
      return emptyList()
    }

    val rawMessageIds: List<Long> = messageIds.map { it.id }
    val storyTypes: MutableMap<Long, StoryType> = mutableMapOf()

    SqlUtil.buildCollectionQuery(ID, rawMessageIds).forEach { query ->
      readableDatabase
        .select(ID, STORY_TYPE)
        .from(TABLE_NAME)
        .where(query.where, query.whereArgs)
        .run()
        .use { cursor ->
          while (cursor.moveToNext()) {
            storyTypes[cursor.requireLong(ID)] = fromCode(cursor.requireInt(STORY_TYPE))
          }
        }
    }

    return rawMessageIds.map { id: Long ->
      if (storyTypes.containsKey(id)) {
        storyTypes[id]!!
      } else {
        StoryType.NONE
      }
    }
  }

  fun setEntireThreadRead(threadId: Long): List<MarkedMessageInfo> {
    return setMessagesRead("$THREAD_ID = ? AND $STORY_TYPE = 0 AND $PARENT_STORY_ID <= 0", buildArgs(threadId))
  }

  fun setAllMessagesRead(): List<MarkedMessageInfo> {
    return setMessagesRead("$STORY_TYPE = 0 AND $PARENT_STORY_ID <= 0 AND ($READ = 0 OR ($REACTIONS_UNREAD = 1 AND (${getOutgoingTypeClause()})))", null)
  }

  private fun setMessagesRead(where: String, arguments: Array<String>?): List<MarkedMessageInfo> {
    val releaseChannelId = SignalStore.releaseChannelValues().releaseChannelRecipientId
    return writableDatabase.withinTransaction { db ->
      val infos = db
        .select(ID, RECIPIENT_ID, DATE_SENT, TYPE, EXPIRES_IN, EXPIRE_STARTED, THREAD_ID, STORY_TYPE)
        .from("$TABLE_NAME INDEXED BY $INDEX_THREAD_DATE")
        .where(where, arguments ?: emptyArray())
        .run()
        .readToList { cursor ->
          val threadId = cursor.requireLong(THREAD_ID)
          val recipientId = RecipientId.from(cursor.requireLong(RECIPIENT_ID))
          val dateSent = cursor.requireLong(DATE_SENT)
          val messageId = cursor.requireLong(ID)
          val expiresIn = cursor.requireLong(EXPIRES_IN)
          val expireStarted = cursor.requireLong(EXPIRE_STARTED)
          val syncMessageId = SyncMessageId(recipientId, dateSent)
          val expirationInfo = ExpirationInfo(messageId, expiresIn, expireStarted, true)
          val storyType = fromCode(CursorUtil.requireInt(cursor, STORY_TYPE))

          if (recipientId != releaseChannelId) {
            MarkedMessageInfo(threadId, syncMessageId, MessageId(messageId), expirationInfo, storyType)
          } else {
            null
          }
        }
        .filterNotNull()

      db.update("$TABLE_NAME INDEXED BY $INDEX_THREAD_DATE")
        .values(
          READ to 1,
          REACTIONS_UNREAD to 0,
          REACTIONS_LAST_SEEN to System.currentTimeMillis()
        )
        .where(where, arguments ?: emptyArray())
        .run()

      infos
    }
  }

  fun getOldestUnreadMentionDetails(threadId: Long): Pair<RecipientId, Long>? {
    return readableDatabase
      .select(RECIPIENT_ID, DATE_RECEIVED)
      .from(TABLE_NAME)
      .where("$THREAD_ID = ? AND $READ = 0 AND $MENTIONS_SELF = 1", threadId)
      .orderBy("$DATE_RECEIVED ASC")
      .limit(1)
      .run()
      .readToSingleObject { cursor ->
        Pair(
          RecipientId.from(cursor.requireLong(RECIPIENT_ID)),
          cursor.requireLong(DATE_RECEIVED)
        )
      }
  }

  fun getUnreadMentionCount(threadId: Long): Int {
    return readableDatabase
      .select("COUNT(*)")
      .from(TABLE_NAME)
      .where("$THREAD_ID = ? AND $READ = 0 AND $MENTIONS_SELF = 1", threadId)
      .run()
      .readToSingleInt()
  }

  /**
   * Trims data related to expired messages. Only intended to be run after a backup restore.
   */
  fun trimEntriesForExpiredMessages() {
    writableDatabase
      .delete(GroupReceiptTable.TABLE_NAME)
      .where("${GroupReceiptTable.MMS_ID} NOT IN (SELECT $ID FROM $TABLE_NAME)")
      .run()

    readableDatabase
      .select(AttachmentTable.ROW_ID, AttachmentTable.UNIQUE_ID)
      .from(AttachmentTable.TABLE_NAME)
      .where("${AttachmentTable.MMS_ID} NOT IN (SELECT $ID FROM $TABLE_NAME)")
      .run()
      .forEach { cursor ->
        attachments.deleteAttachment(AttachmentId(cursor.requireLong(AttachmentTable.ROW_ID), cursor.requireLong(AttachmentTable.UNIQUE_ID)))
      }

    mentions.deleteAbandonedMentions()

    readableDatabase
      .select(ThreadTable.ID)
      .from(ThreadTable.TABLE_NAME)
      .where("${ThreadTable.EXPIRES_IN} > 0")
      .run()
      .forEach { cursor ->
        val id = cursor.requireLong(ThreadTable.ID)
        threads.setLastScrolled(id, 0)
        threads.update(id, false)
      }
  }

  fun getNotification(messageId: Long): Optional<MmsNotificationInfo> {
    return readableDatabase
      .select(RECIPIENT_ID, MMS_CONTENT_LOCATION, MMS_TRANSACTION_ID, SMS_SUBSCRIPTION_ID)
      .from(TABLE_NAME)
      .where("$ID = ?", messageId)
      .run()
      .readToSingleObject { cursor ->
        MmsNotificationInfo(
          from = RecipientId.from(cursor.requireLong(RECIPIENT_ID)),
          contentLocation = cursor.requireNonNullString(MMS_CONTENT_LOCATION),
          transactionId = cursor.requireNonNullString(MMS_TRANSACTION_ID),
          subscriptionId = cursor.requireInt(SMS_SUBSCRIPTION_ID)
        )
      }
      .toOptional()
  }

  @Throws(MmsException::class, NoSuchMessageException::class)
  fun getOutgoingMessage(messageId: Long): OutgoingMessage {
    return rawQueryWithAttachments(RAW_ID_WHERE, arrayOf(messageId.toString())).readToSingleObject { cursor ->
      val associatedAttachments = attachments.getAttachmentsForMessage(messageId)
      val mentions = mentions.getMentionsForMessage(messageId)
      val outboxType = cursor.requireLong(TYPE)
      val body = cursor.requireString(BODY)
      val timestamp = cursor.requireLong(DATE_SENT)
      val subscriptionId = cursor.requireInt(SMS_SUBSCRIPTION_ID)
      val expiresIn = cursor.requireLong(EXPIRES_IN)
      val viewOnce = cursor.requireLong(VIEW_ONCE) == 1L
      val recipient = Recipient.resolved(RecipientId.from(cursor.requireLong(RECIPIENT_ID)))
      val threadId = cursor.requireLong(THREAD_ID)
      val distributionType = threads.getDistributionType(threadId)
      val storyType = StoryType.fromCode(cursor.requireInt(STORY_TYPE))
      val parentStoryId = ParentStoryId.deserialize(cursor.requireLong(PARENT_STORY_ID))
      val messageRangesData = cursor.requireBlob(MESSAGE_RANGES)
      val scheduledDate = cursor.requireLong(SCHEDULED_DATE)

      val quoteId = cursor.requireLong(QUOTE_ID)
      val quoteAuthor = cursor.requireLong(QUOTE_AUTHOR)
      val quoteText = cursor.requireString(QUOTE_BODY)
      val quoteType = cursor.requireInt(QUOTE_TYPE)
      val quoteMissing = cursor.requireBoolean(QUOTE_MISSING)
      val quoteAttachments: List<Attachment> = associatedAttachments.filter { it.isQuote }.toList()
      val quoteMentions: List<Mention> = parseQuoteMentions(cursor)
      val quoteBodyRanges: BodyRangeList? = parseQuoteBodyRanges(cursor)
      val quote: QuoteModel? = if (quoteId > 0 && quoteAuthor > 0 && (!TextUtils.isEmpty(quoteText) || quoteAttachments.isNotEmpty())) {
        QuoteModel(quoteId, RecipientId.from(quoteAuthor), quoteText ?: "", quoteMissing, quoteAttachments, quoteMentions, QuoteModel.Type.fromCode(quoteType), quoteBodyRanges)
      } else {
        null
      }

      val contacts: List<Contact> = getSharedContacts(cursor, associatedAttachments)
      val contactAttachments: Set<Attachment> = contacts.mapNotNull { it.avatarAttachment }.toSet()
      val previews: List<LinkPreview> = getLinkPreviews(cursor, associatedAttachments)
      val previewAttachments: Set<Attachment> = previews.filter { it.thumbnail.isPresent }.map { it.thumbnail.get() }.toSet()
      val attachments: List<Attachment> = associatedAttachments
        .filterNot { it.isQuote }
        .filterNot { contactAttachments.contains(it) }
        .filterNot { previewAttachments.contains(it) }
        .sortedWith(DisplayOrderComparator())

      val mismatchDocument = cursor.requireString(MISMATCHED_IDENTITIES)
      val mismatches: Set<IdentityKeyMismatch> = if (!TextUtils.isEmpty(mismatchDocument)) {
        try {
          JsonUtils.fromJson(mismatchDocument, IdentityKeyMismatchSet::class.java).items.toSet()
        } catch (e: IOException) {
          Log.w(TAG, e)
          setOf()
        }
      } else {
        setOf()
      }

      val networkDocument = cursor.requireString(NETWORK_FAILURES)
      val networkFailures: Set<NetworkFailure> = if (!TextUtils.isEmpty(networkDocument)) {
        try {
          JsonUtils.fromJson(networkDocument, NetworkFailureSet::class.java).items.toSet()
        } catch (e: IOException) {
          Log.w(TAG, e)
          setOf()
        }
      } else {
        setOf()
      }

      if (body != null && (MessageTypes.isGroupQuit(outboxType) || MessageTypes.isGroupUpdate(outboxType))) {
        OutgoingMessage.groupUpdateMessage(
          recipient = recipient,
          groupContext = MessageGroupContext(body, MessageTypes.isGroupV2(outboxType)),
          avatar = attachments,
          sentTimeMillis = timestamp,
          expiresIn = 0,
          viewOnce = false,
          quote = quote,
          contacts = contacts,
          previews = previews,
          mentions = mentions
        )
      } else if (MessageTypes.isExpirationTimerUpdate(outboxType)) {
        OutgoingMessage.expirationUpdateMessage(
          recipient = recipient,
          sentTimeMillis = timestamp,
          expiresIn = expiresIn
        )
      } else if (MessageTypes.isPaymentsNotification(outboxType)) {
        OutgoingMessage.paymentNotificationMessage(
          recipient = recipient,
          paymentUuid = body!!,
          sentTimeMillis = timestamp,
          expiresIn = expiresIn
        )
      } else if (MessageTypes.isPaymentsRequestToActivate(outboxType)) {
        OutgoingMessage.requestToActivatePaymentsMessage(
          recipient = recipient,
          sentTimeMillis = timestamp,
          expiresIn = expiresIn
        )
      } else if (MessageTypes.isPaymentsActivated(outboxType)) {
        OutgoingMessage.paymentsActivatedMessage(
          recipient = recipient,
          sentTimeMillis = timestamp,
          expiresIn = expiresIn
        )
      } else {
        val giftBadge: GiftBadge? = if (body != null && MessageTypes.isGiftBadge(outboxType)) {
          GiftBadge.parseFrom(Base64.decode(body))
        } else {
          null
        }

        val messageRanges: BodyRangeList? = if (messageRangesData != null) {
          try {
            BodyRangeList.parseFrom(messageRangesData)
          } catch (e: InvalidProtocolBufferException) {
            Log.w(TAG, "Error parsing message ranges", e)
            null
          }
        } else {
          null
        }

        OutgoingMessage(
          recipient = recipient,
          body = body,
          attachments = attachments,
          timestamp = timestamp,
          subscriptionId = subscriptionId,
          expiresIn = expiresIn,
          viewOnce = viewOnce,
          distributionType = distributionType,
          storyType = storyType,
          parentStoryId = parentStoryId,
          isStoryReaction = MessageTypes.isStoryReaction(outboxType),
          quote = quote,
          contacts = contacts,
          previews = previews,
          mentions = mentions,
          networkFailures = networkFailures,
          mismatches = mismatches,
          giftBadge = giftBadge,
          isSecure = MessageTypes.isSecureType(outboxType),
          bodyRanges = messageRanges,
          scheduledDate = scheduledDate
        )
      }
    } ?: throw NoSuchMessageException("No record found for id: $messageId")
  }

  @Throws(MmsException::class)
  private fun insertMessageInbox(
    retrieved: IncomingMediaMessage,
    contentLocation: String,
    candidateThreadId: Long,
    mailbox: Long
  ): Optional<InsertResult> {
    val threadId = if (candidateThreadId == -1L || retrieved.isGroupMessage) {
      getThreadIdFor(retrieved)
    } else {
      candidateThreadId
    }

    if (retrieved.isPushMessage && isDuplicate(retrieved, threadId)) {
      Log.w(TAG, "Ignoring duplicate media message (" + retrieved.sentTimeMillis + ")")
      return Optional.empty()
    }

    val silentUpdate = mailbox and MessageTypes.GROUP_UPDATE_BIT > 0

    val contentValues = contentValuesOf(
      DATE_SENT to retrieved.sentTimeMillis,
      DATE_SERVER to retrieved.serverTimeMillis,
      RECIPIENT_ID to retrieved.from!!.serialize(),
      TYPE to mailbox,
      MMS_MESSAGE_TYPE to PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF,
      THREAD_ID to threadId,
      MMS_CONTENT_LOCATION to contentLocation,
      MMS_STATUS to MmsStatus.DOWNLOAD_INITIALIZED,
      DATE_RECEIVED to if (retrieved.isPushMessage) retrieved.receivedTimeMillis else generatePduCompatTimestamp(retrieved.receivedTimeMillis),
      SMS_SUBSCRIPTION_ID to retrieved.subscriptionId,
      EXPIRES_IN to retrieved.expiresIn,
      VIEW_ONCE to if (retrieved.isViewOnce) 1 else 0,
      STORY_TYPE to retrieved.storyType.code,
      PARENT_STORY_ID to if (retrieved.parentStoryId != null) retrieved.parentStoryId.serialize() else 0,
      READ to if (silentUpdate || retrieved.isExpirationUpdate) 1 else 0,
      UNIDENTIFIED to retrieved.isUnidentified,
      SERVER_GUID to retrieved.serverGuid
    )

    val quoteAttachments: MutableList<Attachment> = mutableListOf()
    if (retrieved.quote != null) {
      contentValues.put(QUOTE_ID, retrieved.quote.id)
      contentValues.put(QUOTE_BODY, retrieved.quote.text)
      contentValues.put(QUOTE_AUTHOR, retrieved.quote.author.serialize())
      contentValues.put(QUOTE_TYPE, retrieved.quote.type.code)
      contentValues.put(QUOTE_MISSING, if (retrieved.quote.isOriginalMissing) 1 else 0)

      val quoteBodyRanges: BodyRangeList.Builder = retrieved.quote.bodyRanges?.toBuilder() ?: BodyRangeList.newBuilder()
      val mentionsList = MentionUtil.mentionsToBodyRangeList(retrieved.quote.mentions)

      if (mentionsList != null) {
        quoteBodyRanges.addAllRanges(mentionsList.rangesList)
      }

      if (quoteBodyRanges.rangesCount > 0) {
        contentValues.put(QUOTE_BODY_RANGES, quoteBodyRanges.build().toByteArray())
      }

      quoteAttachments += retrieved.quote.attachments
    }

    val messageId = insertMediaMessage(
      threadId = threadId,
      body = retrieved.body,
      attachments = retrieved.attachments,
      quoteAttachments = quoteAttachments,
      sharedContacts = retrieved.sharedContacts,
      linkPreviews = retrieved.linkPreviews,
      mentions = retrieved.mentions,
      messageRanges = retrieved.messageRanges,
      contentValues = contentValues,
      insertListener = null,
      updateThread = retrieved.storyType === StoryType.NONE,
      unarchive = true
    )

    val isNotStoryGroupReply = retrieved.parentStoryId == null || !retrieved.parentStoryId.isGroupReply()

    if (!MessageTypes.isPaymentsActivated(mailbox) && !MessageTypes.isPaymentsRequestToActivate(mailbox) && !MessageTypes.isExpirationTimerUpdate(mailbox) && !retrieved.storyType.isStory && isNotStoryGroupReply) {
      val incrementUnreadMentions = retrieved.mentions.isNotEmpty() && retrieved.mentions.any { it.recipientId == Recipient.self().id }
      threads.incrementUnread(threadId, 1, if (incrementUnreadMentions) 1 else 0)
      threads.update(threadId, true)
    }

    notifyConversationListeners(threadId)

    if (retrieved.storyType.isStory) {
      ApplicationDependencies.getDatabaseObserver().notifyStoryObservers(threads.getRecipientIdForThreadId(threadId)!!)
    }

    return Optional.of(InsertResult(messageId, threadId))
  }

  @Throws(MmsException::class)
  fun insertMessageInbox(
    retrieved: IncomingMediaMessage,
    contentLocation: String,
    threadId: Long
  ): Optional<InsertResult> {
    var type = MessageTypes.BASE_INBOX_TYPE

    if (retrieved.isPushMessage) {
      type = type or MessageTypes.PUSH_MESSAGE_BIT
    }

    if (retrieved.isExpirationUpdate) {
      type = type or MessageTypes.EXPIRATION_TIMER_UPDATE_BIT
    }

    if (retrieved.isPaymentsNotification) {
      type = type or MessageTypes.SPECIAL_TYPE_PAYMENTS_NOTIFICATION
    }

    if (retrieved.isActivatePaymentsRequest) {
      type = type or MessageTypes.SPECIAL_TYPE_PAYMENTS_ACTIVATE_REQUEST
    }

    if (retrieved.isPaymentsActivated) {
      type = type or MessageTypes.SPECIAL_TYPE_PAYMENTS_ACTIVATED
    }

    return insertMessageInbox(retrieved, contentLocation, threadId, type)
  }

  @Throws(MmsException::class)
  fun insertSecureDecryptedMessageInbox(retrieved: IncomingMediaMessage, threadId: Long): Optional<InsertResult> {
    var type = MessageTypes.BASE_INBOX_TYPE or MessageTypes.SECURE_MESSAGE_BIT
    var hasSpecialType = false

    if (retrieved.isPushMessage) {
      type = type or MessageTypes.PUSH_MESSAGE_BIT
    }

    if (retrieved.isExpirationUpdate) {
      type = type or MessageTypes.EXPIRATION_TIMER_UPDATE_BIT
    }

    if (retrieved.isStoryReaction) {
      type = type or MessageTypes.SPECIAL_TYPE_STORY_REACTION
      hasSpecialType = true
    }

    if (retrieved.giftBadge != null) {
      if (hasSpecialType) {
        throw MmsException("Cannot insert message with multiple special types.")
      }
      type = type or MessageTypes.SPECIAL_TYPE_GIFT_BADGE
      hasSpecialType = true
    }

    if (retrieved.isPaymentsNotification) {
      if (hasSpecialType) {
        throw MmsException("Cannot insert message with multiple special types.")
      }
      type = type or MessageTypes.SPECIAL_TYPE_PAYMENTS_NOTIFICATION
      hasSpecialType = true
    }

    if (retrieved.isActivatePaymentsRequest) {
      if (hasSpecialType) {
        throw MmsException("Cannot insert message with multiple special types.")
      }
      type = type or MessageTypes.SPECIAL_TYPE_PAYMENTS_ACTIVATE_REQUEST
      hasSpecialType = true
    }

    if (retrieved.isPaymentsActivated) {
      if (hasSpecialType) {
        throw MmsException("Cannot insert message with multiple special types.")
      }
      type = type or MessageTypes.SPECIAL_TYPE_PAYMENTS_ACTIVATED
      hasSpecialType = true
    }

    return insertMessageInbox(retrieved, "", threadId, type)
  }

  fun insertMessageInbox(notification: NotificationInd, subscriptionId: Int): Pair<Long, Long> {
    Log.i(TAG, "Message received type: " + notification.messageType)

    val threadId = getThreadIdFor(notification)

    val recipientId: String = if (notification.from != null) {
      Recipient.external(context, Util.toIsoString(notification.from.textString)).id.serialize()
    } else {
      RecipientId.UNKNOWN.serialize()
    }

    val messageId = writableDatabase
      .insertInto(TABLE_NAME)
      .values(
        MMS_CONTENT_LOCATION to notification.contentLocation.toIsoString(),
        DATE_SENT to System.currentTimeMillis(),
        MMS_EXPIRY to if (notification.expiry != -1L) notification.expiry else null,
        MMS_MESSAGE_SIZE to if (notification.messageSize != -1L) notification.messageSize else null,
        MMS_TRANSACTION_ID to notification.transactionId.toIsoString(),
        MMS_MESSAGE_TYPE to if (notification.messageType != 0) notification.messageType else null,
        RECIPIENT_ID to recipientId,
        TYPE to MessageTypes.BASE_INBOX_TYPE,
        THREAD_ID to threadId,
        MMS_STATUS to MmsStatus.DOWNLOAD_INITIALIZED,
        DATE_RECEIVED to generatePduCompatTimestamp(System.currentTimeMillis()),
        READ to if (Util.isDefaultSmsProvider(context)) 0 else 1,
        SMS_SUBSCRIPTION_ID to subscriptionId
      )
      .run()

    return Pair(messageId, threadId)
  }

  fun insertChatSessionRefreshedMessage(recipientId: RecipientId, senderDeviceId: Long, sentTimestamp: Long): InsertResult {
    val threadId = threads.getOrCreateThreadIdFor(Recipient.resolved(recipientId))
    var type = MessageTypes.SECURE_MESSAGE_BIT or MessageTypes.PUSH_MESSAGE_BIT
    type = type and MessageTypes.TOTAL_MASK - MessageTypes.ENCRYPTION_MASK or MessageTypes.ENCRYPTION_REMOTE_FAILED_BIT

    val messageId = writableDatabase
      .insertInto(TABLE_NAME)
      .values(
        RECIPIENT_ID to recipientId.serialize(),
        RECIPIENT_DEVICE_ID to senderDeviceId,
        DATE_RECEIVED to System.currentTimeMillis(),
        DATE_SENT to sentTimestamp,
        DATE_SERVER to -1,
        READ to 0,
        TYPE to type,
        THREAD_ID to threadId
      )
      .run()

    threads.incrementUnread(threadId, 1, 0)
    threads.update(threadId, true)

    notifyConversationListeners(threadId)
    TrimThreadJob.enqueueAsync(threadId)

    return InsertResult(messageId, threadId)
  }

  fun insertBadDecryptMessage(recipientId: RecipientId, senderDevice: Int, sentTimestamp: Long, receivedTimestamp: Long, threadId: Long) {
    writableDatabase
      .insertInto(TABLE_NAME)
      .values(
        RECIPIENT_ID to recipientId.serialize(),
        RECIPIENT_DEVICE_ID to senderDevice,
        DATE_SENT to sentTimestamp,
        DATE_RECEIVED to receivedTimestamp,
        DATE_SERVER to -1,
        READ to 0,
        TYPE to MessageTypes.BAD_DECRYPT_TYPE,
        THREAD_ID to threadId
      )
      .run()

    threads.incrementUnread(threadId, 1, 0)
    threads.update(threadId, true)

    notifyConversationListeners(threadId)
    TrimThreadJob.enqueueAsync(threadId)
  }

  fun markIncomingNotificationReceived(threadId: Long) {
    notifyConversationListeners(threadId)

    if (Util.isDefaultSmsProvider(context)) {
      threads.incrementUnread(threadId, 1, 0)
    }

    threads.update(threadId, true)
    TrimThreadJob.enqueueAsync(threadId)
  }

  fun markGiftRedemptionCompleted(messageId: Long) {
    markGiftRedemptionState(messageId, GiftBadge.RedemptionState.REDEEMED)
  }

  fun markGiftRedemptionStarted(messageId: Long) {
    markGiftRedemptionState(messageId, GiftBadge.RedemptionState.STARTED)
  }

  fun markGiftRedemptionFailed(messageId: Long) {
    markGiftRedemptionState(messageId, GiftBadge.RedemptionState.FAILED)
  }

  private fun markGiftRedemptionState(messageId: Long, redemptionState: GiftBadge.RedemptionState) {
    var updated = false
    var threadId: Long = -1

    writableDatabase.withinTransaction { db ->
      db.select(BODY, THREAD_ID)
        .from(TABLE_NAME)
        .where("($TYPE & ${MessageTypes.SPECIAL_TYPES_MASK} = ${MessageTypes.SPECIAL_TYPE_GIFT_BADGE}) AND $ID = ?", messageId)
        .run()
        .use { cursor ->
          if (cursor.moveToFirst()) {
            val giftBadge = GiftBadge.parseFrom(Base64.decode(cursor.requireNonNullString(BODY)))
            val updatedBadge = giftBadge.toBuilder().setRedemptionState(redemptionState).build()

            updated = db
              .update(TABLE_NAME)
              .values(BODY to Base64.encodeBytes(updatedBadge.toByteArray()))
              .where("$ID = ?", messageId)
              .run() > 0

            threadId = cursor.requireLong(THREAD_ID)
          }
        }
    }

    if (updated) {
      ApplicationDependencies.getDatabaseObserver().notifyMessageUpdateObservers(MessageId(messageId))
      notifyConversationListeners(threadId)
    }
  }

  @Throws(MmsException::class)
  fun insertMessageOutbox(
    message: OutgoingMessage,
    threadId: Long,
    forceSms: Boolean,
    insertListener: InsertListener?
  ): Long {
    return insertMessageOutbox(
      message = message,
      threadId = threadId,
      forceSms = forceSms,
      defaultReceiptStatus = GroupReceiptTable.STATUS_UNDELIVERED,
      insertListener = insertListener
    )
  }

  @Throws(MmsException::class)
  fun insertMessageOutbox(
    message: OutgoingMessage,
    threadId: Long,
    forceSms: Boolean,
    defaultReceiptStatus: Int,
    insertListener: InsertListener?
  ): Long {
    var type = MessageTypes.BASE_SENDING_TYPE
    var hasSpecialType = false

    if (message.isSecure) {
      type = type or (MessageTypes.SECURE_MESSAGE_BIT or MessageTypes.PUSH_MESSAGE_BIT)
    }

    if (forceSms) {
      type = type or MessageTypes.MESSAGE_FORCE_SMS_BIT
    }

    if (message.isSecure) {
      type = type or (MessageTypes.SECURE_MESSAGE_BIT or MessageTypes.PUSH_MESSAGE_BIT)
    } else if (message.isEndSession) {
      type = type or MessageTypes.END_SESSION_BIT
    }

    if (message.isIdentityVerified) {
      type = type or MessageTypes.KEY_EXCHANGE_IDENTITY_VERIFIED_BIT
    } else if (message.isIdentityDefault) {
      type = type or MessageTypes.KEY_EXCHANGE_IDENTITY_DEFAULT_BIT
    }

    if (message.isGroup) {
      if (message.isV2Group) {
        type = type or (MessageTypes.GROUP_V2_BIT or MessageTypes.GROUP_UPDATE_BIT)

        if (message.isJustAGroupLeave) {
          type = type or MessageTypes.GROUP_LEAVE_BIT
        }
      } else {
        val properties = message.requireGroupV1Properties()

        if (properties.isUpdate) {
          type = type or MessageTypes.GROUP_UPDATE_BIT
        } else if (properties.isQuit) {
          type = type or MessageTypes.GROUP_LEAVE_BIT
        }
      }
    }

    if (message.isExpirationUpdate) {
      type = type or MessageTypes.EXPIRATION_TIMER_UPDATE_BIT
    }

    if (message.isStoryReaction) {
      type = type or MessageTypes.SPECIAL_TYPE_STORY_REACTION
      hasSpecialType = true
    }

    if (message.giftBadge != null) {
      if (hasSpecialType) {
        throw MmsException("Cannot insert message with multiple special types.")
      }
      type = type or MessageTypes.SPECIAL_TYPE_GIFT_BADGE
      hasSpecialType = true
    }

    if (message.isPaymentsNotification) {
      if (hasSpecialType) {
        throw MmsException("Cannot insert message with multiple special types.")
      }
      type = type or MessageTypes.SPECIAL_TYPE_PAYMENTS_NOTIFICATION
      hasSpecialType = true
    }

    if (message.isRequestToActivatePayments) {
      if (hasSpecialType) {
        throw MmsException("Cannot insert message with multiple special types.")
      }
      type = type or MessageTypes.SPECIAL_TYPE_PAYMENTS_ACTIVATE_REQUEST
      hasSpecialType = true
    }

    if (message.isPaymentsActivated) {
      if (hasSpecialType) {
        throw MmsException("Cannot insert message with multiple special types.")
      }
      type = type or MessageTypes.SPECIAL_TYPE_PAYMENTS_ACTIVATED
      hasSpecialType = true
    }

    val earlyDeliveryReceipts: Map<RecipientId, Receipt> = earlyDeliveryReceiptCache.remove(message.sentTimeMillis)

    if (earlyDeliveryReceipts.isNotEmpty()) {
      Log.w(TAG, "Found early delivery receipts for " + message.sentTimeMillis + ". Applying them.")
    }

    val contentValues = ContentValues()
    contentValues.put(DATE_SENT, message.sentTimeMillis)
    contentValues.put(MMS_MESSAGE_TYPE, PduHeaders.MESSAGE_TYPE_SEND_REQ)
    contentValues.put(TYPE, type)
    contentValues.put(THREAD_ID, threadId)
    contentValues.put(READ, 1)
    contentValues.put(DATE_RECEIVED, System.currentTimeMillis())
    contentValues.put(SMS_SUBSCRIPTION_ID, message.subscriptionId)
    contentValues.put(EXPIRES_IN, message.expiresIn)
    contentValues.put(VIEW_ONCE, message.isViewOnce)
    contentValues.put(RECIPIENT_ID, message.recipient.id.serialize())
    contentValues.put(DELIVERY_RECEIPT_COUNT, earlyDeliveryReceipts.values.sumOf { it.count })
    contentValues.put(RECEIPT_TIMESTAMP, earlyDeliveryReceipts.values.map { it.timestamp }.maxOrNull() ?: -1L)
    contentValues.put(STORY_TYPE, message.storyType.code)
    contentValues.put(PARENT_STORY_ID, if (message.parentStoryId != null) message.parentStoryId.serialize() else 0)
    contentValues.put(SCHEDULED_DATE, message.scheduledDate)

    if (message.recipient.isSelf && hasAudioAttachment(message.attachments)) {
      contentValues.put(VIEWED_RECEIPT_COUNT, 1L)
    }

    val quoteAttachments: MutableList<Attachment> = mutableListOf()

    if (message.outgoingQuote != null) {
      val updated = MentionUtil.updateBodyAndMentionsWithPlaceholders(message.outgoingQuote.text, message.outgoingQuote.mentions)

      contentValues.put(QUOTE_ID, message.outgoingQuote.id)
      contentValues.put(QUOTE_AUTHOR, message.outgoingQuote.author.serialize())
      contentValues.put(QUOTE_BODY, updated.bodyAsString)
      contentValues.put(QUOTE_TYPE, message.outgoingQuote.type.code)
      contentValues.put(QUOTE_MISSING, if (message.outgoingQuote.isOriginalMissing) 1 else 0)

      val adjustedQuoteBodyRanges = message.outgoingQuote.bodyRanges.adjustBodyRanges(updated.bodyAdjustments)
      val quoteBodyRanges: BodyRangeList.Builder = if (adjustedQuoteBodyRanges != null) {
        adjustedQuoteBodyRanges.toBuilder()
      } else {
        BodyRangeList.newBuilder()
      }

      val mentionsList = MentionUtil.mentionsToBodyRangeList(updated.mentions)
      if (mentionsList != null) {
        quoteBodyRanges.addAllRanges(mentionsList.rangesList)
      }

      if (quoteBodyRanges.rangesCount > 0) {
        contentValues.put(QUOTE_BODY_RANGES, quoteBodyRanges.build().toByteArray())
      }

      quoteAttachments += message.outgoingQuote.attachments
    }

    val updatedBodyAndMentions = MentionUtil.updateBodyAndMentionsWithPlaceholders(message.body, message.mentions)
    val bodyRanges = message.bodyRanges.adjustBodyRanges(updatedBodyAndMentions.bodyAdjustments)
    val messageId = insertMediaMessage(
      threadId = threadId,
      body = updatedBodyAndMentions.bodyAsString,
      attachments = message.attachments,
      quoteAttachments = quoteAttachments,
      sharedContacts = message.sharedContacts,
      linkPreviews = message.linkPreviews,
      mentions = updatedBodyAndMentions.mentions,
      messageRanges = bodyRanges,
      contentValues = contentValues,
      insertListener = insertListener,
      updateThread = false,
      unarchive = false
    )

    if (message.recipient.isGroup) {
      val members: MutableSet<RecipientId> = mutableSetOf()

      if (message.isGroupUpdate && message.isV2Group) {
        members += message.requireGroupV2Properties().allActivePendingAndRemovedMembers
          .distinct()
          .map { uuid -> RecipientId.from(ServiceId.from(uuid)) }
          .toList()

        members -= Recipient.self().id
      } else {
        members += groups.getGroupMembers(message.recipient.requireGroupId(), GroupTable.MemberSet.FULL_MEMBERS_EXCLUDING_SELF).map { it.id }
      }

      groupReceipts.insert(members, messageId, defaultReceiptStatus, message.sentTimeMillis)

      for (recipientId in earlyDeliveryReceipts.keys) {
        groupReceipts.update(recipientId, messageId, GroupReceiptTable.STATUS_DELIVERED, -1)
      }
    } else if (message.recipient.isDistributionList) {
      val members = distributionLists.getMembers(message.recipient.requireDistributionListId())

      groupReceipts.insert(members, messageId, defaultReceiptStatus, message.sentTimeMillis)

      for (recipientId in earlyDeliveryReceipts.keys) {
        groupReceipts.update(recipientId, messageId, GroupReceiptTable.STATUS_DELIVERED, -1)
      }
    }

    threads.updateLastSeenAndMarkSentAndLastScrolledSilenty(threadId)

    if (!message.storyType.isStory) {
      if (message.outgoingQuote == null) {
        ApplicationDependencies.getDatabaseObserver().notifyMessageInsertObservers(threadId, MessageId(messageId))
      } else {
        ApplicationDependencies.getDatabaseObserver().notifyConversationListeners(threadId)
      }

      if (message.scheduledDate != -1L) {
        ApplicationDependencies.getDatabaseObserver().notifyScheduledMessageObservers(threadId)
      }
    } else {
      ApplicationDependencies.getDatabaseObserver().notifyStoryObservers(message.recipient.id)
    }

    notifyConversationListListeners()

    if (!message.isIdentityVerified && !message.isIdentityDefault) {
      TrimThreadJob.enqueueAsync(threadId)
    }

    return messageId
  }

  private fun hasAudioAttachment(attachments: List<Attachment>): Boolean {
    return attachments.any { MediaUtil.isAudio(it) }
  }

  @Throws(MmsException::class)
  private fun insertMediaMessage(
    threadId: Long,
    body: String?,
    attachments: List<Attachment>,
    quoteAttachments: List<Attachment>,
    sharedContacts: List<Contact>,
    linkPreviews: List<LinkPreview>,
    mentions: List<Mention>,
    messageRanges: BodyRangeList?,
    contentValues: ContentValues,
    insertListener: InsertListener?,
    updateThread: Boolean,
    unarchive: Boolean
  ): Long {
    val mentionsSelf = mentions.any { Recipient.resolved(it.recipientId).isSelf }
    val allAttachments: MutableList<Attachment> = mutableListOf()

    allAttachments += attachments
    allAttachments += sharedContacts.mapNotNull { it.avatarAttachment }
    allAttachments += linkPreviews.mapNotNull { it.thumbnail.orElse(null) }

    contentValues.put(BODY, body)
    contentValues.put(MENTIONS_SELF, if (mentionsSelf) 1 else 0)
    if (messageRanges != null) {
      contentValues.put(MESSAGE_RANGES, messageRanges.toByteArray())
    }

    val messageId = writableDatabase.withinTransaction { db ->
      val messageId = db.insert(TABLE_NAME, null, contentValues)

      SignalDatabase.mentions.insert(threadId, messageId, mentions)

      val insertedAttachments = SignalDatabase.attachments.insertAttachmentsForMessage(messageId, allAttachments, quoteAttachments)
      val serializedContacts = getSerializedSharedContacts(insertedAttachments, sharedContacts)
      val serializedPreviews = getSerializedLinkPreviews(insertedAttachments, linkPreviews)

      if (!TextUtils.isEmpty(serializedContacts)) {
        val rows = db
          .update(TABLE_NAME)
          .values(SHARED_CONTACTS to serializedContacts)
          .where("$ID = ?", messageId)
          .run()

        if (rows <= 0) {
          Log.w(TAG, "Failed to update message with shared contact data.")
        }
      }

      if (!TextUtils.isEmpty(serializedPreviews)) {
        val rows = db
          .update(TABLE_NAME)
          .values(LINK_PREVIEWS to serializedPreviews)
          .where("$ID = ?", messageId)
          .run()

        if (rows <= 0) {
          Log.w(TAG, "Failed to update message with link preview data.")
        }
      }

      messageId
    }

    insertListener?.onComplete()

    val contentValuesThreadId = contentValues.getAsLong(THREAD_ID)

    if (updateThread) {
      threads.setLastScrolled(contentValuesThreadId, 0)
      threads.update(threadId, unarchive)
    }

    return messageId
  }

  /**
   * Deletes the call updates specified in the messageIds set.
   */
  fun deleteCallUpdates(messageIds: Set<Long>): Int {
    return deleteCallUpdatesInternal(messageIds, SqlUtil.CollectionOperator.IN)
  }

  /**
   * Deletes all call updates except for those specified in the parameter.
   */
  fun deleteAllCallUpdatesExcept(excludedMessageIds: Set<Long>): Int {
    return deleteCallUpdatesInternal(excludedMessageIds, SqlUtil.CollectionOperator.NOT_IN)
  }

  private fun deleteCallUpdatesInternal(messageIds: Set<Long>, collectionOperator: SqlUtil.CollectionOperator): Int {
    var rowsDeleted = 0
    val threadIds: Set<Long> = writableDatabase.withinTransaction {
      SqlUtil.buildCollectionQuery(
        column = ID,
        values = messageIds,
        prefix = "$IS_CALL_TYPE_CLAUSE AND ",
        collectionOperator = collectionOperator
      ).map { query ->
        val threadSet = writableDatabase.select(ID)
          .from(TABLE_NAME)
          .where(query.where, query.whereArgs)
          .run()
          .readToSet { cursor ->
            cursor.requireLong(ID)
          }

        val rows = writableDatabase
          .delete(TABLE_NAME)
          .where(query.where, query.whereArgs)
          .run()

        if (rows <= 0) {
          Log.w(TAG, "Failed to delete some rows during call update deletion.")
        }

        rowsDeleted += rows
        threadSet
      }.flatten().toSet()
    }

    notifyConversationListeners(threadIds)
    notifyConversationListListeners()
    return rowsDeleted
  }

  fun deleteMessage(messageId: Long): Boolean {
    val threadId = getThreadIdForMessage(messageId)
    return deleteMessage(messageId, threadId)
  }

  fun deleteMessage(messageId: Long, notify: Boolean): Boolean {
    val threadId = getThreadIdForMessage(messageId)
    return deleteMessage(messageId, threadId, notify)
  }

  fun deleteMessage(messageId: Long, threadId: Long): Boolean {
    return deleteMessage(messageId, threadId, true)
  }

  private fun deleteMessage(messageId: Long, threadId: Long, notify: Boolean): Boolean {
    Log.d(TAG, "deleteMessage($messageId)")

    attachments.deleteAttachmentsForMessage(messageId)
    groupReceipts.deleteRowsForMessage(messageId)
    mentions.deleteMentionsForMessage(messageId)

    writableDatabase
      .delete(TABLE_NAME)
      .where("$ID = ?", messageId)
      .run()

    threads.setLastScrolled(threadId, 0)
    val threadDeleted = threads.update(threadId, false)

    if (notify) {
      notifyConversationListeners(threadId)
      notifyStickerListeners()
      notifyStickerPackListeners()
      OptimizeMessageSearchIndexJob.enqueue()
    }

    return threadDeleted
  }

  fun deleteScheduledMessage(messageId: Long) {
    Log.d(TAG, "deleteScheduledMessage($messageId)")

    val threadId = getThreadIdForMessage(messageId)

    writableDatabase.withinTransaction { db ->
      val rowsUpdated = db
        .update(TABLE_NAME)
        .values(
          SCHEDULED_DATE to -1,
          DATE_SENT to System.currentTimeMillis(),
          DATE_RECEIVED to System.currentTimeMillis()
        )
        .where("$ID = ? AND $SCHEDULED_DATE != ?", messageId, -1)
        .run()

      if (rowsUpdated > 0) {
        deleteMessage(messageId, threadId)
      } else {
        Log.w(TAG, "tried to delete scheduled message but it may have already been sent")
      }
    }

    ApplicationDependencies.getScheduledMessageManager().scheduleIfNecessary()
    ApplicationDependencies.getDatabaseObserver().notifyScheduledMessageObservers(threadId)
  }

  fun deleteScheduledMessages(recipientId: RecipientId) {
    Log.d(TAG, "deleteScheduledMessages($recipientId)")

    val threadId: Long = threads.getThreadIdFor(recipientId) ?: return Log.i(TAG, "No thread exists for $recipientId")

    writableDatabase.withinTransaction {
      val scheduledMessages = getScheduledMessagesInThread(threadId)
      for (record in scheduledMessages) {
        deleteScheduledMessage(record.id)
      }
    }
  }

  fun deleteThread(threadId: Long) {
    Log.d(TAG, "deleteThread($threadId)")
    deleteThreads(setOf(threadId))
  }

  private fun getSerializedSharedContacts(insertedAttachmentIds: Map<Attachment, AttachmentId>, contacts: List<Contact>): String? {
    if (contacts.isEmpty()) {
      return null
    }

    val sharedContactJson = JSONArray()

    for (contact in contacts) {
      try {
        val attachmentId: AttachmentId? = if (contact.avatarAttachment != null) {
          insertedAttachmentIds[contact.avatarAttachment]
        } else {
          null
        }

        val updatedAvatar = Contact.Avatar(
          attachmentId,
          contact.avatarAttachment,
          contact.avatar != null && contact.avatar!!.isProfile
        )

        val updatedContact = Contact(contact, updatedAvatar)

        sharedContactJson.put(JSONObject(updatedContact.serialize()))
      } catch (e: JSONException) {
        Log.w(TAG, "Failed to serialize shared contact. Skipping it.", e)
      } catch (e: IOException) {
        Log.w(TAG, "Failed to serialize shared contact. Skipping it.", e)
      }
    }

    return sharedContactJson.toString()
  }

  private fun getSerializedLinkPreviews(insertedAttachmentIds: Map<Attachment, AttachmentId>, previews: List<LinkPreview>): String? {
    if (previews.isEmpty()) {
      return null
    }

    val linkPreviewJson = JSONArray()

    for (preview in previews) {
      try {
        val attachmentId: AttachmentId? = if (preview.thumbnail.isPresent) {
          insertedAttachmentIds[preview.thumbnail.get()]
        } else {
          null
        }

        val updatedPreview = LinkPreview(preview.url, preview.title, preview.description, preview.date, attachmentId)
        linkPreviewJson.put(JSONObject(updatedPreview.serialize()))
      } catch (e: JSONException) {
        Log.w(TAG, "Failed to serialize shared contact. Skipping it.", e)
      } catch (e: IOException) {
        Log.w(TAG, "Failed to serialize shared contact. Skipping it.", e)
      }
    }

    return linkPreviewJson.toString()
  }

  private fun isDuplicate(message: IncomingMediaMessage, threadId: Long): Boolean {
    return readableDatabase
      .exists(TABLE_NAME)
      .where("$DATE_SENT = ? AND $RECIPIENT_ID = ? AND $THREAD_ID = ?", message.sentTimeMillis, message.from!!.serialize(), threadId)
      .run()
  }

  private fun isDuplicate(message: IncomingTextMessage, threadId: Long): Boolean {
    return readableDatabase
      .exists(TABLE_NAME)
      .where("$DATE_SENT = ? AND $RECIPIENT_ID = ? AND $THREAD_ID = ?", message.sentTimestampMillis, message.sender.serialize(), threadId)
      .run()
  }

  fun isSent(messageId: Long): Boolean {
    val type = readableDatabase
      .select(TYPE)
      .from(TABLE_NAME)
      .where("$ID = ?", messageId)
      .run()
      .readToSingleLong()

    return MessageTypes.isSentType(type)
  }

  fun getProfileChangeDetailsRecords(threadId: Long, afterTimestamp: Long): List<MessageRecord> {
    val cursor = readableDatabase
      .select(*MMS_PROJECTION)
      .from(TABLE_NAME)
      .where("$THREAD_ID = ? AND $DATE_RECEIVED >= ? AND $TYPE = ?", threadId, afterTimestamp, MessageTypes.PROFILE_CHANGE_TYPE)
      .orderBy("$ID DESC")
      .run()

    return mmsReaderFor(cursor).use { reader ->
      reader.filterNotNull()
    }
  }
  fun getAllRateLimitedMessageIds(): Set<Long> {
    val db = databaseHelper.signalReadableDatabase
    val where = "(" + TYPE + " & " + MessageTypes.TOTAL_MASK + " & " + MessageTypes.MESSAGE_RATE_LIMITED_BIT + ") > 0"
    val ids: MutableSet<Long> = HashSet()
    db.query(TABLE_NAME, arrayOf(ID), where, null, null, null, null).use { cursor ->
      while (cursor.moveToNext()) {
        ids.add(CursorUtil.requireLong(cursor, ID))
      }
    }
    return ids
  }

  fun getUnexportedInsecureMessages(limit: Int): Cursor {
    return rawQueryWithAttachments(
      projection = appendArg(MMS_PROJECTION_WITH_ATTACHMENTS, EXPORT_STATE),
      where = "${getInsecureMessageClause()} AND NOT $EXPORTED",
      arguments = null,
      reverse = false,
      limit = limit.toLong()
    )
  }

  fun getUnexportedInsecureMessagesEstimatedSize(): Long {
    val bodyTextSize: Long = readableDatabase
      .select("SUM(LENGTH($BODY))")
      .from(TABLE_NAME)
      .where("${getInsecureMessageClause()} AND $EXPORTED < ?", MessageExportStatus.EXPORTED)
      .run()
      .readToSingleLong()

    val fileSize: Long = readableDatabase.rawQuery(
      """
      SELECT 
        SUM(${AttachmentTable.TABLE_NAME}.${AttachmentTable.SIZE}) AS s
      FROM 
        $TABLE_NAME INNER JOIN ${AttachmentTable.TABLE_NAME} ON $TABLE_NAME.$ID = ${AttachmentTable.TABLE_NAME}.${AttachmentTable.MMS_ID}
      WHERE
        ${getInsecureMessageClause()} AND $EXPORTED < ${MessageExportStatus.EXPORTED.serialize()}
      """.toSingleLine(),
      null
    ).readToSingleLong()

    return bodyTextSize + fileSize
  }

  fun deleteExportedMessages() {
    writableDatabase.withinTransaction { db ->
      val threadsToUpdate: List<Long> = db
        .query(TABLE_NAME, arrayOf(THREAD_ID), "$EXPORTED = ?", buildArgs(MessageExportStatus.EXPORTED), THREAD_ID, null, null, null)
        .readToList { it.requireLong(THREAD_ID) }

      db.delete(TABLE_NAME)
        .where("$EXPORTED = ?", MessageExportStatus.EXPORTED)
        .run()

      for (threadId in threadsToUpdate) {
        threads.update(threadId, false)
      }

      attachments.deleteAbandonedAttachmentFiles()
    }

    OptimizeMessageSearchIndexJob.enqueue()
  }

  fun deleteThreads(threadIds: Set<Long>) {
    Log.d(TAG, "deleteThreads(count: ${threadIds.size})")

    writableDatabase.withinTransaction { db ->
      SqlUtil.buildCollectionQuery(THREAD_ID, threadIds).forEach { query ->
        db.select(ID)
          .from(TABLE_NAME)
          .where(query.where, query.whereArgs)
          .run()
          .forEach { cursor ->
            deleteMessage(cursor.requireLong(ID), false)
          }
      }
    }

    notifyConversationListeners(threadIds)
    notifyStickerListeners()
    notifyStickerPackListeners()
    OptimizeMessageSearchIndexJob.enqueue()
  }

  fun deleteMessagesInThreadBeforeDate(threadId: Long, date: Long): Int {
    return writableDatabase
      .delete(TABLE_NAME)
      .where("$THREAD_ID = ? AND $DATE_RECEIVED < $date", threadId)
      .run()
  }

  fun deleteAbandonedMessages(): Int {
    val deletes = writableDatabase
      .delete(TABLE_NAME)
      .where("$THREAD_ID NOT IN (SELECT _id FROM ${ThreadTable.TABLE_NAME})")
      .run()

    if (deletes > 0) {
      Log.i(TAG, "Deleted $deletes abandoned messages")
    }

    return deletes
  }

  fun deleteRemotelyDeletedStory(messageId: Long) {
    if (readableDatabase.exists(TABLE_NAME).where("$ID = ? AND $REMOTE_DELETED = ?", messageId, 1).run()) {
      deleteMessage(messageId)
    } else {
      Log.i(TAG, "Unable to delete remotely deleted story: $messageId")
    }
  }

  private fun getMessagesInThreadAfterInclusive(threadId: Long, timestamp: Long, limit: Long): List<MessageRecord> {
    val where = "$TABLE_NAME.$THREAD_ID = ? AND $TABLE_NAME.$DATE_RECEIVED >= ? AND $TABLE_NAME.$SCHEDULED_DATE = -1"
    val args = buildArgs(threadId, timestamp)

    return mmsReaderFor(rawQueryWithAttachments(where, args, false, limit)).use { reader ->
      reader.filterNotNull()
    }
  }

  fun deleteAllThreads() {
    Log.d(TAG, "deleteAllThreads()")

    attachments.deleteAllAttachments()
    groupReceipts.deleteAllRows()
    mentions.deleteAllMentions()
    writableDatabase.delete(TABLE_NAME).run()

    OptimizeMessageSearchIndexJob.enqueue()
  }

  fun getNearestExpiringViewOnceMessage(): ViewOnceExpirationInfo? {
    val query = """
      SELECT 
        $TABLE_NAME.$ID, 
        $VIEW_ONCE, 
        $DATE_RECEIVED 
      FROM 
        $TABLE_NAME INNER JOIN ${AttachmentTable.TABLE_NAME} ON $TABLE_NAME.$ID = ${AttachmentTable.TABLE_NAME}.${AttachmentTable.MMS_ID} 
      WHERE 
        $VIEW_ONCE > 0 AND 
        (${AttachmentTable.DATA} NOT NULL OR ${AttachmentTable.TRANSFER_STATE} != ?)
      """.toSingleLine()

    val args = buildArgs(AttachmentTable.TRANSFER_PROGRESS_DONE)

    var info: ViewOnceExpirationInfo? = null
    var nearestExpiration = Long.MAX_VALUE

    readableDatabase.rawQuery(query, args).forEach { cursor ->
      val id = cursor.requireLong(ID)
      val dateReceived = cursor.requireLong(DATE_RECEIVED)
      val expiresAt = dateReceived + ViewOnceUtil.MAX_LIFESPAN

      if (info == null || expiresAt < nearestExpiration) {
        info = ViewOnceExpirationInfo(id, dateReceived)
        nearestExpiration = expiresAt
      }
    }

    return info
  }

  /**
   * The number of change number messages in the thread.
   * Currently only used for tests.
   */
  @VisibleForTesting
  fun getChangeNumberMessageCount(recipientId: RecipientId): Int {
    return readableDatabase
      .select("COUNT(*)")
      .from(TABLE_NAME)
      .where("$RECIPIENT_ID = ? AND $TYPE = ?", recipientId, MessageTypes.CHANGE_NUMBER_TYPE)
      .run()
      .readToSingleInt()
  }

  fun beginTransaction(): SQLiteDatabase {
    writableDatabase.beginTransaction()
    return writableDatabase
  }

  fun setTransactionSuccessful() {
    writableDatabase.setTransactionSuccessful()
  }

  fun endTransaction() {
    writableDatabase.endTransaction()
  }

  @VisibleForTesting
  fun collapseJoinRequestEventsIfPossible(threadId: Long, message: IncomingGroupUpdateMessage): Optional<InsertResult> {
    var result: InsertResult? = null

    writableDatabase.withinTransaction { db ->
      mmsReaderFor(getConversation(threadId, 0, 2)).use { reader ->
        val latestMessage = reader.getNext()

        if (latestMessage != null && latestMessage.isGroupV2) {
          val changeEditor = message.changeEditor

          if (changeEditor.isPresent && latestMessage.isGroupV2JoinRequest(changeEditor.get())) {
            val secondLatestMessage = reader.getNext()

            val id: Long
            val encodedBody: String

            if (secondLatestMessage != null && secondLatestMessage.isGroupV2JoinRequest(changeEditor.get())) {
              id = secondLatestMessage.id
              encodedBody = MessageRecord.createNewContextWithAppendedDeleteJoinRequest(secondLatestMessage, message.changeRevision, changeEditor.get())
              deleteMessage(latestMessage.id)
            } else {
              id = latestMessage.id
              encodedBody = MessageRecord.createNewContextWithAppendedDeleteJoinRequest(latestMessage, message.changeRevision, changeEditor.get())
            }

            db.update(TABLE_NAME)
              .values(BODY to encodedBody)
              .where("$ID = ?", id)
              .run()

            result = InsertResult(id, threadId)
          }
        }
      }
    }

    return result.toOptional()
  }

  private fun getOutgoingTypeClause(): String {
    val segments: MutableList<String> = ArrayList(MessageTypes.OUTGOING_MESSAGE_TYPES.size)

    for (outgoingMessageType in MessageTypes.OUTGOING_MESSAGE_TYPES) {
      segments.add("($TABLE_NAME.$TYPE & ${MessageTypes.BASE_TYPE_MASK} = $outgoingMessageType)")
    }

    return segments.joinToString(" OR ")
  }

  fun getInsecureMessageCount(): Int {
    return readableDatabase
      .select("COUNT(*)")
      .from(TABLE_NAME)
      .where(getInsecureMessageClause())
      .run()
      .readToSingleInt()
  }

  fun getInsecureMessageSentCount(threadId: Long): Int {
    return readableDatabase
      .select("COUNT(*)")
      .from(TABLE_NAME)
      .where("$THREAD_ID = ? AND $outgoingInsecureMessageClause AND $DATE_SENT > ?", threadId, (System.currentTimeMillis() - InsightsConstants.PERIOD_IN_MILLIS))
      .run()
      .readToSingleInt()
  }

  fun getSecureMessageCount(threadId: Long): Int {
    return readableDatabase
      .select("COUNT(*)")
      .from(TABLE_NAME)
      .where("$secureMessageClause AND $THREAD_ID = ?", threadId)
      .run()
      .readToSingleInt()
  }

  fun getOutgoingSecureMessageCount(threadId: Long): Int {
    return readableDatabase
      .select("COUNT(*)")
      .from(TABLE_NAME)
      .where("$outgoingSecureMessageClause AND $THREAD_ID = ? AND ($TYPE & ${MessageTypes.GROUP_LEAVE_BIT} = 0 OR $TYPE & ${MessageTypes.GROUP_V2_BIT} = ${MessageTypes.GROUP_V2_BIT})", threadId)
      .run()
      .readToSingleInt()
  }

  fun getInsecureMessageCountForInsights(): Int {
    return getMessageCountForRecipientsAndType(outgoingInsecureMessageClause)
  }

  fun getSecureMessageCountForInsights(): Int {
    return getMessageCountForRecipientsAndType(outgoingSecureMessageClause)
  }

  private fun hasSmsExportMessage(threadId: Long): Boolean {
    return readableDatabase
      .exists(TABLE_NAME)
      .where("$THREAD_ID = ? AND $TYPE = ?", threadId, MessageTypes.SMS_EXPORT_TYPE)
      .run()
  }

  private fun getMessageCountForRecipientsAndType(typeClause: String): Int {
    return readableDatabase
      .select("COUNT(*)")
      .from(TABLE_NAME)
      .where("$typeClause AND $DATE_SENT > ?", (System.currentTimeMillis() - InsightsConstants.PERIOD_IN_MILLIS))
      .run()
      .readToSingleInt()
  }

  private val outgoingInsecureMessageClause = "($TYPE & ${MessageTypes.BASE_TYPE_MASK}) = ${MessageTypes.BASE_SENT_TYPE} AND NOT ($TYPE & ${MessageTypes.SECURE_MESSAGE_BIT})"
  private val outgoingSecureMessageClause = "($TYPE & ${MessageTypes.BASE_TYPE_MASK}) = ${MessageTypes.BASE_SENT_TYPE} AND ($TYPE & ${MessageTypes.SECURE_MESSAGE_BIT or MessageTypes.PUSH_MESSAGE_BIT})"

  private val secureMessageClause: String
    get() {
      val isSent = "($TYPE & ${MessageTypes.BASE_TYPE_MASK}) = ${MessageTypes.BASE_SENT_TYPE}"
      val isReceived = "($TYPE & ${MessageTypes.BASE_TYPE_MASK}) = ${MessageTypes.BASE_INBOX_TYPE}"
      val isSecure = "($TYPE & ${MessageTypes.SECURE_MESSAGE_BIT or MessageTypes.PUSH_MESSAGE_BIT})"
      return "($isSent OR $isReceived) AND $isSecure"
    }

  private fun getInsecureMessageClause(): String {
    return getInsecureMessageClause(-1)
  }

  private fun getInsecureMessageClause(threadId: Long): String {
    val isSent = "($TABLE_NAME.$TYPE & ${MessageTypes.BASE_TYPE_MASK}) = ${MessageTypes.BASE_SENT_TYPE}"
    val isReceived = "($TABLE_NAME.$TYPE & ${MessageTypes.BASE_TYPE_MASK}) = ${MessageTypes.BASE_INBOX_TYPE}"
    val isSecure = "($TABLE_NAME.$TYPE & ${MessageTypes.SECURE_MESSAGE_BIT or MessageTypes.PUSH_MESSAGE_BIT})"
    val isNotSecure = "($TABLE_NAME.$TYPE <= ${MessageTypes.BASE_TYPE_MASK or MessageTypes.MESSAGE_ATTRIBUTE_MASK})"

    var whereClause = "($isSent OR $isReceived) AND NOT $isSecure AND $isNotSecure"

    if (threadId != -1L) {
      whereClause += " AND $TABLE_NAME.$THREAD_ID = $threadId"
    }

    return whereClause
  }

  fun getUnexportedInsecureMessagesCount(): Int {
    return getUnexportedInsecureMessagesCount(-1)
  }

  fun getUnexportedInsecureMessagesCount(threadId: Long): Int {
    return writableDatabase
      .select("COUNT(*)")
      .from(TABLE_NAME)
      .where("${getInsecureMessageClause(threadId)} AND $EXPORTED < ?", MessageExportStatus.EXPORTED)
      .run()
      .readToSingleInt()
  }

  /**
   * Resets the exported state and exported flag so messages can be re-exported.
   */
  fun clearExportState() {
    writableDatabase.update(TABLE_NAME)
      .values(
        EXPORT_STATE to null,
        EXPORTED to MessageExportStatus.UNEXPORTED.serialize()
      )
      .where("$EXPORT_STATE IS NOT NULL OR $EXPORTED != ?", MessageExportStatus.UNEXPORTED)
      .run()
  }

  /**
   * Reset the exported status (not state) to the default for clearing errors.
   */
  fun clearInsecureMessageExportedErrorStatus() {
    writableDatabase.update(TABLE_NAME)
      .values(EXPORTED to MessageExportStatus.UNEXPORTED.serialize())
      .where("$EXPORTED < ?", MessageExportStatus.UNEXPORTED)
      .run()
  }

  fun setReactionsSeen(threadId: Long, sinceTimestamp: Long) {
    val where = "$THREAD_ID = ? AND $REACTIONS_UNREAD = ?" + if (sinceTimestamp > -1) " AND $DATE_RECEIVED <= $sinceTimestamp" else ""

    writableDatabase
      .update(TABLE_NAME)
      .values(
        REACTIONS_UNREAD to 0,
        REACTIONS_LAST_SEEN to System.currentTimeMillis()
      )
      .where(where, threadId, 1)
      .run()
  }

  fun setAllReactionsSeen() {
    writableDatabase
      .update(TABLE_NAME)
      .values(
        REACTIONS_UNREAD to 0,
        REACTIONS_LAST_SEEN to System.currentTimeMillis()
      )
      .where("$REACTIONS_UNREAD != ?", 0)
      .run()
  }

  fun setNotifiedTimestamp(timestamp: Long, ids: List<Long>) {
    if (ids.isEmpty()) {
      return
    }

    val query = buildSingleCollectionQuery(ID, ids)

    writableDatabase
      .update(TABLE_NAME)
      .values(NOTIFIED_TIMESTAMP to timestamp)
      .where(query.where, query.whereArgs)
      .run()
  }

  fun addMismatchedIdentity(messageId: Long, recipientId: RecipientId, identityKey: IdentityKey?) {
    try {
      addToDocument(
        messageId = messageId,
        column = MISMATCHED_IDENTITIES,
        item = IdentityKeyMismatch(recipientId, identityKey),
        clazz = IdentityKeyMismatchSet::class.java
      )
    } catch (e: IOException) {
      Log.w(TAG, e)
    }
  }

  fun removeMismatchedIdentity(messageId: Long, recipientId: RecipientId, identityKey: IdentityKey?) {
    try {
      removeFromDocument(
        messageId = messageId,
        column = MISMATCHED_IDENTITIES,
        item = IdentityKeyMismatch(recipientId, identityKey),
        clazz = IdentityKeyMismatchSet::class.java
      )
    } catch (e: IOException) {
      Log.w(TAG, e)
    }
  }

  fun setMismatchedIdentities(messageId: Long, mismatches: Set<IdentityKeyMismatch?>) {
    try {
      setDocument(
        database = databaseHelper.signalWritableDatabase,
        messageId = messageId,
        column = MISMATCHED_IDENTITIES,
        document = IdentityKeyMismatchSet(mismatches)
      )
    } catch (e: IOException) {
      Log.w(TAG, e)
    }
  }

  private fun getReportSpamMessageServerGuids(threadId: Long, timestamp: Long): List<ReportSpamData> {
    val data: MutableList<ReportSpamData> = ArrayList()

    readableDatabase
      .select(RECIPIENT_ID, SERVER_GUID, DATE_RECEIVED)
      .from(TABLE_NAME)
      .where("$THREAD_ID = ? AND $DATE_RECEIVED <= ?", threadId, timestamp)
      .orderBy("$DATE_RECEIVED DESC")
      .limit(3)
      .run()
      .forEach { cursor ->
        val serverGuid: String? = cursor.requireString(SERVER_GUID)

        if (serverGuid != null && serverGuid.isNotEmpty()) {
          data += ReportSpamData(
            recipientId = RecipientId.from(cursor.requireLong(RECIPIENT_ID)),
            serverGuid = serverGuid,
            dateReceived = cursor.requireLong(DATE_RECEIVED)
          )
        }
      }

    return data
  }

  fun getIncomingPaymentRequestThreads(): List<Long> {
    return readableDatabase
      .select("DISTINCT $THREAD_ID")
      .from(TABLE_NAME)
      .where("($TYPE & ${MessageTypes.BASE_TYPE_MASK}) = ${MessageTypes.BASE_INBOX_TYPE} AND ($TYPE & ?) != 0", MessageTypes.SPECIAL_TYPE_PAYMENTS_ACTIVATE_REQUEST)
      .run()
      .readToList { it.requireLong(THREAD_ID) }
  }

  fun getPaymentMessage(paymentUuid: UUID): MessageId? {
    val id = readableDatabase
      .select(ID)
      .from(TABLE_NAME)
      .where("$TYPE & ? != 0 AND body = ?", MessageTypes.SPECIAL_TYPE_PAYMENTS_NOTIFICATION, paymentUuid)
      .run()
      .readToSingleLong(-1)

    return if (id != -1L) {
      MessageId(id)
    } else {
      null
    }
  }

  /**
   * @return The user that added you to the group, otherwise null.
   */
  fun getGroupAddedBy(threadId: Long): RecipientId? {
    var lastQuitChecked = System.currentTimeMillis()
    var pair: Pair<RecipientId?, Long>

    do {
      pair = getGroupAddedBy(threadId, lastQuitChecked)

      if (pair.first() != null) {
        return pair.first()
      } else {
        lastQuitChecked = pair.second()
      }
    } while (pair.second() != -1L)

    return null
  }

  private fun getGroupAddedBy(threadId: Long, lastQuitChecked: Long): Pair<RecipientId?, Long> {
    val latestQuit = messages.getLatestGroupQuitTimestamp(threadId, lastQuitChecked)
    val id = messages.getOldestGroupUpdateSender(threadId, latestQuit)
    return Pair(id, latestQuit)
  }

  /**
   * Whether or not the message has been quoted by another message.
   */
  fun isQuoted(messageRecord: MessageRecord): Boolean {
    val author = if (messageRecord.isOutgoing) Recipient.self().id else messageRecord.recipient.id

    return readableDatabase
      .exists(TABLE_NAME)
      .where("$QUOTE_ID = ?  AND $QUOTE_AUTHOR = ? AND $SCHEDULED_DATE = ?", messageRecord.dateSent, author, -1)
      .run()
  }

  /**
   * Given a collection of MessageRecords, this will return a set of the IDs of the records that have been quoted by another message.
   * Does an efficient bulk lookup that makes it faster than [.isQuoted] for multiple records.
   */
  fun isQuoted(records: Collection<MessageRecord>): Set<Long> {
    if (records.isEmpty()) {
      return emptySet()
    }

    val byQuoteDescriptor: MutableMap<QuoteDescriptor, MessageRecord> = HashMap(records.size)
    val args: MutableList<Array<String>> = ArrayList(records.size)

    for (record in records) {
      val timestamp = record.dateSent
      val author = if (record.isOutgoing) Recipient.self().id else record.recipient.id

      byQuoteDescriptor[QuoteDescriptor(timestamp, author)] = record
      args.add(buildArgs(timestamp, author, -1))
    }

    val quotedIds: MutableSet<Long> = mutableSetOf()

    buildCustomCollectionQuery("$QUOTE_ID = ?  AND $QUOTE_AUTHOR = ? AND $SCHEDULED_DATE = ?", args).forEach { query ->
      readableDatabase
        .select(QUOTE_ID, QUOTE_AUTHOR)
        .from(TABLE_NAME)
        .where(query.where, query.whereArgs)
        .run()
        .forEach { cursor ->
          val quoteLocator = QuoteDescriptor(
            timestamp = cursor.requireLong(QUOTE_ID),
            author = RecipientId.from(cursor.requireNonNullString(QUOTE_AUTHOR))
          )

          quotedIds += byQuoteDescriptor[quoteLocator]!!.id
        }
    }

    return quotedIds
  }

  fun getRootOfQuoteChain(id: MessageId): MessageId {
    val targetMessage: MmsMessageRecord = messages.getMessageRecord(id.id) as MmsMessageRecord

    if (targetMessage.quote == null) {
      return id
    }

    val query: String = if (targetMessage.quote!!.author == Recipient.self().id) {
      "$DATE_SENT = ${targetMessage.quote!!.id} AND ($TYPE & ${MessageTypes.BASE_TYPE_MASK}) = ${MessageTypes.BASE_SENT_TYPE}"
    } else {
      "$DATE_SENT = ${targetMessage.quote!!.id} AND $RECIPIENT_ID = '${targetMessage.quote!!.author.serialize()}'"
    }

    MmsReader(readableDatabase.query(TABLE_NAME, MMS_PROJECTION, query, null, null, null, "1")).use { reader ->
      val record: MessageRecord? = reader.firstOrNull()
      if (record != null && !record.isStory()) {
        return getRootOfQuoteChain(MessageId(record.id))
      }
    }

    return id
  }

  fun getAllMessagesThatQuote(id: MessageId): List<MessageRecord> {
    val targetMessage: MessageRecord = getMessageRecord(id.id)
    val author = if (targetMessage.isOutgoing) Recipient.self().id else targetMessage.recipient.id

    val query = "$QUOTE_ID = ${targetMessage.dateSent} AND $QUOTE_AUTHOR = ${author.serialize()} AND $SCHEDULED_DATE = -1"
    val order = "$DATE_RECEIVED DESC"

    val records: MutableList<MessageRecord> = ArrayList()
    MmsReader(readableDatabase.query(TABLE_NAME, MMS_PROJECTION, query, null, null, null, order)).use { reader ->
      for (record in reader) {
        records += record
        records += getAllMessagesThatQuote(MessageId(record.id))
      }
    }

    return records.sortedByDescending { it.dateReceived }
  }

  fun getQuotedMessagePosition(threadId: Long, quoteId: Long, recipientId: RecipientId): Int {
    val isOwnNumber = Recipient.resolved(recipientId).isSelf

    readableDatabase
      .select(DATE_SENT, RECIPIENT_ID, REMOTE_DELETED)
      .from(TABLE_NAME)
      .where("$THREAD_ID = $threadId AND $STORY_TYPE = 0 AND $PARENT_STORY_ID <= 0 AND $SCHEDULED_DATE = -1")
      .orderBy("$DATE_RECEIVED DESC")
      .run()
      .forEach { cursor ->
        val quoteIdMatches = cursor.requireLong(DATE_SENT) == quoteId
        val recipientIdMatches = recipientId == RecipientId.from(cursor.requireLong(RECIPIENT_ID))

        if (quoteIdMatches && (recipientIdMatches || isOwnNumber)) {
          return if (cursor.requireBoolean(REMOTE_DELETED)) {
            -1
          } else {
            cursor.position
          }
        }
      }

    return -1
  }

  fun getMessagePositionInConversation(threadId: Long, receivedTimestamp: Long, recipientId: RecipientId): Int {
    val isOwnNumber = Recipient.resolved(recipientId).isSelf

    readableDatabase
      .select(DATE_RECEIVED, RECIPIENT_ID, REMOTE_DELETED)
      .from(TABLE_NAME)
      .where("$THREAD_ID = $threadId AND $STORY_TYPE = 0 AND $PARENT_STORY_ID <= 0 AND $SCHEDULED_DATE = -1")
      .orderBy("$DATE_RECEIVED DESC")
      .run()
      .forEach { cursor ->
        val timestampMatches = cursor.requireLong(DATE_RECEIVED) == receivedTimestamp
        val recipientIdMatches = recipientId == RecipientId.from(cursor.requireLong(RECIPIENT_ID))

        if (timestampMatches && (recipientIdMatches || isOwnNumber)) {
          return if (cursor.requireBoolean(REMOTE_DELETED)) {
            -1
          } else {
            cursor.position
          }
        }
      }

    return -1
  }

  fun getMessagePositionInConversation(threadId: Long, receivedTimestamp: Long): Int {
    return getMessagePositionInConversation(threadId, 0, receivedTimestamp)
  }

  /**
   * Retrieves the position of the message with the provided timestamp in the query results you'd
   * get from calling [.getConversation].
   *
   * Note: This could give back incorrect results in the situation where multiple messages have the
   * same received timestamp. However, because this was designed to determine where to scroll to,
   * you'll still wind up in about the right spot.
   *
   * @param groupStoryId Ignored if passed value is <= 0
   */
  fun getMessagePositionInConversation(threadId: Long, groupStoryId: Long, receivedTimestamp: Long): Int {
    val order: String
    val selection: String

    if (groupStoryId > 0) {
      order = "$DATE_RECEIVED ASC"
      selection = "$THREAD_ID = $threadId AND $DATE_RECEIVED < $receivedTimestamp AND $STORY_TYPE = 0 AND $PARENT_STORY_ID = $groupStoryId AND $SCHEDULED_DATE = -1"
    } else {
      order = "$DATE_RECEIVED DESC"
      selection = "$THREAD_ID = $threadId AND $DATE_RECEIVED > $receivedTimestamp AND $STORY_TYPE = 0 AND $PARENT_STORY_ID <= 0 AND $SCHEDULED_DATE = -1"
    }

    return readableDatabase
      .select("COUNT(*)")
      .from(TABLE_NAME)
      .where(selection)
      .orderBy(order)
      .run()
      .readToSingleInt(-1)
  }

  fun getTimestampForFirstMessageAfterDate(date: Long): Long {
    return readableDatabase
      .select(DATE_RECEIVED)
      .from(TABLE_NAME)
      .where("$DATE_RECEIVED > $date AND $SCHEDULED_DATE = -1")
      .orderBy("$DATE_RECEIVED ASC")
      .limit(1)
      .run()
      .readToSingleLong()
  }

  fun getMessageCountBeforeDate(date: Long): Int {
    return readableDatabase
      .select("COUNT(*)")
      .from(TABLE_NAME)
      .where("$DATE_RECEIVED < $date AND $SCHEDULED_DATE = -1")
      .run()
      .readToSingleInt()
  }

  @Throws(NoSuchMessageException::class)
  fun getMessagesAfterVoiceNoteInclusive(messageId: Long, limit: Long): List<MessageRecord> {
    val origin: MessageRecord = getMessageRecord(messageId)

    return getMessagesInThreadAfterInclusive(origin.threadId, origin.dateReceived, limit)
      .sortedBy { it.dateReceived }
      .take(limit.toInt())
  }

  fun getMessagePositionOnOrAfterTimestamp(threadId: Long, timestamp: Long): Int {
    return readableDatabase
      .select("COUNT(*)")
      .from(TABLE_NAME)
      .where("$THREAD_ID = $threadId AND $DATE_RECEIVED >= $timestamp AND $STORY_TYPE = 0 AND $PARENT_STORY_ID <= 0 AND $SCHEDULED_DATE = -1")
      .run()
      .readToSingleInt()
  }

  @Throws(NoSuchMessageException::class)
  fun getConversationSnippetType(threadId: Long): Long {
    return readableDatabase
      .rawQuery(SNIPPET_QUERY, buildArgs(threadId))
      .readToSingleObject { it.requireLong(TYPE) } ?: throw NoSuchMessageException("no message")
  }

  @Throws(NoSuchMessageException::class)
  fun getConversationSnippet(threadId: Long): MessageRecord {
    return getConversationSnippetCursor(threadId)
      .readToSingleObject { cursor ->
        val id = cursor.requireLong(ID)
        messages.getMessageRecord(id)
      } ?: throw NoSuchMessageException("no message")
  }

  @VisibleForTesting
  fun getConversationSnippetCursor(threadId: Long): Cursor {
    val db = databaseHelper.signalReadableDatabase
    return db.rawQuery(SNIPPET_QUERY, buildArgs(threadId))
  }

  fun getUnreadCount(threadId: Long): Int {
    return readableDatabase
      .select("COUNT(*)")
      .from("$TABLE_NAME INDEXED BY $INDEX_THREAD_DATE")
      .where("$READ = 0 AND $STORY_TYPE = 0 AND $THREAD_ID = $threadId AND $PARENT_STORY_ID <= 0")
      .run()
      .readToSingleInt()
  }

  fun checkMessageExists(messageRecord: MessageRecord): Boolean {
    return readableDatabase
      .exists(TABLE_NAME)
      .where("$ID = ?", messageRecord.id)
      .run()
  }

  fun getReportSpamMessageServerData(threadId: Long, timestamp: Long, limit: Int): List<ReportSpamData> {
    return getReportSpamMessageServerGuids(threadId, timestamp)
      .sortedBy { it.dateReceived }
      .take(limit)
  }

  @Throws(NoSuchMessageException::class)
  private fun getMessageExportState(messageId: MessageId): MessageExportState {
    return readableDatabase
      .select(EXPORT_STATE)
      .from(TABLE_NAME)
      .where("$ID = ?", messageId.id)
      .run()
      .readToSingleObject { cursor ->
        val bytes: ByteArray? = cursor.requireBlob(EXPORT_STATE)

        if (bytes == null) {
          MessageExportState.getDefaultInstance()
        } else {
          try {
            MessageExportState.parseFrom(bytes)
          } catch (e: InvalidProtocolBufferException) {
            MessageExportState.getDefaultInstance()
          }
        }
      } ?: throw NoSuchMessageException("The requested message does not exist.")
  }

  @Throws(NoSuchMessageException::class)
  fun updateMessageExportState(messageId: MessageId, transform: Function<MessageExportState, MessageExportState>) {
    writableDatabase.withinTransaction { db ->
      val oldState = getMessageExportState(messageId)
      val newState = transform.apply(oldState)
      setMessageExportState(messageId, newState)
    }
  }

  fun markMessageExported(messageId: MessageId) {
    writableDatabase
      .update(TABLE_NAME)
      .values(EXPORTED to MessageExportStatus.EXPORTED.serialize())
      .where("$ID = ?", messageId.id)
      .run()
  }

  fun markMessageExportFailed(messageId: MessageId) {
    writableDatabase
      .update(TABLE_NAME)
      .values(EXPORTED to MessageExportStatus.ERROR.serialize())
      .where("$ID = ?", messageId.id)
      .run()
  }

  private fun setMessageExportState(messageId: MessageId, messageExportState: MessageExportState) {
    writableDatabase
      .update(TABLE_NAME)
      .values(EXPORT_STATE to messageExportState.toByteArray())
      .where("$ID = ?", messageId.id)
      .run()
  }

  fun incrementDeliveryReceiptCounts(syncMessageIds: List<SyncMessageId>, timestamp: Long): Collection<SyncMessageId> {
    return incrementReceiptCounts(syncMessageIds, timestamp, ReceiptType.DELIVERY)
  }

  fun incrementDeliveryReceiptCount(syncMessageId: SyncMessageId, timestamp: Long): Boolean {
    return incrementReceiptCount(syncMessageId, timestamp, ReceiptType.DELIVERY)
  }

  /**
   * @return A list of ID's that were not updated.
   */
  fun incrementReadReceiptCounts(syncMessageIds: List<SyncMessageId>, timestamp: Long): Collection<SyncMessageId> {
    return incrementReceiptCounts(syncMessageIds, timestamp, ReceiptType.READ)
  }

  fun incrementReadReceiptCount(syncMessageId: SyncMessageId, timestamp: Long): Boolean {
    return incrementReceiptCount(syncMessageId, timestamp, ReceiptType.READ)
  }

  /**
   * @return A list of ID's that were not updated.
   */
  fun incrementViewedReceiptCounts(syncMessageIds: List<SyncMessageId>, timestamp: Long): Collection<SyncMessageId> {
    return incrementReceiptCounts(syncMessageIds, timestamp, ReceiptType.VIEWED)
  }

  fun incrementViewedNonStoryReceiptCounts(syncMessageIds: List<SyncMessageId>, timestamp: Long): Collection<SyncMessageId> {
    return incrementReceiptCounts(syncMessageIds, timestamp, ReceiptType.VIEWED, MessageQualifier.NORMAL)
  }

  fun incrementViewedReceiptCount(syncMessageId: SyncMessageId, timestamp: Long): Boolean {
    return incrementReceiptCount(syncMessageId, timestamp, ReceiptType.VIEWED)
  }

  fun incrementViewedStoryReceiptCounts(syncMessageIds: List<SyncMessageId>, timestamp: Long): Collection<SyncMessageId> {
    val messageUpdates: MutableSet<MessageUpdate> = HashSet()
    val unhandled: MutableSet<SyncMessageId> = HashSet()

    writableDatabase.withinTransaction {
      for (id in syncMessageIds) {
        val updates = incrementReceiptCountInternal(id, timestamp, ReceiptType.VIEWED, MessageQualifier.STORY)

        if (updates.isNotEmpty()) {
          messageUpdates += updates
        } else {
          unhandled += id
        }
      }
    }

    for (update in messageUpdates) {
      ApplicationDependencies.getDatabaseObserver().notifyMessageUpdateObservers(update.messageId)
      ApplicationDependencies.getDatabaseObserver().notifyVerboseConversationListeners(setOf(update.threadId))
    }

    if (messageUpdates.isNotEmpty()) {
      notifyConversationListListeners()
    }

    return unhandled
  }

  /**
   * Wraps a single receipt update in a transaction and triggers the proper updates.
   *
   * @return Whether or not some thread was updated.
   */
  private fun incrementReceiptCount(syncMessageId: SyncMessageId, timestamp: Long, receiptType: ReceiptType, messageQualifier: MessageQualifier = MessageQualifier.ALL): Boolean {
    var messageUpdates: Set<MessageUpdate> = HashSet()

    writableDatabase.withinTransaction {
      messageUpdates = incrementReceiptCountInternal(syncMessageId, timestamp, receiptType, messageQualifier)

      for (messageUpdate in messageUpdates) {
        threads.update(messageUpdate.threadId, false)
      }
    }

    for (threadUpdate in messageUpdates) {
      ApplicationDependencies.getDatabaseObserver().notifyMessageUpdateObservers(threadUpdate.messageId)
    }

    return messageUpdates.isNotEmpty()
  }

  /**
   * Wraps multiple receipt updates in a transaction and triggers the proper updates.
   *
   * @return All of the messages that didn't result in updates.
   */
  private fun incrementReceiptCounts(syncMessageIds: List<SyncMessageId>, timestamp: Long, receiptType: ReceiptType, messageQualifier: MessageQualifier = MessageQualifier.ALL): Collection<SyncMessageId> {
    val messageUpdates: MutableSet<MessageUpdate> = HashSet()
    val unhandled: MutableSet<SyncMessageId> = HashSet()

    writableDatabase.withinTransaction {
      for (id in syncMessageIds) {
        val updates = incrementReceiptCountInternal(id, timestamp, receiptType, messageQualifier)

        if (updates.isNotEmpty()) {
          messageUpdates += updates
        } else {
          unhandled += id
        }
      }

      for (update in messageUpdates) {
        threads.updateSilently(update.threadId, false)
      }
    }

    for (update in messageUpdates) {
      ApplicationDependencies.getDatabaseObserver().notifyMessageUpdateObservers(update.messageId)
      ApplicationDependencies.getDatabaseObserver().notifyVerboseConversationListeners(setOf(update.threadId))

      if (messageQualifier == MessageQualifier.STORY) {
        ApplicationDependencies.getDatabaseObserver().notifyStoryObservers(threads.getRecipientIdForThreadId(update.threadId)!!)
      }
    }

    if (messageUpdates.isNotEmpty()) {
      notifyConversationListListeners()
    }

    return unhandled
  }

  private fun incrementReceiptCountInternal(messageId: SyncMessageId, timestamp: Long, receiptType: ReceiptType, messageQualifier: MessageQualifier): Set<MessageUpdate> {
    val messageUpdates: MutableSet<MessageUpdate> = HashSet()

    val qualifierWhere: String = when (messageQualifier) {
      MessageQualifier.NORMAL -> " AND NOT ($IS_STORY_CLAUSE)"
      MessageQualifier.STORY -> " AND $IS_STORY_CLAUSE"
      MessageQualifier.ALL -> ""
    }

    readableDatabase
      .select(ID, THREAD_ID, TYPE, RECIPIENT_ID, receiptType.columnName, RECEIPT_TIMESTAMP)
      .from(TABLE_NAME)
      .where("$DATE_SENT = ? $qualifierWhere", messageId.timetamp)
      .run()
      .forEach { cursor ->
        if (MessageTypes.isOutgoingMessageType(cursor.requireLong(TYPE))) {
          val theirRecipientId = RecipientId.from(cursor.requireLong(RECIPIENT_ID))
          val ourRecipientId = messageId.recipientId
          val columnName = receiptType.columnName

          if (ourRecipientId == theirRecipientId || Recipient.resolved(theirRecipientId).isGroup) {
            val id = cursor.requireLong(ID)
            val threadId = cursor.requireLong(THREAD_ID)
            val status = receiptType.groupStatus
            val isFirstIncrement = cursor.requireLong(columnName) == 0L
            val savedTimestamp = cursor.requireLong(RECEIPT_TIMESTAMP)
            val updatedTimestamp = if (isFirstIncrement) max(savedTimestamp, timestamp) else savedTimestamp

            writableDatabase.execSQL(
              """
                UPDATE $TABLE_NAME 
                SET 
                  $columnName = $columnName + 1,
                  $RECEIPT_TIMESTAMP = ? 
                WHERE $ID = ?
              """.toSingleLine(),
              buildArgs(updatedTimestamp, id)
            )

            groupReceipts.update(ourRecipientId, id, status, timestamp)

            messageUpdates += MessageUpdate(threadId, MessageId(id))
          }
        }

        if (messageUpdates.isNotEmpty() && receiptType == ReceiptType.DELIVERY) {
          earlyDeliveryReceiptCache.increment(messageId.timetamp, messageId.recipientId, timestamp)
        }
      }

    messageUpdates += incrementStoryReceiptCount(messageId, timestamp, receiptType)

    return messageUpdates
  }

  private fun incrementStoryReceiptCount(messageId: SyncMessageId, timestamp: Long, receiptType: ReceiptType): Set<MessageUpdate> {
    val messageUpdates: MutableSet<MessageUpdate> = HashSet()
    val columnName = receiptType.columnName

    for (storyMessageId in storySends.getStoryMessagesFor(messageId)) {
      writableDatabase.execSQL(
        """
          UPDATE $TABLE_NAME 
          SET 
            $columnName = $columnName + 1, 
            $RECEIPT_TIMESTAMP = CASE 
              WHEN $columnName = 0 THEN MAX($RECEIPT_TIMESTAMP, ?) 
              ELSE $RECEIPT_TIMESTAMP 
            END 
          WHERE $ID = ?
        """.toSingleLine(),
        buildArgs(timestamp, storyMessageId.id)
      )

      groupReceipts.update(messageId.recipientId, storyMessageId.id, receiptType.groupStatus, timestamp)

      messageUpdates += MessageUpdate(-1, storyMessageId)
    }

    return messageUpdates
  }

  /**
   * @return Unhandled ids
   */
  fun setTimestampReadFromSyncMessage(readMessages: List<ReadMessage>, proposedExpireStarted: Long, threadToLatestRead: MutableMap<Long, Long>): Collection<SyncMessageId> {
    val expiringMessages: MutableList<Pair<Long, Long>> = mutableListOf()
    val updatedThreads: MutableSet<Long> = mutableSetOf()
    val unhandled: MutableCollection<SyncMessageId> = mutableListOf()

    writableDatabase.withinTransaction {
      for (readMessage in readMessages) {
        val authorId: RecipientId = recipients.getOrInsertFromServiceId(readMessage.sender)

        val result: TimestampReadResult = setTimestampReadFromSyncMessageInternal(
          messageId = SyncMessageId(authorId, readMessage.timestamp),
          proposedExpireStarted = proposedExpireStarted,
          threadToLatestRead = threadToLatestRead
        )

        expiringMessages += result.expiring
        updatedThreads += result.threads

        if (result.threads.isEmpty()) {
          unhandled += SyncMessageId(authorId, readMessage.timestamp)
        }
      }

      for (threadId in updatedThreads) {
        threads.updateReadState(threadId)
        threads.setLastSeen(threadId)
      }
    }

    for (expiringMessage in expiringMessages) {
      ApplicationDependencies.getExpiringMessageManager().scheduleDeletion(expiringMessage.first(), true, proposedExpireStarted, expiringMessage.second())
    }

    for (threadId in updatedThreads) {
      notifyConversationListeners(threadId)
    }

    return unhandled
  }

  /**
   * Handles a synchronized read message.
   * @param messageId An id representing the author-timestamp pair of the message that was read on a linked device. Note that the author could be self when
   * syncing read receipts for reactions.
   */
  private fun setTimestampReadFromSyncMessageInternal(messageId: SyncMessageId, proposedExpireStarted: Long, threadToLatestRead: MutableMap<Long, Long>): TimestampReadResult {
    val expiring: MutableList<Pair<Long, Long>> = LinkedList()
    val projection = arrayOf(ID, THREAD_ID, EXPIRES_IN, EXPIRE_STARTED)
    val query = "$DATE_SENT = ? AND ($RECIPIENT_ID = ? OR ($RECIPIENT_ID = ? AND ${getOutgoingTypeClause()}))"
    val args = buildArgs(messageId.timetamp, messageId.recipientId, Recipient.self().id)
    val threads: MutableList<Long> = LinkedList()

    readableDatabase
      .select(ID, THREAD_ID, EXPIRES_IN, EXPIRE_STARTED)
      .from(TABLE_NAME)
      .where("$DATE_SENT = ? AND ($RECIPIENT_ID = ? OR ($RECIPIENT_ID = ? AND ${getOutgoingTypeClause()}))", messageId.timetamp, messageId.recipientId, Recipient.self().id)
      .run()
      .forEach { cursor ->
        val id = cursor.requireLong(ID)
        val threadId = cursor.requireLong(THREAD_ID)
        val expiresIn = cursor.requireLong(EXPIRES_IN)
        val expireStarted = cursor.requireLong(EXPIRE_STARTED).let {
          if (it > 0) {
            min(proposedExpireStarted, it)
          } else {
            proposedExpireStarted
          }
        }

        val values = contentValuesOf(
          READ to 1,
          REACTIONS_UNREAD to 0,
          REACTIONS_LAST_SEEN to System.currentTimeMillis()
        )

        if (expiresIn > 0) {
          values.put(EXPIRE_STARTED, expireStarted)
          expiring += Pair(id, expiresIn)
        }

        writableDatabase
          .update(TABLE_NAME)
          .values(values)
          .where("$ID = ?", id)
          .run()

        threads += threadId

        val latest: Long? = threadToLatestRead[threadId]

        threadToLatestRead[threadId] = if (latest != null) {
          max(latest, messageId.timetamp)
        } else {
          messageId.timetamp
        }
      }

    return TimestampReadResult(expiring, threads)
  }

  /**
   * Finds a message by timestamp+author.
   * Does *not* include attachments.
   */
  fun getMessageFor(timestamp: Long, authorId: RecipientId): MessageRecord? {
    val isSelf = authorId == Recipient.self().id

    val cursor = readableDatabase
      .select(*MMS_PROJECTION)
      .from(TABLE_NAME)
      .where("$DATE_SENT = ?", timestamp)
      .run()

    mmsReaderFor(cursor).use { reader ->
      for (record in reader) {
        if ((isSelf && record.isOutgoing) || (!isSelf && record.individualRecipient.id == authorId)) {
          return record
        }
      }
    }

    return null
  }

  /**
   * A cursor containing all of the messages in a given thread, in the proper order.
   * This does *not* have attachments in it.
   */
  fun getConversation(threadId: Long): Cursor {
    return getConversation(threadId, 0, 0)
  }

  /**
   * A cursor containing all of the messages in a given thread, in the proper order, respecting offset/limit.
   * This does *not* have attachments in it.
   */
  fun getConversation(threadId: Long, offset: Long, limit: Long): Cursor {
    val limitStr: String = if (limit > 0 || offset > 0) "$offset, $limit" else ""

    return readableDatabase
      .select(*MMS_PROJECTION)
      .from(TABLE_NAME)
      .where("$THREAD_ID = ? AND $STORY_TYPE = ? AND $PARENT_STORY_ID <= ? AND $SCHEDULED_DATE = ?", threadId, 0, 0, -1)
      .orderBy("$DATE_RECEIVED DESC")
      .limit(limitStr)
      .run()
  }

  /**
   * Returns messages ordered for display in a reverse list (newest first).
   */
  fun getScheduledMessagesInThread(threadId: Long): List<MessageRecord> {
    val cursor = readableDatabase
      .select(*MMS_PROJECTION)
      .from("$TABLE_NAME INDEXED BY $INDEX_THREAD_STORY_SCHEDULED_DATE")
      .where("$THREAD_ID = ? AND $STORY_TYPE = ? AND $PARENT_STORY_ID <= ? AND $SCHEDULED_DATE != ?", threadId, 0, 0, -1)
      .orderBy("$SCHEDULED_DATE DESC, $ID DESC")
      .run()

    return mmsReaderFor(cursor).use { reader ->
      reader.filterNotNull()
    }
  }

  /**
   * Returns messages order for sending (oldest first).
   */
  fun getScheduledMessagesBefore(time: Long): List<MessageRecord> {
    val cursor = readableDatabase
      .select(*MMS_PROJECTION)
      .from(TABLE_NAME)
      .where("$STORY_TYPE = ? AND $PARENT_STORY_ID <= ? AND $SCHEDULED_DATE != ? AND $SCHEDULED_DATE <= ?", 0, 0, -1, time)
      .orderBy("$SCHEDULED_DATE ASC, $ID ASC")
      .run()

    return mmsReaderFor(cursor).use { reader ->
      reader.filterNotNull()
    }
  }

  fun getOldestScheduledSendTimestamp(): MessageRecord? {
    val cursor = readableDatabase
      .select(*MMS_PROJECTION)
      .from(TABLE_NAME)
      .where("$STORY_TYPE = ? AND $PARENT_STORY_ID <= ? AND $SCHEDULED_DATE != ?", 0, 0, -1)
      .orderBy("$SCHEDULED_DATE ASC, $ID ASC")
      .limit(1)
      .run()

    return mmsReaderFor(cursor).use { reader ->
      reader.firstOrNull()
    }
  }

  fun getMessagesForNotificationState(stickyThreads: Collection<StickyThread>): Cursor {
    val stickyQuery = StringBuilder()

    for ((conversationId, _, earliestTimestamp) in stickyThreads) {
      if (stickyQuery.isNotEmpty()) {
        stickyQuery.append(" OR ")
      }

      stickyQuery.append("(")
        .append("$THREAD_ID = ")
        .append(conversationId.threadId)
        .append(" AND ")
        .append(DATE_RECEIVED)
        .append(" >= ")
        .append(earliestTimestamp)
        .append(getStickyWherePartForParentStoryId(conversationId.groupStoryId))
        .append(")")
    }

    return readableDatabase
      .select(*MMS_PROJECTION)
      .from(TABLE_NAME)
      .where("$NOTIFIED = 0 AND $STORY_TYPE = 0 AND ($READ = 0 OR $REACTIONS_UNREAD = 1 ${if (stickyQuery.isNotEmpty()) "OR ($stickyQuery)" else ""})")
      .orderBy("$DATE_RECEIVED ASC")
      .run()
  }

  private fun getStickyWherePartForParentStoryId(parentStoryId: Long?): String {
    return if (parentStoryId == null) {
      " AND $PARENT_STORY_ID <= 0"
    } else {
      " AND $PARENT_STORY_ID = $parentStoryId"
    }
  }

  override fun remapRecipient(fromId: RecipientId, toId: RecipientId) {
    writableDatabase
      .update(TABLE_NAME)
      .values(RECIPIENT_ID to toId.serialize())
      .where("$RECIPIENT_ID = ?", fromId)
      .run()
  }

  override fun remapThread(fromId: Long, toId: Long) {
    writableDatabase
      .update(TABLE_NAME)
      .values(THREAD_ID to toId)
      .where("$THREAD_ID = ?", fromId)
      .run()
  }

  /**
   * Returns the next ID that would be generated if an insert was done on this table.
   * You should *not* use this for actually generating an ID to use. That will happen automatically!
   * This was added for a very narrow usecase, and you probably don't need to use it.
   */
  fun getNextId(): Long {
    return getNextAutoIncrementId(writableDatabase, TABLE_NAME)
  }

  fun updateReactionsUnread(db: SQLiteDatabase, messageId: Long, hasReactions: Boolean, isRemoval: Boolean) {
    try {
      val isOutgoing = getMessageRecord(messageId).isOutgoing
      val values = ContentValues()

      if (!hasReactions) {
        values.put(REACTIONS_UNREAD, 0)
      } else if (!isRemoval) {
        values.put(REACTIONS_UNREAD, 1)
      }

      if (isOutgoing && hasReactions) {
        values.put(NOTIFIED, 0)
      }

      if (values.size() > 0) {
        db.update(TABLE_NAME)
          .values(values)
          .where("$ID = ?", messageId)
          .run()
      }
    } catch (e: NoSuchMessageException) {
      Log.w(TAG, "Failed to find message $messageId")
    }
  }

  @Throws(IOException::class)
  protected fun <D : Document<I>?, I> removeFromDocument(messageId: Long, column: String, item: I, clazz: Class<D>) {
    writableDatabase.withinTransaction { db ->
      val document: D = getDocument(db, messageId, column, clazz)
      val iterator = document!!.items.iterator()

      while (iterator.hasNext()) {
        val found = iterator.next()
        if (found == item) {
          iterator.remove()
          break
        }
      }

      setDocument(db, messageId, column, document)
    }
  }

  @Throws(IOException::class)
  protected fun <T : Document<I>?, I> addToDocument(messageId: Long, column: String, item: I, clazz: Class<T>) {
    addToDocument(messageId, column, listOf(item), clazz)
  }

  @Throws(IOException::class)
  protected fun <T : Document<I>?, I> addToDocument(messageId: Long, column: String, objects: List<I>?, clazz: Class<T>) {
    writableDatabase.withinTransaction { db ->
      val document: T = getDocument(db, messageId, column, clazz)
      document!!.items.addAll(objects!!)
      setDocument(db, messageId, column, document)
    }
  }

  @Throws(IOException::class)
  protected fun setDocument(database: SQLiteDatabase, messageId: Long, column: String?, document: Document<*>?) {
    val contentValues = ContentValues()

    if (document == null || document.size() == 0) {
      contentValues.put(column, null as String?)
    } else {
      contentValues.put(column, JsonUtils.toJson(document))
    }

    database
      .update(TABLE_NAME)
      .values(contentValues)
      .where("$ID = ?", messageId)
      .run()
  }

  private fun <D : Document<*>?> getDocument(
    database: SQLiteDatabase,
    messageId: Long,
    column: String,
    clazz: Class<D>
  ): D {
    return database
      .select(column)
      .from(TABLE_NAME)
      .where("$ID = ?", messageId)
      .run()
      .readToSingleObject { cursor ->
        val document: String? = cursor.requireString(column)

        if (!document.isNullOrEmpty()) {
          try {
            JsonUtils.fromJson(document, clazz)
          } catch (e: IOException) {
            Log.w(TAG, e)
            clazz.newInstance()
          }
        } else {
          clazz.newInstance()
        }
      }!!
  }

  fun getBodyRangesForMessages(messageIds: List<Long?>): Map<Long, BodyRangeList> {
    val bodyRanges: MutableMap<Long, BodyRangeList> = HashMap()

    SqlUtil.buildCollectionQuery(ID, messageIds).forEach { query ->
      readableDatabase
        .select(ID, MESSAGE_RANGES)
        .from(TABLE_NAME)
        .where(query.where, query.whereArgs)
        .run()
        .forEach { cursor ->
          val data: ByteArray? = cursor.requireBlob(MESSAGE_RANGES)

          if (data != null) {
            try {
              bodyRanges[CursorUtil.requireLong(cursor, ID)] = BodyRangeList.parseFrom(data)
            } catch (e: InvalidProtocolBufferException) {
              Log.w(TAG, "Unable to parse body ranges for search", e)
            }
          }
        }
    }

    return bodyRanges
  }

  private fun generatePduCompatTimestamp(time: Long): Long {
    return time - time % 1000
  }

  private fun getReleaseChannelThreadId(hasSeenReleaseChannelStories: Boolean): Long {
    if (hasSeenReleaseChannelStories) {
      return -1L
    }

    val releaseChannelRecipientId = SignalStore.releaseChannelValues().releaseChannelRecipientId ?: return -1L
    return threads.getThreadIdFor(releaseChannelRecipientId) ?: return -1L
  }

  private fun Cursor.toMarkedMessageInfo(): MarkedMessageInfo {
    return MarkedMessageInfo(
      messageId = MessageId(this.requireLong(ID)),
      threadId = this.requireLong(THREAD_ID),
      syncMessageId = SyncMessageId(
        recipientId = RecipientId.from(this.requireLong(RECIPIENT_ID)),
        timetamp = this.requireLong(DATE_SENT)
      ),
      expirationInfo = null,
      storyType = StoryType.fromCode(this.requireInt(STORY_TYPE))
    )
  }

  private fun ByteArray?.toIsoString(): String? {
    return if (this != null) {
      Util.toIsoString(this)
    } else {
      null
    }
  }

  protected enum class ReceiptType(val columnName: String, val groupStatus: Int) {
    READ(READ_RECEIPT_COUNT, GroupReceiptTable.STATUS_READ),
    DELIVERY(DELIVERY_RECEIPT_COUNT, GroupReceiptTable.STATUS_DELIVERED),
    VIEWED(VIEWED_RECEIPT_COUNT, GroupReceiptTable.STATUS_VIEWED);
  }

  data class SyncMessageId(
    val recipientId: RecipientId,
    val timetamp: Long
  )

  data class ExpirationInfo(
    val id: Long,
    val expiresIn: Long,
    val expireStarted: Long,
    val isMms: Boolean
  )

  data class MarkedMessageInfo(
    val threadId: Long,
    val syncMessageId: SyncMessageId,
    val messageId: MessageId,
    val expirationInfo: ExpirationInfo?,
    val storyType: StoryType
  )

  data class InsertResult(
    val messageId: Long,
    val threadId: Long
  )

  data class MmsNotificationInfo(
    val from: RecipientId,
    val contentLocation: String,
    val transactionId: String,
    val subscriptionId: Int
  )

  data class MessageUpdate(
    val threadId: Long,
    val messageId: MessageId
  )

  data class ReportSpamData(
    val recipientId: RecipientId,
    val serverGuid: String,
    val dateReceived: Long
  )

  private data class QuoteDescriptor(
    private val timestamp: Long,
    private val author: RecipientId
  )

  private class TimestampReadResult(
    val expiring: List<Pair<Long, Long>>,
    val threads: List<Long>
  )

  /**
   * Describes which messages to act on. This is used when incrementing receipts.
   * Specifically, this was added to support stories having separate viewed receipt settings.
   */
  enum class MessageQualifier {
    /** A normal database message (i.e. not a story) */
    NORMAL,

    /** A story message */
    STORY,

    /** Both normal and story message */
    ALL
  }

  object MmsStatus {
    const val DOWNLOAD_INITIALIZED = 1
    const val DOWNLOAD_NO_CONNECTIVITY = 2
    const val DOWNLOAD_CONNECTING = 3
    const val DOWNLOAD_SOFT_FAILURE = 4
    const val DOWNLOAD_HARD_FAILURE = 5
    const val DOWNLOAD_APN_UNAVAILABLE = 6
  }

  object Status {
    const val STATUS_NONE = -1
    const val STATUS_COMPLETE = 0
    const val STATUS_PENDING = 0x20
    const val STATUS_FAILED = 0x40
  }

  fun interface InsertListener {
    fun onComplete()
  }

  /**
   * Allows the developer to safely iterate over and close a cursor containing
   * data for MessageRecord objects. Supports for-each loops as well as try-with-resources
   * blocks.
   *
   * Readers are considered "one-shot" and it's on the caller to decide what needs
   * to be done with the data. Once read, a reader cannot be read from again. This
   * is by design, since reading data out of a cursor involves object creations and
   * lookups, so it is in the best interest of app performance to only read out the
   * data once. If you need to parse the list multiple times, it is recommended that
   * you copy the iterable out into a normal List, or use extension methods such as
   * partition.
   *
   * This reader does not support removal, since this would be considered a destructive
   * database call.
   */
  interface Reader : Closeable, Iterable<MessageRecord> {

    @Deprecated("Use the Iterable interface instead.")
    fun getNext(): MessageRecord?

    @Deprecated("Use the Iterable interface instead.")
    fun getCurrent(): MessageRecord

    /**
     * Pulls the export state out of the query, if it is present.
     */
    fun getMessageExportStateForCurrentRecord(): MessageExportState

    /**
     * From the [Closeable] interface, removing the IOException requirement.
     */
    override fun close()
  }

  /**
   * MessageRecord reader which implements the Iterable interface. This allows it to
   * be used with many Kotlin Extension Functions as well as with for-each loops.
   *
   * Note that it's the responsibility of the developer using the reader to ensure that:
   *
   * 1. They only utilize one of the two interfaces (legacy or iterator)
   * 1. They close this reader after use, preferably via try-with-resources or a use block.
   */
  class MmsReader(val cursor: Cursor) : Reader {
    private val context: Context

    init {
      context = ApplicationDependencies.getApplication()
    }

    override fun getNext(): MessageRecord? {
      return if (!cursor.moveToNext()) {
        null
      } else {
        getCurrent()
      }
    }

    override fun getCurrent(): MessageRecord {
      val mmsType = cursor.requireLong(MMS_MESSAGE_TYPE)

      return if (mmsType == PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND.toLong()) {
        getNotificationMmsMessageRecord(cursor)
      } else {
        getMediaMmsMessageRecord(cursor)
      }
    }

    override fun getMessageExportStateForCurrentRecord(): MessageExportState {
      val messageExportState = CursorUtil.requireBlob(cursor, EXPORT_STATE) ?: return MessageExportState.getDefaultInstance()
      return try {
        MessageExportState.parseFrom(messageExportState)
      } catch (e: InvalidProtocolBufferException) {
        MessageExportState.getDefaultInstance()
      }
    }

    override fun close() {
      cursor.close()
    }

    override fun iterator(): Iterator<MessageRecord> {
      return ReaderIterator()
    }

    fun getCount(): Int {
      return cursor.count
    }

    fun getCurrentId(): MessageId {
      return MessageId(cursor.requireLong(ID))
    }

    private fun getNotificationMmsMessageRecord(cursor: Cursor): NotificationMmsMessageRecord {
      val id = cursor.requireLong(ID)
      val dateSent = cursor.requireLong(DATE_SENT)
      val dateReceived = cursor.requireLong(DATE_RECEIVED)
      val threadId = cursor.requireLong(THREAD_ID)
      val mailbox = cursor.requireLong(TYPE)
      val recipientId = cursor.requireLong(RECIPIENT_ID)
      val addressDeviceId = cursor.requireInt(RECIPIENT_DEVICE_ID)
      val recipient = Recipient.live(RecipientId.from(recipientId)).get()
      val contentLocation = cursor.requireString(MMS_CONTENT_LOCATION).toIsoBytes()
      val transactionId = cursor.requireString(MMS_TRANSACTION_ID).toIsoBytes()
      val messageSize = cursor.requireLong(MMS_MESSAGE_SIZE)
      val expiry = cursor.requireLong(MMS_EXPIRY)
      val status = cursor.requireInt(MMS_STATUS)
      val deliveryReceiptCount = cursor.requireInt(DELIVERY_RECEIPT_COUNT)
      var readReceiptCount = cursor.requireInt(READ_RECEIPT_COUNT)
      val subscriptionId = cursor.requireInt(SMS_SUBSCRIPTION_ID)
      val viewedReceiptCount = cursor.requireInt(VIEWED_RECEIPT_COUNT)
      val receiptTimestamp = cursor.requireLong(RECEIPT_TIMESTAMP)
      val storyType = StoryType.fromCode(cursor.requireInt(STORY_TYPE))
      val parentStoryId = ParentStoryId.deserialize(cursor.requireLong(PARENT_STORY_ID))
      val body = cursor.requireString(BODY)

      if (!TextSecurePreferences.isReadReceiptsEnabled(context)) {
        readReceiptCount = 0
      }

      val slideDeck = SlideDeck(context, MmsNotificationAttachment(status, messageSize))
      val giftBadge: GiftBadge? = if (body != null && MessageTypes.isGiftBadge(mailbox)) {
        try {
          GiftBadge.parseFrom(Base64.decode(body))
        } catch (e: IOException) {
          Log.w(TAG, "Error parsing gift badge", e)
          null
        }
      } else {
        null
      }

      return NotificationMmsMessageRecord(
        id,
        recipient,
        recipient,
        addressDeviceId,
        dateSent,
        dateReceived,
        deliveryReceiptCount,
        threadId,
        contentLocation,
        messageSize,
        expiry,
        status,
        transactionId,
        mailbox,
        subscriptionId,
        slideDeck,
        readReceiptCount,
        viewedReceiptCount,
        receiptTimestamp,
        storyType,
        parentStoryId,
        giftBadge
      )
    }

    private fun getMediaMmsMessageRecord(cursor: Cursor): MediaMmsMessageRecord {
      val id = cursor.requireLong(ID)
      val dateSent = cursor.requireLong(DATE_SENT)
      val dateReceived = cursor.requireLong(DATE_RECEIVED)
      val dateServer = cursor.requireLong(DATE_SERVER)
      val box = cursor.requireLong(TYPE)
      val threadId = cursor.requireLong(THREAD_ID)
      val recipientId = cursor.requireLong(RECIPIENT_ID)
      val addressDeviceId = cursor.requireInt(RECIPIENT_DEVICE_ID)
      val deliveryReceiptCount = cursor.requireInt(DELIVERY_RECEIPT_COUNT)
      var readReceiptCount = cursor.requireInt(READ_RECEIPT_COUNT)
      val body = cursor.requireString(BODY)
      val mismatchDocument = cursor.requireString(MISMATCHED_IDENTITIES)
      val networkDocument = cursor.requireString(NETWORK_FAILURES)
      val subscriptionId = cursor.requireInt(SMS_SUBSCRIPTION_ID)
      val expiresIn = cursor.requireLong(EXPIRES_IN)
      val expireStarted = cursor.requireLong(EXPIRE_STARTED)
      val unidentified = cursor.requireBoolean(UNIDENTIFIED)
      val isViewOnce = cursor.requireBoolean(VIEW_ONCE)
      val remoteDelete = cursor.requireBoolean(REMOTE_DELETED)
      val mentionsSelf = cursor.requireBoolean(MENTIONS_SELF)
      val notifiedTimestamp = cursor.requireLong(NOTIFIED_TIMESTAMP)
      var viewedReceiptCount = cursor.requireInt(VIEWED_RECEIPT_COUNT)
      val receiptTimestamp = cursor.requireLong(RECEIPT_TIMESTAMP)
      val messageRangesData = cursor.requireBlob(MESSAGE_RANGES)
      val storyType = StoryType.fromCode(cursor.requireInt(STORY_TYPE))
      val parentStoryId = ParentStoryId.deserialize(cursor.requireLong(PARENT_STORY_ID))
      val scheduledDate = cursor.requireLong(SCHEDULED_DATE)

      if (!TextSecurePreferences.isReadReceiptsEnabled(context)) {
        readReceiptCount = 0
        if (MessageTypes.isOutgoingMessageType(box) && !storyType.isStory) {
          viewedReceiptCount = 0
        }
      }

      val recipient = Recipient.live(RecipientId.from(recipientId)).get()
      val mismatches = getMismatchedIdentities(mismatchDocument)
      val networkFailures = getFailures(networkDocument)

      val attachments = attachments.getAttachments(cursor)

      val contacts = getSharedContacts(cursor, attachments)
      val contactAttachments = contacts.mapNotNull { it.avatarAttachment }.toSet()

      val previews = getLinkPreviews(cursor, attachments)
      val previewAttachments = previews.mapNotNull { it.thumbnail.orElse(null) }.toSet()

      val slideDeck = buildSlideDeck(context, attachments.filterNot { contactAttachments.contains(it) }.filterNot { previewAttachments.contains(it) })

      val quote = getQuote(cursor)

      val messageRanges: BodyRangeList? = if (messageRangesData != null) {
        try {
          BodyRangeList.parseFrom(messageRangesData)
        } catch (e: InvalidProtocolBufferException) {
          Log.w(TAG, "Error parsing message ranges", e)
          null
        }
      } else {
        null
      }

      val giftBadge: GiftBadge? = if (body != null && MessageTypes.isGiftBadge(box)) {
        try {
          GiftBadge.parseFrom(Base64.decode(body))
        } catch (e: IOException) {
          Log.w(TAG, "Error parsing gift badge", e)
          null
        }
      } else {
        null
      }

      return MediaMmsMessageRecord(
        id,
        recipient,
        recipient,
        addressDeviceId,
        dateSent,
        dateReceived,
        dateServer,
        deliveryReceiptCount,
        threadId,
        body,
        slideDeck,
        box,
        mismatches,
        networkFailures,
        subscriptionId,
        expiresIn,
        expireStarted,
        isViewOnce,
        readReceiptCount,
        quote,
        contacts,
        previews,
        unidentified,
        emptyList(),
        remoteDelete,
        mentionsSelf,
        notifiedTimestamp,
        viewedReceiptCount,
        receiptTimestamp,
        messageRanges,
        storyType,
        parentStoryId,
        giftBadge,
        null,
        null,
        scheduledDate
      )
    }

    private fun getMismatchedIdentities(document: String?): Set<IdentityKeyMismatch> {
      if (!TextUtils.isEmpty(document)) {
        try {
          return JsonUtils.fromJson(document, IdentityKeyMismatchSet::class.java).items
        } catch (e: IOException) {
          Log.w(TAG, e)
        }
      }

      return emptySet()
    }

    private fun getFailures(document: String?): Set<NetworkFailure> {
      if (!TextUtils.isEmpty(document)) {
        try {
          return JsonUtils.fromJson(document, NetworkFailureSet::class.java).items
        } catch (ioe: IOException) {
          Log.w(TAG, ioe)
        }
      }

      return emptySet()
    }

    private fun getQuote(cursor: Cursor): Quote? {
      val quoteId = cursor.requireLong(QUOTE_ID)
      val quoteAuthor = cursor.requireLong(QUOTE_AUTHOR)
      var quoteText: CharSequence? = cursor.requireString(QUOTE_BODY)
      val quoteType = cursor.requireInt(QUOTE_TYPE)
      val quoteMissing = cursor.requireBoolean(QUOTE_MISSING)
      var quoteMentions = parseQuoteMentions(cursor)
      val bodyRanges = parseQuoteBodyRanges(cursor)

      val attachments = attachments.getAttachments(cursor)
      val quoteAttachments: List<Attachment> = attachments.filter { it.isQuote }
      val quoteDeck = SlideDeck(context, quoteAttachments)

      return if (quoteId > 0 && quoteAuthor > 0) {
        if (quoteText != null && (quoteMentions.isNotEmpty() || bodyRanges != null)) {
          val updated: UpdatedBodyAndMentions = MentionUtil.updateBodyAndMentionsWithDisplayNames(context, quoteText, quoteMentions)
          val styledText = SpannableString(updated.body)

          MessageStyler.style(id = quoteId, messageRanges = bodyRanges.adjustBodyRanges(updated.bodyAdjustments), span = styledText)

          quoteText = styledText
          quoteMentions = updated.mentions
        }

        Quote(
          quoteId,
          RecipientId.from(quoteAuthor),
          quoteText,
          quoteMissing,
          quoteDeck,
          quoteMentions,
          QuoteModel.Type.fromCode(quoteType)
        )
      } else {
        null
      }
    }

    private fun String?.toIsoBytes(): ByteArray? {
      return if (this != null && this.isNotEmpty()) {
        Util.toIsoBytes(this)
      } else {
        null
      }
    }

    private inner class ReaderIterator : Iterator<MessageRecord> {
      override fun hasNext(): Boolean {
        return cursor.count != 0 && !cursor.isLast
      }

      override fun next(): MessageRecord {
        return getNext() ?: throw NoSuchElementException()
      }
    }

    companion object {

      @JvmStatic
      fun buildSlideDeck(context: Context, attachments: List<DatabaseAttachment>): SlideDeck {
        val messageAttachments = attachments
          .filterNot { it.isQuote }
          .sortedWith(DisplayOrderComparator())

        return SlideDeck(context, messageAttachments)
      }
    }
  }
}
