package org.thoughtcrime.securesms.profiles.manage;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.res.ResourcesCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModelProviders;
import androidx.navigation.Navigation;

import com.airbnb.lottie.SimpleColorFilter;
import com.bumptech.glide.Glide;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.avatar.Avatars;
import org.thoughtcrime.securesms.avatar.picker.AvatarPickerFragment;
import org.thoughtcrime.securesms.badges.BadgeImageView;
import org.thoughtcrime.securesms.badges.models.Badge;
import org.thoughtcrime.securesms.badges.self.none.BecomeASustainerFragment;
import org.thoughtcrime.securesms.components.emoji.EmojiUtil;
import org.thoughtcrime.securesms.mediasend.Media;
import org.thoughtcrime.securesms.profiles.ProfileName;
import org.thoughtcrime.securesms.profiles.manage.ManageProfileViewModel.AvatarState;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.NameUtil;
import org.thoughtcrime.securesms.util.views.SimpleProgressDialog;
import org.whispersystems.libsignal.util.guava.Optional;

public class ManageProfileFragment extends LoggingFragment {

  private static final String TAG                       = Log.tag(ManageProfileFragment.class);

  private Toolbar                toolbar;
  private ImageView              avatarView;
  private ImageView              avatarPlaceholderView;
  private TextView               profileNameView;
  private View                   profileNameContainer;
  private TextView               usernameView;
  private View                   usernameContainer;
  private TextView               aboutView;
  private View                   aboutContainer;
  private ImageView              aboutEmojiView;
  private AlertDialog            avatarProgress;
  private TextView               avatarInitials;
  private ImageView              avatarBackground;
  private View                   badgesContainer;
  private BadgeImageView         badgeView;

  private ManageProfileViewModel viewModel;

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.manage_profile_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    this.toolbar               = view.findViewById(R.id.toolbar);
    this.avatarView            = view.findViewById(R.id.manage_profile_avatar);
    this.avatarPlaceholderView = view.findViewById(R.id.manage_profile_avatar_placeholder);
    this.profileNameView       = view.findViewById(R.id.manage_profile_name);
    this.profileNameContainer  = view.findViewById(R.id.manage_profile_name_container);
    this.usernameView          = view.findViewById(R.id.manage_profile_username);
    this.usernameContainer     = view.findViewById(R.id.manage_profile_username_container);
    this.aboutView             = view.findViewById(R.id.manage_profile_about);
    this.aboutContainer        = view.findViewById(R.id.manage_profile_about_container);
    this.aboutEmojiView        = view.findViewById(R.id.manage_profile_about_icon);
    this.avatarInitials        = view.findViewById(R.id.manage_profile_avatar_initials);
    this.avatarBackground      = view.findViewById(R.id.manage_profile_avatar_background);
    this.badgesContainer       = view.findViewById(R.id.manage_profile_badges_container);
    this.badgeView             = view.findViewById(R.id.manage_profile_badge);

    initializeViewModel();

    this.toolbar.setNavigationOnClickListener(v -> requireActivity().finish());

    View editAvatar = view.findViewById(R.id.manage_profile_edit_photo);
    editAvatar.setOnClickListener(v -> onEditAvatarClicked());

    this.profileNameContainer.setOnClickListener(v -> {
      Navigation.findNavController(v).navigate(ManageProfileFragmentDirections.actionManageProfileName());
    });

    this.usernameContainer.setOnClickListener(v -> {
      Navigation.findNavController(v).navigate(ManageProfileFragmentDirections.actionManageUsername());
    });

    this.aboutContainer.setOnClickListener(v -> {
      Navigation.findNavController(v).navigate(ManageProfileFragmentDirections.actionManageAbout());
    });

    getParentFragmentManager().setFragmentResultListener(AvatarPickerFragment.REQUEST_KEY_SELECT_AVATAR, getViewLifecycleOwner(), (key, bundle) -> {
      if (bundle.getBoolean(AvatarPickerFragment.SELECT_AVATAR_CLEAR)) {
        viewModel.onAvatarSelected(requireContext(), null);
      } else {
        Media result = bundle.getParcelable(AvatarPickerFragment.SELECT_AVATAR_MEDIA);
        viewModel.onAvatarSelected(requireContext(), result);
      }
    });

    avatarInitials.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
      if (avatarInitials.length() > 0) {
        updateInitials(avatarInitials.getText().toString());
      }
    });

    if (FeatureFlags.donorBadges()) {
      badgesContainer.setOnClickListener(v -> {
        if (Recipient.self().getBadges().isEmpty()) {
          BecomeASustainerFragment.show(getParentFragmentManager());
        } else {
          Navigation.findNavController(v).navigate(ManageProfileFragmentDirections.actionManageProfileFragmentToBadgeManageFragment());
        }
      });
    } else {
      badgesContainer.setVisibility(View.GONE);
    }
  }

  private void initializeViewModel() {
    viewModel = ViewModelProviders.of(this, new ManageProfileViewModel.Factory()).get(ManageProfileViewModel.class);

    LiveData<Optional<byte[]>> avatarImage = Transformations.distinctUntilChanged(Transformations.map(viewModel.getAvatar(), avatar -> Optional.fromNullable(avatar.getAvatar())));
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
      usernameContainer.setVisibility(View.GONE);
    }
  }

  private void presentAvatarImage(@NonNull Optional<byte[]> avatarData) {
    if (avatarData.isPresent()) {
      Glide.with(this)
           .load(avatarData.get())
           .circleCrop()
           .into(avatarView);
    } else {
      avatarView.setImageDrawable(null);
    }
  }

  private void presentAvatarPlaceholder(@NonNull AvatarState avatarState) {
    if (avatarState.getAvatar() == null) {
      CharSequence            initials        = NameUtil.getAbbreviation(avatarState.getSelf().getDisplayName(requireContext()));
      Avatars.ForegroundColor foregroundColor = Avatars.getForegroundColor(avatarState.getSelf().getAvatarColor());

      avatarBackground.setColorFilter(new SimpleColorFilter(avatarState.getSelf().getAvatarColor().colorInt()));
      avatarPlaceholderView.setColorFilter(new SimpleColorFilter(foregroundColor.getColorInt()));
      avatarInitials.setTextColor(foregroundColor.getColorInt());

      if (TextUtils.isEmpty(initials)) {
        avatarPlaceholderView.setVisibility(View.VISIBLE);
        avatarInitials.setVisibility(View.GONE);
      } else {
        updateInitials(initials.toString());
        avatarPlaceholderView.setVisibility(View.GONE);
        avatarInitials.setVisibility(View.VISIBLE);
      }
    } else {
      avatarPlaceholderView.setVisibility(View.GONE);
      avatarInitials.setVisibility(View.GONE);
    }

    if (avatarProgress == null && avatarState.getLoadingState() == ManageProfileViewModel.LoadingState.LOADING) {
      avatarProgress = SimpleProgressDialog.show(requireContext());
    } else if (avatarProgress != null && avatarState.getLoadingState() == ManageProfileViewModel.LoadingState.LOADED) {
      avatarProgress.dismiss();
    }
  }

  private void updateInitials(String initials) {
    avatarInitials.setTextSize(TypedValue.COMPLEX_UNIT_PX, Avatars.getTextSizeForLength(requireContext(), initials, avatarInitials.getMeasuredWidth() * 0.8f, avatarInitials.getMeasuredWidth() * 0.45f));
    avatarInitials.setText(initials);
  }

  private void presentProfileName(@Nullable ProfileName profileName) {
    if (profileName == null || profileName.isEmpty()) {
      profileNameView.setText(R.string.ManageProfileFragment_profile_name);
    } else {
      profileNameView.setText(profileName.toString());
    }
  }

  private void presentUsername(@Nullable String username) {
    if (username == null || username.isEmpty()) {
      usernameView.setText(R.string.ManageProfileFragment_username);
    } else {
      usernameView.setText(username);
    }
  }

  private void presentAbout(@Nullable String about) {
    if (about == null || about.isEmpty()) {
      aboutView.setText(R.string.ManageProfileFragment_about);
    } else {
      aboutView.setText(about);
    }
  }

  private void presentAboutEmoji(@NonNull String aboutEmoji) {
    if (aboutEmoji == null || aboutEmoji.isEmpty()) {
      aboutEmojiView.setImageDrawable(ResourcesCompat.getDrawable(getResources(), R.drawable.ic_compose_24, null));
    } else {
      Drawable emoji = EmojiUtil.convertToDrawable(requireContext(), aboutEmoji);

      if (emoji != null) {
        aboutEmojiView.setImageDrawable(emoji);
      } else {
        aboutEmojiView.setImageDrawable(ResourcesCompat.getDrawable(getResources(), R.drawable.ic_compose_24, null));
      }
    }
  }

  private void presentBadge(@NonNull Optional<Badge> badge) {
    if (badge.isPresent() && badge.get().getVisible() && !badge.get().isExpired()) {
      badgeView.setBadge(badge.orNull());
    } else {
      badgeView.setBadge(null);
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
    Navigation.findNavController(requireView()).navigate(ManageProfileFragmentDirections.actionManageProfileFragmentToAvatarPicker(null, null));
  }
}
