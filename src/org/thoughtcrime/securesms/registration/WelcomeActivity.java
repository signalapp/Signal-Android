package org.thoughtcrime.securesms.registration;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;

import org.thoughtcrime.securesms.BaseActionBarActivity;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.whispersystems.signalservice.loki.utilities.Analytics;

import network.loki.messenger.R;

public class WelcomeActivity extends BaseActionBarActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.registration_welcome_activity);
    findViewById(R.id.welcome_terms_button).setOnClickListener(v -> onTermsClicked());
    findViewById(R.id.welcome_continue_button).setOnClickListener(v -> onContinueClicked());
    Analytics.Companion.getShared().track("Landing Screen Viewed");
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
  }

  private void onTermsClicked() {
    CommunicationActions.openBrowserLink(this, "https://github.com/loki-project/loki-messenger-android/blob/master/privacy-policy.md");
  }

  private void onContinueClicked() {
    Permissions.with(this)
        .request(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)
        .ifNecessary()
        .withRationaleDialog(getString(R.string.activity_landing_permission_dialog_message), R.drawable.ic_folder_white_48dp)
        .onAnyResult(() -> {
          // TextSecurePreferences.setHasSeenWelcomeScreen(WelcomeActivity.this, true);

          Intent nextIntent = getIntent().getParcelableExtra("next_intent");

          if (nextIntent == null) {
            throw new IllegalStateException("Was not supplied a next_intent.");
          }

          startActivity(nextIntent);
          finish();
        })
        .execute();
  }
}
