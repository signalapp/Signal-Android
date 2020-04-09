package org.thoughtcrime.securesms.registration;

import android.content.Context;

import androidx.annotation.NonNull;

import org.greenrobot.eventbus.EventBus;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.service.VerificationCodeParser;
import org.whispersystems.libsignal.util.guava.Optional;

/**
 * Handle a received registration SMS.
 * This SMS could have been received by either the RegistrationNavigationActivity or a SmsReceiveJob
 */
public class RegistrationSmsHandler {
    private static final String TAG = Log.tag(RegistrationSmsHandler.class);

    public static void handleRegistrationSms(Context context, String msgContent){
        Optional<String> code = VerificationCodeParser.parse(context, msgContent);
        if (code.isPresent()) {
            Log.i(TAG, "Received verification code.");
            handleVerificationCodeReceived(code.get());
        } else {
            Log.w(TAG, "Could not parse verification code.");
        }
    }

    private static void handleVerificationCodeReceived(@NonNull String code) {
        EventBus.getDefault().post(new ReceivedSmsEvent(code));
    }
}
