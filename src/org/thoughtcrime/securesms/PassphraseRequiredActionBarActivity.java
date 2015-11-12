package org.thoughtcrime.securesms;

import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

import org.thoughtcrime.securesms.crypto.MasterSecret;

import de.gdata.messaging.util.GDataPreferences;
import de.gdata.messaging.util.GUtil;


public class PassphraseRequiredActionBarActivity extends ActionBarActivity implements PassphraseRequiredActivity {

  private final PassphraseRequiredMixin delegate = new PassphraseRequiredMixin();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    delegate.onCreate(this);
    if(getSupportActionBar()!= null) {
      getSupportActionBar().setBackgroundDrawable(new ColorDrawable(new GDataPreferences(this).getCurrentColorHex()));
      if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.setStatusBarColor(GUtil.darken(new GDataPreferences(this).getCurrentColorHex(), GUtil.ALPHA_10_PERCENT));
      }
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
