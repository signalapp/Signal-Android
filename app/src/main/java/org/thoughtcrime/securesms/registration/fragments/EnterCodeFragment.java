package org.thoughtcrime.securesms.registration.fragments;

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
import androidx.appcompat.app.AlertDialog;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.registration.CallMeCountDownView;
import org.thoughtcrime.securesms.components.registration.VerificationCodeView;
import org.thoughtcrime.securesms.components.registration.VerificationPinKeyboard;
import org.thoughtcrime.securesms.pin.PinRestoreRepository;
import org.thoughtcrime.securesms.registration.ReceivedSmsEvent;
import org.thoughtcrime.securesms.registration.service.CodeVerificationRequest;
import org.thoughtcrime.securesms.registration.service.RegistrationCodeRequest;
import org.thoughtcrime.securesms.registration.service.RegistrationService;
import org.thoughtcrime.securesms.registration.viewmodel.RegistrationViewModel;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.thoughtcrime.securesms.util.SupportEmailUtil;
import org.thoughtcrime.securesms.util.concurrent.AssertedSuccessListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class EnterCodeFragment extends BaseRegistrationFragment
                                     implements SignalStrengthPhoneStateListener.Callback
{

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

  private PhoneStateListener signalStrengthListener;

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
    hideKeyboard(requireContext(), view);

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

    RegistrationViewModel model = getModel();
    model.getSuccessfulCodeRequestAttempts().observe(this, (attempts) -> {
      if (attempts >= 3) {
        noCodeReceivedHelp.setVisibility(View.VISIBLE);
        scrollView.postDelayed(() -> scrollView.smoothScrollTo(0, noCodeReceivedHelp.getBottom()), 15000);
      }
    });

    model.onStartEnterCode();
  }

  private void onWrongNumber() {
    Navigation.findNavController(requireView())
              .navigate(EnterCodeFragmentDirections.actionWrongNumber());
  }

  private void setOnCodeFullyEnteredListener(VerificationCodeView verificationCodeView) {
    verificationCodeView.setOnCompleteListener(code -> {
      RegistrationViewModel model = getModel();

      model.onVerificationCodeEntered(code);
      callMeCountDown.setVisibility(View.INVISIBLE);
      wrongNumber.setVisibility(View.INVISIBLE);
      keyboard.displayProgress();

      RegistrationService registrationService = RegistrationService.getInstance(model.getNumber().getE164Number(), model.getRegistrationSecret());

      registrationService.verifyAccount(requireActivity(), model.getFcmToken(), code, null, null,
        new CodeVerificationRequest.VerifyCallback() {

          @Override
          public void onSuccessfulRegistration() {
            keyboard.displaySuccess().addListener(new AssertedSuccessListener<Boolean>() {
              @Override
              public void onSuccess(Boolean result) {
                handleSuccessfulRegistration();
              }
            });
          }

          @Override
          public void onV1RegistrationLockPinRequiredOrIncorrect(long timeRemaining) {
            model.setLockedTimeRemaining(timeRemaining);
            keyboard.displayLocked().addListener(new AssertedSuccessListener<Boolean>() {
              @Override
              public void onSuccess(Boolean r) {
                Navigation.findNavController(requireView())
                          .navigate(EnterCodeFragmentDirections.actionRequireKbsLockPin(timeRemaining, true));
              }
            });
          }

          @Override
          public void onKbsRegistrationLockPinRequired(long timeRemaining, @NonNull PinRestoreRepository.TokenData tokenData, @NonNull String kbsStorageCredentials) {
            model.setLockedTimeRemaining(timeRemaining);
            model.setKeyBackupTokenData(tokenData);
            keyboard.displayLocked().addListener(new AssertedSuccessListener<Boolean>() {
              @Override
              public void onSuccess(Boolean r) {
                Navigation.findNavController(requireView())
                          .navigate(EnterCodeFragmentDirections.actionRequireKbsLockPin(timeRemaining, false));
              }
            });
          }

          @Override
          public void onIncorrectKbsRegistrationLockPin(@NonNull PinRestoreRepository.TokenData tokenData) {
            throw new AssertionError("Unexpected, user has made no pin guesses");
          }

          @Override
          public void onRateLimited() {
            keyboard.displayFailure().addListener(new AssertedSuccessListener<Boolean>() {
              @Override
              public void onSuccess(Boolean r) {
                new AlertDialog.Builder(requireContext())
                               .setTitle(R.string.RegistrationActivity_too_many_attempts)
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

          @Override
          public void onKbsAccountLocked(@Nullable Long timeRemaining) {
            if (timeRemaining != null) {
              model.setLockedTimeRemaining(timeRemaining);
            }
            Navigation.findNavController(requireView()).navigate(RegistrationLockFragmentDirections.actionAccountLocked());
          }

          @Override
          public void onError() {
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
        });
    });
  }

  private void handleSuccessfulRegistration() {
    Navigation.findNavController(requireView()).navigate(EnterCodeFragmentDirections.actionSuccessfulRegistration());
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
                                        getModel().getNumber().getE164Number(),
                                        this::handlePhoneCallRequestAfterConfirm,
                                        this::onWrongNumber);
  }

  private void handlePhoneCallRequestAfterConfirm() {
    RegistrationViewModel model   = getModel();
    String                captcha = model.getCaptchaToken();
    model.clearCaptchaResponse();

    model.onCallRequested();

    NavController navController = Navigation.findNavController(callMeCountDown);

    RegistrationService registrationService = RegistrationService.getInstance(model.getNumber().getE164Number(), model.getRegistrationSecret());

    registrationService.requestVerificationCode(requireActivity(), RegistrationCodeRequest.Mode.PHONE_CALL, captcha,
      new RegistrationCodeRequest.SmsVerificationCodeCallback() {

        @Override
        public void onNeedCaptcha() {
          navController.navigate(EnterCodeFragmentDirections.actionRequestCaptcha());
        }

        @Override
        public void requestSent(@Nullable String fcmToken) {
          model.setFcmToken(fcmToken);
          model.markASuccessfulAttempt();
        }

        @Override
        public void onRateLimited() {
          Toast.makeText(requireContext(), R.string.RegistrationActivity_rate_limited_to_service, Toast.LENGTH_LONG).show();
        }

        @Override
        public void onError() {
          Toast.makeText(requireContext(), R.string.RegistrationActivity_unable_to_connect_to_service, Toast.LENGTH_LONG).show();
        }
      });
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

    RegistrationViewModel model = getModel();
    model.getLiveNumber().observe(this, (s) -> header.setText(requireContext().getString(R.string.RegistrationActivity_enter_the_code_we_sent_to_s, s.getFullFormattedNumber())));

    model.getCanCallAtTime().observe(this, callAtTime -> callMeCountDown.startCountDownTo(callAtTime));
  }

  private void sendEmailToSupport() {
    String body = SupportEmailUtil.generateSupportEmailBody(requireContext(),
                                                            getString(R.string.RegistrationActivity_code_support_subject),
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
