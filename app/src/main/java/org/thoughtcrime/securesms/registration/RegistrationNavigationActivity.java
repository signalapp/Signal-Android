package org.thoughtcrime.securesms.registration;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.phone.SmsRetriever;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.Status;

import org.greenrobot.eventbus.EventBus;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.service.VerificationCodeParser;
import org.whispersystems.libsignal.util.guava.Optional;

public final class RegistrationNavigationActivity extends AppCompatActivity {

  private static final String TAG = Log.tag(RegistrationNavigationActivity.class);

  public static final String RE_REGISTRATION_EXTRA = "re_registration";

  private SmsRetrieverReceiver smsRetrieverReceiver;

  public static Intent newIntentForNewRegistration(@NonNull Context context) {
    Intent intent = new Intent(context, RegistrationNavigationActivity.class);
    intent.putExtra(RE_REGISTRATION_EXTRA, false);
    return intent;
  }

  public static Intent newIntentForReRegistration(@NonNull Context context) {
    Intent intent = new Intent(context, RegistrationNavigationActivity.class);
    intent.putExtra(RE_REGISTRATION_EXTRA, true);
    return intent;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_registration_navigation);
    initializeChallengeListener();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    shutdownChallengeListener();
  }

  private void initializeChallengeListener() {
    smsRetrieverReceiver = new SmsRetrieverReceiver();

    registerReceiver(smsRetrieverReceiver, new IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION));
  }

  private void shutdownChallengeListener() {
    if (smsRetrieverReceiver != null) {
      unregisterReceiver(smsRetrieverReceiver);
      smsRetrieverReceiver = null;
    }
  }

  private class SmsRetrieverReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
      Log.i(TAG, "SmsRetrieverReceiver received a broadcast...");

      if (SmsRetriever.SMS_RETRIEVED_ACTION.equals(intent.getAction())) {
        Bundle extras = intent.getExtras();
        Status status = (Status) extras.get(SmsRetriever.EXTRA_STATUS);

        switch (status.getStatusCode()) {
          case CommonStatusCodes.SUCCESS:
            RegistrationSmsHandler.handleRegistrationSms(context, (String) extras.get(SmsRetriever.EXTRA_SMS_MESSAGE));
            break;
          case CommonStatusCodes.TIMEOUT:
            Log.w(TAG, "Hit a timeout waiting for the SMS to arrive.");
            break;
        }
      } else {
        Log.w(TAG, "SmsRetrieverReceiver received the wrong action?");
      }
    }
  }
}
