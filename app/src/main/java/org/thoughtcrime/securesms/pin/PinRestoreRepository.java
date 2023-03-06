package org.thoughtcrime.securesms.pin;

import androidx.annotation.NonNull;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobs.NewRegistrationUsernameSyncJob;
import org.thoughtcrime.securesms.jobs.StorageAccountRestoreJob;
import org.thoughtcrime.securesms.jobs.StorageSyncJob;
import org.signal.core.util.Stopwatch;
import org.whispersystems.signalservice.api.KbsPinData;
import org.whispersystems.signalservice.api.KeyBackupSystemNoDataException;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public class PinRestoreRepository {

  private static final String TAG = Log.tag(PinRestoreRepository.class);

  private final Executor executor = SignalExecutors.UNBOUNDED;

  void submitPin(@NonNull String pin, @NonNull TokenData tokenData, @NonNull Callback<PinResultData> callback) {
    executor.execute(() -> {
      try {
        Stopwatch stopwatch = new Stopwatch("PinSubmission");

        KbsPinData kbsData = KbsRepository.restoreMasterKey(pin, tokenData.getEnclave(), tokenData.getBasicAuth(), tokenData.getTokenResponse());
        PinState.onSignalPinRestore(ApplicationDependencies.getApplication(), Objects.requireNonNull(kbsData), pin);
        stopwatch.split("MasterKey");

        ApplicationDependencies.getJobManager().runSynchronously(new StorageAccountRestoreJob(), StorageAccountRestoreJob.LIFESPAN);
        stopwatch.split("AccountRestore");

        ApplicationDependencies
            .getJobManager()
            .startChain(new StorageSyncJob())
            .then(new NewRegistrationUsernameSyncJob())
            .enqueueAndBlockUntilCompletion(TimeUnit.SECONDS.toMillis(10));
        stopwatch.split("ContactRestore");

        stopwatch.stop(TAG);

        callback.onComplete(new PinResultData(PinResult.SUCCESS, tokenData));
      } catch (IOException e) {
        callback.onComplete(new PinResultData(PinResult.NETWORK_ERROR, tokenData));
      } catch (KeyBackupSystemNoDataException e) {
        callback.onComplete(new PinResultData(PinResult.LOCKED, tokenData));
      } catch (KeyBackupSystemWrongPinException e) {
        callback.onComplete(new PinResultData(PinResult.INCORRECT, TokenData.withResponse(tokenData, e.getTokenResponse())));
      }
    });
  }

  interface Callback<T> {
    void onComplete(@NonNull T value);
  }

  static class PinResultData {
    private final PinResult result;
    private final TokenData tokenData;

    PinResultData(@NonNull PinResult result, @NonNull TokenData tokenData) {
      this.result    = result;
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
