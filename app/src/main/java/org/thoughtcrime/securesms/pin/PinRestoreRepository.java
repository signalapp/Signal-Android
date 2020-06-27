package org.thoughtcrime.securesms.pin;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobs.StorageAccountRestoreJob;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.registration.service.KeyBackupSystemWrongPinException;
import org.thoughtcrime.securesms.util.Stopwatch;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.KbsPinData;
import org.whispersystems.signalservice.api.KeyBackupService;
import org.whispersystems.signalservice.api.KeyBackupSystemNoDataException;
import org.whispersystems.signalservice.internal.contacts.entities.TokenResponse;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.Executor;

class PinRestoreRepository {

  private static final String TAG = Log.tag(PinRestoreRepository.class);

  private final Executor         executor = SignalExecutors.UNBOUNDED;
  private final KeyBackupService kbs      = ApplicationDependencies.getKeyBackupService();

  void getToken(@NonNull Callback<Optional<TokenData>> callback) {
    executor.execute(() -> {
      try {
        String        authorization = kbs.getAuthorization();
        TokenResponse token         = kbs.getToken(authorization);
        TokenData     tokenData     = new TokenData(authorization, token);
        callback.onComplete(Optional.of(tokenData));
      } catch (IOException e) {
        callback.onComplete(Optional.absent());
      }
    });
  }

  void submitPin(@NonNull String pin, @NonNull TokenData tokenData, @NonNull Callback<PinResultData> callback) {
    executor.execute(() -> {
      try {
        Stopwatch stopwatch = new Stopwatch("PinSubmission");

        KbsPinData kbsData = PinState.restoreMasterKey(pin, tokenData.basicAuth, tokenData.tokenResponse);
        PinState.onSignalPinRestore(ApplicationDependencies.getApplication(), Objects.requireNonNull(kbsData), pin);
        stopwatch.split("MasterKey");

        ApplicationDependencies.getJobManager().runSynchronously(new StorageAccountRestoreJob(), StorageAccountRestoreJob.LIFESPAN);
        stopwatch.split("AccountRestore");

        stopwatch.stop(TAG);

        callback.onComplete(new PinResultData(PinResult.SUCCESS, tokenData));
      } catch (IOException e) {
        callback.onComplete(new PinResultData(PinResult.NETWORK_ERROR, tokenData));
      } catch (KeyBackupSystemNoDataException e) {
        callback.onComplete(new PinResultData(PinResult.LOCKED, tokenData));
      } catch (KeyBackupSystemWrongPinException e) {
        callback.onComplete(new PinResultData(PinResult.INCORRECT, new TokenData(tokenData.basicAuth, e.getTokenResponse())));
      }
    });
  }

  interface Callback<T> {
    void onComplete(@NonNull T value);
  }

  static class TokenData {
    private final String        basicAuth;
    private final TokenResponse tokenResponse;

    TokenData(@NonNull String basicAuth, @NonNull TokenResponse tokenResponse) {
      this.basicAuth     = basicAuth;
      this.tokenResponse = tokenResponse;
    }

    int getTriesRemaining() {
      return tokenResponse.getTries();
    }
  }

  static class PinResultData {
    private final PinResult result;
    private final TokenData tokenData;

    PinResultData(@NonNull PinResult result, @NonNull TokenData tokenData) {
      this.result = result;
      this.tokenData = tokenData;
    }

    public @NonNull PinResult getResult() {
      return result;
    }

    public @NonNull TokenData getTokenData() {
      return tokenData;
    }
  }

  enum PinResult {
    SUCCESS, INCORRECT, LOCKED, NETWORK_ERROR
  }
}
