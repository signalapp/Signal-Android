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

import android.content.Context;
import android.database.Cursor;
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

import org.thoughtcrime.securesms.PinnedMessageAdapter.HeaderViewHolder;
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

/**
 * A cursor adapter for a pinned messages thread.  Ultimately
 * used by ComposeMessageActivity to display a conversation
 * thread in a ListActivity.
 *
 * @author Daniel Yoo
 *
 */
public class PinnedMessageAdapterTest extends RecyclerView.Adapter<PinnedMessageAdapterTest .ViewHolder> {

    private List<PinnedMessageItem> listPinnedMessages;
    private PinnedMessageFragment context;

    public PinnedMessageAdapterTest(List<PinnedMessageItem> listPinnedMessages, PinnedMessageFragment context) {
        this.listPinnedMessages = listPinnedMessages;
        this.context = context;
    }

    @Override
    public PinnedMessageAdapterTest.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
        .inflate(R.layout.pinned_message_item, parent, false);
        
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(PinnedMessageAdapterTest.ViewHolder holder, int position) {
        PinnedMessageItem pinnedMessage = listPinnedMessages.get(position);
        holder.messageContent.setText(pinnedMessage.getMessageContent());
    }

    @Override
    public int getItemCount() {
        return listPinnedMessages.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public TextView messageContent;

        public ViewHolder(View itemView) {
            super(itemView);

            messageContent = (TextView) itemView.findViewById(R.id.messageContent);
        }
    }
}


