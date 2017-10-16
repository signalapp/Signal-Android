/*
 * Copyright (C) 2014 Open Whisper Systems
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
import android.support.v4.widget.CursorAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;

import org.thoughtcrime.securesms.crypto.MasterCipher;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.database.model.ThreadRecord;
import org.thoughtcrime.securesms.mms.GlideRequests;

/**
 * A CursorAdapter for building a list of open conversations
 *
 * @author Jake McGinty
 */
class ShareListAdapter extends CursorAdapter implements AbsListView.RecyclerListener {

  private final ThreadDatabase threadDatabase;
  private final GlideRequests  glideRequests;
  private final MasterCipher   masterCipher;
  private final LayoutInflater inflater;

  ShareListAdapter(@NonNull Context context, @Nullable MasterSecret masterSecret,
                   @NonNull GlideRequests glideRequests, @Nullable Cursor cursor)
  {
    super(context, cursor, 0);

    if (masterSecret != null) this.masterCipher = new MasterCipher(masterSecret);
    else                      this.masterCipher = null;

    this.glideRequests  = glideRequests;
    this.threadDatabase = DatabaseFactory.getThreadDatabase(context);
    this.inflater       = LayoutInflater.from(context);
  }

  @Override
  public View newView(Context context, Cursor cursor, ViewGroup parent) {
    return inflater.inflate(R.layout.share_list_item_view, parent, false);
  }

  @Override
  public void bindView(View view, Context context, Cursor cursor) {
    if (masterCipher != null) {
      ThreadDatabase.Reader reader = threadDatabase.readerFor(cursor, masterCipher);
      ThreadRecord          record = reader.getCurrent();

      ((ShareListItem)view).set(glideRequests, record);
    }
  }

  @Override
  public void onMovedToScrapHeap(View view) {
    ((ShareListItem)view).unbind();
  }
}
