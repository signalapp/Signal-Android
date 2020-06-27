package org.thoughtcrime.securesms.messagedetails;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.mms.GlideRequests;

import java.util.List;

final class MessageDetailsAdapter extends ListAdapter<MessageDetailsAdapter.MessageDetailsViewState<?>, RecyclerView.ViewHolder> {

  private static final Object EXPIRATION_TIMER_CHANGE_PAYLOAD = new Object();

  private final GlideRequests glideRequests;
  private       boolean       running;

  MessageDetailsAdapter(GlideRequests glideRequests) {
    super(new MessageDetailsDiffer());
    this.glideRequests = glideRequests;
    running            = true;
  }

  @Override
  public @NonNull RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    switch (viewType) {
      case MessageDetailsViewState.MESSAGE_HEADER:
        return new MessageHeaderViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.message_details_header, parent, false), glideRequests);
      case MessageDetailsViewState.RECIPIENT_HEADER:
        return new RecipientHeaderViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.message_details_recipient_header, parent, false));
      case MessageDetailsViewState.RECIPIENT:
        return new RecipientViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.message_details_recipient, parent, false));
      default:
        throw new AssertionError("unknown view type");
    }
  }

  @Override
  public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
    if (holder instanceof MessageHeaderViewHolder) {
      ((MessageHeaderViewHolder) holder).bind((MessageRecord) getItem(position).data, running);
    } else if (holder instanceof RecipientHeaderViewHolder) {
      ((RecipientHeaderViewHolder) holder).bind((RecipientHeader) getItem(position).data);
    } else if (holder instanceof RecipientViewHolder) {
      ((RecipientViewHolder) holder).bind((RecipientDeliveryStatus) getItem(position).data);
    } else {
      throw new AssertionError("unknown view holder");
    }
  }

  @Override
  public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) {
    if (payloads.isEmpty()) {
      super.onBindViewHolder(holder, position, payloads);
    } else if (holder instanceof MessageHeaderViewHolder) {
      ((MessageHeaderViewHolder) holder).partialBind((MessageRecord) getItem(position).data, running);
    }
  }

  @Override
  public int getItemViewType(int position) {
    return getItem(position).itemType;
  }

  void resumeMessageExpirationTimer() {
    running = true;
    if (getItemCount() > 0) {
      notifyItemChanged(0, EXPIRATION_TIMER_CHANGE_PAYLOAD);
    }
  }

  void pauseMessageExpirationTimer() {
    running = false;
    if (getItemCount() > 0) {
      notifyItemChanged(0, EXPIRATION_TIMER_CHANGE_PAYLOAD);
    }
  }

  private static class MessageDetailsDiffer extends DiffUtil.ItemCallback<MessageDetailsViewState<?>> {
    @Override
    public boolean areItemsTheSame(@NonNull MessageDetailsViewState<?> oldItem, @NonNull MessageDetailsViewState<?> newItem) {
      Object oldData = oldItem.data;
      Object newData = newItem.data;

      if (oldData.getClass() == newData.getClass() && oldItem.itemType == newItem.itemType) {
        switch (oldItem.itemType) {
          case MessageDetailsViewState.MESSAGE_HEADER:
            return true;
          case MessageDetailsViewState.RECIPIENT_HEADER:
            return oldData == newData;
          case MessageDetailsViewState.RECIPIENT:
            return ((RecipientDeliveryStatus) oldData).getRecipient().getId().equals(((RecipientDeliveryStatus) newData).getRecipient().getId());
        }
      }

      return false;
    }

    @SuppressLint("DiffUtilEquals")
    @Override
    public boolean areContentsTheSame(@NonNull MessageDetailsViewState<?> oldItem, @NonNull MessageDetailsViewState<?> newItem) {
      Object oldData = oldItem.data;
      Object newData = newItem.data;

      if (oldData.getClass() == newData.getClass() && oldItem.itemType == newItem.itemType) {
        switch (oldItem.itemType) {
          case MessageDetailsViewState.MESSAGE_HEADER:
            return false;
          case MessageDetailsViewState.RECIPIENT_HEADER:
            return true;
          case MessageDetailsViewState.RECIPIENT:
            return ((RecipientDeliveryStatus) oldData).getDeliveryStatus() == ((RecipientDeliveryStatus) newData).getDeliveryStatus();
        }
      }

      return false;
    }
  }

  static final class MessageDetailsViewState<T> {
    public static final int MESSAGE_HEADER   = 0;
    public static final int RECIPIENT_HEADER = 1;
    public static final int RECIPIENT        = 2;

    private final T   data;
    private       int itemType;

    MessageDetailsViewState(T t, int itemType) {
      this.data     = t;
      this.itemType = itemType;
    }
  }
}
