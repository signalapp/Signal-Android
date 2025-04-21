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
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat;

import com.airbnb.lottie.SimpleColorFilter;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.signal.core.util.EditTextUtil;
import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.avatar.picker.AvatarPickerFragment;
import org.thoughtcrime.securesms.components.settings.app.privacy.expire.ExpireTimerSettingsFragment;
import org.thoughtcrime.securesms.groups.ui.GroupMemberListView;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.mediasend.Media;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader;
import org.thoughtcrime.securesms.profiles.AvatarHelper;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.ui.disappearingmessages.RecipientDisappearingMessagesActivity;
import org.thoughtcrime.securesms.util.BitmapUtil;
import org.thoughtcrime.securesms.util.ExpirationUtil;
import org.thoughtcrime.securesms.util.RemoteConfig;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.navigation.SafeNavigation;
import org.thoughtcrime.securesms.util.text.AfterTextChanged;
import org.thoughtcrime.securesms.util.views.CircularProgressMaterialButton;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class AddGroupDetailsFragment extends LoggingFragment {

  private static final int   AVATAR_PLACEHOLDER_INSET_DP = 18;
  private static final short REQUEST_DISAPPEARING_TIMER  = 28621;

  private CircularProgressMaterialButton create;
  private Callback                       callback;
  private AddGroupDetailsViewModel       viewModel;
  private Drawable                       avatarPlaceholder;
  private EditText                       name;
  private Toolbar                        toolbar;
  private View                           disappearingMessagesRow;

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
    create                  = view.findViewById(R.id.create);
    name                    = view.findViewById(R.id.name);
    toolbar                 = view.findViewById(R.id.toolbar);
    disappearingMessagesRow = view.findViewById(R.id.group_disappearing_messages_row);

    setCreateEnabled(false);

    GroupMemberListView members                  = view.findViewById(R.id.member_list);
    ImageView           avatar                   = view.findViewById(R.id.group_avatar);
    View                mmsWarning               = view.findViewById(R.id.mms_warning);
    TextView            mmsWarningText           = view.findViewById(R.id.mms_warning_text);
    View                addLater                 = view.findViewById(R.id.add_later);
    TextView            disappearingMessageValue = view.findViewById(R.id.group_disappearing_messages_value);

    members.initializeAdapter(getViewLifecycleOwner());
    avatarPlaceholder = Objects.requireNonNull(VectorDrawableCompat.create(getResources(), R.drawable.ic_camera_outline_24, requireActivity().getTheme()));
    avatarPlaceholder.setColorFilter(new SimpleColorFilter(ContextCompat.getColor(requireContext(), R.color.signal_icon_tint_primary)));

    if (savedInstanceState == null) {
      avatar.setImageDrawable(new InsetDrawable(avatarPlaceholder, ViewUtil.dpToPx(AVATAR_PLACEHOLDER_INSET_DP)));
    }

    initializeViewModel();

    avatar.setOnClickListener(v -> showAvatarPicker());
    members.setRecipientClickListener(this::handleRecipientClick);
    EditTextUtil.addGraphemeClusterLimitFilter(name, RemoteConfig.getMaxGroupNameGraphemeLength());
    name.addTextChangedListener(new AfterTextChanged(editable -> viewModel.setName(editable.toString())));
    toolbar.setNavigationOnClickListener(unused -> callback.onNavigationButtonPressed());
    create.setOnClickListener(v -> handleCreateClicked());
    viewModel.getMembers().observe(getViewLifecycleOwner(), list -> {
      addLater.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
      members.setMembers(list);
    });
    viewModel.getCanSubmitForm().observe(getViewLifecycleOwner(), this::setCreateEnabled);
    viewModel.getIsMms().observe(getViewLifecycleOwner(), isMms -> {
      disappearingMessagesRow.setVisibility(isMms ? View.GONE : View.VISIBLE);
      mmsWarning.setVisibility(isMms ? View.VISIBLE : View.GONE);
      mmsWarningText.setText(R.string.AddGroupDetailsFragment__youve_selected_a_contact_that_doesnt_support_signal_groups_mms_removal);
      name.setHint(isMms ? R.string.AddGroupDetailsFragment__group_name_optional : R.string.AddGroupDetailsFragment__group_name_required);
      toolbar.setTitle(isMms ? R.string.AddGroupDetailsFragment__create_group : R.string.AddGroupDetailsFragment__name_this_group);
    });
    viewModel.getAvatar().observe(getViewLifecycleOwner(), avatarBytes -> {
      if (avatarBytes == null) {
        avatar.setImageDrawable(new InsetDrawable(avatarPlaceholder, ViewUtil.dpToPx(AVATAR_PLACEHOLDER_INSET_DP)));
      } else {
        Glide.with(this)
                .load(avatarBytes)
                .circleCrop()
                .skipMemoryCache(true)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .into(avatar);
      }
    });

    viewModel.getDisappearingMessagesTimer().observe(getViewLifecycleOwner(), timer -> disappearingMessageValue.setText(ExpirationUtil.getExpirationDisplayValue(requireContext(), timer)));
    disappearingMessagesRow.setOnClickListener(v -> {
      startActivityForResult(RecipientDisappearingMessagesActivity.forCreateGroup(requireContext(), viewModel.getDisappearingMessagesTimer().getValue()), REQUEST_DISAPPEARING_TIMER);
    });

    name.requestFocus();

    getParentFragmentManager().setFragmentResultListener(AvatarPickerFragment.REQUEST_KEY_SELECT_AVATAR,
                                                         getViewLifecycleOwner(),
                                                         (key, bundle) -> handleMediaResult(bundle));
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    if (requestCode == REQUEST_DISAPPEARING_TIMER && resultCode == Activity.RESULT_OK && data != null) {
      viewModel.setDisappearingMessageTimer(data.getIntExtra(ExpireTimerSettingsFragment.FOR_RESULT_VALUE, SignalStore.settings().getUniversalExpireTimer()));
    } else {
      super.onActivityResult(requestCode, resultCode, data);
    }
  }

  private void handleMediaResult(Bundle data) {
    if (data.getBoolean(AvatarPickerFragment.SELECT_AVATAR_CLEAR)) {
      viewModel.setAvatarMedia(null);
      viewModel.setAvatar(null);
      return;
    }

    final Media result                                             = data.getParcelable(AvatarPickerFragment.SELECT_AVATAR_MEDIA);
    final DecryptableStreamUriLoader.DecryptableUri decryptableUri = new DecryptableStreamUriLoader.DecryptableUri(result.getUri());

    viewModel.setAvatarMedia(result);

    Glide.with(this)
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
  }

  private void initializeViewModel() {
    AddGroupDetailsFragmentArgs      args       = AddGroupDetailsFragmentArgs.fromBundle(requireArguments());
    AddGroupDetailsRepository        repository = new AddGroupDetailsRepository(requireContext());
    AddGroupDetailsViewModel.Factory factory    = new AddGroupDetailsViewModel.Factory(Arrays.asList(args.getRecipientIds()), repository);

    viewModel = new ViewModelProvider(this, factory).get(AddGroupDetailsViewModel.class);

    viewModel.getGroupCreateResult().observe(getViewLifecycleOwner(), this::handleGroupCreateResult);
  }

  private void handleCreateClicked() {
    if (!create.isClickable()) {
      return;
    }

    create.setSpinning();

    viewModel.create();
  }

  private void handleRecipientClick(@NonNull Recipient recipient) {
    new MaterialAlertDialogBuilder(requireContext())
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

    create.cancelSpinning();
  }

  private void toast(@StringRes int toastStringId) {
    Toast.makeText(requireContext(), toastStringId, Toast.LENGTH_SHORT)
         .show();
  }

  private void setCreateEnabled(boolean isEnabled) {
    if (create.isClickable() == isEnabled) {
      return;
    }

    create.setClickable(isEnabled);
  }

  private void showAvatarPicker() {
    Media media = viewModel.getAvatarMedia();

    SafeNavigation.safeNavigate(Navigation.findNavController(requireView()), AddGroupDetailsFragmentDirections.actionAddGroupDetailsFragmentToAvatarPicker(null, media).setIsNewGroup(true));
  }

  public interface Callback {
    void onGroupCreated(@NonNull RecipientId recipientId, long threadId, @NonNull List<Recipient> invitedMembers);

    void onNavigationButtonPressed();
  }
}
