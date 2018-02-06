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
public class PinnedMessageAdapter <V extends View & BindableConversationItem> 
extends FastCursorRecyclerViewAdapter<ConversationAdapter.ViewHolder, MessageRecord>
{

  private static final int MAX_CACHE_SIZE = 40;
  private static final String TAG = PinnedMessageAdapter.class.getSimpleName();
  private final Map<String,SoftReference<MessageRecord>> messageRecordCache =
      Collections.synchronizedMap(new LRUCache<String, SoftReference<MessageRecord>>(MAX_CACHE_SIZE));

  private final Set<MessageRecord> batchSelected = Collections.synchronizedSet(new HashSet<MessageRecord>());

  private final @Nullable ItemClickListener clickListener;
  private final @NonNull  MasterSecret      masterSecret;
  private final @NonNull  GlideRequests     glideRequests;
  private final @NonNull  Locale            locale;
  private final @NonNull  Recipient         recipient;
  private final @NonNull  MmsSmsDatabase    db;
  private final @NonNull  LayoutInflater    inflater;
  private final @NonNull  Calendar          calendar;
  private final @NonNull  MessageDigest     digest;

  protected static class ViewHolder extends RecyclerView.ViewHolder {
    public <V extends View & BindableConversationItem> ViewHolder(final @NonNull V itemView) {
      super(itemView);
    }

    @SuppressWarnings("unchecked")
    public <V extends View & BindableConversationItem> V getView() {
      return (V)itemView;
    }
  }


  static class HeaderViewHolder extends RecyclerView.ViewHolder {
    TextView textView;

    HeaderViewHolder(View itemView) {
      super(itemView);
      textView = ViewUtil.findById(itemView, R.id.text);
    }

    HeaderViewHolder(TextView textView) {
      super(textView);
      this.textView = textView;
    }

    public void setText(CharSequence text) {
      textView.setText(text);
    }
  }


  interface ItemClickListener {
    void onItemClick(MessageRecord item);
    void onItemLongClick(MessageRecord item);
  }

  @SuppressWarnings("ConstantConditions")
  @VisibleForTesting
  PinnedMessageAdapter(Context context, Cursor cursor) {
    super(context, cursor);
    try {
      this.masterSecret  = null;
      this.glideRequests = null;
      this.locale        = null;
      this.clickListener = null;
      this.recipient     = null;
      this.inflater      = null;
      this.db            = null;
      this.calendar      = null;
      this.digest        = MessageDigest.getInstance("SHA1");
    } catch (NoSuchAlgorithmException nsae) {
      throw new AssertionError("SHA1 isn't supported!");
    }
  }

  public PinnedMessageAdapter(@NonNull Context context,
                             @NonNull MasterSecret masterSecret,
                             @NonNull GlideRequests glideRequests,
                             @NonNull Locale locale,
                             @Nullable ItemClickListener clickListener,
                             @Nullable Cursor cursor,
                             @NonNull Recipient recipient)
  {
    super(context, cursor);

    try {
      this.masterSecret  = masterSecret;
      this.glideRequests = glideRequests;
      this.locale        = locale;
      this.clickListener = clickListener;
      this.recipient     = recipient;
      this.inflater      = LayoutInflater.from(context);
      this.db            = DatabaseFactory.getMmsSmsDatabase(context);
      this.calendar      = Calendar.getInstance();
      this.digest        = MessageDigest.getInstance("SHA1");

     setHasStableIds(true);
    } catch (NoSuchAlgorithmException nsae) {
      throw new AssertionError("SHA1 isn't supported!");
    }
  }

  @Override
  public ConversationAdapter.ViewHolder onCreateItemViewHolder(ViewGroup parent, int viewType) {
    return null;
  }

  @Override
  protected MessageRecord getRecordFromCursor(@NonNull Cursor cursor) {
    return null;
  }

  @Override
  protected void onBindItemViewHolder(ConversationAdapter.ViewHolder viewHolder, @NonNull MessageRecord record) {

  }

  @Override
  public long getItemId(@NonNull Cursor cursor) {
    String fastPreflightId = cursor.getString(cursor.getColumnIndexOrThrow(AttachmentDatabase.FAST_PREFLIGHT_ID));

    if (fastPreflightId != null) {
      return Long.valueOf(fastPreflightId);
    }

    final String unique = cursor.getString(cursor.getColumnIndexOrThrow(MmsSmsColumns.UNIQUE_ROW_ID));
    final byte[] bytes  = digest.digest(unique.getBytes());
    return Conversions.byteArrayToLong(bytes);
  }

  @Override
  protected long getItemId(@NonNull MessageRecord record) {
    if (record.isOutgoing() && record.isMms()) {
      SlideDeck slideDeck = ((MmsMessageRecord)record).getSlideDeck();

      if (slideDeck.getThumbnailSlide() != null && slideDeck.getThumbnailSlide().getFastPreflightId() != null) {
        return Long.valueOf(slideDeck.getThumbnailSlide().getFastPreflightId());
      }
    }

    return record.getId();
  }

  @Override
  protected int getItemViewType(@NonNull MessageRecord record) {
    return 0;
  }

  @Override
  protected boolean isRecordForId(@NonNull MessageRecord record, long id) {
    return record.getId() == id;
  }

}

