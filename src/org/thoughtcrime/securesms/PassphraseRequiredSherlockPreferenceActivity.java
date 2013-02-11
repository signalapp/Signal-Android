package org.thoughtcrime.securesms;

import android.os.Bundle;

import org.thoughtcrime.securesms.crypto.MasterSecret;

import com.actionbarsherlock.app.SherlockPreferenceActivity;

public abstract class PassphraseRequiredSherlockPreferenceActivity
  extends SherlockPreferenceActivity
  implements PassphraseRequiredActivity
{

  private final PassphraseRequiredMixin delegate = new PassphraseRequiredMixin();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    delegate.onCreate(this, this);
  }

  @Override
  protected void onResume() {
    super.onResume();
    delegate.onResume(this, this);
  }

  @Override
  protected void onPause() {
    super.onPause();
    delegate.onPause(this, this);
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    delegate.onDestroy(this, this);
  }

  @Override
  public void onMasterSecretCleared() {
    finish();
  }

  @Override
  public void onNewMasterSecret(MasterSecret masterSecret) {}

}
