package org.thoughtcrime.securesms.usernames;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;
import androidx.navigation.Navigation;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.thoughtcrime.securesms.util.views.SimpleProgressDialog;
import org.whispersystems.libsignal.util.guava.Optional;

public class ProfileEditOverviewFragment extends Fragment {

  private ImageView   avatarView;
  private TextView    profileText;
  private TextView    usernameText;
  private AlertDialog loadingDialog;

  private ProfileEditOverviewViewModel viewModel;

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.profile_edit_overview_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    avatarView   = view.findViewById(R.id.profile_overview_avatar);
    profileText  = view.findViewById(R.id.profile_overview_profile_name);
    usernameText = view.findViewById(R.id.profile_overview_username);

    View     profileButton  = view.findViewById(R.id.profile_overview_profile_edit_button );
    View     usernameButton = view.findViewById(R.id.profile_overview_username_edit_button);
    TextView infoText       = view.findViewById(R.id.profile_overview_info_text);

    profileButton.setOnClickListener(v -> {
      Navigation.findNavController(view).navigate(ProfileEditOverviewFragmentDirections.actionProfileEdit());
    });

    usernameButton.setOnClickListener(v -> {
      Navigation.findNavController(view).navigate(ProfileEditOverviewFragmentDirections.actionUsernameEdit());
    });

    infoText.setMovementMethod(LinkMovementMethod.getInstance());

    profileText.setOnClickListener(v -> profileButton.callOnClick());
    usernameText.setOnClickListener(v -> usernameButton.callOnClick());

    avatarView.setOnClickListener(v -> Permissions.with(this)
                                                  .request(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                                  .ifNecessary()
                                                  .onAnyResult(() -> viewModel.onAvatarClicked(this))
                                                  .execute());

    viewModel = ViewModelProviders.of(this, new ProfileEditOverviewViewModel.Factory()).get(ProfileEditOverviewViewModel.class);
    viewModel.getAvatar().observe(getViewLifecycleOwner(), this::onAvatarChanged);
    viewModel.getLoading().observe(getViewLifecycleOwner(), this::onLoadingChanged);
    viewModel.getProfileName().observe(getViewLifecycleOwner(), this::onProfileNameChanged);
    viewModel.getUsername().observe(getViewLifecycleOwner(), this::onUsernameChanged);
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    if (!viewModel.onActivityResult(this, requestCode, resultCode, data)) {
      super.onActivityResult(requestCode, resultCode, data);
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    viewModel.onResume();
  }

  private void onAvatarChanged(@NonNull Optional<byte[]> avatar) {
    if (avatar.isPresent()) {
      GlideApp.with(this)
              .load(avatar.get())
              .circleCrop()
              .into(avatarView);
    } else {
      avatarView.setImageDrawable(null);
    }
  }

  private void onLoadingChanged(boolean loading) {
    if (loadingDialog == null && loading) {
      loadingDialog = SimpleProgressDialog.show(requireContext());
    } else if (loadingDialog != null) {
      loadingDialog.dismiss();
      loadingDialog = null;
    }
  }

  private void onProfileNameChanged(@NonNull Optional<String> profileName) {
    profileText.setText(profileName.or(""));
  }

  @SuppressLint("SetTextI18n")
  private void onUsernameChanged(@NonNull Optional<String> username) {
    if (username.isPresent()) {
      usernameText.setText("@" + username.get());
    } else {
      usernameText.setText("");
    }
  }
}
