package org.thoughtcrime.securesms.groups.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.recipients.Recipient;

import java.util.ArrayList;
import java.util.Collection;

final class GroupMemberListAdapter extends RecyclerView.Adapter<GroupMemberListAdapter.ViewHolder> {

  private static final int FULL_MEMBER = 0;

  private final ArrayList<GroupMemberEntry> data = new ArrayList<>();

  void updateData(@NonNull Collection<? extends GroupMemberEntry> recipients) {
    data.clear();
    data.addAll(recipients);
    notifyDataSetChanged();
  }

  @Override
  public @NonNull ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    switch (viewType) {
      case FULL_MEMBER:
        return new FullMemberViewHolder(LayoutInflater.from(parent.getContext())
                                                      .inflate(R.layout.group_recipient_list_item,
                                                               parent, false));
      default:
        throw new AssertionError();
    }
  }

  @Override
  public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
    holder.bind(data.get(position));
  }

  @Override
  public int getItemViewType(int position) {
    GroupMemberEntry groupMemberEntry = data.get(position);

    if (groupMemberEntry instanceof GroupMemberEntry.FullMember) {
      return FULL_MEMBER;
    }

    throw new AssertionError();
  }

  @Override
  public int getItemCount() {
    return data.size();
  }

  static abstract class ViewHolder extends RecyclerView.ViewHolder {

            final Context         context;
    private final AvatarImageView avatar;
    private final TextView        recipient;
            final PopupMenuView   popupMenu;

    ViewHolder(@NonNull View itemView) {
      super(itemView);

      context   = itemView.getContext();
      avatar    = itemView.findViewById(R.id.recipient_avatar);
      recipient = itemView.findViewById(R.id.recipient_name);
      popupMenu = itemView.findViewById(R.id.popupMenu);
    }

    void bindRecipient(@NonNull Recipient recipient) {
      String displayName = recipient.isLocalNumber() ? context.getString(R.string.GroupMembersDialog_you)
                                                     : recipient.getDisplayName(itemView.getContext());
      bindImageAndText(recipient, displayName);
    }

    void bindImageAndText(@NonNull Recipient recipient, @NonNull String displayText) {
      this.recipient.setText(displayText);
      this.avatar.setRecipient(recipient);
    }

    void bind(@NonNull GroupMemberEntry memberEntry) {
      Runnable             onClick         = memberEntry.getOnClick();
      View.OnClickListener onClickListener = v -> { if (onClick != null) onClick.run(); };

      this.avatar.setOnClickListener(onClickListener);
      this.recipient.setOnClickListener(onClickListener);
    }
  }

  final static class FullMemberViewHolder extends ViewHolder {

    FullMemberViewHolder(@NonNull View itemView) {
      super(itemView);
    }

    @Override
    void bind(@NonNull GroupMemberEntry memberEntry) {
      super.bind(memberEntry);

      GroupMemberEntry.FullMember fullMember = (GroupMemberEntry.FullMember) memberEntry;

      bindRecipient(fullMember.getMember());
    }
  }
}
