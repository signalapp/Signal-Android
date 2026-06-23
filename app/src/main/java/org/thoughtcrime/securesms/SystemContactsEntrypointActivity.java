package org.thoughtcrime.securesms;

import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.lifecycle.ViewModelProvider;

public class SystemContactsEntrypointActivity extends ComponentActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    SystemContactsEntrypointViewModel viewModel = new ViewModelProvider(this).get(SystemContactsEntrypointViewModel.class);

    viewModel.getContactAction().observe(this, nextStep -> {
      if (nextStep.getShowSpecifyRecipientToast()) {
        Toast.makeText(this, R.string.ConversationActivity_specify_recipient, Toast.LENGTH_LONG).show();
      }
      startActivity(nextStep.getIntent());
      finish();
    });

    viewModel.resolveNextStep(getIntent());
  }
}
