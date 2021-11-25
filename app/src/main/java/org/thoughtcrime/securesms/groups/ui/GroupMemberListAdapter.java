package org.thoughtcrime.securesms.groups.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.badges.BadgeImageView;
import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.components.emoji.EmojiTextView;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.Util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class GroupMemberListAdapter extends RecyclerView.Adapter<GroupMemberListAdapter.ViewHolder> {

  private static final int FULL_MEMBER                = 0;
  private static final int OWN_INVITE_PENDING         = 1;
  private static final int OTHER_INVITE_PENDING_COUNT = 2;
  private static final int NEW_GROUP_CANDIDATE        = 3;
  private static final int REQUESTING_MEMBER          = 4;

  private final List<GroupMemberEntry>  data                    = new ArrayList<>();
  private final Set<GroupMemberEntry>   selection               = new HashSet<>();
  private final SelectionChangeListener selectionChangeListener = new SelectionChangeListener();

  private final boolean                 selectable;
  private final LifecycleOwner          lifecycleOwner;

  @Nullable private AdminActionsListener             adminActionsListener;
  @Nullable private RecipientClickListener           recipientClickListener;
  @Nullable private RecipientLongClickListener       recipientLongClickListener;
  @Nullable private RecipientSelectionChangeListener recipientSelectionChangeListener;

  GroupMemberListAdapter(boolean selectable, @NonNull LifecycleOwner lifecycleOwner) {
    this.selectable     = selectable;
    this.lifecycleOwner = lifecycleOwner;
  }

  void updateData(@NonNull List<? extends GroupMemberEntry> recipients) {
    if (data.isEmpty()) {
      data.addAll(recipients);
      notifyDataSetChanged();
    } else {
      DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffCallback(data, recipients));
      data.clear();
      data.addAll(recipients);

      if (!selection.isEmpty()) {
        Set<GroupMemberEntry> newSelection = new HashSet<>();
        for (GroupMemberEntry entry : recipients) {
          if (selection.contains(entry)) {
            newSelection.add(entry);
          }
        }
        selection.clear();
        selection.addAll(newSelection);
        if (recipientSelectionChangeListener != null) {
          recipientSelectionChangeListener.onSelectionChanged(selection);
        }
      }

      diffResult.dispatchUpdatesTo(this);
    }
  }

  @Override
  public void onViewDetachedFromWindow(@NonNull ViewHolder holder) {
    super.onViewDetachedFromWindow(holder);
    holder.unbind();
  }

  @Override
  public @NonNull ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    switch (viewType) {
      case FULL_MEMBER:
        return new FullMemberViewHolder(LayoutInflater.from(parent.getContext())
                                                      .inflate(R.layout.group_recipient_list_item, parent, false),
                                        recipientClickListener,
                                        recipientLongClickListener,
                                        adminActionsListener,
                                        selectionChangeListener,
                                        selectable,
                                        lifecycleOwner);
      case OWN_INVITE_PENDING:
        return new OwnInvitePendingMemberViewHolder(LayoutInflater.from(parent.getContext())
                                                                  .inflate(R.layout.group_recipient_list_item, parent, false),
                                                    recipientClickListener,
                                                    recipientLongClickListener,
                                                    adminActionsListener,
                                                    selectionChangeListener,
                                                    selectable,
                                                    lifecycleOwner);
      case OTHER_INVITE_PENDING_COUNT:
        return new UnknownPendingMemberCountViewHolder(LayoutInflater.from(parent.getContext())
                                                                     .inflate(R.layout.group_recipient_list_item, parent, false),
                                                       adminActionsListener,
                                                       selectionChangeListener,
                                                       selectable,
                                                       lifecycleOwner);
      case NEW_GROUP_CANDIDATE:
        return new NewGroupInviteeViewHolder(LayoutInflater.from(parent.getContext())
                                                           .inflate(R.layout.group_new_candidate_recipient_list_item, parent, false),
                                             recipientClickListener,
                                             recipientLongClickListener,
                                             selectionChangeListener,
                                             selectable,
                                             lifecycleOwner);
      case REQUESTING_MEMBER:
        return new RequestingMemberViewHolder(LayoutInflater.from(parent.getContext())
                                                           .inflate(R.layout.group_recipient_requesting_list_item, parent, false),
                                              recipientClickListener,
                                              recipientLongClickListener,
                                              adminActionsListener,
                                              selectionChangeListener,
                                              selectable,
                                              lifecycleOwner);

      default:
        throw new AssertionError();
    }
  }

  void setAdminActionsListener(@Nullable AdminActionsListener adminActionsListener) {
    this.adminActionsListener = adminActionsListener;
  }

  void setRecipientClickListener(@Nullable RecipientClickListener recipientClickListener) {
    this.recipientClickListener = recipientClickListener;
  }

  void setRecipientLongClickListener(@Nullable RecipientLongClickListener recipientLongClickListener) {
    this.recipientLongClickListener = recipientLongClickListener;
  }

  void setRecipientSelectionChangeListener(@Nullable RecipientSelectionChangeListener recipientSelectionChangeListener) {
    this.recipientSelectionChangeListener = recipientSelectionChangeListener;
  }

  @Override
  public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
    GroupMemberEntry entry = data.get(position);
    holder.bind(entry, selection.contains(entry));
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
    } else if (groupMemberEntry instanceof GroupMemberEntry.NewGroupCandidate) {
      return NEW_GROUP_CANDIDATE;
    } else if (groupMemberEntry instanceof GroupMemberEntry.RequestingMember) {
      return REQUESTING_MEMBER;
    }

    throw new AssertionError();
  }

  @Override
  public int getItemCount() {
    return data.size();
  }

  static abstract class ViewHolder extends RecyclerView.ViewHolder {

              final Context                    context;
              final AvatarImageView            avatar;
              final BadgeImageView             badge;
              final TextView                   recipient;
              final EmojiTextView              about;
              final CheckBox                   selected;
              final PopupMenuView              popupMenu;
              final View                       popupMenuContainer;
              final ProgressBar                busyProgress;
    @Nullable final View                       admin;
              final SelectionChangeListener    selectionChangeListener;
    @Nullable final RecipientClickListener     recipientClickListener;
    @Nullable final AdminActionsListener       adminActionsListener;
    @Nullable final RecipientLongClickListener recipientLongClickListener;
              final boolean                    selectable;
              final LifecycleOwner             lifecycleOwner;
              final Observer<Boolean>          busyObserver;

    Runnable stopListeningToBusyChanges;

    ViewHolder(@NonNull View itemView,
               @Nullable RecipientClickListener recipientClickListener,
               @Nullable RecipientLongClickListener recipientLongClickListener,
               @Nullable AdminActionsListener adminActionsListener,
               @NonNull SelectionChangeListener selectionChangeListener,
               boolean selectable,
               @NonNull LifecycleOwner lifecycleOwner)
    {
      super(itemView);

      this.context                    = itemView.getContext();
      this.avatar                     = itemView.findViewById(R.id.recipient_avatar);
      this.badge                      = itemView.findViewById(R.id.recipient_badge);
      this.recipient                  = itemView.findViewById(R.id.recipient_name);
      this.about                      = itemView.findViewById(R.id.recipient_about);
      this.selected                   = itemView.findViewById(R.id.recipient_selected);
      this.popupMenu                  = itemView.findViewById(R.id.popupMenu);
      this.popupMenuContainer         = itemView.findViewById(R.id.popupMenuProgressContainer);
      this.busyProgress               = itemView.findViewById(R.id.menuBusyProgress);
      this.admin                      = itemView.findViewById(R.id.admin);
      this.recipientClickListener     = recipientClickListener;
      this.recipientLongClickListener = recipientLongClickListener;
      this.adminActionsListener       = adminActionsListener;
      this.selectionChangeListener    = selectionChangeListener;
      this.selectable                 = selectable;
      this.lifecycleOwner             = lifecycleOwner;
      this.busyObserver               = this::onBusyChanged;
    }

    private void onBusyChanged(boolean busy) {
      busyProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
      popupMenu.setVisibility(busy ? View.GONE : View.VISIBLE);
    }

    void unbind() {
      if (stopListeningToBusyChanges != null) {
        stopListeningToBusyChanges.run();
      }
    }

    void bindRecipient(@NonNull Recipient recipient) {
      String displayName = recipient.isSelf() ? context.getString(R.string.GroupMembersDialog_you)
                                              : recipient.getDisplayName(itemView.getContext());
      bindImageAndText(recipient, displayName, recipient.getCombinedAboutAndEmoji());
    }

    void bindImageAndText(@NonNull Recipient recipient, @NonNull String displayText, @Nullable String about) {
      this.recipient.setText(displayText);
      this.avatar.setRecipient(recipient);

      if (this.badge != null) {
        this.badge.setBadgeFromRecipient(recipient);
        this.badge.setClickable(false);
      }

      if (this.about != null) {
        this.about.setText(about);
        this.about.setVisibility(Util.isEmpty(about) ? View.GONE : View.VISIBLE);
      }
    }

    void bindRecipientClick(@NonNull Recipient recipient) {
      if (recipient.equals(Recipient.self())) {
        this.itemView.setEnabled(false);
        return;
      }

      this.itemView.setEnabled(true);
      this.itemView.setOnClickListener(v -> {
        if (getAdapterPosition() != RecyclerView.NO_POSITION) {
          if (recipientClickListener != null) {
            recipientClickListener.onClick(recipient);
          }
          if (selected != null) {
            selectionChangeListener.onSelectionChange(getAdapterPosition(), !selected.isChecked());
          }
        }
      });
      this.itemView.setOnLongClickListener(v -> {
        if (recipientLongClickListener != null && getAdapterPosition() != RecyclerView.NO_POSITION) {
          return recipientLongClickListener.onLongClick(recipient);
        }
        return false;
      });
    }

    void bind(@NonNull GroupMemberEntry memberEntry, boolean isSelected) {
      busyProgress.setVisibility(View.GONE);
      if (admin != null) {
        admin.setVisibility(View.GONE);
      }
      hideMenu();

      itemView.setOnClickListener(null);

      memberEntry.getBusy().observe(lifecycleOwner, busyObserver);
      stopListeningToBusyChanges = () -> memberEntry.getBusy().removeObserver(busyObserver);

      selected.setChecked(isSelected);
      if (!selectable && !isSelected) {
        selected.setVisibility(View.GONE);
      } else {
        selected.setVisibility(View.VISIBLE);
      }
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

    FullMemberViewHolder(@NonNull View itemView,
                         @Nullable RecipientClickListener recipientClickListener,
                         @Nullable RecipientLongClickListener recipientLongClickListener,
                         @Nullable AdminActionsListener adminActionsListener,
                         @NonNull SelectionChangeListener selectionChangeListener,
                         boolean selectable,
                         @NonNull LifecycleOwner lifecycleOwner)
    {
      super(itemView, recipientClickListener, recipientLongClickListener, adminActionsListener, selectionChangeListener, selectable, lifecycleOwner);
    }

    @Override
    void bind(@NonNull GroupMemberEntry memberEntry, boolean isSelected) {
      super.bind(memberEntry, isSelected);

      GroupMemberEntry.FullMember fullMember = (GroupMemberEntry.FullMember) memberEntry;

      bindRecipient(fullMember.getMember());
      bindRecipientClick(fullMember.getMember());
      if (admin != null) {
        admin.setVisibility(fullMember.isAdmin() ? View.VISIBLE : View.INVISIBLE);
      }
    }
  }
  final static class NewGroupInviteeViewHolder extends ViewHolder {

    private final View smsContact;
    private final View smsWarning;

    NewGroupInviteeViewHolder(@NonNull View itemView,
                              @Nullable RecipientClickListener recipientClickListener,
                              @Nullable RecipientLongClickListener recipientLongClickListener,
                              @NonNull SelectionChangeListener selectionChangeListener,
                              boolean selectable,
                              @NonNull LifecycleOwner lifecycleOwner)
    {
      super(itemView, recipientClickListener, recipientLongClickListener, null, selectionChangeListener, selectable, lifecycleOwner);

      smsContact = itemView.findViewById(R.id.sms_contact);
      smsWarning = itemView.findViewById(R.id.sms_warning);
    }

    @Override
    void bind(@NonNull GroupMemberEntry memberEntry, boolean isSelected) {
      GroupMemberEntry.NewGroupCandidate newGroupCandidate = (GroupMemberEntry.NewGroupCandidate) memberEntry;

      bindRecipient(newGroupCandidate.getMember());
      bindRecipientClick(newGroupCandidate.getMember());

      int smsWarningVisibility = newGroupCandidate.getMember().isRegistered() ? View.GONE : View.VISIBLE;

      smsContact.setVisibility(smsWarningVisibility);
      smsWarning.setVisibility(smsWarningVisibility);
    }
  }

  final static class OwnInvitePendingMemberViewHolder extends ViewHolder {

    OwnInvitePendingMemberViewHolder(@NonNull View itemView,
                                     @Nullable RecipientClickListener recipientClickListener,
                                     @Nullable RecipientLongClickListener recipientLongClickListener,
                                     @Nullable AdminActionsListener adminActionsListener,
                                     @NonNull SelectionChangeListener selectionChangeListener,
                                     boolean selectable,
                                     @NonNull LifecycleOwner lifecycleOwner)
    {
      super(itemView, recipientClickListener, recipientLongClickListener, adminActionsListener, selectionChangeListener, selectable, lifecycleOwner);
    }

    @Override
    void bind(@NonNull GroupMemberEntry memberEntry, boolean isSelected) {
      super.bind(memberEntry, isSelected);

      GroupMemberEntry.PendingMember pendingMember = (GroupMemberEntry.PendingMember) memberEntry;

      bindImageAndText(pendingMember.getInvitee(), pendingMember.getInvitee().getDisplayNameOrUsername(context), pendingMember.getInvitee().getCombinedAboutAndEmoji());
      bindRecipientClick(pendingMember.getInvitee());

      if (pendingMember.isCancellable() && adminActionsListener != null) {
        popupMenu.setMenu(R.menu.own_invite_pending_menu,
                          item -> {
                            if (item == R.id.revoke_invite) {
                              adminActionsListener.onRevokeInvite(pendingMember);
                              return true;
                            }
                            return false;
                          });
        showMenu();
      }
    }
  }

  final static class UnknownPendingMemberCountViewHolder extends ViewHolder {

    UnknownPendingMemberCountViewHolder(@NonNull View itemView,
                                        @Nullable AdminActionsListener adminActionsListener,
                                        @NonNull SelectionChangeListener selectionChangeListener,
                                        boolean selectable,
                                        @NonNull LifecycleOwner lifecycleOwner)
    {
      super(itemView, null, null, adminActionsListener, selectionChangeListener, selectable, lifecycleOwner);
    }

    @Override
    void bind(@NonNull GroupMemberEntry memberEntry, boolean isSelected) {
      super.bind(memberEntry, isSelected);
      GroupMemberEntry.UnknownPendingMemberCount pendingMembers = (GroupMemberEntry.UnknownPendingMemberCount) memberEntry;

      Recipient inviter     = pendingMembers.getInviter();
      String    displayName = inviter.getDisplayName(itemView.getContext());
      String    displayText = context.getResources().getQuantityString(R.plurals.GroupMemberList_invited,
                                                                       pendingMembers.getInviteCount(),
                                                                       displayName, pendingMembers.getInviteCount());

      bindImageAndText(inviter, displayText, inviter.getAbout());

      if (pendingMembers.isCancellable() && adminActionsListener != null) {
        popupMenu.setMenu(R.menu.others_invite_pending_menu,
                          item -> {
                            if (item.getItemId() == R.id.revoke_invites) {
                              item.setTitle(context.getResources().getQuantityString(R.plurals.PendingMembersActivity_revoke_d_invites, pendingMembers.getInviteCount(),
                                                                                     pendingMembers.getInviteCount()));
                              return true;
                            }
                            return true;
                          },
                          item -> {
                            if (item == R.id.revoke_invites) {
                              adminActionsListener.onRevokeAllInvites(pendingMembers);
                              return true;
                            }
                            return false;
                          });
        showMenu();
      }
    }
  }

  final static class RequestingMemberViewHolder extends ViewHolder {

    private final View approveRequest;
    private final View denyRequest;

    RequestingMemberViewHolder(@NonNull View itemView,
                               @Nullable RecipientClickListener recipientClickListener,
                               @Nullable RecipientLongClickListener recipientLongClickListener,
                               @Nullable AdminActionsListener adminActionsListener,
                               @NonNull SelectionChangeListener selectionChangeListener,
                               boolean selectable,
                               @NonNull LifecycleOwner lifecycleOwner)
    {
      super(itemView, recipientClickListener, recipientLongClickListener, adminActionsListener, selectionChangeListener, selectable, lifecycleOwner);

      approveRequest = itemView.findViewById(R.id.request_approve);
      denyRequest    = itemView.findViewById(R.id.request_deny);
    }

    @Override
    void bind(@NonNull GroupMemberEntry memberEntry, boolean isSelected) {
      super.bind(memberEntry, isSelected);

      GroupMemberEntry.RequestingMember requestingMember = (GroupMemberEntry.RequestingMember) memberEntry;

      if (adminActionsListener != null && requestingMember.isApprovableDeniable()) {
        approveRequest.setVisibility(View.VISIBLE);
        denyRequest   .setVisibility(View.VISIBLE);
        approveRequest.setOnClickListener(v -> adminActionsListener.onApproveRequest(requestingMember));
        denyRequest   .setOnClickListener(v -> adminActionsListener.onDenyRequest   (requestingMember));
      } else {
        approveRequest.setVisibility(View.GONE);
        denyRequest   .setVisibility(View.GONE);
        approveRequest.setOnClickListener(null);
        denyRequest   .setOnClickListener(null);
      }

      bindRecipient(requestingMember.getRequester());
      bindRecipientClick(requestingMember.getRequester());
    }
  }

  private final class SelectionChangeListener {
    void onSelectionChange(int position, boolean isChecked) {
      if (selectable) {
        if (isChecked) {
          selection.add(data.get(position));
        } else {
          selection.remove(data.get(position));
        }
        notifyItemChanged(position);

        if (recipientSelectionChangeListener != null) {
          recipientSelectionChangeListener.onSelectionChanged(selection);
        }
      }
    }
  }

  private final static class DiffCallback extends DiffUtil.Callback {
    private final List<? extends GroupMemberEntry> oldData;
    private final List<? extends GroupMemberEntry> newData;

    DiffCallback(List<? extends GroupMemberEntry> oldData, List<? extends GroupMemberEntry> newData) {
      this.oldData = oldData;
      this.newData = newData;
    }

    @Override
    public int getOldListSize() {
      return oldData.size();
    }

    @Override
    public int getNewListSize() {
      return newData.size();
    }

    @Override
    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
      GroupMemberEntry oldItem = oldData.get(oldItemPosition);
      GroupMemberEntry newItem = newData.get(newItemPosition);

      return oldItem.sameId(newItem);
    }

    @Override
    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
      GroupMemberEntry oldItem = oldData.get(oldItemPosition);
      GroupMemberEntry newItem = newData.get(newItemPosition);

      return oldItem.equals(newItem);
    }
  }
}
