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
      localIdentityFingerprint.setText(R.string.you_do_not_have_an_identity_key_);
      return;
    }
		
    localIdentityFingerprint.setText(IdentityKeyUtil.getFingerprint(this));
  }

  private void initializeRemoteIdentityKey() {
    SessionRecord sessionRecord = new SessionRecord(this, masterSecret, recipient);
    IdentityKey identityKey     = sessionRecord.getIdentityKey();
		
    if (identityKey == null) {
      remoteIdentityFingerprint.setText(R.string.recipient_has_no_identity_key_);
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
      dialogBuilder.setTitle(R.string.abort_session_);
      dialogBuilder.setIcon(android.R.drawable.ic_dialog_info);
      dialogBuilder.setMessage(R.string.are_you_sure_that_you_would_like_to_abort_this_secure_session_);
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
      dialogBuilder.setTitle(R.string.save_identity_key_);
      dialogBuilder.setIcon(android.R.drawable.ic_dialog_info);
      dialogBuilder.setMessage(R.string.are_you_sure_that_you_would_like_to_mark_this_as_a_valid_identity_key);
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
      Toast.makeText(this, R.string.you_don_t_have_an_identity_key_, Toast.LENGTH_LONG).show();
      return;
    }
		
    super.initiateDisplay();
  }
	
  @Override
  protected void initiateScan() {
    SessionRecord sessionRecord = new SessionRecord(this, masterSecret, recipient);
    IdentityKey identityKey     = sessionRecord.getIdentityKey();
		
    if (identityKey == null) {
      Toast.makeText(this, R.string.recipient_has_no_identity_key_, Toast.LENGTH_LONG);
    } else {
      super.initiateScan();
    }		
  }
	
  @Override
  protected String getScanString() {
    return getString(R.string.scan_their_key_to_compare);
  }

  @Override
  protected String getDisplayString() {
    return getString(R.string.get_my_key_scanned);
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
    return getString(R.string.warning_the_scanned_key_does_not_match_please_check_the_fingerprint_text_carefully_);
  }

  @Override
  protected String getNotVerifiedTitle() {
    return getString(R.string.not_verified_);
  }

  @Override
  protected String getVerifiedMessage() {
    return getString(R.string.their_key_is_correct_it_is_also_necessary_to_verify_your_key_with_them_as_well_);
  }

  @Override
  protected String getVerifiedTitle() {
    return getString(R.string.verified_);
  }
}
