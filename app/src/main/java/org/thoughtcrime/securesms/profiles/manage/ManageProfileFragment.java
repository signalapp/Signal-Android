package org.thoughtcrime.securesms.profiles.manage;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.res.ResourcesCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.airbnb.lottie.SimpleColorFilter;
import com.bumptech.glide.Glide;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import org.signal.core.util.logging.Log;
import org.signal.libsignal.usernames.BaseUsernameException;
import org.signal.libsignal.usernames.Username;
import org.thoughtcrime.securesms.AvatarPreviewActivity;
import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.avatar.Avatars;
import org.thoughtcrime.securesms.avatar.picker.AvatarPickerFragment;
import org.thoughtcrime.securesms.badges.models.Badge;
import org.thoughtcrime.securesms.badges.self.none.BecomeASustainerFragment;
import org.thoughtcrime.securesms.components.emoji.EmojiTextView;
import org.thoughtcrime.securesms.components.emoji.EmojiUtil;
import org.thoughtcrime.securesms.databinding.ManageProfileFragmentBinding;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.mediasend.Media;
import org.thoughtcrime.securesms.profiles.ProfileName;
import org.thoughtcrime.securesms.profiles.manage.ManageProfileViewModel.AvatarState;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.signal.core.util.concurrent.LifecycleDisposable;
import org.thoughtcrime.securesms.util.NameUtil;
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil;
import org.thoughtcrime.securesms.util.navigation.SafeNavigation;
import org.thoughtcrime.securesms.util.views.SimpleProgressDialog;
import org.whispersystems.util.Base64UrlSafe;

import java.util.Arrays;
import java.util.Optional;

import io.reactivex.rxjava3.disposables.Disposable;

public class ManageProfileFragment extends LoggingFragment {

  private static final String TAG = Log.tag(ManageProfileFragment.class);

  private AlertDialog                  avatarProgress;
  private ManageProfileViewModel       viewModel;
  private ManageProfileFragmentBinding binding;
  private LifecycleDisposable          disposables;

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    binding = ManageProfileFragmentBinding.inflate(inflater, container, false);

    return binding.getRoot();
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    disposables = new LifecycleDisposable();
    disposables.bindTo(getViewLifecycleOwner());

    new UsernameEditFragment.ResultContract().registerForResult(getParentFragmentManager(), getViewLifecycleOwner(), isUsernameCreated -> {
      Snackbar.make(view, R.string.ManageProfileFragment__username_created, Snackbar.LENGTH_SHORT).show();
    });

    UsernameShareBottomSheet.ResultContract.INSTANCE.registerForResult(getParentFragmentManager(), getViewLifecycleOwner(), isCopiedToClipboard -> {
      Snackbar.make(view, R.string.ManageProfileFragment__username_copied, Snackbar.LENGTH_SHORT).show();
    });

    initializeViewModel();

    binding.toolbar.setNavigationOnClickListener(v -> requireActivity().finish());

    binding.manageProfileEditPhoto.setOnClickListener(v -> onEditAvatarClicked());

    binding.manageProfileNameContainer.setOnClickListener(v -> {
      SafeNavigation.safeNavigate(Navigation.findNavController(v), ManageProfileFragmentDirections.actionManageProfileName());
    });

    binding.manageProfileUsernameContainer.setOnClickListener(v -> {
      if (SignalStore.uiHints().hasSeenUsernameEducation()) {
        if (Recipient.self().getUsername().isPresent()) {
          new MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_Signal_MaterialAlertDialog_List)
              .setItems(R.array.username_edit_entries, (d, w) -> {
                switch (w) {
                  case 0:
                    SafeNavigation.safeNavigate(Navigation.findNavController(v), ManageProfileFragmentDirections.actionManageUsername());
                    break;
                  case 1:
                    displayConfirmUsernameDeletionDialog();
                    break;
                  default:
                    throw new IllegalStateException();
                }
              })
              .show();
        } else {
          SafeNavigation.safeNavigate(Navigation.findNavController(v), ManageProfileFragmentDirections.actionManageUsername());
        }
      } else {
        SafeNavigation.safeNavigate(Navigation.findNavController(v), ManageProfileFragmentDirections.actionManageProfileFragmentToUsernameEducationFragment());
      }
    });

    binding.manageProfileAboutContainer.setOnClickListener(v -> {
      SafeNavigation.safeNavigate(Navigation.findNavController(v), ManageProfileFragmentDirections.actionManageAbout());
    });

    getParentFragmentManager().setFragmentResultListener(AvatarPickerFragment.REQUEST_KEY_SELECT_AVATAR, getViewLifecycleOwner(), (key, bundle) -> {
      if (bundle.getBoolean(AvatarPickerFragment.SELECT_AVATAR_CLEAR)) {
        viewModel.onAvatarSelected(requireContext(), null);
      } else {
        Media result = bundle.getParcelable(AvatarPickerFragment.SELECT_AVATAR_MEDIA);
        viewModel.onAvatarSelected(requireContext(), result);
      }
    });

    EmojiTextView avatarInitials = binding.manageProfileAvatarInitials;
    avatarInitials.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
      if (avatarInitials.length() > 0) {
        updateInitials(avatarInitials.getText().toString());
      }
    });

    binding.manageProfileBadgesContainer.setOnClickListener(v -> {
      if (Recipient.self().getBadges().isEmpty()) {
        BecomeASustainerFragment.show(getParentFragmentManager());
      } else {
        SafeNavigation.safeNavigate(Navigation.findNavController(v), ManageProfileFragmentDirections.actionManageProfileFragmentToBadgeManageFragment());
      }
    });

    binding.manageProfileAvatar.setOnClickListener(v -> {
      startActivity(AvatarPreviewActivity.intentFromRecipientId(requireContext(), Recipient.self().getId()),
                    AvatarPreviewActivity.createTransitionBundle(requireActivity(), binding.manageProfileAvatar));
    });

    binding.manageProfileUsernameShare.setOnClickListener(v -> {
      SafeNavigation.safeNavigate(Navigation.findNavController(v), ManageProfileFragmentDirections.actionManageProfileFragmentToShareUsernameDialog());
    });
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    binding = null;
  }

  private void initializeViewModel() {
    viewModel = new ViewModelProvider(this, new ManageProfileViewModel.Factory()).get(ManageProfileViewModel.class);

    LiveData<Optional<byte[]>> avatarImage = Transformations.map(LiveDataUtil.distinctUntilChanged(viewModel.getAvatar(), (b1, b2) -> Arrays.equals(b1.getAvatar(), b2.getAvatar())),
                                                                 b -> Optional.ofNullable(b.getAvatar()));
    avatarImage.observe(getViewLifecycleOwner(), this::presentAvatarImage);

    viewModel.getAvatar().observe(getViewLifecycleOwner(), this::presentAvatarPlaceholder);
    viewModel.getProfileName().observe(getViewLifecycleOwner(), this::presentProfileName);
    viewModel.getEvents().observe(getViewLifecycleOwner(), this::presentEvent);
    viewModel.getAbout().observe(getViewLifecycleOwner(), this::presentAbout);
    viewModel.getAboutEmoji().observe(getViewLifecycleOwner(), this::presentAboutEmoji);
    viewModel.getBadge().observe(getViewLifecycleOwner(), this::presentBadge);

    if (viewModel.shouldShowUsername()) {
      viewModel.getUsername().observe(getViewLifecycleOwner(), this::presentUsername);
    } else {
      binding.manageProfileUsernameContainer.setVisibility(View.GONE);
    }
  }

  private void presentAvatarImage(@NonNull Optional<byte[]> avatarData) {
    if (avatarData.isPresent()) {
      Glide.with(this)
           .load(avatarData.get())
           .circleCrop()
           .into(binding.manageProfileAvatar);
    } else {
      Glide.with(this).load((Drawable) null).into(binding.manageProfileAvatar);
    }
  }

  private void presentAvatarPlaceholder(@NonNull AvatarState avatarState) {
    if (avatarState.getAvatar() == null) {
      CharSequence            initials        = NameUtil.getAbbreviation(avatarState.getSelf().getDisplayName(requireContext()));
      Avatars.ForegroundColor foregroundColor = Avatars.getForegroundColor(avatarState.getSelf().getAvatarColor());

      binding.manageProfileAvatarBackground.setColorFilter(new SimpleColorFilter(avatarState.getSelf().getAvatarColor().colorInt()));
      binding.manageProfileAvatarPlaceholder.setColorFilter(new SimpleColorFilter(foregroundColor.getColorInt()));
      binding.manageProfileAvatarInitials.setTextColor(foregroundColor.getColorInt());

      if (TextUtils.isEmpty(initials)) {
        binding.manageProfileAvatarPlaceholder.setVisibility(View.VISIBLE);
        binding.manageProfileAvatarInitials.setVisibility(View.GONE);
      } else {
        updateInitials(initials.toString());
        binding.manageProfileAvatarPlaceholder.setVisibility(View.GONE);
        binding.manageProfileAvatarInitials.setVisibility(View.VISIBLE);
      }
    } else {
      binding.manageProfileAvatarPlaceholder.setVisibility(View.GONE);
      binding.manageProfileAvatarInitials.setVisibility(View.GONE);
    }

    if (avatarProgress == null && avatarState.getLoadingState() == ManageProfileViewModel.LoadingState.LOADING) {
      avatarProgress = SimpleProgressDialog.show(requireContext());
    } else if (avatarProgress != null && avatarState.getLoadingState() == ManageProfileViewModel.LoadingState.LOADED) {
      avatarProgress.dismiss();
    }
  }

  private void updateInitials(String initials) {
    binding.manageProfileAvatarInitials.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                                                    Avatars.getTextSizeForLength(requireContext(),
                                                                                 initials,
                                                                                 binding.manageProfileAvatarInitials.getMeasuredWidth() * 0.8f,
                                                                                 binding.manageProfileAvatarInitials.getMeasuredWidth() * 0.45f));
    binding.manageProfileAvatarInitials.setText(initials);
  }

  private void presentProfileName(@Nullable ProfileName profileName) {
    if (profileName == null || profileName.isEmpty()) {
      binding.manageProfileName.setText(R.string.ManageProfileFragment_profile_name);
    } else {
      binding.manageProfileName.setText(profileName.toString());
    }
  }

  private void presentUsername(@Nullable String username) {
    if (username == null || username.isEmpty()) {
      binding.manageProfileUsername.setText(R.string.ManageProfileFragment_username);
      binding.manageProfileUsernameSubtitle.setText(R.string.ManageProfileFragment_your_username);
      binding.manageProfileUsernameShare.setVisibility(View.GONE);
    } else {
      binding.manageProfileUsername.setText(username);

      try {
        binding.manageProfileUsernameSubtitle.setText(getString(R.string.signal_me_username_url_no_scheme, Base64UrlSafe.encodeBytesWithoutPadding(Username.hash(username))));
      } catch (BaseUsernameException e) {
        Log.w(TAG, "Could not format username link", e);
        binding.manageProfileUsernameSubtitle.setText(R.string.ManageProfileFragment_your_username);
      }

      binding.manageProfileUsernameShare.setVisibility(View.VISIBLE);
    }
  }

  private void presentAbout(@Nullable String about) {
    if (about == null || about.isEmpty()) {
      binding.manageProfileAbout.setText(R.string.ManageProfileFragment_about);
    } else {
      binding.manageProfileAbout.setText(about);
    }
  }

  private void presentAboutEmoji(@NonNull String aboutEmoji) {
    if (aboutEmoji == null || aboutEmoji.isEmpty()) {
      binding.manageProfileAboutIcon.setImageDrawable(ResourcesCompat.getDrawable(getResources(), R.drawable.symbol_edit_24, null));
    } else {
      Drawable emoji = EmojiUtil.convertToDrawable(requireContext(), aboutEmoji);

      if (emoji != null) {
        binding.manageProfileAboutIcon.setImageDrawable(emoji);
      } else {
        binding.manageProfileAboutIcon.setImageDrawable(ResourcesCompat.getDrawable(getResources(), R.drawable.symbol_edit_24, null));
      }
    }
  }

  private void presentBadge(@NonNull Optional<Badge> badge) {
    if (badge.isPresent() && badge.get().getVisible() && !badge.get().isExpired()) {
      binding.manageProfileBadge.setBadge(badge.orElse(null));
    } else {
      binding.manageProfileBadge.setBadge(null);
    }
  }

  private void presentEvent(@NonNull ManageProfileViewModel.Event event) {
    switch (event) {
      case AVATAR_DISK_FAILURE:
        Toast.makeText(requireContext(), R.string.ManageProfileFragment_failed_to_set_avatar, Toast.LENGTH_LONG).show();
        break;
      case AVATAR_NETWORK_FAILURE:
        Toast.makeText(requireContext(), R.string.EditProfileNameFragment_failed_to_save_due_to_network_issues_try_again_later, Toast.LENGTH_LONG).show();
        break;
    }
  }

  private void onEditAvatarClicked() {
    SafeNavigation.safeNavigate(Navigation.findNavController(requireView()), ManageProfileFragmentDirections.actionManageProfileFragmentToAvatarPicker(null, null));
  }

  private void displayConfirmUsernameDeletionDialog() {
    new MaterialAlertDialogBuilder(requireContext())
        .setTitle("Delete Username?") // TODO [alex] -- Final copy
        .setMessage("This will remove your username, allowing other users to claim it. Are you sure?") // TODO [alex] -- Final copy
        .setPositiveButton(R.string.delete, (d, w) -> {
          onUserConfirmedUsernameDeletion();
        })
        .setNegativeButton(android.R.string.cancel, (d, w) -> {})
        .show();
  }

  private void onUserConfirmedUsernameDeletion() {
    binding.progressCard.setVisibility(View.VISIBLE);
    Disposable disposable = viewModel.deleteUsername()
                                     .subscribe(result -> {
                                       binding.progressCard.setVisibility(View.GONE);
                                       handleUsernameDeletionResult(result);
                                     });
    disposables.add(disposable);
  }

  private void handleUsernameDeletionResult(@NonNull UsernameEditRepository.UsernameDeleteResult usernameDeleteResult) {
    switch (usernameDeleteResult) {
      case SUCCESS:
        Snackbar.make(requireView(), R.string.ManageProfileFragment__username_deleted, Snackbar.LENGTH_SHORT).show();
        break;
      case NETWORK_ERROR:
        Snackbar.make(requireView(), R.string.ManageProfileFragment__couldnt_delete_username, Snackbar.LENGTH_SHORT).show();
        break;
    }
  }
}
