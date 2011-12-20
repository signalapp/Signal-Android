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

import org.thoughtcrime.securesms.crypto.SerializableKey;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.Dialogs;

import android.app.Activity;
import android.content.Intent;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

/**
 * Activity for initiating/receiving key QR code scans.
 * 
 * @author Moxie Marlinspike
 */
public abstract class KeyScanningActivity extends Activity {

  private static final int MENU_ITEM_SCAN        = 1;
  private static final int MENU_ITEM_GET_SCANNED = 2;

  @Override
  public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
    menu.add(0, MENU_ITEM_SCAN, Menu.NONE, getScanString());
    menu.add(0, MENU_ITEM_GET_SCANNED, Menu.NONE, getDisplayString());
  }
    
  @Override
  public boolean onContextItemSelected(MenuItem item) {

    switch(item.getItemId()) {
    case MENU_ITEM_SCAN:        initiateScan();    return true;
    case MENU_ITEM_GET_SCANNED: initiateDisplay(); return true;
    }
		
    return false;
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent intent) {
    IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent);
		
    if ((scanResult != null) && (scanResult.getContents() != null)) {
      String data = scanResult.getContents();
			
      if (data.equals(Base64.encodeBytes(getIdentityKeyToCompare().serialize()))) {
        Dialogs.displayAlert(this, getVerifiedTitle(), getVerifiedMessage(), android.R.drawable.ic_dialog_info);
      } else {
        Dialogs.displayAlert(this, getNotVerifiedTitle(), getNotVerifiedMessage(), android.R.drawable.ic_dialog_alert);
      }
    } else {
      Toast.makeText(this, "No scanned key found!", Toast.LENGTH_LONG).show();
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
	
  protected abstract String getVerifiedTitle();
  protected abstract String getVerifiedMessage();
		
}
