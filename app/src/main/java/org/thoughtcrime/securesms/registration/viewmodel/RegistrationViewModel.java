package org.thoughtcrime.securesms.registration.viewmodel;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AbstractSavedStateViewModelFactory;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import androidx.savedstate.SavedStateRegistryOwner;

import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.pin.KbsRepository;
import org.thoughtcrime.securesms.pin.TokenData;
import org.thoughtcrime.securesms.registration.RegistrationData;
import org.thoughtcrime.securesms.registration.RegistrationRepository;
import org.thoughtcrime.securesms.registration.RegistrationSessionProcessor;
import org.thoughtcrime.securesms.registration.VerifyAccountRepository;
import org.thoughtcrime.securesms.registration.VerifyResponse;
import org.thoughtcrime.securesms.registration.VerifyResponseProcessor;
import org.thoughtcrime.securesms.registration.VerifyResponseWithRegistrationLockProcessor;
import org.thoughtcrime.securesms.registration.VerifyResponseWithoutKbs;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.signalservice.api.KbsPinData;
import org.whispersystems.signalservice.internal.ServiceResponse;
import org.whispersystems.signalservice.internal.push.RegistrationSessionMetadataResponse;

import java.util.Objects;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

public final class RegistrationViewModel extends BaseRegistrationViewModel {

  private static final String STATE_FCM_TOKEN          = "FCM_TOKEN";
  private static final String STATE_RESTORE_FLOW_SHOWN = "RESTORE_FLOW_SHOWN";
  private static final String STATE_IS_REREGISTER      = "IS_REREGISTER";
  private static final String STATE_BACKUP_COMPLETED   = "BACKUP_COMPLETED";

  private final RegistrationRepository registrationRepository;

  public RegistrationViewModel(@NonNull SavedStateHandle savedStateHandle,
                               boolean isReregister,
                               @NonNull VerifyAccountRepository verifyAccountRepository,
                               @NonNull KbsRepository kbsRepository,
                               @NonNull RegistrationRepository registrationRepository)
  {
    super(savedStateHandle, verifyAccountRepository, kbsRepository, Util.getSecret(18));

    this.registrationRepository = registrationRepository;

    setInitialDefaultValue(STATE_RESTORE_FLOW_SHOWN, false);
    setInitialDefaultValue(STATE_BACKUP_COMPLETED, false);

    this.savedState.set(STATE_IS_REREGISTER, isReregister);
  }

  public boolean isReregister() {
    //noinspection ConstantConditions
    return savedState.get(STATE_IS_REREGISTER);
  }

  public void onNumberDetected(int countryCode, String nationalNumber) {
    setViewState(getNumber().toBuilder()
                            .countryCode(countryCode)
                            .nationalNumber(nationalNumber)
                            .build());
  }

  public @Nullable String getFcmToken() {
    return savedState.get(STATE_FCM_TOKEN);
  }

  @MainThread
  public void setFcmToken(@Nullable String fcmToken) {
    savedState.set(STATE_FCM_TOKEN, fcmToken);
  }

  public void setWelcomeSkippedOnRestore() {
    savedState.set(STATE_RESTORE_FLOW_SHOWN, true);
  }

  public boolean hasRestoreFlowBeenShown() {
    //noinspection ConstantConditions
    return savedState.get(STATE_RESTORE_FLOW_SHOWN);
  }

  public void setIsReregister(boolean isReregister) {
    savedState.set(STATE_IS_REREGISTER, isReregister);
  }

  public void markBackupCompleted() {
    savedState.set(STATE_BACKUP_COMPLETED, true);
  }

  public boolean hasBackupCompleted() {
    Boolean completed = savedState.get(STATE_BACKUP_COMPLETED);
    return completed != null ? completed : false;
  }

  @Override
  protected Single<ServiceResponse<VerifyResponse>> verifyAccountWithoutRegistrationLock() {
    final String sessionId = getSessionId();
    if (sessionId == null) {
      throw new IllegalStateException("No valid registration session");
    }
    return verifyAccountRepository.verifyAccount(sessionId, getRegistrationData())
                                  .map(RegistrationSessionProcessor.RegistrationSessionProcessorForVerification::new)
                                  .observeOn(AndroidSchedulers.mainThread())
                                  .doOnSuccess(processor -> {
                                    setCanSmsAtTime(processor.getNextCodeViaSmsAttempt());
                                    setCanCallAtTime(processor.getNextCodeViaCallAttempt());
                                  })
                                  .observeOn(Schedulers.io())
                                  .flatMap( processor -> {
                                    if (processor.isAlreadyVerified() || (processor.hasResult() && processor.isVerified())) {
                                      return verifyAccountRepository.registerAccount(sessionId, getRegistrationData(), null, null);
                                    } else {
                                      return Single.just(ServiceResponse.<VerifyResponse, RegistrationSessionMetadataResponse>coerceError(processor.getResponse()));
                                    }
                                  })
                                  .flatMap(verifyAccountWithoutKbsResponse -> {
                                    VerifyResponseProcessor processor = new VerifyResponseWithoutKbs(verifyAccountWithoutKbsResponse);
                                    String                  pin       = SignalStore.kbsValues().getPin();

                                    if (processor.registrationLock() && SignalStore.kbsValues().getRegistrationLockToken() != null && pin != null) {
                                      KbsPinData pinData = new KbsPinData(SignalStore.kbsValues().getOrCreateMasterKey(), SignalStore.kbsValues().getRegistrationLockTokenResponse());

                                      return verifyAccountRepository.registerAccount(sessionId, getRegistrationData(), pin, () -> pinData)
                                                                    .map(verifyAccountWithPinResponse -> {
                                                                      if (verifyAccountWithPinResponse.getResult().isPresent() && verifyAccountWithPinResponse.getResult().get().getKbsData() != null) {
                                                                        return verifyAccountWithPinResponse;
                                                                      } else {
                                                                        return verifyAccountWithoutKbsResponse;
                                                                      }
                                                                    });
                                    } else {
                                      return Single.just(verifyAccountWithoutKbsResponse);
                                    }
                                  });
  }

  @Override
  protected Single<ServiceResponse<VerifyResponse>> verifyAccountWithRegistrationLock(@NonNull String pin, @NonNull TokenData kbsTokenData) {
    final String sessionId = getSessionId();
    if (sessionId == null) {
      throw new IllegalStateException("No valid registration session");
    }
    return verifyAccountRepository.verifyAccount(sessionId, getRegistrationData())
                                  .map(RegistrationSessionProcessor.RegistrationSessionProcessorForVerification::new)
                                  .doOnSuccess(processor -> {
                                    if (processor.hasResult()) {
                                      setCanSmsAtTime(processor.getNextCodeViaSmsAttempt());
                                      setCanCallAtTime(processor.getNextCodeViaCallAttempt());
                                    }
                                  })
                                  .flatMap( processor -> {
                                    if (processor.isAlreadyVerified() || (processor.hasResult() && processor.isVerified())) {
                                      return verifyAccountRepository.registerAccount(sessionId, getRegistrationData(), pin, () -> Objects.requireNonNull(KbsRepository.restoreMasterKey(pin, kbsTokenData.getEnclave(), kbsTokenData.getBasicAuth(), kbsTokenData.getTokenResponse())));
                                    } else {
                                      return Single.just(ServiceResponse.coerceError(processor.getResponse()));
                                    }
                                  });
  }

  @Override
  protected Single<VerifyResponseProcessor> onVerifySuccess(@NonNull VerifyResponseProcessor processor) {
    return registrationRepository.registerAccount(getRegistrationData(), processor.getResult())
                                 .map(VerifyResponseWithoutKbs::new);
  }

  @Override
  protected Single<VerifyResponseWithRegistrationLockProcessor> onVerifySuccessWithRegistrationLock(@NonNull VerifyResponseWithRegistrationLockProcessor processor, String pin) {
    return registrationRepository.registerAccount(getRegistrationData(), processor.getResult())
                                 .map(processor::updatedIfRegistrationFailed);
  }

  private RegistrationData getRegistrationData() {
    return new RegistrationData(getTextCodeEntered(),
                                getNumber().getE164Number(),
                                getRegistrationSecret(),
                                registrationRepository.getRegistrationId(),
                                registrationRepository.getProfileKey(getNumber().getE164Number()),
                                getFcmToken(),
                                registrationRepository.getPniRegistrationId(),
                                registrationRepository.getRecoveryPassword());
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
