package org.thoughtcrime.securesms.profiles.manage;

import android.content.Intent;
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
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicatorSpec;
import com.google.android.material.progressindicator.IndeterminateDrawable;
import com.google.android.material.textfield.TextInputLayout;

import org.signal.core.util.DimensionUnit;
import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.PassphraseRequiredActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.contactshare.SimpleTextWatcher;
import org.thoughtcrime.securesms.databinding.UsernameEditFragmentBinding;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.FragmentResultContract;
import org.signal.core.util.concurrent.LifecycleDisposable;
import org.thoughtcrime.securesms.util.UsernameUtil;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.views.CircularProgressMaterialButton;

import java.util.Objects;

public class UsernameEditFragment extends LoggingFragment {

  private static final float DISABLED_ALPHA = 0.5f;

  private UsernameEditViewModel       viewModel;
  private UsernameEditFragmentBinding binding;
  private ImageView                   suffixProgress;
  private LifecycleDisposable         lifecycleDisposable;
  private UsernameEditFragmentArgs    args;

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

    if (args.getIsInRegistration()) {
      binding.toolbar.setNavigationIcon(null);
      binding.toolbar.setTitle(R.string.UsernameEditFragment__add_a_username);
      binding.usernameSkipButton.setVisibility(View.VISIBLE);
      binding.usernameDoneButton.setVisibility(View.VISIBLE);
    } else {
      binding.toolbar.setNavigationOnClickListener(v -> Navigation.findNavController(view).popBackStack());
      binding.usernameSubmitButton.setVisibility(View.VISIBLE);
    }

    binding.usernameTextWrapper.setErrorIconDrawable(null);

    lifecycleDisposable = new LifecycleDisposable();
    lifecycleDisposable.bindTo(getViewLifecycleOwner());

    viewModel = new ViewModelProvider(this, new UsernameEditViewModel.Factory(args.getIsInRegistration())).get(UsernameEditViewModel.class);

    lifecycleDisposable.add(viewModel.getUiState().subscribe(this::onUiStateChanged));
    lifecycleDisposable.add(viewModel.getEvents().subscribe(this::onEvent));

    binding.usernameSubmitButton.setOnClickListener(v -> viewModel.onUsernameSubmitted());
    binding.usernameDeleteButton.setOnClickListener(v -> viewModel.onUsernameDeleted());
    binding.usernameDoneButton.setOnClickListener(v -> viewModel.onUsernameSubmitted());
    binding.usernameSkipButton.setOnClickListener(v -> viewModel.onUsernameSkipped());

    UsernameState usernameState = Recipient.self().getUsername().<UsernameState>map(UsernameState.Set::new).orElse(UsernameState.NoUsername.INSTANCE);
    binding.usernameText.setText(usernameState.getNickname());
    binding.usernameText.addTextChangedListener(new SimpleTextWatcher() {
      @Override
      public void onTextChanged(@NonNull String text) {
        viewModel.onNicknameUpdated(text);
      }
    });
    binding.usernameText.setOnEditorActionListener((v, actionId, event) -> {
      if (actionId == EditorInfo.IME_ACTION_DONE) {
        viewModel.onUsernameSubmitted();
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
    layoutParams.setMarginEnd(suffixTextView.getPaddingEnd());

    suffixProgress = new ImageView(requireContext());
    suffixProgress.setImageDrawable(getInProgressDrawable());
    suffixProgress.setContentDescription(getString(R.string.load_more_header__loading));
    suffixProgress.setVisibility(View.GONE);
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
    TextInputLayout usernameInputWrapper = binding.usernameTextWrapper;

    presentSuffix(state.getUsername());
    presentButtonState(state.getButtonState());
    presentSummary(state.getUsername());

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
    }

    CharSequence error = usernameInputWrapper.getError();
    binding.usernameError.setVisibility(error != null ? View.VISIBLE : View.GONE);
    binding.usernameError.setText(usernameInputWrapper.getError());
  }

  private void presentButtonState(@NonNull UsernameEditViewModel.ButtonState buttonState) {
    if (args.getIsInRegistration()) {
      presentRegistrationButtonState(buttonState);
    } else {
      presentProfileUpdateButtonState(buttonState);
    }
  }

  private void presentSummary(@NonNull UsernameState usernameState) {
    if (usernameState.getUsername() != null) {
      binding.summary.setText(usernameState.getUsername());
    } else {
      binding.summary.setText(R.string.UsernameEditFragment__choose_your_username);
    }
  }

  private void presentRegistrationButtonState(@NonNull UsernameEditViewModel.ButtonState buttonState) {
    binding.usernameText.setEnabled(true);
    binding.usernameProgressCard.setVisibility(View.GONE);

    switch (buttonState) {
      case SUBMIT:
        binding.usernameDoneButton.setEnabled(true);
        binding.usernameDoneButton.setAlpha(1f);
        break;
      case SUBMIT_DISABLED:
        binding.usernameDoneButton.setEnabled(false);
        binding.usernameDoneButton.setAlpha(DISABLED_ALPHA);
        break;
      case SUBMIT_LOADING:
        binding.usernameDoneButton.setEnabled(false);
        binding.usernameDoneButton.setAlpha(DISABLED_ALPHA);
        binding.usernameProgressCard.setVisibility(View.VISIBLE);
        break;
      default:
        throw new IllegalStateException("Delete functionality is not available during registration.");
    }
  }

  private void presentProfileUpdateButtonState(@NonNull UsernameEditViewModel.ButtonState buttonState) {
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

  private void presentSuffix(@NonNull UsernameState usernameState) {
    binding.usernameTextWrapper.setSuffixText(usernameState.getDiscriminator());

    boolean isInProgress = usernameState.isInProgress();

    if (isInProgress) {
      suffixProgress.setVisibility(View.VISIBLE);
    } else {
      suffixProgress.setVisibility(View.GONE);
    }
  }

  private IndeterminateDrawable<CircularProgressIndicatorSpec> getInProgressDrawable() {
    CircularProgressIndicatorSpec spec = new CircularProgressIndicatorSpec(requireContext(), null);
    spec.indicatorInset = 0;
    spec.indicatorSize = (int) DimensionUnit.DP.toPixels(16f);
    spec.trackColor = ContextCompat.getColor(requireContext(), R.color.signal_colorOnSurfaceVariant);
    spec.trackThickness = (int) DimensionUnit.DP.toPixels(1f);

    IndeterminateDrawable<CircularProgressIndicatorSpec> drawable = IndeterminateDrawable.createCircularDrawable(requireContext(), spec);
    drawable.setBounds(0, 0, spec.indicatorSize, spec.indicatorSize);

    return drawable;
  }

  private void onEvent(@NonNull UsernameEditViewModel.Event event) {
    switch (event) {
      case SUBMIT_SUCCESS:
        ResultContract.setUsernameCreated(getParentFragmentManager());
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
    }
  }

  private void closeScreen() {
    if (args.getIsInRegistration()) {
      finishAndStartNextIntent();
    } else {
      NavHostFragment.findNavController(this).popBackStack();
    }
  }

  private void finishAndStartNextIntent() {
    FragmentActivity activity       = requireActivity();
    boolean          didLaunch      = false;
    Intent           activityIntent = activity.getIntent();

    if (activityIntent != null) {
      Intent nextIntent = activityIntent.getParcelableExtra(PassphraseRequiredActivity.NEXT_INTENT_EXTRA);
      if (nextIntent != null) {
        activity.startActivity(nextIntent);
        activity.finish();
        didLaunch = true;
      }
    }

    if (!didLaunch) {
      activity.finish();
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
