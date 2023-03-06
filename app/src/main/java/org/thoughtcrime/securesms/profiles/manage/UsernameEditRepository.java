package org.thoughtcrime.securesms.profiles.manage;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.signal.core.util.Result;
import org.signal.core.util.logging.Log;
import org.signal.libsignal.usernames.BaseUsernameException;
import org.signal.libsignal.usernames.Username;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.UsernameUtil;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.push.exceptions.UsernameIsNotReservedException;
import org.whispersystems.signalservice.api.push.exceptions.UsernameMalformedException;
import org.whispersystems.signalservice.api.push.exceptions.UsernameTakenException;
import org.whispersystems.signalservice.internal.push.ReserveUsernameResponse;
import org.whispersystems.util.Base64UrlSafe;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

class UsernameEditRepository {

  private static final String TAG = Log.tag(UsernameEditRepository.class);

  private final SignalServiceAccountManager accountManager;

  UsernameEditRepository() {
    this.accountManager = ApplicationDependencies.getSignalServiceAccountManager();
  }

  @NonNull Single<Result<UsernameState.Reserved, UsernameSetResult>> reserveUsername(@NonNull String nickname) {
    return Single.fromCallable(() -> reserveUsernameInternal(nickname)).subscribeOn(Schedulers.io());
  }

  @NonNull Single<UsernameSetResult> confirmUsername(@NonNull UsernameState.Reserved reserved) {
    return Single.fromCallable(() -> confirmUsernameInternal(reserved)).subscribeOn(Schedulers.io());
  }

  @NonNull Single<UsernameDeleteResult> deleteUsername() {
    return Single.fromCallable(this::deleteUsernameInternal).subscribeOn(Schedulers.io());
  }

  @WorkerThread
  private @NonNull Result<UsernameState.Reserved, UsernameSetResult> reserveUsernameInternal(@NonNull String nickname) {
    try {
      List<String> candidates = Username.generateCandidates(nickname, UsernameUtil.MIN_LENGTH, UsernameUtil.MAX_LENGTH);
      List<String> hashes     = new ArrayList<>();

      for (String candidate : candidates) {
        byte[] hash = Username.hash(candidate);
        hashes.add(Base64UrlSafe.encodeBytesWithoutPadding(hash));
      }

      ReserveUsernameResponse response  = accountManager.reserveUsername(hashes);
      int                     hashIndex = hashes.indexOf(response.getUsernameHash());
      if (hashIndex == -1) {
        Log.w(TAG, "[reserveUsername] The response hash could not be found in our set of hashes.");
        return Result.failure(UsernameSetResult.CANDIDATE_GENERATION_ERROR);
      }

      Log.i(TAG, "[reserveUsername] Successfully reserved username.");
      return Result.success(new UsernameState.Reserved(candidates.get(hashIndex), response));
    } catch (BaseUsernameException e) {
      Log.w(TAG, "[reserveUsername] An error occurred while generating candidates.");
      return Result.failure(UsernameSetResult.CANDIDATE_GENERATION_ERROR);
    } catch (UsernameTakenException e) {
      Log.w(TAG, "[reserveUsername] Username taken.");
      return Result.failure(UsernameSetResult.USERNAME_UNAVAILABLE);
    } catch (UsernameMalformedException e) {
      Log.w(TAG, "[reserveUsername] Username malformed.");
      return Result.failure(UsernameSetResult.USERNAME_INVALID);
    } catch (IOException e) {
      Log.w(TAG, "[reserveUsername] Generic network exception.", e);
      return Result.failure(UsernameSetResult.NETWORK_ERROR);
    }
  }

  @WorkerThread
  private @NonNull UsernameSetResult confirmUsernameInternal(@NonNull UsernameState.Reserved reserved) {
    try {
      accountManager.confirmUsername(reserved.getUsername(), reserved.getReserveUsernameResponse());
      SignalDatabase.recipients().setUsername(Recipient.self().getId(), reserved.getUsername());
      SignalStore.phoneNumberPrivacy().clearUsernameOutOfSync();
      Log.i(TAG, "[confirmUsername] Successfully reserved username.");
      return UsernameSetResult.SUCCESS;
    } catch (UsernameTakenException e) {
      Log.w(TAG, "[confirmUsername] Username gone.");
      return UsernameSetResult.USERNAME_UNAVAILABLE;
    } catch (UsernameIsNotReservedException e) {
      Log.w(TAG, "[confirmUsername] Username was not reserved.");
      return UsernameSetResult.USERNAME_INVALID;
    } catch (IOException e) {
      Log.w(TAG, "[confirmUsername] Generic network exception.", e);
      return UsernameSetResult.NETWORK_ERROR;
    }
  }

  @WorkerThread
  private @NonNull UsernameDeleteResult deleteUsernameInternal() {
    try {
      accountManager.deleteUsername();
      SignalDatabase.recipients().setUsername(Recipient.self().getId(), null);
      SignalStore.phoneNumberPrivacy().clearUsernameOutOfSync();
      Log.i(TAG, "[deleteUsername] Successfully deleted the username.");
      return UsernameDeleteResult.SUCCESS;
    } catch (IOException e) {
      Log.w(TAG, "[deleteUsername] Generic network exception.", e);
      return UsernameDeleteResult.NETWORK_ERROR;
    }
  }

  enum UsernameSetResult {
    SUCCESS, USERNAME_UNAVAILABLE, USERNAME_INVALID, NETWORK_ERROR, CANDIDATE_GENERATION_ERROR
  }

  enum UsernameDeleteResult {
    SUCCESS, NETWORK_ERROR
  }

  interface Callback<E> {
    void onComplete(E result);
  }
}
