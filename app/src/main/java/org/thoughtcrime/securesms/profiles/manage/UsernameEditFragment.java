package org.thoughtcrime.securesms.profiles.manage;

import android.animation.LayoutTransition;
import android.app.Activity;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import org.signal.core.util.EditTextUtil;
import org.signal.core.util.concurrent.LifecycleDisposable;
import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.contactshare.SimpleTextWatcher;
import org.thoughtcrime.securesms.databinding.UsernameEditFragmentBinding;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.util.FragmentResultContract;
import org.thoughtcrime.securesms.util.UsernameUtil;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.views.CircularProgressMaterialButton;

public class UsernameEditFragment extends LoggingFragment {

  private static final float DISABLED_ALPHA           = 0.5f;
  public static final String IGNORE_TEXT_CHANGE_EVENT = "ignore.text.change.event";

  public static final int REQUEST_CODE = 4242;

  private UsernameEditViewModel       viewModel;
  private UsernameEditFragmentBinding binding;
  private LifecycleDisposable         lifecycleDisposable;
  private UsernameEditFragmentArgs    args;

  private static final LayoutTransition ANIMATED_LAYOUT = new LayoutTransition();
  private static final LayoutTransition STATIC_LAYOUT   = new LayoutTransition();

  static {
    STATIC_LAYOUT.disableTransitionType(LayoutTransition.CHANGE_APPEARING);
    STATIC_LAYOUT.disableTransitionType(LayoutTransition.CHANGE_DISAPPEARING);
    STATIC_LAYOUT.disableTransitionType(LayoutTransition.APPEARING);
    STATIC_LAYOUT.disableTransitionType(LayoutTransition.DISAPPEARING);
    STATIC_LAYOUT.disableTransitionType(LayoutTransition.CHANGING);
  }

  public static UsernameEditFragment newInstance() {
    return new UsernameEditFragment();
  }

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    binding = UsernameEditFragmentBinding.inflate(inflater, container, false);
    return binding.getRoot();
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    Bundle bundle = getArguments();
    if (bundle != null) {
      args = UsernameEditFragmentArgs.fromBundle(bundle);
    } else {
      args = new UsernameEditFragmentArgs.Builder().build();
    }

    binding.toolbar.setNavigationOnClickListener(v -> {
      if (args.getMode() == UsernameEditMode.RECOVERY) {
        getActivity().finish();
      } else {
        Navigation.findNavController(view).popBackStack();
      }
    });
    binding.usernameSubmitButton.setVisibility(View.VISIBLE);

    binding.usernameTextWrapper.setErrorIconDrawable(null);

    lifecycleDisposable = new LifecycleDisposable();
    lifecycleDisposable.bindTo(getViewLifecycleOwner());

    viewModel = new ViewModelProvider(this, new UsernameEditViewModel.Factory(args.getMode())).get(UsernameEditViewModel.class);

    lifecycleDisposable.add(viewModel.getUiState().subscribe(this::onUiStateChanged));
    lifecycleDisposable.add(viewModel.getEvents().subscribe(this::onEvent));
    lifecycleDisposable.add(viewModel.getUsernameInputState().subscribe(this::presentUsernameInputState));

    binding.usernameSubmitButton.setOnClickListener(v -> promptOrSubmitUsername());
    binding.usernameDeleteButton.setOnClickListener(v -> viewModel.onUsernameDeleted());
    binding.usernameDoneButton.setOnClickListener(v -> viewModel.onUsernameSubmitted(false));
    binding.usernameSkipButton.setOnClickListener(v -> viewModel.onUsernameSkipped());

    binding.usernameText.addTextChangedListener(new SimpleTextWatcher() {
      @Override
      public void onTextChanged(@NonNull String text) {
        if (binding.usernameText.getTag() != IGNORE_TEXT_CHANGE_EVENT) {
          viewModel.onNicknameUpdated(text);
        }
      }
    });

    binding.discriminatorText.addTextChangedListener(new SimpleTextWatcher() {
      @Override
      public void onTextChanged(@NonNull String text) {
        if (binding.discriminatorText.getTag() != IGNORE_TEXT_CHANGE_EVENT) {
          viewModel.onDiscriminatorUpdated(text);
        }
      }
    });

    binding.discriminatorText.setOnEditorActionListener((v, actionId, event) -> {
      if (actionId == EditorInfo.IME_ACTION_DONE) {
        promptOrSubmitUsername();
        return true;
      }
      return false;
    });

    binding.usernameDescription.setLinkColor(ContextCompat.getColor(requireContext(), R.color.signal_colorPrimary));
    binding.usernameDescription.setLearnMoreVisible(true);
    binding.usernameDescription.setOnLinkClickListener(this::onLearnMore);

    ViewUtil.focusAndShowKeyboard(binding.usernameText);
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    binding = null;
  }

  private void promptOrSubmitUsername() {
    if (args.getMode() == UsernameEditMode.RECOVERY) {
      new MaterialAlertDialogBuilder(requireContext())
          .setMessage(R.string.UsernameEditFragment_recovery_dialog_confirmation)
          .setPositiveButton(android.R.string.ok, ((dialog, which) -> {
            viewModel.onUsernameSubmitted(true);
            dialog.dismiss();
          }))
          .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss())
          .show();
    } else {
      viewModel.onUsernameSubmitted(false);
    }
  }


  private void onLearnMore(@Nullable View unused) {
    new MaterialAlertDialogBuilder(requireContext())
        .setTitle(getString(R.string.UsernameEditFragment__what_is_this_number))
        .setMessage(R.string.UsernameEditFragment__these_digits_help_keep)
        .setPositiveButton(android.R.string.ok, (dialog, which) -> {})
        .show();
  }

  private void onUiStateChanged(@NonNull UsernameEditViewModel.State state) {
    presentProgressState(state.usernameState);
    presentButtonState(state.buttonState);
    presentSummary(state.usernameState);

    binding.root.setLayoutTransition(ANIMATED_LAYOUT);

    CharSequence error = switch (state.usernameStatus) {
      case NONE -> null;
      case TOO_SHORT, TOO_LONG -> getString(R.string.UsernameEditFragment_usernames_must_be_between_a_and_b_characters, UsernameUtil.MIN_NICKNAME_LENGTH, UsernameUtil.MAX_NICKNAME_LENGTH);
      case INVALID_CHARACTERS -> getString(R.string.UsernameEditFragment_usernames_can_only_include);
      case CANNOT_START_WITH_NUMBER -> getString(R.string.UsernameEditFragment_usernames_cannot_begin_with_a_number);
      case INVALID_GENERIC -> getString(R.string.UsernameEditFragment_username_is_invalid);
      case TAKEN -> getString(R.string.UsernameEditFragment_this_username_is_taken);
      case DISCRIMINATOR_HAS_INVALID_CHARACTERS, DISCRIMINATOR_NOT_AVAILABLE -> getString(R.string.UsernameEditFragment__this_username_is_not_available_try_another_number);
      case DISCRIMINATOR_TOO_LONG -> getString(R.string.UsernameEditFragment__invalid_username_enter_a_maximum_of_d_digits, UsernameUtil.MAX_DISCRIMINATOR_LENGTH);
      case DISCRIMINATOR_TOO_SHORT -> getString(R.string.UsernameEditFragment__invalid_username_enter_a_minimum_of_d_digits, UsernameUtil.MIN_DISCRIMINATOR_LENGTH);
      case DISCRIMINATOR_CANNOT_BE_00 -> getString(R.string.UsernameEditFragment__this_number_cant_be_00);
      case DISCRIMINATOR_CANNOT_START_WITH_00 -> getString(R.string.UsernameEditFragment__this_number_cant_start_with_00);
    };

    int colorRes = error != null ? R.color.signal_colorError : R.color.signal_colorPrimary;
    int color = ContextCompat.getColor(requireContext(), colorRes);

    binding.usernameTextFocusedStroke.setBackgroundColor(color);
    binding.usernameTextWrapper.setHintTextColor(ColorStateList.valueOf(color));
    EditTextUtil.setCursorColor(binding.usernameText, color);
    EditTextUtil.setCursorColor(binding.discriminatorText, color);
    binding.usernameError.setVisibility(error != null ? View.VISIBLE : View.GONE);
    binding.usernameError.setText(error);
    binding.root.setLayoutTransition(STATIC_LAYOUT);

    if (state.usernameState.getDiscriminator() == null && SignalStore.account().getUsername() == null) {
      binding.discriminatorText.setVisibility(View.GONE);
      binding.divider.setVisibility(View.GONE);
    } else {
      binding.discriminatorText.setVisibility(View.VISIBLE);
      binding.divider.setVisibility(View.VISIBLE);
    }
  }

  private void presentButtonState(@NonNull UsernameEditViewModel.ButtonState buttonState) {
    CircularProgressMaterialButton submitButton         = binding.usernameSubmitButton;
    CircularProgressMaterialButton deleteButton         = binding.usernameDeleteButton;
    EditText                       usernameInput        = binding.usernameText;

    usernameInput.setEnabled(true);
    switch (buttonState) {
      case SUBMIT:
        submitButton.cancelSpinning();
        submitButton.setVisibility(View.VISIBLE);
        submitButton.setEnabled(true);
        submitButton.setAlpha(1);
        deleteButton.setVisibility(View.GONE);
        break;
      case SUBMIT_DISABLED:
        submitButton.cancelSpinning();
        submitButton.setVisibility(View.VISIBLE);
        submitButton.setEnabled(false);
        submitButton.setAlpha(DISABLED_ALPHA);
        deleteButton.setVisibility(View.GONE);
        break;
      case SUBMIT_LOADING:
        submitButton.setSpinning();
        submitButton.setVisibility(View.VISIBLE);
        submitButton.setAlpha(1);
        deleteButton.setVisibility(View.GONE);
        usernameInput.setEnabled(false);
        break;
      case DELETE:
        deleteButton.cancelSpinning();
        deleteButton.setVisibility(View.VISIBLE);
        deleteButton.setEnabled(true);
        deleteButton.setAlpha(1);
        submitButton.setVisibility(View.GONE);
        break;
      case DELETE_DISABLED:
        deleteButton.cancelSpinning();
        deleteButton.setVisibility(View.VISIBLE);
        deleteButton.setEnabled(false);
        deleteButton.setAlpha(DISABLED_ALPHA);
        submitButton.setVisibility(View.GONE);
        break;
      case DELETE_LOADING:
        deleteButton.setSpinning();
        deleteButton.setVisibility(View.VISIBLE);
        deleteButton.setAlpha(1);
        submitButton.setVisibility(View.GONE);
        usernameInput.setEnabled(false);
        break;
    }
  }

  private void presentSummary(@NonNull UsernameState usernameState) {
    if (usernameState.getUsername() != null) {
      binding.summary.setText(usernameState.getUsername().getUsername());
      binding.summary.setAlpha(1f);
    } else if (!(usernameState instanceof UsernameState.Loading)) {
      binding.summary.setText(R.string.UsernameEditFragment__choose_your_username);
      binding.summary.setAlpha(1f);
    }
  }

  private void presentUsernameInputState(@NonNull UsernameEditStateMachine.State state) {
    binding.usernameText.setTag(IGNORE_TEXT_CHANGE_EVENT);
    String nickname = state.getNickname();
    if (!binding.usernameText.getText().toString().equals(nickname)) {
      binding.usernameText.setText(state.getNickname());
      binding.usernameText.setSelection(binding.usernameText.length());
    }
    binding.usernameText.setTag(null);

    binding.discriminatorText.setTag(IGNORE_TEXT_CHANGE_EVENT);
    String discriminator = state.getDiscriminator();
    if (!binding.discriminatorText.getText().toString().equals(discriminator)) {
      binding.discriminatorText.setText(state.getDiscriminator());
      binding.discriminatorText.setSelection(binding.discriminatorText.length());
    }
    binding.discriminatorText.setTag(null);
  }

  private void presentProgressState(@NonNull UsernameState usernameState) {
    boolean isInProgress = usernameState.isInProgress();

    if (isInProgress) {
      binding.suffixProgress.setVisibility(View.VISIBLE);
    } else {
      binding.suffixProgress.setVisibility(View.GONE);
    }
  }

  private void onEvent(@NonNull UsernameEditViewModel.Event event) {
    switch (event) {
      case SUBMIT_SUCCESS:
        ResultContract.setUsernameCreated(getParentFragmentManager());
        if (getActivity() != null) {
          getActivity().setResult(Activity.RESULT_OK);
        }
        closeScreen();
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
      case SKIPPED:
        closeScreen();
        break;
      case NEEDS_CONFIRM_RESET:
        new MaterialAlertDialogBuilder(requireContext())
            .setMessage(R.string.UsernameEditFragment_change_confirmation_message)
            .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss())
            .setPositiveButton(R.string.UsernameEditFragment_continue, (dialog, which) -> viewModel.onUsernameSubmitted(true))
            .show();
    }
  }

  private void closeScreen() {
    if (args.getMode() == UsernameEditMode.RECOVERY) {
      getActivity().finish();
    } else {
      NavHostFragment.findNavController(this).popBackStack();
    }
  }

  static class ResultContract extends FragmentResultContract<Boolean> {
    private static final String REQUEST_KEY = "username_created";

    protected ResultContract() {
      super(REQUEST_KEY);
    }

    static void setUsernameCreated(@NonNull FragmentManager fragmentManager) {
      Bundle bundle = new Bundle();
      bundle.putBoolean(REQUEST_KEY, true);
      fragmentManager.setFragmentResult(REQUEST_KEY, bundle);
    }

    @Override
    protected Boolean getResult(@NonNull Bundle bundle) {
      return bundle.getBoolean(REQUEST_KEY, false);
    }
  }
}
