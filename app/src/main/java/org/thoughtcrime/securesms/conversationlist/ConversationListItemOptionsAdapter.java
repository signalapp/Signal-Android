package org.thoughtcrime.securesms.conversationlist;

import android.content.Context;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.BindableConversationListItem;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.conversationlist.model.Conversation;

import java.util.List;


public class ConversationListItemOptionsAdapter extends ListAdapter<Conversation, RecyclerView.ViewHolder> {

  private static final int MENU_OPTIONS_TYPE = 100;
//  private static final String TAG = Log.tag(ConversationListItemOptionsAdapter.class);;

  private final @NonNull
  LayoutInflater inflater;
  private List<String> mDatas;

  ItemClickListener clickListener;

  protected static class ViewHolder extends RecyclerView.ViewHolder {
    public <V extends View & BindableConversationListItem> ViewHolder(final @NonNull V itemView) {
      super(itemView);
    }

    public BindableConversationListItem getItem() {
      return (BindableConversationListItem) itemView;
    }
  }


  ConversationListItemOptionsAdapter(@NonNull Context context,
                                     @Nullable Cursor cursor,
                                     @Nullable ItemClickListener clickListener) {
    super(new ConversationDiffCallback());
    this.inflater = LayoutInflater.from(context);
    this.clickListener = clickListener;
  }

  @Override
  public @NonNull RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {//    Log.i(TAG,"onCreateItemViewHolder : viewType " + viewType);
    final ConversationListItem item = (ConversationListItem) inflater.inflate(R.layout.conversation_list_item_view,
            parent, false);
    item.setViewType(MENU_OPTIONS_TYPE);
    item.bind(mDatas.get(viewType), false);
    item.setOnClickListener(view -> {
      if (clickListener != null) clickListener.onItemClick(item);
    });

    item.setOnFocusChangeListener(new View.OnFocusChangeListener() {
      @Override
      public void onFocusChange(View v, boolean hasFocus) {
        ((ConversationListItem)v).updateItemParas(hasFocus);
      }
    });


    return new ConversationListItemOptionsAdapter.ViewHolder(item);
  }

  @Override
  public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {

  }

  @Override
  public int getItemViewType(int position) {
    return  position;
  }

  @Override
  public int getItemCount() {
    if (mDatas != null) return mDatas.size();
    return 0;
  }

  public void setData(List<String> Datas){
    mDatas = Datas;
  }

  private static final class ConversationDiffCallback extends DiffUtil.ItemCallback<Conversation> {

    @Override
    public boolean areItemsTheSame(@NonNull Conversation oldItem, @NonNull Conversation newItem) {
      return oldItem.getThreadRecord().getThreadId() == newItem.getThreadRecord().getThreadId();
    }

    @Override
    public boolean areContentsTheSame(@NonNull Conversation oldItem, @NonNull Conversation newItem) {
      return oldItem.equals(newItem);
    }
  }

  interface ItemClickListener {
    void onItemClick(ConversationListItem item);
  }
}
