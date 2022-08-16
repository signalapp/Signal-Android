package org.thoughtcrime.securesms.profiles.manage;

import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import org.signal.core.util.DimensionUnit;
import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.contactshare.SimpleTextWatcher;
import org.thoughtcrime.securesms.databinding.UsernameEditFragmentBinding;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.FragmentResultContract;
import org.thoughtcrime.securesms.util.LifecycleDisposable;
import org.thoughtcrime.securesms.util.UsernameUtil;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.views.CircularProgressMaterialButton;
import org.thoughtcrime.securesms.util.views.LearnMoreTextView;

import java.util.Objects;
import java.util.function.Consumer;

public class UsernameEditFragment extends LoggingFragment {

  private static final float DISABLED_ALPHA = 0.5f;

  private UsernameEditViewModel       viewModel;
  private UsernameEditFragmentBinding binding;
  private ImageView                   suffixProgress;
  private LifecycleDisposable         lifecycleDisposable;

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
    binding.toolbar.setNavigationOnClickListener(v -> Navigation.findNavController(view).popBackStack());

    binding.usernameTextWrapper.setErrorIconDrawable(null);

    lifecycleDisposable = new LifecycleDisposable();
    lifecycleDisposable.bindTo(getViewLifecycleOwner());

    viewModel = new ViewModelProvider(this, new UsernameEditViewModel.Factory()).get(UsernameEditViewModel.class);

    lifecycleDisposable.add(viewModel.getUiState().subscribe(this::onUiStateChanged));
    viewModel.getEvents().observe(getViewLifecycleOwner(), this::onEvent);

    binding.usernameSubmitButton.setOnClickListener(v -> viewModel.onUsernameSubmitted(binding.usernameText.getText().toString()));
    binding.usernameDeleteButton.setOnClickListener(v -> viewModel.onUsernameDeleted());

    binding.usernameText.setText(Recipient.self().getUsername().orElse(null));
    binding.usernameText.addTextChangedListener(new SimpleTextWatcher() {
      @Override
      public void onTextChanged(String text) {
        viewModel.onUsernameUpdated(text);
      }
    });
    binding.usernameText.setOnEditorActionListener((v, actionId, event) -> {
      if (actionId == EditorInfo.IME_ACTION_DONE) {
        viewModel.onUsernameSubmitted(binding.usernameText.getText().toString());
        return true;
      }
      return false;
    });

    binding.usernameDescription.setLinkColor(ContextCompat.getColor(requireContext(), R.color.signal_colorPrimary));
    binding.usernameDescription.setLearnMoreVisible(true);
    binding.usernameDescription.setOnLinkClickListener(this::onLearnMore);

    initializeSuffix();
    ViewUtil.focusAndShowKeyboard(binding.usernameText);
  }

  private void initializeSuffix() {
    TextView suffixTextView = binding.usernameTextWrapper.getSuffixTextView();
    Drawable pipe           = Objects.requireNonNull(ContextCompat.getDrawable(requireContext(), R.drawable.pipe_divider));

    pipe.setBounds(0, 0, (int) DimensionUnit.DP.toPixels(1f), (int) DimensionUnit.DP.toPixels(20f));
    suffixTextView.setCompoundDrawablesRelative(pipe, null, null, null);

    LinearLayout              suffixParent   = (LinearLayout) suffixTextView.getParent();
    LinearLayout.LayoutParams layoutParams   = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);

    ViewUtil.setLeftMargin(suffixTextView, (int) DimensionUnit.DP.toPixels(16f));

    binding.usernameTextWrapper.getSuffixTextView().setCompoundDrawablePadding((int) DimensionUnit.DP.toPixels(16f));

    layoutParams.topMargin    = suffixTextView.getPaddingTop();
    layoutParams.bottomMargin = suffixTextView.getPaddingBottom();

    suffixProgress = new ImageView(requireContext());
    suffixProgress.setImageDrawable(UsernameSuffix.getInProgressDrawable(requireContext()));
    suffixParent.addView(suffixProgress, 0, layoutParams);

    suffixTextView.setOnClickListener(this::onLearnMore);
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    binding = null;
    suffixProgress = null;
  }

  private void onLearnMore(@Nullable View unused) {
    new MaterialAlertDialogBuilder(requireContext())
        .setTitle(new StringBuilder("#\n").append(getString(R.string.UsernameEditFragment__what_is_this_number)))
        .setMessage(R.string.UsernameEditFragment__these_digits_help_keep)
        .setPositiveButton(android.R.string.ok, (dialog, which) -> {})
        .show();
  }

  private void onUiStateChanged(@NonNull UsernameEditViewModel.State state) {
    EditText                       usernameInput        = binding.usernameText;
    CircularProgressMaterialButton submitButton         = binding.usernameSubmitButton;
    CircularProgressMaterialButton deleteButton         = binding.usernameDeleteButton;
    TextInputLayout                usernameInputWrapper = binding.usernameTextWrapper;

    usernameInput.setEnabled(true);
    presentSuffix(state.getUsernameSuffix());

    switch (state.getButtonState()) {
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

    switch (state.getUsernameStatus()) {
      case NONE:
        usernameInputWrapper.setError(null);
        break;
      case TOO_SHORT:
      case TOO_LONG:
        usernameInputWrapper.setError(getResources().getString(R.string.UsernameEditFragment_usernames_must_be_between_a_and_b_characters, UsernameUtil.MIN_LENGTH, UsernameUtil.MAX_LENGTH));
        usernameInputWrapper.setErrorTextColor(ColorStateList.valueOf(getResources().getColor(R.color.signal_colorError)));

        break;
      case INVALID_CHARACTERS:
        usernameInputWrapper.setError(getResources().getString(R.string.UsernameEditFragment_usernames_can_only_include));
        usernameInputWrapper.setErrorTextColor(ColorStateList.valueOf(getResources().getColor(R.color.signal_colorError)));

        break;
      case CANNOT_START_WITH_NUMBER:
        usernameInputWrapper.setError(getResources().getString(R.string.UsernameEditFragment_usernames_cannot_begin_with_a_number));
        usernameInputWrapper.setErrorTextColor(ColorStateList.valueOf(getResources().getColor(R.color.signal_colorError)));

        break;
      case INVALID_GENERIC:
        usernameInputWrapper.setError(getResources().getString(R.string.UsernameEditFragment_username_is_invalid));
        usernameInputWrapper.setErrorTextColor(ColorStateList.valueOf(getResources().getColor(R.color.signal_colorError)));

        break;
      case TAKEN:
        usernameInputWrapper.setError(getResources().getString(R.string.UsernameEditFragment_this_username_is_taken));
        usernameInputWrapper.setErrorTextColor(ColorStateList.valueOf(getResources().getColor(R.color.signal_colorError)));

        break;
      case AVAILABLE:
        usernameInputWrapper.setError(getResources().getString(R.string.UsernameEditFragment_this_username_is_available));
        usernameInputWrapper.setErrorTextColor(ColorStateList.valueOf(getResources().getColor(R.color.signal_accent_green)));
        break;
    }
  }

  private void presentSuffix(@NonNull UsernameSuffix usernameSuffix) {
    binding.usernameTextWrapper.setSuffixText(usernameSuffix.getCharSequence());

    boolean isInProgress = usernameSuffix.isInProgress();

    if (isInProgress) {
      suffixProgress.setVisibility(View.VISIBLE);
    } else {
      suffixProgress.setVisibility(View.GONE);
    }
  }

  private void onEvent(@NonNull UsernameEditViewModel.Event event) {
    switch (event) {
      case SUBMIT_SUCCESS:
        ResultContract.setUsernameCreated(getParentFragmentManager());
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
