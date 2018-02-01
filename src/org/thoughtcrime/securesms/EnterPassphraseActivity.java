package org.thoughtcrime.securesms;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicTheme;

public class EnterPassphraseActivity extends BaseActionBarActivity {

  private DynamicTheme dynamicTheme = new DynamicTheme();
  private DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private EditText passphraseView;
  private EditText repeatPassphraseView;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
    super.onCreate(savedInstanceState);

    setContentView(R.layout.enter_passphrase_activity);

    initializeResources();
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
    dynamicLanguage.onResume(this);
  }

  private void initializeResources() {
    this.passphraseView = (EditText) findViewById(R.id.passphrase);
    this.repeatPassphraseView = (EditText) findViewById(R.id.repeat_passphrase);

    Button okButton = (Button) findViewById(R.id.ok_button);
    Button cancelButton = (Button) findViewById(R.id.cancel_button);

    okButton.setOnClickListener(new EnterPassphraseActivity.OkButtonClickListener());
    cancelButton.setOnClickListener(new EnterPassphraseActivity.CancelButtonClickListener());
  }

  private class CancelButtonClickListener implements View.OnClickListener {
    public void onClick(View v) {
      cleanup();
      finish();
    }
  }

  private class OkButtonClickListener implements View.OnClickListener {
    public void onClick(View v) {
      Editable passphraseText = passphraseView.getText();
      Editable repeatPassphraseText = repeatPassphraseView.getText();

      String passphrase = passphraseText == null ? "" : passphraseText.toString();
      String repeatPassphrase = repeatPassphraseText == null ? "" : repeatPassphraseText.toString();

      if (!passphrase.equals(repeatPassphrase)) {
        passphraseView.setText("");
        repeatPassphraseView.setText("");
        passphraseView.setError(getString(R.string.EnterPassphraseActivity_passphrases_dont_match_exclamation));
        passphraseView.requestFocus();
      } else if ("".equals(passphrase)) {
        passphraseView.setText("");
        repeatPassphraseView.setText("");
        passphraseView.setError(getString(R.string.EnterPassphraseActivity_enter_passphrase_exclamation));
        passphraseView.requestFocus();
      } else {
        Intent result = getIntent();
        result.putExtra("passphrase", passphrase);
        setResult(RESULT_OK, result);
        cleanup();
        finish();
      }
    }
  }

  protected void cleanup() {
    this.passphraseView = null;
    this.repeatPassphraseView = null;
    System.gc();
  }

}
