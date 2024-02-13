package org.thoughtcrime.securesms.groups.ui.invitesandrequests.requesting;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.ui.AdminActionsListener;
import org.thoughtcrime.securesms.groups.ui.GroupMemberEntry;
import org.thoughtcrime.securesms.groups.ui.GroupMemberListView;
import org.thoughtcrime.securesms.groups.v2.GroupLinkUrlAndStatus;
import org.thoughtcrime.securesms.recipients.ui.bottomsheet.RecipientBottomSheetDialogFragment;
import org.thoughtcrime.securesms.util.BottomSheetUtil;

import java.util.Objects;

/**
 * Lists and allows approval/denial of people requesting access to the group.
 */
public class RequestingMembersFragment extends Fragment {

  private static final String GROUP_ID = "GROUP_ID";

  private RequestingMemberInvitesViewModel viewModel;
  private GroupMemberListView              requestingMembers;
  private View                             noRequestingMessage;
  private View                             requestingExplanation;

  public static RequestingMembersFragment newInstance(@NonNull GroupId.V2 groupId) {
    RequestingMembersFragment fragment = new RequestingMembersFragment();
    Bundle                    args     = new Bundle();

    args.putString(GROUP_ID, groupId.toString());
    fragment.setArguments(args);

    return fragment;
  }

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.group_requesting_member_fragment, container, false);

    requestingMembers     = view.findViewById(R.id.requesting_members);
    noRequestingMessage   = view.findViewById(R.id.no_requesting);
    requestingExplanation = view.findViewById(R.id.requesting_members_explain);

    requestingMembers.initializeAdapter(getViewLifecycleOwner());

    requestingMembers.setRecipientClickListener(recipient -> {
      RecipientBottomSheetDialogFragment.show(requireActivity().getSupportFragmentManager(), recipient.getId(), null);
    });

    requestingMembers.setAdminActionsListener(new AdminActionsListener() {

      @Override
      public void onRevokeInvite(@NonNull GroupMemberEntry.PendingMember pendingMember) {
        throw new UnsupportedOperationException();
      }

      @Override
      public void onRevokeAllInvites(@NonNull GroupMemberEntry.UnknownPendingMemberCount pendingMembers) {
        throw new UnsupportedOperationException();
      }

      @Override
      public void onApproveRequest(@NonNull GroupMemberEntry.RequestingMember requestingMember) {
        //noinspection CodeBlock2Expr
        RequestConfirmationDialog.showApprove(requireContext(), requestingMember.getRequester(), () -> {
          viewModel.approveRequestFor(requestingMember);
        });
      }

      @Override
      public void onDenyRequest(@NonNull GroupMemberEntry.RequestingMember requestingMember) {
        GroupLinkUrlAndStatus linkStatus  = viewModel.getInviteLink().getValue();
        boolean               linkEnabled = linkStatus == null || linkStatus.isEnabled();

        //noinspection CodeBlock2Expr
        RequestConfirmationDialog.showDeny(requireContext(), requestingMember.getRequester(), linkEnabled, () -> {
          viewModel.denyRequestFor(requestingMember);
        });
      }
    });

    return view;
  }

  @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    GroupId.V2 groupId = GroupId.parseOrThrow(Objects.requireNonNull(requireArguments().getString(GROUP_ID))).requireV2();

    RequestingMemberInvitesViewModel.Factory factory = new RequestingMemberInvitesViewModel.Factory(requireContext(), groupId);

    viewModel = new ViewModelProvider(this, factory).get(RequestingMemberInvitesViewModel.class);

    viewModel.getRequesting().observe(getViewLifecycleOwner(), requesting -> {
      requestingMembers.setMembers(requesting);
      noRequestingMessage.setVisibility(requesting.isEmpty() ? View.VISIBLE : View.GONE);
      requestingExplanation.setVisibility(requesting.isEmpty() ? View.GONE : View.VISIBLE);
    });

    viewModel.getToasts().observe(getViewLifecycleOwner(), toast -> Toast.makeText(requireContext(), toast, Toast.LENGTH_SHORT).show());
  }
}
