package org.thoughtcrime.securesms.usernames.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;
import androidx.navigation.fragment.NavHostFragment;

import com.dd.CircularProgressButton;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

public class ProfileEditNameFragment extends Fragment {

  private EditText                 profileText;
  private CircularProgressButton   submitButton;
  private ProfileEditNameViewModel viewModel;

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.profile_edit_name_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    profileText  = view.findViewById(R.id.profile_name_text);
    submitButton = view.findViewById(R.id.profile_name_submit);

    viewModel = ViewModelProviders.of(this, new ProfileEditNameViewModel.Factory()).get(ProfileEditNameViewModel.class);

    viewModel.isLoading().observe(getViewLifecycleOwner(), this::onLoadingChanged);
    viewModel.getEvents().observe(getViewLifecycleOwner(), this::onEvent);

    profileText.setText(TextSecurePreferences.getProfileName(requireContext()));
    submitButton.setOnClickListener(v -> viewModel.onSubmitPressed(profileText.getText().toString()));
  }

  private void onLoadingChanged(boolean loading) {
    if (loading) {
      profileText.setEnabled(false);
      setSpinning(submitButton);
    } else {
      profileText.setEnabled(true);
      cancelSpinning(submitButton);
    }
  }

  private void onEvent(@NonNull ProfileEditNameViewModel.Event event) {
    switch (event) {
      case SUCCESS:
        Toast.makeText(requireContext(), R.string.ProfileEditNameFragment_successfully_set_profile_name, Toast.LENGTH_SHORT).show();
        NavHostFragment.findNavController(this).popBackStack();
        break;
      case NETWORK_FAILURE:
        Toast.makeText(requireContext(), R.string.ProfileEditNameFragment_encountered_a_network_error, Toast.LENGTH_SHORT).show();
        break;
    }
  }

  private static void setSpinning(@NonNull CircularProgressButton button) {
    button.setClickable(false);
    button.setIndeterminateProgressMode(true);
    button.setProgress(50);
  }

  private static void cancelSpinning(@NonNull CircularProgressButton button) {
    button.setProgress(0);
    button.setIndeterminateProgressMode(false);
    button.setClickable(true);
  }
}
