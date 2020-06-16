package org.thoughtcrime.securesms.groups.ui.managegroup;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProviders;

import com.google.android.material.snackbar.Snackbar;

import org.thoughtcrime.securesms.AvatarPreviewActivity;
import org.thoughtcrime.securesms.MediaPreviewActivity;
import org.thoughtcrime.securesms.MuteDialog;
import org.thoughtcrime.securesms.PushContactSelectionActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.color.MaterialColor;
import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.components.ThreadPhotoRailView;
import org.thoughtcrime.securesms.contacts.avatars.FallbackContactPhoto;
import org.thoughtcrime.securesms.contacts.avatars.FallbackPhoto80dp;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.ui.GroupMemberListView;
import org.thoughtcrime.securesms.groups.ui.LeaveGroupDialog;
import org.thoughtcrime.securesms.groups.ui.managegroup.dialogs.GroupRightsDialog;
import org.thoughtcrime.securesms.groups.ui.pendingmemberinvites.PendingMemberInvitesActivity;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mediaoverview.MediaOverviewActivity;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.profiles.edit.EditProfileActivity;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.ui.bottomsheet.RecipientBottomSheetDialogFragment;
import org.thoughtcrime.securesms.recipients.ui.notifications.CustomNotificationsDialogFragment;
import org.thoughtcrime.securesms.util.DateUtils;
import org.thoughtcrime.securesms.util.LifecycleCursorWrapper;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class ManageGroupFragment extends Fragment {
  private static final String GROUP_ID = "GROUP_ID";

  private static final String TAG = Log.tag(ManageGroupFragment.class);

  private static final int RETURN_FROM_MEDIA = 33114;
  private static final int PICK_CONTACT      = 61341;

  private ManageGroupViewModel               viewModel;
  private GroupMemberListView                groupMemberList;
  private View                               pendingMembersRow;
  private TextView                           pendingMembersCount;
  private Toolbar                            toolbar;
  private TextView                           groupName;
  private TextView                           memberCountUnderAvatar;
  private TextView                           memberCountAboveList;
  private AvatarImageView                    avatar;
  private ThreadPhotoRailView                threadPhotoRailView;
  private View                               groupMediaCard;
  private View                               accessControlCard;
  private View                               pendingMembersCard;
  private ManageGroupViewModel.CursorFactory cursorFactory;
  private View                               sharedMediaRow;
  private View                               editGroupAccessRow;
  private TextView                           editGroupAccessValue;
  private View                               editGroupMembershipRow;
  private TextView                           editGroupMembershipValue;
  private View                               disappearingMessagesCard;
  private View                               disappearingMessagesRow;
  private TextView                           disappearingMessages;
  private View                               blockAndLeaveCard;
  private TextView                           blockGroup;
  private TextView                           unblockGroup;
  private TextView                           leaveGroup;
  private TextView                           addMembers;
  private SwitchCompat                       muteNotificationsSwitch;
  private View                               muteNotificationsRow;
  private TextView                           muteNotificationsUntilLabel;
  private TextView                           customNotificationsButton;
  private View                               customNotificationsRow;
  private View                               toggleAllMembers;

  private final Recipient.FallbackPhotoProvider fallbackPhotoProvider = new Recipient.FallbackPhotoProvider() {
    @Override
    public @NonNull FallbackContactPhoto getPhotoForGroup() {
      return new FallbackPhoto80dp(R.drawable.ic_group_80, MaterialColor.ULTRAMARINE);
    }
  };

  static ManageGroupFragment newInstance(@NonNull String groupId) {
    ManageGroupFragment fragment = new ManageGroupFragment();
    Bundle              args     = new Bundle();

    args.putString(GROUP_ID, groupId);
    fragment.setArguments(args);

    return fragment;
  }

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater,
                                     @Nullable ViewGroup container,
                                     @Nullable Bundle savedInstanceState)
  {
    View view = inflater.inflate(R.layout.group_manage_fragment, container, false);

    avatar                      = view.findViewById(R.id.group_avatar);
    toolbar                     = view.findViewById(R.id.toolbar);
    groupName                   = view.findViewById(R.id.name);
    memberCountUnderAvatar      = view.findViewById(R.id.member_count);
    memberCountAboveList        = view.findViewById(R.id.member_count_2);
    groupMemberList             = view.findViewById(R.id.group_members);
    pendingMembersRow           = view.findViewById(R.id.pending_members_row);
    pendingMembersCount         = view.findViewById(R.id.pending_members_count);
    threadPhotoRailView         = view.findViewById(R.id.recent_photos);
    groupMediaCard              = view.findViewById(R.id.group_media_card);
    accessControlCard           = view.findViewById(R.id.group_access_control_card);
    pendingMembersCard          = view.findViewById(R.id.group_pending_card);
    sharedMediaRow              = view.findViewById(R.id.shared_media_row);
    editGroupAccessRow          = view.findViewById(R.id.edit_group_access_row);
    editGroupAccessValue        = view.findViewById(R.id.edit_group_access_value);
    editGroupMembershipRow      = view.findViewById(R.id.edit_group_membership_row);
    editGroupMembershipValue    = view.findViewById(R.id.edit_group_membership_value);
    disappearingMessagesCard    = view.findViewById(R.id.group_disappearing_messages_card);
    disappearingMessagesRow     = view.findViewById(R.id.disappearing_messages_row);
    disappearingMessages        = view.findViewById(R.id.disappearing_messages);
    blockAndLeaveCard           = view.findViewById(R.id.group_block_and_leave_card);
    blockGroup                  = view.findViewById(R.id.blockGroup);
    unblockGroup                = view.findViewById(R.id.unblockGroup);
    leaveGroup                  = view.findViewById(R.id.leaveGroup);
    addMembers                  = view.findViewById(R.id.add_members);
    muteNotificationsUntilLabel = view.findViewById(R.id.group_mute_notifications_until);
    muteNotificationsSwitch     = view.findViewById(R.id.group_mute_notifications_switch);
    muteNotificationsRow        = view.findViewById(R.id.group_mute_notifications_row);
    customNotificationsButton   = view.findViewById(R.id.group_custom_notifications_button);
    customNotificationsRow      = view.findViewById(R.id.group_custom_notifications_row);
    toggleAllMembers            = view.findViewById(R.id.toggle_all_members);

    return view;
  }

  @Override
  public void onActivityCreated(@Nullable Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);

    Context                      context = requireContext();
    GroupId                      groupId = getGroupId();
    ManageGroupViewModel.Factory factory = new ManageGroupViewModel.Factory(context, groupId);

    disappearingMessagesCard.setVisibility(groupId.isPush() ? View.VISIBLE : View.GONE);
    blockAndLeaveCard.setVisibility(groupId.isPush() ? View.VISIBLE : View.GONE);

    viewModel = ViewModelProviders.of(requireActivity(), factory).get(ManageGroupViewModel.class);

    viewModel.getMembers().observe(getViewLifecycleOwner(), members -> groupMemberList.setMembers(members));

    viewModel.getCanCollapseMemberList().observe(getViewLifecycleOwner(), canCollapseMemberList -> {
      if (canCollapseMemberList) {
        toggleAllMembers.setVisibility(View.VISIBLE);
        toggleAllMembers.setOnClickListener(v -> viewModel.revealCollapsedMembers());
      } else {
        toggleAllMembers.setVisibility(View.GONE);
      }
    });

    viewModel.getPendingMemberCount().observe(getViewLifecycleOwner(),
      pendingInviteCount -> {
        pendingMembersRow.setOnClickListener(v -> {
          FragmentActivity activity = requireActivity();
          activity.startActivity(PendingMemberInvitesActivity.newIntent(activity, groupId.requireV2()));
        });
        if (pendingInviteCount == 0) {
          pendingMembersCount.setText(R.string.ManageGroupActivity_none);
        } else {
          pendingMembersCount.setText(getResources().getQuantityString(R.plurals.ManageGroupActivity_invited, pendingInviteCount, pendingInviteCount));
        }
      });

    avatar.setFallbackPhotoProvider(fallbackPhotoProvider);

    toolbar.setNavigationOnClickListener(v -> requireActivity().onBackPressed());
    toolbar.setOnMenuItemClickListener(this::onMenuItemSelected);
    toolbar.inflateMenu(R.menu.manage_group_fragment);

    viewModel.getCanEditGroupAttributes().observe(getViewLifecycleOwner(), canEdit -> {
      toolbar.getMenu().findItem(R.id.action_edit).setVisible(canEdit);
      disappearingMessages.setEnabled(canEdit);
      disappearingMessagesRow.setEnabled(canEdit);
    });

    viewModel.getTitle().observe(getViewLifecycleOwner(), groupName::setText);
    viewModel.getMemberCountSummary().observe(getViewLifecycleOwner(), memberCountUnderAvatar::setText);
    viewModel.getFullMemberCountSummary().observe(getViewLifecycleOwner(), memberCountAboveList::setText);
    viewModel.getGroupRecipient().observe(getViewLifecycleOwner(), groupRecipient -> {
      avatar.setRecipient(groupRecipient);
      avatar.setOnClickListener(v -> {
        FragmentActivity activity = requireActivity();
        activity.startActivity(AvatarPreviewActivity.intentFromRecipientId(activity, groupRecipient.getId()),
                               AvatarPreviewActivity.createTransitionBundle(activity, avatar));
      });
      customNotificationsRow.setOnClickListener(v -> CustomNotificationsDialogFragment.create(groupRecipient.getId())
                                                                                      .show(requireFragmentManager(), "CUSTOM_NOTIFICATIONS"));
    });

    viewModel.getGroupViewState().observe(getViewLifecycleOwner(), vs -> {
      if (vs == null) return;
      sharedMediaRow.setOnClickListener(v -> startActivity(MediaOverviewActivity.forThread(context, vs.getThreadId())));

      setMediaCursorFactory(vs.getMediaCursorFactory());

      threadPhotoRailView.setListener(mediaRecord ->
          startActivityForResult(MediaPreviewActivity.intentFromMediaRecord(context,
                                                                            mediaRecord,
                                                                            ViewCompat.getLayoutDirection(threadPhotoRailView) == ViewCompat.LAYOUT_DIRECTION_LTR),
                                 RETURN_FROM_MEDIA));

      pendingMembersCard.setVisibility(vs.getGroupRecipient().requireGroupId().isV2() ? View.VISIBLE : View.GONE);
    });

    leaveGroup.setVisibility(groupId.isPush() ? View.VISIBLE : View.GONE);
    leaveGroup.setOnClickListener(v -> LeaveGroupDialog.handleLeavePushGroup(context,
                                                                             getLifecycle(),
                                                                             groupId.requirePush(),
                                                                             null));

    viewModel.getDisappearingMessageTimer().observe(getViewLifecycleOwner(), string -> disappearingMessages.setText(string));

    disappearingMessagesRow.setOnClickListener(v -> viewModel.handleExpirationSelection());
    blockGroup.setOnClickListener(v -> viewModel.blockAndLeave(requireActivity()));
    unblockGroup.setOnClickListener(v -> viewModel.unblock(requireActivity()));

    addMembers.setOnClickListener(v -> viewModel.onAddMembersClick(this, PICK_CONTACT));

    viewModel.getMembershipRights().observe(getViewLifecycleOwner(), r -> {
        if (r != null) {
          editGroupMembershipValue.setText(r.getString());
          editGroupMembershipRow.setOnClickListener(v -> new GroupRightsDialog(context, GroupRightsDialog.Type.MEMBERSHIP, r, (from, to) -> viewModel.applyMembershipRightsChange(to)).show());
        }
      }
    );

    viewModel.getEditGroupAttributesRights().observe(getViewLifecycleOwner(), r -> {
        if (r != null) {
          editGroupAccessValue.setText(r.getString());
          editGroupAccessRow.setOnClickListener(v -> new GroupRightsDialog(context, GroupRightsDialog.Type.ATTRIBUTES, r, (from, to) -> viewModel.applyAttributesRightsChange(to)).show());
        }
      }
    );

    viewModel.getIsAdmin().observe(getViewLifecycleOwner(), admin -> {
      accessControlCard.setVisibility(admin ? View.VISIBLE : View.GONE);
      editGroupMembershipRow.setEnabled(admin);
      editGroupMembershipValue.setEnabled(admin);
      editGroupAccessRow.setEnabled(admin);
      editGroupAccessValue.setEnabled(admin);
    });

    viewModel.getCanAddMembers().observe(getViewLifecycleOwner(), canEdit -> addMembers.setVisibility(canEdit ? View.VISIBLE : View.GONE));

    groupMemberList.setRecipientClickListener(recipient -> RecipientBottomSheetDialogFragment.create(recipient.getId(), groupId).show(requireFragmentManager(), "BOTTOM"));
    groupMemberList.setOverScrollMode(View.OVER_SCROLL_NEVER);

    final CompoundButton.OnCheckedChangeListener muteSwitchListener = (buttonView, isChecked) -> {
      if (isChecked) {
        MuteDialog.show(context, viewModel::setMuteUntil, () -> muteNotificationsSwitch.setChecked(false));
      } else {
        viewModel.clearMuteUntil();
      }
    };

    muteNotificationsRow.setOnClickListener(v -> {
      if (muteNotificationsSwitch.isEnabled()) {
        muteNotificationsSwitch.toggle();
      }
    });

    viewModel.getMuteState().observe(getViewLifecycleOwner(), muteState -> {
      if (muteNotificationsSwitch.isChecked() != muteState.isMuted()) {
        muteNotificationsSwitch.setOnCheckedChangeListener(null);
        muteNotificationsSwitch.setChecked(muteState.isMuted());
      }

      muteNotificationsSwitch.setEnabled(true);
      muteNotificationsSwitch.setOnCheckedChangeListener(muteSwitchListener);
      muteNotificationsUntilLabel.setVisibility(muteState.isMuted() ? View.VISIBLE : View.GONE);

      if (muteState.isMuted()) {
        muteNotificationsUntilLabel.setText(getString(R.string.ManageGroupActivity_until_s,
                                                      DateUtils.getTimeString(requireContext(),
                                                                              Locale.getDefault(),
                                                                              muteState.getMutedUntil())));
      }
    });

    customNotificationsRow.setVisibility(View.VISIBLE);

    //noinspection CodeBlock2Expr
    if (NotificationChannels.supported()) {
      viewModel.hasCustomNotifications().observe(getViewLifecycleOwner(), hasCustomNotifications -> {
        customNotificationsButton.setText(hasCustomNotifications ? R.string.ManageGroupActivity_on
                                                                 : R.string.ManageGroupActivity_off);
      });
    }

    viewModel.getSnackbarEvents().observe(getViewLifecycleOwner(), this::handleSnackbarEvent);

    viewModel.getCanLeaveGroup().observe(getViewLifecycleOwner(), canLeave -> leaveGroup.setVisibility(canLeave ? View.VISIBLE : View.GONE));
    viewModel.getCanBlockGroup().observe(getViewLifecycleOwner(), canBlock -> {
      blockGroup.setVisibility(canBlock ? View.VISIBLE : View.GONE);
      unblockGroup.setVisibility(canBlock ? View.GONE : View.VISIBLE);
    });
  }

  public boolean onMenuItemSelected(@NonNull MenuItem item) {
    if (item.getItemId() == R.id.action_edit) {
      startActivity(EditProfileActivity.getIntentForGroupProfile(requireActivity(), getGroupId().requirePush()));
      return true;
    }

    return false;
  }

  private GroupId getGroupId() {
    return GroupId.parseOrThrow(Objects.requireNonNull(requireArguments().getString(GROUP_ID)));
  }

  private void setMediaCursorFactory(@Nullable ManageGroupViewModel.CursorFactory cursorFactory) {
    if (this.cursorFactory != cursorFactory) {
      this.cursorFactory = cursorFactory;
      applyMediaCursorFactory();
    }
  }

  private void applyMediaCursorFactory() {
    Context context = getContext();
    if (context == null) return;
    if (this.cursorFactory != null) {
      Cursor cursor = this.cursorFactory.create();
      getViewLifecycleOwner().getLifecycle().addObserver(new LifecycleCursorWrapper(cursor));

      threadPhotoRailView.setCursor(GlideApp.with(context), cursor);
      groupMediaCard.setVisibility(cursor.getCount() > 0 ? View.VISIBLE : View.GONE);
    } else {
      threadPhotoRailView.setCursor(GlideApp.with(context), null);
      groupMediaCard.setVisibility(View.GONE);
    }
  }

  private void handleSnackbarEvent(@NonNull ManageGroupViewModel.SnackbarEvent snackbarEvent) {
    Snackbar.make(requireView(), buildSnackbarString(snackbarEvent), Snackbar.LENGTH_SHORT).show();
  }

  private @NonNull String buildSnackbarString(@NonNull ManageGroupViewModel.SnackbarEvent snackbarEvent) {
    return getResources().getQuantityString(R.plurals.ManageGroupActivity_added,
                                            snackbarEvent.getNumberOfMembersAdded(),
                                            snackbarEvent.getNumberOfMembersAdded());
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == RETURN_FROM_MEDIA) {
      applyMediaCursorFactory();
    } else if (requestCode == PICK_CONTACT && data != null) {
      List<RecipientId> selected = data.getParcelableArrayListExtra(PushContactSelectionActivity.KEY_SELECTED_RECIPIENTS);
      viewModel.onAddMembers(selected);
    }
  }
}
