package org.thoughtcrime.securesms.recipients.ui.sharablegrouplink;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProviders;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.util.ThemeUtil;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.views.SimpleProgressDialog;

public final class ShareableGroupLinkDialogFragment extends DialogFragment {

  private static final String ARG_GROUP_ID = "group_id";

  private ShareableGroupLinkViewModel            viewModel;
  private GroupId.V2                             groupId;
  private SimpleProgressDialog.DismissibleDialog dialog;

  public static DialogFragment create(@NonNull GroupId.V2 groupId) {
    DialogFragment fragment = new ShareableGroupLinkDialogFragment();
    Bundle         args     = new Bundle();

    args.putString(ARG_GROUP_ID, groupId.toString());
    fragment.setArguments(args);

    return fragment;
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setStyle(STYLE_NO_FRAME, R.style.Signal_DayNight_Dialog_Animated);
  }

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater,
                                     @Nullable ViewGroup container,
                                     @Nullable Bundle savedInstanceState)
  {
    return inflater.inflate(R.layout.shareable_group_link_dialog_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    initializeViewModel();
    initializeViews(view);
  }

  private void initializeViewModel() {
    //noinspection ConstantConditions
    groupId = GroupId.parseOrThrow(requireArguments().getString(ARG_GROUP_ID)).requireV2();

    ShareableGroupLinkRepository        repository = new ShareableGroupLinkRepository(requireContext(), groupId);
    ShareableGroupLinkViewModel.Factory factory    = new ShareableGroupLinkViewModel.Factory(groupId, repository);

    viewModel = ViewModelProviders.of(this, factory).get(ShareableGroupLinkViewModel.class);
  }

  private void initializeViews(@NonNull View view) {
    SwitchCompat shareableGroupLinkSwitch     = view.findViewById(R.id.shareable_group_link_enable_switch);
    TextView     shareableGroupLinkDisplay    = view.findViewById(R.id.shareable_group_link_display);
    View         shareableGroupLinkDisplayRow = view.findViewById(R.id.shareable_group_link_display_row);
    SwitchCompat approveNewMembersSwitch      = view.findViewById(R.id.shareable_group_link_approve_new_members_switch);
    View         shareableGroupLinkRow        = view.findViewById(R.id.shareable_group_link_row);
    View         shareRow                     = view.findViewById(R.id.shareable_group_link_share_row);
    View         resetLinkRow                 = view.findViewById(R.id.shareable_group_link_reset_link_row);
    View         approveNewMembersRow         = view.findViewById(R.id.shareable_group_link_approve_new_members_row);
    View         membersSectionHeader         = view.findViewById(R.id.shareable_group_link_member_requests_section_header);
    View         descriptionRow               = view.findViewById(R.id.shareable_group_link_display_row2);

    Toolbar toolbar = view.findViewById(R.id.shareable_group_link_toolbar);

    toolbar.setNavigationOnClickListener(v -> dismissAllowingStateLoss());

    viewModel.getGroupLink().observe(getViewLifecycleOwner(), groupLink -> {
      shareableGroupLinkSwitch.setChecked(groupLink.isEnabled());
      approveNewMembersSwitch.setChecked(groupLink.isRequiresApproval());
      shareableGroupLinkDisplay.setText(formatForFullWidthWrapping(groupLink.getUrl()));

      shareableGroupLinkDisplayRow.setVisibility(groupLink.isEnabled() ? View.VISIBLE : View.GONE);
      ViewUtil.setEnabledRecursive(shareRow, groupLink.isEnabled());
      ViewUtil.setEnabledRecursive(resetLinkRow, groupLink.isEnabled());
      ViewUtil.setEnabledRecursive(membersSectionHeader, groupLink.isEnabled());
      ViewUtil.setEnabledRecursive(approveNewMembersRow, groupLink.isEnabled());
      ViewUtil.setEnabledRecursive(descriptionRow, groupLink.isEnabled());
    });

    shareRow.setOnClickListener(v -> GroupLinkBottomSheetDialogFragment.show(requireFragmentManager(), groupId));

    viewModel.getCanEdit().observe(getViewLifecycleOwner(), canEdit -> {
      if (canEdit) {
        shareableGroupLinkRow.setOnClickListener(v -> viewModel.onToggleGroupLink());
        approveNewMembersRow.setOnClickListener(v -> viewModel.onToggleApproveMembers());
        resetLinkRow.setOnClickListener(v -> onResetGroupLink());
      } else {
        shareableGroupLinkRow.setOnClickListener(v -> toast(R.string.ManageGroupActivity_only_admins_can_enable_or_disable_the_sharable_group_link));
        approveNewMembersRow.setOnClickListener(v -> toast(R.string.ManageGroupActivity_only_admins_can_enable_or_disable_the_option_to_approve_new_members));
        resetLinkRow.setOnClickListener(v -> toast(R.string.ManageGroupActivity_only_admins_can_reset_the_sharable_group_link));
      }
    });

    viewModel.getToasts().observe(getViewLifecycleOwner(), this::toast);

    viewModel.getBusy().observe(getViewLifecycleOwner(), busy -> {
      if (busy) {
        if (dialog == null) {
          dialog = SimpleProgressDialog.showDelayed(requireContext());
        }
      } else {
        if (dialog != null) {
          dialog.dismiss();
          dialog = null;
        }
      }
    });
  }

  private void onResetGroupLink() {
    new AlertDialog.Builder(requireContext())
                   .setMessage(R.string.ShareableGroupLinkDialogFragment__are_you_sure_you_want_to_reset_the_group_link)
                   .setPositiveButton(R.string.ShareableGroupLinkDialogFragment__reset_link, (dialog, which) -> viewModel.onResetLink())
                   .setNegativeButton(android.R.string.cancel, null)
                   .show();
  }

  protected void toast(@StringRes int message) {
    Toast.makeText(requireContext(), getString(message), Toast.LENGTH_SHORT).show();
  }

  /**
   * Inserts zero width space characters between each character in the original ensuring it takes
   * the full width of the TextView.
   */
  private static CharSequence formatForFullWidthWrapping(@NonNull String url) {
    char[] chars = new char[url.length() * 2];

    for (int i = 0; i < url.length(); i++) {
      chars[i * 2]     = url.charAt(i);
      chars[i * 2 + 1] = '\u200B';
    }

    return new String(chars);
  }
}
