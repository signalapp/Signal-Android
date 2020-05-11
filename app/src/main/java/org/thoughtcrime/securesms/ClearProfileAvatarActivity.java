package org.thoughtcrime.securesms;


import android.app.Activity;
import android.content.Intent;
import androidx.appcompat.app.AlertDialog;

public class ClearProfileAvatarActivity extends Activity {

  @Override
  public void onResume() {
    super.onResume();

    new AlertDialog.Builder(this)
        .setTitle(R.string.ClearProfileActivity_remove_profile_photo)
        .setNegativeButton(android.R.string.cancel, (dialog, which) -> finish())
        .setPositiveButton(R.string.ClearProfileActivity_remove, (dialog, which) -> {
          Intent result = new Intent();
          result.putExtra("delete", true);
          setResult(Activity.RESULT_OK, result);
          finish();
        })
        .show();
  }

}
