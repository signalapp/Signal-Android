package org.thoughtcrime.securesms.groups.ui.creategroup.details;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProviders;
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat;

import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.dd.CircularProgressButton;

import org.signal.core.util.EditTextUtil;
import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.groups.ui.GroupMemberListView;
import org.thoughtcrime.securesms.groups.ui.creategroup.dialogs.NonGv2MemberDialog;
import org.thoughtcrime.securesms.mediasend.AvatarSelectionActivity;
import org.thoughtcrime.securesms.mediasend.AvatarSelectionBottomSheetDialogFragment;
import org.thoughtcrime.securesms.mediasend.Media;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.profiles.AvatarHelper;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.BitmapUtil;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.text.AfterTextChanged;
import org.thoughtcrime.securesms.util.views.LearnMoreTextView;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class AddGroupDetailsFragment extends LoggingFragment {

  private static final int   AVATAR_PLACEHOLDER_INSET_DP = 18;
  private static final short REQUEST_CODE_AVATAR         = 27621;

  private CircularProgressButton   create;
  private Callback                 callback;
  private AddGroupDetailsViewModel viewModel;
  private Drawable                 avatarPlaceholder;
  private EditText                 name;
  private Toolbar                  toolbar;

  @Override
  public void onAttach(@NonNull Context context) {
    super.onAttach(context);

    if (context instanceof Callback) {
      callback = (Callback) context;
    } else {
      throw new ClassCastException("Parent context should implement AddGroupDetailsFragment.Callback");
    }
  }

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater,
                                     @Nullable ViewGroup container,
                                     @Nullable Bundle savedInstanceState)
  {
    return inflater.inflate(R.layout.add_group_details_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    create  = view.findViewById(R.id.create);
    name    = view.findViewById(R.id.name);
    toolbar = view.findViewById(R.id.toolbar);

    setCreateEnabled(false, false);

    GroupMemberListView members    = view.findViewById(R.id.member_list);
    ImageView           avatar     = view.findViewById(R.id.group_avatar);
    View                mmsWarning = view.findViewById(R.id.mms_warning);
    LearnMoreTextView   gv2Warning = view.findViewById(R.id.gv2_warning);
    View                addLater   = view.findViewById(R.id.add_later);

    avatarPlaceholder = VectorDrawableCompat.create(getResources(), R.drawable.ic_camera_outline_32_ultramarine, requireActivity().getTheme());

    if (savedInstanceState == null) {
      avatar.setImageDrawable(new InsetDrawable(avatarPlaceholder, ViewUtil.dpToPx(AVATAR_PLACEHOLDER_INSET_DP)));
    }

    initializeViewModel();

    avatar.setOnClickListener(v -> showAvatarSelectionBottomSheet());
    members.setRecipientClickListener(this::handleRecipientClick);
    EditTextUtil.addGraphemeClusterLimitFilter(name, FeatureFlags.getMaxGroupNameGraphemeLength());
    name.addTextChangedListener(new AfterTextChanged(editable -> viewModel.setName(editable.toString())));
    toolbar.setNavigationOnClickListener(unused -> callback.onNavigationButtonPressed());
    create.setOnClickListener(v -> handleCreateClicked());
    viewModel.getMembers().observe(getViewLifecycleOwner(), list -> {
      addLater.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
      members.setMembers(list);
    });
    viewModel.getCanSubmitForm().observe(getViewLifecycleOwner(), isFormValid -> setCreateEnabled(isFormValid, true));
    viewModel.getIsMms().observe(getViewLifecycleOwner(), isMms -> {
      mmsWarning.setVisibility(isMms ? View.VISIBLE : View.GONE);
      name.setHint(isMms ? R.string.AddGroupDetailsFragment__group_name_optional : R.string.AddGroupDetailsFragment__group_name_required);
      toolbar.setTitle(isMms ? R.string.AddGroupDetailsFragment__create_group : R.string.AddGroupDetailsFragment__name_this_group);
    });
    viewModel.getNonGv2CapableMembers().observe(getViewLifecycleOwner(), nonGv2CapableMembers -> {
      boolean forcedMigration = FeatureFlags.groupsV1ForcedMigration();

      int stringRes = forcedMigration ? R.plurals.AddGroupDetailsFragment__d_members_do_not_support_new_groups_so_this_group_cannot_be_created
                                      : R.plurals.AddGroupDetailsFragment__d_members_do_not_support_new_groups;

      gv2Warning.setVisibility(nonGv2CapableMembers.isEmpty() ? View.GONE : View.VISIBLE);
      gv2Warning.setText(requireContext().getResources().getQuantityString(stringRes, nonGv2CapableMembers.size(), nonGv2CapableMembers.size()));
      gv2Warning.setLearnMoreVisible(true);
      gv2Warning.setOnLinkClickListener(v -> NonGv2MemberDialog.showNonGv2Members(requireContext(), nonGv2CapableMembers, forcedMigration));
    });
    viewModel.getAvatar().observe(getViewLifecycleOwner(), avatarBytes -> {
      if (avatarBytes == null) {
        avatar.setImageDrawable(new InsetDrawable(avatarPlaceholder, ViewUtil.dpToPx(AVATAR_PLACEHOLDER_INSET_DP)));
      } else {
        GlideApp.with(this)
                .load(avatarBytes)
                .circleCrop()
                .skipMemoryCache(true)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .into(avatar);
      }
    });

    name.requestFocus();
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    if (requestCode == REQUEST_CODE_AVATAR && resultCode == Activity.RESULT_OK && data != null) {

      if (data.getBooleanExtra("delete", false)) {
        viewModel.setAvatar(null);
        return;
      }

      final Media                                     result         = data.getParcelableExtra(AvatarSelectionActivity.EXTRA_MEDIA);
      final DecryptableStreamUriLoader.DecryptableUri decryptableUri = new DecryptableStreamUriLoader.DecryptableUri(result.getUri());

      GlideApp.with(this)
              .asBitmap()
              .load(decryptableUri)
              .skipMemoryCache(true)
              .diskCacheStrategy(DiskCacheStrategy.NONE)
              .centerCrop()
              .override(AvatarHelper.AVATAR_DIMENSIONS, AvatarHelper.AVATAR_DIMENSIONS)
              .into(new CustomTarget<Bitmap>() {
                @Override
                public void onResourceReady(@NonNull Bitmap resource, Transition<? super Bitmap> transition) {
                  viewModel.setAvatar(Objects.requireNonNull(BitmapUtil.toByteArray(resource)));
                }

                @Override
                public void onLoadCleared(@Nullable Drawable placeholder) {
                }
              });
    } else {
      super.onActivityResult(requestCode, resultCode, data);
    }
  }

  private void initializeViewModel() {
    AddGroupDetailsFragmentArgs      args       = AddGroupDetailsFragmentArgs.fromBundle(requireArguments());
    AddGroupDetailsRepository        repository = new AddGroupDetailsRepository(requireContext());
    AddGroupDetailsViewModel.Factory factory    = new AddGroupDetailsViewModel.Factory(Arrays.asList(args.getRecipientIds()), repository);

    viewModel = ViewModelProviders.of(this, factory).get(AddGroupDetailsViewModel.class);

    viewModel.getGroupCreateResult().observe(getViewLifecycleOwner(), this::handleGroupCreateResult);
  }

  private void handleCreateClicked() {
    create.setClickable(false);
    create.setIndeterminateProgressMode(true);
    create.setProgress(50);

    viewModel.create();
  }

  private void handleRecipientClick(@NonNull Recipient recipient) {
    new AlertDialog.Builder(requireContext())
                   .setMessage(getString(R.string.AddGroupDetailsFragment__remove_s_from_this_group, recipient.getDisplayName(requireContext())))
                   .setCancelable(true)
                   .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.cancel())
                   .setPositiveButton(R.string.AddGroupDetailsFragment__remove, (dialog, which) -> {
                     viewModel.delete(recipient.getId());
                     dialog.dismiss();
                   })
                   .show();
  }

  private void handleGroupCreateResult(@NonNull GroupCreateResult groupCreateResult) {
    groupCreateResult.consume(this::handleGroupCreateResultSuccess, this::handleGroupCreateResultError);
  }

  private void handleGroupCreateResultSuccess(@NonNull GroupCreateResult.Success success) {
    callback.onGroupCreated(success.getGroupRecipient().getId(), success.getThreadId(), success.getInvitedMembers());
  }

  private void handleGroupCreateResultError(@NonNull GroupCreateResult.Error error) {
    switch (error.getErrorType()) {
      case ERROR_IO:
      case ERROR_BUSY:
        toast(R.string.AddGroupDetailsFragment__try_again_later);
        break;
      case ERROR_FAILED:
        toast(R.string.AddGroupDetailsFragment__group_creation_failed);
        break;
      case ERROR_INVALID_NAME:
        name.setError(getString(R.string.AddGroupDetailsFragment__this_field_is_required));
        break;
      default:
        throw new IllegalStateException("Unexpected error: " + error.getErrorType().name());
    }
  }

  private void toast(@StringRes int toastStringId) {
    Toast.makeText(requireContext(), toastStringId, Toast.LENGTH_SHORT)
         .show();
  }

  private void setCreateEnabled(boolean isEnabled, boolean animate) {
    if (create.isEnabled() == isEnabled) {
      return;
    }

    create.setEnabled(isEnabled);
    create.animate()
          .setDuration(animate ? 300 : 0)
          .alpha(isEnabled ? 1f : 0.5f);
  }

  private void showAvatarSelectionBottomSheet() {
    AvatarSelectionBottomSheetDialogFragment.create(viewModel.hasAvatar(), true, REQUEST_CODE_AVATAR, true)
                                            .show(getChildFragmentManager(), "BOTTOM");
  }

  public interface Callback {
    void onGroupCreated(@NonNull RecipientId recipientId, long threadId, @NonNull List<Recipient> invitedMembers);
    void onNavigationButtonPressed();
  }
}
