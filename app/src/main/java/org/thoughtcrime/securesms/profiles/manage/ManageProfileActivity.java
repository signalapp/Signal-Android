package org.thoughtcrime.securesms.profiles.manage;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.navigation.NavDirections;
import androidx.navigation.NavGraph;
import androidx.navigation.Navigation;

import org.thoughtcrime.securesms.BaseActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.profiles.edit.EditProfileActivity;
import org.thoughtcrime.securesms.profiles.edit.EditProfileFragmentDirections;
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;

/**
 * Activity that manages the local user's profile, as accessed via the settings.
 */
public class ManageProfileActivity extends BaseActivity {

  private final DynamicTheme dynamicTheme = new DynamicNoActionBarTheme();

  public static final String START_AT_USERNAME = "start_at_username";

  public static @NonNull Intent getIntent(@NonNull Context context) {
    return new Intent(context, ManageProfileActivity.class);
  }

  public static @NonNull Intent getIntentForUsernameEdit(@NonNull Context context) {
    Intent intent = new Intent(context, ManageProfileActivity.class);
    intent.putExtra(START_AT_USERNAME, true);
    return intent;
  }

  @Override
  public void onCreate(Bundle bundle) {
    super.onCreate(bundle);

    dynamicTheme.onCreate(this);

    setContentView(R.layout.manage_profile_activity);

    if (bundle == null) {
      Bundle   extras = getIntent().getExtras();
      NavGraph graph  = Navigation.findNavController(this, R.id.nav_host_fragment).getGraph();

      Navigation.findNavController(this, R.id.nav_host_fragment).setGraph(graph, extras != null ? extras : new Bundle());

      if (extras != null && extras.getBoolean(START_AT_USERNAME, false)) {
        NavDirections action = ManageProfileFragmentDirections.actionManageUsername();
        Navigation.findNavController(this, R.id.nav_host_fragment).navigate(action);
      }
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
  }
}
