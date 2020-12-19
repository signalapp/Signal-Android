package org.thoughtcrime.securesms.groups.ui.managegroup;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProviders;

import com.google.android.material.snackbar.Snackbar;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.AvatarPreviewActivity;
import org.thoughtcrime.securesms.InviteActivity;
import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.MainActivity;
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
import org.thoughtcrime.securesms.groups.ui.GroupChangeFailureReason;
import org.thoughtcrime.securesms.groups.ui.GroupErrors;
import org.thoughtcrime.securesms.groups.ui.GroupMemberListView;
import org.thoughtcrime.securesms.groups.ui.LeaveGroupDialog;
import org.thoughtcrime.securesms.groups.ui.invitesandrequests.ManagePendingAndRequestingMembersActivity;
import org.thoughtcrime.securesms.groups.ui.managegroup.dialogs.GroupInviteSentDialog;
import org.thoughtcrime.securesms.groups.ui.managegroup.dialogs.GroupRightsDialog;
import org.thoughtcrime.securesms.groups.ui.managegroup.dialogs.GroupsLearnMoreBottomSheetDialogFragment;
import org.thoughtcrime.securesms.groups.ui.migration.GroupsV1MigrationInitiationBottomSheetDialogFragment;
import org.thoughtcrime.securesms.mediaoverview.MediaOverviewActivity;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.profiles.edit.EditProfileActivity;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.ui.bottomsheet.RecipientBottomSheetDialogFragment;
import org.thoughtcrime.securesms.recipients.ui.notifications.CustomNotificationsDialogFragment;
import org.thoughtcrime.securesms.recipients.ui.sharablegrouplink.ShareableGroupLinkDialogFragment;
import org.thoughtcrime.securesms.util.AsynchronousCallback;
import org.thoughtcrime.securesms.util.DateUtils;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.LifecycleCursorWrapper;
import org.thoughtcrime.securesms.util.views.LearnMoreTextView;
import org.thoughtcrime.securesms.util.views.SimpleProgressDialog;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class ManageGroupFragment extends LoggingFragment {
  private static final String GROUP_ID = "GROUP_ID";

  private static final String TAG = Log.tag(ManageGroupFragment.class);

  private static final int    RETURN_FROM_MEDIA = 33114;
  private static final int    PICK_CONTACT      = 61341;
  public  static final String DIALOG_TAG        = "DIALOG";

  private ManageGroupViewModel               viewModel;
  private GroupMemberListView                groupMemberList;
  private View                               pendingAndRequestingRow;
  private TextView                           pendingAndRequestingCount;
  private Toolbar                            toolbar;
  private TextView                           groupName;
  private LearnMoreTextView                  groupInfoText;
  private TextView                           memberCountUnderAvatar;
  private TextView                           memberCountAboveList;
  private AvatarImageView                    avatar;
  private ThreadPhotoRailView                threadPhotoRailView;
  private View                               groupMediaCard;
  private View                               accessControlCard;
  private View                               groupLinkCard;
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
  private View                               mentionsRow;
  private TextView                           mentionsValue;
  private View                               toggleAllMembers;
  private View                               groupLinkRow;
  private TextView                           groupLinkButton;

  private final Recipient.FallbackPhotoProvider fallbackPhotoProvider = new Recipient.FallbackPhotoProvider() {
    @Override
    public @NonNull FallbackContactPhoto getPhotoForGroup() {
      return new FallbackPhoto80dp(R.drawable.ic_group_80, MaterialColor.ULTRAMARINE.toAvatarColor(requireContext()));
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
    groupInfoText               = view.findViewById(R.id.manage_group_info_text);
    memberCountUnderAvatar      = view.findViewById(R.id.member_count);
    memberCountAboveList        = view.findViewById(R.id.member_count_2);
    groupMemberList             = view.findViewById(R.id.group_members);
    pendingAndRequestingRow     = view.findViewById(R.id.pending_and_requesting_members_row);
    pendingAndRequestingCount   = view.findViewById(R.id.pending_and_requesting_members_count);
    threadPhotoRailView         = view.findViewById(R.id.recent_photos);
    groupMediaCard              = view.findViewById(R.id.group_media_card);
    accessControlCard           = view.findViewById(R.id.group_access_control_card);
    groupLinkCard               = view.findViewById(R.id.group_link_card);
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
    mentionsRow                 = view.findViewById(R.id.group_mentions_row);
    mentionsValue               = view.findViewById(R.id.group_mentions_value);
    toggleAllMembers            = view.findViewById(R.id.toggle_all_members);
    groupLinkRow                = view.findViewById(R.id.group_link_row);
    groupLinkButton             = view.findViewById(R.id.group_link_button);

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

    viewModel.getPendingAndRequestingCount().observe(getViewLifecycleOwner(), pendingAndRequestingCount -> {
      pendingAndRequestingRow.setOnClickListener(v -> {
        FragmentActivity activity = requireActivity();
        activity.startActivity(ManagePendingAndRequestingMembersActivity.newIntent(activity, groupId.requireV2()));
      });
      if (pendingAndRequestingCount == 0) {
        this.pendingAndRequestingCount.setVisibility(View.GONE);
      } else {
        this.pendingAndRequestingCount.setText(String.format(Locale.getDefault(), "%d", pendingAndRequestingCount));
        this.pendingAndRequestingCount.setVisibility(View.VISIBLE);
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
                                                                                      .show(requireFragmentManager(), DIALOG_TAG));
    });

    if (groupId.isV2()) {
      groupLinkRow.setOnClickListener(v -> ShareableGroupLinkDialogFragment.create(groupId.requireV2())
                                                                           .show(requireFragmentManager(), DIALOG_TAG));
      viewModel.getGroupLinkOn().observe(getViewLifecycleOwner(), linkEnabled -> groupLinkButton.setText(booleanToOnOff(linkEnabled)));
    }

    viewModel.getGroupViewState().observe(getViewLifecycleOwner(), vs -> {
      if (vs == null) return;
      sharedMediaRow.setOnClickListener(v -> startActivity(MediaOverviewActivity.forThread(context, vs.getThreadId())));

      setMediaCursorFactory(vs.getMediaCursorFactory());

      threadPhotoRailView.setListener(mediaRecord ->
          startActivityForResult(MediaPreviewActivity.intentFromMediaRecord(context,
                                                                            mediaRecord,
                                                                            ViewCompat.getLayoutDirection(threadPhotoRailView) == ViewCompat.LAYOUT_DIRECTION_LTR),
                                 RETURN_FROM_MEDIA));

      groupLinkCard.setVisibility(vs.getGroupRecipient().requireGroupId().isV2() ? View.VISIBLE : View.GONE);
    });

    leaveGroup.setVisibility(groupId.isPush() ? View.VISIBLE : View.GONE);
    leaveGroup.setOnClickListener(v -> LeaveGroupDialog.handleLeavePushGroup(requireActivity(), groupId.requirePush(), () -> startActivity(MainActivity.clearTop(context))));

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

    if (NotificationChannels.supported()) {
      viewModel.hasCustomNotifications().observe(getViewLifecycleOwner(), hasCustomNotifications -> {
        customNotificationsButton.setText(booleanToOnOff(hasCustomNotifications));
      });
    }

    mentionsRow.setVisibility(groupId.isV2() ? View.VISIBLE : View.GONE);
    mentionsRow.setOnClickListener(v -> viewModel.handleMentionNotificationSelection());
    viewModel.getMentionSetting().observe(getViewLifecycleOwner(), value -> mentionsValue.setText(value));

    viewModel.getCanLeaveGroup().observe(getViewLifecycleOwner(), canLeave -> leaveGroup.setVisibility(canLeave ? View.VISIBLE : View.GONE));
    viewModel.getCanBlockGroup().observe(getViewLifecycleOwner(), canBlock -> {
      blockGroup.setVisibility(canBlock ? View.VISIBLE : View.GONE);
      unblockGroup.setVisibility(canBlock ? View.GONE : View.VISIBLE);
    });

    viewModel.getGroupInfoMessage().observe(getViewLifecycleOwner(), message -> {
      switch (message) {
        case LEGACY_GROUP_LEARN_MORE:
          groupInfoText.setText(R.string.ManageGroupActivity_legacy_group_learn_more);
          groupInfoText.setOnLinkClickListener(v -> GroupsLearnMoreBottomSheetDialogFragment.show(requireFragmentManager()));
          groupInfoText.setLearnMoreVisible(true);
          groupInfoText.setVisibility(View.VISIBLE);
          break;
        case LEGACY_GROUP_UPGRADE:
          groupInfoText.setText(R.string.ManageGroupActivity_legacy_group_upgrade);
          groupInfoText.setOnLinkClickListener(v -> GroupsV1MigrationInitiationBottomSheetDialogFragment.showForInitiation(requireFragmentManager(), Recipient.externalPossiblyMigratedGroup(requireContext(), groupId).getId()));
          groupInfoText.setLearnMoreVisible(true, R.string.ManageGroupActivity_upgrade_this_group);
          groupInfoText.setVisibility(View.VISIBLE);
          break;
        case LEGACY_GROUP_TOO_LARGE:
          groupInfoText.setText(context.getString(R.string.ManageGroupActivity_legacy_group_too_large, FeatureFlags.groupLimits().getHardLimit() - 1));
          groupInfoText.setLearnMoreVisible(false);
          groupInfoText.setVisibility(View.VISIBLE);
          break;
        case MMS_WARNING:
          groupInfoText.setText(R.string.ManageGroupActivity_this_is_an_insecure_mms_group);
          groupInfoText.setOnLinkClickListener(v -> startActivity(new Intent(requireContext(), InviteActivity.class)));
          groupInfoText.setLearnMoreVisible(true, R.string.ManageGroupActivity_invite_now);
          groupInfoText.setVisibility(View.VISIBLE);
          break;
        default:
          groupInfoText.setVisibility(View.GONE);
          break;
      }
    });
  }

  private static int booleanToOnOff(boolean isOn) {
    return isOn ? R.string.ManageGroupActivity_on
                : R.string.ManageGroupActivity_off;
  }

  public boolean onMenuItemSelected(@NonNull MenuItem item) {
    if (item.getItemId() == R.id.action_edit) {
      startActivity(EditProfileActivity.getIntentForGroupProfile(requireActivity(), getGroupId()));
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

  @Override
  public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    if (requestCode == RETURN_FROM_MEDIA) {
      applyMediaCursorFactory();
    } else if (requestCode == PICK_CONTACT && data != null) {
      List<RecipientId>                      selected = data.getParcelableArrayListExtra(PushContactSelectionActivity.KEY_SELECTED_RECIPIENTS);
      SimpleProgressDialog.DismissibleDialog progress = SimpleProgressDialog.showDelayed(requireContext());

      viewModel.onAddMembers(selected, new AsynchronousCallback.MainThread<ManageGroupViewModel.AddMembersResult, GroupChangeFailureReason>() {
        @Override
        public void onComplete(ManageGroupViewModel.AddMembersResult result) {
          progress.dismiss();
          if (!result.getNewInvitedMembers().isEmpty()) {
            GroupInviteSentDialog.showInvitesSent(requireContext(), result.getNewInvitedMembers());
          }

          if (result.getNumberOfMembersAdded() > 0) {
            String string = getResources().getQuantityString(R.plurals.ManageGroupActivity_added,
                                                             result.getNumberOfMembersAdded(),
                                                             result.getNumberOfMembersAdded());
            Snackbar.make(requireView(), string, Snackbar.LENGTH_SHORT).setTextColor(Color.WHITE).show();
          }
        }

        @Override
        public void onError(@Nullable GroupChangeFailureReason error) {
          progress.dismiss();
          Toast.makeText(requireContext(), GroupErrors.getUserDisplayMessage(error), Toast.LENGTH_LONG).show();
        }
      });
    }
  }
}
