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

import android.os.AsyncTask;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.thoughtcrime.securesms.crypto.InvalidPassphraseException;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.MasterSecretUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

/**
 * Activity for changing a user's local encryption passphrase.
 *
 * @author Moxie Marlinspike
 */

public class PassphraseChangeActivity extends PassphraseActivity {

  private EditText originalPassphrase;
  private EditText newPassphrase;
  private EditText repeatPassphrase;
  private TextView originalPassphraseLabel;
  private Button   okButton;
  private Button   cancelButton;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.change_passphrase_activity);

    initializeResources();
  }

  private void initializeResources() {
    this.originalPassphraseLabel = (TextView) findViewById(R.id.old_passphrase_label);
    this.originalPassphrase      = (EditText) findViewById(R.id.old_passphrase      );
    this.newPassphrase           = (EditText) findViewById(R.id.new_passphrase      );
    this.repeatPassphrase        = (EditText) findViewById(R.id.repeat_passphrase   );

    this.okButton                = (Button  ) findViewById(R.id.ok_button           );
    this.cancelButton            = (Button  ) findViewById(R.id.cancel_button       );

    this.okButton.setOnClickListener(new OkButtonClickListener());
    this.cancelButton.setOnClickListener(new CancelButtonClickListener());

    if (TextSecurePreferences.isPasswordDisabled(this)) {
      this.originalPassphrase.setVisibility(View.GONE);
      this.originalPassphraseLabel.setVisibility(View.GONE);
    } else {
      this.originalPassphrase.setVisibility(View.VISIBLE);
      this.originalPassphraseLabel.setVisibility(View.VISIBLE);
    }
  }

  private void verifyAndSavePassphrases() {
    Editable originalText = this.originalPassphrase.getText();
    Editable newText      = this.newPassphrase.getText();
    Editable repeatText   = this.repeatPassphrase.getText();

    String original         = (originalText == null ? "" : originalText.toString());
    String passphrase       = (newText == null ? "" : newText.toString());
    String passphraseRepeat = (repeatText == null ? "" : repeatText.toString());

    if (TextSecurePreferences.isPasswordDisabled(this)) {
      original = MasterSecretUtil.UNENCRYPTED_PASSPHRASE;
    }

    if (!passphrase.equals(passphraseRepeat)) {
      Toast.makeText(getApplicationContext(),
                     R.string.PassphraseChangeActivity_passphrases_dont_match_exclamation,
                     Toast.LENGTH_SHORT).show();
      this.newPassphrase.setText("");
      this.repeatPassphrase.setText("");
    } else if (passphrase.equals("")) {
      Toast.makeText(getApplicationContext(),
                     R.string.PassphraseChangeActivity_enter_new_passphrase_exclamation,
                     Toast.LENGTH_SHORT).show();
    } else {
      new ChangeMasterSecretPassphrase(this, original, passphrase).execute();
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

  private class ChangeMasterSecretPassphrase extends AsyncTask<Void, Void, Void> {
    private MasterSecret masterSecret;
    private InvalidPassphraseException exception;
    private EditText originalPassphrase;
    private Context context;
    private String original, passphrase;

    public ChangeMasterSecretPassphrase(Context context, String original, String passphrase) {
      this.context = context;
      this.original = original;
      this.passphrase = passphrase;
    }

    @Override
    protected Void doInBackground(Void... params) {
      try {
        this.masterSecret = MasterSecretUtil.changeMasterSecretPassphrase(context, original, passphrase);
        TextSecurePreferences.setPasswordDisabled(context, false);

      } catch (InvalidPassphraseException e) {
        this.exception = e;
      }

      return null;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
      this.originalPassphrase = (EditText) PassphraseChangeActivity.this.findViewById(R.id.old_passphrase);

      // If exception not thrown and master secret not null -> setMasterSecret
      if (exception == null) {
        if (masterSecret != null) {
          setMasterSecret(masterSecret);
        }
      }

      // If exception is thrown -> Toast incorrect old passphrase and set EditText to empty
      else {
        Toast.makeText(context, R.string.PassphraseChangeActivity_incorrect_old_passphrase_exclamation,
                       Toast.LENGTH_LONG).show();
        this.originalPassphrase.setText("");
      }
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
