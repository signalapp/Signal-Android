package org.thoughtcrime.securesms.groups.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.LifecycleRecyclerAdapter;
import org.thoughtcrime.securesms.util.LifecycleViewHolder;

import java.util.ArrayList;
import java.util.Collection;

final class GroupMemberListAdapter extends LifecycleRecyclerAdapter<GroupMemberListAdapter.ViewHolder> {

  private static final int FULL_MEMBER                = 0;
  private static final int OWN_INVITE_PENDING         = 1;
  private static final int OTHER_INVITE_PENDING_COUNT = 2;

  private final ArrayList<GroupMemberEntry> data = new ArrayList<>();

  @Nullable private AdminActionsListener adminActionsListener;

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
                                                               parent, false), adminActionsListener);
      case OWN_INVITE_PENDING:
        return new OwnInvitePendingMemberViewHolder(LayoutInflater.from(parent.getContext())
                                                                  .inflate(R.layout.group_recipient_list_item,
                                                                           parent, false), adminActionsListener);
      case OTHER_INVITE_PENDING_COUNT:
        return new UnknownPendingMemberCountViewHolder(LayoutInflater.from(parent.getContext())
                                                                     .inflate(R.layout.group_recipient_list_item,
                                                                              parent, false), adminActionsListener);
      default:
        throw new AssertionError();
    }
  }

  void setAdminActionsListener(@Nullable AdminActionsListener adminActionsListener) {
    this.adminActionsListener = adminActionsListener;
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
    } else if (groupMemberEntry instanceof GroupMemberEntry.PendingMember) {
      return OWN_INVITE_PENDING;
    } else if (groupMemberEntry instanceof GroupMemberEntry.UnknownPendingMemberCount) {
      return OTHER_INVITE_PENDING_COUNT;
    }

    throw new AssertionError();
  }

  @Override
  public int getItemCount() {
    return data.size();
  }

  static abstract class ViewHolder extends LifecycleViewHolder {

                     final Context              context;
             private final AvatarImageView      avatar;
             private final TextView             recipient;
                     final PopupMenuView        popupMenu;
                     final View                 popupMenuContainer;
                     final ProgressBar          busyProgress;
    @Nullable        final AdminActionsListener adminActionsListener;

    ViewHolder(@NonNull View itemView, @Nullable AdminActionsListener adminActionsListener) {
      super(itemView);

      this.context              = itemView.getContext();
      this.avatar               = itemView.findViewById(R.id.recipient_avatar);
      this.recipient            = itemView.findViewById(R.id.recipient_name);
      this.popupMenu            = itemView.findViewById(R.id.popupMenu);
      this.popupMenuContainer   = itemView.findViewById(R.id.popupMenuProgressContainer);
      this.busyProgress         = itemView.findViewById(R.id.menuBusyProgress);
      this.adminActionsListener = adminActionsListener;
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
      busyProgress.setVisibility(View.GONE);
      hideMenu();

      Runnable             onClick         = memberEntry.getOnClick();
      View.OnClickListener onClickListener = v -> { if (onClick != null) onClick.run(); };

      avatar.setOnClickListener(onClickListener);
      recipient.setOnClickListener(onClickListener);

      memberEntry.getBusy().observe(this, busy -> {
        busyProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
        popupMenu.setVisibility(busy ? View.GONE : View.VISIBLE);
      });
    }

    void hideMenu() {
      popupMenuContainer.setVisibility(View.GONE);
      popupMenu.setVisibility(View.GONE);
    }

    void showMenu() {
      popupMenuContainer.setVisibility(View.VISIBLE);
      popupMenu.setVisibility(View.VISIBLE);
    }
  }

  final static class FullMemberViewHolder extends ViewHolder {

    FullMemberViewHolder(@NonNull View itemView, @Nullable AdminActionsListener adminActionsListener) {
      super(itemView, adminActionsListener);
    }

    @Override
    void bind(@NonNull GroupMemberEntry memberEntry) {
      super.bind(memberEntry);

      GroupMemberEntry.FullMember fullMember = (GroupMemberEntry.FullMember) memberEntry;

      bindRecipient(fullMember.getMember());
    }
  }

  final static class OwnInvitePendingMemberViewHolder extends ViewHolder {

    OwnInvitePendingMemberViewHolder(@NonNull View itemView, @Nullable AdminActionsListener adminActionsListener) {
      super(itemView, adminActionsListener);
    }

    @Override
    void bind(@NonNull GroupMemberEntry memberEntry) {
      super.bind(memberEntry);

      GroupMemberEntry.PendingMember pendingMember = (GroupMemberEntry.PendingMember) memberEntry;

      bindRecipient(pendingMember.getInvitee());

      if (pendingMember.isCancellable() && adminActionsListener != null) {
        popupMenu.setMenu(R.menu.own_invite_pending_menu,
                          item -> {
                            if (item == R.id.cancel_invite) {
                              adminActionsListener.onCancelInvite(pendingMember);
                              return true;
                            }
                            return false;
                          });
        showMenu();
      }
    }
  }

  final static class UnknownPendingMemberCountViewHolder extends ViewHolder {

    UnknownPendingMemberCountViewHolder(@NonNull View itemView, @Nullable AdminActionsListener adminActionsListener) {
      super(itemView, adminActionsListener);
    }

    @Override
    void bind(@NonNull GroupMemberEntry memberEntry) {
      super.bind(memberEntry);
      GroupMemberEntry.UnknownPendingMemberCount pendingMembers = (GroupMemberEntry.UnknownPendingMemberCount) memberEntry;

      Recipient inviter     = pendingMembers.getInviter();
      String    displayName = inviter.getDisplayName(itemView.getContext());
      String    displayText = context.getResources().getQuantityString(R.plurals.GroupMemberList_invited,
                                                                       pendingMembers.getInviteCount(),
                                                                       displayName, pendingMembers.getInviteCount());

      bindImageAndText(inviter, displayText);

      if (pendingMembers.isCancellable() && adminActionsListener != null) {
        popupMenu.setMenu(R.menu.others_invite_pending_menu,
                          item -> {
                            if (item.getItemId() == R.id.cancel_invites) {
                              item.setTitle(context.getResources().getQuantityString(R.plurals.PendingMembersActivity_cancel_d_invites, pendingMembers.getInviteCount(),
                                                                                     pendingMembers.getInviteCount()));
                              return true;
                            }
                            return true;
                          },
                          item -> {
                            if (item == R.id.cancel_invites) {
                              adminActionsListener.onCancelAllInvites(pendingMembers);
                              return true;
                            }
                            return false;
                          });
        showMenu();
      }
    }
  }
}
