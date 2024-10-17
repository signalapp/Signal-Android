package org.thoughtcrime.securesms;

import android.os.Bundle;
import android.view.Window;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity;

public class DeviceProvisioningActivity extends PassphraseRequiredActivity {

  @SuppressWarnings("unused")
  private static final String TAG = Log.tag(DeviceProvisioningActivity.class);

  @Override
  protected void onPreCreate() {
    supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
  }

  @Override
  protected void onCreate(Bundle bundle, boolean ready) {
    AlertDialog dialog = new MaterialAlertDialogBuilder(this)
        .setTitle(getString(R.string.DeviceProvisioningActivity_link_a_signal_device))
        .setMessage(getString(R.string.DeviceProvisioningActivity_it_looks_like_youre_trying_to_link_a_signal_device_using_a_3rd_party_scanner))
        .setPositiveButton(R.string.DeviceProvisioningActivity_continue, (dialog1, which) -> {
          startActivity(AppSettingsActivity.linkedDevices(this));
          finish();
        })
        .setNegativeButton(android.R.string.cancel, (dialog12, which) -> {
          dialog12.dismiss();
          finish();
        })
        .setOnDismissListener(dialog13 -> finish())
        .create();

    dialog.show();
  }
}
