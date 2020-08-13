package org.thoughtcrime.securesms.conversationlist;

import android.view.LayoutInflater;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.PagedList;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.conversationlist.model.Conversation;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.util.adapter.FixedViewsAdapter;
import org.thoughtcrime.securesms.util.adapter.RecyclerViewConcatenateAdapter;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

class CompositeConversationListAdapter extends RecyclerViewConcatenateAdapter {

  private final FixedViewsAdapter       pinnedHeaderAdapter;
  private final ConversationListAdapter pinnedAdapter;
  private final FixedViewsAdapter       unpinnedHeaderAdapter;
  private final ConversationListAdapter unpinnedAdapter;

  CompositeConversationListAdapter(@NonNull RecyclerView rv,
                                   @NonNull GlideRequests glideRequests,
                                   @NonNull ConversationListAdapter.OnConversationClickListener onConversationClickListener)
  {

    TextView pinned   = (TextView) LayoutInflater.from(rv.getContext()).inflate(R.layout.conversation_list_item_header, rv, false);
    TextView unpinned = (TextView) LayoutInflater.from(rv.getContext()).inflate(R.layout.conversation_list_item_header, rv, false);

    pinned.setText(rv.getContext().getString(R.string.conversation_list__pinned));
    unpinned.setText(rv.getContext().getString(R.string.conversation_list__chats));

    this.pinnedHeaderAdapter   = new FixedViewsAdapter(pinned);
    this.pinnedAdapter         = new ConversationListAdapter(glideRequests, onConversationClickListener);
    this.unpinnedHeaderAdapter = new FixedViewsAdapter(unpinned);
    this.unpinnedAdapter       = new ConversationListAdapter(glideRequests, onConversationClickListener);

    pinnedHeaderAdapter.hide();
    unpinnedHeaderAdapter.hide();

    unpinnedAdapter.registerAdapterDataObserver(new UnpinnedAdapterDataObserver());
    pinnedAdapter.registerAdapterDataObserver(new PinnedAdapterDataObserver());

    addAdapter(pinnedHeaderAdapter);
    addAdapter(pinnedAdapter);
    addAdapter(unpinnedHeaderAdapter);
    addAdapter(unpinnedAdapter);
  }

  public void submitPinnedList(@NonNull PagedList<Conversation> pinnedConversations) {
    pinnedAdapter.submitList(pinnedConversations);
  }

  public void submitUnpinnedList(@NonNull PagedList<Conversation> unpinnedConversations) {
    unpinnedAdapter.submitList(unpinnedConversations);
  }

  public void setTypingThreads(@NonNull Set<Long> threads) {
    pinnedAdapter.setTypingThreads(threads);
    unpinnedAdapter.setTypingThreads(threads);
  }

  public @NonNull Set<Long> getBatchSelectionIds() {
    HashSet<Long> hashSet = new HashSet();

    hashSet.addAll(pinnedAdapter.getBatchSelectionIds());
    hashSet.addAll(unpinnedAdapter.getBatchSelectionIds());

    return hashSet;
  }

  public void selectAllThreads() {
    pinnedAdapter.selectAllThreads();
    unpinnedAdapter.selectAllThreads();
  }

  public void updateArchived(int archivedCount) {
    unpinnedAdapter.updateArchived(archivedCount);
  }

  public void toggleConversationInBatchSet(@NonNull Conversation conversation) {
    if (conversation.getThreadRecord().isPinned()) {
      pinnedAdapter.toggleConversationInBatchSet(conversation);
    } else {
      unpinnedAdapter.toggleConversationInBatchSet(conversation);
    }
  }

  public void initializeBatchMode(boolean toggle) {
    pinnedAdapter.initializeBatchMode(toggle);
    unpinnedAdapter.initializeBatchMode(toggle);
  }

  public long getPinnedItemCount() {
    return pinnedAdapter.getItemCount();
  }

  public @NonNull Collection<Conversation> getBatchSelection() {
    Set<Conversation> conversations = new HashSet<>();

    conversations.addAll(pinnedAdapter.getBatchSelection());
    conversations.addAll(unpinnedAdapter.getBatchSelection());

    return conversations;
  }

  private class UnpinnedAdapterDataObserver extends RecyclerView.AdapterDataObserver {
    @Override
    public void onItemRangeRemoved(int positionStart, int itemCount) {
      if (unpinnedAdapter.getItemCount() == 0) {
        unpinnedHeaderAdapter.hide();
      }
    }

    @Override
    public void onItemRangeInserted(int positionStart, int itemCount) {
      if (itemCount > 0 && pinnedAdapter.getItemCount() > 0) {
        unpinnedHeaderAdapter.show();
      }
    }
  }

  private class PinnedAdapterDataObserver extends RecyclerView.AdapterDataObserver {
    @Override
    public void onItemRangeRemoved(int positionStart, int itemCount) {
      if (pinnedAdapter.getItemCount() == 0) {
        pinnedHeaderAdapter.hide();
        unpinnedHeaderAdapter.hide();
      }
    }

    @Override
    public void onItemRangeInserted(int positionStart, int itemCount) {
      if (itemCount > 0) {
        pinnedHeaderAdapter.show();

        if (unpinnedAdapter.getItemCount() > 0) {
          unpinnedHeaderAdapter.show();
        }
      }
    }
  }
}
