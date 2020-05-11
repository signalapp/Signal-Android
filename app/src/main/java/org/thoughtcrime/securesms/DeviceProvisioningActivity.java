package org.thoughtcrime.securesms;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import android.view.Window;

public class DeviceProvisioningActivity extends PassphraseRequiredActionBarActivity {

  @SuppressWarnings("unused")
  private static final String TAG = DeviceProvisioningActivity.class.getSimpleName();

  @Override
  protected void onPreCreate() {
    supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
  }

  @Override
  protected void onCreate(Bundle bundle, boolean ready) {
    assert  getSupportActionBar() != null;
    getSupportActionBar().hide();

    AlertDialog dialog = new AlertDialog.Builder(this)
        .setTitle(getString(R.string.DeviceProvisioningActivity_link_a_signal_device))
        .setMessage(getString(R.string.DeviceProvisioningActivity_it_looks_like_youre_trying_to_link_a_signal_device_using_a_3rd_party_scanner))
        .setPositiveButton(R.string.DeviceProvisioningActivity_continue, (dialog1, which) -> {
          Intent intent = new Intent(DeviceProvisioningActivity.this, DeviceActivity.class);
          intent.putExtra("add", true);
          startActivity(intent);
          finish();
        })
        .setNegativeButton(R.string.DeviceProvisioningActivity_cancel, (dialog12, which) -> {
          dialog12.dismiss();
          finish();
        })
        .setOnDismissListener(dialog13 -> finish())
        .create();

    dialog.setIcon(getResources().getDrawable(R.drawable.icon_dialog));
    dialog.show();
  }
}
