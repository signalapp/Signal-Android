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
import android.content.Intent;
import android.database.Cursor;
import android.support.v7.widget.RecyclerView;
import android.text.Layout;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsSmsDatabase;
import org.thoughtcrime.securesms.database.model.MessageRecord;

public class PinnedMessageAdapter extends RecyclerView.Adapter<PinnedMessageAdapter.ViewHolder> {
    private Cursor                      dataCursor;
    private Context                     context;
    private MmsSmsDatabase              db;
    private MasterSecret                masterSecret;
    private RecyclerView.Adapter        adapter;
    private View                        view;


    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView messageContent;
        public ViewHolder(View v) {
            super(v);
            messageContent = (TextView) v.findViewById(R.id.pinned_message_body);
        }
    }

    public PinnedMessageAdapter(Activity mContext, Cursor cursor, MasterSecret masterSecret) {
        dataCursor        = cursor;
        context           = mContext;
        db                = DatabaseFactory.getMmsSmsDatabase(mContext);
        this.masterSecret = masterSecret;
        this.adapter      = this;

    }

    @Override
    public PinnedMessageAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        Log.v("pinFragment", "on create view holder");

        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View theInflatedView = inflater.inflate(R.layout.pinned_conversation_item_sent, null);
        this.view = theInflatedView;

        return new ViewHolder(theInflatedView);
    }

    public Cursor swapCursor(Cursor cursor) {
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

        MmsSmsDatabase.Reader reader = db.readerFor(dataCursor, masterSecret);
        MessageRecord record = reader.getCurrent();

        this.setMessageView(record, view);
        holder.messageContent.setText(record.getDisplayBody().toString());
        Button unpinButton = (Button)view.findViewById(R.id.unpin_button);
        unpinButton.setOnClickListener(new View.OnClickListener(){

            @Override
            public void onClick(View v) {
                PinnedMessagesHandler handler = new PinnedMessagesHandler(context);
                handler.handleUnpinMessage(record, DatabaseFactory.getSmsDatabase(context));

                ((ViewGroup)v.getParent().getParent().getParent()).removeAllViews();

            }
        });
    }

    private void setMessageView(MessageRecord record, View view) {
        // To check if the message is incoming
        if(record.isOutgoing()) {
            LinearLayout messageBubbleLayout = (LinearLayout)view.findViewById(R.id.pinned_body_bubble);

            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) messageBubbleLayout.getLayoutParams();
            params.leftMargin = 5; params.rightMargin = 50;
        }
    }

    @Override
    public int getItemCount() {
        return (dataCursor == null) ? 0 : dataCursor.getCount();
    }
}