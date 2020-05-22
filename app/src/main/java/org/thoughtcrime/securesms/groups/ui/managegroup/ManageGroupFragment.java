package org.thoughtcrime.securesms.groups.ui.managegroup;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProviders;

import org.thoughtcrime.securesms.ContactSelectionListFragment;
import org.thoughtcrime.securesms.MediaPreviewActivity;
import org.thoughtcrime.securesms.MuteDialog;
import org.thoughtcrime.securesms.PushContactSelectionActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.components.ThreadPhotoRailView;
import org.thoughtcrime.securesms.contacts.ContactsCursorLoader;
import org.thoughtcrime.securesms.contacts.avatars.FallbackContactPhoto;
import org.thoughtcrime.securesms.contacts.avatars.ResourceContactPhoto;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.ui.GroupMemberListView;
import org.thoughtcrime.securesms.groups.ui.LeaveGroupDialog;
import org.thoughtcrime.securesms.groups.ui.managegroup.dialogs.GroupRightsDialog;
import org.thoughtcrime.securesms.groups.ui.notifications.CustomNotificationsDialogFragment;
import org.thoughtcrime.securesms.groups.ui.pendingmemberinvites.PendingMemberInvitesActivity;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mediaoverview.MediaOverviewActivity;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.profiles.edit.EditProfileActivity;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.ui.bottomsheet.RecipientBottomSheetDialogFragment;
import org.thoughtcrime.securesms.util.DateUtils;

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
  private View                               listPending;
  private TextView                           groupTitle;
  private TextView                           memberCountUnderAvatar;
  private TextView                           memberCountAboveList;
  private AvatarImageView                    avatar;
  private ThreadPhotoRailView                threadPhotoRailView;
  private View                               groupMediaCard;
  private View                               accessControlCard;
  private View                               pendingMembersCard;
  private ManageGroupViewModel.CursorFactory cursorFactory;
  private View                               photoRailLabel;
  private View                               editGroupAccessRow;
  private TextView                           editGroupAccessValue;
  private View                               editGroupMembershipRow;
  private TextView                           editGroupMembershipValue;
  private View                               disappearingMessagesRow;
  private TextView                           disappearingMessages;
  private TextView                           blockGroup;
  private TextView                           leaveGroup;
  private TextView                           addMembers;
  private Switch                             muteNotificationsSwitch;
  private View                               muteNotificationsRow;
  private TextView                           muteNotificationsUntilLabel;
  private TextView                           customNotificationsButton;
  private View                               customNotificationsRow;
  private View                               toggleAllMembers;

  private final Recipient.FallbackPhotoProvider fallbackPhotoProvider = new Recipient.FallbackPhotoProvider() {
    @Override
    public @NonNull FallbackContactPhoto getPhotoForGroup() {
      return new ResourceContactPhoto(R.drawable.ic_group_80);
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
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setHasOptionsMenu(true);
  }

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater,
                                     @Nullable ViewGroup container,
                                     @Nullable Bundle savedInstanceState)
  {
    View view = inflater.inflate(R.layout.group_manage_fragment, container, false);

    avatar                      = view.findViewById(R.id.group_avatar);
    groupTitle                  = view.findViewById(R.id.group_title);
    memberCountUnderAvatar      = view.findViewById(R.id.member_count);
    memberCountAboveList        = view.findViewById(R.id.member_count_2);
    groupMemberList             = view.findViewById(R.id.group_members);
    listPending                 = view.findViewById(R.id.listPending);
    threadPhotoRailView         = view.findViewById(R.id.recent_photos);
    groupMediaCard              = view.findViewById(R.id.group_media_card);
    accessControlCard           = view.findViewById(R.id.group_access_control_card);
    pendingMembersCard          = view.findViewById(R.id.group_pending_card);
    photoRailLabel              = view.findViewById(R.id.rail_label);
    editGroupAccessRow          = view.findViewById(R.id.edit_group_access_row);
    editGroupAccessValue        = view.findViewById(R.id.edit_group_access_value);
    editGroupMembershipRow      = view.findViewById(R.id.edit_group_membership_row);
    editGroupMembershipValue    = view.findViewById(R.id.edit_group_membership_value);
    disappearingMessagesRow     = view.findViewById(R.id.disappearing_messages_row);
    disappearingMessages        = view.findViewById(R.id.disappearing_messages);
    blockGroup                  = view.findViewById(R.id.blockGroup);
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
    GroupId.Push                 groupId = getPushGroupId();
    ManageGroupViewModel.Factory factory = new ManageGroupViewModel.Factory(context, groupId);

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
      members -> {
        if (members > 0) {
          listPending.setEnabled(true);
          listPending.setOnClickListener(v -> {
            FragmentActivity activity = requireActivity();
            activity.startActivity(PendingMemberInvitesActivity.newIntent(activity, groupId.requireV2()));
          });
        } else {
          listPending.setEnabled(false);
          listPending.setOnClickListener(null);
        }
      });

    avatar.setFallbackPhotoProvider(fallbackPhotoProvider);

    viewModel.getTitle().observe(getViewLifecycleOwner(), groupTitle::setText);
    viewModel.getMemberCountSummary().observe(getViewLifecycleOwner(), memberCountUnderAvatar::setText);
    viewModel.getFullMemberCountSummary().observe(getViewLifecycleOwner(), memberCountAboveList::setText);
    viewModel.getGroupRecipient().observe(getViewLifecycleOwner(), avatar::setRecipient);

    viewModel.getGroupViewState().observe(getViewLifecycleOwner(), vs -> {
      if (vs == null) return;
      photoRailLabel.setOnClickListener(v -> startActivity(MediaOverviewActivity.forThread(context, vs.getThreadId())));

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

    addMembers.setOnClickListener(v -> {
      Intent intent = new Intent(requireActivity(), PushContactSelectionActivity.class);
      intent.putExtra(ContactSelectionListFragment.DISPLAY_MODE, ContactsCursorLoader.DisplayMode.FLAG_PUSH);
      startActivityForResult(intent, PICK_CONTACT);
    });

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

    viewModel.getCanEditGroupAttributes().observe(getViewLifecycleOwner(), canEdit -> disappearingMessages.setEnabled(canEdit));
    viewModel.getCanAddMembers().observe(getViewLifecycleOwner(), canEdit -> addMembers.setVisibility(canEdit ? View.VISIBLE : View.GONE));

    groupMemberList.setRecipientClickListener(recipient -> RecipientBottomSheetDialogFragment.create(recipient.getId(), groupId).show(requireFragmentManager(), "BOTTOM"));

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

    if (NotificationChannels.supported()) {
      customNotificationsRow.setVisibility(View.VISIBLE);

      customNotificationsRow.setOnClickListener(v -> CustomNotificationsDialogFragment.create(groupId)
                                                                                      .show(requireFragmentManager(), "CUSTOM_NOTIFICATIONS"));

      //noinspection CodeBlock2Expr
      viewModel.hasCustomNotifications().observe(getViewLifecycleOwner(), hasCustomNotifications -> {
        customNotificationsButton.setText(hasCustomNotifications ? R.string.ManageGroupActivity_on
                                                                 : R.string.ManageGroupActivity_off);
      });
    } else {
      customNotificationsRow.setVisibility(View.GONE);
    }
  }

  @Override
  public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
    inflater.inflate(R.menu.manage_group_fragment, menu);

    viewModel.getCanEditGroupAttributes().observe(getViewLifecycleOwner(), canEdit -> menu.findItem(R.id.action_edit).setVisible(canEdit));
  }

  @Override
  public boolean onOptionsItemSelected(@NonNull MenuItem item) {
    if (item.getItemId() == R.id.action_edit) {
      startActivity(EditProfileActivity.getIntentForGroupProfile(requireActivity(), getPushGroupId()));
      return true;
    }

    return false;
  }

  private GroupId.Push getPushGroupId() {
    return GroupId.parseOrThrow(Objects.requireNonNull(requireArguments().getString(GROUP_ID))).requirePush();
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
      threadPhotoRailView.setCursor(GlideApp.with(context), cursor);
      groupMediaCard.setVisibility(cursor.getCount() > 0 ? View.VISIBLE : View.GONE);
    } else {
      threadPhotoRailView.setCursor(GlideApp.with(context), null);
      groupMediaCard.setVisibility(View.GONE);
    }
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
