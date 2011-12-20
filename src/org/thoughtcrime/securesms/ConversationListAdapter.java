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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MessageRecord;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.protocol.Prefix;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;

import android.content.Context;
import android.database.Cursor;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;

/**
 * A CursorAdapter for building a list of conversation threads.
 * 
 * @author Moxie Marlinspike
 */
public class ConversationListAdapter extends CursorAdapter  {
	
  private final Context context;
	
  private final Set<Long> batchSet = Collections.synchronizedSet(new HashSet<Long>());
  private boolean batchMode        = false;
	
  public ConversationListAdapter(Context context, Cursor cursor) {
    super(context, cursor);
    this.context = context;
  }
	
  @Override
  public View newView(Context context, Cursor cursor, ViewGroup parent) {
    ConversationHeaderView view = new ConversationHeaderView(context, batchSet);
    bindView(view, context, cursor);

    return view;
  }

  @Override
  public void bindView(View view, Context context, Cursor cursor) {
    long threadId         = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.ID));
    String recipientId    = cursor.getString(cursor.getColumnIndexOrThrow(ThreadDatabase.RECIPIENT_IDS));
    Recipients recipients = RecipientFactory.getRecipientsForIds(context, recipientId);
		
    long date             = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.DATE));
    long count            = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.MESSAGE_COUNT));
    long read             = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.READ));
		
    MessageRecord message = new MessageRecord(-1, recipients, date, count, read == 1, threadId);
    setBody(cursor, message);
		
    ((ConversationHeaderView)view).set(message, batchMode);		
  }
	
  protected void filterBody(MessageRecord message, String body) {
    if (body == null) body = "(No subject)";
		
    if (body.startsWith(Prefix.SYMMETRIC_ENCRYPT) || body.startsWith(Prefix.ASYMMETRIC_ENCRYPT) || body.startsWith(Prefix.ASYMMETRIC_LOCAL_ENCRYPT)) {
      message.setBody("Encrypted message, enter passphrase... ");
      message.setEmphasis(true);
    } else if (body.startsWith(Prefix.KEY_EXCHANGE)) {
      message.setBody("Key exchange message...");
      message.setEmphasis(true);
    } else {
      message.setBody(body);
    }
  }

  protected void setBody(Cursor cursor, MessageRecord message) {
    filterBody(message, cursor.getString(cursor.getColumnIndexOrThrow(ThreadDatabase.SNIPPET)));
  }	
	
  public Set<Long> getBatchSelections() {
    return batchSet;
  }
	
  public void initializeBatchMode(boolean toggle) {
    this.batchMode = toggle;
    unselectAllThreads();
  }

  public void unselectAllThreads() {
    this.batchSet.clear();
    this.notifyDataSetInvalidated();
  }
	
  public void selectAllThreads() {
    Cursor cursor = DatabaseFactory.getThreadDatabase(context).getConversationList();
		
    try {
      while (cursor != null && cursor.moveToNext()) {
        this.batchSet.add(cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.ID)));
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }
		
    this.notifyDataSetInvalidated();
  }
}
