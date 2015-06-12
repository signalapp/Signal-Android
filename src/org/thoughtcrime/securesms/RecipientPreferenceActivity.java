package org.thoughtcrime.securesms;

import android.content.DialogInterface;
import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.RingtonePreference;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.preference.PreferenceFragment;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.afollestad.materialdialogs.AlertDialogWrapper;

import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.RecipientPreferenceDatabase.VibrateState;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;

public class RecipientPreferenceActivity extends PassphraseRequiredActionBarActivity implements Recipients.RecipientsModifiedListener
{
  private static final String TAG = RecipientPreferenceActivity.class.getSimpleName();

  public static final String RECIPIENTS_EXTRA = "recipient_ids";

  private static final String PREFERENCE_MUTED   = "pref_key_recipient_mute";
  private static final String PREFERENCE_TONE    = "pref_key_recipient_ringtone";
  private static final String PREFERENCE_VIBRATE = "pref_key_recipient_vibrate";
  private static final String PREFERENCE_BLOCK   = "pref_key_recipient_block";

  private final DynamicTheme    dynamicTheme    = new DynamicNoActionBarTheme();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private AvatarImageView avatar;
  private TextView        title;
  private TextView        blockedIndicator;

  @Override
  public void onPreCreate() {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
  }

  @Override
  public void onCreate(Bundle instanceState, @NonNull MasterSecret masterSecret) {
    setContentView(R.layout.recipient_preference_activity);

    long[]     recipientIds = getIntent().getLongArrayExtra(RECIPIENTS_EXTRA);
    Recipients recipients   = RecipientFactory.getRecipientsForIds(this, recipientIds, true);

    initializeToolbar();
    setHeader(recipients);
    recipients.addListener(this);

    Bundle bundle = new Bundle();
    bundle.putLongArray(RECIPIENTS_EXTRA, recipientIds);
    initFragment(R.id.preference_fragment, new RecipientPreferenceFragment(), masterSecret, null, bundle);
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
    dynamicLanguage.onResume(this);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.preference_fragment);
    fragment.onActivityResult(requestCode, resultCode, data);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);
    switch (item.getItemId()) {
      case android.R.id.home: finish(); return true;
    }

    return false;
  }

  private void initializeToolbar() {
    Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);

    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    getSupportActionBar().setDisplayShowTitleEnabled(false);

    this.avatar           = (AvatarImageView) toolbar.findViewById(R.id.avatar);
    this.title            = (TextView) toolbar.findViewById(R.id.name);
    this.blockedIndicator = (TextView) toolbar.findViewById(R.id.blocked_indicator);
  }

  private void setHeader(Recipients recipients) {
    this.avatar.setAvatar(recipients.getPrimaryRecipient(), true);
    this.title.setText(recipients.toShortString());

    if (recipients.isBlocked()) this.blockedIndicator.setVisibility(View.VISIBLE);
    else                        this.blockedIndicator.setVisibility(View.GONE);
  }

  @Override
  public void onModified(final Recipients recipients) {
    title.post(new Runnable() {
      @Override
      public void run() {
        setHeader(recipients);
      }
    });
  }

  public static class RecipientPreferenceFragment
      extends    PreferenceFragment
      implements Recipients.RecipientsModifiedListener
  {

    private final Handler handler = new Handler();

    private Recipients recipients;

    @Override
    public void onCreate(Bundle icicle) {
      super.onCreate(icicle);

      addPreferencesFromResource(R.xml.recipient_preferences);

      this.recipients = RecipientFactory.getRecipientsForIds(getActivity(),
                                                             getArguments().getLongArray(RECIPIENTS_EXTRA),
                                                             true);

      this.recipients.addListener(this);
      this.findPreference(PREFERENCE_TONE)
          .setOnPreferenceChangeListener(new RingtoneChangeListener());
      this.findPreference(PREFERENCE_VIBRATE)
          .setOnPreferenceChangeListener(new VibrateChangeListener());
      this.findPreference(PREFERENCE_MUTED)
          .setOnPreferenceClickListener(new MuteClickedListener());
      this.findPreference(PREFERENCE_BLOCK)
          .setOnPreferenceClickListener(new BlockClickedListener());
    }

    @Override
    public void onResume() {
      super.onResume();
      setSummaries(recipients);
    }

    private void setSummaries(Recipients recipients) {
      CheckBoxPreference mutePreference     = (CheckBoxPreference) this.findPreference(PREFERENCE_MUTED);
      RingtonePreference ringtonePreference = (RingtonePreference) this.findPreference(PREFERENCE_TONE);
      ListPreference     vibratePreference  = (ListPreference) this.findPreference(PREFERENCE_VIBRATE);
      Preference         blockPreference    = this.findPreference(PREFERENCE_BLOCK);

      mutePreference.setChecked(recipients.isMuted());

      if (recipients.getRingtone() != null) {
        Ringtone tone = RingtoneManager.getRingtone(getActivity(), recipients.getRingtone());

        if (tone != null) {
          ringtonePreference.setSummary(tone.getTitle(getActivity()));
        }
      } else {
        ringtonePreference.setSummary(R.string.preferences__default);
      }

      if (recipients.getVibrate() == VibrateState.DEFAULT) {
        vibratePreference.setSummary(R.string.preferences__default);
        vibratePreference.setValueIndex(0);
      } else if (recipients.getVibrate() == VibrateState.ENABLED) {
        vibratePreference.setSummary(R.string.RecipientPreferenceActivity_enabled);
        vibratePreference.setValueIndex(1);
      } else {
        vibratePreference.setSummary(R.string.RecipientPreferenceActivity_disabled);
        vibratePreference.setValueIndex(2);
      }

      if (!recipients.isSingleRecipient() || recipients.isGroupRecipient()) {
        blockPreference.setEnabled(false);
      } else {
        blockPreference.setEnabled(true);
        if (recipients.isBlocked()) blockPreference.setTitle(R.string.RecipientPreferenceActivity_unblock);
        else                        blockPreference.setTitle(R.string.RecipientPreferenceActivity_block);
      }
    }

    @Override
    public void onModified(final Recipients recipients) {
      handler.post(new Runnable() {
        @Override
        public void run() {
          setSummaries(recipients);
        }
      });
    }

    private class RingtoneChangeListener implements Preference.OnPreferenceChangeListener {
      @Override
      public boolean onPreferenceChange(Preference preference, Object newValue) {
        String value = (String)newValue;

        final Uri uri;

        if (TextUtils.isEmpty(value) || Settings.System.DEFAULT_NOTIFICATION_URI.toString().equals(value)) {
          uri = null;
        } else {
          uri = Uri.parse(value);
        }

        recipients.setRingtone(uri);

        new AsyncTask<Uri, Void, Void>() {
          @Override
          protected Void doInBackground(Uri... params) {
            DatabaseFactory.getRecipientPreferenceDatabase(getActivity())
                           .setRingtone(recipients, params[0]);
            return null;
          }
        }.execute(uri);

        return false;
      }
    }

    private class VibrateChangeListener implements Preference.OnPreferenceChangeListener {
      @Override
      public boolean onPreferenceChange(Preference preference, Object newValue) {
              int          value        = Integer.parseInt((String) newValue);
        final VibrateState vibrateState = VibrateState.fromId(value);

        recipients.setVibrate(vibrateState);

        new AsyncTask<Void, Void, Void>() {
          @Override
          protected Void doInBackground(Void... params) {
            DatabaseFactory.getRecipientPreferenceDatabase(getActivity())
                           .setVibrate(recipients, vibrateState);
            return null;
          }
        }.execute();

        return false;
      }
    }

    private class MuteClickedListener implements Preference.OnPreferenceClickListener {
      @Override
      public boolean onPreferenceClick(Preference preference) {
        if (recipients.isMuted()) handleUnmute();
        else                      handleMute();

        return true;
      }

      private void handleMute() {
        MuteDialog.show(getActivity(), new MuteDialog.MuteSelectionListener() {
          @Override
          public void onMuted(long until) {
            setMuted(recipients, until);
          }
        });

        setSummaries(recipients);
      }

      private void handleUnmute() {
        setMuted(recipients, 0);
      }

      private void setMuted(final Recipients recipients, final long until) {
        recipients.setMuted(until);

        new AsyncTask<Void, Void, Void>() {
          @Override
          protected Void doInBackground(Void... params) {
            DatabaseFactory.getRecipientPreferenceDatabase(getActivity())
                           .setMuted(recipients, until);
            return null;
          }
        }.execute();
      }
    }

    private class BlockClickedListener implements Preference.OnPreferenceClickListener {
      @Override
      public boolean onPreferenceClick(Preference preference) {
        if (recipients.isBlocked()) handleUnblock();
        else                        handleBlock();

        return true;
      }

      private void handleBlock() {
        new AlertDialogWrapper.Builder(getActivity())
            .setTitle(R.string.RecipientPreferenceActivity_block_this_contact_question)
            .setMessage(R.string.RecipientPreferenceActivity_you_will_no_longer_see_messages_from_this_user)
            .setCancelable(true)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.RecipientPreferenceActivity_block, new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialog, int which) {
                setBlocked(recipients, true);
              }
            }).show();
      }

      private void handleUnblock() {
        new AlertDialogWrapper.Builder(getActivity())
            .setTitle(R.string.RecipientPreferenceActivity_unblock_this_contact_question)
            .setMessage(R.string.RecipientPreferenceActivity_are_you_sure_you_want_to_unblock_this_contact)
            .setCancelable(true)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.RecipientPreferenceActivity_unblock, new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialog, int which) {
                setBlocked(recipients, false);
              }
            }).show();
      }

      private void setBlocked(final Recipients recipients, final boolean blocked) {
        recipients.setBlocked(blocked);

        new AsyncTask<Void, Void, Void>() {
          @Override
          protected Void doInBackground(Void... params) {
            DatabaseFactory.getRecipientPreferenceDatabase(getActivity())
                           .setBlocked(recipients, blocked);
            return null;
          }
        }.execute();
      }
    }
  }
}
