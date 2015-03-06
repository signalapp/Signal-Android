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
import android.support.v4.widget.CursorAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;

import org.thoughtcrime.securesms.ConversationListFragment.ConversationClickListener;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.database.model.ThreadRecord;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * A CursorAdapter for building a list of conversation threads.
 *
 * @author Moxie Marlinspike
 */
public class ConversationListAdapter extends CursorAdapter implements AbsListView.RecyclerListener {

  private static final int VIEW_TYPE_BASE               = 0;
  private static final int VIEW_TYPE_WITH_MEDIA_PREVIEW = 1;

  private final ThreadDatabase            threadDatabase;
  private final MasterSecret              masterSecret;
  private final Context                   context;
  private final ConversationClickListener clickListener;
  private final LayoutInflater            inflater;

  private final Set<Long> batchSet  = Collections.synchronizedSet(new HashSet<Long>());
  private       boolean   batchMode = false;

  public ConversationListAdapter(Context context, Cursor cursor, MasterSecret masterSecret,
                                 ConversationClickListener clickListener)
  {
    super(context, cursor, 0);

    this.masterSecret   = masterSecret;
    this.context        = context;
    this.clickListener  = clickListener;
    this.threadDatabase = DatabaseFactory.getThreadDatabase(context);
    this.inflater       = LayoutInflater.from(context);
  }

  @Override
  public View newView(Context context, Cursor cursor, ViewGroup parent) {
    switch (getItemViewType(cursor)) {
      case VIEW_TYPE_BASE:
        return inflater.inflate(R.layout.conversation_list_item_view, parent, false);

      case VIEW_TYPE_WITH_MEDIA_PREVIEW:
        return inflater.inflate(R.layout.conversation_list_item_with_media_preview_view, parent, false);

      default:
        throw new IllegalArgumentException("unsupported item view type given to ConversationListAdapter");
    }
  }

  @Override
  public void bindView(View view, Context context, Cursor cursor) {
    if (masterSecret != null) {
      ThreadDatabase.Reader reader = threadDatabase.readerFor(cursor, masterSecret);
      ThreadRecord          record = reader.getCurrent();

      ((ConversationListItem)view).set(masterSecret, record, batchSet, clickListener, batchMode);
    }
  }

  @Override
  public int getViewTypeCount() {
    return 2;
  }

  private int getItemViewType(Cursor cursor) {
    if (masterSecret != null) {
      ThreadDatabase.Reader reader = threadDatabase.readerFor(cursor, masterSecret);
      ThreadRecord          record = reader.getCurrent();

      if (record.getSnippetSlide() != null) {
        return VIEW_TYPE_WITH_MEDIA_PREVIEW;
      }
    }

    return VIEW_TYPE_BASE;
  }

  @Override
  public int getItemViewType(int position) {
    Cursor cursor = (Cursor)getItem(position);
    return getItemViewType(cursor);
  }

  public void toggleThreadInBatchSet(long threadId) {
    if (batchSet.contains(threadId)) {
      batchSet.remove(threadId);
    } else {
      batchSet.add(threadId);
    }
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
