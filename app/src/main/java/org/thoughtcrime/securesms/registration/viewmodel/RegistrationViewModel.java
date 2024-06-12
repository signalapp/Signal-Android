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
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.jobs.ReclaimUsernameAndLinkJob;
import org.thoughtcrime.securesms.jobs.StorageAccountRestoreJob;
import org.thoughtcrime.securesms.jobs.StorageSyncJob;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.pin.SvrWrongPinException;
import org.thoughtcrime.securesms.pin.SvrRepository;
import org.thoughtcrime.securesms.registration.RegistrationData;
import org.thoughtcrime.securesms.registration.RegistrationRepository;
import org.thoughtcrime.securesms.registration.RegistrationSessionProcessor;
import org.thoughtcrime.securesms.registration.VerifyAccountRepository;
import org.thoughtcrime.securesms.registration.VerifyResponse;
import org.thoughtcrime.securesms.registration.VerifyResponseProcessor;
import org.thoughtcrime.securesms.registration.VerifyResponseWithRegistrationLockProcessor;
import org.thoughtcrime.securesms.registration.VerifyResponseWithoutKbs;
import org.thoughtcrime.securesms.util.RemoteConfig;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.signalservice.api.SvrNoDataException;
import org.whispersystems.signalservice.api.kbs.MasterKey;
import org.whispersystems.signalservice.api.kbs.PinHashUtil;
import org.whispersystems.signalservice.api.push.exceptions.IncorrectCodeException;
import org.whispersystems.signalservice.api.push.exceptions.IncorrectRegistrationRecoveryPasswordException;
import org.whispersystems.signalservice.internal.ServiceResponse;
import org.whispersystems.signalservice.internal.push.RegistrationSessionMetadataResponse;
import org.signal.core.util.Base64;

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
                               @NonNull RegistrationRepository registrationRepository)
  {
    super(savedStateHandle, verifyAccountRepository, Util.getSecret(18));

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
                                    String                  pin       = SignalStore.svr().getPin();

                                    if ((processor.isRegistrationLockPresentAndSvrExhausted() || processor.registrationLock()) && SignalStore.svr().getRegistrationLockToken() != null && pin != null) {
                                      return verifyAccountRepository.registerAccount(sessionId, getRegistrationData(), pin, () -> SignalStore.svr().getOrCreateMasterKey())
                                                                    .map(verifyAccountWithPinResponse -> {
                                                                      if (verifyAccountWithPinResponse.getResult().isPresent() && verifyAccountWithPinResponse.getResult().get().getMasterKey() != null) {
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
  protected Single<ServiceResponse<VerifyResponse>> verifyAccountWithRegistrationLock(@NonNull String pin, @NonNull SvrAuthCredentialSet svrAuthCredentials) {
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
                                      return verifyAccountRepository.registerAccount(sessionId, getRegistrationData(), pin, () -> SvrRepository.restoreMasterKeyPreRegistration(svrAuthCredentials, pin));
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
                                                 .flatMap(s -> verifyReRegisterWithRecoveryPassword(pin, data.masterKey));
                   } else {
                     throw new IncorrectRegistrationRecoveryPasswordException();
                   }
                 })
                 .onErrorReturn(t -> new VerifyResponseWithRegistrationLockProcessor(ServiceResponse.forUnknownError(t), getSvrAuthCredentials()))
                 .map(p -> {
                   if (p instanceof VerifyResponseWithRegistrationLockProcessor) {
                     VerifyResponseWithRegistrationLockProcessor lockProcessor = (VerifyResponseWithRegistrationLockProcessor) p;
                     if (lockProcessor.wrongPin() && lockProcessor.getSvrTriesRemaining() != null) {
                       return new VerifyResponseWithRegistrationLockProcessor(lockProcessor.getResponse(), lockProcessor.getSvrAuthCredentials());
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
      throws SvrWrongPinException, IOException, SvrNoDataException
  {
    String localPinHash = SignalStore.svr().getLocalPinHash();

    if (hasRecoveryPassword() && localPinHash != null) {
      if (PinHashUtil.verifyLocalPinHash(localPinHash, pin)) {
        Log.i(TAG, "Local pin matches input, attempting registration");
        return ReRegistrationData.canProceed(SignalStore.svr().getOrCreateMasterKey());
      } else {
        throw new SvrWrongPinException(0);
      }
    } else {
      SvrAuthCredentialSet authCredentials = getSvrAuthCredentials();
      if (authCredentials == null) {
        Log.w(TAG, "No SVR auth credentials, abort skip flow");
        return ReRegistrationData.cannotProceed();
      }

      MasterKey masterKey = SvrRepository.restoreMasterKeyPreRegistration(authCredentials, pin);

      setRecoveryPassword(masterKey.deriveRegistrationRecoveryPassword());
      setSvrTriesRemaining(10);
      return ReRegistrationData.canProceed(masterKey);
    }
  }

  private Single<VerifyResponseProcessor> verifyReRegisterWithRecoveryPassword(@NonNull String pin, @NonNull MasterKey masterKey) {
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
                                      setSvrAuthCredentials(processor.getSvrAuthCredentials());
                                      return verifyAccountRepository.registerAccount(null, registrationData, pin, () -> masterKey)
                                                                    .onErrorReturn(ServiceResponse::forUnknownError)
                                                                    .map(r -> new VerifyResponseWithRegistrationLockProcessor(r, processor.getSvrAuthCredentials()));
                                    } else {
                                      return Single.just(processor);
                                    }
                                  })
                                  .flatMap(processor -> {
                                    if (processor.hasResult()) {
                                      VerifyResponse verifyResponse             = processor.getResult();
                                      boolean        setRegistrationLockEnabled = verifyResponse.getMasterKey() != null;

                                      if (!setRegistrationLockEnabled) {
                                        verifyResponse = new VerifyResponse(processor.getResult().getVerifyAccountResponse(), masterKey, pin, verifyResponse.getAciPreKeyCollection(), verifyResponse.getPniPreKeyCollection());
                                      }

                                      return registrationRepository.registerAccount(registrationData, verifyResponse, setRegistrationLockEnabled)
                                                                   .map(r -> new VerifyResponseWithRegistrationLockProcessor(r, getSvrAuthCredentials()));
                                    } else {
                                      return Single.just(processor);
                                    }
                                  });
  }

  public @NonNull Single<Boolean> canEnterSkipSmsFlow() {
    if (userSkippedReRegisterFlow) {
      Log.d(TAG, "User skipped re-register flow.");
      return Single.just(false);
    }

    Log.d(TAG, "Querying if user can enter skip SMS flow.");
    return Single.just(hasRecoveryPassword())
                 .flatMap(hasRecoveryPassword -> {
                   Log.i(TAG, "Checking if user has existing recovery password: " + hasRecoveryPassword);
                   if (hasRecoveryPassword) {
                     return Single.just(true);
                   } else {
                     return checkForValidSvrAuthCredentials();
                   }
                 });
  }

  private Single<Boolean> checkForValidSvrAuthCredentials() {
    final List<String> svrAuthTokenList = SignalStore.svr().getSvr2AuthTokens();
    List<String> usernamePasswords = svrAuthTokenList
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
      Log.d(TAG, "No valid SVR tokens in local store.");
      return Single.just(false);
    }

    Log.d(TAG, "Valid tokens in local store, validating with SVR.");
    return registrationRepository.getSvrAuthCredential(getRegistrationData(), usernamePasswords)
                                 .flatMap(p -> {
                                   if (p.hasValidSvr2AuthCredential()) {
                                     Log.d(TAG, "Saving valid SVR2 auth credential.");
                                     setSvrAuthCredentials(new SvrAuthCredentialSet(p.requireSvr2AuthCredential(), null));
                                     return Single.just(true);
                                   } else {
                                     Log.d(TAG, "SVR2 response contained no valid SVR2 auth credentials.");
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

    AppDependencies.getJobManager().runSynchronously(new StorageAccountRestoreJob(), StorageAccountRestoreJob.LIFESPAN);
    stopwatch.split("AccountRestore");

    AppDependencies
        .getJobManager()
        .startChain(new StorageSyncJob())
        .then(new ReclaimUsernameAndLinkJob())
        .enqueueAndBlockUntilCompletion(TimeUnit.SECONDS.toMillis(10));
    stopwatch.split("ContactRestore");

    try {
      RemoteConfig.refreshSync();
    } catch (IOException e) {
      Log.w(TAG, "Failed to refresh flags.", e);
    }
    stopwatch.split("RemoteConfig");

    stopwatch.stop(TAG);
  }

  private boolean hasRecoveryPassword() {
    return getRecoveryPassword() != null && Objects.equals(getRegistrationData().getE164(), SignalStore.account().getE164());
  }

  private static class ReRegistrationData {
    public boolean   canProceed;
    public MasterKey masterKey;

    private ReRegistrationData(boolean canProceed, @Nullable MasterKey masterKey) {
      this.canProceed = canProceed;
      this.masterKey  = masterKey;
    }

    public static ReRegistrationData cannotProceed() {
      return new ReRegistrationData(false, null);
    }

    public static ReRegistrationData canProceed(@NonNull MasterKey masterKey) {
      return new ReRegistrationData(true, masterKey);
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
                                                       new VerifyAccountRepository(AppDependencies.getApplication()),
                                                       new RegistrationRepository(AppDependencies.getApplication())));
    }
  }
}
