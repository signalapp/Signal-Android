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

import android.os.Bundle;
import android.text.Editable;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import org.thoughtcrime.securesms.crypto.InvalidPassphraseException;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.MasterSecretUtil;
import org.thoughtcrime.securesms.util.MemoryCleaner;

/**
 * Activity that prompts for a users's passphrase.
 *
 * @author Moxie Marlinspike
 */
public class PassphrasePromptActivity extends PassphraseActivity {

  private EditText passphraseText;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.prompt_passphrase_activity);
    initializeResources();
  }

  private void initializeResources() {
    Button okButton = (Button)  findViewById(R.id.ok_button);
    passphraseText  = (EditText)findViewById(R.id.passphrase_edit);

    okButton.setOnClickListener(new OkButtonClickListener());
  }

  private class OkButtonClickListener implements OnClickListener {
    @Override
    public void onClick(View v) {
      try {
        Editable text             = passphraseText.getText();
        String passphrase         = (text == null ? "" : text.toString());
        MasterSecret masterSecret = MasterSecretUtil.getMasterSecret(PassphrasePromptActivity.this, passphrase);

        MemoryCleaner.clean(passphrase);
        setMasterSecret(masterSecret);
      } catch (InvalidPassphraseException ipe) {
        passphraseText.setText("");
        Toast.makeText(getApplicationContext(),
                       R.string.PassphrasePromptActivity_invalid_passphrase_exclamation,
                       Toast.LENGTH_SHORT).show();
      }
    }
  }

  @Override
  protected void cleanup() {
    this.passphraseText.setText("");
    System.gc();
  }
}
