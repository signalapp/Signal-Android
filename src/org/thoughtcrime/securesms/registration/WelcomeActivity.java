package org.thoughtcrime.securesms.registration;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.BaseActionBarActivity;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import network.loki.messenger.R;

public class WelcomeActivity extends BaseActionBarActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.registration_welcome_activity);
    findViewById(R.id.welcome_terms_button).setOnClickListener(v -> onTermsClicked());
    findViewById(R.id.welcome_continue_button).setOnClickListener(v -> onContinueClicked());
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (TextSecurePreferences.getWasUnlinked(this)) {
      AlertDialog.Builder builder = new AlertDialog.Builder(this);
      builder.setTitle(R.string.dialog_device_unlink_title);
      builder.setMessage(R.string.dialog_device_unlink_message);
      builder.setPositiveButton(R.string.ok, null);
      builder.setOnDismissListener(new DialogInterface.OnDismissListener() {

        @Override
        public void onDismiss(DialogInterface dialog) {
          TextSecurePreferences.setWasUnlinked(getBaseContext(), false);
        }
      });
      builder.show();
    }
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
        .withRationaleDialog(getString(R.string.activity_landing_permission_dialog_message), R.drawable.ic_baseline_folder_48)
        .onAnyResult(() -> {
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
