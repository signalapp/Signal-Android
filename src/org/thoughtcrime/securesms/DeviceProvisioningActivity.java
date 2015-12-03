package org.thoughtcrime.securesms;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.view.Window;

import org.thoughtcrime.securesms.crypto.MasterSecret;

public class DeviceProvisioningActivity extends PassphraseRequiredActionBarActivity {

  private static final String TAG = DeviceProvisioningActivity.class.getSimpleName();

  @Override
  protected void onPreCreate() {
    supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
  }

  @Override
  protected void onCreate(Bundle bundle, @NonNull MasterSecret masterSecret) {
    getSupportActionBar().hide();

    AlertDialog dialog = new AlertDialog.Builder(this)
        .setTitle(getString(R.string.DeviceProvisioningActivity_link_a_signal_device))
        .setMessage(getString(R.string.DeviceProvisioningActivity_it_looks_like_youre_trying_to_link_a_signal_device_using_a_3rd_party_scanner))
        .setPositiveButton(R.string.DeviceProvisioningActivity_continue, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            Intent intent = new Intent(DeviceProvisioningActivity.this, DeviceActivity.class);
            intent.putExtra("add", true);
            startActivity(intent);
            finish();
          }
        })
        .setNegativeButton(R.string.DeviceProvisioningActivity_cancel, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            dialog.dismiss();
            finish();
          }
        })
        .setOnDismissListener(new DialogInterface.OnDismissListener() {
          @Override
          public void onDismiss(DialogInterface dialog) {
            finish();
          }
        })
        .create();

    dialog.setIcon(getResources().getDrawable(R.drawable.icon_dialog));
    dialog.show();
  }
}
