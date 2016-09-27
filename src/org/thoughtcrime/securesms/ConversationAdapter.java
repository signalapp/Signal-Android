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
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;

import org.thoughtcrime.redphone.util.Conversions;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.CursorRecyclerViewAdapter;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsSmsColumns;
import org.thoughtcrime.securesms.database.MmsSmsDatabase;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.LRUCache;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.VisibleForTesting;

import java.lang.ref.SoftReference;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A cursor adapter for a conversation thread.  Ultimately
 * used by ComposeMessageActivity to display a conversation
 * thread in a ListActivity.
 *
 * @author Moxie Marlinspike
 *
 */
public class ConversationAdapter <V extends View & BindableConversationItem>
    extends CursorRecyclerViewAdapter<ConversationAdapter.ViewHolder>
{

  private static final int MAX_CACHE_SIZE = 40;
  private final Map<String,SoftReference<MessageRecord>> messageRecordCache =
      Collections.synchronizedMap(new LRUCache<String, SoftReference<MessageRecord>>(MAX_CACHE_SIZE));

  public static final int MESSAGE_TYPE_OUTGOING = 0;
  public static final int MESSAGE_TYPE_INCOMING = 1;
  public static final int MESSAGE_TYPE_UPDATE   = 2;

  private final Set<MessageRecord> batchSelected = Collections.synchronizedSet(new HashSet<MessageRecord>());

  private final @Nullable ItemClickListener clickListener;
  private final @NonNull  MasterSecret      masterSecret;
  private final @NonNull  Locale            locale;
  private final @NonNull  Recipients        recipients;
  private final @NonNull  MmsSmsDatabase    db;
  private final @NonNull  LayoutInflater    inflater;
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

  public interface ItemClickListener {
    void onItemClick(MessageRecord item);
    void onItemLongClick(MessageRecord item);
  }

  @SuppressWarnings("ConstantConditions")
  @VisibleForTesting
  ConversationAdapter(Context context, Cursor cursor) {
    super(context, cursor);
    try {
      this.masterSecret  = null;
      this.locale        = null;
      this.clickListener = null;
      this.recipients    = null;
      this.inflater      = null;
      this.db            = null;
      this.digest        = MessageDigest.getInstance("SHA1");
    } catch (NoSuchAlgorithmException nsae) {
      throw new AssertionError("SHA1 isn't supported!");
    }
  }

  public ConversationAdapter(@NonNull Context context,
                             @NonNull MasterSecret masterSecret,
                             @NonNull Locale locale,
                             @Nullable ItemClickListener clickListener,
                             @Nullable Cursor cursor,
                             @NonNull Recipients recipients)
  {
    super(context, cursor);
    try {
      this.masterSecret  = masterSecret;
      this.locale        = locale;
      this.clickListener = clickListener;
      this.recipients    = recipients;
      this.inflater      = LayoutInflater.from(context);
      this.db            = DatabaseFactory.getMmsSmsDatabase(context);
      this.digest        = MessageDigest.getInstance("SHA1");

      setHasStableIds(true);
    } catch (NoSuchAlgorithmException nsae) {
      throw new AssertionError("SHA1 isn't supported!");
    }
  }

  @Override
  public void changeCursor(Cursor cursor) {
    messageRecordCache.clear();
    super.changeCursor(cursor);
  }

  @Override
  public void onBindItemViewHolder(ViewHolder viewHolder, @NonNull Cursor cursor) {
    long          id            = cursor.getLong(cursor.getColumnIndexOrThrow(SmsDatabase.ID));
    String        type          = cursor.getString(cursor.getColumnIndexOrThrow(MmsSmsDatabase.TRANSPORT));
    MessageRecord messageRecord = getMessageRecord(id, cursor, type);

    viewHolder.getView().bind(masterSecret, messageRecord, locale, batchSelected, recipients);
  }

  @Override
  public ViewHolder onCreateItemViewHolder(ViewGroup parent, int viewType) {
    final V itemView = ViewUtil.inflate(inflater, parent, getLayoutForViewType(viewType));
    itemView.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View view) {
        if (clickListener != null) {
          clickListener.onItemClick(itemView.getMessageRecord());
        }
      }
    });
    itemView.setOnLongClickListener(new OnLongClickListener() {
      @Override
      public boolean onLongClick(View view) {
        if (clickListener != null) {
          clickListener.onItemLongClick(itemView.getMessageRecord());
        }
        return true;
      }
    });

    return new ViewHolder(itemView);
  }

  @Override
  public void onItemViewRecycled(ViewHolder holder) {
    holder.getView().unbind();
  }

  private @LayoutRes int getLayoutForViewType(int viewType) {
    switch (viewType) {
    case ConversationAdapter.MESSAGE_TYPE_OUTGOING: return R.layout.conversation_item_sent;
    case ConversationAdapter.MESSAGE_TYPE_INCOMING: return R.layout.conversation_item_received;
    case ConversationAdapter.MESSAGE_TYPE_UPDATE:   return R.layout.conversation_item_update;
    default: throw new IllegalArgumentException("unsupported item view type given to ConversationAdapter");
    }
  }

  @Override
  public int getItemViewType(@NonNull Cursor cursor) {
    long          id            = cursor.getLong(cursor.getColumnIndexOrThrow(MmsSmsColumns.ID));
    String        type          = cursor.getString(cursor.getColumnIndexOrThrow(MmsSmsDatabase.TRANSPORT));
    MessageRecord messageRecord = getMessageRecord(id, cursor, type);

    if (messageRecord.isGroupAction() || messageRecord.isCallLog() || messageRecord.isJoined() || 
        messageRecord.isExpirationTimerUpdate() || messageRecord.isEndSession() || messageRecord.isIdentityUpdate()) 
   {
      return MESSAGE_TYPE_UPDATE;
    } else if (messageRecord.isOutgoing()) {
      return MESSAGE_TYPE_OUTGOING;
    } else {
      return MESSAGE_TYPE_INCOMING;
    }
  }

  @Override
  public long getItemId(@NonNull Cursor cursor) {
    final String unique = cursor.getString(cursor.getColumnIndexOrThrow(MmsSmsColumns.UNIQUE_ROW_ID));
    final byte[] bytes  = digest.digest(unique.getBytes());
    return Conversions.byteArrayToLong(bytes);
  }

  private MessageRecord getMessageRecord(long messageId, Cursor cursor, String type) {
    final SoftReference<MessageRecord> reference = messageRecordCache.get(type + messageId);
    if (reference != null) {
      final MessageRecord record = reference.get();
      if (record != null) return record;
    }

    final MessageRecord messageRecord = db.readerFor(cursor, masterSecret).getCurrent();
    messageRecordCache.put(type + messageId, new SoftReference<>(messageRecord));

    return messageRecord;
  }

  public void close() {
    getCursor().close();
  }

  public void toggleSelection(MessageRecord messageRecord) {
    if (!batchSelected.remove(messageRecord)) {
      batchSelected.add(messageRecord);
    }
  }

  public void clearSelection() {
    batchSelected.clear();
  }

  public Set<MessageRecord> getSelectedItems() {
    return Collections.unmodifiableSet(new HashSet<>(batchSelected));
  }
}
