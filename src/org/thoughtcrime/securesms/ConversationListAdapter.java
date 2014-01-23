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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.CursorAdapter;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.SpamSenderDatabase;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.database.model.ThreadRecord;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.whispersystems.textsecure.crypto.MasterSecret;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * A CursorAdapter for building a list of conversation threads.
 *
 * @author Moxie Marlinspike
 */
public class ConversationListAdapter extends CursorAdapter implements AbsListView.RecyclerListener {

  private final MasterSecret masterSecret;
  private final Context context;
  private final LayoutInflater inflater;

  private final Set<Long> batchSet = Collections.synchronizedSet(new HashSet<Long>());
  private boolean batchMode        = false;
  private boolean batchHasSpam     = false;

  public ConversationListAdapter(Context context, Cursor cursor, MasterSecret masterSecret) {
    super(context, cursor);
    this.masterSecret = masterSecret;
    this.context      = context;
    this.inflater     = LayoutInflater.from(context);
  }

  @Override
  public View newView(Context context, Cursor cursor, ViewGroup parent) {
    return inflater.inflate(R.layout.conversation_list_item_view, parent, false);
  }

  @Override
  public void bindView(View view, Context context, Cursor cursor) {
    if (masterSecret != null) {
      SpamSenderDatabase spamSenderDatabase = DatabaseFactory.getSpamSenderDatabase(context);
      ThreadDatabase.Reader reader          = DatabaseFactory.getThreadDatabase(context).readerFor(cursor, masterSecret);
      ThreadRecord record                   = reader.getCurrent();
      boolean isSpam                        = spamSenderDatabase.isSpamSender(record.getRecipients().getPrimaryRecipient().getNumber());

      ((ConversationListItem)view).set(record, batchSet, batchMode, isSpam);
    }
  }

  public void toggleThreadInBatchSet(long threadId) {
    boolean addToBatch = !batchSet.contains(threadId);
    if (addToBatch) {
      batchSet.add(threadId);
    } else {
      batchSet.remove(threadId);
    }
    if (addToBatch && !batchHasSpam ||
        !addToBatch && batchHasSpam) {
      updateBatchHasSpam();
    }
  }

  private void updateBatchHasSpam() {
    ThreadDatabase threadDatabase = DatabaseFactory.getThreadDatabase(context);
    SpamSenderDatabase spamSenderDatabase = DatabaseFactory.getSpamSenderDatabase(context);
    batchHasSpam = false;
    for (Long threadId : batchSet) {
      Recipients recipients = threadDatabase.getRecipientsForThreadId(threadId);
      batchHasSpam = spamSenderDatabase.isSpamSender(recipients.getPrimaryRecipient().getNumber());
      if (batchHasSpam) {
        break;
      }
    }
  }

  public boolean batchHasSpam() {
    return batchHasSpam;
  }

  public Set<Long> getBatchSelections() {
    return batchSet;
  }

  public void initializeBatchMode(boolean toggle) {
    this.batchMode = toggle;
    this.batchHasSpam = false;
    unselectAllThreads();
  }

  public void unselectAllThreads() {
    this.batchSet.clear();
    this.notifyDataSetChanged();
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

    this.notifyDataSetChanged();
  }

  @Override
  public void onMovedToScrapHeap(View view) {
    ((ConversationListItem)view).unbind();
  }
}
