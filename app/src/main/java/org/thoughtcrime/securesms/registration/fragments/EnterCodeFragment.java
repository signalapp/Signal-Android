package org.thoughtcrime.securesms.registration.fragments;

import static org.thoughtcrime.securesms.registration.fragments.RegistrationViewDelegate.setDebugLogSubmitMultiTapView;
import static org.thoughtcrime.securesms.registration.fragments.RegistrationViewDelegate.showConfirmNumberDialogIfTranslated;

import android.animation.Animator;
import android.os.Bundle;
import android.telephony.PhoneStateListener;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProviders;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.registration.CallMeCountDownView;
import org.thoughtcrime.securesms.components.registration.VerificationCodeView;
import org.thoughtcrime.securesms.components.registration.VerificationPinKeyboard;
import org.thoughtcrime.securesms.registration.ReceivedSmsEvent;
import org.thoughtcrime.securesms.registration.VerifyAccountRepository;
import org.thoughtcrime.securesms.registration.viewmodel.RegistrationViewModel;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.LifecycleDisposable;
import org.thoughtcrime.securesms.util.SupportEmailUtil;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.concurrent.AssertedSuccessListener;
import org.thoughtcrime.securesms.util.concurrent.SimpleTask;
import org.whispersystems.signalservice.internal.push.LockedException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;

public final class EnterCodeFragment extends LoggingFragment implements SignalStrengthPhoneStateListener.Callback {

  private static final String TAG = Log.tag(EnterCodeFragment.class);

  private ScrollView              scrollView;
  private TextView                header;
  private VerificationCodeView    verificationCodeView;
  private VerificationPinKeyboard keyboard;
  private CallMeCountDownView     callMeCountDown;
  private View                    wrongNumber;
  private View                    noCodeReceivedHelp;
  private View                    serviceWarning;
  private boolean                 autoCompleting;

  private PhoneStateListener    signalStrengthListener;
  private RegistrationViewModel viewModel;

  private final LifecycleDisposable disposables = new LifecycleDisposable();

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_registration_enter_code, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    setDebugLogSubmitMultiTapView(view.findViewById(R.id.verify_header));

    scrollView           = view.findViewById(R.id.scroll_view);
    header               = view.findViewById(R.id.verify_header);
    verificationCodeView = view.findViewById(R.id.code);
    keyboard             = view.findViewById(R.id.keyboard);
    callMeCountDown      = view.findViewById(R.id.call_me_count_down);
    wrongNumber          = view.findViewById(R.id.wrong_number);
    noCodeReceivedHelp   = view.findViewById(R.id.no_code);
    serviceWarning       = view.findViewById(R.id.cell_service_warning);

    signalStrengthListener = new SignalStrengthPhoneStateListener(this, this);

    connectKeyboard(verificationCodeView, keyboard);
    ViewUtil.hideKeyboard(requireContext(), view);

    setOnCodeFullyEnteredListener(verificationCodeView);

    wrongNumber.setOnClickListener(v -> onWrongNumber());

    callMeCountDown.setOnClickListener(v -> handlePhoneCallRequest());

    callMeCountDown.setListener((v, remaining) -> {
      if (remaining <= 30) {
        scrollView.smoothScrollTo(0, v.getBottom());
        callMeCountDown.setListener(null);
      }
    });

    noCodeReceivedHelp.setOnClickListener(v -> sendEmailToSupport());

    disposables.bindTo(getViewLifecycleOwner().getLifecycle());
    viewModel = ViewModelProviders.of(requireActivity()).get(RegistrationViewModel.class);
    viewModel.getSuccessfulCodeRequestAttempts().observe(getViewLifecycleOwner(), (attempts) -> {
      if (attempts >= 3) {
        noCodeReceivedHelp.setVisibility(View.VISIBLE);
        scrollView.postDelayed(() -> scrollView.smoothScrollTo(0, noCodeReceivedHelp.getBottom()), 15000);
      }
    });

    viewModel.onStartEnterCode();
  }

  private void onWrongNumber() {
    Navigation.findNavController(requireView())
              .navigate(EnterCodeFragmentDirections.actionWrongNumber());
  }

  private void setOnCodeFullyEnteredListener(VerificationCodeView verificationCodeView) {
    verificationCodeView.setOnCompleteListener(code -> {

      callMeCountDown.setVisibility(View.INVISIBLE);
      wrongNumber.setVisibility(View.INVISIBLE);
      keyboard.displayProgress();

      Disposable verify = viewModel.verifyCodeAndRegisterAccountWithoutRegistrationLock(code)
                                   .observeOn(AndroidSchedulers.mainThread())
                                   .subscribe(processor -> {
                                     if (processor.hasResult()) {
                                       handleSuccessfulRegistration();
                                     } else if (processor.rateLimit()) {
                                       handleRateLimited();
                                     } else if (processor.registrationLock() && !processor.isKbsLocked()) {
                                       LockedException lockedException = processor.getLockedException();
                                       handleRegistrationLock(lockedException.getTimeRemaining());
                                     } else if (processor.isKbsLocked()) {
                                       handleKbsAccountLocked();
                                     } else if (processor.authorizationFailed()) {
                                       handleIncorrectCodeError();
                                     } else {
                                       Log.w(TAG, "Unable to verify code", processor.getError());
                                       handleGeneralError();
                                     }
                                   });

      disposables.add(verify);
    });
  }

  public void handleSuccessfulRegistration() {
    SimpleTask.run(() -> {
      long startTime = System.currentTimeMillis();
      try {
        FeatureFlags.refreshSync();
        Log.i(TAG, "Took " + (System.currentTimeMillis() - startTime) + " ms to get feature flags.");
      } catch (IOException e) {
        Log.w(TAG, "Failed to refresh flags after " + (System.currentTimeMillis() - startTime) + " ms.", e);
      }
      return null;
    }, none -> {
      keyboard.displaySuccess().addListener(new AssertedSuccessListener<Boolean>() {
        @Override
        public void onSuccess(Boolean result) {
          Navigation.findNavController(requireView()).navigate(EnterCodeFragmentDirections.actionSuccessfulRegistration());
        }
      });
    });
  }

  public void handleRateLimited() {
    keyboard.displayFailure().addListener(new AssertedSuccessListener<Boolean>() {
      @Override
      public void onSuccess(Boolean r) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());

        builder.setTitle(R.string.RegistrationActivity_too_many_attempts)
               .setMessage(R.string.RegistrationActivity_you_have_made_too_many_attempts_please_try_again_later)
               .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                 callMeCountDown.setVisibility(View.VISIBLE);
                 wrongNumber.setVisibility(View.VISIBLE);
                 verificationCodeView.clear();
                 keyboard.displayKeyboard();
               })
               .show();
      }
    });
  }

  public void handleRegistrationLock(long timeRemaining) {
    keyboard.displayLocked().addListener(new AssertedSuccessListener<Boolean>() {
      @Override
      public void onSuccess(Boolean r) {
        Navigation.findNavController(requireView())
                  .navigate(EnterCodeFragmentDirections.actionRequireKbsLockPin(timeRemaining, false));
      }
    });
  }

  public void handleKbsAccountLocked() {
    Navigation.findNavController(requireView()).navigate(RegistrationLockFragmentDirections.actionAccountLocked());
  }

  public void handleIncorrectCodeError() {
    Toast.makeText(requireContext(), R.string.RegistrationActivity_incorrect_code, Toast.LENGTH_LONG).show();
    keyboard.displayFailure().addListener(new AssertedSuccessListener<Boolean>() {
      @Override
      public void onSuccess(Boolean result) {
        callMeCountDown.setVisibility(View.VISIBLE);
        wrongNumber.setVisibility(View.VISIBLE);
        verificationCodeView.clear();
        keyboard.displayKeyboard();
      }
    });
  }

  public void handleGeneralError() {
    Toast.makeText(requireContext(), R.string.RegistrationActivity_error_connecting_to_service, Toast.LENGTH_LONG).show();
    keyboard.displayFailure().addListener(new AssertedSuccessListener<Boolean>() {
      @Override
      public void onSuccess(Boolean result) {
        callMeCountDown.setVisibility(View.VISIBLE);
        wrongNumber.setVisibility(View.VISIBLE);
        verificationCodeView.clear();
        keyboard.displayKeyboard();
      }
    });
  }

  @Override
  public void onStart() {
    super.onStart();
    EventBus.getDefault().register(this);
  }

  @Override
  public void onStop() {
    super.onStop();
    EventBus.getDefault().unregister(this);
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onVerificationCodeReceived(@NonNull ReceivedSmsEvent event) {
    verificationCodeView.clear();

    List<Integer> parsedCode = convertVerificationCodeToDigits(event.getCode());

    autoCompleting = true;

    final int size = parsedCode.size();

    for (int i = 0; i < size; i++) {
      final int index = i;
      verificationCodeView.postDelayed(() -> {
        verificationCodeView.append(parsedCode.get(index));
        if (index == size - 1) {
          autoCompleting = false;
        }
      }, i * 200);
    }
  }

  private static List<Integer> convertVerificationCodeToDigits(@Nullable String code) {
    if (code == null || code.length() != 6) {
      return Collections.emptyList();
    }

    List<Integer> result = new ArrayList<>(code.length());

    try {
      for (int i = 0; i < code.length(); i++) {
        result.add(Integer.parseInt(Character.toString(code.charAt(i))));
      }
    } catch (NumberFormatException e) {
      Log.w(TAG, "Failed to convert code into digits.", e);
      return Collections.emptyList();
    }

    return result;
  }

  private void handlePhoneCallRequest() {
    showConfirmNumberDialogIfTranslated(requireContext(),
                                        R.string.RegistrationActivity_you_will_receive_a_call_to_verify_this_number,
                                        viewModel.getNumber().getE164Number(),
                                        this::handlePhoneCallRequestAfterConfirm,
                                        this::onWrongNumber);
  }

  private void handlePhoneCallRequestAfterConfirm() {
    Disposable request = viewModel.requestVerificationCode(VerifyAccountRepository.Mode.PHONE_CALL)
                                  .observeOn(AndroidSchedulers.mainThread())
                                  .subscribe(processor -> {
                                    if (processor.hasResult()) {
                                      Toast.makeText(requireContext(), R.string.RegistrationActivity_call_requested, Toast.LENGTH_LONG).show();
                                    } else if (processor.captchaRequired()) {
                                      NavHostFragment.findNavController(this).navigate(EnterCodeFragmentDirections.actionRequestCaptcha());
                                    } else if (processor.rateLimit()) {
                                      Toast.makeText(requireContext(), R.string.RegistrationActivity_rate_limited_to_service, Toast.LENGTH_LONG).show();
                                    } else {
                                      Log.w(TAG, "Unable to request phone code", processor.getError());
                                      Toast.makeText(requireContext(), R.string.RegistrationActivity_unable_to_connect_to_service, Toast.LENGTH_LONG).show();
                                    }
                                  });

    disposables.add(request);
  }

  private void connectKeyboard(VerificationCodeView verificationCodeView, VerificationPinKeyboard keyboard) {
    keyboard.setOnKeyPressListener(key -> {
      if (!autoCompleting) {
        if (key >= 0) {
          verificationCodeView.append(key);
        } else {
          verificationCodeView.delete();
        }
      }
    });
  }

  @Override
  public void onResume() {
    super.onResume();

    header.setText(requireContext().getString(R.string.RegistrationActivity_enter_the_code_we_sent_to_s, viewModel.getNumber().getFullFormattedNumber()));

    viewModel.getCanCallAtTime().observe(getViewLifecycleOwner(), callAtTime -> callMeCountDown.startCountDownTo(callAtTime));
  }

  private void sendEmailToSupport() {
    String body = SupportEmailUtil.generateSupportEmailBody(requireContext(),
                                                            R.string.RegistrationActivity_code_support_subject,
                                                            null,
                                                            null);
    CommunicationActions.openEmail(requireContext(),
                                   SupportEmailUtil.getSupportEmailAddress(requireContext()),
                                   getString(R.string.RegistrationActivity_code_support_subject),
                                   body);
  }

  @Override
  public void onNoCellSignalPresent() {
    if (serviceWarning.getVisibility() == View.VISIBLE) {
      return;
    }
    serviceWarning.setVisibility(View.VISIBLE);
    serviceWarning.animate()
                  .alpha(1)
                  .setListener(null)
                  .start();

    scrollView.postDelayed(() -> {
      if (serviceWarning.getVisibility() == View.VISIBLE) {
        scrollView.smoothScrollTo(0, serviceWarning.getBottom());
      }
    }, 1000);
  }

  @Override
  public void onCellSignalPresent() {
    if (serviceWarning.getVisibility() != View.VISIBLE) {
      return;
    }
    serviceWarning.animate()
                  .alpha(0)
                  .setListener(new Animator.AnimatorListener() {
                    @Override public void onAnimationEnd(Animator animation) {
                      serviceWarning.setVisibility(View.GONE);
                    }

                    @Override public void onAnimationStart(Animator animation) {}

                    @Override public void onAnimationCancel(Animator animation) {}

                    @Override public void onAnimationRepeat(Animator animation) {}
                  })
                  .start();
  }
}
