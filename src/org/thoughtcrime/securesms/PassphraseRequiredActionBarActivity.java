package org.thoughtcrime.securesms;

import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;

import org.thoughtcrime.securesms.crypto.MasterSecret;

import de.gdata.messaging.util.GDataPreferences;


public class PassphraseRequiredActionBarActivity extends ActionBarActivity implements PassphraseRequiredActivity {

  private final PassphraseRequiredMixin delegate = new PassphraseRequiredMixin();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    delegate.onCreate(this);
    if(getSupportActionBar()!= null) {
      getSupportActionBar().setBackgroundDrawable(new ColorDrawable(new GDataPreferences(this).getCurrentColorHex()));
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    delegate.onResume(this);
  }

  @Override
  protected void onPause() {
    super.onPause();
    delegate.onPause(this);
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    delegate.onDestroy(this);
  }

  @Override
  public void onMasterSecretCleared() {
    finish();
  }

  @Override
  public void onNewMasterSecret(MasterSecret masterSecret) {}

}
