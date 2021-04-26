package org.thoughtcrime.securesms.pin;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.KbsEnclave;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobs.StorageAccountRestoreJob;
import org.thoughtcrime.securesms.jobs.StorageSyncJob;
import org.thoughtcrime.securesms.registration.service.KeyBackupSystemWrongPinException;
import org.thoughtcrime.securesms.util.Stopwatch;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.KbsPinData;
import org.whispersystems.signalservice.api.KeyBackupService;
import org.whispersystems.signalservice.api.KeyBackupSystemNoDataException;
import org.whispersystems.signalservice.internal.contacts.entities.TokenResponse;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public class PinRestoreRepository {

  private static final String TAG = Log.tag(PinRestoreRepository.class);

  private final Executor executor = SignalExecutors.UNBOUNDED;

  void getToken(@NonNull Callback<Optional<TokenData>> callback) {
    executor.execute(() -> {
      try {
        callback.onComplete(Optional.fromNullable(getTokenSync(null)));
      } catch (IOException e) {
        callback.onComplete(Optional.absent());
      }
    });
  }

  /**
   * @param authorization If this is being called before the user is registered (i.e. as part of
   *                      reglock), you must pass in an authorization token that can be used to
   *                      retrieve a backup. Otherwise, pass in null and we'll fetch one.
   */
  public @NonNull TokenData getTokenSync(@Nullable String authorization) throws IOException {
    TokenData firstKnownTokenData = null;

    for (KbsEnclave enclave : KbsEnclaves.all()) {
      KeyBackupService kbs = ApplicationDependencies.getKeyBackupService(enclave);

      authorization = authorization == null ? kbs.getAuthorization() : authorization;

      TokenResponse token     = kbs.getToken(authorization);
      TokenData     tokenData = new TokenData(enclave, authorization, token);

      if (tokenData.getTriesRemaining() > 0) {
        Log.i(TAG, "Found data! " + enclave.getEnclaveName());
        return tokenData;
      } else if (firstKnownTokenData == null) {
        Log.i(TAG, "No data, but storing as the first response. " + enclave.getEnclaveName());
        firstKnownTokenData = tokenData;
      } else {
        Log.i(TAG, "No data, and we already have a 'first response'. " + enclave.getEnclaveName());
      }
    }

    return Objects.requireNonNull(firstKnownTokenData);
  }

  void submitPin(@NonNull String pin, @NonNull TokenData tokenData, @NonNull Callback<PinResultData> callback) {
    executor.execute(() -> {
      try {
        Stopwatch stopwatch = new Stopwatch("PinSubmission");

        KbsPinData kbsData = PinState.restoreMasterKey(pin, tokenData.getEnclave(), tokenData.getBasicAuth(), tokenData.getTokenResponse());
        PinState.onSignalPinRestore(ApplicationDependencies.getApplication(), Objects.requireNonNull(kbsData), pin);
        stopwatch.split("MasterKey");

        ApplicationDependencies.getJobManager().runSynchronously(new StorageAccountRestoreJob(), StorageAccountRestoreJob.LIFESPAN);
        stopwatch.split("AccountRestore");

        ApplicationDependencies.getJobManager().runSynchronously(new StorageSyncJob(), TimeUnit.SECONDS.toMillis(10));
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

  public static class TokenData implements Parcelable {
    private final KbsEnclave    enclave;
    private final String        basicAuth;
    private final TokenResponse tokenResponse;

    TokenData(@NonNull KbsEnclave enclave, @NonNull String basicAuth, @NonNull TokenResponse tokenResponse) {
      this.enclave       = enclave;
      this.basicAuth     = basicAuth;
      this.tokenResponse = tokenResponse;
    }

    private TokenData(Parcel in) {
      //noinspection ConstantConditions
      this.enclave   = new KbsEnclave(in.readString(), in.readString(), in.readString());
      this.basicAuth = in.readString();

      byte[] backupId = new byte[0];
      byte[] token    = new byte[0];

      in.readByteArray(backupId);
      in.readByteArray(token);

      this.tokenResponse = new TokenResponse(backupId, token, in.readInt());
    }

    public static @NonNull TokenData withResponse(@NonNull TokenData data, @NonNull TokenResponse response) {
      return new TokenData(data.getEnclave(), data.getBasicAuth(), response);
    }

    public int getTriesRemaining() {
      return tokenResponse.getTries();
    }

    public @NonNull String getBasicAuth() {
      return basicAuth;
    }

    public @NonNull TokenResponse getTokenResponse() {
      return tokenResponse;
    }

    public @NonNull KbsEnclave getEnclave() {
      return enclave;
    }

    @Override
    public int describeContents() {
      return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
      dest.writeString(enclave.getEnclaveName());
      dest.writeString(enclave.getServiceId());
      dest.writeString(enclave.getMrEnclave());

      dest.writeString(basicAuth);

      dest.writeByteArray(tokenResponse.getBackupId());
      dest.writeByteArray(tokenResponse.getToken());
      dest.writeInt(tokenResponse.getTries());
    }

    public static final Creator<TokenData> CREATOR = new Creator<TokenData>() {
      @Override
      public TokenData createFromParcel(Parcel in) {
        return new TokenData(in);
      }

      @Override
      public TokenData[] newArray(int size) {
        return new TokenData[size];
      }
    };

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
