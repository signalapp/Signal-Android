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
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.CursorAdapter;

import org.thoughtcrime.securesms.util.GroupUtil;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsSmsColumns;
import org.thoughtcrime.securesms.database.MmsSmsDatabase;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.util.LRUCache;
import org.whispersystems.textsecure.push.PushMessageProtos.PushMessageContent.GroupContext;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A cursor adapter for a conversation thread.  Ultimately
 * used by ComposeMessageActivity to display a conversation
 * thread in a ListActivity.
 *
 * @author Moxie Marlinspike, Lukas Barth
 *
 */
public class ConversationAdapter extends ArrayAdapter<MessageRecord> implements AbsListView.RecyclerListener {
  public static final int MESSAGE_TYPE_OUTGOING = 0;
  public static final int MESSAGE_TYPE_INCOMING = 1;
  public static final int MESSAGE_TYPE_GROUP_ACTION = 2;

  private final Handler failedIconClickHandler;
  private final Context context;
  private final MasterSecret masterSecret;
  private final boolean groupThread;
  private final boolean pushDestination;
  private final LayoutInflater inflater;

  public ConversationAdapter(Context context, MasterSecret masterSecret,
                                Handler failedIconClickHandler, boolean groupThread, boolean pushDestination)
  {
    super(context, 0);

    this.context                = context;
    this.masterSecret           = masterSecret;
    this.failedIconClickHandler = failedIconClickHandler;
    this.groupThread            = groupThread;
    this.pushDestination        = pushDestination;
    this.inflater               = (LayoutInflater)context
            .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
  }

  private int getItemViewType(MessageRecord messageRecord) {
    if      (messageRecord.isGroupAction()) return MESSAGE_TYPE_GROUP_ACTION;
    else if (messageRecord.isOutgoing())    return MESSAGE_TYPE_OUTGOING;
    else                                    return MESSAGE_TYPE_INCOMING;
  }

  @Override
  public int getItemViewType(int position) {
    MessageRecord messageRecord = getItem(position);
    return getItemViewType(messageRecord);
  }

  @Override
  public View getView(int position, View convertView, ViewGroup parent) {
    View view;

    MessageRecord messageRecord = getItem(position);

    int type = getItemViewType(messageRecord);

    if (convertView == null) {
      switch (type) {
        case ConversationAdapter.MESSAGE_TYPE_OUTGOING:
          view = inflater.inflate(R.layout.conversation_item_sent, parent, false);
          break;
        case ConversationAdapter.MESSAGE_TYPE_INCOMING:
          view = inflater.inflate(R.layout.conversation_item_received, parent, false);
          break;
        case ConversationAdapter.MESSAGE_TYPE_GROUP_ACTION:
          view = inflater.inflate(R.layout.conversation_item_activity, parent, false);
          break;
        default:
          throw new IllegalArgumentException("unsupported item view type given to ConversationAdapter");
      }
    } else {
      view = convertView;
    }

    ConversationItem item = (ConversationItem)view;
    item.set(masterSecret, messageRecord, failedIconClickHandler, groupThread, pushDestination);

    return view;
  }

  @Override
  public int getViewTypeCount() {
    return 3;
  }

  @Override
  public void onMovedToScrapHeap(View view) {
    ((ConversationItem)view).unbind();
  }

  public Long getOldestDisplayedDate() {
    if (getCount() == 0) {
      return null;
    }

    long oldestDisplayed = getItem(getCount() - 1).getDateSent();

    return oldestDisplayed;
  }

  public void appendMessages(List<MessageRecord> messageRecords) {
    while ((messageRecords.size() > 0) && (getOldestDisplayedDate() != null) &&
            (messageRecords.get(0).getDateSent() > getOldestDisplayedDate())) {
      messageRecords.remove(0);
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
      addAll(messageRecords);
    } else {
      for (MessageRecord messageRecord : messageRecords) {
        add(messageRecord);
      }
    }

    notifyDataSetChanged();
  }
}