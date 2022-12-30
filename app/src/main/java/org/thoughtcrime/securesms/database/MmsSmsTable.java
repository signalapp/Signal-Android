/*
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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.database;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.zetetic.database.sqlcipher.SQLiteQueryBuilder;

import org.signal.core.util.SqlUtil;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.model.MessageId;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.notifications.v2.DefaultMessageNotifier;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class MmsSmsTable extends DatabaseTable {

  @SuppressWarnings("unused")
  private static final String TAG = Log.tag(MmsSmsTable.class);

  private static final String[] PROJECTION = { MessageTable.TABLE_NAME + "." + MessageTable.ID + " AS " + MessageTable.ID,
                                               MessageTable.BODY,
                                               MessageTable.TYPE,
                                               MessageTable.THREAD_ID,
                                               MessageTable.RECIPIENT_ID,
                                               MessageTable.RECIPIENT_DEVICE_ID,
                                               MessageTable.DATE_SENT,
                                               MessageTable.DATE_RECEIVED,
                                               MessageTable.DATE_SERVER,
                                               MessageTable.MMS_MESSAGE_TYPE,
                                               MessageTable.UNIDENTIFIED,
                                               MessageTable.MMS_CONTENT_LOCATION,
                                               MessageTable.MMS_TRANSACTION_ID,
                                               MessageTable.MMS_MESSAGE_SIZE,
                                               MessageTable.MMS_EXPIRY,
                                               MessageTable.MMS_STATUS,
                                               MessageTable.DELIVERY_RECEIPT_COUNT,
                                               MessageTable.READ_RECEIPT_COUNT,
                                               MessageTable.MISMATCHED_IDENTITIES,
                                               MessageTable.NETWORK_FAILURES,
                                               MessageTable.SMS_SUBSCRIPTION_ID,
                                               MessageTable.EXPIRES_IN,
                                               MessageTable.EXPIRE_STARTED,
                                               MessageTable.NOTIFIED,
                                               MessageTable.QUOTE_ID,
                                               MessageTable.QUOTE_AUTHOR,
                                               MessageTable.QUOTE_BODY,
                                               MessageTable.QUOTE_MISSING,
                                               MessageTable.QUOTE_TYPE,
                                               MessageTable.QUOTE_MENTIONS,
                                               MessageTable.SHARED_CONTACTS,
                                               MessageTable.LINK_PREVIEWS,
                                               MessageTable.VIEW_ONCE,
                                               MessageTable.READ,
                                               MessageTable.REACTIONS_UNREAD,
                                               MessageTable.REACTIONS_LAST_SEEN,
                                               MessageTable.REMOTE_DELETED,
                                               MessageTable.MENTIONS_SELF,
                                               MessageTable.NOTIFIED_TIMESTAMP,
                                               MessageTable.VIEWED_RECEIPT_COUNT,
                                               MessageTable.RECEIPT_TIMESTAMP,
                                               MessageTable.MESSAGE_RANGES,
                                               MessageTable.STORY_TYPE,
                                               MessageTable.PARENT_STORY_ID};

  public MmsSmsTable(Context context, SignalDatabase databaseHelper) {
    super(context, databaseHelper);
  }

  public @Nullable MessageRecord getMessageFor(long timestamp, RecipientId authorId) {
    Recipient author = Recipient.resolved(authorId);

    try (Cursor cursor = queryTables(PROJECTION, MessageTable.DATE_SENT + " = " + timestamp, null, null, true)) {
      MessageTable.Reader reader = MessageTable.mmsReaderFor(cursor);

      MessageRecord messageRecord;

      while ((messageRecord = reader.getNext()) != null) {
        if ((author.isSelf() && messageRecord.isOutgoing()) ||
            (!author.isSelf() && messageRecord.getIndividualRecipient().getId().equals(authorId)))
        {
          return messageRecord;
        }
      }
    }

    return null;
  }

  public Cursor getConversation(long threadId, long offset, long limit) {
    SQLiteDatabase db        = databaseHelper.getSignalReadableDatabase();
    String         order     = MessageTable.DATE_RECEIVED + " DESC";
    String         selection = MessageTable.THREAD_ID + " = " + threadId + " AND " + MessageTable.STORY_TYPE + " = 0 AND " + MessageTable.PARENT_STORY_ID + " <= 0";
    String         limitStr  = limit > 0 || offset > 0 ? offset + ", " + limit : null;
    String         query     = buildQuery(PROJECTION, selection, order, limitStr, false);

    return db.rawQuery(query, null);
  }

  public Cursor getConversation(long threadId) {
    return getConversation(threadId, 0, 0);
  }
  public Cursor getMessagesForNotificationState(Collection<DefaultMessageNotifier.StickyThread> stickyThreads) {
    StringBuilder stickyQuery = new StringBuilder();
    for (DefaultMessageNotifier.StickyThread stickyThread : stickyThreads) {
      if (stickyQuery.length() > 0) {
        stickyQuery.append(" OR ");
      }
      stickyQuery.append("(")
                 .append(MessageTable.THREAD_ID + " = ")
                 .append(stickyThread.getConversationId().getThreadId())
                 .append(" AND ")
                 .append(MessageTable.DATE_RECEIVED)
                 .append(" >= ")
                 .append(stickyThread.getEarliestTimestamp())
                 .append(getStickyWherePartForParentStoryId(stickyThread.getConversationId().getGroupStoryId()))
                 .append(")");
    }

    String order     = MessageTable.DATE_RECEIVED + " ASC";
    String selection = MessageTable.NOTIFIED + " = 0 AND " + MessageTable.STORY_TYPE + " = 0 AND (" + MessageTable.READ + " = 0 OR " + MessageTable.REACTIONS_UNREAD + " = 1" + (stickyQuery.length() > 0 ? " OR (" + stickyQuery + ")" : "") + ")";

    return queryTables(PROJECTION, selection, order, null, true);
  }

  public List<MessageRecord> getAllMessagesThatQuote(@NonNull MessageId id) {
    MessageRecord targetMessage;
    try {
      targetMessage = SignalDatabase.messages().getMessageRecord(id.getId());
    } catch (NoSuchMessageException e) {
      throw new IllegalArgumentException("Invalid message ID!");
    }

    RecipientId author = targetMessage.isOutgoing() ? Recipient.self().getId() : targetMessage.getRecipient().getId();
    String      query  = MessageTable.QUOTE_ID + " = " + targetMessage.getDateSent() + " AND " + MessageTable.QUOTE_AUTHOR + " = " + author.serialize();
    String      order  = MessageTable.DATE_RECEIVED + " DESC";

    List<MessageRecord> records = new ArrayList<>();

    try (MessageTable.Reader reader = new MessageTable.MmsReader(queryTables(PROJECTION, query, order, null, true))) {
      MessageRecord record;
      while ((record = reader.getNext()) != null) {
        records.add(record);
        records.addAll(getAllMessagesThatQuote(new MessageId(record.getId())));
      }
    }

    Collections.sort(records, (lhs, rhs) -> {
      if (lhs.getDateReceived() > rhs.getDateReceived()) {
        return -1;
      } else if (lhs.getDateReceived() < rhs.getDateReceived()) {
        return 1;
      } else {
        return 0;
      }
    });

    return records;
  }

  private @NonNull String getStickyWherePartForParentStoryId(@Nullable Long parentStoryId) {
    if (parentStoryId == null) {
      return " AND " + MessageTable.PARENT_STORY_ID + " <= 0";
    }

    return " AND " + MessageTable.PARENT_STORY_ID + " = " + parentStoryId;
  }

  private static @NonNull String buildQuery(String[] projection, String selection, String order, String limit, boolean includeAttachments) {
    String attachmentJsonJoin;
    if (includeAttachments) {
      attachmentJsonJoin = "json_group_array(json_object(" + "'" + AttachmentTable.ROW_ID + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.ROW_ID + ", " +
                           "'" + AttachmentTable.UNIQUE_ID + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.UNIQUE_ID + ", " +
                           "'" + AttachmentTable.MMS_ID + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.MMS_ID + "," +
                           "'" + AttachmentTable.SIZE + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.SIZE + ", " +
                           "'" + AttachmentTable.FILE_NAME + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.FILE_NAME + ", " +
                           "'" + AttachmentTable.DATA + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.DATA + ", " +
                           "'" + AttachmentTable.CONTENT_TYPE + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.CONTENT_TYPE + ", " +
                           "'" + AttachmentTable.CDN_NUMBER + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.CDN_NUMBER + ", " +
                           "'" + AttachmentTable.CONTENT_LOCATION + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.CONTENT_LOCATION + ", " +
                           "'" + AttachmentTable.FAST_PREFLIGHT_ID + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.FAST_PREFLIGHT_ID + ", " +
                           "'" + AttachmentTable.VOICE_NOTE + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.VOICE_NOTE + ", " +
                           "'" + AttachmentTable.BORDERLESS + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.BORDERLESS + ", " +
                           "'" + AttachmentTable.VIDEO_GIF + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.VIDEO_GIF + ", " +
                           "'" + AttachmentTable.WIDTH + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.WIDTH + ", " +
                           "'" + AttachmentTable.HEIGHT + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.HEIGHT + ", " +
                           "'" + AttachmentTable.QUOTE + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.QUOTE + ", " +
                           "'" + AttachmentTable.CONTENT_DISPOSITION + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.CONTENT_DISPOSITION + ", " +
                           "'" + AttachmentTable.NAME + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.NAME + ", " +
                           "'" + AttachmentTable.TRANSFER_STATE + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.TRANSFER_STATE + ", " +
                           "'" + AttachmentTable.CAPTION + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.CAPTION + ", " +
                           "'" + AttachmentTable.STICKER_PACK_ID + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.STICKER_PACK_ID + ", " +
                           "'" + AttachmentTable.STICKER_PACK_KEY + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.STICKER_PACK_KEY + ", " +
                           "'" + AttachmentTable.STICKER_ID + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.STICKER_ID + ", " +
                           "'" + AttachmentTable.STICKER_EMOJI + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.STICKER_EMOJI + ", " +
                           "'" + AttachmentTable.VISUAL_HASH + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.VISUAL_HASH + ", " +
                           "'" + AttachmentTable.TRANSFORM_PROPERTIES + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.TRANSFORM_PROPERTIES + ", " +
                           "'" + AttachmentTable.DISPLAY_ORDER + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.DISPLAY_ORDER + ", " +
                           "'" + AttachmentTable.UPLOAD_TIMESTAMP + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.UPLOAD_TIMESTAMP + "))";
    } else {
      attachmentJsonJoin = "NULL";
    }

    projection = SqlUtil.appendArg(projection, attachmentJsonJoin + " AS " + AttachmentTable.ATTACHMENT_JSON_ALIAS);

    SQLiteQueryBuilder mmsQueryBuilder = new SQLiteQueryBuilder();

    if (includeAttachments) {
      mmsQueryBuilder.setDistinct(true);
    }

    if (includeAttachments) {
      mmsQueryBuilder.setTables(MessageTable.TABLE_NAME + " LEFT OUTER JOIN " + AttachmentTable.TABLE_NAME +
                                " ON " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.MMS_ID + " = " + MessageTable.TABLE_NAME + "." + MessageTable.ID);
    } else {
      mmsQueryBuilder.setTables(MessageTable.TABLE_NAME);
    }

    String mmsGroupBy = includeAttachments ? MessageTable.TABLE_NAME + "." + MessageTable.ID : null;

    return mmsQueryBuilder.buildQuery(projection, selection, null, mmsGroupBy, null, order, limit);
  }

  private Cursor queryTables(String[] projection, String selection, String order, String limit, boolean includeAttachments) {
    String query = buildQuery(projection, selection, order, limit, includeAttachments);

    return databaseHelper.getSignalReadableDatabase().rawQuery(query, null);
  }
}
