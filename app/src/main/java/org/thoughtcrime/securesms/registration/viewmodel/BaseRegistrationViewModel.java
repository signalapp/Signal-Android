package org.thoughtcrime.securesms.registration.viewmodel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.pin.KbsRepository;
import org.thoughtcrime.securesms.pin.TokenData;
import org.thoughtcrime.securesms.registration.RequestVerificationCodeResponseProcessor;
import org.thoughtcrime.securesms.registration.VerifyAccountRepository;
import org.thoughtcrime.securesms.registration.VerifyAccountRepository.Mode;
import org.thoughtcrime.securesms.registration.VerifyResponse;
import org.thoughtcrime.securesms.registration.VerifyResponseProcessor;
import org.thoughtcrime.securesms.registration.VerifyResponseWithFailedKbs;
import org.thoughtcrime.securesms.registration.VerifyResponseWithSuccessfulKbs;
import org.thoughtcrime.securesms.registration.VerifyResponseWithoutKbs;
import org.thoughtcrime.securesms.registration.VerifyResponseWithRegistrationLockProcessor;
import org.whispersystems.signalservice.internal.ServiceResponse;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;

/**
 * Base view model used in registration and change number flow. Handles the storage of all data
 * shared between the two flows, orchestrating verification, and calling to subclasses to peform
 * the specific verify operations for each flow.
 */
public abstract class BaseRegistrationViewModel extends ViewModel {

  private static final long FIRST_CALL_AVAILABLE_AFTER_MS      = TimeUnit.SECONDS.toMillis(64);
  private static final long SUBSEQUENT_CALL_AVAILABLE_AFTER_MS = TimeUnit.SECONDS.toMillis(300);

  private static final String STATE_NUMBER                           = "NUMBER";
  private static final String STATE_REGISTRATION_SECRET              = "REGISTRATION_SECRET";
  private static final String STATE_VERIFICATION_CODE                = "TEXT_CODE_ENTERED";
  private static final String STATE_CAPTCHA                          = "CAPTCHA";
  private static final String STATE_SUCCESSFUL_CODE_REQUEST_ATTEMPTS = "SUCCESSFUL_CODE_REQUEST_ATTEMPTS";
  private static final String STATE_REQUEST_RATE_LIMITER             = "REQUEST_RATE_LIMITER";
  private static final String STATE_KBS_TOKEN                        = "KBS_TOKEN";
  private static final String STATE_TIME_REMAINING                   = "TIME_REMAINING";
  private static final String STATE_CAN_CALL_AT_TIME                 = "CAN_CALL_AT_TIME";

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
    setInitialDefaultValue(STATE_SUCCESSFUL_CODE_REQUEST_ATTEMPTS, 0);
    setInitialDefaultValue(STATE_REQUEST_RATE_LIMITER, new LocalCodeRequestRateLimiter(60_000));
  }

  protected <T> void setInitialDefaultValue(@NonNull String key, @NonNull T initialValue) {
    if (!savedState.contains(key) || savedState.get(key) == null) {
      savedState.set(key, initialValue);
    }
  }

  public @NonNull NumberViewState getNumber() {
    //noinspection ConstantConditions
    return savedState.get(STATE_NUMBER);
  }

  public @NonNull LiveData<NumberViewState> getLiveNumber() {
    return savedState.getLiveData(STATE_NUMBER);
  }

  public void onCountrySelected(@Nullable String selectedCountryName, int countryCode) {
    setViewState(getNumber().toBuilder()
                            .selectedCountryDisplayName(selectedCountryName)
                            .countryCode(countryCode).build());
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

  public void markASuccessfulAttempt() {
    //noinspection ConstantConditions
    savedState.set(STATE_SUCCESSFUL_CODE_REQUEST_ATTEMPTS, (Integer) savedState.get(STATE_SUCCESSFUL_CODE_REQUEST_ATTEMPTS) + 1);
  }

  public LiveData<Integer> getSuccessfulCodeRequestAttempts() {
    return savedState.getLiveData(STATE_SUCCESSFUL_CODE_REQUEST_ATTEMPTS, 0);
  }

  public @NonNull LocalCodeRequestRateLimiter getRequestLimiter() {
    //noinspection ConstantConditions
    return savedState.get(STATE_REQUEST_RATE_LIMITER);
  }

  public void updateLimiter() {
    savedState.set(STATE_REQUEST_RATE_LIMITER, savedState.get(STATE_REQUEST_RATE_LIMITER));
  }

  public @Nullable TokenData getKeyBackupCurrentToken() {
    return savedState.get(STATE_KBS_TOKEN);
  }

  public void setKeyBackupTokenData(@Nullable TokenData tokenData) {
    savedState.set(STATE_KBS_TOKEN, tokenData);
  }

  public LiveData<Long> getLockedTimeRemaining() {
    return savedState.getLiveData(STATE_TIME_REMAINING, 0L);
  }

  public LiveData<Long> getCanCallAtTime() {
    return savedState.getLiveData(STATE_CAN_CALL_AT_TIME, 0L);
  }

  public void setLockedTimeRemaining(long lockedTimeRemaining) {
    savedState.set(STATE_TIME_REMAINING, lockedTimeRemaining);
  }

  public void onStartEnterCode() {
    savedState.set(STATE_CAN_CALL_AT_TIME, System.currentTimeMillis() + FIRST_CALL_AVAILABLE_AFTER_MS);
  }

  public void onCallRequested() {
    savedState.set(STATE_CAN_CALL_AT_TIME, System.currentTimeMillis() + SUBSEQUENT_CALL_AVAILABLE_AFTER_MS);
  }

  public Single<RequestVerificationCodeResponseProcessor> requestVerificationCode(@NonNull Mode mode) {
    String captcha = getCaptchaToken();
    clearCaptchaResponse();

    if (mode == Mode.PHONE_CALL) {
      onCallRequested();
    } else if (!getRequestLimiter().canRequest(mode, getNumber().getE164Number(), System.currentTimeMillis())) {
      return Single.just(RequestVerificationCodeResponseProcessor.forLocalRateLimit());
    }

    return verifyAccountRepository.requestVerificationCode(getNumber().getE164Number(),
                                                           getRegistrationSecret(),
                                                           mode,
                                                           captcha)
                                  .map(RequestVerificationCodeResponseProcessor::new)
                                  .observeOn(AndroidSchedulers.mainThread())
                                  .doOnSuccess(processor -> {
                                    if (processor.hasResult()) {
                                      markASuccessfulAttempt();
                                      getRequestLimiter().onSuccessfulRequest(mode, getNumber().getE164Number(), System.currentTimeMillis());
                                    } else {
                                      getRequestLimiter().onUnsuccessfulRequest();
                                    }
                                    updateLimiter();
                                  });
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
