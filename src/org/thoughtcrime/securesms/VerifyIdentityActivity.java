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
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.thoughtcrime.securesms.crypto.KeyUtil;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.SessionRecord;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.MemoryCleaner;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Activity for verifying identity keys.
 * 
 * @author Moxie Marlinspike
 */
public class VerifyIdentityActivity extends KeyScanningActivity {
	
  private Recipient recipient;
  private MasterSecret masterSecret;
	
  private TextView localIdentityFingerprint;
  private TextView remoteIdentityFingerprint;
	
  private Button verifiedButton;
  private Button abortButton;
  private Button cancelButton;
  private Button compareButton;
	
  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    setContentView(R.layout.verify_identity_activity);
		
    initializeResources();
    initializeFingerprints();
    initializeListeners();
  }
  
  @Override
  protected void onDestroy() {
    MemoryCleaner.clean(masterSecret);
    super.onDestroy();
  }
		
  private void initializeLocalIdentityKey() {
    if (!IdentityKeyUtil.hasIdentityKey(this)) {
      localIdentityFingerprint.setText("You do not have an identity key.");
      return;
    }
		
    localIdentityFingerprint.setText(IdentityKeyUtil.getFingerprint(this));
  }

  private void initializeRemoteIdentityKey() {
    SessionRecord sessionRecord = new SessionRecord(this, masterSecret, recipient);
    IdentityKey identityKey     = sessionRecord.getIdentityKey();
		
    if (identityKey == null) {
      remoteIdentityFingerprint.setText("Recipient has no identity key.");
      verifiedButton.setEnabled(false);
    } else {
      remoteIdentityFingerprint.setText(identityKey.getFingerprint());
    }
  }
	
  private void initializeListeners() {
    verifiedButton.setOnClickListener(new VerifiedButtonListener());
    cancelButton.setOnClickListener(new CancelButtonListener());
    abortButton.setOnClickListener(new AbortButtonListener());
    compareButton.setOnClickListener(new CompareButtonListener());
  }
	
  private void initializeFingerprints() {
    initializeLocalIdentityKey();
    initializeRemoteIdentityKey();
  }
	
  private void initializeResources() {
    localIdentityFingerprint  = (TextView)findViewById(R.id.you_read);
    remoteIdentityFingerprint = (TextView)findViewById(R.id.friend_reads);
    recipient                 = (Recipient)this.getIntent().getParcelableExtra("recipient");
    masterSecret              = (MasterSecret)this.getIntent().getParcelableExtra("master_secret");
    verifiedButton            = (Button)findViewById(R.id.verified_button);
    abortButton               = (Button)findViewById(R.id.abort_button);
    cancelButton              = (Button)findViewById(R.id.cancel_button);
    compareButton             = (Button)findViewById(R.id.compare_button);
  }
	
  private void abortSession() {
    KeyUtil.abortSessionFor(this, recipient);
  }
	
  private void saveRemoteIdentity() {
    SessionRecord sessionRecord = new SessionRecord(this, masterSecret, recipient);
    IdentityKey identityKey     = sessionRecord.getIdentityKey();
    String recipientName        = recipient.getName();
    Intent intent               = new Intent(this, SaveIdentityActivity.class);
    intent.putExtra("name_suggestion", recipientName);
    intent.putExtra("master_secret", masterSecret);
    intent.putExtra("identity_key", identityKey);

    startActivity(intent);		
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
	
  private class AbortButtonListener implements View.OnClickListener {
    public void onClick(View v) {
      AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(VerifyIdentityActivity.this);
      dialogBuilder.setTitle("Abort Session?");
      dialogBuilder.setIcon(android.R.drawable.ic_dialog_info);
      dialogBuilder.setMessage("Are you sure that you would like to abort this secure session?");
      dialogBuilder.setCancelable(true);
      dialogBuilder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface arg0, int arg1) {
          abortSession();
          finish();
        }
      });
      dialogBuilder.setNegativeButton(R.string.no, null);
      dialogBuilder.show();
    }		
  }
	
  private class VerifiedButtonListener implements View.OnClickListener {
    public void onClick(View v) {
      AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(VerifyIdentityActivity.this);
      dialogBuilder.setTitle("Save Identity Key?");
      dialogBuilder.setIcon(android.R.drawable.ic_dialog_info);
      dialogBuilder.setMessage("Are you sure that you would like to mark this as a valid identity key for all future correspondence with this recipient?  You should only do this if you have actually verified the fingerprint.");
      dialogBuilder.setCancelable(true);
      dialogBuilder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface arg0, int arg1) {
          saveRemoteIdentity();
          finish();
        }
      });
      dialogBuilder.setNegativeButton(R.string.no, null);
      dialogBuilder.show();
    }
  }

  @Override
  protected void initiateDisplay() {
    if (!IdentityKeyUtil.hasIdentityKey(this)) {
      Toast.makeText(this, "You don't have an identity key!", Toast.LENGTH_LONG).show();
      return;
    }
		
    super.initiateDisplay();
  }
	
  @Override
  protected void initiateScan() {
    SessionRecord sessionRecord = new SessionRecord(this, masterSecret, recipient);
    IdentityKey identityKey     = sessionRecord.getIdentityKey();
		
    if (identityKey == null) {
      Toast.makeText(this, "Recipient has no identity key!", Toast.LENGTH_LONG);
    } else {
      super.initiateScan();
    }		
  }
	
  @Override
  protected String getScanString() {
    return "Scan their key to compare";
  }

  @Override
  protected String getDisplayString() {
    return "Get my key scanned";
  }

  @Override
  protected IdentityKey getIdentityKeyToCompare() {
    SessionRecord sessionRecord = new SessionRecord(this, masterSecret, recipient);
    return sessionRecord.getIdentityKey();
  }

  @Override
  protected IdentityKey getIdentityKeyToDisplay() {
    return IdentityKeyUtil.getIdentityKey(this);
  }

  @Override
  protected String getNotVerifiedMessage() {
    return "WARNING, the scanned key DOES NOT match! Please check the fingerprint text carefully.";
  }

  @Override
  protected String getNotVerifiedTitle() {
    return "NOT Verified!";
  }

  @Override
  protected String getVerifiedMessage() {
    return "Their key is correct. It is also necessary to verify your key with them as well.";
  }

  @Override
  protected String getVerifiedTitle() {
    return "Verified!";
  }
}
