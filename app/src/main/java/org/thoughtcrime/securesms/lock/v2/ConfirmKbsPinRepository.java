package org.thoughtcrime.securesms.lock.v2;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;

import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.pin.PinState;
import org.thoughtcrime.securesms.util.concurrent.SimpleTask;
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
        PinState.onPinChangedOrCreated(context, pinValue, keyboard);
        Log.i(TAG, "Pin set on KBS");

        return PinSetResult.SUCCESS;
      } catch (IOException | UnauthenticatedResponseException e) {
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
