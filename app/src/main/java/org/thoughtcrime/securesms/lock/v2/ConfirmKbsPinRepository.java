package org.thoughtcrime.securesms.lock.v2;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;

import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.keyvalue.KbsValues;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.lock.PinHashing;
import org.thoughtcrime.securesms.lock.RegistrationLockReminders;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.megaphone.Megaphones;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.concurrent.SimpleTask;
import org.whispersystems.signalservice.api.KeyBackupService;
import org.whispersystems.signalservice.api.KeyBackupServicePinException;
import org.whispersystems.signalservice.api.KeyBackupSystemNoDataException;
import org.whispersystems.signalservice.api.RegistrationLockData;
import org.whispersystems.signalservice.api.kbs.HashedPin;
import org.whispersystems.signalservice.api.kbs.MasterKey;
import org.whispersystems.signalservice.internal.contacts.crypto.UnauthenticatedResponseException;

import java.io.IOException;

final class ConfirmKbsPinRepository {

  private static final String TAG = Log.tag(ConfirmKbsPinRepository.class);

  void setPin(@NonNull KbsPin kbsPin, @NonNull PinKeyboardType keyboard, @NonNull Consumer<PinSetResult> resultConsumer) {

    Context context  = ApplicationDependencies.getApplication();
    String  pinValue = kbsPin.toString();

    SimpleTask.run(() -> {
      try {
        Log.i(TAG, "Setting pin on KBS");

        KbsValues                         kbsValues        = SignalStore.kbsValues();
        MasterKey                         masterKey        = kbsValues.getOrCreateMasterKey();
        KeyBackupService                  keyBackupService = ApplicationDependencies.getKeyBackupService();
        KeyBackupService.PinChangeSession pinChangeSession = keyBackupService.newPinChangeSession();
        HashedPin                         hashedPin        = PinHashing.hashPin(pinValue, pinChangeSession);
        RegistrationLockData              kbsData          = pinChangeSession.setPin(hashedPin, masterKey);
        RegistrationLockData              restoredData     = keyBackupService.newRestoreSession(kbsData.getTokenResponse())
                                                                             .restorePin(hashedPin);

        if (!restoredData.getMasterKey().equals(masterKey)) {
          throw new AssertionError("Failed to set the pin correctly");
        } else {
          Log.i(TAG, "Set and retrieved pin on KBS successfully");
        }

        kbsValues.setRegistrationLockMasterKey(restoredData, PinHashing.localPinHash(pinValue));
        TextSecurePreferences.clearOldRegistrationLockPin(context);
        TextSecurePreferences.setRegistrationLockLastReminderTime(context, System.currentTimeMillis());
        TextSecurePreferences.setRegistrationLockNextReminderInterval(context, RegistrationLockReminders.INITIAL_INTERVAL);
        SignalStore.kbsValues().setKeyboardType(keyboard);
        ApplicationDependencies.getMegaphoneRepository().markFinished(Megaphones.Event.PINS_FOR_ALL);

        return PinSetResult.SUCCESS;
      } catch (IOException | UnauthenticatedResponseException | KeyBackupServicePinException | KeyBackupSystemNoDataException e) {
        Log.w(TAG, e);
        return PinSetResult.FAILURE;
      }
    }, resultConsumer::accept);
  }

  enum PinSetResult {
    SUCCESS,
    FAILURE
  }
}
