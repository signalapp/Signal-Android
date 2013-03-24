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
import android.text.method.PasswordTransformationMethod;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.thoughtcrime.securesms.crypto.InvalidPassphraseException;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.MasterSecretUtil;
import org.thoughtcrime.securesms.util.MemoryCleaner;

/**
 * Activity that prompts for a users's passphrase.
 *
 * @author Moxie Marlinspike
 */
public class PassphrasePromptActivity extends PassphraseActivity {

  private EditText passphraseText;
  private Button okButton;
  private CheckBox showPasswordCheckbox;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.prompt_passphrase_activity);
    initializeResources();
  }

  private void initializeResources() {
    passphraseText       = (EditText)findViewById(R.id.passphrase_edit);
    okButton             = (Button)findViewById(R.id.ok_button);
    showPasswordCheckbox = (CheckBox)findViewById(R.id.show_password_checkbox);

    okButton.setOnClickListener(new OkButtonClickListener());
    passphraseText.setOnEditorActionListener(new EditText.OnEditorActionListener() {

      @Override
      public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_DONE ||
                    event.getAction() == KeyEvent.ACTION_DOWN &&
                    event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
          okButton.performClick();
          return true;
        }
        return false;
      }
    });

    showPasswordCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

      @Override
      public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        int start = passphraseText.getSelectionStart();
        int stop = passphraseText.getSelectionEnd();
        if (isChecked) {
          passphraseText.setTransformationMethod(null);
        } else {
          passphraseText.setTransformationMethod(new PasswordTransformationMethod());
        }
        passphraseText.setSelection(start, stop);
      }
    });

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
    this.passphraseText = null;
    System.gc();
  }
}
