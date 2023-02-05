package org.thoughtcrime.securesms.profiles.manage;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.NavDirections;
import androidx.navigation.NavGraph;
import androidx.navigation.fragment.NavHostFragment;

import org.thoughtcrime.securesms.PassphraseRequiredActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.reactions.any.ReactWithAnyEmojiBottomSheetDialogFragment;
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.navigation.SafeNavigation;

/**
 * Activity that manages the local user's profile, as accessed via the settings.
 */
public class ManageProfileActivity extends PassphraseRequiredActivity implements ReactWithAnyEmojiBottomSheetDialogFragment.Callback {

  public static final int RESULT_BECOME_A_SUSTAINER = 12382;

  private final DynamicTheme dynamicTheme = new DynamicNoActionBarTheme();

  public static final String START_AT_USERNAME = "start_at_username";
  public static final String START_AT_AVATAR   = "start_at_avatar";

  public static @NonNull Intent getIntent(@NonNull Context context) {
    return new Intent(context, ManageProfileActivity.class);
  }

  public static @NonNull Intent getIntentForUsernameEdit(@NonNull Context context) {
    Intent intent = new Intent(context, ManageProfileActivity.class);
    intent.putExtra(START_AT_USERNAME, true);
    return intent;
  }

  public static @NonNull Intent getIntentForAvatarEdit(@NonNull Context context) {
    Intent intent = new Intent(context, ManageProfileActivity.class);
    intent.putExtra(START_AT_AVATAR, true);
    return intent;
  }

  @Override
  public void onCreate(Bundle bundle, boolean ready) {
    dynamicTheme.onCreate(this);

    setContentView(R.layout.manage_profile_activity);

    if (bundle == null) {
      Bundle   extras = getIntent().getExtras();

      //noinspection ConstantConditions
      NavController navController = ((NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment)).getNavController();

      NavGraph graph  = navController.getGraph();

      navController.setGraph(graph, extras != null ? extras : new Bundle());

      if (extras != null && extras.getBoolean(START_AT_USERNAME, false)) {
        if (SignalStore.uiHints().hasSeenUsernameEducation()) {
          NavDirections action = ManageProfileFragmentDirections.actionManageUsername();
          SafeNavigation.safeNavigate(navController, action);
        } else {
          NavDirections action = ManageProfileFragmentDirections.actionManageProfileFragmentToUsernameEducationFragment();
          SafeNavigation.safeNavigate(navController, action);
        }
      }

      if (extras != null && extras.getBoolean(START_AT_AVATAR, false)) {
        NavDirections action = ManageProfileFragmentDirections.actionManageProfileFragmentToAvatarPicker(null, null);
        SafeNavigation.safeNavigate(navController, action);
      }
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
  }

  @Override
  public void onReactWithAnyEmojiDialogDismissed() {
  }

  @Override
  public void onReactWithAnyEmojiSelected(@NonNull String emoji) {
    NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().getPrimaryNavigationFragment();
    Fragment        activeFragment  = navHostFragment.getChildFragmentManager().getPrimaryNavigationFragment();

    if (activeFragment instanceof EmojiController) {
      ((EmojiController) activeFragment).onEmojiSelected(emoji);
    }
  }

  interface EmojiController {
    void onEmojiSelected(@NonNull String emoji);
  }
}
