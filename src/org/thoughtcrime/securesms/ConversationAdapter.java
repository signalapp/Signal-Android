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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsSmsColumns;
import org.thoughtcrime.securesms.database.MmsSmsDatabase;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.recipients.Recipients;

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

  private final LinkedHashMap<String,MessageRecord> messageRecordCache;
  private final Handler failedIconClickHandler;
  private final Context context;
  private final MasterSecret masterSecret;
  private final LayoutInflater inflater;

  public ConversationAdapter(Recipients recipients, long threadId, Context context,
                             MasterSecret masterSecret, Handler failedIconClickHandler)
  {
    super(context, null);
    this.context                = context;
    this.masterSecret           = masterSecret;
    this.failedIconClickHandler = failedIconClickHandler;
    this.messageRecordCache     = initializeCache();
    this.inflater               = (LayoutInflater)context
                                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
  }

  @Override
  public void bindView(View view, Context context, Cursor cursor) {
    ConversationItem item       = (ConversationItem)view;
    long id                     = cursor.getLong(cursor.getColumnIndexOrThrow(SmsDatabase.ID));
    String type                 = cursor.getString(cursor.getColumnIndexOrThrow(MmsSmsDatabase.TRANSPORT));
    MessageRecord messageRecord = getMessageRecord(id, cursor, type);

    item.set(masterSecret, messageRecord, failedIconClickHandler);
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
    long id                     = cursor.getLong(cursor.getColumnIndexOrThrow(MmsSmsColumns.ID));
    String type                 = cursor.getString(cursor.getColumnIndexOrThrow(MmsSmsDatabase.TRANSPORT));
    MessageRecord messageRecord = getMessageRecord(id, cursor, type);

    if (messageRecord.isOutgoing()) return 0;
    else                            return 1;
  }

  private MessageRecord getMessageRecord(long messageId, Cursor cursor, String type) {
    if (messageRecordCache.containsKey(type + messageId))
      return messageRecordCache.get(type + messageId);

    MmsSmsDatabase.Reader reader = DatabaseFactory.getMmsSmsDatabase(context).readerFor(cursor, masterSecret);

    MessageRecord messageRecord = reader.getCurrent();

    messageRecordCache.put(type + messageId, messageRecord);
    return messageRecord;
  }

  @Override
  protected void onContentChanged() {
    super.onContentChanged();
    messageRecordCache.clear();
  }

  public void close() {
    this.getCursor().close();
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
