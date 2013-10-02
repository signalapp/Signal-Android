package org.thoughtcrime.securesms;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class PromptMmsActivity extends PassphraseRequiredSherlockActivity {

  private Button okButton;
  private Button cancelButton;

  @Override
  public void onCreate(Bundle bundle) {
    super.onCreate(bundle);

    setContentView(R.layout.prompt_apn_activity);
    initializeResources();
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    if (resultCode == MmsPreferencesActivity.RESULT_FINISH_MMS_PROMPT)
      finish();
  }

  private void initializeResources() {
    this.okButton     = (Button)findViewById(R.id.ok_button);
    this.cancelButton = (Button)findViewById(R.id.cancel_button);

    this.okButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        Intent intent = new Intent(PromptMmsActivity.this, MmsPreferencesActivity.class);
        intent.putExtras(PromptMmsActivity.this.getIntent().getExtras());
        intent.putExtra(MmsPreferencesActivity.PARENT_IS_PROMPT_MMS, true);
        startActivityForResult(intent, 1337);
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
