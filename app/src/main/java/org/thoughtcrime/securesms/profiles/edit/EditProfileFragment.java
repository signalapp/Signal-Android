package org.thoughtcrime.securesms.profiles.edit;

import android.Manifest;
import android.animation.Animator;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.dd.CircularProgressButton;
import com.google.android.gms.common.util.IOUtils;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.contacts.avatars.ResourceContactPhoto;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.lock.v2.RegistrationLockUtil;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mediasend.AvatarSelectionActivity;
import org.thoughtcrime.securesms.mediasend.AvatarSelectionBottomSheetDialogFragment;
import org.thoughtcrime.securesms.mediasend.Media;
import org.thoughtcrime.securesms.megaphone.Megaphones;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.profiles.ProfileName;
import org.thoughtcrime.securesms.providers.BlobProvider;
import org.thoughtcrime.securesms.registration.RegistrationUtil;
import org.thoughtcrime.securesms.storage.StorageSyncHelper;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.concurrent.SimpleTask;
import org.thoughtcrime.securesms.util.text.AfterTextChanged;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.IOException;
import java.io.InputStream;

import static android.app.Activity.RESULT_OK;
import static org.thoughtcrime.securesms.profiles.edit.EditProfileActivity.DISPLAY_USERNAME;
import static org.thoughtcrime.securesms.profiles.edit.EditProfileActivity.EXCLUDE_SYSTEM;
import static org.thoughtcrime.securesms.profiles.edit.EditProfileActivity.NEXT_BUTTON_TEXT;
import static org.thoughtcrime.securesms.profiles.edit.EditProfileActivity.NEXT_INTENT;
import static org.thoughtcrime.securesms.profiles.edit.EditProfileActivity.SHOW_TOOLBAR;

public class EditProfileFragment extends Fragment {

  private static final String TAG                        = Log.tag(EditProfileFragment.class);
  private static final String AVATAR_STATE               = "avatar";
  private static final short  REQUEST_CODE_SELECT_AVATAR = 31726;

  private Toolbar                toolbar;
  private View                   title;
  private ImageView              avatar;
  private CircularProgressButton finishButton;
  private EditText               givenName;
  private EditText               familyName;
  private View                   reveal;
  private TextView               preview;
  private View                   usernameLabel;
  private View                   usernameEditButton;
  private TextView               username;

  private Intent nextIntent;

  private EditProfileViewModel viewModel;

  private Controller controller;

  @Override
  public void onAttach(@NonNull Context context) {
    super.onAttach(context);

    if (context instanceof Controller) {
      controller = (Controller) context;
    } else {
      throw new IllegalStateException("Context must subclass Controller");
    }
  }

  public static EditProfileFragment create(boolean excludeSystem,
                                           Intent nextIntent,
                                           boolean displayUsernameField,
                                           @StringRes int nextButtonText) {

    EditProfileFragment fragment = new EditProfileFragment();
    Bundle              args     = new Bundle();

    args.putBoolean(EXCLUDE_SYSTEM, excludeSystem);
    args.putParcelable(NEXT_INTENT, nextIntent);
    args.putBoolean(DISPLAY_USERNAME, displayUsernameField);
    args.putInt(NEXT_BUTTON_TEXT, nextButtonText);
    fragment.setArguments(args);

    return fragment;
  }

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.profile_create_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    initializeResources(view);
    initializeViewModel(requireArguments().getBoolean(EXCLUDE_SYSTEM, false), savedInstanceState != null);
    initializeProfileName();
    initializeProfileAvatar();
    initializeUsername();

    requireActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle outState) {
    outState.putByteArray(AVATAR_STATE, viewModel.getAvatarSnapshot());
  }

  @Override
  public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
    super.onViewStateRestored(savedInstanceState);

    if (savedInstanceState != null && savedInstanceState.containsKey(AVATAR_STATE)) {
      viewModel.setAvatar(savedInstanceState.getByteArray(AVATAR_STATE));
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    if (requestCode == REQUEST_CODE_SELECT_AVATAR && resultCode == RESULT_OK) {

      if (data != null && data.getBooleanExtra("delete", false)) {
        viewModel.setAvatar(null);
        avatar.setImageDrawable(new ResourceContactPhoto(R.drawable.ic_camera_solid_white_24).asDrawable(requireActivity(), getResources().getColor(R.color.grey_400)));
        return;
      }

      SimpleTask.run(() -> {
        try {
          Media       result = data.getParcelableExtra(AvatarSelectionActivity.EXTRA_MEDIA);
          InputStream stream = BlobProvider.getInstance().getStream(requireContext(), result.getUri());

          return IOUtils.readInputStreamFully(stream);
        } catch (IOException ioException) {
          Log.w(TAG, ioException);
          return null;
        }
      },
      (avatarBytes) -> {
        if (avatarBytes != null) {
          viewModel.setAvatar(avatarBytes);
          GlideApp.with(EditProfileFragment.this)
                  .load(avatarBytes)
                  .skipMemoryCache(true)
                  .diskCacheStrategy(DiskCacheStrategy.NONE)
                  .circleCrop()
                  .into(avatar);
        } else {
          Toast.makeText(requireActivity(), R.string.CreateProfileActivity_error_setting_profile_photo, Toast.LENGTH_LONG).show();
        }
      });
    }
  }

  private void initializeViewModel(boolean excludeSystem, boolean hasSavedInstanceState) {
    EditProfileRepository        repository = new EditProfileRepository(requireContext(), excludeSystem);
    EditProfileViewModel.Factory factory    = new EditProfileViewModel.Factory(repository, hasSavedInstanceState);

    viewModel = ViewModelProviders.of(this, factory).get(EditProfileViewModel.class);
  }

  private void initializeResources(@NonNull View view) {
    Bundle arguments = requireArguments();

    this.toolbar            = view.findViewById(R.id.toolbar);
    this.title              = view.findViewById(R.id.title);
    this.avatar             = view.findViewById(R.id.avatar);
    this.givenName          = view.findViewById(R.id.given_name);
    this.familyName         = view.findViewById(R.id.family_name);
    this.finishButton       = view.findViewById(R.id.finish_button);
    this.reveal             = view.findViewById(R.id.reveal);
    this.preview            = view.findViewById(R.id.name_preview);
    this.username           = view.findViewById(R.id.profile_overview_username);
    this.usernameEditButton = view.findViewById(R.id.profile_overview_username_edit_button);
    this.usernameLabel      = view.findViewById(R.id.profile_overview_username_label);
    this.nextIntent         = arguments.getParcelable(NEXT_INTENT);

    if (FeatureFlags.usernames() && arguments.getBoolean(DISPLAY_USERNAME, false)) {
      username.setVisibility(View.VISIBLE);
      usernameEditButton.setVisibility(View.VISIBLE);
      usernameLabel.setVisibility(View.VISIBLE);
    }

    this.avatar.setOnClickListener(v -> Permissions.with(this)
               .request(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)
               .ifNecessary()
               .onAnyResult(this::startAvatarSelection)
               .execute());

    this.givenName .addTextChangedListener(new AfterTextChanged(s -> {
                                                                       trimInPlace(s);
                                                                       viewModel.setGivenName(s.toString());
                                                                     }));
    this.familyName.addTextChangedListener(new AfterTextChanged(s -> {
                                                                       trimInPlace(s);
                                                                       viewModel.setFamilyName(s.toString());
                                                                     }));

    this.finishButton.setOnClickListener(v -> {
      this.finishButton.setIndeterminateProgressMode(true);
      this.finishButton.setProgress(50);
      handleUpload();
    });

    this.finishButton.setText(arguments.getInt(NEXT_BUTTON_TEXT, R.string.CreateProfileActivity_next));

    this.usernameEditButton.setOnClickListener(v -> {
      NavDirections action = EditProfileFragmentDirections.actionEditUsername();
      Navigation.findNavController(v).navigate(action);
    });

    if (arguments.getBoolean(SHOW_TOOLBAR, true)) {
      this.toolbar.setVisibility(View.VISIBLE);
      this.toolbar.setNavigationOnClickListener(v -> requireActivity().finish());
      this.title.setVisibility(View.GONE);
    }
  }

  private void initializeProfileName() {
    viewModel.givenName().observe(this, givenName -> updateFieldIfNeeded(this.givenName, givenName));

    viewModel.familyName().observe(this, familyName -> updateFieldIfNeeded(this.familyName, familyName));

    viewModel.profileName().observe(this, profileName -> {
      preview.setText(profileName.toString());

      boolean validEntry = !profileName.isGivenNameEmpty();

      finishButton.setEnabled(validEntry);
      finishButton.setAlpha(validEntry ? 1f : 0.5f);
    });
  }

  private void initializeProfileAvatar() {
    viewModel.avatar().observe(this, bytes -> {
      if (bytes == null) return;

      GlideApp.with(this)
              .load(bytes)
              .circleCrop()
              .into(avatar);
    });
  }

  private void initializeUsername() {
    viewModel.username().observe(this, this::onUsernameChanged);
  }

  private static void updateFieldIfNeeded(@NonNull EditText field, @NonNull String value) {
    String fieldTrimmed = field.getText().toString().trim();
    String valueTrimmed = value.trim();

    if (!fieldTrimmed.equals(valueTrimmed)) {
      boolean setSelectionToEnd = field.getText().length() == 0;

      field.setText(value);

      if (setSelectionToEnd) {
        field.setSelection(field.getText().length());
      }
    }
  }

  private void onUsernameChanged(@NonNull Optional<String> username) {
    this.username.setText(username.transform(s -> "@" + s).or(""));
  }

  private void startAvatarSelection() {
    AvatarSelectionBottomSheetDialogFragment.create(viewModel.hasAvatar(), true, REQUEST_CODE_SELECT_AVATAR).show(getChildFragmentManager(), null);
  }

  private void handleUpload() {
    viewModel.submitProfile(uploadResult -> {
      if (uploadResult == EditProfileRepository.UploadResult.SUCCESS) {
        RegistrationUtil.markRegistrationPossiblyComplete();

        ApplicationDependencies.getMegaphoneRepository().markFinished(Megaphones.Event.PROFILE_NAMES_FOR_ALL);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) handleFinishedLollipop();
        else                                                       handleFinishedLegacy();
      } else {
        Toast.makeText(requireContext(), R.string.CreateProfileActivity_problem_setting_profile, Toast.LENGTH_LONG).show();
      }
    });
  }

  private void handleFinishedLegacy() {
    finishButton.setProgress(0);
    if (nextIntent != null) startActivity(nextIntent);

    controller.onProfileNameUploadCompleted();
  }

  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  private void handleFinishedLollipop() {
    int[] finishButtonLocation = new int[2];
    int[] revealLocation       = new int[2];

    finishButton.getLocationInWindow(finishButtonLocation);
    reveal.getLocationInWindow(revealLocation);

    int finishX = finishButtonLocation[0] - revealLocation[0];
    int finishY = finishButtonLocation[1] - revealLocation[1];

    finishX += finishButton.getWidth() / 2;
    finishY += finishButton.getHeight() / 2;

    Animator animation = ViewAnimationUtils.createCircularReveal(reveal, finishX, finishY, 0f, (float) Math.max(reveal.getWidth(), reveal.getHeight()));
    animation.setDuration(500);
    animation.addListener(new Animator.AnimatorListener() {
      @Override
      public void onAnimationStart(Animator animation) {}

      @Override
      public void onAnimationEnd(Animator animation) {
        finishButton.setProgress(0);
        if (nextIntent != null)  startActivity(nextIntent);

        controller.onProfileNameUploadCompleted();
      }

      @Override
      public void onAnimationCancel(Animator animation) {}

      @Override
      public void onAnimationRepeat(Animator animation) {}
    });

    reveal.setVisibility(View.VISIBLE);
    animation.start();
  }

  private static void trimInPlace(Editable s) {
    int trimmedLength = ProfileName.trimToFit(s.toString()).length();
    if (s.length() > trimmedLength) {
      s.delete(trimmedLength, s.length());
    }
  }

  public interface Controller {
    void onProfileNameUploadCompleted();
  }
}
