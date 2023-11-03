package org.thoughtcrime.securesms.profiles.edit;


import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;

import org.thoughtcrime.securesms.BaseActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.util.DynamicRegistrationTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;

/**
 * Shows editing screen for your profile during registration. Also handles group name editing.
 */
public class CreateProfileActivity extends BaseActivity implements CreateProfileFragment.Controller {

  public static final String NEXT_INTENT       = "next_intent";
  public static final String EXCLUDE_SYSTEM    = "exclude_system";
  public static final String NEXT_BUTTON_TEXT  = "next_button_text";
  public static final String SHOW_TOOLBAR      = "show_back_arrow";
  public static final String GROUP_ID          = "group_id";

  private final DynamicTheme dynamicTheme = new DynamicRegistrationTheme();

  public static @NonNull Intent getIntentForUserProfile(@NonNull Context context) {
    Intent intent = new Intent(context, CreateProfileActivity.class);
    intent.putExtra(CreateProfileActivity.SHOW_TOOLBAR, false);
    return intent;
  }

  public static @NonNull Intent getIntentForGroupProfile(@NonNull Context context, @NonNull GroupId groupId) {
    Intent intent = new Intent(context, CreateProfileActivity.class);
    intent.putExtra(CreateProfileActivity.SHOW_TOOLBAR, true);
    intent.putExtra(CreateProfileActivity.GROUP_ID, groupId.toString());
    intent.putExtra(CreateProfileActivity.NEXT_BUTTON_TEXT, R.string.save);
    return intent;
  }

  @Override
  public void onCreate(Bundle bundle) {
    super.onCreate(bundle);

    dynamicTheme.onCreate(this);

    setContentView(R.layout.create_profile_activity);

    if (bundle == null) {
      NavHostFragment fragment = NavHostFragment.create(R.navigation.create_profile, getIntent().getExtras());
      getSupportFragmentManager().beginTransaction()
                                 .add(R.id.fragment_container, fragment)
                                 .commit();
    }

    getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
      @Override public void handleOnBackPressed() {
        if (!Navigation.findNavController(CreateProfileActivity.this, R.id.fragment_container).popBackStack()) {
          finish();
        }
      }
    });
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
