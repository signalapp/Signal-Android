package org.thoughtcrime.securesms.registration.viewmodel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.pin.KbsRepository;
import org.thoughtcrime.securesms.pin.TokenData;
import org.thoughtcrime.securesms.registration.RegistrationSessionProcessor;
import org.thoughtcrime.securesms.registration.VerifyAccountRepository;
import org.thoughtcrime.securesms.registration.VerifyAccountRepository.Mode;
import org.thoughtcrime.securesms.registration.VerifyResponse;
import org.thoughtcrime.securesms.registration.VerifyResponseProcessor;
import org.thoughtcrime.securesms.registration.VerifyResponseWithFailedKbs;
import org.thoughtcrime.securesms.registration.VerifyResponseWithRegistrationLockProcessor;
import org.thoughtcrime.securesms.registration.VerifyResponseWithSuccessfulKbs;
import org.thoughtcrime.securesms.registration.VerifyResponseWithoutKbs;
import org.whispersystems.signalservice.internal.ServiceResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;

/**
 * Base view model used in registration and change number flow. Handles the storage of all data
 * shared between the two flows, orchestrating verification, and calling to subclasses to peform
 * the specific verify operations for each flow.
 */
public abstract class BaseRegistrationViewModel extends ViewModel {

  private static final String TAG = Log.tag(BaseRegistrationViewModel.class);

  private static final String STATE_NUMBER                  = "NUMBER";
  private static final String STATE_REGISTRATION_SECRET     = "REGISTRATION_SECRET";
  private static final String STATE_VERIFICATION_CODE       = "TEXT_CODE_ENTERED";
  private static final String STATE_CAPTCHA                 = "CAPTCHA";
  private static final String STATE_PUSH_TIMED_OUT          = "PUSH_TIMED_OUT";
  private static final String STATE_INCORRECT_CODE_ATTEMPTS = "STATE_INCORRECT_CODE_ATTEMPTS";
  private static final String STATE_REQUEST_RATE_LIMITER    = "REQUEST_RATE_LIMITER";
  private static final String STATE_KBS_TOKEN               = "KBS_TOKEN";
  private static final String STATE_TIME_REMAINING          = "TIME_REMAINING";
  private static final String STATE_CAN_CALL_AT_TIME        = "CAN_CALL_AT_TIME";
  private static final String STATE_CAN_SMS_AT_TIME         = "CAN_SMS_AT_TIME";
  private static final String STATE_RECOVERY_PASSWORD       = "RECOVERY_PASSWORD";

  protected final SavedStateHandle        savedState;
  protected final VerifyAccountRepository verifyAccountRepository;
  protected final KbsRepository           kbsRepository;

  public BaseRegistrationViewModel(@NonNull SavedStateHandle savedStateHandle,
                                   @NonNull VerifyAccountRepository verifyAccountRepository,
                                   @NonNull KbsRepository kbsRepository,
                                   @NonNull String password)
  {
    this.savedState = savedStateHandle;

    this.verifyAccountRepository = verifyAccountRepository;
    this.kbsRepository           = kbsRepository;

    setInitialDefaultValue(STATE_NUMBER, NumberViewState.INITIAL);
    setInitialDefaultValue(STATE_REGISTRATION_SECRET, password);
    setInitialDefaultValue(STATE_VERIFICATION_CODE, "");
    setInitialDefaultValue(STATE_INCORRECT_CODE_ATTEMPTS, 0);
    setInitialDefaultValue(STATE_REQUEST_RATE_LIMITER, new LocalCodeRequestRateLimiter(60_000));
    setInitialDefaultValue(STATE_RECOVERY_PASSWORD, SignalStore.kbsValues().getRecoveryPassword());
    setInitialDefaultValue(STATE_PUSH_TIMED_OUT, false);
  }

  protected <T> void setInitialDefaultValue(@NonNull String key, @Nullable T initialValue) {
    if (!savedState.contains(key) || savedState.get(key) == null) {
      savedState.set(key, initialValue);
    }
  }

  public @Nullable String getSessionId() {
    return SignalStore.registrationValues().getSessionId();
  }

  public void setSessionId(String sessionId) {
    SignalStore.registrationValues().setSessionId(sessionId);
  }

  public @Nullable String getSessionE164() {
    return SignalStore.registrationValues().getSessionE164();
  }

  public void setSessionE164(String sessionE164) {
    SignalStore.registrationValues().setSessionE164(sessionE164);
  }

  public void resetSession() {
    setSessionE164(null);
    setSessionId(null);
  }

  public @NonNull NumberViewState getNumber() {
    //noinspection ConstantConditions
    return savedState.get(STATE_NUMBER);
  }

  public @NonNull LiveData<NumberViewState> getLiveNumber() {
    return savedState.getLiveData(STATE_NUMBER);
  }

  public void restorePhoneNumberStateFromE164(String e164) throws NumberParseException {
    Phonenumber.PhoneNumber phoneNumber = PhoneNumberUtil.getInstance().parse(e164, null);
    onCountrySelected(null, phoneNumber.getCountryCode());
    setNationalNumber(String.valueOf(phoneNumber.getNationalNumber()));
  }

  public void onCountrySelected(@Nullable String selectedCountryName, int countryCode) {
    setViewState(getNumber().toBuilder()
                            .selectedCountryDisplayName(selectedCountryName)
                            .countryCode(countryCode)
                            .build());
  }

  public void setNationalNumber(String number) {
    NumberViewState numberViewState = getNumber().toBuilder().nationalNumber(number).build();
    setViewState(numberViewState);
  }

  protected void setViewState(NumberViewState numberViewState) {
    if (!numberViewState.equals(getNumber())) {
      savedState.set(STATE_NUMBER, numberViewState);
    }
  }

  public @NonNull String getRegistrationSecret() {
    //noinspection ConstantConditions
    return savedState.get(STATE_REGISTRATION_SECRET);
  }

  public @NonNull String getTextCodeEntered() {
    //noinspection ConstantConditions
    return savedState.get(STATE_VERIFICATION_CODE);
  }

  public @Nullable String getCaptchaToken() {
    return savedState.get(STATE_CAPTCHA);
  }

  public boolean hasCaptchaToken() {
    return getCaptchaToken() != null;
  }

  public void setCaptchaResponse(@Nullable String captchaToken) {
    savedState.set(STATE_CAPTCHA, captchaToken);
  }

  public void clearCaptchaResponse() {
    setCaptchaResponse(null);
  }

  public void onVerificationCodeEntered(String code) {
    savedState.set(STATE_VERIFICATION_CODE, code);
  }

  public void incrementIncorrectCodeAttempts() {
    //noinspection ConstantConditions
    savedState.set(STATE_INCORRECT_CODE_ATTEMPTS, (Integer) savedState.get(STATE_INCORRECT_CODE_ATTEMPTS) + 1);
  }

  public LiveData<Integer> getIncorrectCodeAttempts() {
    return savedState.getLiveData(STATE_INCORRECT_CODE_ATTEMPTS, 0);
  }

  public void markPushChallengeTimedOut() {
    savedState.set(STATE_PUSH_TIMED_OUT, true);
  }

  public List<String> getExcludedChallenges() {
    ArrayList<String> challengeKeys = new ArrayList<>();
    if (Boolean.TRUE.equals(savedState.get(STATE_PUSH_TIMED_OUT))) {
      challengeKeys.add(RegistrationSessionProcessor.PUSH_CHALLENGE_KEY);
    }
    return challengeKeys;
  }

  public @Nullable TokenData getKeyBackupCurrentToken() {
    return savedState.get(STATE_KBS_TOKEN);
  }

  public void setKeyBackupTokenData(@Nullable TokenData tokenData) {
    savedState.set(STATE_KBS_TOKEN, tokenData);
  }

  public void setRecoveryPassword(@Nullable String recoveryPassword) {
    savedState.set(STATE_RECOVERY_PASSWORD, recoveryPassword);
  }

  public @Nullable String getRecoveryPassword() {
    return savedState.get(STATE_RECOVERY_PASSWORD);
  }

  public LiveData<Long> getLockedTimeRemaining() {
    return savedState.getLiveData(STATE_TIME_REMAINING, 0L);
  }

  public LiveData<Long> getCanCallAtTime() {
    return savedState.getLiveData(STATE_CAN_CALL_AT_TIME, 0L);
  }

  public LiveData<Long> getCanSmsAtTime() {
    return savedState.getLiveData(STATE_CAN_SMS_AT_TIME, 0L);
  }

  public void setLockedTimeRemaining(long lockedTimeRemaining) {
    savedState.set(STATE_TIME_REMAINING, lockedTimeRemaining);
  }

  public void setCanCallAtTime(long callingTimestamp) {
    savedState.getLiveData(STATE_CAN_CALL_AT_TIME).postValue(callingTimestamp);
  }

  public void setCanSmsAtTime(long smsTimestamp) {
    savedState.getLiveData(STATE_CAN_SMS_AT_TIME).postValue(smsTimestamp);
  }

  public Single<RegistrationSessionProcessor> requestVerificationCode(@NonNull Mode mode, @Nullable String mcc, @Nullable String mnc) {

    final String e164 = getNumber().getE164Number();

    return getValidSession(e164, mcc, mnc)
        .flatMap(processor -> {
          if (!processor.hasResult()) {
            return Single.just(processor);
          }

          String sessionId = processor.getSessionId();
          setSessionId(sessionId);
          setSessionE164(e164);

          return handleRequiredChallenges(processor, e164);
        })
        .flatMap(processor -> {
          if (!processor.hasResult()) {
            return Single.just(processor);
          }

          if (!processor.isAllowedToRequestCode()) {
            return Single.just(processor);
          }

          String sessionId = processor.getSessionId();
          clearCaptchaResponse();
          return verifyAccountRepository.requestVerificationCode(sessionId,
                                                                 getNumber().getE164Number(),
                                                                 getRegistrationSecret(),
                                                                 mode)
                                        .map(RegistrationSessionProcessor.RegistrationSessionProcessorForVerification::new);
        })
        .observeOn(AndroidSchedulers.mainThread())
        .doOnSuccess((RegistrationSessionProcessor processor) -> {
          if (processor.hasResult() && processor.isAllowedToRequestCode()) {
            setCanSmsAtTime(processor.getNextCodeViaSmsAttempt());
            setCanCallAtTime(processor.getNextCodeViaCallAttempt());
          }
        });
  }

  public Single<RegistrationSessionProcessor.RegistrationSessionProcessorForSession> validateSession(String e164) {
    String storedSessionId = null;
    if (e164.equals(getSessionE164())) {
      storedSessionId = getSessionId();
    }
    return verifyAccountRepository.validateSession(storedSessionId, e164, getRegistrationSecret())
                                  .map(RegistrationSessionProcessor.RegistrationSessionProcessorForSession::new);
  }

  public Single<RegistrationSessionProcessor.RegistrationSessionProcessorForSession> getValidSession(String e164, @Nullable String mcc, @Nullable String mnc) {
    return validateSession(e164)
        .flatMap(processor -> {
          if (processor.isInvalidSession()) {
            return verifyAccountRepository.requestValidSession(e164, getRegistrationSecret(), mcc, mnc)
                                          .map(RegistrationSessionProcessor.RegistrationSessionProcessorForSession::new)
                                          .doOnSuccess(createSessionProcessor -> {
                                            if (createSessionProcessor.pushChallengeTimedOut()) {
                                              markPushChallengeTimedOut();
                                            }
                                          });
          } else {
            return Single.just(processor);
          }
        });
  }

  public Single<RegistrationSessionProcessor> handleRequiredChallenges(RegistrationSessionProcessor processor, String e164) {
    final String sessionId = processor.getSessionId();

    if (processor.isAllowedToRequestCode()) {
      return Single.just(processor);
    }

    if (hasCaptchaToken() && processor.captchaRequired(getExcludedChallenges())) {
      Log.d(TAG, "Submitting completed captcha challenge");
      final String captcha = Objects.requireNonNull(getCaptchaToken());
      clearCaptchaResponse();
      return verifyAccountRepository.verifyCaptcha(sessionId, captcha, e164, getRegistrationSecret())
                                    .map(RegistrationSessionProcessor.RegistrationSessionProcessorForSession::new);
    } else {
      String challenge = processor.getChallenge(getExcludedChallenges());
      Log.d(TAG, "Handling challenge of type " + challenge);
      if (challenge != null) {
        switch (challenge) {
          case RegistrationSessionProcessor.PUSH_CHALLENGE_KEY:
            return verifyAccountRepository.requestAndVerifyPushToken(sessionId,
                                                                     getNumber().getE164Number(),
                                                                     getRegistrationSecret())
                                          .map(RegistrationSessionProcessor.RegistrationSessionProcessorForSession::new);

          case RegistrationSessionProcessor.CAPTCHA_KEY:
            // fall through to passing the processor back so that the eventual subscriber will check captchaRequired() and handle accordingly
          default:
            break;
        }
      }
    }

    return Single.just(processor);
  }

  public Single<VerifyResponseProcessor> verifyCodeWithoutRegistrationLock(@NonNull String code) {
    onVerificationCodeEntered(code);

    return verifyAccountWithoutRegistrationLock()
        .flatMap(response -> {
          if (response.getResult().isPresent() && response.getResult().get().getKbsData() != null) {
            return onVerifySuccessWithRegistrationLock(new VerifyResponseWithRegistrationLockProcessor(response, null), response.getResult().get().getPin());
          }

          VerifyResponseProcessor processor = new VerifyResponseWithoutKbs(response);
          if (processor.hasResult()) {
            return onVerifySuccess(processor);
          } else if (processor.registrationLock() && !processor.isKbsLocked()) {
            return kbsRepository.getToken(processor.getLockedException().getBasicStorageCredentials())
                                .map(r -> r.getResult().isPresent() ? new VerifyResponseWithSuccessfulKbs(processor.getResponse(), r.getResult().get())
                                                                    : new VerifyResponseWithFailedKbs(r));
          }
          return Single.just(processor);
        })
        .observeOn(AndroidSchedulers.mainThread())
        .doOnSuccess(processor -> {
          if (processor.registrationLock() && !processor.isKbsLocked()) {
            setLockedTimeRemaining(processor.getLockedException().getTimeRemaining());
            setKeyBackupTokenData(processor.getTokenData());
          } else if (processor.isKbsLocked()) {
            setLockedTimeRemaining(processor.getLockedException().getTimeRemaining());
          }
        });
  }

  public Single<VerifyResponseWithRegistrationLockProcessor> verifyCodeAndRegisterAccountWithRegistrationLock(@NonNull String pin) {
    TokenData kbsTokenData = Objects.requireNonNull(getKeyBackupCurrentToken());

    return verifyAccountWithRegistrationLock(pin, kbsTokenData)
        .map(r -> new VerifyResponseWithRegistrationLockProcessor(r, kbsTokenData))
        .flatMap(processor -> {
          if (processor.hasResult()) {
            return onVerifySuccessWithRegistrationLock(processor, pin);
          } else if (processor.wrongPin()) {
            TokenData newToken = TokenData.withResponse(kbsTokenData, processor.getTokenResponse());
            return Single.just(new VerifyResponseWithRegistrationLockProcessor(processor.getResponse(), newToken));
          }
          return Single.just(processor);
        })
        .observeOn(AndroidSchedulers.mainThread())
        .doOnSuccess(processor -> {
          if (processor.wrongPin()) {
            setKeyBackupTokenData(processor.getTokenData());
          }
        });
  }

  protected abstract Single<ServiceResponse<VerifyResponse>> verifyAccountWithoutRegistrationLock();

  protected abstract Single<ServiceResponse<VerifyResponse>> verifyAccountWithRegistrationLock(@NonNull String pin, @NonNull TokenData kbsTokenData);

  protected abstract Single<VerifyResponseProcessor> onVerifySuccess(@NonNull VerifyResponseProcessor processor);

  protected abstract Single<VerifyResponseWithRegistrationLockProcessor> onVerifySuccessWithRegistrationLock(@NonNull VerifyResponseWithRegistrationLockProcessor processor, String pin);

}
