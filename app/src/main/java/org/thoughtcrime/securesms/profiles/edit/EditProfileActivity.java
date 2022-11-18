package org.thoughtcrime.securesms.profiles.edit;


import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import androidx.navigation.fragment.NavHostFragment;

import org.thoughtcrime.securesms.BaseActivity;
import org.thoughtcrime.securesms.MainNavigator;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.DynamicRegistrationTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;

/**
 * Shows editing screen for your profile during registration. Also handles group name editing.
 */
@SuppressLint("StaticFieldLeak")
public class EditProfileActivity extends BaseActivity implements EditProfileFragment.Controller {

  public static final String NEXT_INTENT       = "next_intent";
  public static final String EXCLUDE_SYSTEM    = "exclude_system";
  public static final String NEXT_BUTTON_TEXT  = "next_button_text";
  public static final String SHOW_TOOLBAR      = "show_back_arrow";
  public static final String GROUP_ID          = "group_id";

  private final DynamicTheme dynamicTheme = new DynamicRegistrationTheme();

  public static @NonNull Intent getIntentForUserProfile(@NonNull Context context) {
    Intent intent = new Intent(context, EditProfileActivity.class);
    intent.putExtra(EditProfileActivity.SHOW_TOOLBAR, false);
    return intent;
  }

  public static @NonNull Intent getIntentForUserProfileEdit(@NonNull Context context) {
    Intent intent = new Intent(context, EditProfileActivity.class);
    intent.putExtra(EditProfileActivity.EXCLUDE_SYSTEM, true);
    intent.putExtra(EditProfileActivity.NEXT_BUTTON_TEXT, R.string.save);
    return intent;
  }

  public static @NonNull Intent getIntentForGroupProfile(@NonNull Context context, @NonNull GroupId groupId) {
    Intent intent = new Intent(context, EditProfileActivity.class);
    intent.putExtra(EditProfileActivity.SHOW_TOOLBAR, false);
    intent.putExtra(EditProfileActivity.GROUP_ID, groupId.toString());
    intent.putExtra(EditProfileActivity.NEXT_BUTTON_TEXT, R.string.save);
    return intent;
  }

  @Override
  public void onCreate(Bundle bundle) {
    super.onCreate(bundle);

    dynamicTheme.onCreate(this);

    setContentView(R.layout.profile_create_activity);

    if (bundle == null) {
      Bundle   extras = getIntent().getExtras();
      NavHostFragment fragment = NavHostFragment.create(R.navigation.edit_profile, extras != null ? extras : new Bundle());
      getSupportFragmentManager().beginTransaction()
              .add(R.id.fragment_container, fragment)
              .commit();
    }
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    Fragment navFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
    if (navFragment == null) {
      return false;
    }
    Fragment fragment = navFragment.getChildFragmentManager().getPrimaryNavigationFragment();
    int code = event.getKeyCode();

    switch (code) {
      case KeyEvent.KEYCODE_CALL:
        if (fragment instanceof EditProfileFragment){
          ((EditProfileFragment)fragment).onKeyDown(code);
        }
        break;
      case KeyEvent.KEYCODE_BACK:
        if(Recipient.self().getProfileName().toString().equals("")){
          setResult(MainNavigator.PROFILE_EMPTY);
        }
        break;
    }
    return super.dispatchKeyEvent(event);
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

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_CALL){

    }
    return super.onKeyDown(keyCode, event);
  }
}
