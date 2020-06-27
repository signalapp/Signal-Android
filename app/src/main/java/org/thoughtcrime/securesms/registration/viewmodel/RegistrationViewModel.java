package org.thoughtcrime.securesms.registration.viewmodel;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.signalservice.internal.contacts.entities.TokenResponse;
import org.whispersystems.signalservice.internal.util.JsonUtil;

import java.io.IOException;

public final class RegistrationViewModel extends ViewModel {

  private static final String TAG = Log.tag(RegistrationViewModel.class);

  private final String                                       secret;
  private final MutableLiveData<NumberViewState>             number;
  private final MutableLiveData<String>                      textCodeEntered;
  private final MutableLiveData<String>                      captchaToken;
  private final MutableLiveData<String>                      fcmToken;
  private final MutableLiveData<String>                      basicStorageCredentials;
  private final MutableLiveData<Boolean>                     restoreFlowShown;
  private final MutableLiveData<Integer>                     successfulCodeRequestAttempts;
  private final MutableLiveData<LocalCodeRequestRateLimiter> requestLimiter;
  private final MutableLiveData<String>                      keyBackupcurrentTokenJson;
  private final MutableLiveData<Long>                        timeRemaining;

  public RegistrationViewModel(@NonNull SavedStateHandle savedStateHandle) {
    secret = loadValue(savedStateHandle, "REGISTRATION_SECRET", Util.getSecret(18));

    number                        = savedStateHandle.getLiveData("NUMBER", NumberViewState.INITIAL);
    textCodeEntered               = savedStateHandle.getLiveData("TEXT_CODE_ENTERED", "");
    captchaToken                  = savedStateHandle.getLiveData("CAPTCHA");
    fcmToken                      = savedStateHandle.getLiveData("FCM_TOKEN");
    basicStorageCredentials       = savedStateHandle.getLiveData("BASIC_STORAGE_CREDENTIALS");
    restoreFlowShown              = savedStateHandle.getLiveData("RESTORE_FLOW_SHOWN", false);
    successfulCodeRequestAttempts = savedStateHandle.getLiveData("SUCCESSFUL_CODE_REQUEST_ATTEMPTS", 0);
    requestLimiter                = savedStateHandle.getLiveData("REQUEST_RATE_LIMITER", new LocalCodeRequestRateLimiter(60_000));
    keyBackupcurrentTokenJson     = savedStateHandle.getLiveData("KBS_TOKEN");
    timeRemaining                 = savedStateHandle.getLiveData("TIME_REMAINING", 0L);
  }

  private static <T> T loadValue(@NonNull SavedStateHandle savedStateHandle, @NonNull String key, @NonNull T initialValue) {
    if (!savedStateHandle.contains(key)) {
      savedStateHandle.set(key, initialValue);
    }
    return savedStateHandle.get(key);
  }

  public @NonNull NumberViewState getNumber() {
    //noinspection ConstantConditions Live data was given an initial value
    return number.getValue();
  }

  public @NonNull LiveData<NumberViewState> getLiveNumber() {
    return number;
  }

  public @NonNull String getTextCodeEntered() {
    //noinspection ConstantConditions Live data was given an initial value
    return textCodeEntered.getValue();
  }

  public String getCaptchaToken() {
    return captchaToken.getValue();
  }

  public boolean hasCaptchaToken() {
    return getCaptchaToken() != null;
  }

  public String getRegistrationSecret() {
    return secret;
  }

  public void onCaptchaResponse(String captchaToken) {
    this.captchaToken.setValue(captchaToken);
  }

  public void clearCaptchaResponse() {
    captchaToken.setValue(null);
  }

  public void onCountrySelected(@Nullable String selectedCountryName, int countryCode) {
    setViewState(getNumber().toBuilder()
                            .selectedCountryDisplayName(selectedCountryName)
                            .countryCode(countryCode).build());
  }

  public void setNationalNumber(long number) {
    NumberViewState numberViewState = getNumber().toBuilder().nationalNumber(number).build();
    setViewState(numberViewState);
  }

  private void setViewState(NumberViewState numberViewState) {
    if (!numberViewState.equals(getNumber())) {
      number.setValue(numberViewState);
    }
  }

  @MainThread
  public void onVerificationCodeEntered(String code) {
    textCodeEntered.setValue(code);
  }

  public void onNumberDetected(int countryCode, long nationalNumber) {
    setViewState(getNumber().toBuilder()
                            .countryCode(countryCode)
                            .nationalNumber(nationalNumber)
                            .build());
  }

  public String getFcmToken() {
    return fcmToken.getValue();
  }

  @MainThread
  public void setFcmToken(@Nullable String fcmToken) {
    this.fcmToken.setValue(fcmToken);
  }

  public void setWelcomeSkippedOnRestore() {
    restoreFlowShown.setValue(true);
  }

  public boolean hasRestoreFlowBeenShown() {
    //noinspection ConstantConditions Live data was given an initial value
    return restoreFlowShown.getValue();
  }

  public void markASuccessfulAttempt() {
    //noinspection ConstantConditions Live data was given an initial value
    successfulCodeRequestAttempts.setValue(successfulCodeRequestAttempts.getValue() + 1);
  }

  public LiveData<Integer> getSuccessfulCodeRequestAttempts() {
    return successfulCodeRequestAttempts;
  }

  public @NonNull LocalCodeRequestRateLimiter getRequestLimiter() {
    //noinspection ConstantConditions Live data was given an initial value
    return requestLimiter.getValue();
  }

  public void updateLimiter() {
    requestLimiter.setValue(requestLimiter.getValue());
  }

  public void setStorageCredentials(@Nullable String storageCredentials) {
    basicStorageCredentials.setValue(storageCredentials);
  }

  public @Nullable String getBasicStorageCredentials() {
    return basicStorageCredentials.getValue();
  }

  public @Nullable TokenResponse getKeyBackupCurrentToken() {
    String json = keyBackupcurrentTokenJson.getValue();
    if (json == null) return null;
    try {
      return JsonUtil.fromJson(json, TokenResponse.class);
    } catch (IOException e) {
      Log.w(TAG, e);
      return null;
    }
  }

  public void setKeyBackupCurrentToken(TokenResponse tokenResponse) {
    String json = tokenResponse == null ? null : JsonUtil.toJson(tokenResponse);
    keyBackupcurrentTokenJson.setValue(json);
  }

  public LiveData<Long> getTimeRemaining() {
    return timeRemaining;
  }

  public void setTimeRemaining(long timeRemaining) {
    this.timeRemaining.setValue(timeRemaining);
  }
}
