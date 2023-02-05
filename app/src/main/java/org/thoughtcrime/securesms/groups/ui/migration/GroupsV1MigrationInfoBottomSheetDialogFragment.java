package org.thoughtcrime.securesms.groups.ui.migration;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.groups.GroupMigrationMembershipChange;
import org.thoughtcrime.securesms.groups.ui.GroupMemberListView;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.BottomSheetUtil;
import org.thoughtcrime.securesms.util.ThemeUtil;
import org.thoughtcrime.securesms.util.WindowUtil;

import java.util.List;

/**
 * Shows more info about a GV1->GV2 migration event. Looks similar to
 * {@link GroupsV1MigrationInitiationBottomSheetDialogFragment}, but only displays static data.
 */
public final class GroupsV1MigrationInfoBottomSheetDialogFragment extends BottomSheetDialogFragment {

  private static final String KEY_MEMBERSHIP_CHANGE = "membership_change";

  private GroupsV1MigrationInfoViewModel viewModel;
  private GroupMemberListView            pendingList;
  private TextView                       pendingTitle;
  private View                           pendingContainer;
  private GroupMemberListView            droppedList;
  private TextView                       droppedTitle;
  private View                           droppedContainer;

  public static void show(@NonNull FragmentManager manager, @NonNull GroupMigrationMembershipChange membershipChange) {
    Bundle args = new Bundle();
    args.putString(KEY_MEMBERSHIP_CHANGE, membershipChange.serialize());

    GroupsV1MigrationInfoBottomSheetDialogFragment fragment = new GroupsV1MigrationInfoBottomSheetDialogFragment();
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
    return inflater.inflate(R.layout.groupsv1_migration_learn_more_bottom_sheet, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    this.pendingContainer = view.findViewById(R.id.gv1_learn_more_pending_container);
    this.pendingTitle     = view.findViewById(R.id.gv1_learn_more_pending_title);
    this.pendingList      = view.findViewById(R.id.gv1_learn_more_pending_list);
    this.droppedContainer = view.findViewById(R.id.gv1_learn_more_dropped_container);
    this.droppedTitle     = view.findViewById(R.id.gv1_learn_more_dropped_title);
    this.droppedList      = view.findViewById(R.id.gv1_learn_more_dropped_list);

    pendingList.initializeAdapter(getViewLifecycleOwner());
    droppedList.initializeAdapter(getViewLifecycleOwner());

    //noinspection ConstantConditions
    GroupMigrationMembershipChange membershipChange = GroupMigrationMembershipChange.deserialize(getArguments().getString(KEY_MEMBERSHIP_CHANGE));

    this.viewModel = new ViewModelProvider(this, new GroupsV1MigrationInfoViewModel.Factory(membershipChange)).get(GroupsV1MigrationInfoViewModel.class);
    viewModel.getPendingMembers().observe(getViewLifecycleOwner(), this::onPendingMembersChanged);
    viewModel.getDroppedMembers().observe(getViewLifecycleOwner(), this::onDroppedMembersChanged);

    view.findViewById(R.id.gv1_learn_more_ok_button).setOnClickListener(v -> dismiss());
  }

  @Override
  public void onResume() {
    super.onResume();
    WindowUtil.initializeScreenshotSecurity(requireContext(), requireDialog().getWindow());
  }

  @Override
  public void show(@NonNull FragmentManager manager, @Nullable String tag) {
    BottomSheetUtil.show(manager, tag, this);
  }

  private void onPendingMembersChanged(@NonNull List<Recipient> pendingMembers) {
    if (pendingMembers.size() == 1 && pendingMembers.get(0).isSelf()) {
      pendingContainer.setVisibility(View.VISIBLE);
      pendingTitle.setText(R.string.GroupsV1MigrationLearnMore_you_will_need_to_accept_an_invite_to_join_this_group_again);
    } else if (pendingMembers.size() > 0) {
      pendingContainer.setVisibility(View.VISIBLE);
      pendingTitle.setText(getResources().getQuantityText(R.plurals.GroupsV1MigrationLearnMore_these_members_will_need_to_accept_an_invite, pendingMembers.size()));
      pendingList.setDisplayOnlyMembers(pendingMembers);
    } else {
      pendingContainer.setVisibility(View.GONE);
    }
  }

  private void onDroppedMembersChanged(@NonNull List<Recipient> droppedMembers) {
    if (droppedMembers.size() > 0) {
      droppedContainer.setVisibility(View.VISIBLE);
      droppedTitle.setText(getResources().getQuantityText(R.plurals.GroupsV1MigrationLearnMore_these_members_were_removed_from_the_group, droppedMembers.size()));
      droppedList.setDisplayOnlyMembers(droppedMembers);
    } else {
      droppedContainer.setVisibility(View.GONE);
    }
  }
}
