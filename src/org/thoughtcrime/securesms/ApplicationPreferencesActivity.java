/**
 * Copyright (C) 2011 Whisper Systems
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
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.preference.PreferenceFragment;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.gcm.GoogleCloudMessaging;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.push.TextSecureCommunicationFactory;
import org.thoughtcrime.securesms.preferences.PreferenceFragmentAdvanced;
import org.thoughtcrime.securesms.preferences.PreferenceFragmentAppProtection;
import org.thoughtcrime.securesms.preferences.PreferenceFragmentAppearance;
import org.thoughtcrime.securesms.preferences.PreferenceFragmentNotifications;
import org.thoughtcrime.securesms.preferences.PreferenceFragmentSmsMms;
import org.thoughtcrime.securesms.preferences.PreferenceFragmentStorage;
import org.thoughtcrime.securesms.util.Dialogs;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.MemoryCleaner;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libaxolotl.util.guava.Optional;
import org.whispersystems.textsecure.api.TextSecureAccountManager;
import org.whispersystems.textsecure.api.push.exceptions.AuthorizationFailedException;

import java.io.IOException;

/**
 * The Activity for application preference display and management.
 *
 * @author Moxie Marlinspike
 *
 */

public class ApplicationPreferencesActivity extends PassphraseRequiredActionBarActivity
    implements SharedPreferences.OnSharedPreferenceChangeListener
{
  private static final String TAG = ApplicationPreferencesActivity.class.getSimpleName();

  public static final int PICK_IDENTITY_CONTACT        = 1;
  public static final int ENABLE_PASSPHRASE_ACTIVITY   = 2;

  private static final String PREFERENCE_CATEGORY_SMS_MMS        = "preference_category_sms_mms";
  private static final String PREFERENCE_CATEGORY_NOTIFICATIONS  = "preference_category_notifications";
  private static final String PREFERENCE_CATEGORY_APP_PROTECTION = "preference_category_app_protection";
  private static final String PREFERENCE_CATEGORY_APPEARANCE     = "preference_category_appearance";
  private static final String PREFERENCE_CATEGORY_STORAGE        = "preference_category_storage";
  private static final String PREFERENCE_CATEGORY_ADVANCED       = "preference_category_advanced";

  private static final String PUSH_MESSAGING_PREF = "pref_toggle_push_messaging";

  private final DynamicTheme    dynamicTheme    = new DynamicTheme();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  @Override
  protected void onCreate(Bundle icicle) {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
    super.onCreate(icicle);

    this.getSupportActionBar().setDisplayHomeAsUpEnabled(true);

    Fragment            fragment            = new ApplicationPreferenceFragment();
    FragmentManager     fragmentManager     = getSupportFragmentManager();
    FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
    fragmentTransaction.replace(android.R.id.content, fragment);
    fragmentTransaction.commit();
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
      Intent intent = new Intent(this, ConversationListActivity.class);
      intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
      startActivity(intent);
      finish();
    }
    return true;
  }

  @Override
  public void onDestroy() {
    MemoryCleaner.clean((MasterSecret) getIntent().getParcelableExtra("master_secret"));
    super.onDestroy();
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    if (key.equals(TextSecurePreferences.THEME_PREF)) {
      dynamicTheme.onResume(this);
    } else if (key.equals(TextSecurePreferences.LANGUAGE_PREF)) {
      dynamicLanguage.onResume(this);
    }
  }

  public static class ApplicationPreferenceFragment extends PreferenceFragment {
    @Override
    public void onCreate(Bundle icicle) {
      super.onCreate(icicle);

      addPreferencesFromResource(R.xml.preferences);

      initializePushMessagingToggle();

      this.findPreference(PREFERENCE_CATEGORY_SMS_MMS)
        .setOnPreferenceClickListener(new CategoryClickListener(PREFERENCE_CATEGORY_SMS_MMS));
      this.findPreference(PREFERENCE_CATEGORY_NOTIFICATIONS)
        .setOnPreferenceClickListener(new CategoryClickListener(PREFERENCE_CATEGORY_NOTIFICATIONS));
      this.findPreference(PREFERENCE_CATEGORY_APP_PROTECTION)
        .setOnPreferenceClickListener(new CategoryClickListener(PREFERENCE_CATEGORY_APP_PROTECTION));
      this.findPreference(PREFERENCE_CATEGORY_APPEARANCE)
        .setOnPreferenceClickListener(new CategoryClickListener(PREFERENCE_CATEGORY_APPEARANCE));
      this.findPreference(PREFERENCE_CATEGORY_STORAGE)
        .setOnPreferenceClickListener(new CategoryClickListener(PREFERENCE_CATEGORY_STORAGE));
      this.findPreference(PREFERENCE_CATEGORY_ADVANCED)
        .setOnPreferenceClickListener(new CategoryClickListener(PREFERENCE_CATEGORY_ADVANCED));
    }

    @Override
    public void onResume() {
      super.onResume();
      ((ApplicationPreferencesActivity) getActivity()).getSupportActionBar().setTitle(R.string.text_secure_normal__menu_settings);
      setCategorySummaries();
    }

    private void setCategorySummaries() {
      this.findPreference(PREFERENCE_CATEGORY_SMS_MMS)
          .setSummary(PreferenceFragmentSmsMms.getSummary(getActivity()));
      this.findPreference(PREFERENCE_CATEGORY_NOTIFICATIONS)
          .setSummary(PreferenceFragmentNotifications.getSummary(getActivity()));
      this.findPreference(PREFERENCE_CATEGORY_APP_PROTECTION)
          .setSummary(PreferenceFragmentAppProtection.getSummary(getActivity()));
      this.findPreference(PREFERENCE_CATEGORY_APPEARANCE)
          .setSummary(PreferenceFragmentAppearance.getSummary(getActivity()));
      this.findPreference(PREFERENCE_CATEGORY_STORAGE)
          .setSummary(PreferenceFragmentStorage.getSummary(getActivity()));
    }

    private class CategoryClickListener implements Preference.OnPreferenceClickListener {
      private String category;

      public CategoryClickListener(String category) {
        this.category = category;
      }

      @Override
      public boolean onPreferenceClick(Preference preference) {
        Fragment fragment = null;

        switch (category) {
        case PREFERENCE_CATEGORY_SMS_MMS:
          fragment = new PreferenceFragmentSmsMms();
          break;
        case PREFERENCE_CATEGORY_NOTIFICATIONS:
          fragment = new PreferenceFragmentNotifications();
          break;
        case PREFERENCE_CATEGORY_APP_PROTECTION:
          fragment = new PreferenceFragmentAppProtection();
          break;
        case PREFERENCE_CATEGORY_APPEARANCE:
          fragment = new PreferenceFragmentAppearance();
          break;
        case PREFERENCE_CATEGORY_STORAGE:
          fragment = new PreferenceFragmentStorage();
          break;
        case PREFERENCE_CATEGORY_ADVANCED:
          fragment = new PreferenceFragmentAdvanced();
          break;
        default:
          throw new AssertionError();
        }

        FragmentManager     fragmentManager     = getActivity().getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(android.R.id.content, fragment);
        fragmentTransaction.addToBackStack(null);
        fragmentTransaction.commit();

        return true;
      }
    }

    private void initializePushMessagingToggle() {
      CheckBoxPreference preference = (CheckBoxPreference)this.findPreference(PUSH_MESSAGING_PREF);
      preference.setChecked(TextSecurePreferences.isPushRegistered(getActivity()));
      preference.setOnPreferenceChangeListener(new PushMessagingClickListener());
    }

    private class PushMessagingClickListener implements Preference.OnPreferenceChangeListener {

      private static final int SUCCESS       = 0;
      private static final int NETWORK_ERROR = 1;

      private class DisablePushMessagesTask extends AsyncTask<Void, Void, Integer> {
        private ProgressDialog dialog;
        private final Preference preference;

        public DisablePushMessagesTask(final Preference preference) {
          this.preference = preference;
        }

        @Override
        protected void onPreExecute() {
          dialog = ProgressDialog.show(getActivity(),
            getString(R.string.ApplicationPreferencesActivity_unregistering),
            getString(R.string.ApplicationPreferencesActivity_unregistering_for_data_based_communication),
            true, false);
        }

        @Override
        protected void onPostExecute(Integer result) {
          if (dialog != null)
            dialog.dismiss();

          switch (result) {
          case NETWORK_ERROR:
            Toast.makeText(getActivity(),
              R.string.ApplicationPreferencesActivity_error_connecting_to_server,
              Toast.LENGTH_LONG).show();
            break;
          case SUCCESS:
            ((CheckBoxPreference)preference).setChecked(false);
            TextSecurePreferences.setPushRegistered(getActivity(), false);
            break;
          }
        }

        @Override
        protected Integer doInBackground(Void... params) {
          try {
            Context                  context        = getActivity();
            TextSecureAccountManager accountManager = TextSecureCommunicationFactory.createManager(context);

            accountManager.setGcmId(Optional.<String>absent());
            GoogleCloudMessaging.getInstance(context).unregister();

            return SUCCESS;
          } catch (AuthorizationFailedException afe) {
            Log.w(TAG, afe);
            return SUCCESS;
          } catch (IOException ioe) {
            Log.w(TAG, ioe);
            return NETWORK_ERROR;
          }
        }
      }

      @Override
      public boolean onPreferenceChange(final Preference preference, Object newValue) {
        if (((CheckBoxPreference)preference).isChecked()) {
          AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
          builder.setIcon(Dialogs.resolveIcon(getActivity(), R.attr.dialog_info_icon));
          builder.setTitle(R.string.ApplicationPreferencesActivity_disable_push_messages);
          builder.setMessage(R.string.ApplicationPreferencesActivity_this_will_disable_push_messages);
          builder.setNegativeButton(android.R.string.cancel, null);
          builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
              new DisablePushMessagesTask(preference).execute();
            }
          });
          builder.show();
        } else {
          Intent nextIntent = new Intent(getActivity(), ApplicationPreferencesActivity.class);
          nextIntent.putExtra("master_secret", getActivity().getIntent().getParcelableExtra("master_secret"));

          Intent intent = new Intent(getActivity(), RegistrationActivity.class);
          intent.putExtra("cancel_button", true);
          intent.putExtra("next_intent", nextIntent);
          intent.putExtra("master_secret", getActivity().getIntent().getParcelableExtra("master_secret"));
          startActivity(intent);
        }

        return false;
      }
    }

    private void initializeEditTextSummary(final EditTextPreference preference) {
      if (preference.getText() == null) {
        preference.setSummary("Not set");
      } else {
        preference.setSummary(preference.getText());
      }

      preference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference pref, Object newValue) {
          preference.setSummary(newValue == null ? "Not set" : ((String) newValue));
          return true;
        }
      });
    }

    /* http://code.google.com/p/android/issues/detail?id=4611#c35 */
    @SuppressWarnings("deprecation")
    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference)
    {
      super.onPreferenceTreeClick(preferenceScreen, preference);
      if (preference!=null)
        if (preference instanceof PreferenceScreen)
          if (((PreferenceScreen)preference).getDialog()!=null)
            ((PreferenceScreen) preference).getDialog().getWindow().getDecorView().setBackgroundDrawable(getActivity().getWindow().getDecorView().getBackground().getConstantState().newDrawable());
      return false;
    }
  }
}
