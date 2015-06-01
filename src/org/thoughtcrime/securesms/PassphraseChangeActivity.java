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

import android.app.IntentService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
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

  private static final String CHANGEPHRASE_FILTER = "PassphraseChangeActivity_changePhraseReceiver";
  private EditText originalPassphrase;
  private EditText newPassphrase;
  private EditText repeatPassphrase;
  private TextView originalPassphraseLabel;
  private Button   okButton;
  private Button   cancelButton;
  private ChangePassphraseReceiver changePhraseReceiver;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.change_passphrase_activity);

    initializeResources();
    changePhraseReceiver = new ChangePassphraseReceiver();
    LocalBroadcastManager.getInstance(this)
      .registerReceiver(changePhraseReceiver, new IntentFilter(CHANGEPHRASE_FILTER));
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    if (changePhraseReceiver != null)
      LocalBroadcastManager.getInstance(this).unregisterReceiver(changePhraseReceiver);
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
      Intent changePhrase = new Intent(this, ChangePassphraseService.class);
      changePhraseReceiver.disableOKButton();
      String[] params = { original, passphrase };
      changePhrase.putExtra("params", params);
      this.startService(changePhrase);
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

  private class ChangePassphraseReceiver extends BroadcastReceiver {

    public void disableOKButton() {
      okButton.setEnabled(false);
    }

    @Override
    public void onReceive(Context receiverContext, Intent receiverIntent) {
      MasterSecret masterSecret = (MasterSecret) receiverIntent.getParcelableExtra("returnValue");
      okButton.setEnabled(true);

      if (masterSecret != null) {
        setMasterSecret(masterSecret);
      } else {
        Toast.makeText(PassphraseChangeActivity.this,
                       R.string.PassphraseChangeActivity_incorrect_old_passphrase_exclamation,
                       Toast.LENGTH_LONG).show();
        originalPassphrase.setText("");
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

  public static class ChangePassphraseService extends IntentService {
    public ChangePassphraseService() {
      super("ChangePassphraseService");
    }

    public void onHandleIntent(Intent intent) {
      String[] params = intent.getStringArrayExtra("params");
      Intent resultIntent = new Intent(CHANGEPHRASE_FILTER);
      try {
        MasterSecret masterSecret = MasterSecretUtil.changeMasterSecretPassphrase(this, params[0], params[1]);
        TextSecurePreferences.setPasswordDisabled(this, false);
        resultIntent.putExtra("returnValue", masterSecret);
        LocalBroadcastManager.getInstance(this).sendBroadcast(resultIntent);
      } catch (InvalidPassphraseException e) {
        Log.w(PassphraseChangeActivity.class.getSimpleName(), e);
        LocalBroadcastManager.getInstance(this).sendBroadcast(resultIntent);
      }
    }
  }
}
