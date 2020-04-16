package org.thoughtcrime.securesms.lock.v2;

import android.os.Bundle;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import com.airbnb.lottie.LottieAnimationView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.thoughtcrime.securesms.util.text.AfterTextChanged;
import org.thoughtcrime.securesms.util.views.LearnMoreTextView;

abstract class BaseKbsPinFragment<ViewModel extends BaseKbsPinViewModel> extends Fragment {

  private TextView            title;
  private LearnMoreTextView   description;
  private EditText            input;
  private TextView            label;
  private TextView            keyboardToggle;
  private TextView            confirm;
  private LottieAnimationView lottieProgress;
  private LottieAnimationView lottieEnd;
  private ViewModel           viewModel;

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater,
                                     @Nullable ViewGroup container,
                                     @Nullable Bundle savedInstanceState)
  {
    return inflater.inflate(R.layout.base_kbs_pin_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    initializeViews(view);

    viewModel = initializeViewModel();
    viewModel.getUserEntry().observe(getViewLifecycleOwner(), kbsPin -> {
      boolean isEntryValid = kbsPin.length() >= KbsConstants.MINIMUM_PIN_LENGTH;

      confirm.setEnabled(isEntryValid);
      confirm.setAlpha(isEntryValid ? 1f : 0.5f);
    });

    viewModel.getKeyboard().observe(getViewLifecycleOwner(), keyboardType -> {
      updateKeyboard(keyboardType);
      keyboardToggle.setText(resolveKeyboardToggleText(keyboardType));
    });

    description.setOnLinkClickListener(v -> {
      CommunicationActions.openBrowserLink(requireContext(), getString(R.string.BaseKbsPinFragment__learn_more_url));
    });

    initializeListeners();
  }

  @Override
  public void onResume() {
    super.onResume();

    input.requestFocus();
  }

  protected abstract ViewModel initializeViewModel();

  protected abstract void initializeViewStates();

  protected TextView getTitle() {
    return title;
  }

  protected LearnMoreTextView getDescription() {
    return description;
  }

  protected EditText getInput() {
    return input;
  }

  protected LottieAnimationView getLottieProgress() {
    return lottieProgress;
  }

  protected LottieAnimationView getLottieEnd() {
    return lottieEnd;
  }

  protected TextView getLabel() {
    return label;
  }

  protected TextView getKeyboardToggle() {
    return keyboardToggle;
  }

  protected TextView getConfirm() {
    return confirm;
  }

  private void initializeViews(@NonNull View view) {
    title          = view.findViewById(R.id.edit_kbs_pin_title);
    description    = view.findViewById(R.id.edit_kbs_pin_description);
    input          = view.findViewById(R.id.edit_kbs_pin_input);
    label          = view.findViewById(R.id.edit_kbs_pin_input_label);
    keyboardToggle = view.findViewById(R.id.edit_kbs_pin_keyboard_toggle);
    confirm        = view.findViewById(R.id.edit_kbs_pin_confirm);
    lottieProgress = view.findViewById(R.id.edit_kbs_pin_lottie_progress);
    lottieEnd      = view.findViewById(R.id.edit_kbs_pin_lottie_end);

    initializeViewStates();
  }

  private void initializeListeners() {
    input.addTextChangedListener(new AfterTextChanged(s -> viewModel.setUserEntry(s.toString())));
    input.setImeOptions(EditorInfo.IME_ACTION_NEXT);
    input.setOnEditorActionListener(this::handleEditorAction);
    keyboardToggle.setOnClickListener(v -> viewModel.toggleAlphaNumeric());
    confirm.setOnClickListener(v -> viewModel.confirm());
  }

  private boolean handleEditorAction(@NonNull View view, int actionId, @NonNull KeyEvent event) {
    if (actionId == EditorInfo.IME_ACTION_NEXT && confirm.isEnabled()) {
      viewModel.confirm();
    }

    return true;
  }

  private void updateKeyboard(@NonNull PinKeyboardType keyboard) {
    boolean isAlphaNumeric = keyboard == PinKeyboardType.ALPHA_NUMERIC;

    input.setInputType(isAlphaNumeric ? InputType.TYPE_CLASS_TEXT   | InputType.TYPE_TEXT_VARIATION_PASSWORD
                                      : InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
  }

  private @StringRes int resolveKeyboardToggleText(@NonNull PinKeyboardType keyboard) {
    if (keyboard == PinKeyboardType.ALPHA_NUMERIC) {
      return R.string.BaseKbsPinFragment__create_numeric_pin;
    } else {
      return R.string.BaseKbsPinFragment__create_alphanumeric_pin;
    }
  }
}
