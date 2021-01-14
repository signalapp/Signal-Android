package org.thoughtcrime.securesms.profiles.manage;

import android.content.Intent;
import android.os.Bundle;
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
import androidx.lifecycle.ViewModelProviders;
import androidx.navigation.Navigation;

import com.bumptech.glide.Glide;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.mediasend.AvatarSelectionActivity;
import org.thoughtcrime.securesms.mediasend.AvatarSelectionBottomSheetDialogFragment;
import org.thoughtcrime.securesms.mediasend.Media;
import org.thoughtcrime.securesms.profiles.ProfileName;
import org.thoughtcrime.securesms.profiles.manage.ManageProfileViewModel.AvatarState;
import org.thoughtcrime.securesms.util.views.SimpleProgressDialog;

import static android.app.Activity.RESULT_OK;

public class ManageProfileFragment extends LoggingFragment {

  private static final String TAG                        = Log.tag(ManageProfileFragment.class);
  private static final short  REQUEST_CODE_SELECT_AVATAR = 31726;

  private Toolbar                toolbar;
  private ImageView              avatarView;
  private View                   avatarPlaceholderView;
  private TextView               profileNameView;
  private View                   profileNameContainer;
  private TextView               usernameView;
  private View                   usernameContainer;
  private TextView               aboutView;
  private View                   aboutContainer;
  private TextView               aboutEmojiView;
  private AlertDialog            avatarProgress;

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

    initializeViewModel();

    this.toolbar.setNavigationOnClickListener(v -> requireActivity().finish());
    this.avatarView.setOnClickListener(v -> onAvatarClicked());

    this.profileNameContainer.setOnClickListener(v -> {
      Navigation.findNavController(v).navigate(ManageProfileFragmentDirections.actionManageProfileName());
    });

    this.usernameContainer.setOnClickListener(v -> {
      Navigation.findNavController(v).navigate(ManageProfileFragmentDirections.actionManageUsername());
    });
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    if (requestCode == REQUEST_CODE_SELECT_AVATAR && resultCode == RESULT_OK) {
      if (data != null && data.getBooleanExtra("delete", false)) {
        viewModel.onAvatarSelected(requireContext(), null);
        return;
      }

      Media result = data.getParcelableExtra(AvatarSelectionActivity.EXTRA_MEDIA);

      viewModel.onAvatarSelected(requireContext(), result);
    }
  }

  private void initializeViewModel() {
    viewModel = ViewModelProviders.of(this, new ManageProfileViewModel.Factory()).get(ManageProfileViewModel.class);

    viewModel.getAvatar().observe(getViewLifecycleOwner(), this::presentAvatar);
    viewModel.getProfileName().observe(getViewLifecycleOwner(), this::presentProfileName);
    viewModel.getEvents().observe(getViewLifecycleOwner(), this::presentEvent);

    if (viewModel.shouldShowAbout()) {
      viewModel.getAbout().observe(getViewLifecycleOwner(), this::presentAbout);
      viewModel.getAboutEmoji().observe(getViewLifecycleOwner(), this::presentAboutEmoji);
    } else {
      aboutContainer.setVisibility(View.GONE);
    }

    if (viewModel.shouldShowUsername()) {
      viewModel.getUsername().observe(getViewLifecycleOwner(), this::presentUsername);
    } else {
      usernameContainer.setVisibility(View.GONE);
    }
  }

  private void presentAvatar(@NonNull AvatarState avatarState) {
    if (avatarState.getAvatar() == null) {
      avatarView.setImageDrawable(null);
      avatarPlaceholderView.setVisibility(View.VISIBLE);
    } else {
      avatarPlaceholderView.setVisibility(View.GONE);
      Glide.with(this)
           .load(avatarState.getAvatar())
           .circleCrop()
           .into(avatarView);
    }

    if (avatarProgress == null && avatarState.getLoadingState() == ManageProfileViewModel.LoadingState.LOADING) {
      avatarProgress = SimpleProgressDialog.show(requireContext());
    } else if (avatarProgress != null && avatarState.getLoadingState() == ManageProfileViewModel.LoadingState.LOADED) {
      avatarProgress.dismiss();
    }
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
      usernameView.setTextColor(requireContext().getResources().getColor(R.color.signal_text_secondary));
    } else {
      usernameView.setText(username);
      usernameView.setTextColor(requireContext().getResources().getColor(R.color.signal_text_primary));
    }
  }

  private void presentAbout(@Nullable String about) {
    if (about == null || about.isEmpty()) {
      aboutView.setHint(R.string.ManageProfileFragment_about);
    } else {
      aboutView.setText(about);
    }
  }

  private void presentAboutEmoji(@NonNull String aboutEmoji) {

  }

  private void presentEvent(@NonNull ManageProfileViewModel.Event event) {
    if (event == ManageProfileViewModel.Event.AVATAR_FAILURE) {
      Toast.makeText(requireContext(), R.string.ManageProfileFragment_failed_to_set_avatar, Toast.LENGTH_LONG).show();
    }
  }

  private void onAvatarClicked() {
    AvatarSelectionBottomSheetDialogFragment.create(viewModel.canRemoveAvatar(),
                                                    true,
                                                    REQUEST_CODE_SELECT_AVATAR,
                                                    false)
                                            .show(getChildFragmentManager(), null);
  }
}
