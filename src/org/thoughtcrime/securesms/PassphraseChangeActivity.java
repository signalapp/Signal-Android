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

import org.thoughtcrime.securesms.crypto.InvalidPassphraseException;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.MasterSecretUtil;
import org.thoughtcrime.securesms.lang.BhoButton;
import org.thoughtcrime.securesms.lang.BhoEditText;
import org.thoughtcrime.securesms.util.MemoryCleaner;

import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Toast;

/**
 * Activity for changing a user's local encryption passphrase.
 * 
 * @author Moxie Marlinspike
 */

public class PassphraseChangeActivity extends PassphraseActivity {
  private BhoEditText	originalPassphrase;
  private BhoEditText	newPassphrase;
  private BhoEditText	repeatPassphrase;
  private BhoButton	  okButton;
  private BhoButton	  cancelButton;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
		
    setContentView(R.layout.change_passphrase_activity);
		
    initializeResources();
  }
	
  private void initializeResources() {
    this.originalPassphrase = (BhoEditText) findViewById(R.id.old_passphrase);
    this.newPassphrase      = (BhoEditText) findViewById(R.id.new_passphrase);
    this.repeatPassphrase   = (BhoEditText) findViewById(R.id.repeat_passphrase);

    this.okButton           = (BhoButton) findViewById(R.id.ok_button);
    this.cancelButton       = (BhoButton) findViewById(R.id.cancel_button);
		
    this.okButton.setOnClickListener(new OkButtonClickListener());
    this.cancelButton.setOnClickListener(new CancelButtonClickListener());
  }
	
  private void verifyAndSavePassphrases() {
    String original         = this.originalPassphrase.getText().toString();
    String passphrase       = this.newPassphrase.getText().toString();
    String passphraseRepeat = this.repeatPassphrase.getText().toString();
		
    try {
      if (!passphrase.equals(passphraseRepeat)) {
        Toast.makeText(getApplicationContext(), R.string.passphrases_don_t_match_, Toast.LENGTH_SHORT).show();
        this.newPassphrase.setText("");
        this.repeatPassphrase.setText("");
      } else {
        MasterSecret masterSecret = MasterSecretUtil.changeMasterSecretPassphrase(this, original, passphrase);
        MemoryCleaner.clean(original);
        MemoryCleaner.clean(passphrase);
        MemoryCleaner.clean(passphraseRepeat);
        
        setMasterSecret(masterSecret);
      }
    } catch (InvalidPassphraseException e) {
      Toast.makeText(this, R.string.incorrect_old_passphrase_, Toast.LENGTH_LONG).show();
      this.originalPassphrase.setText("");
    }
  }

  private class CancelButtonClickListener implements OnClickListener {
    public void onClick(View v) {	
      finish();
    }
  }
	
  private class OkButtonClickListener implements OnClickListener {
    public void onClick(View v) {
      verifyAndSavePassphrases();
    }
  }

  @Override
  protected void cleanup() {
    this.originalPassphrase = null;
    this.newPassphrase      = null;
    this.repeatPassphrase   = null;
    
    System.gc();
  }
}
