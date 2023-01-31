package org.thoughtcrime.securesms.groups.ui.migration;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.groups.ui.GroupMemberListView;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.BottomSheetUtil;
import org.thoughtcrime.securesms.util.ThemeUtil;
import org.thoughtcrime.securesms.util.WindowUtil;
import org.thoughtcrime.securesms.util.views.SimpleProgressDialog;

/**
 * A bottom sheet that allows a user to initiation a manual GV1->GV2 migration. Will show the user
 * the members that will be invited/left behind.
 */
public final class GroupsV1MigrationInitiationBottomSheetDialogFragment extends BottomSheetDialogFragment {

  private static final String KEY_GROUP_RECIPIENT_ID = "group_recipient_id";

  private GroupsV1MigrationInitiationViewModel viewModel;
  private GroupMemberListView                  inviteList;
  private TextView                             inviteTitle;
  private View                                 inviteContainer;
  private GroupMemberListView                  ineligibleList;
  private TextView                             ineligibleTitle;
  private View                                 ineligibleContainer;
  private View                                 upgradeButton;
  private View                                 spinner;

  public static void showForInitiation(@NonNull FragmentManager manager, @NonNull RecipientId groupRecipientId) {
    Bundle args = new Bundle();
    args.putParcelable(KEY_GROUP_RECIPIENT_ID, groupRecipientId);

    GroupsV1MigrationInitiationBottomSheetDialogFragment fragment = new GroupsV1MigrationInitiationBottomSheetDialogFragment();
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
    return inflater.inflate(R.layout.groupsv1_migration_bottom_sheet, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    this.inviteContainer     = view.findViewById(R.id.gv1_migrate_invite_container);
    this.inviteTitle         = view.findViewById(R.id.gv1_migrate_invite_title);
    this.inviteList          = view.findViewById(R.id.gv1_migrate_invite_list);
    this.ineligibleContainer = view.findViewById(R.id.gv1_migrate_ineligible_container);
    this.ineligibleTitle     = view.findViewById(R.id.gv1_migrate_ineligible_title);
    this.ineligibleList      = view.findViewById(R.id.gv1_migrate_ineligible_list);
    this.upgradeButton       = view.findViewById(R.id.gv1_migrate_upgrade_button);
    this.spinner             = view.findViewById(R.id.gv1_migrate_spinner);

    inviteList.initializeAdapter(getViewLifecycleOwner());
    ineligibleList.initializeAdapter(getViewLifecycleOwner());

    inviteList.setNestedScrollingEnabled(false);
    ineligibleList.setNestedScrollingEnabled(false);

    //noinspection ConstantConditions
    RecipientId groupRecipientId = getArguments().getParcelable(KEY_GROUP_RECIPIENT_ID);

    //noinspection ConstantConditions
    viewModel = new ViewModelProvider(this, new GroupsV1MigrationInitiationViewModel.Factory(groupRecipientId)).get(GroupsV1MigrationInitiationViewModel.class);
    viewModel.getMigrationState().observe(getViewLifecycleOwner(), this::onMigrationStateChanged);

    upgradeButton.setEnabled(false);
    upgradeButton.setOnClickListener(v -> onUpgradeClicked());
    view.findViewById(R.id.gv1_migrate_cancel_button).setOnClickListener(v -> dismiss());
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

  private void onMigrationStateChanged(@NonNull MigrationState migrationState) {
    if (migrationState.getNeedsInvite().size() > 0) {
      inviteContainer.setVisibility(View.VISIBLE);
      inviteTitle.setText(getResources().getQuantityText(R.plurals.GroupsV1MigrationInitiation_these_members_will_need_to_accept_an_invite, migrationState.getNeedsInvite().size()));
      inviteList.setDisplayOnlyMembers(migrationState.getNeedsInvite());
    } else {
      inviteContainer.setVisibility(View.GONE);
    }

    if (migrationState.getIneligible().size() > 0) {
      ineligibleContainer.setVisibility(View.VISIBLE);
      ineligibleTitle.setText(getResources().getQuantityText(R.plurals.GroupsV1MigrationInitiation_these_members_are_not_capable_of_joining_new_groups, migrationState.getIneligible().size()));
      ineligibleList.setDisplayOnlyMembers(migrationState.getIneligible());
    } else {
      ineligibleContainer.setVisibility(View.GONE);
    }

    upgradeButton.setEnabled(true);
    spinner.setVisibility(View.GONE);
  }

  private void onUpgradeClicked() {
    AlertDialog dialog = SimpleProgressDialog.show(requireContext());
    viewModel.onUpgradeClicked().observe(getViewLifecycleOwner(), result -> {
      switch (result) {
        case SUCCESS:
          dismiss();
          break;
        case FAILURE_GENERAL:
          Toast.makeText(requireContext(), R.string.GroupsV1MigrationInitiation_failed_to_upgrade, Toast.LENGTH_SHORT).show();
          dismiss();
          break;
        case FAILURE_NETWORK:
          Toast.makeText(requireContext(), R.string.GroupsV1MigrationInitiation_encountered_a_network_error, Toast.LENGTH_SHORT).show();
          dismiss();
          break;
        default:
          throw new IllegalStateException();
      }
      dialog.dismiss();
    });
  }
}
