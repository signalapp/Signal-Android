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

import java.util.LinkedHashMap;

import org.thoughtcrime.securesms.crypto.MasterCipher;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.MessageDisplayHelper;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MessageRecord;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.mms.MmsFactory;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.RecipientFormattingException;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.service.MessageNotifier;

import ws.com.google.android.mms.MmsException;
import android.content.Context;
import android.database.Cursor;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;

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
	
  private boolean dataChanged;
		
  public ConversationAdapter(Recipients recipients, long threadId, Context context, Cursor c, MasterSecret masterSecret, Handler failedIconClickHandler) {
    super(context, c);
    this.context                = context;
    this.recipients             = recipients;
    this.threadId               = threadId;
    this.masterSecret           = masterSecret;
    this.masterCipher           = new MasterCipher(masterSecret);
    this.dataChanged            = false;
    this.failedIconClickHandler = failedIconClickHandler;
    this.messageRecordCache     = initializeCache();
		
    DatabaseFactory.getThreadDatabase(context).setRead(threadId);
    MessageNotifier.updateNotification(context, false);
  }
	
  private Recipient buildRecipient(String address) {
    Recipient recipient;
		
    try {
      if (address == null) recipient = recipients.getPrimaryRecipient();
      else                 recipient = RecipientFactory.getRecipientsFromString(context, address).getPrimaryRecipient();
    } catch (RecipientFormattingException e) {
      Log.w("ConversationAdapter", e);
      recipient = new Recipient("Unknown", "Unknown", null);
    }
		
    return recipient;
  }
	
  @Override
  public void bindView(View view, Context context, Cursor cursor) {
    long id                     = cursor.getLong(cursor.getColumnIndexOrThrow(SmsDatabase.ID));
    String type                 = cursor.getString(cursor.getColumnIndexOrThrow(SmsDatabase.TRANSPORT));
    MessageRecord messageRecord = getMessageRecord(id, cursor, type);
		
    ((ConversationItem)view).set(masterSecret, messageRecord, failedIconClickHandler);		
    view.setOnTouchListener(touchListener);
  }

  @Override
  public View newView(Context context, Cursor cursor, ViewGroup parent) {
    ConversationItem view = new ConversationItem(context);
    bindView(view, context, cursor);

    return view;
  }
	
  private MessageRecord getNewMmsMessageRecord(long messageId, Cursor cursor) {
    MessageRecord messageRecord = getNewSmsMessageRecord(messageId, cursor);
    long mmsType                = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.MESSAGE_TYPE));
    long mmsBox                 = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.MESSAGE_BOX));
		
    try {
      return MmsFactory.getMms(context, masterSecret, messageRecord, mmsType, mmsBox);
    } catch (MmsException me) {
      Log.w("ConversationAdapter", me);
      return messageRecord;
    }
  }
	
  private MessageRecord getNewSmsMessageRecord(long messageId, Cursor cursor) {
    long date                   = cursor.getLong(cursor.getColumnIndexOrThrow(SmsDatabase.DATE));
    long type                   = cursor.getLong(cursor.getColumnIndexOrThrow(SmsDatabase.TYPE));
    String address              = cursor.getString(cursor.getColumnIndexOrThrow(SmsDatabase.ADDRESS));
    Recipient recipient         = buildRecipient(address);
    MessageRecord messageRecord = new MessageRecord(messageId, recipients, date, type, threadId);
	
    messageRecord.setMessageRecipient(recipient);
    setBody(cursor, messageRecord);
		
    return messageRecord;
  }
	
  private MessageRecord getMessageRecord(long messageId, Cursor cursor, String type) {
    if (messageRecordCache.containsKey(type + messageId))
      return messageRecordCache.get(type + messageId);
		
    MessageRecord messageRecord;
		
    if (type.equals("mms"))	messageRecord = getNewMmsMessageRecord(messageId, cursor);
    else					messageRecord = getNewSmsMessageRecord(messageId, cursor);
		
    messageRecordCache.put(type + messageId, messageRecord);		
    return messageRecord;
  }
		
  protected void setBody(Cursor cursor, MessageRecord message) {
    String body = cursor.getString(cursor.getColumnIndexOrThrow(SmsDatabase.BODY));
		
    if (body == null)
      message.setBody("");
    else
      MessageDisplayHelper.setDecryptedMessageBody(body, message, masterCipher);
  }
	
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
