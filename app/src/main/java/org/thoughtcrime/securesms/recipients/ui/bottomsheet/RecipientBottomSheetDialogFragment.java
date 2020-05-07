package org.thoughtcrime.securesms.recipients.ui.bottomsheet;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProviders;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.ThemeUtil;

import java.util.Objects;

public final class RecipientBottomSheetDialogFragment extends BottomSheetDialogFragment {

  private static final String ARGS_RECIPIENT_ID = "RECIPIENT_ID";
  private static final String ARGS_GROUP_ID     = "GROUP_ID";

  private RecipientDialogViewModel viewModel;
  private AvatarImageView          avatar;
  private TextView                 fullName;
  private TextView                 usernameNumber;
  private Button                   messageButton;
  private Button                   secureCallButton;
  private Button                   blockButton;
  private Button                   unblockButton;
  private Button                   viewSafetyNumberButton;
  private Button                   makeGroupAdminButton;
  private Button                   removeAdminButton;
  private Button                   removeFromGroupButton;
  private ProgressBar              adminActionBusy;

  public static BottomSheetDialogFragment create(@NonNull RecipientId recipientId,
                                                 @Nullable GroupId groupId)
  {
    Bundle                             args     = new Bundle();
    RecipientBottomSheetDialogFragment fragment = new RecipientBottomSheetDialogFragment();

    args.putString(ARGS_RECIPIENT_ID, recipientId.serialize());
    if (groupId != null) {
      args.putString(ARGS_GROUP_ID, groupId.toString());
    }

    fragment.setArguments(args);

    return fragment;
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    setStyle(DialogFragment.STYLE_NORMAL,
             ThemeUtil.isDarkTheme(requireContext()) ? R.style.Theme_Signal_RecipientBottomSheet
                                                     : R.style.Theme_Signal_RecipientBottomSheet_Light);
    super.onCreate(savedInstanceState);
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.recipient_bottom_sheet, container, false);

    avatar                 = view.findViewById(R.id.recipient_avatar);
    fullName               = view.findViewById(R.id.full_name);
    usernameNumber         = view.findViewById(R.id.username_number);
    messageButton          = view.findViewById(R.id.message_button);
    secureCallButton       = view.findViewById(R.id.secure_call_button);
    blockButton            = view.findViewById(R.id.block_button);
    unblockButton          = view.findViewById(R.id.unblock_button);
    viewSafetyNumberButton = view.findViewById(R.id.view_safety_number_button);
    makeGroupAdminButton   = view.findViewById(R.id.make_group_admin_button);
    removeAdminButton      = view.findViewById(R.id.remove_group_admin_button);
    removeFromGroupButton  = view.findViewById(R.id.remove_from_group_button);
    adminActionBusy        = view.findViewById(R.id.admin_action_busy);

    return view;
  }

  @Override
  public void onViewCreated(@NonNull View fragmentView, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(fragmentView, savedInstanceState);

    Bundle      arguments   = requireArguments();
    RecipientId recipientId = RecipientId.from(Objects.requireNonNull(arguments.getString(ARGS_RECIPIENT_ID)));
    GroupId     groupId     = GroupId.parseNullableOrThrow(arguments.getString(ARGS_GROUP_ID));

    RecipientDialogViewModel.Factory factory = new RecipientDialogViewModel.Factory(requireContext().getApplicationContext(), recipientId, groupId);

    viewModel = ViewModelProviders.of(this, factory).get(RecipientDialogViewModel.class);

    viewModel.getRecipient().observe(getViewLifecycleOwner(), recipient -> {
      avatar.setRecipient(recipient);

      String name = recipient.getProfileName().toString();
      fullName.setText(name);
      fullName.setVisibility(TextUtils.isEmpty(name) ? View.GONE : View.VISIBLE);

      String usernameNumberString = String.format("%s %s", recipient.getUsername().or(""), recipient.getSmsAddress().or(""))
                                          .trim();
      usernameNumber.setText(usernameNumberString);
      usernameNumber.setVisibility(TextUtils.isEmpty(usernameNumberString) ? View.GONE : View.VISIBLE);

      boolean blocked = recipient.isBlocked();
      blockButton.setVisibility(blocked ? View.GONE : View.VISIBLE);
      unblockButton.setVisibility(blocked ? View.VISIBLE : View.GONE);

      secureCallButton.setVisibility(recipient.isRegistered() ? View.VISIBLE : View.GONE);
    });

    viewModel.getAdminActionStatus().observe(getViewLifecycleOwner(), adminStatus -> {
      makeGroupAdminButton.setVisibility(adminStatus.isCanMakeAdmin() ? View.VISIBLE : View.GONE);
      removeAdminButton.setVisibility(adminStatus.isCanMakeNonAdmin() ? View.VISIBLE : View.GONE);
      removeFromGroupButton.setVisibility(adminStatus.isCanRemove() ? View.VISIBLE : View.GONE);
    });

    viewModel.getIdentity().observe(getViewLifecycleOwner(), identityRecord -> {
      viewSafetyNumberButton.setVisibility(identityRecord != null ? View.VISIBLE : View.GONE);

      if (identityRecord != null) {
        viewSafetyNumberButton.setOnClickListener(view -> {
          dismiss();
          viewModel.onViewSafetyNumberClicked(requireActivity(), identityRecord);
        });
      }
    });

    avatar.setOnClickListener(view -> {
      dismiss();
      viewModel.onAvatarClicked(requireActivity());
    });

    messageButton.setOnClickListener(view -> {
      dismiss();
      viewModel.onMessageClicked(requireActivity());
    });

    secureCallButton.setOnClickListener(view -> {
      dismiss();
      viewModel.onSecureCallClicked(requireActivity());
    });

    blockButton.setOnClickListener(view -> viewModel.onBlockClicked(requireActivity()));
    unblockButton.setOnClickListener(view -> viewModel.onUnblockClicked(requireActivity()));

    makeGroupAdminButton.setOnClickListener(view -> viewModel.onMakeGroupAdminClicked(requireActivity()));
    removeAdminButton.setOnClickListener(view -> viewModel.onRemoveGroupAdminClicked(requireActivity()));

    removeFromGroupButton.setOnClickListener(view -> viewModel.onRemoveFromGroupClicked(requireActivity(), this::dismiss));

    viewModel.getAdminActionBusy().observe(getViewLifecycleOwner(), busy -> {
      adminActionBusy.setVisibility(busy ? View.VISIBLE : View.GONE);

      makeGroupAdminButton.setEnabled(!busy);
      removeAdminButton.setEnabled(!busy);
      removeFromGroupButton.setEnabled(!busy);
    });
  }
}
