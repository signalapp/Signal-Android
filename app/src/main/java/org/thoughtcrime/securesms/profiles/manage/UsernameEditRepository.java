package org.thoughtcrime.securesms.profiles.manage;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.signal.core.util.Result;
import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.push.exceptions.UsernameIsNotReservedException;
import org.whispersystems.signalservice.api.push.exceptions.UsernameMalformedException;
import org.whispersystems.signalservice.api.push.exceptions.UsernameTakenException;
import org.whispersystems.signalservice.internal.push.ReserveUsernameResponse;

import java.io.IOException;
import java.util.concurrent.Executor;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

class UsernameEditRepository {

  private static final String TAG = Log.tag(UsernameEditRepository.class);

  private final SignalServiceAccountManager accountManager;

  UsernameEditRepository() {
    this.accountManager = ApplicationDependencies.getSignalServiceAccountManager();
  }

  @NonNull Single<Result<ReserveUsernameResponse, UsernameSetResult>> reserveUsername(@NonNull String nickname) {
    return Single.fromCallable(() -> reserveUsernameInternal(nickname)).subscribeOn(Schedulers.io());
  }

  @NonNull Single<UsernameSetResult> confirmUsername(@NonNull ReserveUsernameResponse reserveUsernameResponse) {
    return Single.fromCallable(() -> confirmUsernameInternal(reserveUsernameResponse)).subscribeOn(Schedulers.io());
  }

  @NonNull Single<UsernameDeleteResult> deleteUsername() {
    return Single.fromCallable(this::deleteUsernameInternal).subscribeOn(Schedulers.io());
  }

  @WorkerThread
  private @NonNull Result<ReserveUsernameResponse, UsernameSetResult> reserveUsernameInternal(@NonNull String nickname) {
    try {
      ReserveUsernameResponse username = accountManager.reserveUsername(nickname);
      Log.i(TAG, "[reserveUsername] Successfully reserved username.");
      return Result.success(username);
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
  private @NonNull UsernameSetResult confirmUsernameInternal(@NonNull ReserveUsernameResponse reserveUsernameResponse) {
    try {
      accountManager.confirmUsername(reserveUsernameResponse);
      SignalDatabase.recipients().setUsername(Recipient.self().getId(), reserveUsernameResponse.getUsername());
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
      Log.i(TAG, "[deleteUsername] Successfully deleted the username.");
      return UsernameDeleteResult.SUCCESS;
    } catch (IOException e) {
      Log.w(TAG, "[deleteUsername] Generic network exception.", e);
      return UsernameDeleteResult.NETWORK_ERROR;
    }
  }

  enum UsernameSetResult {
    SUCCESS, USERNAME_UNAVAILABLE, USERNAME_INVALID, NETWORK_ERROR
  }

  enum UsernameDeleteResult {
    SUCCESS, NETWORK_ERROR
  }

  interface Callback<E> {
    void onComplete(E result);
  }
}
