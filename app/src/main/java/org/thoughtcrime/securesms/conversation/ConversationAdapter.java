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
package org.thoughtcrime.securesms.conversation;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.AnyThread;
import androidx.annotation.LayoutRes;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.PagedList;
import androidx.paging.PagedListAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.BindableConversationItem;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.CachedInflater;
import org.thoughtcrime.securesms.util.Conversions;
import org.thoughtcrime.securesms.util.DateUtils;
import org.thoughtcrime.securesms.util.StickyHeaderDecoration;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.whispersystems.libsignal.util.guava.Optional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Adapter that renders a conversation.
 *
 * Important spacial thing to keep in mind: The adapter is intended to be shown on a reversed layout
 * manager, so position 0 is at the bottom of the screen. That's why the "header" is at the bottom,
 * the "footer" is at the top, and we refer to the "next" record as having a lower index.
 */
public class ConversationAdapter<V extends View & BindableConversationItem>
    extends PagedListAdapter<MessageRecord, RecyclerView.ViewHolder>
    implements StickyHeaderDecoration.StickyHeaderAdapter<ConversationAdapter.StickyHeaderViewHolder>
{

  private static final String TAG = Log.tag(ConversationAdapter.class);

  private static final int MESSAGE_TYPE_OUTGOING    = 0;
  private static final int MESSAGE_TYPE_INCOMING    = 1;
  private static final int MESSAGE_TYPE_UPDATE      = 2;
  private static final int MESSAGE_TYPE_HEADER      = 3;
  private static final int MESSAGE_TYPE_FOOTER      = 4;
  private static final int MESSAGE_TYPE_PLACEHOLDER = 5;

  private static final long HEADER_ID   = Long.MIN_VALUE;
  private static final long FOOTER_ID   = Long.MIN_VALUE + 1;

  private final ItemClickListener clickListener;
  private final GlideRequests     glideRequests;
  private final Locale            locale;
  private final Recipient         recipient;

  private final Set<MessageRecord>  selected;
  private final List<MessageRecord> fastRecords;
  private final Set<Long>           releasedFastRecords;
  private final Calendar            calendar;
  private final MessageDigest       digest;

  private String        searchQuery;
  private MessageRecord recordToPulseHighlight;
  private View          headerView;
  private View          footerView;


  ConversationAdapter(@NonNull GlideRequests glideRequests,
                      @NonNull Locale locale,
                      @Nullable ItemClickListener clickListener,
                      @NonNull Recipient recipient)
  {
    super(new DiffCallback());

    this.glideRequests       = glideRequests;
    this.locale              = locale;
    this.clickListener       = clickListener;
    this.recipient           = recipient;
    this.selected            = new HashSet<>();
    this.fastRecords         = new ArrayList<>();
    this.releasedFastRecords = new HashSet<>();
    this.calendar            = Calendar.getInstance();
    this.digest              = getMessageDigestOrThrow();

    setHasStableIds(true);
  }

  @Override
  public int getItemViewType(int position) {
    if (hasHeader() && position == 0) {
      return MESSAGE_TYPE_HEADER;
    }

    if (hasFooter() && position == getItemCount() - 1) {
      return MESSAGE_TYPE_FOOTER;
    }

    MessageRecord messageRecord = getItem(position);

    if (messageRecord == null) {
      return MESSAGE_TYPE_PLACEHOLDER;
    } else if (messageRecord.isUpdate()) {
      return MESSAGE_TYPE_UPDATE;
    } else if (messageRecord.isOutgoing()) {
      return MESSAGE_TYPE_OUTGOING;
    } else {
      return MESSAGE_TYPE_INCOMING;
    }
  }

  @Override
  public long getItemId(int position) {
    if (hasHeader() && position == 0) {
      return HEADER_ID;
    }

    if (hasFooter() && position == getItemCount() - 1) {
      return FOOTER_ID;
    }

    MessageRecord record = getItem(position);

    if (record == null) {
      return -1;
    }

    String unique = (record.isMms() ? "MMS::" : "SMS::") + record.getId();
    byte[] bytes  = digest.digest(unique.getBytes());

    return Conversions.byteArrayToLong(bytes);
  }

  @Override
  public @NonNull RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    switch (viewType) {
      case MESSAGE_TYPE_INCOMING:
      case MESSAGE_TYPE_OUTGOING:
      case MESSAGE_TYPE_UPDATE:
        long start = System.currentTimeMillis();

        V itemView = CachedInflater.from(parent.getContext()).inflate(getLayoutForViewType(viewType), parent, false);

        itemView.setOnClickListener(view -> {
          if (clickListener != null) {
            clickListener.onItemClick(itemView.getMessageRecord());
          }
        });

        itemView.setOnLongClickListener(view -> {
          if (clickListener != null) {
            clickListener.onItemLongClick(itemView, itemView.getMessageRecord());
          }
          return true;
        });

        itemView.setEventListener(clickListener);

        Log.d(TAG, "Inflate time: " + (System.currentTimeMillis() - start));
        return new ConversationViewHolder(itemView);
      case MESSAGE_TYPE_PLACEHOLDER:
        View v = new FrameLayout(parent.getContext());
        v.setLayoutParams(new FrameLayout.LayoutParams(1, ViewUtil.dpToPx(100)));
        return new PlaceholderViewHolder(v);
      case MESSAGE_TYPE_HEADER:
      case MESSAGE_TYPE_FOOTER:
        return new HeaderFooterViewHolder(CachedInflater.from(parent.getContext()).inflate(R.layout.cursor_adapter_header_footer_view, parent, false));
      default:
        throw new IllegalStateException("Cannot create viewholder for type: " + viewType);
    }
  }

  @Override
  public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
    switch (getItemViewType(position)) {
      case MESSAGE_TYPE_INCOMING:
      case MESSAGE_TYPE_OUTGOING:
      case MESSAGE_TYPE_UPDATE:
        ConversationViewHolder conversationViewHolder = (ConversationViewHolder) holder;
        MessageRecord          messageRecord          = Objects.requireNonNull(getItem(position));
        int                    adapterPosition        = holder.getAdapterPosition();

        MessageRecord previousRecord = adapterPosition < getItemCount() - 1  && !isFooterPosition(adapterPosition + 1) ? getItem(adapterPosition + 1) : null;
        MessageRecord nextRecord     = adapterPosition > 0                   && !isHeaderPosition(adapterPosition - 1) ? getItem(adapterPosition - 1) : null;

        conversationViewHolder.getView().bind(messageRecord,
                                              Optional.fromNullable(previousRecord),
                                              Optional.fromNullable(nextRecord),
                                              glideRequests,
                                              locale,
                                              selected,
                                              recipient,
                                              searchQuery,
                                              messageRecord == recordToPulseHighlight);

        if (messageRecord == recordToPulseHighlight) {
          recordToPulseHighlight = null;
        }
        break;
      case MESSAGE_TYPE_HEADER:
        ((HeaderFooterViewHolder) holder).bind(headerView);
        break;
      case MESSAGE_TYPE_FOOTER:
        ((HeaderFooterViewHolder) holder).bind(footerView);
        break;
    }
  }

  @Override
  public void submitList(@Nullable PagedList<MessageRecord> pagedList) {
    cleanFastRecords();
    super.submitList(pagedList, this::notifyDataSetChanged);
  }

  @Override
  protected @Nullable MessageRecord getItem(int position) {
    position = hasHeader() ? position - 1 : position;

    if (position < fastRecords.size()) {
      return fastRecords.get(position);
    } else {
      int correctedPosition = position - fastRecords.size();
      return super.getItem(correctedPosition);
    }
  }

  @Override
  public int getItemCount() {
    boolean hasHeader = headerView != null;
    boolean hasFooter = footerView != null;
    return super.getItemCount() + fastRecords.size() + (hasHeader ? 1 : 0) + (hasFooter ? 1 : 0);
  }

  @Override
  public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
    if (holder instanceof ConversationViewHolder) {
      ((ConversationViewHolder) holder).getView().unbind();
    } else if (holder instanceof HeaderFooterViewHolder) {
      ((HeaderFooterViewHolder) holder).unbind();
    }
  }

  @Override
  public long getHeaderId(int position) {
    if (isHeaderPosition(position)) return -1;
    if (isFooterPosition(position)) return -1;
    if (position >= getItemCount()) return -1;
    if (position < 0)               return -1;

    MessageRecord record = getItem(position);

    if (record == null) return -1;

    calendar.setTime(new Date(record.getDateSent()));
    return Util.hashCode(calendar.get(Calendar.YEAR), calendar.get(Calendar.DAY_OF_YEAR));
  }

  @Override
  public StickyHeaderViewHolder onCreateHeaderViewHolder(ViewGroup parent, int position) {
    return new StickyHeaderViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.conversation_item_header, parent, false));
  }

  @Override
  public void onBindHeaderViewHolder(StickyHeaderViewHolder viewHolder, int position) {
    MessageRecord messageRecord = Objects.requireNonNull(getItem(position));
    viewHolder.setText(DateUtils.getRelativeDate(viewHolder.itemView.getContext(), locale, messageRecord.getDateReceived()));
  }

  void onBindLastSeenViewHolder(StickyHeaderViewHolder viewHolder, int position) {
    viewHolder.setText(viewHolder.itemView.getContext().getResources().getQuantityString(R.plurals.ConversationAdapter_n_unread_messages, (position + 1), (position + 1)));
  }

  /**
   * The presence of a header may throw off the position you'd like to jump to. This will return
   * an adjusted message position based on adapter state.
   */
  @MainThread
  int getAdapterPositionForMessagePosition(int messagePosition) {
    return hasHeader() ? messagePosition + 1 : messagePosition;
  }

  /**
   * Finds the received timestamp for the item at the requested adapter position. Will return 0 if
   * the position doesn't refer to an incoming message.
   */
  @MainThread
  long getReceivedTimestamp(int position) {
    if (isHeaderPosition(position)) return 0;
    if (isFooterPosition(position)) return 0;
    if (position >= getItemCount()) return 0;
    if (position < 0)               return 0;

    MessageRecord messageRecord = getItem(position);

    if (messageRecord == null || messageRecord.isOutgoing()) {
      return 0;
    } else {
      return messageRecord.getDateReceived();
    }
  }

  /**
   * Sets the view the appears at the top of the list (because the list is reversed).
   */
  void setFooterView(View view) {
    this.footerView = view;
  }

  /**
   * Sets the view that appears at the bottom of the list (because the list is reversed).
   */
  void setHeaderView(View view) {
    this.headerView = view;
  }

  /**
   * Returns the header view, if one was set.
   */
  @Nullable View getHeaderView() {
    return headerView;
  }

  /**
   * Momentarily highlights a row at the requested position.
   */
  void pulseHighlightItem(int position) {
    if (position >= 0 && position < getItemCount()) {
      recordToPulseHighlight = getItem(position);
      notifyItemChanged(position);
    }
  }

  /**
   * Conversation search query updated. Allows rendering of text highlighting.
   */
  void onSearchQueryUpdated(String query) {
    this.searchQuery = query;
    notifyDataSetChanged();
  }

  /**
   * Adds a record to a memory cache to allow it to be rendered immediately, as opposed to waiting
   * for a database change.
   */
  @MainThread
  void addFastRecord(MessageRecord record) {
    fastRecords.add(0, record);
    notifyDataSetChanged();
  }

  /**
   * Marks a record as no-longer-needed. Will be removed from the adapter the next time the database
   * changes.
   */
  @AnyThread
  void releaseFastRecord(long id) {
    synchronized (releasedFastRecords) {
      releasedFastRecords.add(id);
    }
  }

  /**
   * Returns set of records that are selected in multi-select mode.
   */
  Set<MessageRecord> getSelectedItems() {
    return new HashSet<>(selected);
  }

  /**
   * Clears all selected records from multi-select mode.
   */
  void clearSelection() {
    selected.clear();
  }

  /**
   * Toggles the selected state of a record in multi-select mode.
   */
  void toggleSelection(MessageRecord record) {
    if (selected.contains(record)) {
      selected.remove(record);
    } else {
      selected.add(record);
    }
  }

  /**
   * Provided a pool, this will initialize it with view counts that make sense.
   */
  @MainThread
  static void initializePool(@NonNull RecyclerView.RecycledViewPool pool) {
    pool.setMaxRecycledViews(MESSAGE_TYPE_INCOMING, 15);
    pool.setMaxRecycledViews(MESSAGE_TYPE_OUTGOING, 15);
    pool.setMaxRecycledViews(MESSAGE_TYPE_PLACEHOLDER, 15);
    pool.setMaxRecycledViews(MESSAGE_TYPE_HEADER, 1);
    pool.setMaxRecycledViews(MESSAGE_TYPE_FOOTER, 1);
    pool.setMaxRecycledViews(MESSAGE_TYPE_UPDATE, 5);
  }

  @MainThread
  private void cleanFastRecords() {
    Util.assertMainThread();

    synchronized (releasedFastRecords) {
      Iterator<MessageRecord> recordIterator = fastRecords.iterator();
      while (recordIterator.hasNext()) {
        long id = recordIterator.next().getId();
        if (releasedFastRecords.contains(id)) {
          recordIterator.remove();
          releasedFastRecords.remove(id);
        }
      }
    }
  }

  private boolean hasHeader() {
    return headerView != null;
  }

  private boolean hasFooter() {
    return footerView != null;
  }

  private boolean isHeaderPosition(int position) {
    return hasHeader() && position == 0;
  }

  private boolean isFooterPosition(int position) {
    return hasFooter() && position == (getItemCount() - 1);
  }

  private @LayoutRes int getLayoutForViewType(int viewType) {
    switch (viewType) {
      case MESSAGE_TYPE_OUTGOING: return R.layout.conversation_item_sent;
      case MESSAGE_TYPE_INCOMING: return R.layout.conversation_item_received;
      case MESSAGE_TYPE_UPDATE:   return R.layout.conversation_item_update;
      default:                    throw new IllegalArgumentException("Unknown type!");
    }
  }

  private static MessageDigest getMessageDigestOrThrow() {
    try {
      return MessageDigest.getInstance("SHA1");
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  static class ConversationViewHolder extends RecyclerView.ViewHolder {
    public <V extends View & BindableConversationItem> ConversationViewHolder(final @NonNull V itemView) {
      super(itemView);
    }

    public <V extends View & BindableConversationItem> V getView() {
      //noinspection unchecked
      return (V)itemView;
    }
  }

  static class StickyHeaderViewHolder extends RecyclerView.ViewHolder {
    TextView textView;

    StickyHeaderViewHolder(View itemView) {
      super(itemView);
      textView = ViewUtil.findById(itemView, R.id.text);
    }

    StickyHeaderViewHolder(TextView textView) {
      super(textView);
      this.textView = textView;
    }

    public void setText(CharSequence text) {
      textView.setText(text);
    }
  }

  private static class HeaderFooterViewHolder extends RecyclerView.ViewHolder {

    private ViewGroup container;

    HeaderFooterViewHolder(@NonNull View itemView) {
      super(itemView);
      this.container = (ViewGroup) itemView;
    }

    void bind(@Nullable View view) {
      unbind();

      if (view != null) {
        container.addView(view);
      }
    }

    void unbind() {
      container.removeAllViews();
    }
  }

  private static class PlaceholderViewHolder extends RecyclerView.ViewHolder {
    PlaceholderViewHolder(@NonNull View itemView) {
      super(itemView);
    }
  }

  private static class DiffCallback extends DiffUtil.ItemCallback<MessageRecord> {
    @Override
    public boolean areItemsTheSame(@NonNull MessageRecord oldItem, @NonNull MessageRecord newItem) {
      return oldItem.isMms() == newItem.isMms() && oldItem.getId() == newItem.getId();
    }

    @Override
    public boolean areContentsTheSame(@NonNull MessageRecord oldItem, @NonNull MessageRecord newItem) {
      // Corner rounding is not part of the model, so we can't use this yet
      return false;
    }
  }

  interface ItemClickListener extends BindableConversationItem.EventListener {
    void onItemClick(MessageRecord item);
    void onItemLongClick(View maskTarget, MessageRecord item);
  }
}
