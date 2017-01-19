package org.thoughtcrime.securesms;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.View;
import android.widget.Button;

import org.thoughtcrime.securesms.preferences.MmsPreferencesActivity;
import org.thoughtcrime.securesms.crypto.MasterSecret;

public class PromptMmsActivity extends PassphraseRequiredActionBarActivity {

  private Button okButton;
  private Button cancelButton;

  @Override
  protected void onCreate(Bundle bundle, @NonNull MasterSecret masterSecret) {
    setContentView(R.layout.prompt_apn_activity);
    initializeResources();
  }

  private void initializeResources() {
    this.okButton     = (Button)findViewById(R.id.ok_button);
    this.cancelButton = (Button)findViewById(R.id.cancel_button);

    this.okButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        Intent intent = new Intent(PromptMmsActivity.this, MmsPreferencesActivity.class);
        intent.putExtras(PromptMmsActivity.this.getIntent().getExtras());
        startActivity(intent);
        finish();
      }
    });

    this.cancelButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        finish();
      }
    });
  }

}
