package org.thoughtcrime.securesms.registration.viewmodel;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.AbstractSavedStateViewModelFactory;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import androidx.savedstate.SavedStateRegistryOwner;

import org.signal.core.util.Stopwatch;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobs.NewRegistrationUsernameSyncJob;
import org.thoughtcrime.securesms.jobs.StorageAccountRestoreJob;
import org.thoughtcrime.securesms.jobs.StorageSyncJob;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.lock.PinHashing;
import org.thoughtcrime.securesms.pin.KbsRepository;
import org.thoughtcrime.securesms.pin.KeyBackupSystemWrongPinException;
import org.thoughtcrime.securesms.pin.TokenData;
import org.thoughtcrime.securesms.registration.RegistrationData;
import org.thoughtcrime.securesms.registration.RegistrationRepository;
import org.thoughtcrime.securesms.registration.RegistrationSessionProcessor;
import org.thoughtcrime.securesms.registration.VerifyAccountRepository;
import org.thoughtcrime.securesms.registration.VerifyResponse;
import org.thoughtcrime.securesms.registration.VerifyResponseProcessor;
import org.thoughtcrime.securesms.registration.VerifyResponseWithRegistrationLockProcessor;
import org.thoughtcrime.securesms.registration.VerifyResponseWithoutKbs;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.signalservice.api.KbsPinData;
import org.whispersystems.signalservice.api.KeyBackupSystemNoDataException;
import org.whispersystems.signalservice.api.push.exceptions.IncorrectCodeException;
import org.whispersystems.signalservice.internal.ServiceResponse;
import org.whispersystems.signalservice.internal.contacts.entities.TokenResponse;
import org.whispersystems.signalservice.internal.push.RegistrationSessionMetadataResponse;
import org.whispersystems.util.Base64;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

public final class RegistrationViewModel extends BaseRegistrationViewModel {

  private static final String TAG = Log.tag(RegistrationViewModel.class);

  private static final String STATE_FCM_TOKEN          = "FCM_TOKEN";
  private static final String STATE_RESTORE_FLOW_SHOWN = "RESTORE_FLOW_SHOWN";
  private static final String STATE_IS_REREGISTER      = "IS_REREGISTER";
  private static final String STATE_BACKUP_COMPLETED   = "BACKUP_COMPLETED";

  private final RegistrationRepository registrationRepository;

  private boolean userSkippedReRegisterFlow = false;
  private boolean autoShowSmsConfirmDialog = false;

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
    String token = savedState.get(STATE_FCM_TOKEN);
    if (token == null || token.isEmpty()) {
      return null;
    }
    return token;
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

  public boolean hasUserSkippedReRegisterFlow() {
    return userSkippedReRegisterFlow;
  }

  public void setUserSkippedReRegisterFlow(boolean userSkippedReRegisterFlow) {
    Log.i(TAG, "User skipped re-register flow.");
    this.userSkippedReRegisterFlow = userSkippedReRegisterFlow;
    if (userSkippedReRegisterFlow) {
      setAutoShowSmsConfirmDialog(true);
    }
  }

  public boolean shouldAutoShowSmsConfirmDialog() {
    return autoShowSmsConfirmDialog;
  }

  public void setAutoShowSmsConfirmDialog(boolean autoShowSmsConfirmDialog) {
    this.autoShowSmsConfirmDialog = autoShowSmsConfirmDialog;
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
                                    if (processor.hasResult()) {
                                      setCanSmsAtTime(processor.getNextCodeViaSmsAttempt());
                                      setCanCallAtTime(processor.getNextCodeViaCallAttempt());
                                    }
                                  })
                                  .observeOn(Schedulers.io())
                                  .flatMap(processor -> {
                                    if (processor.isAlreadyVerified() || (processor.hasResult() && processor.isVerified())) {
                                      return verifyAccountRepository.registerAccount(sessionId, getRegistrationData(), null, null);
                                    } else if (processor.getError() == null) {
                                      return Single.just(ServiceResponse.<VerifyResponse>forApplicationError(new IncorrectCodeException(), 403, null));
                                    } else {
                                      return Single.just(ServiceResponse.<VerifyResponse, RegistrationSessionMetadataResponse>coerceError(processor.getResponse()));
                                    }
                                  })
                                  .flatMap(verifyAccountWithoutKbsResponse -> {
                                    VerifyResponseProcessor processor = new VerifyResponseWithoutKbs(verifyAccountWithoutKbsResponse);
                                    String                  pin       = SignalStore.kbsValues().getPin();

                                    if ((processor.isKbsLocked() || processor.registrationLock()) && SignalStore.kbsValues().getRegistrationLockToken() != null && pin != null) {
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
                                  })
                                  .onErrorReturn(ServiceResponse::forUnknownError);
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
                                  .<ServiceResponse<VerifyResponse>>flatMap(processor -> {
                                    if (processor.isAlreadyVerified() || (processor.hasResult() && processor.isVerified())) {
                                      return verifyAccountRepository.registerAccount(sessionId, getRegistrationData(), pin, () -> Objects.requireNonNull(KbsRepository.restoreMasterKey(pin, kbsTokenData.getEnclave(), kbsTokenData.getBasicAuth(), kbsTokenData.getTokenResponse())));
                                    } else {
                                      return Single.just(ServiceResponse.coerceError(processor.getResponse()));
                                    }
                                  })
                                  .onErrorReturn(ServiceResponse::forUnknownError);
  }

  @Override
  protected Single<VerifyResponseProcessor> onVerifySuccess(@NonNull VerifyResponseProcessor processor) {
    return registrationRepository.registerAccount(getRegistrationData(), processor.getResult(), false)
                                 .map(VerifyResponseWithoutKbs::new);
  }

  @Override
  protected Single<VerifyResponseWithRegistrationLockProcessor> onVerifySuccessWithRegistrationLock(@NonNull VerifyResponseWithRegistrationLockProcessor processor, String pin) {
    return registrationRepository.registerAccount(getRegistrationData(), processor.getResult(), true)
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
                                getSessionId() != null ? null : getRecoveryPassword());
  }

  public @NonNull Single<VerifyResponseProcessor> verifyReRegisterWithPin(@NonNull String pin) {
    return Single.fromCallable(() -> verifyReRegisterWithPinInternal(pin))
                 .subscribeOn(Schedulers.io())
                 .observeOn(Schedulers.io())
                 .flatMap(data -> {
                   if (data.canProceed) {
                     return updateFcmTokenValue().subscribeOn(Schedulers.io())
                                                 .observeOn(Schedulers.io())
                                                 .onErrorReturnItem("")
                                                 .flatMap(s -> verifyReRegisterWithRecoveryPassword(pin, data.pinData));
                   } else {
                     throw new IllegalStateException("Unable to get token or master key");
                   }
                 })
                 .onErrorReturn(t -> new VerifyResponseWithRegistrationLockProcessor(ServiceResponse.forUnknownError(t), getKeyBackupCurrentToken()))
                 .map(p -> {
                   if (p instanceof VerifyResponseWithRegistrationLockProcessor) {
                     VerifyResponseWithRegistrationLockProcessor lockProcessor = (VerifyResponseWithRegistrationLockProcessor) p;
                     if (lockProcessor.wrongPin() && lockProcessor.getTokenData() != null) {
                       TokenData newToken = TokenData.withResponse(lockProcessor.getTokenData(), lockProcessor.getTokenResponse());
                       return new VerifyResponseWithRegistrationLockProcessor(lockProcessor.getResponse(), newToken);
                     }
                   }

                   return p;
                 })
                 .doOnSuccess(p -> {
                   if (p.hasResult()) {
                     restoreFromStorageService();
                   }
                 })
                 .observeOn(AndroidSchedulers.mainThread());
  }

  @WorkerThread
  private @NonNull ReRegistrationData verifyReRegisterWithPinInternal(@NonNull String pin)
      throws KeyBackupSystemWrongPinException, IOException, KeyBackupSystemNoDataException
  {
    String localPinHash = SignalStore.kbsValues().getLocalPinHash();

    if (hasRecoveryPassword() && localPinHash != null) {
      if (PinHashing.verifyLocalPinHash(localPinHash, pin)) {
        Log.i(TAG, "Local pin matches input, attempting registration");
        return ReRegistrationData.canProceed(new KbsPinData(SignalStore.kbsValues().getOrCreateMasterKey(), SignalStore.kbsValues().getRegistrationLockTokenResponse()));
      } else {
        throw new KeyBackupSystemWrongPinException(new TokenResponse(null, null, 0));
      }
    } else {
      TokenData data = getKeyBackupCurrentToken();
      if (data == null) {
        Log.w(TAG, "No token data, abort skip flow");
        return ReRegistrationData.cannotProceed();
      }

      KbsPinData kbsPinData = KbsRepository.restoreMasterKey(pin, data.getEnclave(), data.getBasicAuth(), data.getTokenResponse());
      if (kbsPinData == null || kbsPinData.getMasterKey() == null) {
        Log.w(TAG, "No kbs data, abort skip flow");
        return ReRegistrationData.cannotProceed();
      }

      setRecoveryPassword(kbsPinData.getMasterKey().deriveRegistrationRecoveryPassword());
      setKeyBackupTokenData(data);
      return ReRegistrationData.canProceed(kbsPinData);
    }
  }

  private Single<VerifyResponseProcessor> verifyReRegisterWithRecoveryPassword(@NonNull String pin, @NonNull KbsPinData pinData) {
    RegistrationData registrationData = getRegistrationData();
    if (registrationData.getRecoveryPassword() == null) {
      throw new IllegalStateException("No valid recovery password");
    }

    return verifyAccountRepository.registerAccount(null, registrationData, null, null)
                                  .observeOn(Schedulers.io())
                                  .onErrorReturn(ServiceResponse::forUnknownError)
                                  .map(VerifyResponseWithoutKbs::new)
                                  .flatMap(processor -> {
                                    if (processor.registrationLock()) {
                                      return verifyAccountRepository.registerAccount(null, registrationData, pin, () -> pinData)
                                                                    .onErrorReturn(ServiceResponse::forUnknownError)
                                                                    .map(r -> new VerifyResponseWithRegistrationLockProcessor(r, getKeyBackupCurrentToken()));
                                    } else {
                                      return Single.just(processor);
                                    }
                                  })
                                  .flatMap(processor -> {
                                    if (processor.hasResult()) {
                                      VerifyResponse verifyResponse             = processor.getResult();
                                      boolean        setRegistrationLockEnabled = verifyResponse.getKbsData() != null;

                                      if (!setRegistrationLockEnabled) {
                                        verifyResponse = new VerifyResponse(processor.getResult().getVerifyAccountResponse(), pinData, pin);
                                      }

                                      return registrationRepository.registerAccount(registrationData, verifyResponse, setRegistrationLockEnabled)
                                                                   .map(r -> new VerifyResponseWithRegistrationLockProcessor(r, getKeyBackupCurrentToken()));
                                    } else {
                                      return Single.just(processor);
                                    }
                                  });
  }

  public @NonNull Single<Boolean> canEnterSkipSmsFlow() {
    if (userSkippedReRegisterFlow) {
      return Single.just(false);
    }

    return Single.just(hasRecoveryPassword())
                 .flatMap(hasRecoveryPassword -> {
                   Log.i(TAG, "Checking if user has existing recovery password: " + hasRecoveryPassword);
                   if (hasRecoveryPassword) {
                     return Single.just(true);
                   } else {
                     return checkForValidKbsAuthCredentials();
                   }
                 });
  }

  private Single<Boolean> checkForValidKbsAuthCredentials() {
    final List<String> kbsAuthTokenList = SignalStore.kbsValues().getKbsAuthTokenList();
    List<String> usernamePasswords = kbsAuthTokenList
        .stream()
        .limit(10)
        .map(t -> {
          try {
            return new String(Base64.decode(t.replace("Basic ", "").trim()), StandardCharsets.ISO_8859_1);
          } catch (IOException e) {
            return null;
          }
        })
        .collect(Collectors.toList());

    if (usernamePasswords.isEmpty()) {
      return Single.just(false);
    }

    return registrationRepository.getKbsAuthCredential(getRegistrationData(), usernamePasswords)
                                 .flatMap(p -> {
                                   if (p.getValid() != null) {
                                     return kbsRepository.getToken(p.getValid())
                                                         .flatMap(r -> {
                                                           if (r.getResult().isPresent()) {
                                                             TokenData tokenData = r.getResult().get();
                                                             setKeyBackupTokenData(tokenData);
                                                             return Single.just(tokenData.getTriesRemaining() > 0);
                                                           } else {
                                                             return Single.just(false);
                                                           }
                                                         });
                                   } else {
                                     return Single.just(false);
                                   }
                                 })
                                 .onErrorReturnItem(false)
                                 .observeOn(AndroidSchedulers.mainThread());
  }

  public Single<String> updateFcmTokenValue() {
    return verifyAccountRepository.getFcmToken().observeOn(AndroidSchedulers.mainThread()).doOnSuccess(this::setFcmToken);
  }

  private void restoreFromStorageService() {
    SignalStore.onboarding().clearAll();

    Stopwatch stopwatch = new Stopwatch("ReRegisterRestore");

    ApplicationDependencies.getJobManager().runSynchronously(new StorageAccountRestoreJob(), StorageAccountRestoreJob.LIFESPAN);
    stopwatch.split("AccountRestore");

    ApplicationDependencies
        .getJobManager()
        .startChain(new StorageSyncJob())
        .then(new NewRegistrationUsernameSyncJob())
        .enqueueAndBlockUntilCompletion(TimeUnit.SECONDS.toMillis(10));
    stopwatch.split("ContactRestore");

    try {
      FeatureFlags.refreshSync();
    } catch (IOException e) {
      Log.w(TAG, "Failed to refresh flags.", e);
    }
    stopwatch.split("FeatureFlags");

    stopwatch.stop(TAG);
  }

  private boolean hasRecoveryPassword() {
    return getRecoveryPassword() != null && Objects.equals(getRegistrationData().getE164(), SignalStore.account().getE164());
  }

  private static class ReRegistrationData {
    public boolean    canProceed;
    public KbsPinData pinData;

    private ReRegistrationData(boolean canProceed, @Nullable KbsPinData pinData) {
      this.canProceed = canProceed;
      this.pinData    = pinData;
    }

    public static ReRegistrationData cannotProceed() {
      return new ReRegistrationData(false, null);
    }

    public static ReRegistrationData canProceed(@NonNull KbsPinData pinData) {
      return new ReRegistrationData(true, pinData);
    }
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
