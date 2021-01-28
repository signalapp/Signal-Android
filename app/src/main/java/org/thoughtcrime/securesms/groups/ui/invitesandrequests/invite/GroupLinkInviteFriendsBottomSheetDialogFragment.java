package org.thoughtcrime.securesms.groups.ui.invitesandrequests.invite;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProviders;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.groups.BadGroupIdException;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.recipients.ui.sharablegrouplink.GroupLinkBottomSheetDialogFragment;
import org.thoughtcrime.securesms.util.BottomSheetUtil;
import org.thoughtcrime.securesms.util.ThemeUtil;
import org.thoughtcrime.securesms.util.views.SimpleProgressDialog;

import java.util.Objects;

public final class GroupLinkInviteFriendsBottomSheetDialogFragment extends BottomSheetDialogFragment {

  private static final String TAG = Log.tag(GroupLinkInviteFriendsBottomSheetDialogFragment.class);

  private static final String ARG_GROUP_ID = "group_id";

  private Button       groupLinkEnableAndShareButton;
  private Button       groupLinkShareButton;
  private View         memberApprovalRow;
  private View         memberApprovalRow2;
  private SwitchCompat memberApprovalSwitch;

  private SimpleProgressDialog.DismissibleDialog busyDialog;

  public static void show(@NonNull FragmentManager manager,
                          @NonNull GroupId.V2 groupId)
  {
    GroupLinkInviteFriendsBottomSheetDialogFragment fragment = new GroupLinkInviteFriendsBottomSheetDialogFragment();

    Bundle args = new Bundle();
    args.putString(ARG_GROUP_ID, groupId.toString());
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
    View view = inflater.inflate(R.layout.group_invite_link_enable_and_share_bottom_sheet, container, false);

    groupLinkEnableAndShareButton = view.findViewById(R.id.group_link_enable_and_share_button);
    groupLinkShareButton          = view.findViewById(R.id.group_link_share_button);
    memberApprovalRow             = view.findViewById(R.id.group_link_enable_and_share_approve_new_members_row);
    memberApprovalRow2            = view.findViewById(R.id.group_link_enable_and_share_approve_new_members_row2);
    memberApprovalSwitch          = view.findViewById(R.id.group_link_enable_and_share_approve_new_members_switch);

    view.findViewById(R.id.group_link_enable_and_share_cancel_button).setOnClickListener(v -> dismiss());

    return view;
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    GroupId.V2 groupId = getGroupId();

    GroupLinkInviteFriendsViewModel.Factory factory   = new GroupLinkInviteFriendsViewModel.Factory(requireContext().getApplicationContext(), groupId);
    GroupLinkInviteFriendsViewModel         viewModel = ViewModelProviders.of(this, factory).get(GroupLinkInviteFriendsViewModel.class);

    viewModel.getGroupInviteLinkAndStatus()
             .observe(getViewLifecycleOwner(), groupLinkUrlAndStatus -> {
               if (groupLinkUrlAndStatus.isEnabled()) {
                 groupLinkShareButton.setVisibility(View.VISIBLE);
                 groupLinkEnableAndShareButton.setVisibility(View.INVISIBLE);
                 memberApprovalRow.setVisibility(View.GONE);
                 memberApprovalRow2.setVisibility(View.GONE);

                 groupLinkShareButton.setOnClickListener(v -> shareGroupLinkAndDismiss(groupId));
               } else {
                 memberApprovalRow.setVisibility(View.VISIBLE);
                 memberApprovalRow2.setVisibility(View.VISIBLE);

                 groupLinkEnableAndShareButton.setVisibility(View.VISIBLE);
                 groupLinkShareButton.setVisibility(View.INVISIBLE);
               }
             });

    memberApprovalRow.setOnClickListener(v -> viewModel.toggleMemberApproval());

    viewModel.getMemberApproval()
             .observe(getViewLifecycleOwner(), enabled -> memberApprovalSwitch.setChecked(enabled));

    viewModel.isBusy()
             .observe(getViewLifecycleOwner(), this::setBusy);

    viewModel.getEnableErrors()
             .observe(getViewLifecycleOwner(), error -> {
               Toast.makeText(requireContext(), errorToMessage(error), Toast.LENGTH_SHORT).show();

               if (error == EnableInviteLinkError.NOT_IN_GROUP || error == EnableInviteLinkError.INSUFFICIENT_RIGHTS) {
                 dismiss();
               }
             });

    groupLinkEnableAndShareButton.setOnClickListener(v -> viewModel.enable());

    viewModel.getEnableSuccess()
             .observe(getViewLifecycleOwner(), joinGroupSuccess -> {
                        Log.i(TAG, "Group link enabled, sharing");
                        shareGroupLinkAndDismiss(groupId);
                      }
             );
  }

  protected void shareGroupLinkAndDismiss(@NonNull GroupId.V2 groupId) {
    dismiss();

    GroupLinkBottomSheetDialogFragment.show(requireFragmentManager(), groupId);
  }

  protected GroupId.V2 getGroupId() {
    try {
      return GroupId.parse(Objects.requireNonNull(requireArguments().getString(ARG_GROUP_ID)))
                    .requireV2();
    } catch (BadGroupIdException e) {
      throw new AssertionError(e);
    }
  }

  private void setBusy(boolean isBusy) {
    if (isBusy) {
      if (busyDialog == null) {
        busyDialog = SimpleProgressDialog.showDelayed(requireContext());
      }
    } else {
      if (busyDialog != null) {
        busyDialog.dismiss();
        busyDialog = null;
      }
    }
  }

  private @NonNull String errorToMessage(@NonNull EnableInviteLinkError error) {
    switch (error) {
      case NETWORK_ERROR       : return getString(R.string.GroupInviteLinkEnableAndShareBottomSheetDialogFragment_encountered_a_network_error);
      case INSUFFICIENT_RIGHTS : return getString(R.string.GroupInviteLinkEnableAndShareBottomSheetDialogFragment_you_dont_have_the_right_to_enable_group_link);
      case NOT_IN_GROUP        : return getString(R.string.GroupInviteLinkEnableAndShareBottomSheetDialogFragment_you_are_not_currently_a_member_of_the_group);
      default                  : return getString(R.string.GroupInviteLinkEnableAndShareBottomSheetDialogFragment_unable_to_enable_group_link_please_try_again_later);
    }
  }

  @Override
  public void show(@NonNull FragmentManager manager, @Nullable String tag) {
    BottomSheetUtil.show(manager, tag, this);
  }
}
