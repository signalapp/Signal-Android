package org.thoughtcrime.securesms.groups.ui.managegroup;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProviders;

import org.thoughtcrime.securesms.MediaPreviewActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.components.ThreadPhotoRailView;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.ui.GroupMemberListView;
import org.thoughtcrime.securesms.groups.ui.LeaveGroupDialog;
import org.thoughtcrime.securesms.groups.ui.managegroup.dialogs.GroupRightsDialog;
import org.thoughtcrime.securesms.groups.ui.pendingmemberinvites.PendingMemberInvitesActivity;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mediaoverview.MediaOverviewActivity;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.recipients.ui.bottomsheet.RecipientBottomSheetDialogFragment;

import java.util.Objects;

public class ManageGroupFragment extends Fragment {
  private static final String GROUP_ID = "GROUP_ID";

  private static final String TAG = Log.tag(ManageGroupFragment.class);

  private static final int RETURN_FROM_MEDIA = 33114;

  private ManageGroupViewModel               viewModel;
  private GroupMemberListView                groupMemberList;
  private View                               listPending;
  private TextView                           groupTitle;
  private TextView                           memberCount;
  private AvatarImageView                    avatar;
  private ThreadPhotoRailView                threadPhotoRailView;
  private View                               groupMediaCard;
  private View                               accessControlCard;
  private View                               pendingMembersCard;
  private ManageGroupViewModel.CursorFactory cursorFactory;
  private View                               photoRailLabel;
  private Button                             editGroupAccessValue;
  private Button                             editGroupMembershipValue;
  private Button                             disappearingMessages;
  private Button                             blockGroup;
  private Button                             leaveGroup;

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

    avatar                   = view.findViewById(R.id.group_avatar);
    groupTitle               = view.findViewById(R.id.group_title);
    memberCount              = view.findViewById(R.id.member_count);
    groupMemberList          = view.findViewById(R.id.group_members);
    listPending              = view.findViewById(R.id.listPending);
    threadPhotoRailView      = view.findViewById(R.id.recent_photos);
    groupMediaCard           = view.findViewById(R.id.group_media_card);
    accessControlCard        = view.findViewById(R.id.group_access_control_card);
    pendingMembersCard       = view.findViewById(R.id.group_pending_card);
    photoRailLabel           = view.findViewById(R.id.rail_label);
    editGroupAccessValue     = view.findViewById(R.id.edit_group_access_value);
    editGroupMembershipValue = view.findViewById(R.id.edit_group_membership_value);
    disappearingMessages     = view.findViewById(R.id.disappearing_messages);
    blockGroup               = view.findViewById(R.id.blockGroup);
    leaveGroup               = view.findViewById(R.id.leaveGroup);

    return view;
  }

  @Override
  public void onActivityCreated(@Nullable Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);

    Context                      context = requireContext();
    GroupId.Push                 groupId = GroupId.parseOrThrow(Objects.requireNonNull(requireArguments().getString(GROUP_ID))).requirePush();
    ManageGroupViewModel.Factory factory = new ManageGroupViewModel.Factory(context, groupId);

    viewModel = ViewModelProviders.of(requireActivity(), factory).get(ManageGroupViewModel.class);

    viewModel.getMembers().observe(getViewLifecycleOwner(), members -> groupMemberList.setMembers(members));

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

    viewModel.getTitle().observe(getViewLifecycleOwner(), groupTitle::setText);
    viewModel.getMemberCountSummary().observe(getViewLifecycleOwner(), memberCount::setText);

    viewModel.getGroupViewState().observe(getViewLifecycleOwner(), vs -> {
      if (vs == null) return;
      photoRailLabel.setOnClickListener(v -> startActivity(MediaOverviewActivity.forThread(context, vs.getThreadId())));
      avatar.setRecipient(vs.getGroupRecipient());

      setMediaCursorFactory(vs.getMediaCursorFactory());

      threadPhotoRailView.setListener(mediaRecord ->
          startActivityForResult(MediaPreviewActivity.intentFromMediaRecord(context,
                                                                            mediaRecord,
                                                                            ViewCompat.getLayoutDirection(threadPhotoRailView) == ViewCompat.LAYOUT_DIRECTION_LTR),
                                 RETURN_FROM_MEDIA));

      accessControlCard.setVisibility(vs.getGroupRecipient().requireGroupId().isV2() ? View.VISIBLE : View.GONE);
      pendingMembersCard.setVisibility(vs.getGroupRecipient().requireGroupId().isV2() ? View.VISIBLE : View.GONE);
    });

    leaveGroup.setVisibility(groupId.isPush() ? View.VISIBLE : View.GONE);
    leaveGroup.setOnClickListener(v -> LeaveGroupDialog.handleLeavePushGroup(context,
                                                                             getLifecycle(),
                                                                             groupId.requirePush(),
                                                                             null));

    viewModel.getDisappearingMessageTimer().observe(getViewLifecycleOwner(), string -> disappearingMessages.setText(string));

    disappearingMessages.setOnClickListener(v -> viewModel.handleExpirationSelection());
    blockGroup.setOnClickListener(v -> viewModel.blockAndLeave(requireActivity()));

    viewModel.getMembershipRights().observe(getViewLifecycleOwner(), r -> {
        if (r != null) {
          editGroupMembershipValue.setText(r.getString());
          editGroupMembershipValue.setOnClickListener(v -> new GroupRightsDialog(context, GroupRightsDialog.Type.MEMBERSHIP, r, (from, to) -> viewModel.applyMembershipRightsChange(to)).show());
        }
      }
    );

    viewModel.getEditGroupAttributesRights().observe(getViewLifecycleOwner(), r -> {
        if (r != null) {
          editGroupAccessValue.setText(r.getString());
          editGroupAccessValue.setOnClickListener(v -> new GroupRightsDialog(context, GroupRightsDialog.Type.ATTRIBUTES, r, (from, to) -> viewModel.applyAttributesRightsChange(to)).show());
        }
      }
    );

    viewModel.getIsAdmin().observe(getViewLifecycleOwner(), admin -> {
      editGroupMembershipValue.setEnabled(admin);
      editGroupAccessValue.setEnabled(admin);
    });

    viewModel.getCanEditGroupAttributes().observe(getViewLifecycleOwner(), canEdit -> disappearingMessages.setEnabled(canEdit));

    groupMemberList.setRecipientClickListener(recipient -> RecipientBottomSheetDialogFragment.create(recipient.getId(), groupId).show(requireFragmentManager(), "BOTTOM"));
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
    }
  }
}
