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
import android.graphics.PorterDuff;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;

import org.thoughtcrime.securesms.help.HelpFragment;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.preferences.AdvancedPreferenceFragment;
import org.thoughtcrime.securesms.preferences.AppProtectionPreferenceFragment;
import org.thoughtcrime.securesms.preferences.AppearancePreferenceFragment;
import org.thoughtcrime.securesms.preferences.BackupsPreferenceFragment;
import org.thoughtcrime.securesms.preferences.ChatsPreferenceFragment;
import org.thoughtcrime.securesms.preferences.CorrectedPreferenceFragment;
import org.thoughtcrime.securesms.preferences.DataAndStoragePreferenceFragment;
import org.thoughtcrime.securesms.preferences.NotificationsPreferenceFragment;
import org.thoughtcrime.securesms.preferences.SmsMmsPreferenceFragment;
import org.thoughtcrime.securesms.preferences.widgets.ProfilePreference;
import org.thoughtcrime.securesms.preferences.widgets.UsernamePreference;
import org.thoughtcrime.securesms.profiles.edit.EditProfileActivity;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.util.CachedInflater;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.thoughtcrime.securesms.util.DynamicLanguage;
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

  private static final String PREFERENCE_CATEGORY_PROFILE        = "preference_category_profile";
  private static final String PREFERENCE_CATEGORY_USERNAME       = "preference_category_username";
  private static final String PREFERENCE_CATEGORY_SMS_MMS        = "preference_category_sms_mms";
  private static final String PREFERENCE_CATEGORY_NOTIFICATIONS  = "preference_category_notifications";
  private static final String PREFERENCE_CATEGORY_APP_PROTECTION = "preference_category_app_protection";
  private static final String PREFERENCE_CATEGORY_APPEARANCE     = "preference_category_appearance";
  private static final String PREFERENCE_CATEGORY_CHATS          = "preference_category_chats";
  private static final String PREFERENCE_CATEGORY_STORAGE        = "preference_category_storage";
  private static final String PREFERENCE_CATEGORY_DEVICES        = "preference_category_devices";
  private static final String PREFERENCE_CATEGORY_HELP           = "preference_category_help";
  private static final String PREFERENCE_CATEGORY_ADVANCED       = "preference_category_advanced";
  private static final String PREFERENCE_CATEGORY_DONATE         = "preference_category_donate";

  private static final String WAS_CONFIGURATION_UPDATED          = "was_configuration_updated";

  private final DynamicTheme    dynamicTheme    = new DynamicTheme();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private boolean wasConfigurationUpdated = false;

  @Override
  protected void onPreCreate() {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle icicle, boolean ready) {
    //noinspection ConstantConditions
    this.getSupportActionBar().setDisplayHomeAsUpEnabled(true);

    if (getIntent() != null && getIntent().getCategories() != null && getIntent().getCategories().contains("android.intent.category.NOTIFICATION_PREFERENCES")) {
      initFragment(android.R.id.content, new NotificationsPreferenceFragment());
    } else if (getIntent() != null && getIntent().getBooleanExtra(LAUNCH_TO_BACKUPS_FRAGMENT, false)) {
      initFragment(android.R.id.content, new BackupsPreferenceFragment());
    } else if (getIntent() != null && getIntent().getBooleanExtra(LAUNCH_TO_HELP_FRAGMENT, false)) {
      initFragment(android.R.id.content, new HelpFragment());
    } else if (icicle == null) {
      initFragment(android.R.id.content, new ApplicationPreferenceFragment());
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
    dynamicTheme.onResume(this);
    dynamicLanguage.onResume(this);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data)
  {
    super.onActivityResult(requestCode, resultCode, data);
    Fragment fragment = getSupportFragmentManager().findFragmentById(android.R.id.content);
    fragment.onActivityResult(requestCode, resultCode, data);
  }

  @Override
  public boolean onSupportNavigateUp() {
    FragmentManager fragmentManager = getSupportFragmentManager();
    if (fragmentManager.getBackStackEntryCount() > 0) {
      fragmentManager.popBackStack();
    } else {
      if (wasConfigurationUpdated) {
        setResult(MainActivity.RESULT_CONFIG_CHANGED);
      } else {
        setResult(RESULT_OK);
      }
      finish();
    }
    return true;
  }

  @Override
  public void onBackPressed() {
    onSupportNavigateUp();
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

  public void pushFragment(@NonNull Fragment fragment) {
    getSupportFragmentManager().beginTransaction()
                               .setCustomAnimations(R.anim.slide_from_end, R.anim.slide_to_start, R.anim.slide_from_start, R.anim.slide_to_end)
                               .replace(android.R.id.content, fragment)
                               .addToBackStack(null)
                               .commit();
  }

  public static class ApplicationPreferenceFragment extends CorrectedPreferenceFragment {

    @Override
    public void onCreate(Bundle icicle) {
      super.onCreate(icicle);

      this.findPreference(PREFERENCE_CATEGORY_PROFILE)
          .setOnPreferenceClickListener(new ProfileClickListener());
      this.findPreference(PREFERENCE_CATEGORY_USERNAME)
          .setOnPreferenceClickListener(new UsernameClickListener());
      this.findPreference(PREFERENCE_CATEGORY_SMS_MMS)
        .setOnPreferenceClickListener(new CategoryClickListener(PREFERENCE_CATEGORY_SMS_MMS));
      this.findPreference(PREFERENCE_CATEGORY_NOTIFICATIONS)
        .setOnPreferenceClickListener(new CategoryClickListener(PREFERENCE_CATEGORY_NOTIFICATIONS));
      this.findPreference(PREFERENCE_CATEGORY_APP_PROTECTION)
        .setOnPreferenceClickListener(new CategoryClickListener(PREFERENCE_CATEGORY_APP_PROTECTION));
      this.findPreference(PREFERENCE_CATEGORY_APPEARANCE)
        .setOnPreferenceClickListener(new CategoryClickListener(PREFERENCE_CATEGORY_APPEARANCE));
      this.findPreference(PREFERENCE_CATEGORY_CHATS)
        .setOnPreferenceClickListener(new CategoryClickListener(PREFERENCE_CATEGORY_CHATS));
      this.findPreference(PREFERENCE_CATEGORY_STORAGE)
        .setOnPreferenceClickListener(new CategoryClickListener(PREFERENCE_CATEGORY_STORAGE));
      this.findPreference(PREFERENCE_CATEGORY_DEVICES)
        .setOnPreferenceClickListener(new CategoryClickListener(PREFERENCE_CATEGORY_DEVICES));
      this.findPreference(PREFERENCE_CATEGORY_HELP)
          .setOnPreferenceClickListener(new CategoryClickListener(PREFERENCE_CATEGORY_HELP));
      this.findPreference(PREFERENCE_CATEGORY_ADVANCED)
          .setOnPreferenceClickListener(new CategoryClickListener(PREFERENCE_CATEGORY_ADVANCED));
      this.findPreference(PREFERENCE_CATEGORY_DONATE)
          .setOnPreferenceClickListener(new CategoryClickListener(PREFERENCE_CATEGORY_DONATE));

      tintIcons();
    }

    private void tintIcons() {
      if (Build.VERSION.SDK_INT >= 21) return;

      Preference preference = this.findPreference(PREFERENCE_CATEGORY_SMS_MMS);
      preference.getIcon().setColorFilter(ContextCompat.getColor(requireContext(), R.color.signal_icon_tint_primary), PorterDuff.Mode.SRC_IN);
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
    public void onResume() {
      super.onResume();
      //noinspection ConstantConditions
      ((ApplicationPreferencesActivity) getActivity()).getSupportActionBar().setTitle(R.string.text_secure_normal__menu_settings);
      setCategorySummaries();
      setCategoryVisibility();
    }

    private void setCategorySummaries() {
      ((ProfilePreference)this.findPreference(PREFERENCE_CATEGORY_PROFILE)).refresh();

      if (FeatureFlags.usernames()) {
        this.findPreference(PREFERENCE_CATEGORY_USERNAME)
            .setVisible(shouldDisplayUsernameReminder());
      }

      this.findPreference(PREFERENCE_CATEGORY_SMS_MMS)
          .setSummary(SmsMmsPreferenceFragment.getSummary(getActivity()));
      this.findPreference(PREFERENCE_CATEGORY_NOTIFICATIONS)
          .setSummary(NotificationsPreferenceFragment.getSummary(getActivity()));
      this.findPreference(PREFERENCE_CATEGORY_APP_PROTECTION)
          .setSummary(AppProtectionPreferenceFragment.getSummary(getActivity()));
      this.findPreference(PREFERENCE_CATEGORY_APPEARANCE)
          .setSummary(AppearancePreferenceFragment.getSummary(getActivity()));
      this.findPreference(PREFERENCE_CATEGORY_CHATS)
          .setSummary(ChatsPreferenceFragment.getSummary(getActivity()));
    }

    private void setCategoryVisibility() {
      Preference devicePreference = this.findPreference(PREFERENCE_CATEGORY_DEVICES);
      if (devicePreference != null && !TextSecurePreferences.isPushRegistered(getActivity())) {
        getPreferenceScreen().removePreference(devicePreference);
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
        case PREFERENCE_CATEGORY_SMS_MMS:
          fragment = new SmsMmsPreferenceFragment();
          break;
        case PREFERENCE_CATEGORY_NOTIFICATIONS:
          fragment = new NotificationsPreferenceFragment();
          break;
        case PREFERENCE_CATEGORY_APP_PROTECTION:
          fragment = new AppProtectionPreferenceFragment();
          break;
        case PREFERENCE_CATEGORY_APPEARANCE:
          fragment = new AppearancePreferenceFragment();
          break;
        case PREFERENCE_CATEGORY_CHATS:
          fragment = new ChatsPreferenceFragment();
          break;
        case PREFERENCE_CATEGORY_STORAGE:
          fragment = new DataAndStoragePreferenceFragment();
          break;
        case PREFERENCE_CATEGORY_DEVICES:
          Intent intent = new Intent(getActivity(), DeviceActivity.class);
          startActivity(intent);
          break;
        case PREFERENCE_CATEGORY_ADVANCED:
          fragment = new AdvancedPreferenceFragment();
          break;
        case PREFERENCE_CATEGORY_HELP:
          fragment = new HelpFragment();
          break;
        case PREFERENCE_CATEGORY_DONATE:
          CommunicationActions.openBrowserLink(requireContext(), getString(R.string.donate_url));
          break;
        default:
          throw new AssertionError();
        }

        if (fragment != null) {
          Bundle args = new Bundle();
          fragment.setArguments(args);

          ((ApplicationPreferencesActivity) requireActivity()).pushFragment(fragment);
        }

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
