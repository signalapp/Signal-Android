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
import android.database.sqlite.SQLiteException
import android.text.SpannableString
import android.text.TextUtils
import androidx.annotation.VisibleForTesting
import androidx.core.content.contentValuesOf
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.signal.core.util.Base64
import org.signal.core.util.CursorUtil
import org.signal.core.util.SqlUtil
import org.signal.core.util.SqlUtil.buildArgs
import org.signal.core.util.SqlUtil.buildCustomCollectionQuery
import org.signal.core.util.SqlUtil.buildSingleCollectionQuery
import org.signal.core.util.SqlUtil.buildTrueUpdateQuery
import org.signal.core.util.SqlUtil.getNextAutoIncrementId
import org.signal.core.util.Stopwatch
import org.signal.core.util.count
import org.signal.core.util.delete
import org.signal.core.util.deleteAll
import org.signal.core.util.exists
import org.signal.core.util.forEach
import org.signal.core.util.insertInto
import org.signal.core.util.logging.Log
import org.signal.core.util.readToList
import org.signal.core.util.readToSet
import org.signal.core.util.readToSingleInt
import org.signal.core.util.readToSingleLong
import org.signal.core.util.readToSingleLongOrNull
import org.signal.core.util.readToSingleObject
import org.signal.core.util.requireBlob
import org.signal.core.util.requireBoolean
import org.signal.core.util.requireInt
import org.signal.core.util.requireLong
import org.signal.core.util.requireLongOrNull
import org.signal.core.util.requireNonNullString
import org.signal.core.util.requireString
import org.signal.core.util.select
import org.signal.core.util.toInt
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
import org.thoughtcrime.securesms.contactshare.Contact
import org.thoughtcrime.securesms.conversation.MessageStyler
import org.thoughtcrime.securesms.database.EarlyDeliveryReceiptCache.Receipt
import org.thoughtcrime.securesms.database.MentionUtil.UpdatedBodyAndMentions
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.attachments
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.calls
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.distributionLists
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.groupReceipts
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.groups
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.mentions
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
import org.thoughtcrime.securesms.database.model.Mention
import org.thoughtcrime.securesms.database.model.MessageExportStatus
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
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
import org.thoughtcrime.securesms.database.model.databaseprotos.MessageExtras
import org.thoughtcrime.securesms.database.model.databaseprotos.ProfileChangeDetails
import org.thoughtcrime.securesms.database.model.databaseprotos.SessionSwitchoverEvent
import org.thoughtcrime.securesms.database.model.databaseprotos.ThreadMergeEvent
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.groups.GroupMigrationMembershipChange
import org.thoughtcrime.securesms.jobs.OptimizeMessageSearchIndexJob
import org.thoughtcrime.securesms.jobs.ThreadUpdateJob
import org.thoughtcrime.securesms.jobs.TrimThreadJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.linkpreview.LinkPreview
import org.thoughtcrime.securesms.mms.IncomingMessage
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
import org.thoughtcrime.securesms.sms.GroupV2UpdateMessageUtil
import org.thoughtcrime.securesms.stories.Stories.isFeatureEnabled
import org.thoughtcrime.securesms.util.JsonUtils
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.MessageConstraintsUtil
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.Util
import org.thoughtcrime.securesms.util.isStory
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.internal.push.SyncMessage
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
    const val FROM_RECIPIENT_ID = "from_recipient_id"
    const val FROM_DEVICE_ID = "from_device_id"
    const val TO_RECIPIENT_ID = "to_recipient_id"
    const val HAS_DELIVERY_RECEIPT = "has_delivery_receipt"
    const val HAS_READ_RECEIPT = "has_read_receipt"
    const val VIEWED_COLUMN = "viewed"
    const val MISMATCHED_IDENTITIES = "mismatched_identities"
    const val SMS_SUBSCRIPTION_ID = "subscription_id"
    const val EXPIRES_IN = "expires_in"
    const val EXPIRE_STARTED = "expire_started"
    const val EXPIRE_TIMER_VERSION = "expire_timer_version"
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
    const val LATEST_REVISION_ID = "latest_revision_id"
    const val ORIGINAL_MESSAGE_ID = "original_message_id"
    const val REVISION_NUMBER = "revision_number"
    const val MESSAGE_EXTRAS = "message_extras"

    const val QUOTE_NOT_PRESENT_ID = 0L
    const val QUOTE_TARGET_MISSING_ID = -1L

    const val ADDRESSABLE_MESSAGE_LIMIT = 5
    const val PARENT_STORY_MISSING_ID = -1L

    const val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY AUTOINCREMENT,
        $DATE_SENT INTEGER NOT NULL,
        $DATE_RECEIVED INTEGER NOT NULL,
        $DATE_SERVER INTEGER DEFAULT -1,
        $THREAD_ID INTEGER NOT NULL REFERENCES ${ThreadTable.TABLE_NAME} (${ThreadTable.ID}) ON DELETE CASCADE,
        $FROM_RECIPIENT_ID INTEGER NOT NULL REFERENCES ${RecipientTable.TABLE_NAME} (${RecipientTable.ID}) ON DELETE CASCADE,
        $FROM_DEVICE_ID INTEGER,
        $TO_RECIPIENT_ID INTEGER NOT NULL REFERENCES ${RecipientTable.TABLE_NAME} (${RecipientTable.ID}) ON DELETE CASCADE,
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
        $HAS_DELIVERY_RECEIPT INTEGER DEFAULT 0, 
        $HAS_READ_RECEIPT INTEGER DEFAULT 0, 
        $VIEWED_COLUMN INTEGER DEFAULT 0,
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
        $SCHEDULED_DATE INTEGER DEFAULT -1,
        $LATEST_REVISION_ID INTEGER DEFAULT NULL REFERENCES $TABLE_NAME ($ID) ON DELETE CASCADE,
        $ORIGINAL_MESSAGE_ID INTEGER DEFAULT NULL REFERENCES $TABLE_NAME ($ID) ON DELETE CASCADE,
        $REVISION_NUMBER INTEGER DEFAULT 0,
        $MESSAGE_EXTRAS BLOB DEFAULT NULL,
        $EXPIRE_TIMER_VERSION INTEGER DEFAULT 1 NOT NULL
      )
    """

    private const val INDEX_THREAD_STORY_SCHEDULED_DATE_LATEST_REVISION_ID = "message_thread_story_parent_story_scheduled_date_latest_revision_id_index"
    private const val INDEX_DATE_SENT_FROM_TO_THREAD = "message_date_sent_from_to_thread_index"
    private const val INDEX_THREAD_COUNT = "message_thread_count_index"
    private const val INDEX_THREAD_UNREAD_COUNT = "message_thread_unread_count_index"

    @JvmField
    val CREATE_INDEXS = arrayOf(
      "CREATE INDEX IF NOT EXISTS message_read_and_notified_and_thread_id_index ON $TABLE_NAME ($READ, $NOTIFIED, $THREAD_ID)",
      "CREATE INDEX IF NOT EXISTS message_type_index ON $TABLE_NAME ($TYPE)",
      "CREATE INDEX IF NOT EXISTS $INDEX_DATE_SENT_FROM_TO_THREAD ON $TABLE_NAME ($DATE_SENT, $FROM_RECIPIENT_ID, $TO_RECIPIENT_ID, $THREAD_ID)",
      "CREATE INDEX IF NOT EXISTS message_date_server_index ON $TABLE_NAME ($DATE_SERVER)",
      "CREATE INDEX IF NOT EXISTS message_reactions_unread_index ON $TABLE_NAME ($REACTIONS_UNREAD);",
      "CREATE INDEX IF NOT EXISTS message_story_type_index ON $TABLE_NAME ($STORY_TYPE);",
      "CREATE INDEX IF NOT EXISTS message_parent_story_id_index ON $TABLE_NAME ($PARENT_STORY_ID);",
      "CREATE INDEX IF NOT EXISTS $INDEX_THREAD_STORY_SCHEDULED_DATE_LATEST_REVISION_ID ON $TABLE_NAME ($THREAD_ID, $DATE_RECEIVED, $STORY_TYPE, $PARENT_STORY_ID, $SCHEDULED_DATE, $LATEST_REVISION_ID);",
      "CREATE INDEX IF NOT EXISTS message_quote_id_quote_author_scheduled_date_latest_revision_id_index ON $TABLE_NAME ($QUOTE_ID, $QUOTE_AUTHOR, $SCHEDULED_DATE, $LATEST_REVISION_ID);",
      "CREATE INDEX IF NOT EXISTS message_exported_index ON $TABLE_NAME ($EXPORTED);",
      "CREATE INDEX IF NOT EXISTS message_id_type_payment_transactions_index ON $TABLE_NAME ($ID,$TYPE) WHERE $TYPE & ${MessageTypes.SPECIAL_TYPE_PAYMENTS_NOTIFICATION} != 0;",
      "CREATE INDEX IF NOT EXISTS message_original_message_id_index ON $TABLE_NAME ($ORIGINAL_MESSAGE_ID);",
      "CREATE INDEX IF NOT EXISTS message_latest_revision_id_index ON $TABLE_NAME ($LATEST_REVISION_ID)",
      "CREATE INDEX IF NOT EXISTS message_from_recipient_id_index ON $TABLE_NAME ($FROM_RECIPIENT_ID)",
      "CREATE INDEX IF NOT EXISTS message_to_recipient_id_index ON $TABLE_NAME ($TO_RECIPIENT_ID)",
      "CREATE UNIQUE INDEX IF NOT EXISTS message_unique_sent_from_thread ON $TABLE_NAME ($DATE_SENT, $FROM_RECIPIENT_ID, $THREAD_ID)",
      // This index is created specifically for getting the number of messages in a thread and therefore needs to be kept in sync with that query
      "CREATE INDEX IF NOT EXISTS $INDEX_THREAD_COUNT ON $TABLE_NAME ($THREAD_ID) WHERE $STORY_TYPE = 0 AND $PARENT_STORY_ID <= 0 AND $SCHEDULED_DATE = -1 AND $LATEST_REVISION_ID IS NULL",
      // This index is created specifically for getting the number of unread messages in a thread and therefore needs to be kept in sync with that query
      "CREATE INDEX IF NOT EXISTS $INDEX_THREAD_UNREAD_COUNT ON $TABLE_NAME ($THREAD_ID) WHERE $STORY_TYPE = 0 AND $PARENT_STORY_ID <= 0 AND $SCHEDULED_DATE = -1 AND $LATEST_REVISION_ID IS NULL AND $READ = 0"
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
      MMS_MESSAGE_SIZE,
      MMS_STATUS,
      MMS_TRANSACTION_ID,
      BODY,
      FROM_RECIPIENT_ID,
      FROM_DEVICE_ID,
      TO_RECIPIENT_ID,
      HAS_DELIVERY_RECEIPT,
      HAS_READ_RECEIPT,
      MISMATCHED_IDENTITIES,
      NETWORK_FAILURES,
      SMS_SUBSCRIPTION_ID,
      EXPIRES_IN,
      EXPIRE_STARTED,
      EXPIRE_TIMER_VERSION,
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
      VIEWED_COLUMN,
      RECEIPT_TIMESTAMP,
      MESSAGE_RANGES,
      STORY_TYPE,
      PARENT_STORY_ID,
      SCHEDULED_DATE,
      LATEST_REVISION_ID,
      ORIGINAL_MESSAGE_ID,
      REVISION_NUMBER,
      MESSAGE_EXTRAS
    )

    private val MMS_PROJECTION: Array<String> = MMS_PROJECTION_BASE + "NULL AS ${AttachmentTable.ATTACHMENT_JSON_ALIAS}"

    private val MMS_PROJECTION_WITH_ATTACHMENTS: Array<String> = MMS_PROJECTION_BASE +
      """
        json_group_array(
          json_object(
            '${AttachmentTable.ID}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.ID}, 
            '${AttachmentTable.MESSAGE_ID}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.MESSAGE_ID},
            '${AttachmentTable.DATA_SIZE}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.DATA_SIZE}, 
            '${AttachmentTable.FILE_NAME}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.FILE_NAME}, 
            '${AttachmentTable.DATA_FILE}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.DATA_FILE},
            '${AttachmentTable.THUMBNAIL_FILE}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.THUMBNAIL_FILE},
            '${AttachmentTable.CONTENT_TYPE}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.CONTENT_TYPE}, 
            '${AttachmentTable.CDN_NUMBER}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.CDN_NUMBER}, 
            '${AttachmentTable.REMOTE_LOCATION}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.REMOTE_LOCATION}, 
            '${AttachmentTable.FAST_PREFLIGHT_ID}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.FAST_PREFLIGHT_ID},
            '${AttachmentTable.VOICE_NOTE}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.VOICE_NOTE},
            '${AttachmentTable.BORDERLESS}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.BORDERLESS},
            '${AttachmentTable.VIDEO_GIF}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.VIDEO_GIF},
            '${AttachmentTable.WIDTH}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.WIDTH},
            '${AttachmentTable.HEIGHT}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.HEIGHT},
            '${AttachmentTable.QUOTE}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.QUOTE},
            '${AttachmentTable.REMOTE_KEY}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.REMOTE_KEY},
            '${AttachmentTable.TRANSFER_STATE}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.TRANSFER_STATE},
            '${AttachmentTable.CAPTION}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.CAPTION},
            '${AttachmentTable.STICKER_PACK_ID}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.STICKER_PACK_ID},
            '${AttachmentTable.STICKER_PACK_KEY}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.STICKER_PACK_KEY},
            '${AttachmentTable.STICKER_ID}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.STICKER_ID},
            '${AttachmentTable.STICKER_EMOJI}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.STICKER_EMOJI},
            '${AttachmentTable.BLUR_HASH}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.BLUR_HASH},
            '${AttachmentTable.TRANSFORM_PROPERTIES}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.TRANSFORM_PROPERTIES},
            '${AttachmentTable.DISPLAY_ORDER}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.DISPLAY_ORDER},
            '${AttachmentTable.UPLOAD_TIMESTAMP}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.UPLOAD_TIMESTAMP},
            '${AttachmentTable.DATA_HASH_END}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.DATA_HASH_END},
            '${AttachmentTable.ARCHIVE_CDN}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.ARCHIVE_CDN},
            '${AttachmentTable.ARCHIVE_MEDIA_NAME}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.ARCHIVE_MEDIA_NAME},
            '${AttachmentTable.ARCHIVE_MEDIA_ID}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.ARCHIVE_MEDIA_ID},
            '${AttachmentTable.THUMBNAIL_RESTORE_STATE}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.THUMBNAIL_RESTORE_STATE},
            '${AttachmentTable.ARCHIVE_TRANSFER_STATE}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.ARCHIVE_TRANSFER_STATE},
            '${AttachmentTable.ATTACHMENT_UUID}', ${AttachmentTable.TABLE_NAME}.${AttachmentTable.ATTACHMENT_UUID}
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
          $TABLE_NAME INDEXED BY $INDEX_THREAD_STORY_SCHEDULED_DATE_LATEST_REVISION_ID
        WHERE 
          $THREAD_ID = ? AND 
          $TYPE & ${MessageTypes.GROUP_V2_LEAVE_BITS} != ${MessageTypes.GROUP_V2_LEAVE_BITS} AND 
          $STORY_TYPE = 0 AND 
          $PARENT_STORY_ID <= 0 AND
          $SCHEDULED_DATE = -1 AND
          $LATEST_REVISION_ID IS NULL AND
          $TYPE & ${MessageTypes.KEY_EXCHANGE_IDENTITY_DEFAULT_BIT} = 0 AND
          $TYPE & ${MessageTypes.KEY_EXCHANGE_IDENTITY_VERIFIED_BIT} = 0 AND
          $TYPE & ${MessageTypes.SPECIAL_TYPES_MASK} != ${MessageTypes.SPECIAL_TYPE_REPORTED_SPAM} AND
          $TYPE & ${MessageTypes.SPECIAL_TYPES_MASK} != ${MessageTypes.SPECIAL_TYPE_MESSAGE_REQUEST_ACCEPTED} AND
          $TYPE & ${MessageTypes.SPECIAL_TYPES_MASK} != ${MessageTypes.SPECIAL_TYPE_BLOCKED} AND
          $TYPE & ${MessageTypes.SPECIAL_TYPES_MASK} != ${MessageTypes.SPECIAL_TYPE_UNBLOCKED} AND
          $TYPE NOT IN (
            ${MessageTypes.PROFILE_CHANGE_TYPE}, 
            ${MessageTypes.GV1_MIGRATION_TYPE},
            ${MessageTypes.CHANGE_NUMBER_TYPE},
            ${MessageTypes.RELEASE_CHANNEL_DONATION_REQUEST_TYPE},
            ${MessageTypes.SMS_EXPORT_TYPE}
           )
          ORDER BY $DATE_RECEIVED DESC LIMIT 1
       """

    const val IS_CALL_TYPE_CLAUSE = """(
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
      OR
      ($TYPE = ${MessageTypes.GROUP_CALL_TYPE})
    )"""

    private const val IS_MISSED_CALL_TYPE_CLAUSE = """(
      ($TYPE = ${MessageTypes.MISSED_AUDIO_CALL_TYPE})
      OR
      ($TYPE = ${MessageTypes.MISSED_VIDEO_CALL_TYPE})
    )"""

    private val outgoingTypeClause: String by lazy {
      MessageTypes.OUTGOING_MESSAGE_TYPES
        .map { "($TABLE_NAME.$TYPE & ${MessageTypes.BASE_TYPE_MASK} = $it)" }
        .joinToString(" OR ")
    }

    /**
     * A message that can be correctly identified with an author/sent timestamp across devices.
     *
     * Must be:
     * - Incoming or sent outgoing
     * - Secure or push
     * - Not a group update
     * - Not a key exchange message
     * - Not an encryption message
     * - Not a report spam message
     * - Not a message rqeuest accepted message
     * - Not be a story
     * - Have a valid sent timestamp
     * - Be a normal message or direct (1:1) story reply
     *
     * Changes should be reflected in [MmsMessageRecord.canDeleteSync].
     */
    private const val IS_ADDRESSABLE_CLAUSE = """
      (($TYPE & ${MessageTypes.BASE_TYPE_MASK}) = ${MessageTypes.BASE_SENT_TYPE} OR ($TYPE & ${MessageTypes.BASE_TYPE_MASK}) = ${MessageTypes.BASE_INBOX_TYPE}) AND 
      ($TYPE & (${MessageTypes.SECURE_MESSAGE_BIT} | ${MessageTypes.PUSH_MESSAGE_BIT})) != 0 AND 
      ($TYPE & ${MessageTypes.GROUP_MASK}) = 0 AND 
      ($TYPE & ${MessageTypes.KEY_EXCHANGE_MASK}) = 0 AND 
      ($TYPE & ${MessageTypes.ENCRYPTION_MASK}) = 0 AND
      ($TYPE & ${MessageTypes.SPECIAL_TYPES_MASK}) != ${MessageTypes.SPECIAL_TYPE_REPORTED_SPAM} AND
      ($TYPE & ${MessageTypes.SPECIAL_TYPES_MASK}) != ${MessageTypes.SPECIAL_TYPE_MESSAGE_REQUEST_ACCEPTED} AND
      $STORY_TYPE = 0 AND
      $DATE_SENT > 0 AND
      $PARENT_STORY_ID <= 0
    """

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
          BodyRangeList.ADAPTER.decode(data)
        } catch (e: IOException) {
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
            .ADAPTER.decode(data)
            .ranges
            .filter { bodyRange -> bodyRange.mentionUuid == null }

          return BodyRangeList(ranges = bodyRanges)
        } catch (e: IOException) {
          Log.w(TAG, "Unable to parse quote body ranges", e)
        }
      }
      return null
    }
  }

  private val earlyDeliveryReceiptCache = EarlyDeliveryReceiptCache()

  private fun getOldestGroupUpdateSender(threadId: Long, minimumDateReceived: Long): RecipientId? {
    val type = MessageTypes.SECURE_MESSAGE_BIT or MessageTypes.PUSH_MESSAGE_BIT or MessageTypes.GROUP_UPDATE_BIT or MessageTypes.BASE_INBOX_TYPE

    return readableDatabase
      .select(FROM_RECIPIENT_ID)
      .from(TABLE_NAME)
      .where("$THREAD_ID = ? AND $TYPE & ? AND $DATE_RECEIVED >= ?", threadId.toString(), type.toString(), minimumDateReceived.toString())
      .limit(1)
      .run()
      .readToSingleObject { RecipientId.from(it.requireLong(FROM_RECIPIENT_ID)) }
  }

  fun getExpirationStartedMessages(): Cursor {
    val where = "$EXPIRE_STARTED > 0"
    return rawQueryWithAttachments(where, null)
  }

  fun getMessagesBySentTimestamp(sentTimestamp: Long): List<MessageRecord> {
    return readableDatabase
      .select(*MMS_PROJECTION)
      .from(TABLE_NAME)
      .where("$DATE_SENT = $sentTimestamp")
      .run()
      .readToList { MmsReader(it).getCurrent() }
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

  fun markAsInvalidVersionKeyExchange(id: Long) {
    updateTypeBitmask(id, 0, MessageTypes.KEY_EXCHANGE_INVALID_VERSION_BIT)
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
        """,
        buildArgs(id)
      )

      val threadId = getThreadIdForMessage(id)
      threads.updateSnippetTypeSilently(threadId)
    }

    AppDependencies.databaseObserver.notifyMessageUpdateObservers(MessageId(id))
    AppDependencies.databaseObserver.notifyConversationListListeners()
  }

  private fun updateMessageBodyAndType(messageId: Long, body: String, maskOff: Long, maskOn: Long): InsertResult {
    writableDatabase.execSQL(
      """
        UPDATE $TABLE_NAME
        SET
          $BODY = ?,
          $TYPE = ($TYPE & ${MessageTypes.TOTAL_MASK - maskOff} | $maskOn) 
        WHERE $ID = ?
      """,
      arrayOf(body, messageId.toString() + "")
    )

    val threadId = getThreadIdForMessage(messageId)
    threads.update(threadId, true)
    notifyConversationListeners(threadId)

    return InsertResult(
      messageId = messageId,
      threadId = threadId,
      threadWasNewlyCreated = false
    )
  }

  fun updateBundleMessageBody(messageId: Long, body: String): InsertResult {
    val type = MessageTypes.BASE_INBOX_TYPE or MessageTypes.SECURE_MESSAGE_BIT or MessageTypes.PUSH_MESSAGE_BIT
    return updateMessageBodyAndType(messageId, body, MessageTypes.TOTAL_MASK, type)
  }

  fun getViewedIncomingMessages(threadId: Long): List<MarkedMessageInfo> {
    return readableDatabase
      .select(ID, FROM_RECIPIENT_ID, DATE_RECEIVED, DATE_SENT, TYPE, THREAD_ID, STORY_TYPE)
      .from(TABLE_NAME)
      .where("$THREAD_ID = ? AND $VIEWED_COLUMN > 0 AND $TYPE & ${MessageTypes.BASE_INBOX_TYPE} = ${MessageTypes.BASE_INBOX_TYPE}", threadId)
      .run()
      .readToList { it.toMarkedMessageInfo(outgoing = false) }
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
      .select(ID, FROM_RECIPIENT_ID, DATE_SENT, DATE_RECEIVED, TYPE, THREAD_ID, STORY_TYPE)
      .from(TABLE_NAME)
      .where("$ID IN (${Util.join(messageIds, ",")}) AND $VIEWED_COLUMN = 0")
      .run()
      .readToList { cursor ->
        val type = cursor.requireLong(TYPE)

        if (MessageTypes.isSecureType(type) && MessageTypes.isInboxType(type)) {
          cursor.toMarkedMessageInfo(outgoing = false)
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
            VIEWED_COLUMN to 1,
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
    AppDependencies.databaseObserver.notifyStoryObservers(storyRecipientsUpdated)

    return results
  }

  fun setOutgoingGiftsRevealed(messageIds: List<Long>): List<MarkedMessageInfo> {
    val results: List<MarkedMessageInfo> = readableDatabase
      .select(ID, TO_RECIPIENT_ID, DATE_SENT, DATE_RECEIVED, THREAD_ID, STORY_TYPE)
      .from(TABLE_NAME)
      .where("""$ID IN (${Util.join(messageIds, ",")}) AND ($outgoingTypeClause) AND ($TYPE & ${MessageTypes.SPECIAL_TYPES_MASK} = ${MessageTypes.SPECIAL_TYPE_GIFT_BADGE}) AND $VIEWED_COLUMN = 0""")
      .run()
      .readToList { it.toMarkedMessageInfo(outgoing = true) }

    val currentTime = System.currentTimeMillis()
    SqlUtil
      .buildCollectionQuery(ID, results.map { it.messageId.id })
      .forEach { query ->
        writableDatabase
          .update(TABLE_NAME)
          .values(
            VIEWED_COLUMN to 1,
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

  fun insertCallLog(recipientId: RecipientId, type: Long, timestamp: Long, outgoing: Boolean): InsertResult {
    val recipient = Recipient.resolved(recipientId)
    val threadIdResult = threads.getOrCreateThreadIdResultFor(recipient.id, recipient.isGroup)
    val threadId = threadIdResult.threadId

    val values = contentValuesOf(
      FROM_RECIPIENT_ID to if (outgoing) Recipient.self().id.serialize() else recipientId.serialize(),
      FROM_DEVICE_ID to 1,
      TO_RECIPIENT_ID to if (outgoing) recipientId.serialize() else Recipient.self().id.serialize(),
      DATE_RECEIVED to System.currentTimeMillis(),
      DATE_SENT to timestamp,
      READ to 1,
      TYPE to type,
      THREAD_ID to threadId
    )

    val messageId = writableDatabase.insert(TABLE_NAME, null, values)

    threads.update(threadId, true)

    notifyConversationListeners(threadId)
    TrimThreadJob.enqueueAsync(threadId)

    return InsertResult(
      messageId = messageId,
      threadId = threadId,
      threadWasNewlyCreated = threadIdResult.newlyCreated
    )
  }

  fun updateCallLog(messageId: Long, type: Long) {
    writableDatabase
      .update(TABLE_NAME)
      .values(
        TYPE to type,
        READ to 1
      )
      .where("$ID = ?", messageId)
      .run()

    val threadId = getThreadIdForMessage(messageId)

    threads.update(threadId, true)

    notifyConversationListeners(threadId)
    AppDependencies.databaseObserver.notifyMessageUpdateObservers(MessageId(messageId))
  }

  fun insertGroupCall(
    groupRecipientId: RecipientId,
    sender: RecipientId,
    timestamp: Long,
    eraId: String,
    joinedUuids: Collection<UUID>,
    isCallFull: Boolean,
    isIncomingGroupCallRingingOnLocalDevice: Boolean
  ): MessageId {
    val recipient = Recipient.resolved(groupRecipientId)
    val threadId = threads.getOrCreateThreadIdFor(recipient)
    val messageId: MessageId = writableDatabase.withinTransaction { db ->
      val self = Recipient.self()
      val markRead = joinedUuids.contains(self.requireServiceId().rawUuid) || self.id == sender
      val updateDetails: ByteArray = GroupCallUpdateDetails(
        eraId = eraId,
        startedCallUuid = Recipient.resolved(sender).requireServiceId().toString(),
        startedCallTimestamp = timestamp,
        inCallUuids = joinedUuids.map { it.toString() },
        isCallFull = isCallFull,
        localUserJoined = joinedUuids.contains(Recipient.self().requireServiceId().rawUuid),
        isRingingOnLocalDevice = isIncomingGroupCallRingingOnLocalDevice
      ).encode()

      val values = contentValuesOf(
        FROM_RECIPIENT_ID to sender.serialize(),
        FROM_DEVICE_ID to 1,
        TO_RECIPIENT_ID to groupRecipientId.serialize(),
        DATE_RECEIVED to timestamp,
        DATE_SENT to timestamp,
        READ to if (markRead) 1 else 0,
        BODY to Base64.encodeWithPadding(updateDetails),
        TYPE to MessageTypes.GROUP_CALL_TYPE,
        THREAD_ID to threadId
      )

      val messageId = MessageId(db.insert(TABLE_NAME, null, values))
      threads.incrementUnread(threadId, 1, 0)
      threads.update(threadId, true)

      messageId
    }

    notifyConversationListeners(threadId)
    TrimThreadJob.enqueueAsync(threadId)

    return messageId
  }

  /**
   * Updates the timestamps associated with the given message id to the given ts
   */
  fun updateCallTimestamps(messageId: Long, timestamp: Long) {
    val message = try {
      getMessageRecord(messageId = messageId)
    } catch (e: NoSuchMessageException) {
      error("Message $messageId does not exist")
    }

    val updateDetail = GroupCallUpdateDetailsUtil.parse(message.body)
    val contentValues = contentValuesOf(
      BODY to Base64.encodeWithPadding(updateDetail.newBuilder().startedCallTimestamp(timestamp).build().encode()),
      DATE_SENT to timestamp,
      DATE_RECEIVED to timestamp
    )

    val query = buildTrueUpdateQuery(ID_WHERE, buildArgs(messageId), contentValues)
    val updated = writableDatabase.update(TABLE_NAME, contentValues, query.where, query.whereArgs) > 0

    if (updated) {
      notifyConversationListeners(message.threadId)
    }
  }

  /**
   * Clears the flag in GroupCallUpdateDetailsUtil that specifies that the call is ringing on the local device.
   * Called when cleaning up the call ringing state (which can get out of sync in the case of an application crash)
   */
  fun clearIsRingingOnLocalDeviceFlag(messageIds: Collection<Long>) {
    writableDatabase.withinTransaction { db ->
      val queries = SqlUtil.buildCollectionQuery(ID, messageIds)

      for (query in queries) {
        val messageIdBodyPairs = db.select(ID, BODY)
          .from(TABLE_NAME)
          .where(query.where, query.whereArgs)
          .run()
          .readToList { cursor ->
            cursor.requireLong(ID) to cursor.requireString(BODY)
          }

        for ((messageId, body) in messageIdBodyPairs) {
          val oldBody = GroupCallUpdateDetailsUtil.parse(body)
          if (!oldBody.isRingingOnLocalDevice) {
            continue
          }

          val newBody = GroupCallUpdateDetailsUtil.createUpdatedBody(oldBody, oldBody.inCallUuids, oldBody.isCallFull, false)

          db.update(TABLE_NAME)
            .values(BODY to newBody)
            .where(ID_WHERE, messageId)
            .run()
        }
      }
    }
  }

  fun updateGroupCall(
    messageId: Long,
    eraId: String,
    joinedUuids: Collection<UUID>,
    isCallFull: Boolean,
    isRingingOnLocalDevice: Boolean
  ): MessageId {
    writableDatabase.withinTransaction { db ->
      val message = try {
        getMessageRecord(messageId = messageId)
      } catch (e: NoSuchMessageException) {
        error("Message $messageId does not exist.")
      }

      val updateDetail = GroupCallUpdateDetailsUtil.parse(message.body)
      val containsSelf = joinedUuids.contains(SignalStore.account.requireAci().rawUuid)
      val sameEraId = updateDetail.eraId == eraId && !Util.isEmpty(eraId)
      val inCallUuids = if (sameEraId) joinedUuids.map { it.toString() } else emptyList()
      val body = GroupCallUpdateDetailsUtil.createUpdatedBody(updateDetail, inCallUuids, isCallFull, isRingingOnLocalDevice)
      val contentValues = contentValuesOf(
        BODY to body
      )

      if (sameEraId && (containsSelf || updateDetail.localUserJoined)) {
        contentValues.put(READ, 1)
      }

      val query = buildTrueUpdateQuery(ID_WHERE, buildArgs(messageId), contentValues)
      val updated = db.update(TABLE_NAME, contentValues, query.where, query.whereArgs) > 0

      if (updated) {
        notifyConversationListeners(message.threadId)
      }
    }

    return MessageId(messageId)
  }

  fun updatePreviousGroupCall(
    threadId: Long,
    peekGroupCallEraId: String?,
    peekJoinedUuids: Collection<UUID>,
    isCallFull: Boolean,
    isRingingOnLocalDevice: Boolean
  ) {
    writableDatabase.withinTransaction { db ->
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
        val containsSelf = peekJoinedUuids.contains(SignalStore.account.requireAci().rawUuid)
        val sameEraId = groupCallUpdateDetails.eraId == peekGroupCallEraId && !Util.isEmpty(peekGroupCallEraId)

        val inCallUuids = if (sameEraId) {
          peekJoinedUuids.map { it.toString() }.toList()
        } else {
          emptyList()
        }

        val contentValues = contentValuesOf(
          BODY to GroupCallUpdateDetailsUtil.createUpdatedBody(groupCallUpdateDetails, inCallUuids, isCallFull, isRingingOnLocalDevice)
        )

        if (sameEraId && (containsSelf || groupCallUpdateDetails.localUserJoined)) {
          contentValues.put(READ, 1)
        }

        val query = buildTrueUpdateQuery(ID_WHERE, buildArgs(record.id), contentValues)
        val updated = db.update(TABLE_NAME, contentValues, query.where, query.whereArgs) > 0

        if (updated) {
          notifyConversationListeners(threadId)
        }
      }
    }
  }

  fun insertEditMessageInbox(mediaMessage: IncomingMessage, targetMessage: MmsMessageRecord): Optional<InsertResult> {
    val insertResult = insertMessageInbox(retrieved = mediaMessage, editedMessage = targetMessage, notifyObservers = false)

    if (insertResult.isPresent) {
      val (messageId) = insertResult.get()

      if (targetMessage.expireStarted > 0) {
        markExpireStarted(messageId, targetMessage.expireStarted)
      }

      writableDatabase.update(TABLE_NAME)
        .values(LATEST_REVISION_ID to messageId)
        .where("$ID = ? OR $LATEST_REVISION_ID = ?", targetMessage.id, targetMessage.id)
        .run()

      reactions.moveReactionsToNewMessage(newMessageId = messageId, previousId = targetMessage.id)

      notifyConversationListeners(targetMessage.threadId)
    }

    return insertResult
  }

  fun insertProfileNameChangeMessages(recipient: Recipient, newProfileName: String, previousProfileName: String) {
    writableDatabase.withinTransaction { db ->
      val groupRecords = groups.getGroupsContainingMember(recipient.id, false)

      val extras = MessageExtras(
        profileChangeDetails = ProfileChangeDetails(profileNameChange = ProfileChangeDetails.StringChange(previous = previousProfileName, newValue = newProfileName))
      )

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
            FROM_RECIPIENT_ID to recipient.id.serialize(),
            FROM_DEVICE_ID to 1,
            TO_RECIPIENT_ID to Recipient.self().id.serialize(),
            DATE_RECEIVED to System.currentTimeMillis(),
            DATE_SENT to System.currentTimeMillis(),
            READ to 1,
            TYPE to MessageTypes.PROFILE_CHANGE_TYPE,
            THREAD_ID to threadId,
            MESSAGE_EXTRAS to extras.encode()
          )
          db.insert(TABLE_NAME, null, values)
          notifyConversationListeners(threadId)
          TrimThreadJob.enqueueAsync(threadId)
        }

      groupRecords.filter { it.isV2Group }.forEach {
        SignalDatabase.nameCollisions.handleGroupNameCollisions(it.id.requireV2(), setOf(recipient.id))
      }
    }
  }

  fun insertLearnedProfileNameChangeMessage(recipient: Recipient, e164: String?, username: String?) {
    if ((e164 == null && username == null) || (e164 != null && username != null)) {
      Log.w(TAG, "Learn profile event expects an e164 or username")
      return
    }

    val threadId: Long? = SignalDatabase.threads.getThreadIdFor(recipient.id)

    if (threadId != null) {
      val extras = MessageExtras(
        profileChangeDetails = ProfileChangeDetails(learnedProfileName = ProfileChangeDetails.LearnedProfileName(e164 = e164, username = username))
      )

      writableDatabase
        .insertInto(TABLE_NAME)
        .values(
          FROM_RECIPIENT_ID to recipient.id.serialize(),
          FROM_DEVICE_ID to 1,
          TO_RECIPIENT_ID to Recipient.self().id.serialize(),
          DATE_RECEIVED to System.currentTimeMillis(),
          DATE_SENT to System.currentTimeMillis(),
          READ to 1,
          TYPE to MessageTypes.PROFILE_CHANGE_TYPE,
          THREAD_ID to threadId,
          MESSAGE_EXTRAS to extras.encode()
        )
        .run()

      notifyConversationListeners(threadId)
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
      FROM_RECIPIENT_ID to recipientId.serialize(),
      FROM_DEVICE_ID to 1,
      TO_RECIPIENT_ID to Recipient.self().id.serialize(),
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
            FROM_RECIPIENT_ID to recipientId.serialize(),
            FROM_DEVICE_ID to 1,
            TO_RECIPIENT_ID to Recipient.self().id.serialize(),
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
        FROM_RECIPIENT_ID to recipientId.serialize(),
        FROM_DEVICE_ID to 1,
        TO_RECIPIENT_ID to Recipient.self().id.serialize(),
        DATE_RECEIVED to System.currentTimeMillis(),
        DATE_SENT to System.currentTimeMillis(),
        READ to 1,
        TYPE to MessageTypes.RELEASE_CHANNEL_DONATION_REQUEST_TYPE,
        THREAD_ID to threadId,
        BODY to null
      )
      .run()
  }

  fun insertThreadMergeEvent(recipientId: RecipientId, threadId: Long, event: ThreadMergeEvent) {
    writableDatabase
      .insertInto(TABLE_NAME)
      .values(
        FROM_RECIPIENT_ID to recipientId.serialize(),
        FROM_DEVICE_ID to 1,
        TO_RECIPIENT_ID to Recipient.self().id.serialize(),
        DATE_RECEIVED to System.currentTimeMillis(),
        DATE_SENT to System.currentTimeMillis(),
        READ to 1,
        TYPE to MessageTypes.THREAD_MERGE_TYPE,
        THREAD_ID to threadId,
        BODY to Base64.encodeWithPadding(event.encode())
      )
      .run()
    AppDependencies.databaseObserver.notifyConversationListeners(threadId)
  }

  fun insertSessionSwitchoverEvent(recipientId: RecipientId, threadId: Long, event: SessionSwitchoverEvent) {
    writableDatabase
      .insertInto(TABLE_NAME)
      .values(
        FROM_RECIPIENT_ID to recipientId.serialize(),
        FROM_DEVICE_ID to 1,
        TO_RECIPIENT_ID to Recipient.self().id.serialize(),
        DATE_RECEIVED to System.currentTimeMillis(),
        DATE_SENT to System.currentTimeMillis(),
        READ to 1,
        TYPE to MessageTypes.SESSION_SWITCHOVER_TYPE,
        THREAD_ID to threadId,
        BODY to Base64.encodeWithPadding(event.encode())
      )
      .run()
    AppDependencies.databaseObserver.notifyConversationListeners(threadId)
  }

  fun insertSmsExportMessage(recipientId: RecipientId, threadId: Long) {
    val updated = writableDatabase.withinTransaction { db ->
      if (messages.hasSmsExportMessage(threadId)) {
        false
      } else {
        db.insertInto(TABLE_NAME)
          .values(
            FROM_RECIPIENT_ID to recipientId.serialize(),
            FROM_DEVICE_ID to 1,
            TO_RECIPIENT_ID to Recipient.self().id.serialize(),
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
      AppDependencies.databaseObserver.notifyConversationListeners(threadId)
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

    var where = "$IS_STORY_CLAUSE AND ($outgoingTypeClause)"
    val whereArgs: Array<String>

    if (threadId == null) {
      where += " AND $FROM_RECIPIENT_ID = ?"
      whereArgs = buildArgs(recipientId)
    } else {
      where += " AND $THREAD_ID = ?"
      whereArgs = buildArgs(threadId)
    }

    return MmsReader(rawQueryWithAttachments(where, whereArgs))
  }

  fun getAllOutgoingStories(reverse: Boolean, limit: Int): Reader {
    val where = "$IS_STORY_CLAUSE AND ($outgoingTypeClause)"
    return MmsReader(rawQueryWithAttachments(where, null, reverse, limit.toLong()))
  }

  fun markAllIncomingStoriesRead(): List<MarkedMessageInfo> {
    val where = "$IS_STORY_CLAUSE AND NOT ($outgoingTypeClause) AND $READ = 0"
    val markedMessageInfos = setMessagesRead(where, null)
    notifyConversationListListeners()
    return markedMessageInfos
  }

  fun markAllCallEventsRead(): List<MarkedMessageInfo> {
    val where = "$IS_CALL_TYPE_CLAUSE AND $READ = 0"
    val markedMessageInfos = setMessagesRead(where, null)
    notifyConversationListListeners()
    return markedMessageInfos
  }

  fun markAllFailedStoriesNotified() {
    val where = "$IS_STORY_CLAUSE AND ($outgoingTypeClause) AND $NOTIFIED = 0 AND ($TYPE & ${MessageTypes.BASE_TYPE_MASK}) = ${MessageTypes.BASE_SENT_FAILED_TYPE}"

    writableDatabase
      .update("$TABLE_NAME INDEXED BY $INDEX_THREAD_STORY_SCHEDULED_DATE_LATEST_REVISION_ID")
      .values(NOTIFIED to 1)
      .where(where)
      .run()
    notifyConversationListListeners()
  }

  fun markOnboardingStoryRead() {
    val recipientId = SignalStore.releaseChannel.releaseChannelRecipientId ?: return
    val where = "$IS_STORY_CLAUSE AND NOT ($outgoingTypeClause) AND $READ = 0 AND $FROM_RECIPIENT_ID = ?"
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
    val query = "$IS_STORY_CLAUSE AND NOT ($outgoingTypeClause) AND $THREAD_ID = ? AND $VIEWED_COLUMN = ?"
    val args = buildArgs(threadId, 0)
    return MmsReader(rawQueryWithAttachments(query, args, false, limit.toLong()))
  }

  fun getUnreadMissedCallCount(): Long {
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
  fun updateViewedStories(targetTimestamps: Set<Long>) {
    val timestamps: String = targetTimestamps
      .joinToString(",")

    writableDatabase.withinTransaction { db ->
      db.select(FROM_RECIPIENT_ID)
        .from(TABLE_NAME)
        .where("$IS_STORY_CLAUSE AND $DATE_SENT IN ($timestamps) AND NOT ($outgoingTypeClause) AND $VIEWED_COLUMN > 0")
        .run()
        .readToList { cursor -> RecipientId.from(cursor.requireLong(FROM_RECIPIENT_ID)) }
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
      .where("$IS_STORY_CLAUSE AND $THREAD_ID = ? AND $VIEWED_COLUMN = ? AND NOT ($outgoingTypeClause)", threadId, 0)
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
      .where("$TO_RECIPIENT_ID = ? AND $STORY_TYPE > 0 AND $DATE_SENT = ? AND ($outgoingTypeClause)", recipientId, sentTimestamp)
      .run()
  }

  @Throws(NoSuchMessageException::class)
  fun getStoryId(authorId: RecipientId, sentTimestamp: Long): MessageId {
    return readableDatabase
      .select(ID)
      .from(TABLE_NAME)
      .where("$IS_STORY_CLAUSE AND $DATE_SENT = ? AND $FROM_RECIPIENT_ID = ?", sentTimestamp, authorId)
      .run()
      .readToSingleObject { cursor ->
        MessageId(CursorUtil.requireLong(cursor, ID))
      } ?: throw NoSuchMessageException("No story sent at $sentTimestamp")
  }

  fun getUnreadStoryThreadRecipientIds(): List<RecipientId> {
    val query = """
      SELECT DISTINCT ${ThreadTable.TABLE_NAME}.${ThreadTable.RECIPIENT_ID}
      FROM $TABLE_NAME 
        JOIN ${ThreadTable.TABLE_NAME} ON $TABLE_NAME.$THREAD_ID = ${ThreadTable.TABLE_NAME}.${ThreadTable.ID}
      WHERE 
        $IS_STORY_CLAUSE AND 
        ($outgoingTypeClause) = 0 AND 
        $VIEWED_COLUMN = 0 AND 
        $TABLE_NAME.$READ = 0
      """

    return readableDatabase
      .rawQuery(query, null)
      .readToList { RecipientId.from(it.getLong(0)) }
  }

  fun hasFailedOutgoingStory(): Boolean {
    val where = "$IS_STORY_CLAUSE AND ($outgoingTypeClause) AND $NOTIFIED = 0 AND ($TYPE & ${MessageTypes.BASE_TYPE_MASK}) = ${MessageTypes.BASE_SENT_FAILED_TYPE}"
    return readableDatabase.exists(TABLE_NAME).where(where).run()
  }

  fun getOrderedStoryRecipientsAndIds(isOutgoingOnly: Boolean): List<StoryResult> {
    val query = """
      SELECT
        $TABLE_NAME.$DATE_SENT AS sent_timestamp,
        $TABLE_NAME.$ID AS mms_id,
        ${ThreadTable.TABLE_NAME}.${ThreadTable.RECIPIENT_ID},
        ($outgoingTypeClause) AS is_outgoing,
        $VIEWED_COLUMN,
        $TABLE_NAME.$DATE_SENT,
        $RECEIPT_TIMESTAMP,
        ($outgoingTypeClause) = 0 AND $VIEWED_COLUMN = 0 AS is_unread
        FROM $TABLE_NAME 
          JOIN ${ThreadTable.TABLE_NAME} ON $TABLE_NAME.$THREAD_ID = ${ThreadTable.TABLE_NAME}.${ThreadTable.ID}
        WHERE
          $STORY_TYPE > 0 AND 
          $REMOTE_DELETED = 0
          ${if (isOutgoingOnly) " AND is_outgoing != 0" else ""}
        ORDER BY
          is_unread DESC,
          CASE
            WHEN is_outgoing = 0 AND $VIEWED_COLUMN = 0 THEN $TABLE_NAME.$DATE_SENT
            WHEN is_outgoing = 0 AND $VIEWED_COLUMN > 0 THEN $RECEIPT_TIMESTAMP
            WHEN is_outgoing = 1 THEN $TABLE_NAME.$DATE_SENT
          END DESC
      """

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
      .where("$PARENT_STORY_ID = ? AND ($outgoingTypeClause)", -parentStoryId)
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
        """

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
        """

      db.execSQL(deleteStoryRepliesQuery, sharedArgs)
      db.execSQL(disassociateQuoteQuery, sharedArgs)

      db.select(FROM_RECIPIENT_ID)
        .from(TABLE_NAME)
        .where(storiesBeforeTimestampWhere, sharedArgs)
        .run()
        .readToList { RecipientId.from(it.requireLong(FROM_RECIPIENT_ID)) }
        .forEach { id -> AppDependencies.databaseObserver.notifyStoryObservers(id) }

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

  /**
   * Delete all the stories received from the recipient in 1:1 stories
   */
  fun deleteStoriesForRecipient(recipientId: RecipientId): Int {
    return writableDatabase.withinTransaction { db ->
      val threadId = threads.getThreadIdFor(recipientId) ?: return@withinTransaction 0
      val storesInRecipientThread = "$IS_STORY_CLAUSE AND $THREAD_ID = ?"
      val sharedArgs = buildArgs(threadId)

      val deleteStoryRepliesQuery = """
        DELETE FROM $TABLE_NAME 
        WHERE 
          $PARENT_STORY_ID > 0 AND 
          $PARENT_STORY_ID IN (
            SELECT $ID 
            FROM $TABLE_NAME 
            WHERE $storesInRecipientThread
          )
        """

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
            WHERE $storesInRecipientThread
          )
        """

      db.execSQL(deleteStoryRepliesQuery, sharedArgs)
      db.execSQL(disassociateQuoteQuery, sharedArgs)

      AppDependencies.databaseObserver.notifyStoryObservers(recipientId)

      val deletedStoryCount = db.select(ID)
        .from(TABLE_NAME)
        .where(storesInRecipientThread, sharedArgs)
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

  fun getLatestReceivedAt(threadId: Long, messages: List<SyncMessageId>): Long? {
    if (messages.isEmpty()) {
      return null
    }

    val args: List<Array<String>> = messages.map { arrayOf(it.timetamp.toString(), it.recipientId.serialize(), threadId.toString()) }
    val queries = SqlUtil.buildCustomCollectionQuery("$DATE_SENT = ? AND $FROM_RECIPIENT_ID = ? AND $THREAD_ID = ?", args)

    var overallLatestReceivedAt: Long? = null
    for (query in queries) {
      val latestReceivedAt: Long? = readableDatabase
        .select("MAX($DATE_RECEIVED)")
        .from(TABLE_NAME)
        .where(query.where, query.whereArgs)
        .run()
        .readToSingleLongOrNull()

      if (overallLatestReceivedAt == null) {
        overallLatestReceivedAt = latestReceivedAt
      } else if (latestReceivedAt != null) {
        overallLatestReceivedAt = max(overallLatestReceivedAt, latestReceivedAt)
      }
    }

    return overallLatestReceivedAt
  }

  fun getScheduledMessageCountForThread(threadId: Long): Int {
    return readableDatabase
      .select("COUNT(*)")
      .from("$TABLE_NAME INDEXED BY $INDEX_THREAD_STORY_SCHEDULED_DATE_LATEST_REVISION_ID")
      .where("$THREAD_ID = ? AND $STORY_TYPE = ? AND $PARENT_STORY_ID <= ? AND $SCHEDULED_DATE != ?", threadId, 0, 0, -1)
      .run()
      .readToSingleInt()
  }

  fun getMessageCountForThread(threadId: Long): Int {
    return readableDatabase
      .select("COUNT(*)")
      .from("$TABLE_NAME INDEXED BY $INDEX_THREAD_COUNT")
      .where("$THREAD_ID = $threadId AND $STORY_TYPE = 0 AND $PARENT_STORY_ID <= 0 AND $SCHEDULED_DATE = -1 AND $LATEST_REVISION_ID IS NULL")
      .run()
      .readToSingleInt()
  }

  /**
   * Given a set of thread ids, return the count of all messages in the table that match that thread id. This will include *all* messages, and is
   * explicitly for use as a "fuzzy total"
   */
  fun getApproximateExportableMessageCount(threadIds: Set<Long>): Long {
    val queries = SqlUtil.buildCollectionQuery(THREAD_ID, threadIds)
    return queries.sumOf {
      readableDatabase.count()
        .from(TABLE_NAME)
        .where(it.where, it.whereArgs)
        .run()
        .readToSingleLong(0L)
    }
  }

  fun canSetUniversalTimer(threadId: Long): Boolean {
    if (threadId == -1L) {
      return true
    }

    val meaningfulQuery = buildMeaningfulMessagesQuery(threadId)
    val isNotJoinedType = SqlUtil.buildQuery("$TYPE & ${MessageTypes.BASE_TYPE_MASK} != ${MessageTypes.JOINED_TYPE}")

    val query = meaningfulQuery and isNotJoinedType
    val hasMeaningfulMessages = readableDatabase
      .exists("$TABLE_NAME INDEXED BY $INDEX_THREAD_STORY_SCHEDULED_DATE_LATEST_REVISION_ID")
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
      .exists("$TABLE_NAME INDEXED BY $INDEX_THREAD_STORY_SCHEDULED_DATE_LATEST_REVISION_ID")
      .where(query.where, query.whereArgs)
      .run()
  }

  /**
   * Returns the receipt status of the most recent meaningful message in the thread if it matches the provided message ID.
   * If the ID doesn't match or otherwise can't be found, it will return null.
   *
   * This is a very specific method for use with [ThreadTable.updateReceiptStatus] to improve the perfomance of
   * processing receipts.
   */
  fun getReceiptStatusIfItsTheMostRecentMeaningfulMessage(messageId: Long, threadId: Long): MessageReceiptStatus? {
    val query = buildMeaningfulMessagesQuery(threadId)

    return readableDatabase
      .select(ID, HAS_DELIVERY_RECEIPT, HAS_READ_RECEIPT, TYPE)
      .from("$TABLE_NAME INDEXED BY $INDEX_THREAD_STORY_SCHEDULED_DATE_LATEST_REVISION_ID")
      .where(query.where, query.whereArgs)
      .orderBy("$DATE_RECEIVED DESC")
      .limit(1)
      .run()
      .use { cursor ->
        if (cursor.moveToFirst()) {
          if (cursor.requireLong(ID) != messageId) {
            return null
          }

          return MessageReceiptStatus(
            hasDeliveryReceipt = cursor.requireBoolean(HAS_DELIVERY_RECEIPT),
            hasReadReceipt = cursor.requireBoolean(HAS_READ_RECEIPT),
            type = cursor.requireLong(TYPE)
          )
        } else {
          null
        }
      }
  }

  private fun buildMeaningfulMessagesQuery(threadId: Long): SqlUtil.Query {
    val query = """
      $THREAD_ID = $threadId AND
      $STORY_TYPE = 0 AND
      $LATEST_REVISION_ID IS NULL AND
      $PARENT_STORY_ID <= 0 AND
      (
        NOT $TYPE & ${MessageTypes.IGNORABLE_TYPESMASK_WHEN_COUNTING} AND
        $TYPE != ${MessageTypes.PROFILE_CHANGE_TYPE} AND
        $TYPE != ${MessageTypes.CHANGE_NUMBER_TYPE} AND
        $TYPE != ${MessageTypes.SMS_EXPORT_TYPE} AND
        $TYPE != ${MessageTypes.RELEASE_CHANNEL_DONATION_REQUEST_TYPE} AND
        $TYPE & ${MessageTypes.GROUP_V2_LEAVE_BITS} != ${MessageTypes.GROUP_V2_LEAVE_BITS} AND
        $TYPE & ${MessageTypes.SPECIAL_TYPES_MASK} != ${MessageTypes.SPECIAL_TYPE_REPORTED_SPAM} AND
        $TYPE & ${MessageTypes.SPECIAL_TYPES_MASK} != ${MessageTypes.SPECIAL_TYPE_MESSAGE_REQUEST_ACCEPTED} AND
        $TYPE & ${MessageTypes.SPECIAL_TYPES_MASK} != ${MessageTypes.SPECIAL_TYPE_BLOCKED} AND
        $TYPE & ${MessageTypes.SPECIAL_TYPES_MASK} != ${MessageTypes.SPECIAL_TYPE_UNBLOCKED}
      )
    """

    return SqlUtil.buildQuery(query)
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

  private fun getThreadIdFor(retrieved: IncomingMessage): ThreadTable.ThreadIdResult {
    return if (retrieved.groupId != null) {
      val groupRecipientId = recipients.getOrInsertFromPossiblyMigratedGroupId(retrieved.groupId)
      val groupRecipients = Recipient.resolved(groupRecipientId)
      threads.getOrCreateThreadIdResultFor(groupRecipients.id, isGroup = true)
    } else {
      val sender = Recipient.resolved(retrieved.from)
      threads.getOrCreateThreadIdResultFor(sender.id, isGroup = false)
    }
  }

  private fun rawQueryWithAttachments(where: String, arguments: Array<String>?, reverse: Boolean = false, limit: Long = 0): Cursor {
    val database = databaseHelper.signalReadableDatabase
    var rawQueryString = """
      SELECT 
        ${Util.join(MMS_PROJECTION_WITH_ATTACHMENTS, ",")}
      FROM 
        $TABLE_NAME LEFT OUTER JOIN ${AttachmentTable.TABLE_NAME} ON ($TABLE_NAME.$ID = ${AttachmentTable.TABLE_NAME}.${AttachmentTable.MESSAGE_ID}) 
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

  private fun getOriginalEditedMessageRecord(messageId: Long): Long {
    return readableDatabase.select(ID)
      .from(TABLE_NAME)
      .where("$TABLE_NAME.$LATEST_REVISION_ID = ?", messageId)
      .orderBy("$ID DESC")
      .limit(1)
      .run()
      .readToSingleLong(0)
  }

  fun getMessages(messageIds: Collection<Long?>): MmsReader {
    val ids = TextUtils.join(",", messageIds)
    return mmsReaderFor(rawQueryWithAttachments("$TABLE_NAME.$ID IN ($ids)", null))
  }

  fun getMessageEditHistory(id: Long): MmsReader {
    val cursor = readableDatabase.select(*MMS_PROJECTION)
      .from(TABLE_NAME)
      .where("$TABLE_NAME.$ID = ? OR $TABLE_NAME.$ORIGINAL_MESSAGE_ID = ?", id, id)
      .orderBy("$TABLE_NAME.$DATE_SENT ASC")
      .run()

    return mmsReaderFor(cursor)
  }

  private fun getPreviousEditIds(id: Long): List<Long> {
    return readableDatabase
      .select(ID)
      .from(TABLE_NAME)
      .where("$LATEST_REVISION_ID = ?", id)
      .orderBy("$DATE_SENT ASC")
      .run()
      .readToList {
        it.requireLong(ID)
      }
  }

  private fun updateMailboxBitmask(id: Long, maskOff: Long, maskOn: Long, threadId: Optional<Long>) {
    writableDatabase.withinTransaction { db ->
      db.execSQL(
        """
          UPDATE $TABLE_NAME 
          SET $TYPE = ($TYPE & ${MessageTypes.TOTAL_MASK - maskOff} | $maskOn ) 
          WHERE $ID = ?
        """,
        buildArgs(id)
      )

      if (threadId.isPresent) {
        threads.updateSnippetTypeSilently(threadId.get())
      }
    }
  }

  fun markAsRateLimited(messageId: Long) {
    val threadId = getThreadIdForMessage(messageId)
    updateMailboxBitmask(messageId, 0, MessageTypes.MESSAGE_RATE_LIMITED_BIT, Optional.of(threadId))
    AppDependencies.databaseObserver.notifyMessageUpdateObservers(MessageId(messageId))
  }

  fun clearRateLimitStatus(ids: Collection<Long>) {
    writableDatabase.withinTransaction {
      for (id in ids) {
        val threadId = getThreadIdForMessage(id)
        updateMailboxBitmask(id, MessageTypes.MESSAGE_RATE_LIMITED_BIT, 0, Optional.of(threadId))
      }
    }
  }

  fun markAsSending(messageId: Long) {
    val threadId = getThreadIdForMessage(messageId)
    updateMailboxBitmask(messageId, MessageTypes.BASE_TYPE_MASK, MessageTypes.BASE_SENDING_TYPE, Optional.of(threadId))
    AppDependencies.databaseObserver.notifyMessageUpdateObservers(MessageId(messageId))
    AppDependencies.databaseObserver.notifyConversationListListeners()
  }

  fun markAsSentFailed(messageId: Long) {
    val threadId = getThreadIdForMessage(messageId)
    updateMailboxBitmask(messageId, MessageTypes.BASE_TYPE_MASK, MessageTypes.BASE_SENT_FAILED_TYPE, Optional.of(threadId))
    AppDependencies.databaseObserver.notifyMessageUpdateObservers(MessageId(messageId))
    AppDependencies.databaseObserver.notifyConversationListListeners()
  }

  fun markAsSent(messageId: Long, secure: Boolean) {
    val threadId = getThreadIdForMessage(messageId)
    updateMailboxBitmask(messageId, MessageTypes.BASE_TYPE_MASK, MessageTypes.BASE_SENT_TYPE or if (secure) MessageTypes.PUSH_MESSAGE_BIT or MessageTypes.SECURE_MESSAGE_BIT else 0, Optional.of(threadId))
    AppDependencies.databaseObserver.notifyMessageUpdateObservers(MessageId(messageId))
    AppDependencies.databaseObserver.notifyConversationListListeners()
  }

  fun markAsRemoteDelete(targetMessage: MessageRecord) {
    writableDatabase.withinTransaction { db ->
      if (targetMessage.isEditMessage) {
        val latestRevisionId = (targetMessage as? MmsMessageRecord)?.latestRevisionId?.id ?: targetMessage.id
        markAsRemoteDeleteInternal(latestRevisionId)
        getPreviousEditIds(latestRevisionId).map { id ->
          db.update(TABLE_NAME)
            .values(
              ORIGINAL_MESSAGE_ID to null,
              LATEST_REVISION_ID to null
            )
            .where("$ID = ?", id)
            .run()
          deleteMessage(id)
        }
      } else {
        markAsRemoteDeleteInternal(targetMessage.id)
      }
    }
  }

  fun markAsRemoteDelete(messageId: Long) {
    val targetMessage: MessageRecord = getMessageRecord(messageId)
    markAsRemoteDelete(targetMessage)
  }

  private fun markAsRemoteDeleteInternal(messageId: Long) {
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
          SHARED_CONTACTS to null,
          ORIGINAL_MESSAGE_ID to null,
          LATEST_REVISION_ID to null
        )
        .where("$ID = ?", messageId)
        .run()

      deletedAttachments = attachments.deleteAttachmentsForMessage(messageId)
      mentions.deleteMentionsForMessage(messageId)
      SignalDatabase.messageLog.deleteAllRelatedToMessage(messageId)
      reactions.deleteReactions(MessageId(messageId))
      deleteGroupStoryReplies(messageId)
      disassociateStoryQuotes(messageId)

      val threadId = getThreadIdForMessage(messageId)
      threads.update(threadId, false)
    }

    OptimizeMessageSearchIndexJob.enqueue()
    AppDependencies.databaseObserver.notifyMessageUpdateObservers(MessageId(messageId))
    AppDependencies.databaseObserver.notifyConversationListListeners()

    if (deletedAttachments) {
      AppDependencies.databaseObserver.notifyAttachmentDeletedObservers()
    }
  }

  fun markDownloadState(messageId: Long, state: Long) {
    writableDatabase
      .update(TABLE_NAME)
      .values(MMS_STATUS to state)
      .where("$ID = ?", messageId)
      .run()

    AppDependencies.databaseObserver.notifyMessageUpdateObservers(MessageId(messageId))
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

    AppDependencies.databaseObserver.notifyMessageInsertObservers(threadId, MessageId(messageId))
    AppDependencies.databaseObserver.notifyScheduledMessageObservers(threadId)

    return rowsUpdated > 0
  }

  fun rescheduleMessage(threadId: Long, messageId: Long, time: Long) {
    val rowsUpdated = writableDatabase
      .update(TABLE_NAME)
      .values(SCHEDULED_DATE to time)
      .where("$ID = ? AND $SCHEDULED_DATE != ?", messageId, -1)
      .run()

    AppDependencies.databaseObserver.notifyScheduledMessageObservers(threadId)
    AppDependencies.scheduledMessageManager.scheduleIfNecessary()

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
    markExpireStarted(setOf(id to startedTimestamp))
  }

  fun markExpireStarted(ids: Collection<kotlin.Pair<Long, Long>>) {
    writableDatabase.withinTransaction { db ->
      for ((id, startedAtTimestamp) in ids) {
        db.update(TABLE_NAME)
          .values(EXPIRE_STARTED to startedAtTimestamp)
          .where("$ID = ? AND ($EXPIRE_STARTED = 0 OR $EXPIRE_STARTED > ?)", id, startedAtTimestamp)
          .run()
        AppDependencies.databaseObserver.notifyMessageUpdateObservers(MessageId(id))
      }
    }
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

  fun setAllEditMessageRevisionsRead(messageId: Long): List<MarkedMessageInfo> {
    var query = """
      (
        $ORIGINAL_MESSAGE_ID = ? OR
        $ID = ?
      ) AND 
      (
        $READ = 0 OR 
        (
          $REACTIONS_UNREAD = 1 AND 
          ($outgoingTypeClause)
        )
      )
      """

    val args = mutableListOf(messageId.toString(), messageId.toString())

    return setMessagesRead(query, args.toTypedArray())
  }

  fun setMessagesReadSince(threadId: Long, sinceTimestamp: Long): List<MarkedMessageInfo> {
    var query = """
      $THREAD_ID = ? AND 
      $STORY_TYPE = 0 AND 
      $PARENT_STORY_ID <= 0 AND 
      $LATEST_REVISION_ID IS NULL AND
      (
        $READ = 0 OR 
        (
          $REACTIONS_UNREAD = 1 AND 
          ($outgoingTypeClause)
        )
      )
      """

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
          ($outgoingTypeClause)
        )
      )
      """

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
    return setMessagesRead("$STORY_TYPE = 0 AND $PARENT_STORY_ID <= 0 AND ($READ = 0 OR ($REACTIONS_UNREAD = 1 AND ($outgoingTypeClause)))", null)
  }

  private fun setMessagesRead(where: String, arguments: Array<String>?): List<MarkedMessageInfo> {
    val releaseChannelId = SignalStore.releaseChannel.releaseChannelRecipientId
    return writableDatabase.rawQuery(
      """
          UPDATE $TABLE_NAME INDEXED BY $INDEX_THREAD_STORY_SCHEDULED_DATE_LATEST_REVISION_ID
          SET $READ = 1, $REACTIONS_UNREAD = 0, $REACTIONS_LAST_SEEN = ${System.currentTimeMillis()}
          WHERE $where
          RETURNING $ID, $FROM_RECIPIENT_ID, $DATE_SENT, $DATE_RECEIVED, $TYPE, $EXPIRES_IN, $EXPIRE_STARTED, $THREAD_ID, $STORY_TYPE
        """,
      arguments ?: emptyArray()
    ).readToList { cursor ->
      val threadId = cursor.requireLong(THREAD_ID)
      val recipientId = RecipientId.from(cursor.requireLong(FROM_RECIPIENT_ID))
      val dateSent = cursor.requireLong(DATE_SENT)
      val dateReceived = cursor.requireLong(DATE_RECEIVED)
      val messageId = cursor.requireLong(ID)
      val expiresIn = cursor.requireLong(EXPIRES_IN)
      val expireStarted = cursor.requireLong(EXPIRE_STARTED)
      val syncMessageId = SyncMessageId(recipientId, dateSent)
      val expirationInfo = ExpirationInfo(messageId, expiresIn, expireStarted, true)
      val storyType = fromCode(CursorUtil.requireInt(cursor, STORY_TYPE))

      if (recipientId != releaseChannelId) {
        MarkedMessageInfo(threadId, syncMessageId, MessageId(messageId), expirationInfo, storyType, dateReceived)
      } else {
        null
      }
    }
      .filterNotNull()
  }

  fun getOldestUnreadMentionDetails(threadId: Long): Pair<RecipientId, Long>? {
    return readableDatabase
      .select(FROM_RECIPIENT_ID, DATE_RECEIVED)
      .from("$TABLE_NAME INDEXED BY $INDEX_THREAD_UNREAD_COUNT")
      .where("$THREAD_ID = ? AND $STORY_TYPE = 0 AND $PARENT_STORY_ID <= 0 AND $LATEST_REVISION_ID IS NULL AND $SCHEDULED_DATE = -1 AND $READ = 0 AND $MENTIONS_SELF = 1", threadId)
      .orderBy("$DATE_RECEIVED ASC")
      .limit(1)
      .run()
      .readToSingleObject { cursor ->
        Pair(
          RecipientId.from(cursor.requireLong(FROM_RECIPIENT_ID)),
          cursor.requireLong(DATE_RECEIVED)
        )
      }
  }

  fun getUnreadMentionCount(threadId: Long): Int {
    return readableDatabase
      .count()
      .from("$TABLE_NAME INDEXED BY $INDEX_THREAD_UNREAD_COUNT")
      .where("$THREAD_ID = ? AND $STORY_TYPE = 0 AND $PARENT_STORY_ID <= 0 AND $LATEST_REVISION_ID IS NULL AND $SCHEDULED_DATE = -1 AND $READ = 0 AND $MENTIONS_SELF = 1", threadId)
      .run()
      .readToSingleInt()
  }

  /**
   * Trims data related to expired messages. Only intended to be run after a backup restore.
   */
  fun trimEntriesForExpiredMessages() {
    val messageDeleteCount = writableDatabase
      .delete(TABLE_NAME)
      .where("$EXPIRE_STARTED > 0 AND $EXPIRES_IN > 0 AND ($EXPIRE_STARTED + $EXPIRES_IN) < ${System.currentTimeMillis()}")
      .run()

    Log.d(TAG, "Deleted $messageDeleteCount expired messages after backup.")

    writableDatabase
      .delete(GroupReceiptTable.TABLE_NAME)
      .where("${GroupReceiptTable.MMS_ID} NOT IN (SELECT $ID FROM $TABLE_NAME)")
      .run()

    readableDatabase
      .select(AttachmentTable.ID)
      .from(AttachmentTable.TABLE_NAME)
      .where("${AttachmentTable.MESSAGE_ID} NOT IN (SELECT $ID FROM $TABLE_NAME)")
      .run()
      .forEach { cursor ->
        attachments.deleteAttachment(AttachmentId(cursor.requireLong(AttachmentTable.ID)))
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
      val expireTimerVersion = cursor.requireInt(EXPIRE_TIMER_VERSION)
      val viewOnce = cursor.requireLong(VIEW_ONCE) == 1L
      val threadId = cursor.requireLong(THREAD_ID)
      val threadRecipient = Recipient.resolved(threads.getRecipientIdForThreadId(threadId)!!)
      val distributionType = threads.getDistributionType(threadId)
      val storyType = StoryType.fromCode(cursor.requireInt(STORY_TYPE))
      val parentStoryId = ParentStoryId.deserialize(cursor.requireLong(PARENT_STORY_ID))
      val messageRangesData = cursor.requireBlob(MESSAGE_RANGES)
      val scheduledDate = cursor.requireLong(SCHEDULED_DATE)
      val messageExtrasBytes = cursor.requireBlob(MESSAGE_EXTRAS)
      val messageExtras = if (messageExtrasBytes != null) MessageExtras.ADAPTER.decode(messageExtrasBytes) else null

      val quoteId = cursor.requireLong(QUOTE_ID)
      val quoteAuthor = cursor.requireLong(QUOTE_AUTHOR)
      val quoteText = cursor.requireString(QUOTE_BODY)
      val quoteType = cursor.requireInt(QUOTE_TYPE)
      val quoteMissing = cursor.requireBoolean(QUOTE_MISSING)
      val quoteAttachments: List<Attachment> = associatedAttachments.filter { it.quote }.toList()
      val quoteMentions: List<Mention> = parseQuoteMentions(cursor)
      val quoteBodyRanges: BodyRangeList? = parseQuoteBodyRanges(cursor)
      val quote: QuoteModel? = if (quoteId != QUOTE_NOT_PRESENT_ID && quoteAuthor > 0 && (!TextUtils.isEmpty(quoteText) || quoteAttachments.isNotEmpty())) {
        QuoteModel(quoteId, RecipientId.from(quoteAuthor), quoteText ?: "", quoteMissing, quoteAttachments, quoteMentions, QuoteModel.Type.fromCode(quoteType), quoteBodyRanges)
      } else {
        null
      }

      val contacts: List<Contact> = getSharedContacts(cursor, associatedAttachments)
      val contactAttachments: Set<Attachment> = contacts.mapNotNull { it.avatarAttachment }.toSet()
      val previews: List<LinkPreview> = getLinkPreviews(cursor, associatedAttachments)
      val previewAttachments: Set<Attachment> = previews.filter { it.thumbnail.isPresent }.map { it.thumbnail.get() }.toSet()
      val attachments: List<Attachment> = associatedAttachments
        .filterNot { it.quote }
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
          threadRecipient = threadRecipient,
          groupContext = if (messageExtras != null) MessageGroupContext(messageExtras, MessageTypes.isGroupV2(outboxType)) else MessageGroupContext(body, MessageTypes.isGroupV2(outboxType)),
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
          threadRecipient = threadRecipient,
          sentTimeMillis = timestamp,
          expiresIn = expiresIn,
          expireTimerVersion = expireTimerVersion
        )
      } else if (MessageTypes.isPaymentsNotification(outboxType)) {
        OutgoingMessage.paymentNotificationMessage(
          threadRecipient = threadRecipient,
          paymentUuid = body!!,
          sentTimeMillis = timestamp,
          expiresIn = expiresIn
        )
      } else if (MessageTypes.isPaymentsRequestToActivate(outboxType)) {
        OutgoingMessage.requestToActivatePaymentsMessage(
          threadRecipient = threadRecipient,
          sentTimeMillis = timestamp,
          expiresIn = expiresIn
        )
      } else if (MessageTypes.isPaymentsActivated(outboxType)) {
        OutgoingMessage.paymentsActivatedMessage(
          threadRecipient = threadRecipient,
          sentTimeMillis = timestamp,
          expiresIn = expiresIn
        )
      } else if (MessageTypes.isReportedSpam(outboxType)) {
        OutgoingMessage.reportSpamMessage(
          threadRecipient = threadRecipient,
          sentTimeMillis = timestamp,
          expiresIn = expiresIn
        )
      } else if (MessageTypes.isMessageRequestAccepted(outboxType)) {
        OutgoingMessage.messageRequestAcceptMessage(
          threadRecipient = threadRecipient,
          sentTimeMillis = timestamp,
          expiresIn = expiresIn
        )
      } else if (MessageTypes.isBlocked(outboxType)) {
        OutgoingMessage.blockedMessage(
          threadRecipient = threadRecipient,
          sentTimeMillis = timestamp,
          expiresIn = expiresIn
        )
      } else if (MessageTypes.isUnblocked(outboxType)) {
        OutgoingMessage.unblockedMessage(
          threadRecipient = threadRecipient,
          sentTimeMillis = timestamp,
          expiresIn = expiresIn
        )
      } else {
        val giftBadge: GiftBadge? = if (body != null && MessageTypes.isGiftBadge(outboxType)) {
          GiftBadge.ADAPTER.decode(Base64.decode(body))
        } else {
          null
        }

        val messageRanges: BodyRangeList? = if (messageRangesData != null) {
          try {
            BodyRangeList.ADAPTER.decode(messageRangesData)
          } catch (e: IOException) {
            Log.w(TAG, "Error parsing message ranges", e)
            null
          }
        } else {
          null
        }

        val editedMessage = getOriginalEditedMessageRecord(messageId)

        OutgoingMessage(
          recipient = threadRecipient,
          body = body,
          attachments = attachments,
          timestamp = timestamp,
          expiresIn = expiresIn,
          expireTimerVersion = expireTimerVersion,
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
          scheduledDate = scheduledDate,
          messageToEdit = editedMessage
        )
      }
    } ?: throw NoSuchMessageException("No record found for id: $messageId")
  }

  @JvmOverloads
  @Throws(MmsException::class)
  fun insertMessageInbox(
    retrieved: IncomingMessage,
    candidateThreadId: Long = -1,
    editedMessage: MmsMessageRecord? = null,
    notifyObservers: Boolean = true
  ): Optional<InsertResult> {
    val type = retrieved.toMessageType()

    val threadIdResult = if (candidateThreadId == -1L || retrieved.isGroupMessage) {
      getThreadIdFor(retrieved)
    } else {
      ThreadTable.ThreadIdResult(threadId = candidateThreadId, newlyCreated = false)
    }
    val threadId = threadIdResult.threadId

    if (retrieved.type == MessageType.GROUP_UPDATE && retrieved.groupContext?.let { GroupV2UpdateMessageUtil.isJoinRequestCancel(it) } == true) {
      val result = collapseJoinRequestEventsIfPossible(threadId, retrieved)
      if (result.isPresent) {
        Log.d(TAG, "[insertMessageInbox] Collapsed join request events.")
        return result
      }
    }

    val silent = (MessageTypes.isGroupUpdate(type) && !retrieved.isGroupAdd) ||
      retrieved.type == MessageType.IDENTITY_DEFAULT ||
      retrieved.type == MessageType.IDENTITY_VERIFIED ||
      retrieved.type == MessageType.IDENTITY_UPDATE

    val read = silent || retrieved.type == MessageType.EXPIRATION_UPDATE

    val contentValues = contentValuesOf(
      DATE_SENT to retrieved.sentTimeMillis,
      DATE_SERVER to retrieved.serverTimeMillis,
      FROM_RECIPIENT_ID to retrieved.from.serialize(),
      TO_RECIPIENT_ID to Recipient.self().id.serialize(),
      TYPE to type,
      THREAD_ID to threadId,
      MMS_STATUS to MmsStatus.DOWNLOAD_INITIALIZED,
      DATE_RECEIVED to retrieved.receivedTimeMillis,
      SMS_SUBSCRIPTION_ID to retrieved.subscriptionId,
      EXPIRES_IN to retrieved.expiresIn,
      VIEW_ONCE to if (retrieved.isViewOnce) 1 else 0,
      STORY_TYPE to retrieved.storyType.code,
      PARENT_STORY_ID to if (retrieved.parentStoryId != null) retrieved.parentStoryId.serialize() else 0,
      READ to read.toInt(),
      UNIDENTIFIED to retrieved.isUnidentified,
      SERVER_GUID to retrieved.serverGuid,
      LATEST_REVISION_ID to null,
      ORIGINAL_MESSAGE_ID to editedMessage?.getOriginalOrOwnMessageId()?.id,
      REVISION_NUMBER to (editedMessage?.revisionNumber?.inc() ?: 0),
      MESSAGE_EXTRAS to (retrieved.messageExtras?.encode())
    )

    val quoteAttachments: MutableList<Attachment> = mutableListOf()
    if (retrieved.quote != null) {
      contentValues.put(QUOTE_ID, retrieved.quote.id)
      contentValues.put(QUOTE_BODY, retrieved.quote.text)
      contentValues.put(QUOTE_AUTHOR, retrieved.quote.author.serialize())
      contentValues.put(QUOTE_TYPE, retrieved.quote.type.code)
      contentValues.put(QUOTE_MISSING, if (retrieved.quote.isOriginalMissing) 1 else 0)

      val quoteBodyRanges: BodyRangeList.Builder = retrieved.quote.bodyRanges?.newBuilder() ?: BodyRangeList.Builder()
      val mentionsList = MentionUtil.mentionsToBodyRangeList(retrieved.quote.mentions)

      if (mentionsList != null) {
        quoteBodyRanges.ranges += mentionsList.ranges
      }

      if (quoteBodyRanges.ranges.isNotEmpty()) {
        contentValues.put(QUOTE_BODY_RANGES, quoteBodyRanges.build().encode())
      }

      quoteAttachments += retrieved.quote.attachments
    }

    val (messageId, insertedAttachments) = insertMediaMessage(
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
      updateThread = retrieved.storyType === StoryType.NONE && !silent,
      unarchive = true
    )

    if (messageId < 0) {
      Log.w(TAG, "Failed to insert media message (${retrieved.sentTimeMillis}, ${retrieved.from}, ThreadId::$threadId})! Likely a duplicate.")
      return Optional.empty()
    }

    if (editedMessage != null) {
      if (retrieved.quote != null && editedMessage.quote != null) {
        writableDatabase.execSQL(
          """  
          WITH o as (SELECT $QUOTE_ID, $QUOTE_AUTHOR, $QUOTE_BODY, $QUOTE_TYPE, $QUOTE_MISSING, $QUOTE_BODY_RANGES FROM $TABLE_NAME WHERE $ID = ${editedMessage.id})
          UPDATE $TABLE_NAME
          SET $QUOTE_ID = old.$QUOTE_ID, $QUOTE_AUTHOR = old.$QUOTE_AUTHOR, $QUOTE_BODY = old.$QUOTE_BODY, $QUOTE_TYPE = old.$QUOTE_TYPE, $QUOTE_MISSING = old.$QUOTE_MISSING, $QUOTE_BODY_RANGES = old.$QUOTE_BODY_RANGES
          FROM o old
          WHERE $TABLE_NAME.$ID = $messageId
          """
        )
      }
    }

    if (retrieved.attachments.isEmpty() && editedMessage?.id != null && attachments.getAttachmentsForMessage(editedMessage.id).isNotEmpty()) {
      val linkPreviewAttachmentIds = editedMessage.linkPreviews.mapNotNull { it.attachmentId?.id }.toSet()
      attachments.duplicateAttachmentsForMessage(messageId, editedMessage.id, linkPreviewAttachmentIds)
    }

    val isNotStoryGroupReply = retrieved.parentStoryId == null || !retrieved.parentStoryId.isGroupReply()

    if (!MessageTypes.isPaymentsActivated(type) &&
      !MessageTypes.isPaymentsRequestToActivate(type) &&
      !MessageTypes.isReportedSpam(type) &&
      !MessageTypes.isMessageRequestAccepted(type) &&
      !MessageTypes.isExpirationTimerUpdate(type) &&
      !MessageTypes.isBlocked(type) &&
      !MessageTypes.isUnblocked(type) &&
      !retrieved.storyType.isStory &&
      isNotStoryGroupReply &&
      !silent
    ) {
      val incrementUnreadMentions = retrieved.mentions.isNotEmpty() && retrieved.mentions.any { it.recipientId == Recipient.self().id }
      threads.incrementUnread(threadId, 1, if (incrementUnreadMentions) 1 else 0)
      ThreadUpdateJob.enqueue(threadId)
    }

    if (notifyObservers) {
      notifyConversationListeners(threadId)
    }

    if (retrieved.storyType.isStory) {
      AppDependencies.databaseObserver.notifyStoryObservers(threads.getRecipientIdForThreadId(threadId)!!)
    }

    return Optional.of(
      InsertResult(
        messageId = messageId,
        threadId = threadId,
        threadWasNewlyCreated = threadIdResult.newlyCreated,
        insertedAttachments = insertedAttachments
      )
    )
  }

  fun insertChatSessionRefreshedMessage(recipientId: RecipientId, senderDeviceId: Long, sentTimestamp: Long): InsertResult {
    val recipient = Recipient.resolved(recipientId)
    val threadIdResult = threads.getOrCreateThreadIdResultFor(recipient.id, recipient.isGroup)
    val threadId = threadIdResult.threadId
    var type = MessageTypes.SECURE_MESSAGE_BIT or MessageTypes.PUSH_MESSAGE_BIT
    type = type and MessageTypes.TOTAL_MASK - MessageTypes.ENCRYPTION_MASK or MessageTypes.ENCRYPTION_REMOTE_FAILED_BIT

    val messageId = writableDatabase
      .insertInto(TABLE_NAME)
      .values(
        FROM_RECIPIENT_ID to recipientId.serialize(),
        FROM_DEVICE_ID to senderDeviceId,
        TO_RECIPIENT_ID to Recipient.self().id.serialize(),
        TO_RECIPIENT_ID to Recipient.self().id.serialize(),
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

    return InsertResult(
      messageId = messageId,
      threadId = threadId,
      threadWasNewlyCreated = threadIdResult.newlyCreated
    )
  }

  fun insertBadDecryptMessage(recipientId: RecipientId, senderDevice: Int, sentTimestamp: Long, receivedTimestamp: Long, threadId: Long) {
    writableDatabase
      .insertInto(TABLE_NAME)
      .values(
        FROM_RECIPIENT_ID to recipientId.serialize(),
        FROM_DEVICE_ID to senderDevice,
        TO_RECIPIENT_ID to Recipient.self().id.serialize(),
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
            val giftBadge = GiftBadge.ADAPTER.decode(Base64.decode(cursor.requireNonNullString(BODY)))
            val updatedBadge = giftBadge.newBuilder().redemptionState(redemptionState).build()

            updated = db
              .update(TABLE_NAME)
              .values(BODY to Base64.encodeWithPadding(updatedBadge.encode()))
              .where("$ID = ?", messageId)
              .run() > 0

            threadId = cursor.requireLong(THREAD_ID)
          }
        }
    }

    if (updated) {
      AppDependencies.databaseObserver.notifyMessageUpdateObservers(MessageId(messageId))
      notifyConversationListeners(threadId)
    }
  }

  @Throws(MmsException::class)
  fun insertMessageOutbox(
    message: OutgoingMessage,
    threadId: Long,
    forceSms: Boolean = false,
    insertListener: InsertListener? = null
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
      if (message.isBlocked) {
        type = type or MessageTypes.GROUP_V2_BIT or MessageTypes.SPECIAL_TYPE_BLOCKED
        hasSpecialType = true
      } else if (message.isUnblocked) {
        if (hasSpecialType) {
          throw MmsException("Cannot insert message with multiple special types.")
        }
        type = type or MessageTypes.GROUP_V2_BIT or MessageTypes.SPECIAL_TYPE_UNBLOCKED
        hasSpecialType = true
      } else if (message.isV2Group) {
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
      if (hasSpecialType) {
        throw MmsException("Cannot insert message with multiple special types.")
      }
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

    if (message.isReportSpam) {
      if (hasSpecialType) {
        throw MmsException("Cannot insert message with multiple special types.")
      }
      type = type or MessageTypes.SPECIAL_TYPE_REPORTED_SPAM
      hasSpecialType = true
    }

    if (message.isMessageRequestAccept) {
      if (hasSpecialType) {
        throw MmsException("Cannot insert message with multiple special types.")
      }
      type = type or MessageTypes.SPECIAL_TYPE_MESSAGE_REQUEST_ACCEPTED
      hasSpecialType = true
    }

    if (message.isBlocked && !message.isGroup) {
      if (hasSpecialType) {
        throw MmsException("Cannot insert message with multiple special types.")
      }
      type = type or MessageTypes.SPECIAL_TYPE_BLOCKED
      hasSpecialType = true
    }

    if (message.isUnblocked && !message.isGroup) {
      if (hasSpecialType) {
        throw MmsException("Cannot insert message with multiple special types.")
      }
      type = type or MessageTypes.SPECIAL_TYPE_UNBLOCKED
      hasSpecialType = true
    }

    val earlyDeliveryReceipts: Map<RecipientId, Receipt> = earlyDeliveryReceiptCache.remove(message.sentTimeMillis)

    if (earlyDeliveryReceipts.isNotEmpty()) {
      Log.w(TAG, "Found early delivery receipts for " + message.sentTimeMillis + ". Applying them.")
    }

    var editedMessage: MessageRecord? = null
    if (message.isMessageEdit) {
      try {
        editedMessage = getMessageRecord(message.messageToEdit)
        if (!MessageConstraintsUtil.isValidEditMessageSend(editedMessage)) {
          throw MmsException("Message is not valid to edit")
        }
      } catch (e: NoSuchMessageException) {
        throw MmsException("Unable to locate edited message", e)
      }
    }

    val parentStoryId: Long = if (editedMessage == null) {
      if (message.parentStoryId != null) message.parentStoryId.serialize() else 0
    } else {
      val originalId = (editedMessage as? MmsMessageRecord)?.parentStoryId
      if (originalId != null && message.outgoingQuote != null) {
        originalId.serialize()
      } else {
        0L
      }
    }

    val dateReceived = editedMessage?.dateReceived ?: System.currentTimeMillis()

    val contentValues = ContentValues()
    contentValues.put(DATE_SENT, message.sentTimeMillis)
    contentValues.put(TYPE, type)
    contentValues.put(THREAD_ID, threadId)
    contentValues.put(READ, 1)
    contentValues.put(DATE_RECEIVED, dateReceived)
    contentValues.put(SMS_SUBSCRIPTION_ID, message.subscriptionId)
    contentValues.put(EXPIRES_IN, editedMessage?.expiresIn ?: message.expiresIn)
    contentValues.put(EXPIRE_TIMER_VERSION, editedMessage?.expireTimerVersion ?: message.expireTimerVersion)
    contentValues.put(VIEW_ONCE, message.isViewOnce)
    contentValues.put(FROM_RECIPIENT_ID, Recipient.self().id.serialize())
    contentValues.put(FROM_DEVICE_ID, SignalStore.account.deviceId)
    contentValues.put(TO_RECIPIENT_ID, message.threadRecipient.id.serialize())
    contentValues.put(HAS_DELIVERY_RECEIPT, earlyDeliveryReceipts.values.sumOf { it.count })
    contentValues.put(RECEIPT_TIMESTAMP, earlyDeliveryReceipts.values.map { it.timestamp }.maxOrNull() ?: -1L)
    contentValues.put(STORY_TYPE, message.storyType.code)
    contentValues.put(PARENT_STORY_ID, parentStoryId)
    contentValues.put(SCHEDULED_DATE, message.scheduledDate)
    contentValues.putNull(LATEST_REVISION_ID)
    contentValues.put(MESSAGE_EXTRAS, message.messageExtras?.encode())

    if (editedMessage != null) {
      contentValues.put(ORIGINAL_MESSAGE_ID, editedMessage.getOriginalOrOwnMessageId().id)
      contentValues.put(REVISION_NUMBER, editedMessage.revisionNumber + 1)
      contentValues.put(EXPIRE_STARTED, editedMessage.expireStarted)
    } else {
      contentValues.putNull(ORIGINAL_MESSAGE_ID)
    }

    if (message.threadRecipient.isSelf && hasAudioAttachment(message.attachments)) {
      contentValues.put(VIEWED_COLUMN, 1L)
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
        adjustedQuoteBodyRanges.newBuilder()
      } else {
        BodyRangeList.Builder()
      }

      val mentionsList = MentionUtil.mentionsToBodyRangeList(updated.mentions)
      if (mentionsList != null) {
        quoteBodyRanges.ranges += mentionsList.ranges
      }

      if (quoteBodyRanges.ranges.isNotEmpty()) {
        contentValues.put(QUOTE_BODY_RANGES, quoteBodyRanges.build().encode())
      }

      if (editedMessage == null) {
        quoteAttachments += message.outgoingQuote.attachments
      }
    }

    val updatedBodyAndMentions = MentionUtil.updateBodyAndMentionsWithPlaceholders(message.body, message.mentions)
    val bodyRanges = message.bodyRanges.adjustBodyRanges(updatedBodyAndMentions.bodyAdjustments)
    val (messageId, insertedAttachments) = insertMediaMessage(
      threadId = threadId,
      body = updatedBodyAndMentions.bodyAsString?.trim(),
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

    if (messageId < 0) {
      throw MmsException("Failed to insert message! Likely a duplicate.")
    }

    if (message.threadRecipient.isGroup) {
      val members: MutableSet<RecipientId> = mutableSetOf()

      if (message.isGroupUpdate && message.isV2Group) {
        members += message.requireGroupV2Properties().allActivePendingAndRemovedMembers
          .distinct()
          .map { serviceId -> RecipientId.from(serviceId) }
          .toList()

        members -= Recipient.self().id
      } else {
        members += groups.getGroupMembers(message.threadRecipient.requireGroupId(), GroupTable.MemberSet.FULL_MEMBERS_EXCLUDING_SELF).map { it.id }
      }

      groupReceipts.insert(members, messageId, defaultReceiptStatus, message.sentTimeMillis)

      for (recipientId in earlyDeliveryReceipts.keys) {
        groupReceipts.update(recipientId, messageId, GroupReceiptTable.STATUS_DELIVERED, -1)
      }
    } else if (message.threadRecipient.isDistributionList) {
      val members = distributionLists.getMembers(message.threadRecipient.requireDistributionListId())

      groupReceipts.insert(members, messageId, defaultReceiptStatus, message.sentTimeMillis)

      for (recipientId in earlyDeliveryReceipts.keys) {
        groupReceipts.update(recipientId, messageId, GroupReceiptTable.STATUS_DELIVERED, -1)
      }
    }

    if (message.messageToEdit > 0) {
      writableDatabase.update(TABLE_NAME)
        .values(LATEST_REVISION_ID to messageId)
        .where("$ID_WHERE OR $LATEST_REVISION_ID = ?", message.messageToEdit, message.messageToEdit)
        .run()

      val textAttachments = (editedMessage as? MmsMessageRecord)?.slideDeck?.asAttachments()?.filter { it.contentType == MediaUtil.LONG_TEXT }?.mapNotNull { (it as? DatabaseAttachment)?.attachmentId?.id } ?: emptyList()
      val linkPreviewAttachments = (editedMessage as? MmsMessageRecord)?.linkPreviews?.mapNotNull { it.attachmentId?.id } ?: emptyList()
      val excludeIds = HashSet<Long>()
      excludeIds += textAttachments
      excludeIds += linkPreviewAttachments
      attachments.duplicateAttachmentsForMessage(messageId, message.messageToEdit, excludeIds)

      reactions.moveReactionsToNewMessage(messageId, message.messageToEdit)
    }

    threads.updateLastSeenAndMarkSentAndLastScrolledSilenty(threadId, dateReceived)

    if (!message.storyType.isStory) {
      if (message.outgoingQuote == null && editedMessage == null) {
        AppDependencies.databaseObserver.notifyMessageInsertObservers(threadId, MessageId(messageId))
      } else {
        AppDependencies.databaseObserver.notifyConversationListeners(threadId)
      }

      if (message.scheduledDate != -1L) {
        AppDependencies.databaseObserver.notifyScheduledMessageObservers(threadId)
      }
    } else {
      AppDependencies.databaseObserver.notifyStoryObservers(message.threadRecipient.id)
    }

    if (!message.isIdentityVerified && !message.isIdentityDefault) {
      ThreadUpdateJob.enqueue(threadId)
    }

    TrimThreadJob.enqueueAsync(threadId)

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
  ): kotlin.Pair<Long, Map<Attachment, AttachmentId>?> {
    val mentionsSelf = mentions.any { Recipient.resolved(it.recipientId).isSelf }
    val allAttachments: MutableList<Attachment> = mutableListOf()

    allAttachments += attachments
    allAttachments += sharedContacts.mapNotNull { it.avatarAttachment }
    allAttachments += linkPreviews.mapNotNull { it.thumbnail.orElse(null) }

    contentValues.put(BODY, body)
    contentValues.put(MENTIONS_SELF, if (mentionsSelf) 1 else 0)
    if (messageRanges != null) {
      contentValues.put(MESSAGE_RANGES, messageRanges.encode())
    }

    val (messageId, insertedAttachments) = writableDatabase.withinTransaction { db ->
      val messageId = db.insert(TABLE_NAME, null, contentValues)
      if (messageId < 0) {
        Log.w(TAG, "Tried to insert media message but failed. Assuming duplicate.")
        return@withinTransaction -1L to null
      }

      threads.markAsActiveEarly(threadId)
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

      messageId to insertedAttachments
    }

    if (messageId < 0) {
      return messageId to insertedAttachments
    }

    insertListener?.onComplete()

    val contentValuesThreadId = contentValues.getAsLong(THREAD_ID)

    if (updateThread) {
      threads.setLastScrolled(contentValuesThreadId, 0)
      threads.update(threadId, unarchive)
    }

    return messageId to insertedAttachments
  }

  /**
   * Deletes the call updates specified in the messageIds set.
   */
  fun deleteCallUpdates(messageIds: Set<Long>): Int {
    return deleteCallUpdatesInternal(messageIds, SqlUtil.CollectionOperator.IN)
  }

  private fun deleteCallUpdatesInternal(
    messageIds: Set<Long>,
    collectionOperator: SqlUtil.CollectionOperator
  ): Int {
    var rowsDeleted = 0
    val threadIds: Set<Long> = writableDatabase.withinTransaction {
      SqlUtil.buildCollectionQuery(
        column = ID,
        values = messageIds,
        prefix = "$IS_CALL_TYPE_CLAUSE AND ",
        collectionOperator = collectionOperator
      ).map { query ->
        val threadSet = writableDatabase.select(THREAD_ID)
          .from(TABLE_NAME)
          .where(query.where, query.whereArgs)
          .run()
          .readToSet { cursor ->
            cursor.requireLong(THREAD_ID)
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

    threadIds.forEach {
      threads.update(
        threadId = it,
        unarchive = false,
        allowDeletion = true
      )
    }

    notifyConversationListeners(threadIds)
    notifyConversationListListeners()
    return rowsDeleted
  }

  fun deleteMessage(messageId: Long): Boolean {
    val threadId = getThreadIdForMessage(messageId)
    return deleteMessage(messageId, threadId)
  }

  @VisibleForTesting
  fun deleteMessage(messageId: Long, threadId: Long, notify: Boolean = true, updateThread: Boolean = true): Boolean {
    Log.d(TAG, "deleteMessage($messageId)")

    attachments.deleteAttachmentsForMessage(messageId)
    groupReceipts.deleteRowsForMessage(messageId)
    mentions.deleteMentionsForMessage(messageId)

    writableDatabase
      .delete(TABLE_NAME)
      .where("$ID = ?", messageId)
      .run()

    calls.updateCallEventDeletionTimestamps()
    threads.setLastScrolled(threadId, 0)

    val threadDeleted = if (updateThread) {
      threads.update(threadId, unarchive = false, syncThreadDelete = false)
    } else {
      false
    }

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

    AppDependencies.scheduledMessageManager.scheduleIfNecessary()
    AppDependencies.databaseObserver.notifyScheduledMessageObservers(threadId)
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

  fun getSerializedSharedContacts(insertedAttachmentIds: Map<Attachment, AttachmentId>, contacts: List<Contact>): String? {
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

  fun getSerializedLinkPreviews(insertedAttachmentIds: Map<Attachment, AttachmentId>, previews: List<LinkPreview>): String? {
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

  fun deleteMessagesInThreadBeforeDate(threadId: Long, date: Long, inclusive: Boolean): Int {
    val condition = if (inclusive) "<=" else "<"
    val extraWhere = "AND ${TABLE_NAME}.$DATE_RECEIVED $condition $date"

    return deleteMessagesInThread(listOf(threadId), extraWhere)
  }

  fun deleteMessagesInThread(threadIds: Collection<Long>, extraWhere: String = ""): Int {
    var totalDeletedCount = 0

    writableDatabase.withinTransaction { db ->
      SignalDatabase.messageSearch.dropAfterMessageDeleteTrigger()
      SignalDatabase.messageLog.dropAfterMessageDeleteTrigger()

      for (threadId in threadIds) {
        val subSelect = "SELECT ${TABLE_NAME}.$ID FROM $TABLE_NAME WHERE ${TABLE_NAME}.$THREAD_ID = $threadId $extraWhere LIMIT 1000"
        do {
          // Bulk deleting FK tables for large message delete efficiency
          db.delete(StorySendTable.TABLE_NAME)
            .where("${StorySendTable.TABLE_NAME}.${StorySendTable.MESSAGE_ID} IN ($subSelect)")
            .run()

          db.delete(ReactionTable.TABLE_NAME)
            .where("${ReactionTable.TABLE_NAME}.${ReactionTable.MESSAGE_ID} IN ($subSelect)")
            .run()

          db.delete(CallTable.TABLE_NAME)
            .where("${CallTable.TABLE_NAME}.${CallTable.MESSAGE_ID} IN ($subSelect)")
            .run()

          // Must delete rows from FTS table before deleting from main table due to FTS requirement when deleting by rowid
          db.delete(SearchTable.FTS_TABLE_NAME)
            .where("${SearchTable.FTS_TABLE_NAME}.${SearchTable.ID} IN ($subSelect)")
            .run()

          // Actually delete messages
          val deletedCount = db.delete(TABLE_NAME)
            .where("$ID IN ($subSelect)")
            .run()

          totalDeletedCount += deletedCount
        } while (deletedCount > 0)
      }

      SignalDatabase.messageSearch.restoreAfterMessageDeleteTrigger()
      SignalDatabase.messageLog.restoreAfterMessageDeleteTrigger()
    }

    return totalDeletedCount
  }

  fun deleteAbandonedMessages(threadId: Long? = null): Int {
    val where = if (threadId == null) {
      "$THREAD_ID NOT IN (SELECT ${ThreadTable.TABLE_NAME}.${ThreadTable.ID} FROM ${ThreadTable.TABLE_NAME} WHERE ${ThreadTable.ACTIVE} = 1)"
    } else {
      "$THREAD_ID = $threadId AND (SELECT ${ThreadTable.TABLE_NAME}.${ThreadTable.ACTIVE} FROM ${ThreadTable.TABLE_NAME} WHERE ${ThreadTable.TABLE_NAME}.${ThreadTable.ID} = $threadId) != 1"
    }

    val deletes = writableDatabase
      .delete(TABLE_NAME)
      .where(where)
      .run()

    if (deletes > 0) {
      Log.i(TAG, "Deleted $deletes abandoned messages")
      calls.updateCallEventDeletionTimestamps()
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

  fun getMessageIdOrNull(message: SyncMessageId): Long? {
    return readableDatabase
      .select(ID)
      .from(TABLE_NAME)
      .where("$DATE_SENT = ? AND $FROM_RECIPIENT_ID = ?", message.timetamp, message.recipientId)
      .run()
      .readToSingleLongOrNull()
  }

  fun getMessageIdOrNull(message: SyncMessageId, threadId: Long): Long? {
    return readableDatabase
      .select(ID)
      .from(TABLE_NAME)
      .where("$DATE_SENT = ? AND $FROM_RECIPIENT_ID = ? AND $THREAD_ID = $threadId", message.timetamp, message.recipientId)
      .run()
      .readToSingleLongOrNull()
  }

  fun deleteMessages(messagesToDelete: List<SyncMessageId>): List<SyncMessageId> {
    val threads = mutableSetOf<Long>()
    val unhandled = mutableListOf<SyncMessageId>()

    for (message in messagesToDelete) {
      readableDatabase
        .select(ID, THREAD_ID)
        .from(TABLE_NAME)
        .where("$DATE_SENT = ? AND $FROM_RECIPIENT_ID = ?", message.timetamp, message.recipientId)
        .run()
        .use {
          if (it.moveToFirst()) {
            val messageId = it.requireLong(ID)
            val threadId = it.requireLong(THREAD_ID)

            deleteMessage(
              messageId = messageId,
              threadId = threadId,
              notify = false,
              updateThread = false
            )
            threads += threadId
          } else {
            unhandled += message
          }
        }
    }

    threads
      .forEach { threadId ->
        SignalDatabase.threads.update(threadId, unarchive = false)
        notifyConversationListeners(threadId)
      }

    notifyConversationListListeners()
    notifyStickerListeners()
    notifyStickerPackListeners()
    OptimizeMessageSearchIndexJob.enqueue()

    return unhandled
  }

  private fun getMessagesInThreadAfterInclusive(threadId: Long, timestamp: Long, limit: Long): List<MessageRecord> {
    val where = "$TABLE_NAME.$THREAD_ID = ? AND $TABLE_NAME.$DATE_RECEIVED >= ? AND $TABLE_NAME.$SCHEDULED_DATE = -1 AND $TABLE_NAME.$LATEST_REVISION_ID IS NULL"
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
    writableDatabase.deleteAll(TABLE_NAME)
    calls.updateCallEventDeletionTimestamps()

    OptimizeMessageSearchIndexJob.enqueue()
  }

  fun getNearestExpiringViewOnceMessage(): ViewOnceExpirationInfo? {
    val query = """
      SELECT 
        $TABLE_NAME.$ID, 
        $VIEW_ONCE, 
        $DATE_RECEIVED 
      FROM 
        $TABLE_NAME INNER JOIN ${AttachmentTable.TABLE_NAME} ON $TABLE_NAME.$ID = ${AttachmentTable.TABLE_NAME}.${AttachmentTable.MESSAGE_ID} 
      WHERE 
        $VIEW_ONCE > 0 AND 
        (${AttachmentTable.DATA_FILE} NOT NULL OR ${AttachmentTable.TRANSFER_STATE} != ?)
      """

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
      .where("$FROM_RECIPIENT_ID = ? AND $TYPE = ?", recipientId, MessageTypes.CHANGE_NUMBER_TYPE)
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
  fun collapseJoinRequestEventsIfPossible(threadId: Long, message: IncomingMessage): Optional<InsertResult> {
    var result: InsertResult? = null

    writableDatabase.withinTransaction { db ->
      mmsReaderFor(getConversation(threadId, 0, 2)).use { reader ->
        val latestMessage = reader.getNext()

        if (latestMessage != null && latestMessage.isGroupV2) {
          val changeEditor: Optional<ServiceId> = message.groupContext?.let { GroupV2UpdateMessageUtil.getChangeEditor(it) } ?: Optional.empty()

          if (changeEditor.isPresent && latestMessage.isGroupV2JoinRequest(changeEditor.get())) {
            val secondLatestMessage = reader.getNext()

            val id: Long
            val encodedBody: String
            val changeRevision: Int = message.groupContext?.let { GroupV2UpdateMessageUtil.getChangeRevision(it) } ?: -1

            if (secondLatestMessage != null && secondLatestMessage.isGroupV2JoinRequest(changeEditor.get())) {
              id = secondLatestMessage.id
              encodedBody = MessageRecord.createNewContextWithAppendedDeleteJoinRequest(secondLatestMessage, changeRevision, changeEditor.get().toByteString())
              deleteMessage(latestMessage.id)
            } else {
              id = latestMessage.id
              encodedBody = MessageRecord.createNewContextWithAppendedDeleteJoinRequest(latestMessage, changeRevision, changeEditor.get().toByteString())
            }

            db.update(TABLE_NAME)
              .values(BODY to encodedBody)
              .where("$ID = ?", id)
              .run()

            result = InsertResult(
              messageId = id,
              threadId = threadId,
              threadWasNewlyCreated = false
            )
          }
        }
      }
    }

    return result.toOptional()
  }

  fun getInsecureMessageCount(): Int {
    return readableDatabase
      .select("COUNT(*)")
      .from(TABLE_NAME)
      .where(getInsecureMessageClause())
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

  private fun hasSmsExportMessage(threadId: Long): Boolean {
    return readableDatabase
      .exists(TABLE_NAME)
      .where("$THREAD_ID = ? AND $TYPE = ?", threadId, MessageTypes.SMS_EXPORT_TYPE)
      .run()
  }

  fun hasReportSpamMessage(threadId: Long): Boolean {
    return readableDatabase
      .exists(TABLE_NAME)
      .where("$THREAD_ID = $threadId AND ($TYPE & ${MessageTypes.SPECIAL_TYPES_MASK}) = ${MessageTypes.SPECIAL_TYPE_REPORTED_SPAM}")
      .run()
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
      .select(FROM_RECIPIENT_ID, SERVER_GUID, DATE_RECEIVED)
      .from(TABLE_NAME)
      .where("$THREAD_ID = ? AND $DATE_RECEIVED <= ?", threadId, timestamp)
      .orderBy("$DATE_RECEIVED DESC")
      .limit(3)
      .run()
      .forEach { cursor ->
        val serverGuid: String? = cursor.requireString(SERVER_GUID)

        if (serverGuid != null && serverGuid.isNotEmpty()) {
          data += ReportSpamData(
            recipientId = RecipientId.from(cursor.requireLong(FROM_RECIPIENT_ID)),
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
    return readableDatabase
      .exists(TABLE_NAME)
      .where("$QUOTE_ID = ?  AND $QUOTE_AUTHOR = ? AND $SCHEDULED_DATE = ?", messageRecord.dateSent, messageRecord.fromRecipient.id, -1)
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

      byQuoteDescriptor[QuoteDescriptor(timestamp, record.fromRecipient.id)] = record
      args.add(buildArgs(timestamp, record.fromRecipient.id, -1))
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

    val query = "$DATE_SENT = ${targetMessage.quote!!.id} AND $FROM_RECIPIENT_ID = '${targetMessage.quote!!.author.serialize()}'"

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

    val query = "$QUOTE_ID = ${targetMessage.dateSent} AND $QUOTE_AUTHOR = ${targetMessage.fromRecipient.id.serialize()} AND $SCHEDULED_DATE = -1 AND $LATEST_REVISION_ID IS NULL"
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

  fun getQuotedMessagePosition(threadId: Long, quoteId: Long, authorId: RecipientId): Int {
    val targetMessageDateReceived: Long = readableDatabase
      .select(DATE_RECEIVED, LATEST_REVISION_ID)
      .from(TABLE_NAME)
      .where("$DATE_SENT = $quoteId AND $FROM_RECIPIENT_ID = ? AND $REMOTE_DELETED = 0 AND $SCHEDULED_DATE = -1", authorId)
      .run()
      .readToSingleObject { cursor ->
        val latestRevisionId = cursor.requireLongOrNull(LATEST_REVISION_ID)
        if (latestRevisionId != null) {
          readableDatabase
            .select(DATE_RECEIVED)
            .from(TABLE_NAME)
            .where("$ID = ?", latestRevisionId)
            .run()
            .readToSingleLong(-1L)
        } else {
          cursor.requireLong(DATE_RECEIVED)
        }
      } ?: -1L

    if (targetMessageDateReceived == -1L) {
      return -1
    }

    return readableDatabase
      .select("COUNT(*)")
      .from(TABLE_NAME)
      .where("$THREAD_ID = $threadId AND $STORY_TYPE = 0 AND $PARENT_STORY_ID <= 0 AND $SCHEDULED_DATE = -1 AND $LATEST_REVISION_ID IS NULL AND $DATE_RECEIVED > $targetMessageDateReceived")
      .run()
      .readToSingleInt()
  }

  fun getMessagePositionInConversation(threadId: Long, receivedTimestamp: Long, authorId: RecipientId): Int {
    val validMessageExists: Boolean = readableDatabase
      .exists(TABLE_NAME)
      .where("$DATE_RECEIVED = $receivedTimestamp AND $FROM_RECIPIENT_ID = ? AND $REMOTE_DELETED = 0 AND $SCHEDULED_DATE = -1 AND $LATEST_REVISION_ID IS NULL", authorId)
      .run()

    if (!validMessageExists) {
      return -1
    }

    return readableDatabase
      .select("COUNT(*)")
      .from(TABLE_NAME)
      .where("$THREAD_ID = $threadId AND $STORY_TYPE = 0 AND $PARENT_STORY_ID <= 0 AND $SCHEDULED_DATE = -1 AND $LATEST_REVISION_ID IS NULL AND $DATE_RECEIVED > $receivedTimestamp")
      .run()
      .readToSingleInt(-1)
  }

  fun getMessagePositionInConversation(threadId: Long, receivedTimestamp: Long): Int {
    return getMessagePositionInConversation(threadId, 0, receivedTimestamp)
  }

  fun messageExistsOnDays(threadId: Long, dayStarts: Collection<Long>): Map<Long, Boolean> {
    if (dayStarts.isEmpty()) {
      return emptyMap()
    }
    return dayStarts.associateWith { startOfDay ->
      readableDatabase
        .exists(TABLE_NAME)
        .where("$THREAD_ID = $threadId AND $DATE_SENT >= $startOfDay AND $DATE_SENT < $startOfDay + 86400000 AND $SCHEDULED_DATE = -1 AND $LATEST_REVISION_ID IS NULL AND $STORY_TYPE = 0 AND $PARENT_STORY_ID <= 0")
        .run()
    }
  }

  fun getEarliestMessageSentDate(threadId: Long): Long {
    return readableDatabase
      .select(DATE_SENT)
      .from(TABLE_NAME)
      .where("$THREAD_ID = $threadId AND $SCHEDULED_DATE = -1 AND $LATEST_REVISION_ID IS NULL AND $STORY_TYPE = 0 AND $PARENT_STORY_ID <= 0")
      .orderBy("$DATE_SENT ASC")
      .limit(1)
      .run()
      .readToSingleLong(0)
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
    val selection = if (groupStoryId > 0) {
      "$THREAD_ID = $threadId AND $DATE_RECEIVED < $receivedTimestamp AND $STORY_TYPE = 0 AND $PARENT_STORY_ID = $groupStoryId AND $SCHEDULED_DATE = -1 AND $LATEST_REVISION_ID IS NULL"
    } else {
      "$THREAD_ID = $threadId AND $DATE_RECEIVED > $receivedTimestamp AND $STORY_TYPE = 0 AND $PARENT_STORY_ID <= 0 AND $SCHEDULED_DATE = -1 AND $LATEST_REVISION_ID IS NULL"
    }

    return readableDatabase
      .select("COUNT(*)")
      .from(TABLE_NAME)
      .where(selection)
      .run()
      .readToSingleInt(-1)
  }

  fun getTimestampForFirstMessageAfterDate(date: Long): Long {
    return readableDatabase
      .select(DATE_RECEIVED)
      .from(TABLE_NAME)
      .where("$DATE_RECEIVED > $date AND $SCHEDULED_DATE = -1 AND $LATEST_REVISION_ID IS NULL")
      .orderBy("$DATE_RECEIVED ASC")
      .limit(1)
      .run()
      .readToSingleLong()
  }

  fun getMessageCountBeforeDate(date: Long): Int {
    return readableDatabase
      .select("COUNT(*)")
      .from(TABLE_NAME)
      .where("$DATE_RECEIVED < $date AND $SCHEDULED_DATE = -1 AND $LATEST_REVISION_ID IS NULL")
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

  fun getMessagePositionByDateReceivedTimestamp(threadId: Long, timestamp: Long, inclusive: Boolean): Int {
    return readableDatabase
      .select("COUNT(*)")
      .from(TABLE_NAME)
      .where("$THREAD_ID = $threadId AND $DATE_RECEIVED ${if (inclusive) ">=" else ">"} $timestamp AND $STORY_TYPE = 0 AND $PARENT_STORY_ID <= 0 AND $SCHEDULED_DATE = -1 AND $LATEST_REVISION_ID IS NULL")
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
      .from("$TABLE_NAME INDEXED BY $INDEX_THREAD_UNREAD_COUNT")
      .where("$THREAD_ID = $threadId AND $STORY_TYPE = 0 AND $PARENT_STORY_ID <= 0 AND $LATEST_REVISION_ID IS NULL AND $SCHEDULED_DATE = -1 AND $READ = 0")
      .run()
      .readToSingleInt()
  }

  fun messageExists(messageRecord: MessageRecord): Boolean {
    return readableDatabase
      .exists(TABLE_NAME)
      .where("$ID = ?", messageRecord.id)
      .run()
  }

  fun messageExists(sentTimestamp: Long, author: RecipientId): Boolean {
    return readableDatabase
      .exists("$TABLE_NAME INDEXED BY $INDEX_DATE_SENT_FROM_TO_THREAD")
      .where("$DATE_SENT = ? AND $FROM_RECIPIENT_ID = ?", sentTimestamp, author)
      .run()
  }

  fun getReportSpamMessageServerData(threadId: Long, timestamp: Long, limit: Int): List<ReportSpamData> {
    return getReportSpamMessageServerGuids(threadId, timestamp)
      .sortedBy { it.dateReceived }
      .take(limit)
  }

  fun getGroupReportSpamMessageServerData(threadId: Long, inviter: RecipientId, timestamp: Long, limit: Int): List<ReportSpamData> {
    val data: MutableList<ReportSpamData> = ArrayList()

    val incomingGroupUpdateClause = "($TYPE & ${MessageTypes.BASE_TYPE_MASK}) = ${MessageTypes.BASE_INBOX_TYPE} AND ($TYPE & ${MessageTypes.GROUP_UPDATE_BIT}) != 0"

    readableDatabase
      .select(FROM_RECIPIENT_ID, SERVER_GUID, DATE_RECEIVED)
      .from(TABLE_NAME)
      .where("$FROM_RECIPIENT_ID = ? AND $THREAD_ID = ? AND $DATE_RECEIVED <= ? AND $incomingGroupUpdateClause", inviter, threadId, timestamp)
      .orderBy("$DATE_RECEIVED DESC")
      .limit(limit)
      .run()
      .forEach { cursor ->
        val serverGuid: String? = cursor.requireString(SERVER_GUID)

        if (serverGuid != null && serverGuid.isNotEmpty()) {
          data += ReportSpamData(
            recipientId = RecipientId.from(cursor.requireLong(FROM_RECIPIENT_ID)),
            serverGuid = serverGuid,
            dateReceived = cursor.requireLong(DATE_RECEIVED)
          )
        }
      }

    return data
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
          MessageExportState()
        } else {
          try {
            MessageExportState.ADAPTER.decode(bytes)
          } catch (e: IOException) {
            MessageExportState()
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
      .values(EXPORT_STATE to messageExportState.encode())
      .where("$ID = ?", messageId.id)
      .run()
  }

  fun incrementDeliveryReceiptCounts(targetTimestamps: List<Long>, receiptAuthor: RecipientId, receiptSentTimestamp: Long, stopwatch: Stopwatch? = null): Set<Long> {
    return incrementReceiptCounts(targetTimestamps, receiptAuthor, receiptSentTimestamp, ReceiptType.DELIVERY, stopwatch = stopwatch)
  }

  fun incrementDeliveryReceiptCount(targetTimestamps: Long, receiptAuthor: RecipientId, receiptSentTimestamp: Long): Boolean {
    return incrementReceiptCount(targetTimestamps, receiptAuthor, receiptSentTimestamp, ReceiptType.DELIVERY)
  }

  /**
   * @return A list of ID's that were not updated.
   */
  fun incrementReadReceiptCounts(targetTimestamps: List<Long>, receiptAuthor: RecipientId, receiptSentTimestamp: Long): Set<Long> {
    return incrementReceiptCounts(targetTimestamps, receiptAuthor, receiptSentTimestamp, ReceiptType.READ)
  }

  fun incrementReadReceiptCount(targetTimestamps: Long, receiptAuthor: RecipientId, receiptSentTimestamp: Long): Boolean {
    return incrementReceiptCount(targetTimestamps, receiptAuthor, receiptSentTimestamp, ReceiptType.READ)
  }

  /**
   * @return A list of ID's that were not updated.
   */
  fun incrementViewedReceiptCounts(targetTimestamps: List<Long>, receiptAuthor: RecipientId, receiptSentTimestamp: Long): Set<Long> {
    return incrementReceiptCounts(targetTimestamps, receiptAuthor, receiptSentTimestamp, ReceiptType.VIEWED)
  }

  fun incrementViewedNonStoryReceiptCounts(targetTimestamps: List<Long>, receiptAuthor: RecipientId, receiptSentTimestamp: Long): Set<Long> {
    return incrementReceiptCounts(targetTimestamps, receiptAuthor, receiptSentTimestamp, ReceiptType.VIEWED, MessageQualifier.NORMAL)
  }

  fun incrementViewedReceiptCount(targetTimestamp: Long, receiptAuthor: RecipientId, receiptSentTimestamp: Long): Boolean {
    return incrementReceiptCount(targetTimestamp, receiptAuthor, receiptSentTimestamp, ReceiptType.VIEWED)
  }

  fun incrementViewedStoryReceiptCounts(targetTimestamps: List<Long>, receiptAuthor: RecipientId, receiptSentTimestamp: Long): Set<Long> {
    val messageUpdates: MutableSet<MessageReceiptUpdate> = HashSet()
    val unhandled: MutableSet<Long> = HashSet()

    writableDatabase.withinTransaction {
      for (targetTimestamp in targetTimestamps) {
        val updates = incrementReceiptCountInternal(targetTimestamp, receiptAuthor, receiptSentTimestamp, ReceiptType.VIEWED, MessageQualifier.STORY)

        if (updates.isNotEmpty()) {
          messageUpdates += updates
        } else {
          unhandled += targetTimestamp
        }
      }
    }

    for (update in messageUpdates) {
      AppDependencies.databaseObserver.notifyMessageUpdateObservers(update.messageId)
      AppDependencies.databaseObserver.notifyVerboseConversationListeners(setOf(update.threadId))
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
  private fun incrementReceiptCount(targetTimestamp: Long, receiptAuthor: RecipientId, receiptSentTimestamp: Long, receiptType: ReceiptType, messageQualifier: MessageQualifier = MessageQualifier.ALL): Boolean {
    var messageUpdates: Set<MessageReceiptUpdate> = HashSet()

    writableDatabase.withinTransaction {
      messageUpdates = incrementReceiptCountInternal(targetTimestamp, receiptAuthor, receiptSentTimestamp, receiptType, messageQualifier)

      for (messageUpdate in messageUpdates) {
        threads.updateReceiptStatus(messageUpdate.messageId.id, messageUpdate.threadId)
      }
    }

    for (threadUpdate in messageUpdates) {
      AppDependencies.databaseObserver.notifyMessageUpdateObservers(threadUpdate.messageId)
    }

    return messageUpdates.isNotEmpty()
  }

  /**
   * Wraps multiple receipt updates in a transaction and triggers the proper updates.
   *
   * @return All of the target timestamps that couldn't be found in the table.
   */
  private fun incrementReceiptCounts(targetTimestamps: List<Long>, receiptAuthor: RecipientId, receiptSentTimestamp: Long, receiptType: ReceiptType, messageQualifier: MessageQualifier = MessageQualifier.ALL, stopwatch: Stopwatch? = null): Set<Long> {
    val messageUpdates: MutableSet<MessageReceiptUpdate> = HashSet()
    val missingTargetTimestamps: MutableSet<Long> = HashSet()

    writableDatabase.withinTransaction {
      for (targetTimestamp in targetTimestamps) {
        val updates: Set<MessageReceiptUpdate> = incrementReceiptCountInternal(targetTimestamp, receiptAuthor, receiptSentTimestamp, receiptType, messageQualifier, stopwatch)
        if (updates.isNotEmpty()) {
          messageUpdates += updates
        } else {
          missingTargetTimestamps += targetTimestamp
        }
      }

      for (update in messageUpdates) {
        if (update.shouldUpdateSnippet) {
          threads.updateReceiptStatus(update.messageId.id, update.threadId, stopwatch)
        }
      }
    }

    for (update in messageUpdates) {
      AppDependencies.databaseObserver.notifyMessageUpdateObservers(update.messageId)
      AppDependencies.databaseObserver.notifyVerboseConversationListeners(setOf(update.threadId))

      if (messageQualifier == MessageQualifier.STORY) {
        AppDependencies.databaseObserver.notifyStoryObservers(threads.getRecipientIdForThreadId(update.threadId)!!)
      }
    }

    if (messageUpdates.isNotEmpty()) {
      notifyConversationListListeners()
    }

    stopwatch?.split("observers")

    return missingTargetTimestamps
  }

  private fun incrementReceiptCountInternal(targetTimestamp: Long, receiptAuthor: RecipientId, receiptSentTimestamp: Long, receiptType: ReceiptType, messageQualifier: MessageQualifier, stopwatch: Stopwatch? = null): Set<MessageReceiptUpdate> {
    val qualifierWhere: String = when (messageQualifier) {
      MessageQualifier.NORMAL -> " AND NOT ($IS_STORY_CLAUSE)"
      MessageQualifier.STORY -> " AND $IS_STORY_CLAUSE"
      MessageQualifier.ALL -> ""
    }

    // Note: While it is true that multiple messages can have the same (sent, author) pair, this should only happen for stories, which are handled below.
    val receiptData: ReceiptData? = readableDatabase
      .select(ID, THREAD_ID, STORY_TYPE, receiptType.columnName, TO_RECIPIENT_ID)
      .from(TABLE_NAME)
      .where(
        """
        $DATE_SENT = $targetTimestamp AND
        $FROM_RECIPIENT_ID = ? AND
        (
          $TO_RECIPIENT_ID = ? OR 
          EXISTS (
            SELECT 1 
            FROM ${RecipientTable.TABLE_NAME} 
            WHERE 
              ${RecipientTable.TABLE_NAME}.${RecipientTable.ID} = $TO_RECIPIENT_ID AND 
              ${RecipientTable.TABLE_NAME}.${RecipientTable.TYPE} != ${RecipientTable.RecipientType.INDIVIDUAL.id}
          )
        )
        $qualifierWhere
        """,
        Recipient.self().id,
        receiptAuthor
      )
      .limit(1)
      .run()
      .readToSingleObject { cursor ->
        ReceiptData(
          messageId = cursor.requireLong(ID),
          threadId = cursor.requireLong(THREAD_ID),
          storyType = StoryType.fromCode(cursor.requireInt(STORY_TYPE)),
          marked = cursor.requireBoolean(receiptType.columnName),
          forIndividualChat = cursor.requireLong(TO_RECIPIENT_ID) == receiptAuthor.toLong()
        )
      }

    stopwatch?.split("receipt-query")

    if (receiptData == null) {
      if (receiptType == ReceiptType.DELIVERY) {
        earlyDeliveryReceiptCache.increment(targetTimestamp, receiptAuthor, receiptSentTimestamp)
      }

      return emptySet()
    }

    if (!receiptData.marked) {
      // We set the receipt_timestamp to the max of the two values because that single column represents the timestamp of the last receipt of any type.
      // That means we want to update it for each new receipt type, but we never want the time to go backwards.
      writableDatabase.execSQL(
        """
        UPDATE $TABLE_NAME
        SET
          ${receiptType.columnName} = 1,
          $RECEIPT_TIMESTAMP = MAX($RECEIPT_TIMESTAMP, $receiptSentTimestamp) 
        WHERE
          $ID = ${receiptData.messageId}
        """
      )
    }
    stopwatch?.split("receipt-update")

    if (!receiptData.forIndividualChat) {
      groupReceipts.update(receiptAuthor, receiptData.messageId, receiptType.groupStatus, receiptSentTimestamp)
    }

    stopwatch?.split("group-receipt")

    return if (receiptData.storyType != StoryType.NONE) {
      val storyMessageIds = storySends.getStoryMessagesFor(receiptAuthor, targetTimestamp)
      storyMessageIds.forEach { messageId -> groupReceipts.update(receiptAuthor, messageId.id, receiptType.groupStatus, receiptSentTimestamp) }
      storyMessageIds.map { messageId -> MessageReceiptUpdate(-1, messageId, false) }.toSet()
    } else {
      setOf(MessageReceiptUpdate(receiptData.threadId, MessageId(receiptData.messageId), shouldUpdateSnippet = receiptType != ReceiptType.VIEWED && !receiptData.marked))
    }.also {
      stopwatch?.split("stories")
    }
  }

  /**
   * @return Unhandled ids
   */
  fun setTimestampReadFromSyncMessage(readMessages: List<SyncMessage.Read>, proposedExpireStarted: Long, threadToLatestRead: MutableMap<Long, Long>): Collection<SyncMessageId> {
    val expiringMessages: MutableList<Pair<Long, Long>> = mutableListOf()
    val updatedThreads: MutableSet<Long> = mutableSetOf()
    val unhandled: MutableCollection<SyncMessageId> = mutableListOf()

    writableDatabase.withinTransaction {
      for (readMessage in readMessages) {
        val authorId: RecipientId = recipients.getOrInsertFromServiceId(ServiceId.parseOrThrow(readMessage.senderAci!!))

        val result: TimestampReadResult = setTimestampReadFromSyncMessageInternal(
          messageId = SyncMessageId(authorId, readMessage.timestamp!!),
          proposedExpireStarted = proposedExpireStarted,
          threadToLatestRead = threadToLatestRead
        )

        expiringMessages += result.expiring
        updatedThreads += result.threads

        if (result.threads.isEmpty()) {
          unhandled += SyncMessageId(authorId, readMessage.timestamp!!)
        }
      }

      for (threadId in updatedThreads) {
        threads.updateReadState(threadId)
      }
    }

    for (expiringMessage in expiringMessages) {
      AppDependencies.expiringMessageManager.scheduleDeletion(expiringMessage.first(), true, proposedExpireStarted, expiringMessage.second())
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
    val threads: MutableList<Long> = LinkedList()

    readableDatabase
      .select(ID, THREAD_ID, EXPIRES_IN, EXPIRE_STARTED, LATEST_REVISION_ID)
      .from(TABLE_NAME)
      .where("$DATE_SENT = ? AND ($FROM_RECIPIENT_ID = ? OR ($FROM_RECIPIENT_ID = ? AND $outgoingTypeClause))", messageId.timetamp, messageId.recipientId, Recipient.self().id)
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
        val latestRevisionId: Long? = cursor.requireLongOrNull(LATEST_REVISION_ID)

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
          .where("$ID = ?", latestRevisionId ?: id)
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
    val cursor = readableDatabase
      .select(*MMS_PROJECTION)
      .from(TABLE_NAME)
      .where("$DATE_SENT = ? AND $FROM_RECIPIENT_ID = ?", timestamp, authorId)
      .run()

    return mmsReaderFor(cursor).use { reader ->
      reader.firstOrNull()
    }
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
      .from("$TABLE_NAME INDEXED BY $INDEX_THREAD_STORY_SCHEDULED_DATE_LATEST_REVISION_ID")
      .where("$THREAD_ID = ? AND $STORY_TYPE = ? AND $PARENT_STORY_ID <= ? AND $SCHEDULED_DATE = ? AND $LATEST_REVISION_ID IS NULL", threadId, 0, 0, -1)
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
      .from("$TABLE_NAME INDEXED BY $INDEX_THREAD_STORY_SCHEDULED_DATE_LATEST_REVISION_ID")
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
      .where(
        """
        $NOTIFIED = 0 
        AND $STORY_TYPE = 0 
        AND $LATEST_REVISION_ID IS NULL 
        AND (
          $READ = 0 
          OR $REACTIONS_UNREAD = 1 
          ${if (stickyQuery.isNotEmpty()) "OR ($stickyQuery)" else ""}
          OR ($IS_MISSED_CALL_TYPE_CLAUSE AND EXISTS (SELECT 1 FROM ${CallTable.TABLE_NAME} WHERE ${CallTable.MESSAGE_ID} = $TABLE_NAME.$ID AND ${CallTable.EVENT} = ${CallTable.Event.serialize(CallTable.Event.MISSED)} AND ${CallTable.READ} = 0)) 
        )
        """.trimIndent()
      )
      .orderBy("$DATE_RECEIVED ASC")
      .run()
  }

  fun updatePendingSelfData(placeholder: RecipientId, self: RecipientId) {
    val fromUpdates = writableDatabase
      .update(TABLE_NAME)
      .values(FROM_RECIPIENT_ID to self.serialize())
      .where("$FROM_RECIPIENT_ID = ?", placeholder)
      .run()

    val toUpdates = writableDatabase
      .update(TABLE_NAME)
      .values(TO_RECIPIENT_ID to self.serialize())
      .where("$TO_RECIPIENT_ID = ?", placeholder)
      .run()

    Log.i(TAG, "Updated $fromUpdates FROM_RECIPIENT_ID rows and $toUpdates TO_RECIPIENT_ID rows.")
  }

  private fun getStickyWherePartForParentStoryId(parentStoryId: Long?): String {
    return if (parentStoryId == null) {
      " AND $PARENT_STORY_ID <= 0"
    } else {
      " AND $PARENT_STORY_ID = $parentStoryId"
    }
  }

  override fun remapRecipient(fromId: RecipientId, toId: RecipientId) {
    val fromCount = try {
      writableDatabase
        .update(TABLE_NAME)
        .values(FROM_RECIPIENT_ID to toId.serialize())
        .where("$FROM_RECIPIENT_ID = ?", fromId)
        .run()
    } catch (e: SQLiteException) {
      Log.w(TAG, "Failed to remap fromRecipient, likely causes a unique constraint violation. Fixing.", e)
      val fromIdLong = fromId.toLong()
      val toIdLong = toId.toLong()

      // Looks at all of the messages where the fromRecipient is either fromId or toId, then
      // finds all messages where the threadId and dateSent match, but the fromRecipients do not.
      // Deletes the more recent of the messages to prevent duplicates.
      val deleteCount = writableDatabase
        .delete(TABLE_NAME)
        .where(
          """
          $ID IN (
            SELECT $ID FROM $TABLE_NAME AS m1
            WHERE $FROM_RECIPIENT_ID IN ($fromIdLong, $toIdLong)
            AND EXISTS (
              SELECT 1 FROM $TABLE_NAME AS m2
              WHERE m1.$THREAD_ID = m2.$THREAD_ID
              AND m1.$DATE_SENT = m2.$DATE_SENT
              AND m1.$FROM_RECIPIENT_ID !=  m2.$FROM_RECIPIENT_ID
              AND m1.$ID > m2.$ID
            )
          )
          """
        )
        .run()

      Log.w(TAG, "Deleted $deleteCount duplicates. Retrying the remap.", e)
      writableDatabase
        .update(TABLE_NAME)
        .values(FROM_RECIPIENT_ID to toId.serialize())
        .where("$FROM_RECIPIENT_ID = ?", fromId)
        .run()
    }

    val toCount = writableDatabase
      .update(TABLE_NAME)
      .values(TO_RECIPIENT_ID to toId.serialize())
      .where("$TO_RECIPIENT_ID = ?", fromId)
      .run()

    val quoteAuthorCount = writableDatabase
      .update(TABLE_NAME)
      .values(QUOTE_AUTHOR to toId.serialize())
      .where("$QUOTE_AUTHOR = ?", fromId)
      .run()

    Log.d(TAG, "Remapped $fromId to $toId. fromRecipient: $fromCount, toRecipient: $toCount, quoteAuthor: $quoteAuthorCount")
  }

  override fun remapThread(fromId: Long, toId: Long) {
    try {
      writableDatabase
        .update(TABLE_NAME)
        .values(THREAD_ID to toId)
        .where("$THREAD_ID = ?", fromId)
        .run()
    } catch (e: SQLiteException) {
      Log.w(TAG, "Failed to remap threadId, likely causes a unique constraint violation. Fixing.", e)
      // Looks at all of the messages where the fromRecipient is either fromId or toId, then
      // finds all messages where the threadId and dateSent match, but the fromRecipients do not.
      // Deletes the more recent of the messages to prevent duplicates.
      val deleteCount = writableDatabase
        .delete(TABLE_NAME)
        .where(
          """
          $ID IN (
            SELECT $ID FROM $TABLE_NAME AS m1
            WHERE $THREAD_ID IN ($fromId, $toId)
            AND EXISTS (
              SELECT 1 FROM $TABLE_NAME AS m2
              WHERE m1.$FROM_RECIPIENT_ID = m2.$FROM_RECIPIENT_ID
              AND m1.$DATE_SENT = m2.$DATE_SENT
              AND m1.$THREAD_ID != m2.$THREAD_ID
              AND m1.$ID > m2.$ID
            )
          )
          """
        )
        .run()

      Log.w(TAG, "Deleted $deleteCount duplicates. Retrying the remap.", e)
      writableDatabase
        .update(TABLE_NAME)
        .values(THREAD_ID to toId)
        .where("$THREAD_ID = ?", fromId)
        .run()
    }
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
              bodyRanges[CursorUtil.requireLong(cursor, ID)] = BodyRangeList.ADAPTER.decode(data)
            } catch (e: IOException) {
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

    val releaseChannelRecipientId = SignalStore.releaseChannel.releaseChannelRecipientId ?: return -1L
    return threads.getThreadIdFor(releaseChannelRecipientId) ?: return -1L
  }

  private fun Cursor.toMarkedMessageInfo(outgoing: Boolean): MarkedMessageInfo {
    val recipientColumn = if (outgoing) TO_RECIPIENT_ID else FROM_RECIPIENT_ID
    return MarkedMessageInfo(
      messageId = MessageId(this.requireLong(ID)),
      threadId = this.requireLong(THREAD_ID),
      syncMessageId = SyncMessageId(
        recipientId = RecipientId.from(this.requireLong(recipientColumn)),
        timetamp = this.requireLong(DATE_SENT)
      ),
      expirationInfo = null,
      storyType = StoryType.fromCode(this.requireInt(STORY_TYPE)),
      dateReceived = this.requireLong(DATE_RECEIVED)
    )
  }

  private fun MessageRecord.getOriginalOrOwnMessageId(): MessageId {
    return this.originalMessageId ?: MessageId(this.id)
  }

  /**
   * Determines the database type bitmask for the inbound message.
   */
  @Throws(MmsException::class)
  private fun IncomingMessage.toMessageType(): Long {
    var type = MessageTypes.SECURE_MESSAGE_BIT or MessageTypes.PUSH_MESSAGE_BIT

    if (this.giftBadge != null) {
      type = type or MessageTypes.SPECIAL_TYPE_GIFT_BADGE
    }

    type = type or when (this.type) {
      MessageType.NORMAL -> MessageTypes.BASE_INBOX_TYPE
      MessageType.EXPIRATION_UPDATE -> MessageTypes.EXPIRATION_TIMER_UPDATE_BIT or MessageTypes.BASE_INBOX_TYPE
      MessageType.STORY_REACTION -> MessageTypes.SPECIAL_TYPE_STORY_REACTION or MessageTypes.BASE_INBOX_TYPE
      MessageType.PAYMENTS_NOTIFICATION -> MessageTypes.SPECIAL_TYPE_PAYMENTS_NOTIFICATION or MessageTypes.BASE_INBOX_TYPE
      MessageType.ACTIVATE_PAYMENTS_REQUEST -> MessageTypes.SPECIAL_TYPE_PAYMENTS_ACTIVATE_REQUEST or MessageTypes.BASE_INBOX_TYPE
      MessageType.PAYMENTS_ACTIVATED -> MessageTypes.SPECIAL_TYPE_PAYMENTS_ACTIVATED or MessageTypes.BASE_INBOX_TYPE
      MessageType.CONTACT_JOINED -> MessageTypes.JOINED_TYPE
      MessageType.IDENTITY_UPDATE -> MessageTypes.KEY_EXCHANGE_IDENTITY_UPDATE_BIT or MessageTypes.BASE_INBOX_TYPE
      MessageType.IDENTITY_VERIFIED -> MessageTypes.KEY_EXCHANGE_IDENTITY_VERIFIED_BIT or MessageTypes.BASE_INBOX_TYPE
      MessageType.IDENTITY_DEFAULT -> MessageTypes.KEY_EXCHANGE_IDENTITY_DEFAULT_BIT or MessageTypes.BASE_INBOX_TYPE
      MessageType.END_SESSION -> MessageTypes.END_SESSION_BIT or MessageTypes.BASE_INBOX_TYPE
      MessageType.GROUP_UPDATE -> {
        val isOnlyGroupLeave = this.groupContext?.let { GroupV2UpdateMessageUtil.isJustAGroupLeave(it) } ?: false

        if (isOnlyGroupLeave) {
          MessageTypes.GROUP_V2_BIT or MessageTypes.GROUP_UPDATE_BIT or MessageTypes.GROUP_LEAVE_BIT or MessageTypes.BASE_INBOX_TYPE
        } else {
          MessageTypes.GROUP_V2_BIT or MessageTypes.GROUP_UPDATE_BIT or MessageTypes.BASE_INBOX_TYPE
        }
      }
    }

    return type
  }

  fun threadContainsSms(threadId: Long): Boolean {
    return readableDatabase
      .exists(TABLE_NAME)
      .where(getInsecureMessageClause(threadId))
      .run()
  }

  fun threadContainsAddressableMessages(threadId: Long): Boolean {
    return readableDatabase
      .exists(TABLE_NAME)
      .where("$IS_ADDRESSABLE_CLAUSE AND $THREAD_ID = ?", threadId)
      .run()
  }

  fun threadIsEmpty(threadId: Long): Boolean {
    val hasMessages = readableDatabase
      .exists(TABLE_NAME)
      .where("$THREAD_ID = ?", threadId)
      .run()

    return !hasMessages
  }

  fun getMostRecentReadMessageDateReceived(threadId: Long): Long? {
    return readableDatabase
      .select(DATE_RECEIVED)
      .from("$TABLE_NAME INDEXED BY $INDEX_THREAD_STORY_SCHEDULED_DATE_LATEST_REVISION_ID")
      .where("$THREAD_ID = ? AND $READ = 1", threadId)
      .orderBy("$DATE_RECEIVED DESC")
      .limit(1)
      .run()
      .readToSingleLongOrNull()
  }

  fun getMostRecentAddressableMessages(threadId: Long, excludeExpiring: Boolean): Set<MessageRecord> {
    return readableDatabase
      .select(*MMS_PROJECTION)
      .from(TABLE_NAME)
      .where("$IS_ADDRESSABLE_CLAUSE AND $THREAD_ID = ? ${if (excludeExpiring) "AND $EXPIRES_IN = 0" else ""}", threadId)
      .orderBy("$DATE_RECEIVED DESC")
      .limit(ADDRESSABLE_MESSAGE_LIMIT)
      .run()
      .use {
        MmsReader(it).toSet()
      }
  }

  fun getAddressableMessagesBefore(threadId: Long, beforeTimestamp: Long, excludeExpiring: Boolean): Set<MessageRecord> {
    return readableDatabase
      .select(*MMS_PROJECTION)
      .from(TABLE_NAME)
      .where("$IS_ADDRESSABLE_CLAUSE AND $THREAD_ID = ? AND $DATE_RECEIVED < ? ${if (excludeExpiring) "AND $EXPIRES_IN = 0" else ""}", threadId, beforeTimestamp)
      .orderBy("$DATE_RECEIVED DESC")
      .limit(ADDRESSABLE_MESSAGE_LIMIT)
      .run()
      .use {
        MmsReader(it).toSet()
      }
  }

  protected enum class ReceiptType(val columnName: String, val groupStatus: Int) {
    READ(HAS_READ_RECEIPT, GroupReceiptTable.STATUS_READ),
    DELIVERY(HAS_DELIVERY_RECEIPT, GroupReceiptTable.STATUS_DELIVERED),
    VIEWED(VIEWED_COLUMN, GroupReceiptTable.STATUS_VIEWED)
  }

  data class ReceiptData(
    val messageId: Long,
    val threadId: Long,
    val storyType: StoryType,
    val marked: Boolean,
    val forIndividualChat: Boolean
  )

  data class MessageReceiptStatus(
    val hasReadReceipt: Boolean,
    val hasDeliveryReceipt: Boolean,
    val type: Long
  )

  enum class MessageStatus {
    PENDING,
    SENT,
    DELIVERED,
    READ,
    VIEWED,
    FAILED
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
    val storyType: StoryType,
    val dateReceived: Long
  )

  data class InsertResult(
    val messageId: Long,
    val threadId: Long,
    val threadWasNewlyCreated: Boolean,
    val insertedAttachments: Map<Attachment, AttachmentId>? = null
  )

  data class MessageReceiptUpdate(
    val threadId: Long,
    val messageId: MessageId,
    val shouldUpdateSnippet: Boolean
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
      context = AppDependencies.application
    }

    override fun getNext(): MessageRecord? {
      return if (!cursor.moveToNext()) {
        null
      } else {
        getCurrent()
      }
    }

    override fun getCurrent(): MessageRecord {
      return getMediaMmsMessageRecord(cursor)
    }

    override fun getMessageExportStateForCurrentRecord(): MessageExportState {
      val messageExportState = CursorUtil.requireBlob(cursor, EXPORT_STATE) ?: return MessageExportState()
      return try {
        MessageExportState.ADAPTER.decode(messageExportState)
      } catch (e: IOException) {
        MessageExportState()
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

    private fun getMediaMmsMessageRecord(cursor: Cursor): MmsMessageRecord {
      val id = cursor.requireLong(ID)
      val dateSent = cursor.requireLong(DATE_SENT)
      val dateReceived = cursor.requireLong(DATE_RECEIVED)
      val dateServer = cursor.requireLong(DATE_SERVER)
      val box = cursor.requireLong(TYPE)
      val threadId = cursor.requireLong(THREAD_ID)
      val fromRecipientId = cursor.requireLong(FROM_RECIPIENT_ID)
      val fromDeviceId = cursor.requireInt(FROM_DEVICE_ID)
      val toRecipientId = cursor.requireLong(TO_RECIPIENT_ID)
      val hasDeliveryReceipt = cursor.requireBoolean(HAS_DELIVERY_RECEIPT)
      var hasReadReceipt = cursor.requireBoolean(HAS_READ_RECEIPT)
      val body = cursor.requireString(BODY)
      val mismatchDocument = cursor.requireString(MISMATCHED_IDENTITIES)
      val networkDocument = cursor.requireString(NETWORK_FAILURES)
      val subscriptionId = cursor.requireInt(SMS_SUBSCRIPTION_ID)
      val expiresIn = cursor.requireLong(EXPIRES_IN)
      val expireStarted = cursor.requireLong(EXPIRE_STARTED)
      val expireTimerVersion = cursor.requireInt(EXPIRE_TIMER_VERSION)
      val unidentified = cursor.requireBoolean(UNIDENTIFIED)
      val isViewOnce = cursor.requireBoolean(VIEW_ONCE)
      val remoteDelete = cursor.requireBoolean(REMOTE_DELETED)
      val mentionsSelf = cursor.requireBoolean(MENTIONS_SELF)
      val notifiedTimestamp = cursor.requireLong(NOTIFIED_TIMESTAMP)
      var isViewed = cursor.requireBoolean(VIEWED_COLUMN)
      val receiptTimestamp = cursor.requireLong(RECEIPT_TIMESTAMP)
      val messageRangesData = cursor.requireBlob(MESSAGE_RANGES)
      val storyType = StoryType.fromCode(cursor.requireInt(STORY_TYPE))
      val parentStoryId = ParentStoryId.deserialize(cursor.requireLong(PARENT_STORY_ID))
      val scheduledDate = cursor.requireLong(SCHEDULED_DATE)
      val latestRevisionId: MessageId? = cursor.requireLong(LATEST_REVISION_ID).let { if (it == 0L) null else MessageId(it) }
      val originalMessageId: MessageId? = cursor.requireLong(ORIGINAL_MESSAGE_ID).let { if (it == 0L) null else MessageId(it) }
      val editCount = cursor.requireInt(REVISION_NUMBER)
      val isRead = cursor.requireBoolean(READ)
      val messageExtraBytes = cursor.requireBlob(MESSAGE_EXTRAS)
      val messageExtras = messageExtraBytes?.let { MessageExtras.ADAPTER.decode(it) }

      if (!TextSecurePreferences.isReadReceiptsEnabled(context)) {
        hasReadReceipt = false
        if (MessageTypes.isOutgoingMessageType(box) && !storyType.isStory) {
          isViewed = false
        }
      }

      val fromRecipient = Recipient.live(RecipientId.from(fromRecipientId)).get()
      val toRecipient = Recipient.live(RecipientId.from(toRecipientId)).get()
      val mismatches = getMismatchedIdentities(mismatchDocument)
      val networkFailures = getFailures(networkDocument)

      val attachments = attachments.getAttachments(cursor)

      val contacts = getSharedContacts(cursor, attachments)
      val contactAttachments = contacts.mapNotNull { it.avatarAttachment }.toSet()

      val previews = getLinkPreviews(cursor, attachments)
      val previewAttachments = previews.mapNotNull { it.thumbnail.orElse(null) }.toSet()

      val slideDeck = buildSlideDeck(attachments.filterNot { contactAttachments.contains(it) }.filterNot { previewAttachments.contains(it) })

      val quote = getQuote(cursor)

      val messageRanges: BodyRangeList? = if (messageRangesData != null) {
        try {
          BodyRangeList.ADAPTER.decode(messageRangesData)
        } catch (e: IOException) {
          Log.w(TAG, "Error parsing message ranges", e)
          null
        }
      } else {
        null
      }

      val giftBadge: GiftBadge? = if (body != null && MessageTypes.isGiftBadge(box)) {
        try {
          GiftBadge.ADAPTER.decode(Base64.decode(body))
        } catch (e: IOException) {
          Log.w(TAG, "Error parsing gift badge", e)
          null
        }
      } else {
        null
      }

      return MmsMessageRecord(
        id,
        fromRecipient,
        fromDeviceId,
        toRecipient,
        dateSent,
        dateReceived,
        dateServer,
        hasDeliveryReceipt,
        threadId,
        body,
        slideDeck,
        box,
        mismatches,
        networkFailures,
        subscriptionId,
        expiresIn,
        expireStarted,
        expireTimerVersion,
        isViewOnce,
        hasReadReceipt,
        quote,
        contacts,
        previews,
        unidentified,
        emptyList(),
        remoteDelete,
        mentionsSelf,
        notifiedTimestamp,
        isViewed,
        receiptTimestamp,
        messageRanges,
        storyType,
        parentStoryId,
        giftBadge,
        null,
        null,
        scheduledDate,
        latestRevisionId,
        originalMessageId,
        editCount,
        isRead,
        messageExtras
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
      val quoteAttachments: List<Attachment> = attachments.filter { it.quote }
      val quoteDeck = SlideDeck(quoteAttachments)

      return if (quoteId != QUOTE_NOT_PRESENT_ID && quoteAuthor > 0) {
        if (quoteText != null && (quoteMentions.isNotEmpty() || bodyRanges != null)) {
          val updated: UpdatedBodyAndMentions = MentionUtil.updateBodyAndMentionsWithDisplayNames(context, quoteText, quoteMentions)
          val styledText = SpannableString(updated.body)

          MessageStyler.style(id = "${MessageStyler.QUOTE_ID}$quoteId", messageRanges = bodyRanges.adjustBodyRanges(updated.bodyAdjustments), span = styledText)

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
      fun buildSlideDeck(attachments: List<DatabaseAttachment>): SlideDeck {
        val messageAttachments = attachments
          .filterNot { it.quote }
          .sortedWith(DisplayOrderComparator())

        return SlideDeck(messageAttachments)
      }
    }
  }
}
