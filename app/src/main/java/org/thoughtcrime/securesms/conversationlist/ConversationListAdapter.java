package org.thoughtcrime.securesms.conversationlist;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.signal.core.util.logging.Log;
import org.signal.paging.PagingController;
import org.thoughtcrime.securesms.BindableConversationListItem;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.conversationlist.model.Conversation;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.util.CachedInflater;
import org.thoughtcrime.securesms.util.ViewUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

class ConversationListAdapter extends ListAdapter<Conversation, RecyclerView.ViewHolder> {

  private static final String TAG = Log.tag(ConversationListArchiveFragment.class);

  private static final int TYPE_THREAD      = 1;
  private static final int TYPE_ACTION      = 2;
  private static final int TYPE_PLACEHOLDER = 3;
  private static final int TYPE_HEADER      = 4;
  public static final int MENU_OPTIONS_TYPE = 100;

  private enum Payload {
    TYPING_INDICATOR,
    SELECTION
  }

  private final GlideRequests glideRequests;
  private final OnConversationClickListener onConversationClickListener;
  private final Map<Long, Conversation> batchSet = Collections.synchronizedMap(new LinkedHashMap<>());
  private boolean batchMode = false;
  private final Set<Long> typingSet = new HashSet<>();
  private final Context mContext;

  private PagingController pagingController;

  private List<String> data;
  private boolean hasDivider;
  private int dividerType;
  private ConversationListItem fromText;
  private View.OnKeyListener keyListener;
  private boolean isFromLauncher = false;
  private int originItemCount = 0;

  private Conversation conversation1;

  protected ConversationListAdapter(@NonNull Context context,
                                    @NonNull GlideRequests glideRequests,
                                    @NonNull OnConversationClickListener onConversationClickListener) {
    super(new ConversationDiffCallback());

    this.mContext = context;
    this.glideRequests = glideRequests;
    this.onConversationClickListener = onConversationClickListener;

    this.setHasStableIds(true);
  }

  protected ConversationListAdapter(@NonNull Context context,
                                    @NonNull GlideRequests glideRequests,
                                    @NonNull OnConversationClickListener onConversationClickListener,
                                    @NonNull View.OnKeyListener onKeyListener) {
    super(new ConversationDiffCallback());
      this.mContext = context;
      this.glideRequests = glideRequests;
      this.onConversationClickListener = onConversationClickListener;
      this.keyListener = onKeyListener;
  }

  @Override
  public @NonNull
  RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    Log.v(TAG, "onCreateViewHolder viewType: " + viewType);
    if (viewType == TYPE_ACTION) {
      ConversationViewHolder holder = new ConversationViewHolder(LayoutInflater.from(parent.getContext())
              .inflate(R.layout.conversation_list_item_action, parent, false));

      holder.itemView.setOnClickListener(v -> {
        if (holder.getAdapterPosition() != RecyclerView.NO_POSITION) {
          onConversationClickListener.onShowArchiveClick();
        }
      });

      return holder;
    } else if (viewType == TYPE_THREAD || viewType >= MENU_OPTIONS_TYPE) {
      ConversationViewHolder holder =  new ConversationViewHolder(CachedInflater.from(parent.getContext())
                                                                                .inflate(R.layout.conversation_list_item_view, parent, false));

      ((ConversationListItem)holder.itemView).setViewType(viewType);
      if (viewType >= MENU_OPTIONS_TYPE) {
        if (hasDivider && viewType == dividerType + MENU_OPTIONS_TYPE) {
          ((ConversationListItem)holder.itemView).bind(data.get(viewType - MENU_OPTIONS_TYPE), true);
        } else {
          ((ConversationListItem)holder.itemView).bind(data.get(viewType - MENU_OPTIONS_TYPE), false);
        }
        ((ConversationListItem)holder.itemView).setViewType(MENU_OPTIONS_TYPE);

        TextView menuItem = holder.itemView.findViewById(R.id.conversation_list_item_name);
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) menuItem.getLayoutParams();
        params.rightMargin = 0;
        if (menuItem != null) menuItem.setLayoutParams(params);

      }

      holder.itemView.setOnClickListener(v -> {
        int position = holder.getAdapterPosition();

        if (position != RecyclerView.NO_POSITION) {
          onConversationClickListener.onConversationClick((ConversationListItem)holder.itemView, getItem(position));
        }
      });
      if (viewType == TYPE_THREAD) {
        holder.itemView.setOnLongClickListener(view -> {
          if (onConversationClickListener != null) {
            ConversationListFragment.longClickItemPosition = holder.getAdapterPosition();
            return onConversationClickListener.onConversationLongClick((ConversationListItem)holder.itemView);
          }
          return false;
        });
      }

//      holder.itemView.setOnLongClickListener(v -> {
//        int position = holder.getAdapterPosition();
//
//        if (position != RecyclerView.NO_POSITION) {
//          return onConversationClickListener.onConversationLongClick(getItem(position));
//        }
//
//        return false;
//      });

      holder.itemView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View v, boolean hasFocus) {
          ((ConversationListItem)v).updateItemParas(hasFocus);
          if (hasFocus) {
            setFromText((ConversationListItem)v);
          }
        }
      });
      holder.itemView.setOnKeyListener(keyListener);
      return holder;
    } else if (viewType == TYPE_PLACEHOLDER) {
      View v = new FrameLayout(parent.getContext());
      v.setLayoutParams(new FrameLayout.LayoutParams(1, ViewUtil.dpToPx(100)));
      return new PlaceholderViewHolder(v);
    } else if (viewType == TYPE_HEADER) {
      View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.conversation_list_item_header, parent, false);
      return new HeaderViewHolder(v);
    } else {
      throw new IllegalStateException("Unknown type! " + viewType);
    }
  }

  @Override
  public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) {
    if (payloads.isEmpty()) {
      onBindViewHolder(holder, position);
    } else if (holder instanceof ConversationViewHolder) {
      for (Object payloadObject : payloads) {
        if (payloadObject instanceof Payload) {
          Payload payload = (Payload) payloadObject;

          if (payload == Payload.SELECTION) {
            ((ConversationViewHolder) holder).getConversationListItem().setBatchMode(batchMode);
          } else {
            ((ConversationViewHolder) holder).getConversationListItem().updateTypingIndicator(typingSet);
          }
        }
      }
    }

//    Log.d(TAG, "isFromLauncher : " + isFromLauncher);
    /*if(isFromLauncher() && holder.getAdapterPosition() == 6){
      View item = (ConversationListItem) holder.itemView;
      item.requestFocus();
      item.performClick();
      setFromLauncher(false);
    }*/
  }

  public Conversation getConversation(){
    conversation1 = getItem(6);
    return conversation1;
  }

  public ConversationListItem getFromText() {
    return this.fromText;
  }

  public void setFromText(ConversationListItem item) {
    this.fromText = item;
  }

  @Override
  public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
    if (holder.getItemViewType() == TYPE_ACTION || holder.getItemViewType() == TYPE_THREAD) {
      ConversationViewHolder casted = (ConversationViewHolder) holder;
      Conversation conversation = Objects.requireNonNull(getItem(position));

      casted.getConversationListItem().bind(conversation.getThreadRecord(),
              glideRequests,
              Locale.getDefault(),
              typingSet,
              getBatchSelectionIds(),
              batchMode);
    } else if (holder.getItemViewType() == TYPE_HEADER) {
      HeaderViewHolder casted       = (HeaderViewHolder) holder;
      Conversation     conversation = Objects.requireNonNull(getItem(position));
      switch (conversation.getType()) {
        case PINNED_HEADER:
          casted.headerText.setText(R.string.conversation_list__pinned);
          break;
        case UNPINNED_HEADER:
          casted.headerText.setText(R.string.conversation_list__chats);
          break;
        default:
          throw new IllegalArgumentException();
      }
    }
  }

  @Override
  public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
    if (holder instanceof ConversationViewHolder) {
      ((ConversationViewHolder) holder).getConversationListItem().unbind();
    }
  }

  @Override
  public int getItemCount() {
    originItemCount = super.getItemCount();
    return originItemCount == 0 ? getDataSize() : originItemCount ;
  }

  @Override
  protected Conversation getItem(int position) {
    if (originItemCount == 0 && position < getDataSize()) {
      return null;
    }

    if (pagingController != null) {
      pagingController.onDataNeededAroundIndex(position);
    }

    return super.getItem(position);
  }

  @Override
  public long getItemId(int position) {
    Conversation item = getItem(position);

    if (item == null) {
      return 0;
    }

    switch (item.getType()) {
      case THREAD:          return item.getThreadRecord().getThreadId();
      case PINNED_HEADER:   return -1;
      case UNPINNED_HEADER: return -2;
      case ARCHIVED_FOOTER: return -3;
      default:              throw new AssertionError();
    }
  }

  public void setPagingController(@Nullable PagingController pagingController) {
    this.pagingController = pagingController;
  }

  void setTypingThreads(@NonNull Set<Long> typingThreadSet) {
    this.typingSet.clear();
    this.typingSet.addAll(typingThreadSet);

    notifyItemRangeChanged(0, getItemCount(), Payload.TYPING_INDICATOR);
  }

  void toggleConversationInBatchSet(@NonNull Conversation conversation) {
    if (batchSet.containsKey(conversation.getThreadRecord().getThreadId())) {
      batchSet.remove(conversation.getThreadRecord().getThreadId());
    } else if (conversation.getThreadRecord().getThreadId() != -1) {
      batchSet.put(conversation.getThreadRecord().getThreadId(), conversation);
    }

    notifyItemRangeChanged(0, getItemCount(), Payload.SELECTION);
  }

  Collection<Conversation> getBatchSelection() {
    return batchSet.values();
  }

  @Override
  public int getItemViewType(int position) {
    if (data != null && position < data.size()) {
      return position + MENU_OPTIONS_TYPE;
    } else {
        Conversation conversation = getItem(position);
        if (conversation == null) {
          return TYPE_PLACEHOLDER;
        }
        switch (conversation.getType()) {
          case PINNED_HEADER:
          case UNPINNED_HEADER:
            return TYPE_HEADER;
          case ARCHIVED_FOOTER:
            return TYPE_ACTION;
          case THREAD:
            return TYPE_THREAD;
          default:
            throw new IllegalArgumentException();
        }
    }
  }

  @NonNull Set<Long> getBatchSelectionIds() {
    return batchSet.keySet();
  }

  static final class ConversationViewHolder extends RecyclerView.ViewHolder {

    private final BindableConversationListItem conversationListItem;

    ConversationViewHolder(@NonNull View itemView) {
      super(itemView);

      conversationListItem = (BindableConversationListItem) itemView;
    }

    public BindableConversationListItem getConversationListItem() {
      return conversationListItem;
    }
  }

  private static final class ConversationDiffCallback extends DiffUtil.ItemCallback<Conversation> {

    @Override
    public boolean areItemsTheSame(@NonNull Conversation oldItem, @NonNull Conversation newItem) {
      return oldItem.getThreadRecord().getThreadId() == newItem.getThreadRecord().getThreadId();
    }

    @Override
    public boolean areContentsTheSame(@NonNull Conversation oldItem, @NonNull Conversation newItem) {
      if(oldItem.getThreadRecord().getThreadId() == -1 || newItem.getThreadRecord().getThreadId() == -1) {
        return true;
      }
      return oldItem.equals(newItem);
    }
  }

  private static class PlaceholderViewHolder extends RecyclerView.ViewHolder {
    PlaceholderViewHolder(@NonNull View itemView) {
      super(itemView);
    }
  }

  static class HeaderViewHolder extends RecyclerView.ViewHolder {
    private TextView headerText;

    public HeaderViewHolder(@NonNull View itemView) {
      super(itemView);
      headerText = (TextView) itemView;
    }
  }

  public void setData(List<String> Data){
    data = Data;
  }

  public void setHasDivider(boolean divider, int type) {
    hasDivider = divider;
    dividerType = type;
  }

  public int getDataSize() {
    if (data != null) {
      return data.size();
    }
    return 0;
  }

  public void setFromLauncher(boolean isFromLauncher){
    this.isFromLauncher = isFromLauncher;
  }

  public boolean isFromLauncher(){
    return isFromLauncher;
  }

  interface OnConversationClickListener {
    void onConversationClick(ConversationListItem item, Conversation conversation);
    boolean onConversationLongClick(ConversationListItem item);
    void onShowArchiveClick();
  }
}
