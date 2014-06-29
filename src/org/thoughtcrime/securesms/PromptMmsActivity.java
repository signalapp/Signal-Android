package org.thoughtcrime.securesms;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class PromptMmsActivity extends PassphraseRequiredActionBarActivity {

  private Button okButton;
  private Button cancelButton;

  @Override
  public void onCreate(Bundle bundle) {
    super.onCreate(bundle);

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
