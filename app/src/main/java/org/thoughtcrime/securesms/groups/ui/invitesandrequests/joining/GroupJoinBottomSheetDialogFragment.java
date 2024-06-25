package org.thoughtcrime.securesms.groups.ui.invitesandrequests.joining;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.components.emoji.EmojiTextView;
import org.thoughtcrime.securesms.conversation.ConversationIntents;
import org.thoughtcrime.securesms.conversation.colors.AvatarColor;
import org.thoughtcrime.securesms.groups.ui.managegroup.dialogs.GroupDescriptionDialog;
import org.thoughtcrime.securesms.groups.v2.GroupDescriptionUtil;
import org.thoughtcrime.securesms.groups.v2.GroupInviteLinkUrl;
import org.thoughtcrime.securesms.util.BottomSheetUtil;
import org.thoughtcrime.securesms.util.LongClickMovementMethod;
import org.thoughtcrime.securesms.util.ThemeUtil;
import org.thoughtcrime.securesms.util.WindowUtil;

public final class GroupJoinBottomSheetDialogFragment extends BottomSheetDialogFragment {

  private static final String TAG = Log.tag(GroupJoinBottomSheetDialogFragment.class);

  private static final String ARG_GROUP_INVITE_LINK_URL = "group_invite_url";

  private ProgressBar     busy;
  private AvatarImageView avatar;
  private TextView        groupName;
  private TextView        groupDetails;
  private EmojiTextView   groupDescription;
  private TextView        groupJoinExplain;
  private Button          groupJoinButton;
  private Button          groupCancelButton;

  public static void show(@NonNull FragmentManager manager,
                          @NonNull GroupInviteLinkUrl groupInviteLinkUrl)
  {
    GroupJoinBottomSheetDialogFragment fragment = new GroupJoinBottomSheetDialogFragment();

    Bundle args = new Bundle();
    args.putString(ARG_GROUP_INVITE_LINK_URL, groupInviteLinkUrl.getUrl());
    fragment.setArguments(args);

    fragment.show(manager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG);
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    setStyle(DialogFragment.STYLE_NORMAL,
             ThemeUtil.isDarkTheme(requireContext()) ? R.style.Theme_Signal_RoundedBottomSheet
                                                     : R.style.Theme_Signal_RoundedBottomSheet_Light);

    super.onCreate(savedInstanceState);
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.group_join_bottom_sheet, container, false);

    groupCancelButton = view.findViewById(R.id.group_join_cancel_button);
    groupJoinButton   = view.findViewById(R.id.group_join_button);
    busy              = view.findViewById(R.id.group_join_busy);
    avatar            = view.findViewById(R.id.group_join_recipient_avatar);
    groupName         = view.findViewById(R.id.group_join_group_name);
    groupDescription  = view.findViewById(R.id.group_join_group_description);
    groupDetails      = view.findViewById(R.id.group_join_group_details);
    groupJoinExplain  = view.findViewById(R.id.group_join_explain);

    groupCancelButton.setOnClickListener(v -> dismiss());

    avatar.setImageBytesForGroup(null, AvatarColor.UNKNOWN);

    return view;
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    GroupJoinViewModel.Factory factory = new GroupJoinViewModel.Factory(requireContext().getApplicationContext(), getGroupInviteLinkUrl());

    GroupJoinViewModel viewModel = new ViewModelProvider(this, factory).get(GroupJoinViewModel.class);

    viewModel.getGroupDetails().observe(getViewLifecycleOwner(), details -> {
      groupName.setText(details.getGroupName());
      groupDetails.setText(requireContext().getResources().getQuantityString(R.plurals.GroupJoinBottomSheetDialogFragment_group_dot_d_members, details.getGroupMembershipCount(), details.getGroupMembershipCount()));

      if (!TextUtils.isEmpty(details.getGroupDescription())) {
        updateGroupDescription(details.getGroupName(), details.getGroupDescription());
      }

      groupJoinExplain.setText(details.joinRequiresAdminApproval() ? R.string.GroupJoinBottomSheetDialogFragment_admin_approval_needed
                                                                   : R.string.GroupJoinBottomSheetDialogFragment_direct_join);
      groupJoinButton.setText(details.joinRequiresAdminApproval() ? R.string.GroupJoinBottomSheetDialogFragment_request_to_join
                                                                  : R.string.GroupJoinBottomSheetDialogFragment_join);
      groupJoinButton.setOnClickListener(v -> {
        Log.i(TAG, details.joinRequiresAdminApproval() ? "Attempting to direct join group" : "Attempting to request to join group");
        viewModel.join(details);
      });
      groupJoinButton.setVisibility(View.VISIBLE);

      avatar.setImageBytesForGroup(details.getAvatarBytes(), AvatarColor.UNKNOWN);

      groupCancelButton.setVisibility(View.VISIBLE);
    });

    viewModel.isBusy().observe(getViewLifecycleOwner(), isBusy -> busy.setVisibility(isBusy ? View.VISIBLE : View.GONE));

    viewModel.getErrors().observe(getViewLifecycleOwner(), this::showError);

    viewModel.getJoinErrors().observe(getViewLifecycleOwner(), error -> Toast.makeText(requireContext(), errorToMessage(error), Toast.LENGTH_SHORT).show());

    viewModel.getJoinSuccess().observe(getViewLifecycleOwner(), joinGroupSuccess -> {
        Log.i(TAG, "Group joined, navigating to group");

        Intent intent = ConversationIntents.createBuilderSync(requireContext(), joinGroupSuccess.getGroupRecipient().getId(), joinGroupSuccess.getGroupThreadId())
                                           .build();
        requireActivity().startActivity(intent);

        dismiss();
      }
    );
  }

  @Override
  public void onResume() {
    super.onResume();
    WindowUtil.initializeScreenshotSecurity(requireContext(), requireDialog().getWindow());
  }

  private void updateGroupDescription(@NonNull String name, @NonNull String description) {
    groupDescription.setVisibility(View.VISIBLE);
    groupDescription.setMovementMethod(LongClickMovementMethod.getInstance(requireContext()));
    GroupDescriptionUtil.setText(requireContext(),
                                 groupDescription,
                                 description,
                                 true,
                                 () -> GroupDescriptionDialog.show(getChildFragmentManager(), name, description, true));
  }

  private void showError(FetchGroupDetailsError error) {
    avatar.setVisibility(View.INVISIBLE);
    groupCancelButton.setVisibility(View.GONE);
    groupDetails.setVisibility(View.VISIBLE);
    groupJoinButton.setVisibility(View.VISIBLE);
    groupJoinButton.setText(getString(android.R.string.ok));
    groupJoinButton.setOnClickListener(v -> dismissAllowingStateLoss());

    switch (error) {
      case GroupLinkNotActive:
        groupName.setText(R.string.GroupJoinBottomSheetDialogFragment_cant_join_group);
        groupDetails.setText(R.string.GroupJoinBottomSheetDialogFragment_this_group_link_is_no_longer_valid);
        break;
      case BannedFromGroup:
        groupName.setText(R.string.GroupJoinBottomSheetDialogFragment_cant_join_group);
        groupDetails.setText(R.string.GroupJoinBottomSheetDialogFragment_you_cant_join_this_group_via_the_group_link_because_an_admin_removed_you);
        break;
      case NetworkError:
        groupName.setText(R.string.GroupJoinBottomSheetDialogFragment_link_error);
        groupDetails.setText(R.string.GroupJoinBottomSheetDialogFragment_joining_via_this_link_failed_try_joining_again_later);
        break;
    }
  }

  private @NonNull String errorToMessage(@NonNull JoinGroupError error) {
    switch (error) {
      case GROUP_LINK_NOT_ACTIVE: return getString(R.string.GroupJoinBottomSheetDialogFragment_this_group_link_is_not_active);
      case BANNED               : return getString(R.string.GroupJoinBottomSheetDialogFragment_you_cant_join_this_group_via_the_group_link_because_an_admin_removed_you);
      case NETWORK_ERROR        : return getString(R.string.GroupJoinBottomSheetDialogFragment_encountered_a_network_error);
      default                   : return getString(R.string.GroupJoinBottomSheetDialogFragment_unable_to_join_group_please_try_again_later);
    }
  }

  private GroupInviteLinkUrl getGroupInviteLinkUrl() {
    try {
      //noinspection ConstantConditions
      return GroupInviteLinkUrl.fromUri(requireArguments().getString(ARG_GROUP_INVITE_LINK_URL));
    } catch (GroupInviteLinkUrl.InvalidGroupLinkException | GroupInviteLinkUrl.UnknownGroupLinkVersionException e) {
      throw new AssertionError();
    }
  }

  @Override
  public void show(@NonNull FragmentManager manager, @Nullable String tag) {
    BottomSheetUtil.show(manager, tag, this);
  }

}
