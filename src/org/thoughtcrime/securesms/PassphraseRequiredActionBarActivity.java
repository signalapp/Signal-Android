package org.thoughtcrime.securesms;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.MasterSecretUtil;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

public abstract class PassphraseRequiredActionBarActivity extends BaseActionBarActivity implements MasterSecretListener {
  private static final String TAG = PassphraseRequiredActionBarActivity.class.getSimpleName();

  private static final int STATE_NORMAL                   = 0;
  private static final int STATE_CREATE_PASSPHRASE        = 1;
  private static final int STATE_PROMPT_PASSPHRASE        = 2;
  private static final int STATE_UPGRADE_DATABASE         = 3;
  private static final int STATE_PROMPT_PUSH_REGISTRATION = 4;

  private final PassphraseRequiredMixin delegate = new PassphraseRequiredMixin();

  private boolean isVisible;

  @Override
  protected final void onCreate(Bundle savedInstanceState) {
    onPreCreate();
    final MasterSecret masterSecret = KeyCachingService.getMasterSecret(this);
    routeApplicationState(masterSecret);
    super.onCreate(savedInstanceState);
    if (!isFinishing()) {
      delegate.onCreate(this);
      onCreate(savedInstanceState, masterSecret);
    }
  }

  protected void onPreCreate() {}
  protected void onCreate(Bundle savedInstanceState, @NonNull MasterSecret masterSecret) {}

  @Override
  protected void onResume() {
    super.onResume();
    delegate.onResume(this);
    isVisible = true;
  }

  @Override
  protected void onPause() {
    super.onPause();
    delegate.onPause(this);
    isVisible = false;
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    delegate.onDestroy(this);
  }

  @Override
  public void onMasterSecretCleared() {
    Log.w(TAG, "onMasterSecretCleared()");
    if (isVisible) routeApplicationState(null);
    else           finish();
  }

  protected void routeApplicationState(MasterSecret masterSecret) {
    Intent intent = getIntentForState(masterSecret, getApplicationState(masterSecret));
    if (intent != null) {
      startActivity(intent);
      finish();
    }
  }

  protected Intent getIntentForState(MasterSecret masterSecret, int state) {
    Log.w(TAG, "routeApplicationState(), state: " + state);

    switch (state) {
      case STATE_CREATE_PASSPHRASE:        return getCreatePassphraseIntent();
      case STATE_PROMPT_PASSPHRASE:        return getPromptPassphraseIntent();
      case STATE_UPGRADE_DATABASE:         return getUpgradeDatabaseIntent(masterSecret);
      case STATE_PROMPT_PUSH_REGISTRATION: return getPushRegistrationIntent(masterSecret);
      default:                             return null;
    }
  }

  private int getApplicationState(MasterSecret masterSecret) {
    if (!MasterSecretUtil.isPassphraseInitialized(this)) {
      return STATE_CREATE_PASSPHRASE;
    } else if (masterSecret == null) {
      return STATE_PROMPT_PASSPHRASE;
    } else if (DatabaseUpgradeActivity.isUpdate(this)) {
      return STATE_UPGRADE_DATABASE;
    } else if (!TextSecurePreferences.hasPromptedPushRegistration(this)) {
      return STATE_PROMPT_PUSH_REGISTRATION;
    } else {
      return STATE_NORMAL;
    }
  }

  private Intent getCreatePassphraseIntent() {
    Intent intent = new Intent(this, PassphraseCreateActivity.class);
    intent.putExtra("next_intent", getIntent());
    return intent;
  }

  private Intent getPromptPassphraseIntent() {
    Intent intent = new Intent(this, PassphrasePromptActivity.class);
    intent.putExtra("next_intent", getIntent());
    return intent;
  }

  private Intent getUpgradeDatabaseIntent(MasterSecret masterSecret) {
    Intent intent = new Intent(this, DatabaseUpgradeActivity.class);
    intent.putExtra("master_secret", masterSecret);
    intent.putExtra("next_intent", TextSecurePreferences.hasPromptedPushRegistration(this) ?
        getConversationListIntent() : getPushRegistrationIntent(masterSecret));
    return intent;
  }

  private Intent  getPushRegistrationIntent(MasterSecret masterSecret) {
    Intent intent = new Intent(this, RegistrationActivity.class);
    intent.putExtra("master_secret", masterSecret);
    intent.putExtra("next_intent", getConversationListIntent());
    return intent;
  }

  private Intent getConversationListIntent() {
    return new Intent(this, ConversationListActivity.class);
  }
}
