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
package org.thoughtcrime.securesms;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.TextView;

//import org.thoughtcrime.securesms.PinnedMessageAdapter.HeaderViewHolder;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.AttachmentDatabase;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.FastCursorRecyclerViewAdapter;
import org.thoughtcrime.securesms.database.MmsSmsColumns;
import org.thoughtcrime.securesms.database.MmsSmsDatabase;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.MmsMessageRecord;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.Conversions;
import org.thoughtcrime.securesms.util.DateUtils;
import org.thoughtcrime.securesms.util.LRUCache;
import org.thoughtcrime.securesms.util.StickyHeaderDecoration;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;

import java.lang.ref.SoftReference;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class PinnedMessageAdapter extends RecyclerView.Adapter<PinnedMessageAdapter.ViewHolder> {
    Cursor dataCursor;
    Context context;
    MmsSmsDatabase db;
    MasterSecret masterSecret;

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView messageContent;
        public ViewHolder(View v) {
            super(v);
            messageContent = (TextView) v.findViewById(R.id.conversation_item_body);
        }
    }

    public PinnedMessageAdapter(Activity mContext, Cursor cursor, MasterSecret masterSecret) {
        dataCursor = cursor;
        context = mContext;
        db = DatabaseFactory.getMmsSmsDatabase(mContext);
        this.masterSecret = masterSecret;
    }

    @Override
    public PinnedMessageAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        Log.v("pinFragment", "on create view holder");
        View cardview = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.pinned_message_item, parent, false);

        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View theInflatedView = inflater.inflate(R.layout.conversation_item_sent, null);

        return new ViewHolder(theInflatedView);
    }

    public Cursor swapCursor(Cursor cursor) {
        Log.v("pinFragment", "swap cursor");
        if (dataCursor == cursor) {
            return null;
        }
        Cursor oldCursor = dataCursor;
        this.dataCursor = cursor;
        if (cursor != null) {
            this.notifyDataSetChanged();
        }
        return oldCursor;
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        dataCursor.moveToPosition(position);
        Log.v("pinFragment", "onbindViewHolder");
        // Log.v("pinFragment", DatabaseUtils.dumpCursorToString(dataCursor));

        MmsSmsDatabase.Reader reader = db.readerFor(dataCursor, masterSecret);
        MessageRecord record = reader.getCurrent();

        Log.v("pinFragment", record.getDisplayBody().toString());

        holder.messageContent.setText(record.getDisplayBody().toString());
    }

    @Override
    public int getItemCount() {
        return (dataCursor == null) ? 0 : dataCursor.getCount();
    }
}