package org.thoughtcrime.securesms.preferences;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.support.v4.preference.PreferenceFragment;
import android.util.Log;

import com.afollestad.materialdialogs.AlertDialogWrapper;

import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Trimmer;

public class StoragePreferenceFragment extends PreferenceFragment {
  private static final String TAG = StoragePreferenceFragment.class.getSimpleName();

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);
    addPreferencesFromResource(R.xml.preferences_storage);

    this.findPreference(TextSecurePreferences.THREAD_TRIM_NOW)
      .setOnPreferenceClickListener(new TrimNowClickListener());
    this.findPreference(TextSecurePreferences.THREAD_TRIM_LENGTH)
      .setOnPreferenceChangeListener(new TrimLengthValidationListener());
  }

  @Override
  public void onResume() {
    super.onResume();
    ((ApplicationPreferencesActivity) getActivity()).getSupportActionBar().setTitle(R.string.preferences__delete_old_messages);
  }

  private class TrimNowClickListener implements Preference.OnPreferenceClickListener {
    @Override
    public boolean onPreferenceClick(Preference preference) {
      final int threadLengthLimit = TextSecurePreferences.getThreadTrimLength(getActivity());
      AlertDialogWrapper.Builder builder = new AlertDialogWrapper.Builder(getActivity());
      builder.setTitle(R.string.ApplicationPreferencesActivity_delete_all_old_messages_now);
      builder.setMessage(getString(R.string.ApplicationPreferencesActivity_are_you_sure_you_would_like_to_immediately_trim_all_conversation_threads_to_the_s_most_recent_messages,
                                   threadLengthLimit));
      builder.setPositiveButton(R.string.ApplicationPreferencesActivity_delete,
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            Trimmer.trimAllThreads(getActivity(), threadLengthLimit);
          }
        });

      builder.setNegativeButton(android.R.string.cancel, null);
      builder.show();

      return true;
    }
  }

  private class TrimLengthValidationListener implements Preference.OnPreferenceChangeListener {

    public TrimLengthValidationListener() {
      EditTextPreference preference = (EditTextPreference)findPreference(TextSecurePreferences.THREAD_TRIM_LENGTH);
      preference.setSummary(getString(R.string.ApplicationPreferencesActivity_messages_per_conversation, preference.getText()));
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
      if (newValue == null || ((String)newValue).trim().length() == 0) {
        return false;
      }

      try {
        Integer.parseInt((String)newValue);
      } catch (NumberFormatException nfe) {
        Log.w(TAG, nfe);
        return false;
      }

      if (Integer.parseInt((String)newValue) < 1) {
        return false;
      }

      preference.setSummary(getString(R.string.ApplicationPreferencesActivity_messages_per_conversation, newValue));
      return true;
    }
  }

  public static CharSequence getSummary(Context context) {
    final int onCapsResId  = R.string.ApplicationPreferencesActivity_On;
    final int offCapsResId = R.string.ApplicationPreferencesActivity_Off;

    return context.getString(TextSecurePreferences.isThreadLengthTrimmingEnabled(context) ? onCapsResId : offCapsResId);
  }
}
