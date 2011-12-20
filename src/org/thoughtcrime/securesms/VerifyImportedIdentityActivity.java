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

import org.thoughtcrime.securesms.crypto.IdentityKey;
import org.thoughtcrime.securesms.crypto.InvalidKeyException;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.util.Dialogs;
import org.thoughtcrime.securesms.util.MemoryCleaner;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Activity for verifying identities keys as they are imported.
 * 
 * @author Moxie Marlinspike
 */

public class VerifyImportedIdentityActivity extends KeyScanningActivity {
		
  private MasterSecret masterSecret;
  private String contactName;
  private IdentityKey identityKey;
  private EditText identityName;
  private TextView identityFingerprint;
	
  private Button compareButton;
  private Button verifiedButton;
  private Button cancelButton;
	
  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    setContentView(R.layout.verify_imported_identity_activity);
		
    initializeResources();
    initializeFingerprints();
    initializeListeners();
  }
  
  @Override
  protected void onDestroy() {
    MemoryCleaner.clean(masterSecret);
    super.onDestroy();
  }
	
  private void initializeListeners() {
    verifiedButton.setOnClickListener(new VerifiedButtonListener());
    cancelButton.setOnClickListener(new CancelButtonListener());
    compareButton.setOnClickListener(new CompareButtonListener());
  }
	
  private void initializeFingerprints() {
    if (contactName != null)
      identityName.setText(contactName);
    identityFingerprint.setText(identityKey.getFingerprint());
  }
	
  private void initializeResources() {
    masterSecret        = (MasterSecret)this.getIntent().getParcelableExtra("master_secret");
    identityFingerprint = (TextView)findViewById(R.id.imported_identity);
    identityName        = (EditText)findViewById(R.id.identity_name);
    identityKey         = (IdentityKey)this.getIntent().getParcelableExtra("identity_key");
    contactName         = (String)this.getIntent().getStringExtra("contact_name");
    verifiedButton      = (Button)findViewById(R.id.verified_button);
    cancelButton        = (Button)findViewById(R.id.cancel_button);
    compareButton       = (Button)findViewById(R.id.compare_button);
  }
			
  private class CancelButtonListener implements View.OnClickListener {
    public void onClick(View v) {
      finish();
    }
  }
	
  private class CompareButtonListener implements View.OnClickListener {
    public void onClick(View v) {
      registerForContextMenu(compareButton);
      compareButton.showContextMenu();
    }
  }
		
  private class VerifiedButtonListener implements View.OnClickListener {
    public void onClick(View v) {
      if (identityName.getText() == null || identityName.getText().length() == 0) {
        Toast.makeText(VerifyImportedIdentityActivity.this, "You must specify a name for this contact!", Toast.LENGTH_LONG);
        return;
      }
			
      AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(VerifyImportedIdentityActivity.this);
      dialogBuilder.setTitle("Save Identity Key?");
      dialogBuilder.setIcon(android.R.drawable.ic_dialog_info);
      dialogBuilder.setMessage("Are you sure that you would like to mark this as a valid identity key for all future correspondence with " + identityName.getText() + "?  You should only do this if you have actually verified the fingerprint.");
      dialogBuilder.setCancelable(true);
      dialogBuilder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface arg0, int arg1) {
          try {
            DatabaseFactory.getIdentityDatabase(VerifyImportedIdentityActivity.this).saveIdentity(masterSecret, identityKey, identityName.getText().toString());
          } catch (InvalidKeyException ike) {
            Log.w("VerifiedButtonListener", ike);
            Dialogs.displayAlert(VerifyImportedIdentityActivity.this, "Error saving identity key!", 
                                 "This identity key or an identity key with the same name already exists.  Please edit your key database.", 
                                 android.R.drawable.ic_dialog_alert);
            return;
          }

          finish();
        }
      });
      dialogBuilder.setNegativeButton(R.string.no, null);
      dialogBuilder.show();
    }
  }
	
  @Override
  protected String getScanString() {
    return "Scan to compare";
  }

  @Override
  protected String getDisplayString() {
    return "Get scanned to compare";
  }

  @Override
  protected IdentityKey getIdentityKeyToCompare() {
    return identityKey;
  }

  @Override
  protected IdentityKey getIdentityKeyToDisplay() {
    return identityKey;
  }

  @Override
  protected String getNotVerifiedMessage() {
    return  "WARNING, the scanned key DOES NOT match!";
  }

  @Override
  protected String getNotVerifiedTitle() {
    return "NOT Verified!";
  }

  @Override
  protected String getVerifiedMessage() {
    return "The scanned key matches!";
  }

  @Override
  protected String getVerifiedTitle() {
    return "Verified!";
  }
}
