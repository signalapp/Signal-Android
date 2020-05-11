package org.thoughtcrime.securesms.profiles.edit;


import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.navigation.NavGraph;
import androidx.navigation.Navigation;

import org.thoughtcrime.securesms.BaseActionBarActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.DynamicRegistrationTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;

@SuppressLint("StaticFieldLeak")
public class EditProfileActivity extends BaseActionBarActivity implements EditProfileFragment.Controller {

  public static final String NEXT_INTENT       = "next_intent";
  public static final String EXCLUDE_SYSTEM    = "exclude_system";
  public static final String DISPLAY_USERNAME  = "display_username";
  public static final String NEXT_BUTTON_TEXT  = "next_button_text";
  public static final String SHOW_TOOLBAR      = "show_back_arrow";

  private final DynamicTheme dynamicTheme = new DynamicRegistrationTheme();

  public static @NonNull Intent getIntent(@NonNull Context context, boolean showToolbar) {
    Intent intent = new Intent(context, EditProfileActivity.class);
    intent.putExtra(EditProfileActivity.SHOW_TOOLBAR, showToolbar);
    return intent;
  }

  @Override
  public void onCreate(Bundle bundle) {
    super.onCreate(bundle);

    dynamicTheme.onCreate(this);

    setContentView(R.layout.profile_create_activity);

    if (bundle == null) {
      Bundle   extras = getIntent().getExtras();
      NavGraph graph  = Navigation.findNavController(this, R.id.nav_host_fragment).getGraph();

      Navigation.findNavController(this, R.id.nav_host_fragment).setGraph(graph, extras != null ? extras : new Bundle());
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
  }

  @Override
  public void onProfileNameUploadCompleted() {
    setResult(RESULT_OK);
    finish();
  }
}
