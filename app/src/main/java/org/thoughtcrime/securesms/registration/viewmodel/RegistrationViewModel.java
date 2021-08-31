package org.thoughtcrime.securesms.registration.viewmodel;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AbstractSavedStateViewModelFactory;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import androidx.savedstate.SavedStateRegistryOwner;

import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.pin.KbsRepository;
import org.thoughtcrime.securesms.pin.TokenData;
import org.thoughtcrime.securesms.registration.RegistrationData;
import org.thoughtcrime.securesms.registration.RegistrationRepository;
import org.thoughtcrime.securesms.registration.RequestVerificationCodeResponseProcessor;
import org.thoughtcrime.securesms.registration.VerifyAccountRepository;
import org.thoughtcrime.securesms.registration.VerifyAccountRepository.Mode;
import org.thoughtcrime.securesms.registration.VerifyAccountResponseProcessor;
import org.thoughtcrime.securesms.registration.VerifyAccountResponseWithFailedKbs;
import org.thoughtcrime.securesms.registration.VerifyAccountResponseWithSuccessfulKbs;
import org.thoughtcrime.securesms.registration.VerifyAccountResponseWithoutKbs;
import org.thoughtcrime.securesms.registration.VerifyCodeWithRegistrationLockResponseProcessor;
import org.thoughtcrime.securesms.util.Util;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;

public final class RegistrationViewModel extends ViewModel {

  private static final long FIRST_CALL_AVAILABLE_AFTER_MS      = TimeUnit.SECONDS.toMillis(64);
  private static final long SUBSEQUENT_CALL_AVAILABLE_AFTER_MS = TimeUnit.SECONDS.toMillis(300);

  private static final String STATE_REGISTRATION_SECRET              = "REGISTRATION_SECRET";
  private static final String STATE_NUMBER                           = "NUMBER";
  private static final String STATE_VERIFICATION_CODE                = "TEXT_CODE_ENTERED";
  private static final String STATE_CAPTCHA                          = "CAPTCHA";
  private static final String STATE_FCM_TOKEN                        = "FCM_TOKEN";
  private static final String STATE_RESTORE_FLOW_SHOWN               = "RESTORE_FLOW_SHOWN";
  private static final String STATE_SUCCESSFUL_CODE_REQUEST_ATTEMPTS = "SUCCESSFUL_CODE_REQUEST_ATTEMPTS";
  private static final String STATE_REQUEST_RATE_LIMITER             = "REQUEST_RATE_LIMITER";
  private static final String STATE_KBS_TOKEN                        = "KBS_TOKEN";
  private static final String STATE_TIME_REMAINING                   = "TIME_REMAINING";
  private static final String STATE_CAN_CALL_AT_TIME                 = "CAN_CALL_AT_TIME";
  private static final String STATE_IS_REREGISTER                    = "IS_REREGISTER";

  private final SavedStateHandle        registrationState;
  private final VerifyAccountRepository verifyAccountRepository;
  private final KbsRepository           kbsRepository;
  private final RegistrationRepository  registrationRepository;

  public RegistrationViewModel(@NonNull SavedStateHandle savedStateHandle,
                               boolean isReregister,
                               @NonNull VerifyAccountRepository verifyAccountRepository,
                               @NonNull KbsRepository kbsRepository,
                               @NonNull RegistrationRepository registrationRepository)
  {
    this.registrationState       = savedStateHandle;
    this.verifyAccountRepository = verifyAccountRepository;
    this.kbsRepository           = kbsRepository;
    this.registrationRepository  = registrationRepository;

    setInitialDefaultValue(this.registrationState, STATE_REGISTRATION_SECRET, Util.getSecret(18));
    setInitialDefaultValue(this.registrationState, STATE_NUMBER, NumberViewState.INITIAL);
    setInitialDefaultValue(this.registrationState, STATE_VERIFICATION_CODE, "");
    setInitialDefaultValue(this.registrationState, STATE_RESTORE_FLOW_SHOWN, false);
    setInitialDefaultValue(this.registrationState, STATE_SUCCESSFUL_CODE_REQUEST_ATTEMPTS, 0);
    setInitialDefaultValue(this.registrationState, STATE_REQUEST_RATE_LIMITER, new LocalCodeRequestRateLimiter(60_000));

    this.registrationState.set(STATE_IS_REREGISTER, isReregister);
  }

  private static <T> void setInitialDefaultValue(@NonNull SavedStateHandle savedStateHandle, @NonNull String key, @NonNull T initialValue) {
    if (!savedStateHandle.contains(key) || savedStateHandle.get(key) == null) {
      savedStateHandle.set(key, initialValue);
    }
  }

  public boolean isReregister() {
    //noinspection ConstantConditions
    return registrationState.get(STATE_IS_REREGISTER);
  }

  public @NonNull NumberViewState getNumber() {
    //noinspection ConstantConditions
    return registrationState.get(STATE_NUMBER);
  }

  public @NonNull String getTextCodeEntered() {
    //noinspection ConstantConditions
    return registrationState.get(STATE_VERIFICATION_CODE);
  }

  private @Nullable String getCaptchaToken() {
    return registrationState.get(STATE_CAPTCHA);
  }

  public boolean hasCaptchaToken() {
    return getCaptchaToken() != null;
  }

  private @NonNull String getRegistrationSecret() {
    //noinspection ConstantConditions
    return registrationState.get(STATE_REGISTRATION_SECRET);
  }

  public void setCaptchaResponse(@Nullable String captchaToken) {
    registrationState.set(STATE_CAPTCHA, captchaToken);
  }

  private void clearCaptchaResponse() {
    setCaptchaResponse(null);
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

  private void setViewState(NumberViewState numberViewState) {
    if (!numberViewState.equals(getNumber())) {
      registrationState.set(STATE_NUMBER, numberViewState);
    }
  }

  @MainThread
  public void onVerificationCodeEntered(String code) {
    registrationState.set(STATE_VERIFICATION_CODE, code);
  }

  public void onNumberDetected(int countryCode, String nationalNumber) {
    setViewState(getNumber().toBuilder()
                            .countryCode(countryCode)
                            .nationalNumber(nationalNumber)
                            .build());
  }

  private @Nullable String getFcmToken() {
    return registrationState.get(STATE_FCM_TOKEN);
  }

  @MainThread
  public void setFcmToken(@Nullable String fcmToken) {
    registrationState.set(STATE_FCM_TOKEN, fcmToken);
  }

  public void setWelcomeSkippedOnRestore() {
    registrationState.set(STATE_RESTORE_FLOW_SHOWN, true);
  }

  public boolean hasRestoreFlowBeenShown() {
    //noinspection ConstantConditions
    return registrationState.get(STATE_RESTORE_FLOW_SHOWN);
  }

  private void markASuccessfulAttempt() {
    //noinspection ConstantConditions
    registrationState.set(STATE_SUCCESSFUL_CODE_REQUEST_ATTEMPTS, (Integer) registrationState.get(STATE_SUCCESSFUL_CODE_REQUEST_ATTEMPTS) + 1);
  }

  public LiveData<Integer> getSuccessfulCodeRequestAttempts() {
    return registrationState.getLiveData(STATE_SUCCESSFUL_CODE_REQUEST_ATTEMPTS, 0);
  }

  private @NonNull LocalCodeRequestRateLimiter getRequestLimiter() {
    //noinspection ConstantConditions
    return registrationState.get(STATE_REQUEST_RATE_LIMITER);
  }

  private void updateLimiter() {
    registrationState.set(STATE_REQUEST_RATE_LIMITER, registrationState.get(STATE_REQUEST_RATE_LIMITER));
  }

  public @Nullable TokenData getKeyBackupCurrentToken() {
    return registrationState.get(STATE_KBS_TOKEN);
  }

  public void setKeyBackupTokenData(@Nullable TokenData tokenData) {
    registrationState.set(STATE_KBS_TOKEN, tokenData);
  }

  public LiveData<Long> getLockedTimeRemaining() {
    return registrationState.getLiveData(STATE_TIME_REMAINING, 0L);
  }

  public LiveData<Long> getCanCallAtTime() {
    return registrationState.getLiveData(STATE_CAN_CALL_AT_TIME, 0L);
  }

  public void setLockedTimeRemaining(long lockedTimeRemaining) {
    registrationState.set(STATE_TIME_REMAINING, lockedTimeRemaining);
  }

  public void onStartEnterCode() {
    registrationState.set(STATE_CAN_CALL_AT_TIME, System.currentTimeMillis() + FIRST_CALL_AVAILABLE_AFTER_MS);
  }

  private void onCallRequested() {
    registrationState.set(STATE_CAN_CALL_AT_TIME, System.currentTimeMillis() + SUBSEQUENT_CALL_AVAILABLE_AFTER_MS);
  }

  public void setIsReregister(boolean isReregister) {
    registrationState.set(STATE_IS_REREGISTER, isReregister);
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
                                      setFcmToken(processor.getResult().getFcmToken().orNull());
                                      getRequestLimiter().onSuccessfulRequest(mode, getNumber().getE164Number(), System.currentTimeMillis());
                                    } else {
                                      getRequestLimiter().onUnsuccessfulRequest();
                                    }
                                    updateLimiter();
                                  });
  }

  public Single<VerifyAccountResponseProcessor> verifyCodeAndRegisterAccountWithoutRegistrationLock(@NonNull String code) {
    onVerificationCodeEntered(code);

    RegistrationData registrationData = new RegistrationData(getTextCodeEntered(),
                                                             getNumber().getE164Number(),
                                                             getRegistrationSecret(),
                                                             registrationRepository.getRegistrationId(),
                                                             registrationRepository.getProfileKey(getNumber().getE164Number()),
                                                             getFcmToken());

    return verifyAccountRepository.verifyAccount(registrationData)
                                  .map(VerifyAccountResponseWithoutKbs::new)
                                  .flatMap(processor -> {
                                    if (processor.hasResult()) {
                                      return registrationRepository.registerAccountWithoutRegistrationLock(registrationData, processor.getResult())
                                                                   .map(VerifyAccountResponseWithoutKbs::new);
                                    } else if (processor.registrationLock() && !processor.isKbsLocked()) {
                                      return kbsRepository.getToken(processor.getLockedException().getBasicStorageCredentials())
                                                          .map(r -> r.getResult().isPresent() ? new VerifyAccountResponseWithSuccessfulKbs(processor.getResponse(), r.getResult().get())
                                                                                              : new VerifyAccountResponseWithFailedKbs(r));
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

  public Single<VerifyCodeWithRegistrationLockResponseProcessor> verifyCodeAndRegisterAccountWithRegistrationLock(@NonNull String pin) {
    RegistrationData registrationData = new RegistrationData(getTextCodeEntered(),
                                                             getNumber().getE164Number(),
                                                             getRegistrationSecret(),
                                                             registrationRepository.getRegistrationId(),
                                                             registrationRepository.getProfileKey(getNumber().getE164Number()),
                                                             getFcmToken());

    TokenData kbsTokenData = Objects.requireNonNull(getKeyBackupCurrentToken());

    return verifyAccountRepository.verifyAccountWithPin(registrationData, pin, kbsTokenData)
                                  .map(r -> new VerifyCodeWithRegistrationLockResponseProcessor(r, kbsTokenData))
                                  .flatMap(processor -> {
                                    if (processor.hasResult()) {
                                      return registrationRepository.registerAccountWithRegistrationLock(registrationData, processor.getResult(), pin)
                                                                   .map(processor::updatedIfRegistrationFailed);
                                    } else if (processor.wrongPin()) {
                                      TokenData newToken = TokenData.withResponse(kbsTokenData, processor.getTokenResponse());
                                      return Single.just(new VerifyCodeWithRegistrationLockResponseProcessor(processor.getResponse(), newToken));
                                    }
                                    return Single.just(processor);
                                  })
                                  .observeOn(AndroidSchedulers.mainThread())
                                  .doOnSuccess(processor -> {
                                    if (processor.wrongPin()) {
                                      setKeyBackupTokenData(processor.getToken());
                                    }
                                  });
  }

  public static final class Factory extends AbstractSavedStateViewModelFactory {
    private final boolean isReregister;

    public Factory(@NonNull SavedStateRegistryOwner owner, boolean isReregister) {
      super(owner, null);
      this.isReregister = isReregister;
    }

    @Override
    protected @NonNull <T extends ViewModel> T create(@NonNull String key, @NonNull Class<T> modelClass, @NonNull SavedStateHandle handle) {
      //noinspection ConstantConditions
      return modelClass.cast(new RegistrationViewModel(handle,
                                                       isReregister,
                                                       new VerifyAccountRepository(ApplicationDependencies.getApplication()),
                                                       new KbsRepository(),
                                                       new RegistrationRepository(ApplicationDependencies.getApplication())));
    }
  }
}
