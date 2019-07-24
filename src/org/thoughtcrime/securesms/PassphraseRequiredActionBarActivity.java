package org.thoughtcrime.securesms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import org.thoughtcrime.securesms.logging.Log;

import org.thoughtcrime.securesms.crypto.MasterSecretUtil;
import org.thoughtcrime.securesms.jobs.PushNotificationReceiveJob;
import org.thoughtcrime.securesms.push.SignalServiceNetworkAccess;
import org.thoughtcrime.securesms.registration.WelcomeActivity;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.util.Locale;

public abstract class PassphraseRequiredActionBarActivity extends BaseActionBarActivity implements MasterSecretListener {
  private static final String TAG = PassphraseRequiredActionBarActivity.class.getSimpleName();

  public static final String LOCALE_EXTRA = "locale_extra";

  private static final int STATE_NORMAL                   = 0;
  private static final int STATE_CREATE_PASSPHRASE        = 1;
  private static final int STATE_PROMPT_PASSPHRASE        = 2;
  private static final int STATE_UPGRADE_DATABASE         = 3;
  private static final int STATE_PROMPT_PUSH_REGISTRATION = 4;
  private static final int STATE_EXPERIENCE_UPGRADE       = 5;
  private static final int STATE_WELCOME_SCREEN           = 6;

  private SignalServiceNetworkAccess networkAccess;
  private BroadcastReceiver          clearKeyReceiver;

  @Override
  protected final void onCreate(Bundle savedInstanceState) {
    Log.i(TAG, "onCreate(" + savedInstanceState + ")");
    this.networkAccess = new SignalServiceNetworkAccess(this);
    onPreCreate();

    final boolean locked = KeyCachingService.isLocked(this);
    routeApplicationState(locked);

    super.onCreate(savedInstanceState);

    if (!isFinishing()) {
      initializeClearKeyReceiver();
      onCreate(savedInstanceState, true);
    }
  }

  protected void onPreCreate() {}
  protected void onCreate(Bundle savedInstanceState, boolean ready) {}

  @Override
  protected void onResume() {
    Log.i(TAG, "onResume()");
    super.onResume();

    if (networkAccess.isCensored(this)) {
      ApplicationContext.getInstance(this).getJobManager().add(new PushNotificationReceiveJob(this));
    }
  }

  @Override
  protected void onPause() {
    Log.i(TAG, "onPause()");
    super.onPause();
  }

  @Override
  protected void onDestroy() {
    Log.i(TAG, "onDestroy()");
    super.onDestroy();
    removeClearKeyReceiver(this);
  }

  @Override
  public void onMasterSecretCleared() {
    Log.i(TAG, "onMasterSecretCleared()");
    if (ApplicationContext.getInstance(this).isAppVisible()) routeApplicationState(true);
    else                                                     finish();
  }

  protected <T extends Fragment> T initFragment(@IdRes int target,
                                                @NonNull T fragment)
  {
    return initFragment(target, fragment, null);
  }

  protected <T extends Fragment> T initFragment(@IdRes int target,
                                                @NonNull T fragment,
                                                @Nullable Locale locale)
  {
    return initFragment(target, fragment, locale, null);
  }

  protected <T extends Fragment> T initFragment(@IdRes int target,
                                                @NonNull T fragment,
                                                @Nullable Locale locale,
                                                @Nullable Bundle extras)
  {
    Bundle args = new Bundle();
    args.putSerializable(LOCALE_EXTRA, locale);

    if (extras != null) {
      args.putAll(extras);
    }

    fragment.setArguments(args);
    getSupportFragmentManager().beginTransaction()
                               .replace(target, fragment)
                               .commitAllowingStateLoss();
    return fragment;
  }

  private void routeApplicationState(boolean locked) {
    Intent intent = getIntentForState(getApplicationState(locked));
    if (intent != null) {
      startActivity(intent);
      finish();
    }
  }

  private Intent getIntentForState(int state) {
    Log.i(TAG, "routeApplicationState(), state: " + state);

    switch (state) {
    case STATE_CREATE_PASSPHRASE:        return getCreatePassphraseIntent();
    case STATE_PROMPT_PASSPHRASE:        return getPromptPassphraseIntent();
    case STATE_UPGRADE_DATABASE:         return getUpgradeDatabaseIntent();
    case STATE_WELCOME_SCREEN:           return getWelcomeIntent();
    case STATE_PROMPT_PUSH_REGISTRATION: return getPushRegistrationIntent();
    case STATE_EXPERIENCE_UPGRADE:       return getExperienceUpgradeIntent();
    default:                             return null;
    }
  }

  private int getApplicationState(boolean locked) {
    if (!MasterSecretUtil.isPassphraseInitialized(this)) {
      return STATE_CREATE_PASSPHRASE;
    } else if (locked) {
      return STATE_PROMPT_PASSPHRASE;
    } else if (DatabaseUpgradeActivity.isUpdate(this)) {
      return STATE_UPGRADE_DATABASE;
    } else if (!TextSecurePreferences.hasSeenWelcomeScreen(this)) {
      return STATE_WELCOME_SCREEN;
    } else if (!TextSecurePreferences.hasPromptedPushRegistration(this)) {
      return STATE_PROMPT_PUSH_REGISTRATION;
    } else if (ExperienceUpgradeActivity.isUpdate(this)) {
      return STATE_EXPERIENCE_UPGRADE;
    } else {
      return STATE_NORMAL;
    }
  }

  private Intent getCreatePassphraseIntent() {
    return getRoutedIntent(PassphraseCreateActivity.class, getIntent());
  }

  private Intent getPromptPassphraseIntent() {
    return getRoutedIntent(PassphrasePromptActivity.class, getIntent());
  }

  private Intent getUpgradeDatabaseIntent() {
    return getRoutedIntent(DatabaseUpgradeActivity.class,
                           TextSecurePreferences.hasPromptedPushRegistration(this)
                               ? getConversationListIntent()
                               : getPushRegistrationIntent());
  }

  private Intent getExperienceUpgradeIntent() {
    return getRoutedIntent(ExperienceUpgradeActivity.class, getIntent());
  }

  private Intent getWelcomeIntent() {
    return getRoutedIntent(WelcomeActivity.class, getPushRegistrationIntent());
  }

  private Intent getPushRegistrationIntent() {
    return getRoutedIntent(RegistrationActivity.class, getCreateProfileIntent());
  }

  private Intent getCreateProfileIntent() {
    return getRoutedIntent(CreateProfileActivity.class, getConversationListIntent());
  }

  private Intent getRoutedIntent(Class<?> destination, @Nullable Intent nextIntent) {
    final Intent intent = new Intent(this, destination);
    if (nextIntent != null)   intent.putExtra("next_intent", nextIntent);
    return intent;
  }

  private Intent getConversationListIntent() {
    return new Intent(this, ConversationListActivity.class);
  }

  private void initializeClearKeyReceiver() {
    Log.i(TAG, "initializeClearKeyReceiver()");
    this.clearKeyReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "onReceive() for clear key event");
        onMasterSecretCleared();
      }
    };

    IntentFilter filter = new IntentFilter(KeyCachingService.CLEAR_KEY_EVENT);
    registerReceiver(clearKeyReceiver, filter, KeyCachingService.KEY_PERMISSION, null);
  }

  private void removeClearKeyReceiver(Context context) {
    if (clearKeyReceiver != null) {
      context.unregisterReceiver(clearKeyReceiver);
      clearKeyReceiver = null;
    }
  }
}
