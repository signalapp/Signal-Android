/**
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.whispersystems.textsecure.crypto.IdentityKey;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.whispersystems.textsecure.crypto.SerializableKey;
import org.whispersystems.textsecure.util.Base64;
import org.thoughtcrime.securesms.util.Dialogs;
import org.thoughtcrime.securesms.util.DynamicTheme;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import org.whispersystems.textsecure.zxing.integration.IntentIntegrator;
import org.whispersystems.textsecure.zxing.integration.IntentResult;

/**
 * Activity for initiating/receiving key QR code scans.
 *
 * @author Moxie Marlinspike
 */
public abstract class KeyScanningActivity extends PassphraseRequiredSherlockActivity {

  private final DynamicTheme dynamicTheme = new DynamicTheme();

  @Override
  protected void onCreate(Bundle bundle) {
    dynamicTheme.onCreate(this);
    super.onCreate(bundle);
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    super.onPrepareOptionsMenu(menu);

    MenuInflater inflater = this.getSupportMenuInflater();
    menu.clear();

    inflater.inflate(R.menu.key_scanning, menu);

    menu.findItem(R.id.menu_scan).setTitle(getScanString());
    menu.findItem(R.id.menu_get_scanned).setTitle(getDisplayString());

    if (this.getRecipientId() == null) {
      menu.findItem(R.id.menu_revoke_verification).setEnabled(false);
    } else {
      menu.findItem(R.id.menu_revoke_verification).setEnabled(true);
    }

    return true;
  }

  private void handleRevokeVerification() {
    if ((this.getRecipientId() == null) || (this.getMasterSecret() == null)) {
      return;
    }

    IdentityDatabase identityDatabase = DatabaseFactory.getIdentityDatabase(this);
    identityDatabase.setIdentityUnverified(this.getMasterSecret(), this.getRecipientId(),
            (IdentityKey)this.getIdentityKeyToCompare());

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
      this.recreate();
    } // TODO do some activity-reloading magic for pre-honeycomb devices?
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);

    switch (item.getItemId()) {
    case R.id.menu_scan:                initiateScan();    return true;
    case R.id.menu_get_scanned:         initiateDisplay(); return true;
    case R.id.menu_revoke_verification: handleRevokeVerification(); return true;
    case android.R.id.home:             finish();          return true;
    }

    return false;
  }

  private void markKeyVerified() {
    if (getRecipientId() == null) {
      return;
    }

    if (getMasterSecret() == null) {
      return;
    }

    IdentityDatabase identityDatabase = DatabaseFactory.getIdentityDatabase(this);
    long recipientId = getRecipientId();
    MasterSecret masterSecret = getMasterSecret();

    identityDatabase.setIdentityVerified(masterSecret, recipientId, (IdentityKey)getIdentityKeyToCompare());
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent intent) {
    IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent);

    if ((scanResult != null) && (scanResult.getContents() != null)) {
      String data = scanResult.getContents();

      if (data.equals(Base64.encodeBytes(getIdentityKeyToCompare().serialize()))) {
        Dialogs.showInfoDialog(this, getVerifiedTitle(), getVerifiedMessage());
        markKeyVerified();
      } else {
        Dialogs.showAlertDialog(this, getNotVerifiedTitle(), getNotVerifiedMessage());
      }
    } else {
      Toast.makeText(this, R.string.KeyScanningActivity_no_scanned_key_found_exclamation,
                     Toast.LENGTH_LONG).show();
    }
  }

  protected void initiateScan() {
    IntentIntegrator.initiateScan(this);
  }

  protected void initiateDisplay() {
    IntentIntegrator.shareText(this, Base64.encodeBytes(getIdentityKeyToDisplay().serialize()));
  }

  protected abstract String getScanString();
  protected abstract String getDisplayString();

  protected abstract String getNotVerifiedTitle();
  protected abstract String getNotVerifiedMessage();

  protected abstract SerializableKey getIdentityKeyToCompare();
  protected abstract SerializableKey getIdentityKeyToDisplay();
  protected abstract Long getRecipientId();
  protected abstract MasterSecret getMasterSecret();

  protected abstract String getVerifiedTitle();
  protected abstract String getVerifiedMessage();

}
