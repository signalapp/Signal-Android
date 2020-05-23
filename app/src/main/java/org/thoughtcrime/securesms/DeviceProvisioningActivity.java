package org.thoughtcrime.securesms;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;

public class DeviceProvisioningActivity extends AppCompatActivity {

  @SuppressWarnings("unused")
  private static final String TAG = Log.tag(DeviceProvisioningActivity.class);

  private final DynamicTheme dynamicTheme = new DynamicNoActionBarTheme();

  @Override
  protected void onCreate(Bundle bundle) {
    super.onCreate(bundle);
    dynamicTheme.onCreate(this);

    new AlertDialog.Builder(this)
                   .setTitle(getString(R.string.DeviceProvisioningActivity_link_a_signal_device))
                   .setMessage(getString(R.string.DeviceProvisioningActivity_it_looks_like_youre_trying_to_link_a_signal_device_using_a_3rd_party_scanner))
                   .setIcon(getResources().getDrawable(R.drawable.icon_dialog))
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
                   .show();
  }
}
