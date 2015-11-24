package org.thoughtcrime.securesms.preferences;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.preference.PreferenceFragment;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.gcm.GoogleCloudMessaging;

import org.thoughtcrime.redphone.signaling.RedPhoneAccountManager;
import org.thoughtcrime.redphone.signaling.RedPhoneTrustStore;
import org.thoughtcrime.redphone.signaling.UnauthorizedException;
import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.LogSubmitActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.RegistrationActivity;
import org.thoughtcrime.securesms.contacts.ContactAccessor;
import org.thoughtcrime.securesms.contacts.ContactIdentityManager;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.push.TextSecureCommunicationFactory;
import org.thoughtcrime.securesms.util.ProgressDialogAsyncTask;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libaxolotl.util.guava.Optional;
import org.whispersystems.textsecure.api.TextSecureAccountManager;
import org.whispersystems.textsecure.api.push.exceptions.AuthorizationFailedException;

import java.io.IOException;

public class AdvancedPreferenceFragment extends PreferenceFragment {
  private static final String TAG = AdvancedPreferenceFragment.class.getSimpleName();

  private static final String PUSH_MESSAGING_PREF   = "pref_toggle_push_messaging";
  private static final String SUBMIT_DEBUG_LOG_PREF = "pref_submit_debug_logs";

  private static final int PICK_IDENTITY_CONTACT = 1;

  private MasterSecret masterSecret;

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);
    masterSecret = getArguments().getParcelable("master_secret");
    addPreferencesFromResource(R.xml.preferences_advanced);

    initializeIdentitySelection();

    Preference submitDebugLog = this.findPreference(SUBMIT_DEBUG_LOG_PREF);
    submitDebugLog.setOnPreferenceClickListener(new SubmitDebugLogListener());
    submitDebugLog.setSummary(getVersion(getActivity()));
  }

  @Override
  public void onResume() {
    super.onResume();
    ((ApplicationPreferencesActivity) getActivity()).getSupportActionBar().setTitle(R.string.preferences__advanced);

    initializePushMessagingToggle();
  }

  @Override
  public void onActivityResult(int reqCode, int resultCode, Intent data) {
    super.onActivityResult(reqCode, resultCode, data);

    Log.w(TAG, "Got result: " + resultCode + " for req: " + reqCode);
    if (resultCode == Activity.RESULT_OK && reqCode == PICK_IDENTITY_CONTACT) {
      handleIdentitySelection(data);
    }
  }

  private void initializePushMessagingToggle() {
    CheckBoxPreference preference = (CheckBoxPreference)this.findPreference(PUSH_MESSAGING_PREF);

    if (TextSecurePreferences.isPushRegistered(getActivity())) {
      preference.setChecked(true);
      preference.setSummary(TextSecurePreferences.getLocalNumber(getActivity()));
    } else {
      preference.setChecked(false);
      preference.setSummary(R.string.preferences__free_private_messages_and_calls);
    }

    preference.setOnPreferenceChangeListener(new PushMessagingClickListener());
  }

  private void initializeIdentitySelection() {
    ContactIdentityManager identity = ContactIdentityManager.getInstance(getActivity());

    Preference preference = this.findPreference(TextSecurePreferences.IDENTITY_PREF);

    if (identity.isSelfIdentityAutoDetected()) {
      this.getPreferenceScreen().removePreference(preference);
    } else {
      Uri contactUri = identity.getSelfIdentityUri();

      if (contactUri != null) {
        String contactName = ContactAccessor.getInstance().getNameFromContact(getActivity(), contactUri);
        preference.setSummary(String.format(getString(R.string.ApplicationPreferencesActivity_currently_s),
                                            contactName));
      }

      preference.setOnPreferenceClickListener(new IdentityPreferenceClickListener());
    }
  }

  private @NonNull String getVersion(@Nullable Context context) {
    try {
      if (context == null) return "";

      String app     = context.getString(R.string.app_name);
      String version = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;

      return String.format("%s %s", app, version);
    } catch (PackageManager.NameNotFoundException e) {
      Log.w(TAG, e);
      return context.getString(R.string.app_name);
    }
  }

  private class IdentityPreferenceClickListener implements Preference.OnPreferenceClickListener {
    @Override
    public boolean onPreferenceClick(Preference preference) {
      Intent intent = new Intent(Intent.ACTION_PICK);
      intent.setType(ContactsContract.Contacts.CONTENT_TYPE);
      startActivityForResult(intent, PICK_IDENTITY_CONTACT);
      return true;
    }
  }

  private void handleIdentitySelection(Intent data) {
    Uri contactUri = data.getData();

    if (contactUri != null) {
      TextSecurePreferences.setIdentityContactUri(getActivity(), contactUri.toString());
      initializeIdentitySelection();
    }
  }

  private class SubmitDebugLogListener implements Preference.OnPreferenceClickListener {
    @Override
    public boolean onPreferenceClick(Preference preference) {
      final Intent intent = new Intent(getActivity(), LogSubmitActivity.class);
      startActivity(intent);
      return true;
    }
  }

  private class PushMessagingClickListener implements Preference.OnPreferenceChangeListener {
    private static final int SUCCESS       = 0;
    private static final int NETWORK_ERROR = 1;

    private class DisablePushMessagesTask extends ProgressDialogAsyncTask<Void, Void, Integer> {
      private final CheckBoxPreference checkBoxPreference;

      public DisablePushMessagesTask(final CheckBoxPreference checkBoxPreference) {
        super(getActivity(), R.string.ApplicationPreferencesActivity_unregistering, R.string.ApplicationPreferencesActivity_unregistering_from_signal_messages_and_calls);
        this.checkBoxPreference = checkBoxPreference;
      }

      @Override
      protected void onPostExecute(Integer result) {
        super.onPostExecute(result);
        switch (result) {
        case NETWORK_ERROR:
          Toast.makeText(getActivity(),
                         R.string.ApplicationPreferencesActivity_error_connecting_to_server,
                         Toast.LENGTH_LONG).show();
          break;
        case SUCCESS:
          TextSecurePreferences.setPushRegistered(getActivity(), false);
          initializePushMessagingToggle();
          break;
        }
      }

      @Override
      protected Integer doInBackground(Void... params) {
        try {
          Context                  context                = getActivity();
          TextSecureAccountManager accountManager         = TextSecureCommunicationFactory.createManager(context);
          RedPhoneAccountManager   redPhoneAccountManager = new RedPhoneAccountManager(BuildConfig.REDPHONE_MASTER_URL,
                                                                                       new RedPhoneTrustStore(context),
                                                                                       TextSecurePreferences.getLocalNumber(context),
                                                                                       TextSecurePreferences.getPushServerPassword(context));

          try {
            accountManager.setGcmId(Optional.<String>absent());
          } catch (AuthorizationFailedException e) {
            Log.w(TAG, e);
          }

          try {
            redPhoneAccountManager.setGcmId(Optional.<String>absent());
          } catch (UnauthorizedException e) {
            Log.w(TAG, e);
          }

          GoogleCloudMessaging.getInstance(context).unregister();

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
        builder.setIconAttribute(R.attr.dialog_info_icon);
        builder.setTitle(R.string.ApplicationPreferencesActivity_disable_signal_messages_and_calls);
        builder.setMessage(R.string.ApplicationPreferencesActivity_disable_signal_messages_and_calls_by_unregistering);
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            new DisablePushMessagesTask((CheckBoxPreference)preference).execute();
          }
        });
        builder.show();
      } else {
        Intent nextIntent = new Intent(getActivity(), ApplicationPreferencesActivity.class);

        Intent intent = new Intent(getActivity(), RegistrationActivity.class);
        intent.putExtra("cancel_button", true);
        intent.putExtra("next_intent", nextIntent);
        intent.putExtra("master_secret", masterSecret);
        startActivity(intent);
      }

      return false;
    }
  }
}
