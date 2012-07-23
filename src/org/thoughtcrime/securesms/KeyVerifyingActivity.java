package org.thoughtcrime.securesms;

import android.os.Bundle;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

public abstract class KeyVerifyingActivity extends KeyScanningActivity {

  @Override
  protected void onCreate(Bundle bundle) {
    super.onCreate(bundle);
    this.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    super.onPrepareOptionsMenu(menu);

    MenuInflater inflater = this.getSupportMenuInflater();
    inflater.inflate(R.menu.verify_keys, menu);

    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);

    switch (item.getItemId()) {
    case R.id.menu_session_verified: handleVerified(); return true;
    }

    return false;
  }

  protected abstract void handleVerified();

}
