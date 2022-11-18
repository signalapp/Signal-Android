package org.thoughtcrime.securesms.registration;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.google.android.gms.auth.api.phone.SmsRetriever;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.Status;

import org.greenrobot.eventbus.EventBus;
import org.signal.core.util.logging.Log;
import androidx.fragment.app.Fragment;

import org.thoughtcrime.securesms.DisclaimerFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.TermsFragment;
import org.thoughtcrime.securesms.registration.fragments.CaptchaFragment;
import org.thoughtcrime.securesms.registration.fragments.EnterCodeFragment;
import org.thoughtcrime.securesms.registration.fragments.WelcomeFragment;
import org.thoughtcrime.securesms.service.VerificationCodeParser;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.whispersystems.libsignal.util.guava.Optional;

public final class RegistrationNavigationActivity extends AppCompatActivity {

  private static final String TAG = Log.tag(RegistrationNavigationActivity.class);

  public static final String RE_REGISTRATION_EXTRA = "re_registration";

  private SmsRetrieverReceiver smsRetrieverReceiver;

  /**
   */
  public static Intent newIntentForNewRegistration(@NonNull Context context, @Nullable Intent originalIntent) {
    Intent intent = new Intent(context, RegistrationNavigationActivity.class);
    intent.putExtra(RE_REGISTRATION_EXTRA, false);

    if (originalIntent != null) {
      intent.setData(originalIntent.getData());
    }

    return intent;
  }

  public static Intent newIntentForReRegistration(@NonNull Context context) {
    Intent intent = new Intent(context, RegistrationNavigationActivity.class);
    intent.putExtra(RE_REGISTRATION_EXTRA, true);
    return intent;
  }

  @Override
  protected void attachBaseContext(@NonNull Context newBase) {
    getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_NO);
    super.attachBaseContext(newBase);
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_registration_navigation);
    initializeChallengeListener();

    if (getIntent() != null && getIntent().getData() != null) {
      CommunicationActions.handlePotentialProxyLinkUrl(this, getIntent().getDataString());
    }
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);

    if (intent.getData() != null) {
      CommunicationActions.handlePotentialProxyLinkUrl(this, intent.getDataString());
    }
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    shutdownChallengeListener();
  }

  @Override
  public void onBackPressed() {
    super.onBackPressed();
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    Fragment navFragment = getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
    if (navFragment == null) {
      return false;
    }
    Fragment fragment = navFragment.getChildFragmentManager().getPrimaryNavigationFragment();
    int code = event.getKeyCode();

    switch (code) {
      case KeyEvent.KEYCODE_2:
      case KeyEvent.KEYCODE_4:
      case KeyEvent.KEYCODE_6:
      case KeyEvent.KEYCODE_8:
      case KeyEvent.KEYCODE_5:
      case KeyEvent.KEYCODE_0:
        if (fragment instanceof CaptchaFragment) {
          if (event.getAction() == KeyEvent.ACTION_DOWN) {
            ((CaptchaFragment) fragment).onKeyDown(code);
          }
        }
        break;
      case KeyEvent.KEYCODE_BACK:
        if (fragment instanceof EnterCodeFragment) {
          if (event.getAction() == KeyEvent.ACTION_DOWN) {
            return ((EnterCodeFragment) fragment).onKeyDown(code);
          }
        }else if(fragment instanceof WelcomeFragment){
          if (event.getAction() == KeyEvent.ACTION_DOWN) {
            return ((WelcomeFragment) fragment).onKeyDown(code);
          }
        }else if (fragment instanceof DisclaimerFragment) {
          if (event.getAction() == KeyEvent.ACTION_DOWN) {
            return ((DisclaimerFragment) fragment).onKeyDown(code);
          }
        }else if (fragment instanceof TermsFragment){
          if (event.getAction() == KeyEvent.ACTION_DOWN){
            return ((TermsFragment) fragment).onKeyDown(code);
          }
        }
        break;
      case KeyEvent.KEYCODE_DPAD_DOWN:
        if (fragment instanceof DisclaimerFragment){
          ((DisclaimerFragment)fragment).onKeyDown();
        }
        if (fragment instanceof TermsFragment){
          ((TermsFragment)fragment).onKeyDown();
        }
        break;
      case KeyEvent.KEYCODE_DPAD_UP:
        if (fragment instanceof DisclaimerFragment){
          ((DisclaimerFragment)fragment).onKeyUp();
        }
        if (fragment instanceof TermsFragment){
          ((TermsFragment)fragment).onKeyUp();
        }
        break;
    }
    return super.dispatchKeyEvent(event);
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
            Optional<String> code = VerificationCodeParser.parse((String) extras.get(SmsRetriever.EXTRA_SMS_MESSAGE));
            if (code.isPresent()) {
              Log.i(TAG, "Received verification code.");
              handleVerificationCodeReceived(code.get());
            } else {
              Log.w(TAG, "Could not parse verification code.");
            }
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

  private void handleVerificationCodeReceived(@NonNull String code) {
    EventBus.getDefault().post(new ReceivedSmsEvent(code));
  }
}
