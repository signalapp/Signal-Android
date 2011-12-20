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
import org.thoughtcrime.securesms.util.MemoryCleaner;

import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

/**
 * Activity that prompts for a users's passphrase.
 * 
 * @author Moxie Marlinspike  
 */
public class PassphrasePromptActivity extends PassphraseActivity {

  private EditText passphraseText;
  private Button okButton;
  private Button cancelButton;
		
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.prompt_passphrase_activity);
    initializeResources();
  }
	
  private void initializeResources() {
    passphraseText = (EditText)findViewById(R.id.passphrase_edit);
    okButton       = (Button)findViewById(R.id.ok_button);
    cancelButton   = (Button)findViewById(R.id.cancel_button);
		
    okButton.setOnClickListener(new OkButtonClickListener());
    cancelButton.setOnClickListener(new CancelButtonClickListener());
  }
	
  private class OkButtonClickListener implements OnClickListener {
    public void onClick(View v) {
      try {
        String passphrase         = passphraseText.getText().toString();
        MasterSecret masterSecret = MasterSecretUtil.getMasterSecret(PassphrasePromptActivity.this, passphrase);
        
        MemoryCleaner.clean(passphrase);
        setMasterSecret(masterSecret);
      } catch (InvalidPassphraseException ipe) {
        Toast.makeText(getApplicationContext(), "Invalid Passphrase!", Toast.LENGTH_SHORT).show();
      }
    }
  }
	
  private class CancelButtonClickListener implements OnClickListener {
    public void onClick(View v) {
      finish();
    }
  }

  @Override
  protected void cleanup() {
    this.passphraseText = null;
    System.gc();
  }
	
}
