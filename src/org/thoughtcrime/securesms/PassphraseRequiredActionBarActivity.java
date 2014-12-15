package org.thoughtcrime.securesms;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.MasterSecretUtil;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.RecipientFormattingException;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

public abstract class PassphraseRequiredActionBarActivity extends BaseActionBarActivity implements PassphraseRequiredActivity {
  private static final String TAG = PassphraseRequiredActionBarActivity.class.getSimpleName();

  private static final int STATE_NORMAL                   = 0;
  private static final int STATE_CREATE_PASSPHRASE        = 1;
  private static final int STATE_PROMPT_PASSPHRASE        = 2;
  private static final int STATE_UPGRADE_DATABASE         = 3;
  private static final int STATE_PROMPT_PUSH_REGISTRATION = 4;

  private final PassphraseRequiredMixin delegate = new PassphraseRequiredMixin();

  private MasterSecret masterSecret;
  private boolean      isVisible;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    masterSecret = KeyCachingService.getMasterSecret(this);
    routeApplicationState(masterSecret);
    super.onCreate(savedInstanceState);
    if (!isFinishing()) {
      delegate.onCreate(this);
      onCreate(savedInstanceState, masterSecret);
    }
  }

  protected abstract void onCreate(Bundle savedInstanceState, MasterSecret masterSecret);

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
    masterSecret = null;
    super.onDestroy();
    delegate.onDestroy(this);
  }

  protected boolean isVisible() {
    return isVisible;
  }

  protected MasterSecret getMasterSecret() {
    return masterSecret;
  }

  @Override
  public void onMasterSecretCleared() {
    Log.w(TAG, "onMasterSecretCleared()");
    masterSecret = null;
    if (isVisible) routeApplicationState(null);
    else           finish();
  }

  @Override
  public void onNewMasterSecret(MasterSecret masterSecret) {}

  protected void routeApplicationState(MasterSecret masterSecret) {
    Intent intent = getIntentForState(getApplicationState(masterSecret));
    if (intent != null) {
      startActivity(intent);
      finish();
    }
  }

  protected Intent getIntentForState(int state) {
    Log.w(TAG, "routeApplicationState(), state: " + state);

    switch (state) {
      case STATE_CREATE_PASSPHRASE:        return getCreatePassphraseIntent();
      case STATE_PROMPT_PASSPHRASE:        return getPromptPassphraseIntent();
      case STATE_UPGRADE_DATABASE:         return getUpgradeDatabaseIntent();
      case STATE_PROMPT_PUSH_REGISTRATION: return getPushRegistrationIntent();
      default:                             return null;
    }
  }

  private int getApplicationState(MasterSecret masterSecret) {
    if (!MasterSecretUtil.isPassphraseInitialized(this))
      return STATE_CREATE_PASSPHRASE;

    if (masterSecret == null)
      return STATE_PROMPT_PASSPHRASE;

    if (DatabaseUpgradeActivity.isUpdate(this))
      return STATE_UPGRADE_DATABASE;

    if (!TextSecurePreferences.hasPromptedPushRegistration(this))
      return STATE_PROMPT_PUSH_REGISTRATION;

    return STATE_NORMAL;
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

  private Intent getUpgradeDatabaseIntent() {
    Intent intent = new Intent(this, DatabaseUpgradeActivity.class);
    intent.putExtra("next_intent", TextSecurePreferences.hasPromptedPushRegistration(this) ?
        getConversationListIntent() : getPushRegistrationIntent());
    return intent;
  }

  private Intent  getPushRegistrationIntent() {
    Intent intent = new Intent(this, RegistrationActivity.class);
    intent.putExtra("next_intent", getConversationListIntent());
    return intent;
  }

  private Intent getConversationListIntent() {
    return new Intent(this, ConversationListActivity.class);
  }
}
