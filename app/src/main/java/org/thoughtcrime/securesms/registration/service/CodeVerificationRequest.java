package org.thoughtcrime.securesms.registration.service;

import android.content.Context;
import android.os.AsyncTask;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.zkgroup.profiles.ProfileKey;
import org.thoughtcrime.securesms.AppCapabilities;
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.thoughtcrime.securesms.crypto.PreKeyUtil;
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil;
import org.thoughtcrime.securesms.crypto.SessionUtil;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.jobs.DirectoryRefreshJob;
import org.thoughtcrime.securesms.jobs.RotateCertificateJob;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.pin.PinState;
import org.thoughtcrime.securesms.push.AccountManagerFactory;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.service.DirectoryRefreshListener;
import org.thoughtcrime.securesms.service.RotateSignedPreKeyListener;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.util.KeyHelper;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.KeyBackupSystemNoDataException;
import org.whispersystems.signalservice.api.KbsPinData;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.push.exceptions.RateLimitException;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.internal.contacts.entities.TokenResponse;
import org.whispersystems.signalservice.internal.push.LockedException;
import org.whispersystems.signalservice.internal.push.VerifyAccountResponse;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

public final class CodeVerificationRequest {

  private static final String TAG = Log.tag(CodeVerificationRequest.class);

  private enum Result {
    SUCCESS,
    PIN_LOCKED,
    KBS_WRONG_PIN,
    RATE_LIMITED,
    KBS_ACCOUNT_LOCKED,
    ERROR
  }

  /**
   * Asynchronously verify the account via the code.
   *
   * @param fcmToken         The FCM token for the device.
   * @param code             The code that was delivered to the user.
   * @param pin              The users registration pin.
   * @param callback         Exactly one method on this callback will be called.
   * @param kbsTokenResponse By keeping the token, on failure, a newly returned token will be reused in subsequent pin
   *                         attempts, preventing certain attacks, we can also track the attempts making missing replies easier to spot.
   */
  static void verifyAccount(@NonNull Context context,
                            @NonNull Credentials credentials,
                            @Nullable String fcmToken,
                            @NonNull String code,
                            @Nullable String pin,
                            @Nullable String basicStorageCredentials,
                            @Nullable TokenResponse kbsTokenResponse,
                            @NonNull VerifyCallback callback)
  {
    new AsyncTask<Void, Void, Result>() {

      private volatile LockedException lockedException;
      private volatile TokenResponse   kbsToken;

      @Override
      protected Result doInBackground(Void... voids) {
        final boolean pinSupplied = pin != null;
        final boolean tryKbs      = kbsTokenResponse != null;

        try {
          kbsToken = kbsTokenResponse;
          verifyAccount(context, credentials, code, pin, kbsTokenResponse, basicStorageCredentials, fcmToken);
          return Result.SUCCESS;
        } catch (KeyBackupSystemNoDataException e) {
          Log.w(TAG, "No data found on KBS");
          return Result.KBS_ACCOUNT_LOCKED;
        } catch (KeyBackupSystemWrongPinException e) {
          kbsToken = e.getTokenResponse();
          return Result.KBS_WRONG_PIN;
        } catch (LockedException e) {
          if (pinSupplied && tryKbs) {
            throw new AssertionError("KBS Pin appeared to matched but reg lock still failed!");
          }

          Log.w(TAG, e);
          lockedException = e;
          if (e.getBasicStorageCredentials() != null) {
            try {
              kbsToken = getToken(e.getBasicStorageCredentials());
              if (kbsToken == null || kbsToken.getTries() == 0) {
                return Result.KBS_ACCOUNT_LOCKED;
              }
            } catch (IOException ex) {
              Log.w(TAG, e);
              return Result.ERROR;
            }
          }
          return Result.PIN_LOCKED;
        } catch (RateLimitException e) {
          Log.w(TAG, e);
          return Result.RATE_LIMITED;
        } catch (IOException e) {
          Log.w(TAG, e);
          return Result.ERROR;
        }
      }

      @Override
      protected void onPostExecute(Result result) {
        switch (result) {
          case SUCCESS:
            handleSuccessfulRegistration(context);
            callback.onSuccessfulRegistration();
            break;
          case PIN_LOCKED:
            if (kbsToken != null) {
              if (lockedException.getBasicStorageCredentials() == null) {
                throw new AssertionError("KBS Token set, but no storage credentials supplied.");
              }
              Log.w(TAG, "Reg Locked: V2 pin needed for registration");
              callback.onKbsRegistrationLockPinRequired(lockedException.getTimeRemaining(), kbsToken, lockedException.getBasicStorageCredentials());
            } else {
              Log.w(TAG, "Reg Locked: V1 pin needed for registration");
              callback.onV1RegistrationLockPinRequiredOrIncorrect(lockedException.getTimeRemaining());
            }
            break;
          case RATE_LIMITED:
            callback.onRateLimited();
            break;
          case ERROR:
            callback.onError();
            break;
          case KBS_WRONG_PIN:
            Log.w(TAG, "KBS Pin was wrong");
            callback.onIncorrectKbsRegistrationLockPin(kbsToken);
            break;
          case KBS_ACCOUNT_LOCKED:
            Log.w(TAG, "KBS Account is locked");
            callback.onKbsAccountLocked(lockedException != null ? lockedException.getTimeRemaining() : null);
            break;
        }
      }
    }.executeOnExecutor(SignalExecutors.UNBOUNDED);
  }

  private static TokenResponse getToken(@Nullable String basicStorageCredentials) throws IOException {
    if (basicStorageCredentials == null) return null;
    return ApplicationDependencies.getKeyBackupService().getToken(basicStorageCredentials);
  }

  private static void handleSuccessfulRegistration(@NonNull Context context) {
    JobManager jobManager = ApplicationDependencies.getJobManager();
    jobManager.add(new DirectoryRefreshJob(false));
    jobManager.add(new RotateCertificateJob(context));

    DirectoryRefreshListener.schedule(context);
    RotateSignedPreKeyListener.schedule(context);
  }

  private static void verifyAccount(@NonNull Context context,
                                    @NonNull Credentials credentials,
                                    @NonNull String code,
                                    @Nullable String pin,
                                    @Nullable TokenResponse kbsTokenResponse,
                                    @Nullable String kbsStorageCredentials,
                                    @Nullable String fcmToken)
    throws IOException, KeyBackupSystemWrongPinException, KeyBackupSystemNoDataException
  {
    boolean    isV2RegistrationLock        = kbsTokenResponse != null;
    int        registrationId              = KeyHelper.generateRegistrationId(false);
    boolean    universalUnidentifiedAccess = TextSecurePreferences.isUniversalUnidentifiedAccess(context);
    ProfileKey profileKey                  = findExistingProfileKey(context, credentials.getE164number());

    if (profileKey == null) {
      profileKey = ProfileKeyUtil.createNew();
      Log.i(TAG, "No profile key found, created a new one");
    }

    byte[] unidentifiedAccessKey = UnidentifiedAccess.deriveAccessKeyFrom(profileKey);

    TextSecurePreferences.setLocalRegistrationId(context, registrationId);
    SessionUtil.archiveAllSessions(context);

    SignalServiceAccountManager accountManager     = AccountManagerFactory.createUnauthenticated(context, credentials.getE164number(), credentials.getPassword());
    KbsPinData                  kbsData            = isV2RegistrationLock ? PinState.restoreMasterKey(pin, kbsStorageCredentials, kbsTokenResponse) : null;
    String                      registrationLockV2 = kbsData != null ? kbsData.getMasterKey().deriveRegistrationLock() : null;
    String                      registrationLockV1 = isV2RegistrationLock ? null : pin;
    boolean                     hasFcm             = fcmToken != null;

    Log.i(TAG, "Calling verifyAccountWithCode(): reglockV1? " + !TextUtils.isEmpty(registrationLockV1) + ", reglockV2? " + !TextUtils.isEmpty(registrationLockV2));

    VerifyAccountResponse response = accountManager.verifyAccountWithCode(code,
                                                                          null,
                                                                          registrationId,
                                                                          !hasFcm,
                                                                          registrationLockV1,
                                                                          registrationLockV2,
                                                                          unidentifiedAccessKey,
                                                                          universalUnidentifiedAccess,
                                                                          AppCapabilities.getCapabilities(true));

    UUID    uuid   = UuidUtil.parseOrThrow(response.getUuid());
    boolean hasPin = response.isStorageCapable();

    IdentityKeyPair    identityKey  = IdentityKeyUtil.getIdentityKeyPair(context);
    List<PreKeyRecord> records      = PreKeyUtil.generatePreKeys(context);
    SignedPreKeyRecord signedPreKey = PreKeyUtil.generateSignedPreKey(context, identityKey, true);

    accountManager = AccountManagerFactory.createAuthenticated(context, uuid, credentials.getE164number(), credentials.getPassword());
    accountManager.setPreKeys(identityKey.getPublicKey(), signedPreKey, records);

    if (hasFcm) {
      accountManager.setGcmId(Optional.fromNullable(fcmToken));
    }

    RecipientDatabase recipientDatabase = DatabaseFactory.getRecipientDatabase(context);
    RecipientId       selfId            = recipientDatabase.getOrInsertFromE164(credentials.getE164number());

    recipientDatabase.setProfileSharing(selfId, true);
    recipientDatabase.markRegistered(selfId, uuid);

    TextSecurePreferences.setLocalNumber(context, credentials.getE164number());
    TextSecurePreferences.setLocalUuid(context, uuid);
    recipientDatabase.setProfileKey(selfId, profileKey);
    ApplicationDependencies.getRecipientCache().clearSelf();

    TextSecurePreferences.setFcmToken(context, fcmToken);
    TextSecurePreferences.setFcmDisabled(context, !hasFcm);
    TextSecurePreferences.setWebsocketRegistered(context, true);

    DatabaseFactory.getIdentityDatabase(context)
                   .saveIdentity(selfId,
                                 identityKey.getPublicKey(), IdentityDatabase.VerifiedStatus.VERIFIED,
                                 true, System.currentTimeMillis(), true);

    TextSecurePreferences.setVerifying(context, false);
    TextSecurePreferences.setPushRegistered(context, true);
    TextSecurePreferences.setPushServerPassword(context, credentials.getPassword());
    TextSecurePreferences.setSignedPreKeyRegistered(context, true);
    TextSecurePreferences.setPromptedPushRegistration(context, true);
    TextSecurePreferences.setUnauthorizedReceived(context, false);

    PinState.onRegistration(context, kbsData, pin, hasPin);
  }

  private static @Nullable ProfileKey findExistingProfileKey(@NonNull Context context, @NonNull String e164number) {
    RecipientDatabase     recipientDatabase = DatabaseFactory.getRecipientDatabase(context);
    Optional<RecipientId> recipient         = recipientDatabase.getByE164(e164number);

    if (recipient.isPresent()) {
      return ProfileKeyUtil.profileKeyOrNull(Recipient.resolved(recipient.get()).getProfileKey());
    }

    return null;
  }

  public interface VerifyCallback {

    void onSuccessfulRegistration();

    /**
     * The account is locked with a V1 (non-KBS) pin.
     *
     * @param timeRemaining Time until pin expires and number can be reused.
     */
    void onV1RegistrationLockPinRequiredOrIncorrect(long timeRemaining);

    /**
     * The account is locked with a V2 (KBS) pin. Called before any user pin guesses.
     */
    void onKbsRegistrationLockPinRequired(long timeRemaining, @NonNull TokenResponse kbsTokenResponse, @NonNull String kbsStorageCredentials);

    /**
     * The account is locked with a V2 (KBS) pin. Called after a user pin guess.
     * <p>
     * i.e. an attempt has likely been used.
     */
    void onIncorrectKbsRegistrationLockPin(@NonNull TokenResponse kbsTokenResponse);

    /**
     * V2 (KBS) pin is set, but there is no data on KBS.
     *
     * @param timeRemaining Non-null if known.
     */
    void onKbsAccountLocked(@Nullable Long timeRemaining);

    void onRateLimited();

    void onError();
  }
}
