/*
 * Copyright (C) 2011 Whisper Systems
 * Copyright (C) 2013-2017 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import org.thoughtcrime.securesms.components.mp02anim.ItemAnimViewController;
import org.thoughtcrime.securesms.help.HelpFragment;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.preferences.AdvancedPreferenceFragment;
import org.thoughtcrime.securesms.preferences.AppProtectionPreferenceFragment;
import org.thoughtcrime.securesms.preferences.BackupsPreferenceFragment;
import org.thoughtcrime.securesms.preferences.ChatsPreferenceFragment;
import org.thoughtcrime.securesms.preferences.CorrectedPreferenceFragment;
import org.thoughtcrime.securesms.preferences.NotificationsPreferenceFragment;
import org.thoughtcrime.securesms.preferences.SmsMmsPreferenceFragment;
import org.thoughtcrime.securesms.preferences.StoragePreferenceFragment;
import org.thoughtcrime.securesms.preferences.widgets.UsernamePreference;
import org.thoughtcrime.securesms.profiles.edit.EditProfileActivity;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.util.CachedInflater;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

/**
 * The Activity for application preference display and management.
 *
 * @author Moxie Marlinspike
 *
 */

public class ApplicationPreferencesActivity extends PassphraseRequiredActivity
    implements SharedPreferences.OnSharedPreferenceChangeListener
{
  public static final String LAUNCH_TO_BACKUPS_FRAGMENT = "launch.to.backups.fragment";
  public static final String LAUNCH_TO_HELP_FRAGMENT    = "launch.to.help.fragment";

  @SuppressWarnings("unused")
  private static final String TAG = ApplicationPreferencesActivity.class.getSimpleName();

  private static final String PREFERENCE_CATEGORY_USERNAME       = "preference_category_username";
  private static final String PREFERENCE_CATEGORY_PROFILE = "preference_category_profile";
  private static final String PREFERENCE_CATEGORY_SMS_MMS = "preference_category_sms_mms";
  private static final String PREFERENCE_CATEGORY_NOTIFICATIONS = "preference_category_notifications";
  private static final String PREFERENCE_CATEGORY_APP_PROTECTION = "preference_category_app_protection";
  private static final String PREFERENCE_CATEGORY_CHATS = "preference_category_chats";
  private static final String PREFERENCE_CATEGORY_STORAGE = "preference_category_storage";
  private static final String PREFERENCE_CATEGORY_ADVANCED = "preference_category_advanced";

  private static final String WAS_CONFIGURATION_UPDATED          = "was_configuration_updated";

  private static ApplicationPreferenceFragment applicationPreferenceFragment;
  private static int focus;

  private boolean wasConfigurationUpdated = false;

  @Override
  protected void onPreCreate() {
  }

  @Override
  protected void onCreate(Bundle icicle, boolean ready) {
    if (getIntent() != null && getIntent().getCategories() != null && getIntent().getCategories().contains("android.intent.category.NOTIFICATION_PREFERENCES")) {
      initFragment(android.R.id.content, new NotificationsPreferenceFragment());
    } else if (getIntent() != null && getIntent().getBooleanExtra(LAUNCH_TO_BACKUPS_FRAGMENT, false)) {
      initFragment(android.R.id.content, new BackupsPreferenceFragment());
    } else if (getIntent() != null && getIntent().getBooleanExtra(LAUNCH_TO_HELP_FRAGMENT, false)) {
      initFragment(android.R.id.content, new HelpFragment());
    } else if (icicle == null) {
      applicationPreferenceFragment=new ApplicationPreferenceFragment();
      initFragment(android.R.id.content, applicationPreferenceFragment);
    } else {
      wasConfigurationUpdated = icicle.getBoolean(WAS_CONFIGURATION_UPDATED);
    }
  }

  @Override
  protected void onSaveInstanceState(@NonNull Bundle outState) {
    outState.putBoolean(WAS_CONFIGURATION_UPDATED, wasConfigurationUpdated);
    super.onSaveInstanceState(outState);
  }

  @Override
  public void onResume() {
    super.onResume();
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data)
  {
    super.onActivityResult(requestCode, resultCode, data);
    Fragment fragment = getSupportFragmentManager().findFragmentById(android.R.id.content);
    fragment.onActivityResult(requestCode, resultCode, data);
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    if (key.equals(TextSecurePreferences.THEME_PREF)) {
      DynamicTheme.setDefaultDayNightMode(this);
      recreate();
    } else if (key.equals(TextSecurePreferences.LANGUAGE_PREF)) {
      CachedInflater.from(this).clear();
      wasConfigurationUpdated = true;
      recreate();

      Intent intent = new Intent(this, KeyCachingService.class);
      intent.setAction(KeyCachingService.LOCALE_CHANGE_EVENT);
      startService(intent);
    }
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    Fragment fragment = getSupportFragmentManager().findFragmentById(android.R.id.content);
//    switch (keyCode) {
//      case KeyEvent.KEYCODE_DPAD_DOWN:
//      case KeyEvent.KEYCODE_DPAD_UP:
//        if (fragment instanceof ApplicationPreferenceFragment) {
//          return ((ApplicationPreferenceFragment) fragment).onKeyDown(event);
//        } else if (fragment instanceof SmsMmsPreferenceFragment) {
//          return ((SmsMmsPreferenceFragment) fragment).onKeyDown(event);
//        } else if (fragment instanceof NotificationsPreferenceFragment) {
//          return ((NotificationsPreferenceFragment) fragment).onKeyDown(event);
//        } else if (fragment instanceof AppProtectionPreferenceFragment) {
//          return ((AppProtectionPreferenceFragment) fragment).onKeyDown(event);
//        } else if (fragment instanceof ChatsPreferenceFragment) {
//          return ((ChatsPreferenceFragment) fragment).onKeyDown(event);
//        } else if (fragment instanceof StoragePreferenceFragment) {
//          return ((StoragePreferenceFragment) fragment).onKeyDown(event);
//        }
//        break;
//    }
    return super.onKeyDown(keyCode, event);
  }

  public void pushFragment(@NonNull Fragment fragment) {
    getSupportFragmentManager().beginTransaction()
                               .setCustomAnimations(R.anim.slide_from_end, R.anim.slide_to_start, R.anim.slide_from_start, R.anim.slide_to_end)
                               .replace(android.R.id.content, fragment)
                               .addToBackStack(null)
                               .commit();
  }

  public static class ApplicationPreferenceFragment extends CorrectedPreferenceFragment {

    private PreferenceGroup mPrefGroup;
    private int mPrefCount;
    private int mCurPoi = 0;
    private ItemAnimViewController mParentViewController;

    @Override
    public void onCreate(Bundle icicle) {
      super.onCreate(icicle);

      this.findPreference(PREFERENCE_CATEGORY_PROFILE)
              .setOnPreferenceClickListener(new ProfileClickListener());
//      this.findPreference(PREFERENCE_CATEGORY_SMS_MMS)
//              .setOnPreferenceClickListener(new CategoryClickListener(PREFERENCE_CATEGORY_SMS_MMS));
      this.findPreference(PREFERENCE_CATEGORY_NOTIFICATIONS)
              .setOnPreferenceClickListener(new CategoryClickListener(PREFERENCE_CATEGORY_NOTIFICATIONS));
      this.findPreference(PREFERENCE_CATEGORY_APP_PROTECTION)
              .setOnPreferenceClickListener(new CategoryClickListener(PREFERENCE_CATEGORY_APP_PROTECTION));
      this.findPreference(PREFERENCE_CATEGORY_CHATS)
              .setOnPreferenceClickListener(new CategoryClickListener(PREFERENCE_CATEGORY_CHATS));
      this.findPreference(PREFERENCE_CATEGORY_STORAGE)
              .setOnPreferenceClickListener(new CategoryClickListener(PREFERENCE_CATEGORY_STORAGE));
      this.findPreference(PREFERENCE_CATEGORY_ADVANCED)
              .setOnPreferenceClickListener(new CategoryClickListener(PREFERENCE_CATEGORY_ADVANCED));
      focus = 0;
      mPrefGroup = getPreferenceGroup();
      mPrefCount = mPrefGroup.getPreferenceCount();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
      View view = super.onCreateView(inflater, container, savedInstanceState);
      view.findViewById(android.R.id.list_container).setBackgroundColor(getResources().getColor(R.color.sim_background));
      setFocus(focus);
//      mParentViewController = getParentAnimViewController();
//      changeView(mCurPoi, false);
      return view;
    }

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, String rootKey) {
      addPreferencesFromResource(R.xml.preferences);

      if (FeatureFlags.usernames()) {
        UsernamePreference pref = (UsernamePreference) findPreference(PREFERENCE_CATEGORY_USERNAME);
        pref.setVisible(shouldDisplayUsernameReminder());
        pref.setOnLongClickListener(v -> {
          new AlertDialog.Builder(requireContext())
                         .setMessage(R.string.ApplicationPreferencesActivity_hide_reminder)
                         .setPositiveButton(R.string.ApplicationPreferencesActivity_hide, (dialog, which) -> {
                           dialog.dismiss();
                           SignalStore.misc().hideUsernameReminder();
                             findPreference(PREFERENCE_CATEGORY_USERNAME).setVisible(false);
                         })
                         .setNegativeButton(android.R.string.cancel, ((dialog, which) -> dialog.dismiss()))
                         .setCancelable(true)
                         .show();
          return true;
        });
      }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
      super.onActivityCreated(savedInstanceState);
    }

    @Override
    public boolean onKeyDown(KeyEvent event) {
      View v = getListView().getFocusedChild();
      if (v != null) {
        mCurPoi = (int) v.getTag();
      }
      switch (event.getKeyCode()) {
        case KeyEvent.KEYCODE_DPAD_DOWN:
          if (mCurPoi < mPrefCount - 1) {
            mCurPoi++;
            changeView(mCurPoi, true);
          }
          break;
        case KeyEvent.KEYCODE_DPAD_UP:
          if (mCurPoi > 0) {
            mCurPoi--;
            changeView(mCurPoi, false);
          }
          break;
      }
      return super.onKeyDown(event);
    }

    private void changeView(int currentPosition, boolean b) {
      Preference preference = mPrefGroup.getPreference(currentPosition);
      Preference preference1 = null;
      Preference preference2 = null;
      if (currentPosition > 0)
        preference1 = mPrefGroup.getPreference(currentPosition - 1);
      if (currentPosition < mPrefCount - 1)
        preference2 = mPrefGroup.getPreference(currentPosition + 1);

      String curTitle = "";
      String title1 = "";
      String title2 = "";

      curTitle = preference.getTitle().toString();
      if (preference1 != null) {

        title1 = preference1.getTitle().toString();
      }
      if (preference2 != null) {
        title2 = preference2.getTitle().toString();
      }

      if (b) {
        mParentViewController.actionUpIn(title1, curTitle);
      } else {
        mParentViewController.actionDownIn(title2, curTitle);
      }
    }

    private static boolean shouldDisplayUsernameReminder() {
      return FeatureFlags.usernames() && !Recipient.self().getUsername().isPresent() && SignalStore.misc().shouldShowUsernameReminder();
    }

    private class CategoryClickListener implements Preference.OnPreferenceClickListener {
      private String category;

      CategoryClickListener(String category) {
        this.category = category;
      }

      @Override
      public boolean onPreferenceClick(Preference preference) {
        Fragment fragment = null;

        switch (category) {
//          case PREFERENCE_CATEGORY_SMS_MMS:
//            fragment = new SmsMmsPreferenceFragment();
//            break;
          case PREFERENCE_CATEGORY_NOTIFICATIONS:
            fragment = new NotificationsPreferenceFragment();
            focus=1;
            break;
          case PREFERENCE_CATEGORY_APP_PROTECTION:
            fragment = new AppProtectionPreferenceFragment();
            focus=2;
            break;
          case PREFERENCE_CATEGORY_CHATS:
            fragment = new ChatsPreferenceFragment();
            focus=3;
            break;
          case PREFERENCE_CATEGORY_STORAGE:
            fragment = new StoragePreferenceFragment();
            focus=4;
            break;
          case PREFERENCE_CATEGORY_ADVANCED:
            fragment = new AdvancedPreferenceFragment();
            focus=5;
            break;
          default:
            throw new AssertionError();
        }

        Bundle args = new Bundle();
        fragment.setArguments(args);
        FragmentManager fragmentManager = getActivity().getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(android.R.id.content, fragment);
        fragmentTransaction.addToBackStack(null);
        fragmentTransaction.commit();
        return true;
      }
    }

    private class ProfileClickListener implements Preference.OnPreferenceClickListener {
      @Override
      public boolean onPreferenceClick(Preference preference) {
        requireActivity().startActivity(EditProfileActivity.getIntentForUserProfileEdit(preference.getContext()));
        return true;
      }
    }

    private class UsernameClickListener implements Preference.OnPreferenceClickListener {
      @Override
      public boolean onPreferenceClick(Preference preference) {
        requireActivity().startActivity(EditProfileActivity.getIntentForUsernameEdit(preference.getContext()));
        return true;
      }
    }
  }
}
