package org.thoughtcrime.securesms.preferences;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;

import androidx.preference.PreferenceScreen;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.PassphraseRequiredActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.preferences.widgets.MpRadioButtonPreference;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class RingtonePickerActivity extends PassphraseRequiredActivity {

    private static final String TAG = RingtonePickerActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle icicle, boolean ready) {
        initFragment(android.R.id.content, new RingtonePickerFragment());
    }

    public static class RingtonePickerFragment extends CorrectedPreferenceFragment implements MpRadioButtonPreference.OnClickListener {
        private Context mContext;
        private List<MpRadioButtonPreference> mPreflist = new ArrayList<>();
        private List<Uri> mRingtoneUriList = new ArrayList<>();
        boolean mIsFirst;
        private Ringtone tone = null;
        private RingtoneManager manager;
        private int index;
        private int mType;
        private boolean mHasDefaultItem;
        private Uri mUriForDefaultItem;
        private boolean mHasSilentItem;
        private Uri mExistingUri;

        @Override
        public void onCreate(Bundle icicle) {
            super.onCreate(icicle);
            mContext = getActivity();

            Intent intent = getActivity().getIntent();
            mType = intent.getIntExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, -1);
            manager = new RingtoneManager(mContext);
            if (mType != -1) {
                manager.setType(mType);
            }

            mHasDefaultItem = intent.getBooleanExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
            mUriForDefaultItem = intent.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI);
            if (mUriForDefaultItem == null) {
                if (mType == RingtoneManager.TYPE_NOTIFICATION) {
                    mUriForDefaultItem = Settings.System.DEFAULT_NOTIFICATION_URI;
                } else if (mType == RingtoneManager.TYPE_RINGTONE) {
                    mUriForDefaultItem = Settings.System.DEFAULT_RINGTONE_URI;
                } else {
                    // or leave it null for silence.
                    mUriForDefaultItem = Settings.System.DEFAULT_RINGTONE_URI;
                }
            }
            mHasSilentItem = intent.getBooleanExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
            mExistingUri = intent.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI);
            if (mExistingUri.equals(Uri.EMPTY)) {
                mExistingUri = null;
            }

            initPreferences();

            prepareRingtone(mExistingUri);
            mIsFirst = true;
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            addPreferencesFromResource(R.xml.preferences_ringtone_settings);
        }

        @Override
        public void onResume() {
            super.onResume();
            if (mIsFirst) {
                mIsFirst = false;
                setDivider(null);
                setChecked(index);

                RecyclerView list = getListView();
                if (index > 0) {
                    list.scrollToPosition(index);
                }
            }
        }

        @Override
        public void onPause() {
            if (tone != null) {
                tone.stop();
                tone = null;
            }
            super.onPause();
        }

        @Override
        public void onDestroy() {
            if (tone != null) {
                tone.stop();
                tone = null;
            }
            super.onDestroy();
        }

        private void prepareRingtone(Uri soundUri) {
            try {
                if (tone != null) {
                    tone.stop();
                    tone = null;
                }

                tone = soundUri != null ? RingtoneManager.getRingtone(mContext, soundUri) : null;
                if (tone == null) {
                    return;
                }
                tone.setStreamType(AudioManager.STREAM_ALARM);

                if (tone.isPlaying()) {
                    tone.stop();
                } else {
                    tone.play();
                }
            } catch (Exception e) {

            }
        }

        public List<Uri> getRingtoneUriList() {
            List<Uri> resArr = new ArrayList<>();

            if (mHasDefaultItem) {
                resArr.add(mUriForDefaultItem);
            }
            if (mHasSilentItem) {
                resArr.add(null);
            }
            Cursor cursor = manager.getCursor();
            int count = cursor.getCount();
            for (int i = 0; i < count; i++) {
                resArr.add(manager.getRingtoneUri(i));
            }
            return resArr;
        }

        public void initPreferences() {
            int offset = 0;
            PreferenceScreen preferenceScreen = getPreferenceScreen();
            if (mHasDefaultItem) {
                MpRadioButtonPreference defaultPref = new MpRadioButtonPreference(mContext);
                if (mType == RingtoneManager.TYPE_NOTIFICATION) {
                    defaultPref.setTitle(R.string.notification_sound_default);
                } else {
                    defaultPref.setTitle(R.string.ringtone_default);
                }
                defaultPref.setKey("0");
                defaultPref.setOnClickListener(this);
                mPreflist.add(defaultPref);
                preferenceScreen.addPreference(defaultPref);
                offset ++;
            }

            if (mHasSilentItem) {
                MpRadioButtonPreference nonePref = new MpRadioButtonPreference(mContext);
                nonePref.setTitle("None");
                nonePref.setKey(mHasDefaultItem ? "1" : "0");
                nonePref.setOnClickListener(this);
                mPreflist.add(nonePref);
                preferenceScreen.addPreference(nonePref);
                offset ++;
            }

            Cursor cursor = manager.getCursor();
            if (cursor.moveToFirst()) {
                do {
                    MpRadioButtonPreference ringtonePrf = new MpRadioButtonPreference(mContext);
                    ringtonePrf.setTitle(cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX));
                    ringtonePrf.setKey(String.valueOf(cursor.getPosition() + offset));
                    ringtonePrf.setOnClickListener(this);
                    mPreflist.add(ringtonePrf);
                    preferenceScreen.addPreference(ringtonePrf);
                } while (cursor.moveToNext());
            }

            mRingtoneUriList = getRingtoneUriList();
            index = mRingtoneUriList.indexOf(mExistingUri);
            mPreflist.get(index).initfocus(index);
        }

        private void setChecked(int index) {
            for (MpRadioButtonPreference prf : mPreflist) {
                prf.setChecked(false);
            }
            mPreflist.get(index).setChecked(true);
        }

        @Override
        public void onRadioButtonClicked(MpRadioButtonPreference pref) {
            if (!pref.isChecked()) {
                int index = Integer.parseInt(pref.getKey());
                setChecked(index);
                prepareRingtone(mRingtoneUriList.get(index));

                if (Objects.equals(mRingtoneUriList.get(index), mExistingUri)) {
                    getActivity().setResult(RESULT_CANCELED);
                } else {
                    Intent resultIntent = new Intent();
                    resultIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, mRingtoneUriList.get(index));
                    getActivity().setResult(RESULT_OK, resultIntent);
                }
            } else {
                int index = Integer.parseInt(pref.getKey());
                prepareRingtone(mRingtoneUriList.get(index));
            }
        }
    }
}
