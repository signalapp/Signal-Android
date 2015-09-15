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
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;

import org.thoughtcrime.securesms.crypto.MasterCipher;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.CursorRecyclerViewAdapter;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.database.model.ThreadRecord;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * A CursorAdapter for building a list of conversation threads.
 *
 * @author Moxie Marlinspike
 */
public class ConversationListAdapter extends CursorRecyclerViewAdapter<ConversationListAdapter.ViewHolder> {

  private final ThreadDatabase    threadDatabase;
  private final MasterCipher      masterCipher;
  private final Locale            locale;
  private final Context           context;
  private final LayoutInflater    inflater;
  private final ItemClickListener clickListener;

  private final Set<Long> batchSet  = Collections.synchronizedSet(new HashSet<Long>());
  private       boolean   batchMode = false;

  protected static class ViewHolder extends RecyclerView.ViewHolder {
    public ViewHolder(final @NonNull ConversationListItem itemView,
                      final @Nullable ItemClickListener clickListener) {
      super(itemView);
      itemView.setOnClickListener(new OnClickListener() {
        @Override
        public void onClick(View view) {
          if (clickListener != null) clickListener.onItemClick(itemView);
        }
      });
      itemView.setOnLongClickListener(new OnLongClickListener() {
        @Override
        public boolean onLongClick(View view) {
          if (clickListener != null) clickListener.onItemLongClick(itemView);
          return true;
        }
      });
    }

    public ConversationListItem getItem() {
      return (ConversationListItem) itemView;
    }
  }

  public ConversationListAdapter(@NonNull Context context,
                                 @NonNull MasterSecret masterSecret,
                                 @NonNull Locale locale,
                                 @Nullable Cursor cursor,
                                 @Nullable ItemClickListener clickListener) {
    super(context, cursor);
    this.masterCipher   = new MasterCipher(masterSecret);
    this.context        = context;
    this.threadDatabase = DatabaseFactory.getThreadDatabase(context);
    this.locale         = locale;
    this.inflater       = LayoutInflater.from(context);
    this.clickListener  = clickListener;
  }

  @Override
  public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    return new ViewHolder((ConversationListItem)inflater.inflate(R.layout.conversation_list_item_view,
                                                                 parent, false), clickListener);
  }

  @Override
  public void onBindViewHolder(ViewHolder viewHolder, @NonNull Cursor cursor) {
    ThreadDatabase.Reader reader = threadDatabase.readerFor(cursor, masterCipher);
    ThreadRecord          record = reader.getCurrent();

    viewHolder.getItem().set(record, locale, batchSet, batchMode);
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
    for (int i = 0; i < getItemCount(); i++) {
      batchSet.add(getItemId(i));
    }
    this.notifyDataSetChanged();
  }

  public interface ItemClickListener {
    void onItemClick(ConversationListItem item);
    void onItemLongClick(ConversationListItem item);
  }
}
