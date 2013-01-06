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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms;

import android.content.Context;
import android.database.Cursor;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;

import org.thoughtcrime.securesms.crypto.MasterCipher;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.MessageDisplayHelper;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.MmsSmsDatabase;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.MessageRecord.GroupData;
import org.thoughtcrime.securesms.database.model.NotificationMmsMessageRecord;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.RecipientFormattingException;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.service.MessageNotifier;
import org.thoughtcrime.securesms.util.InvalidMessageException;

import ws.com.google.android.mms.MmsException;
import ws.com.google.android.mms.pdu.MultimediaMessagePdu;
import ws.com.google.android.mms.pdu.NotificationInd;
import ws.com.google.android.mms.pdu.PduHeaders;

import java.util.LinkedHashMap;

/**
 * A cursor adapter for a conversation thread.  Ultimately
 * used by ComposeMessageActivity to display a conversation
 * thread in a ListActivity.
 *
 * @author Moxie Marlinspike
 *
 */
public class ConversationAdapter extends CursorAdapter {

  private static final int MAX_CACHE_SIZE = 40;

  private final TouchListener touchListener = new TouchListener();
  private final LinkedHashMap<String,MessageRecord> messageRecordCache;
  private final Handler failedIconClickHandler;
  private final long threadId;
  private final Context context;
  private final Recipients recipients;
  private final MasterSecret masterSecret;
  private final MasterCipher masterCipher;
  private final LayoutInflater inflater;

  private boolean dataChanged;

  public ConversationAdapter(Recipients recipients, long threadId, Context context,
                             MasterSecret masterSecret, Handler failedIconClickHandler)
  {
    super(context, null);
    this.context                = context;
    this.recipients             = recipients;
    this.threadId               = threadId;
    this.masterSecret           = masterSecret;
    this.masterCipher           = new MasterCipher(masterSecret);
    this.dataChanged            = false;
    this.failedIconClickHandler = failedIconClickHandler;
    this.messageRecordCache     = initializeCache();
    this.inflater               = (LayoutInflater)context
                                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);

    DatabaseFactory.getThreadDatabase(context).setRead(threadId);
    MessageNotifier.updateNotification(context, false);
  }

  @Override
  public void bindView(View view, Context context, Cursor cursor) {
    ConversationItem item       = (ConversationItem)view;
    long id                     = cursor.getLong(cursor.getColumnIndexOrThrow(SmsDatabase.ID));
    String type                 = cursor.getString(cursor.getColumnIndexOrThrow(MmsSmsDatabase.TRANSPORT));
    MessageRecord messageRecord = getMessageRecord(id, cursor, type);

    item.set(masterSecret, messageRecord, failedIconClickHandler);

    view.setOnTouchListener(touchListener);
  }

  @Override
  public View newView(Context context, Cursor cursor, ViewGroup parent) {
    View view;

    int type = getItemViewType(cursor);

    if (type == 0) view = inflater.inflate(R.layout.conversation_item_sent, parent, false);
    else           view = inflater.inflate(R.layout.conversation_item_received, parent, false);

    bindView(view, context, cursor);
    return view;
  }

  @Override
  public int getViewTypeCount() {
    return 2;
  }

  @Override
  public int getItemViewType(int position) {
    Cursor cursor = (Cursor)getItem(position);
    return getItemViewType(cursor);
  }

  private int getItemViewType(Cursor cursor) {
    long id                     = cursor.getLong(cursor.getColumnIndexOrThrow(SmsDatabase.ID));
    String type                 = cursor.getString(cursor.getColumnIndexOrThrow(MmsSmsDatabase.TRANSPORT));
    MessageRecord messageRecord = getMessageRecord(id, cursor, type);

    if (messageRecord.isOutgoing()) return 0;
    else                            return 1;
  }

  private MediaMmsMessageRecord getMediaMmsMessageRecord(long messageId, Cursor cursor) {
    long id             = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.ID));
    long dateSent       = cursor.getLong(cursor.getColumnIndexOrThrow(MmsSmsDatabase.DATE_SENT));
    long dateReceived   = cursor.getLong(cursor.getColumnIndexOrThrow(MmsSmsDatabase.DATE_RECEIVED));
    long box            = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.MESSAGE_BOX));
    Recipient recipient = getIndividualRecipientFor(null);
    GroupData groupData = null;

    SlideDeck slideDeck;

    try {
      MultimediaMessagePdu pdu = DatabaseFactory.getEncryptingMmsDatabase(context, masterSecret).getMediaMessage(messageId);
      slideDeck                = new SlideDeck(context, masterSecret, pdu.getBody());

      if (recipients != null && !recipients.isSingleRecipient()) {
        int groupSize       = pdu.getTo().length;
        int groupSent       = MmsDatabase.Types.isFailedMmsBox(box) ? 0 : groupSize;
        int groupSendFailed = groupSize - groupSent;

        if (groupSize <= 1) {
          groupSize       = cursor.getInt(cursor.getColumnIndexOrThrow(MmsSmsDatabase.GROUP_SIZE));
          groupSent       = cursor.getInt(cursor.getColumnIndexOrThrow(MmsSmsDatabase.MMS_GROUP_SENT_COUNT));
          groupSendFailed = cursor.getInt(cursor.getColumnIndexOrThrow(MmsSmsDatabase.MMS_GROUP_SEND_FAILED_COUNT));
        }

        Log.w("ConversationAdapter", "MMS GroupSize: " + groupSize + " , GroupSent: " + groupSent + " , GroupSendFailed: " + groupSendFailed);

        groupData = new MessageRecord.GroupData(groupSize, groupSent, groupSendFailed);
      }
    } catch (MmsException me) {
      Log.w("ConversationAdapter", me);
      slideDeck = null;
    }

    return new MediaMmsMessageRecord(context, id, recipients, recipient,
                                     dateSent, dateReceived, threadId,
                                     slideDeck, box, groupData);
  }

  private NotificationMmsMessageRecord getNotificationMmsMessageRecord(long messageId, Cursor cursor) {
    Recipient recipient = getIndividualRecipientFor(null);
    long id             = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.ID));
    long dateSent       = cursor.getLong(cursor.getColumnIndexOrThrow(MmsSmsDatabase.DATE_SENT));
    long dateReceived   = cursor.getLong(cursor.getColumnIndexOrThrow(MmsSmsDatabase.DATE_RECEIVED));

    NotificationInd notification;

    try {
      notification = DatabaseFactory.getMmsDatabase(context).getNotificationMessage(messageId);
    } catch (MmsException me) {
      Log.w("ConversationAdapter", me);
      notification = new NotificationInd(new PduHeaders());
    }

    return new NotificationMmsMessageRecord(id, recipients, recipient,
                                            dateSent, dateReceived, threadId,
                                            notification.getContentLocation(),
                                            notification.getMessageSize(),
                                            notification.getExpiry(),
                                            notification.getStatus(),
                                            notification.getTransactionId());
  }

  private SmsMessageRecord getSmsMessageRecord(long messageId, Cursor cursor) {
    long dateReceived   = cursor.getLong(cursor.getColumnIndexOrThrow(MmsSmsDatabase.DATE_RECEIVED));
    long dateSent       = cursor.getLong(cursor.getColumnIndexOrThrow(MmsSmsDatabase.DATE_SENT));
    long type           = cursor.getLong(cursor.getColumnIndexOrThrow(SmsDatabase.TYPE));
    String body         = cursor.getString(cursor.getColumnIndexOrThrow(SmsDatabase.BODY));
    String address      = cursor.getString(cursor.getColumnIndexOrThrow(SmsDatabase.ADDRESS));
    Recipient recipient = getIndividualRecipientFor(address);

    MessageRecord.GroupData groupData = null;

    if (recipients != null && !recipients.isSingleRecipient()) {
      int groupSize       = cursor.getInt(cursor.getColumnIndexOrThrow(MmsSmsDatabase.GROUP_SIZE));
      int groupSent       = cursor.getInt(cursor.getColumnIndexOrThrow(MmsSmsDatabase.SMS_GROUP_SENT_COUNT));
      int groupSendFailed = cursor.getInt(cursor.getColumnIndexOrThrow(MmsSmsDatabase.SMS_GROUP_SEND_FAILED_COUNT));

      Log.w("ConversationAdapter", "GroupSize: " + groupSize + " , GroupSent: " + groupSent + " , GroupSendFailed: " + groupSendFailed);

      groupData = new MessageRecord.GroupData(groupSize, groupSent, groupSendFailed);
    }

    SmsMessageRecord messageRecord = new SmsMessageRecord(context, messageId, recipients,
                                                          recipient, dateSent, dateReceived,
                                                          type, threadId, groupData);

    if (body == null) {
      body = "";
    }

    try {
      String decryptedBody = MessageDisplayHelper.getDecryptedMessageBody(masterCipher, body);
      messageRecord.setBody(decryptedBody);
    } catch (InvalidMessageException ime) {
      Log.w("ConversationAdapter", ime);
      messageRecord.setBody(context.getString(R.string.MessageDisplayHelper_decryption_error_local_message_corrupted_mac_doesn_t_match_potential_tampering_question));
      messageRecord.setEmphasis(true);
    }

    return messageRecord;
  }

  private MessageRecord getMessageRecord(long messageId, Cursor cursor, String type) {
    if (messageRecordCache.containsKey(type + messageId))
      return messageRecordCache.get(type + messageId);

    MessageRecord messageRecord;

    if (type.equals("mms")) {
      long mmsType = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.MESSAGE_TYPE));

      if (mmsType == PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND) {
        messageRecord = getNotificationMmsMessageRecord(messageId, cursor);
      } else {
        messageRecord = getMediaMmsMessageRecord(messageId, cursor);
      }
    } else {
      messageRecord = getSmsMessageRecord(messageId, cursor);
    }

    messageRecordCache.put(type + messageId, messageRecord);
    return messageRecord;
  }

  private Recipient getIndividualRecipientFor(String address) {
    Recipient recipient;

    try {
      if (address == null) recipient = recipients.getPrimaryRecipient();
      else                 recipient = RecipientFactory.getRecipientsFromString(context, address, false).getPrimaryRecipient();
    } catch (RecipientFormattingException e) {
      Log.w("ConversationAdapter", e);
      recipient = new Recipient("Unknown", "Unknown", null, null);
    }

    return recipient;
  }
  @Override
  protected void onContentChanged() {
    super.onContentChanged();
    messageRecordCache.clear();
    DatabaseFactory.getThreadDatabase(context).setRead(threadId);
    this.dataChanged = true;
  }

  public void close() {
    this.getCursor().close();
  }

  private class TouchListener implements View.OnTouchListener {
    public boolean onTouch(View v, MotionEvent event) {
      if (ConversationAdapter.this.dataChanged) {
        ConversationAdapter.this.dataChanged = false;
        MessageNotifier.updateNotification(context, false);
      }

      return false;
    }
  }

  private LinkedHashMap<String,MessageRecord> initializeCache() {
    return new LinkedHashMap<String,MessageRecord>() {
      @Override
      protected boolean removeEldestEntry(Entry<String,MessageRecord> eldest) {
        return this.size() > MAX_CACHE_SIZE;
      }
    };
  }


}
