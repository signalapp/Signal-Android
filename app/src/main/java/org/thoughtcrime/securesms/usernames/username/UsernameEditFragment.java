package org.thoughtcrime.securesms.usernames.username;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;
import androidx.navigation.fragment.NavHostFragment;

import com.dd.CircularProgressButton;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.contactshare.SimpleTextWatcher;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.UsernameUtil;

public class UsernameEditFragment extends Fragment {

  private static final float DISABLED_ALPHA = 0.5f;

  private UsernameEditViewModel viewModel;

  private EditText               usernameInput;
  private TextView               usernameSubtext;
  private CircularProgressButton submitButton;
  private CircularProgressButton deleteButton;

  public static UsernameEditFragment newInstance() {
    return new UsernameEditFragment();
  }

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.username_edit_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    usernameInput   = view.findViewById(R.id.username_text);
    usernameSubtext = view.findViewById(R.id.username_subtext);
    submitButton    = view.findViewById(R.id.username_submit_button);
    deleteButton    = view.findViewById(R.id.username_delete_button);

    viewModel = ViewModelProviders.of(this, new UsernameEditViewModel.Factory()).get(UsernameEditViewModel.class);

    viewModel.getUiState().observe(getViewLifecycleOwner(), this::onUiStateChanged);
    viewModel.getEvents().observe(getViewLifecycleOwner(), this::onEvent);

    submitButton.setOnClickListener(v -> viewModel.onUsernameSubmitted(usernameInput.getText().toString()));
    deleteButton.setOnClickListener(v -> viewModel.onUsernameDeleted());

    usernameInput.setText(TextSecurePreferences.getLocalUsername(requireContext()));
    usernameInput.addTextChangedListener(new SimpleTextWatcher() {
      @Override
      public void onTextChanged(String text) {
        viewModel.onUsernameUpdated(text);
      }
    });
  }

  private void onUiStateChanged(@NonNull UsernameEditViewModel.State state) {
    usernameInput.setEnabled(true);

    switch (state.getButtonState()) {
      case SUBMIT:
        cancelSpinning(submitButton);
        submitButton.setVisibility(View.VISIBLE);
        submitButton.setEnabled(true);
        submitButton.setAlpha(1);
        deleteButton.setVisibility(View.GONE);
        break;
      case SUBMIT_DISABLED:
        cancelSpinning(submitButton);
        submitButton.setVisibility(View.VISIBLE);
        submitButton.setEnabled(false);
        submitButton.setAlpha(DISABLED_ALPHA);
        deleteButton.setVisibility(View.GONE);
        break;
      case SUBMIT_LOADING:
        setSpinning(submitButton);
        submitButton.setVisibility(View.VISIBLE);
        submitButton.setAlpha(1);
        deleteButton.setVisibility(View.GONE);
        usernameInput.setEnabled(false);
        break;
      case DELETE:
        cancelSpinning(deleteButton);
        deleteButton.setVisibility(View.VISIBLE);
        deleteButton.setEnabled(true);
        deleteButton.setAlpha(1);
        submitButton.setVisibility(View.GONE);
        break;
      case DELETE_DISABLED:
        cancelSpinning(deleteButton);
        deleteButton.setVisibility(View.VISIBLE);
        deleteButton.setEnabled(false);
        deleteButton.setAlpha(DISABLED_ALPHA);
        submitButton.setVisibility(View.GONE);
        break;
      case DELETE_LOADING:
        setSpinning(deleteButton);
        deleteButton.setVisibility(View.VISIBLE);
        deleteButton.setAlpha(1);
        submitButton.setVisibility(View.GONE);
        usernameInput.setEnabled(false);
        break;
    }

    switch (state.getUsernameStatus()) {
      case NONE:
        usernameSubtext.setText("");
        break;
      case TOO_SHORT:
      case TOO_LONG:
        usernameSubtext.setText(getResources().getString(R.string.UsernameEditFragment_usernames_must_be_between_a_and_b_characters, UsernameUtil.MIN_LENGTH, UsernameUtil.MAX_LENGTH));
        usernameSubtext.setTextColor(getResources().getColor(R.color.core_red));
        break;
      case INVALID_CHARACTERS:
        usernameSubtext.setText(R.string.UsernameEditFragment_usernames_can_only_include);
        usernameSubtext.setTextColor(getResources().getColor(R.color.core_red));
        break;
      case CANNOT_START_WITH_NUMBER:
        usernameSubtext.setText(R.string.UsernameEditFragment_usernames_cannot_begin_with_a_number);
        usernameSubtext.setTextColor(getResources().getColor(R.color.core_red));
        break;
      case INVALID_GENERIC:
        usernameSubtext.setText(R.string.UsernameEditFragment_username_is_invalid);
        usernameSubtext.setTextColor(getResources().getColor(R.color.core_red));
        break;
      case TAKEN:
        usernameSubtext.setText(R.string.UsernameEditFragment_this_username_is_taken);
        usernameSubtext.setTextColor(getResources().getColor(R.color.core_red));
        break;
      case AVAILABLE:
        usernameSubtext.setText(R.string.UsernameEditFragment_this_username_is_available);
        usernameSubtext.setTextColor(getResources().getColor(R.color.core_green));
        break;
    }
  }

  private void onEvent(@NonNull UsernameEditViewModel.Event event) {
    switch (event) {
      case SUBMIT_SUCCESS:
        Toast.makeText(requireContext(), R.string.UsernameEditFragment_successfully_set_username, Toast.LENGTH_SHORT).show();
        NavHostFragment.findNavController(this).popBackStack();
        break;
      case SUBMIT_FAIL_TAKEN:
        Toast.makeText(requireContext(), R.string.UsernameEditFragment_this_username_is_taken, Toast.LENGTH_SHORT).show();
        break;
      case SUBMIT_FAIL_INVALID:
        Toast.makeText(requireContext(), R.string.UsernameEditFragment_username_is_invalid, Toast.LENGTH_SHORT).show();
        break;
      case DELETE_SUCCESS:
        Toast.makeText(requireContext(), R.string.UsernameEditFragment_successfully_removed_username, Toast.LENGTH_SHORT).show();
        NavHostFragment.findNavController(this).popBackStack();
        break;
      case NETWORK_FAILURE:
        Toast.makeText(requireContext(), R.string.UsernameEditFragment_encountered_a_network_error, Toast.LENGTH_SHORT).show();
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
