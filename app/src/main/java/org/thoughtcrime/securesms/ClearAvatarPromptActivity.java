package org.thoughtcrime.securesms;


import android.app.Activity;
import android.content.Intent;
import android.view.ContextThemeWrapper;

import androidx.appcompat.app.AlertDialog;

import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.util.DynamicTheme;

public final class ClearAvatarPromptActivity extends Activity {

  private static final String ARG_TITLE = "arg_title";

  public static Intent createForUserProfilePhoto() {
    Intent intent = new Intent(ApplicationDependencies.getApplication(), ClearAvatarPromptActivity.class);
    intent.putExtra(ARG_TITLE, R.string.ClearProfileActivity_remove_profile_photo);
    return intent;
  }

  public static Intent createForGroupProfilePhoto() {
    Intent intent = new Intent(ApplicationDependencies.getApplication(), ClearAvatarPromptActivity.class);
    intent.putExtra(ARG_TITLE, R.string.ClearProfileActivity_remove_group_photo);
    return intent;
  }

  @Override
  public void onResume() {
    super.onResume();

    int message = getIntent().getIntExtra(ARG_TITLE, 0);

    new AlertDialog.Builder(new ContextThemeWrapper(this, DynamicTheme.isDarkTheme(this) ? R.style.TextSecure_DarkTheme : R.style.TextSecure_LightTheme))
                   .setMessage(message)
                   .setNegativeButton(android.R.string.cancel, (dialog, which) -> finish())
                   .setPositiveButton(R.string.ClearProfileActivity_remove, (dialog, which) -> {
                     Intent result = new Intent();
                     result.putExtra("delete", true);
                     setResult(Activity.RESULT_OK, result);
                     finish();
                   })
                   .setOnCancelListener(dialog -> finish())
                   .show();
  }

}
