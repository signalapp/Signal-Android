package org.thoughtcrime.securesms;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.actionbarsherlock.app.SherlockActivity;

public class RegistrationProblemsActivity extends SherlockActivity {

  @Override
  public void onCreate(Bundle bundle) {
    super.onCreate(bundle);
    setContentView(R.layout.registration_problems);
    setTitle(getString(R.string.RegistrationProblemsActivity_possible_problems));

    ((Button)findViewById(R.id.close_button)).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        finish();
      }
    });
  }
}
