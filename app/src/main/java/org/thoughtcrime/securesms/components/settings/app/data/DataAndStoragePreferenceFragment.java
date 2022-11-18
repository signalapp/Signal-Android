package org.thoughtcrime.securesms.components.settings.app.data;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import org.greenrobot.eventbus.EventBus;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.mp02anim.ItemAnimViewController;
import org.thoughtcrime.securesms.preferences.ListSummaryPreferenceFragment;
import org.thoughtcrime.securesms.preferences.widgets.Mp02CommonPreference;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.ThrottledDebouncer;

import java.util.HashSet;
import java.util.Set;

public class DataAndStoragePreferenceFragment extends ListSummaryPreferenceFragment implements Preference.OnPreferenceClickListener {
  private static final String PREFER_SYSTEM_CONTACT_PHOTOS = "pref_system_contact_photos";

  private final ThrottledDebouncer refreshDebouncer = new ThrottledDebouncer(500);
  private static final String PREF_MOBILE = "pref_mobile";
  private static final String PREF_MOBILE_IMAGES = "pref_mobile_images";
  private static final String PREF_MOBILE_AUDIO = "pref_mobile_audio";
  private static final String PREF_MOBILE_VIDEO = "pref_mobile_video";
  private static final String PREF_MOBILE_DOCUMENTS = "pref_mobile_documents";
  private static final String PREF_WIFI = "pref_wifi";
  private static final String PREF_WIFI_IMAGES = "pref_wifi_images";
  private static final String PREF_WIFI_AUDIO = "pref_wifi_audio";
  private static final String PREF_WIFI_VIDEO = "pref_wifi_video";
  private static final String PREF_WIFI_DOCUMENTS = "pref_wifi_documents";
  private static final String PREF_ROAMING = "pref_roaming";
  private static final String PREF_ROAMING_IMAGES = "pref_roaming_images";
  private static final String PREF_ROAMING_AUDIO = "pref_roaming_audio";
  private static final String PREF_ROAMING_VIDEO = "pref_roaming_video";
  private static final String PREF_ROAMING_DOCUMENTS = "pref_roaming_documents";

  private Mp02CommonPreference mMobileImg;
  private Mp02CommonPreference mMobileAudio;
  private Mp02CommonPreference mMobileVideo;
  private Mp02CommonPreference mMobileDoc;
  private Mp02CommonPreference mWifiImg;
  private Mp02CommonPreference mWifiAudio;
  private Mp02CommonPreference mWifiVideo;
  private Mp02CommonPreference mWifiDoc;
  private Mp02CommonPreference mRoamingImg;
  private Mp02CommonPreference mRoamingAudio;
  private Mp02CommonPreference mRoamingVideo;
  private Mp02CommonPreference mRoamingDoc;

  private boolean mMobileImgEnabled;
  private boolean mMobileAudioEnabled;
  private boolean mMobileVideoEnabled;
  private boolean mMobileDocEnabled;
  private boolean mWifiImgEnabled;
  private boolean mWifiAudioEnabled;
  private boolean mWifiVideoEnabled;
  private boolean mWifiDocEnabled;
  private boolean mRoamingImgEnabled;
  private boolean mRoamingAudioEnabled;
  private boolean mRoamingVideoEnabled;
  private boolean mRoamingDocEnabled;

  private PreferenceGroup mPrefGroup;
  private int mPrefCount;
  private int mCurPoi = 0;
  private ItemAnimViewController mParentViewController;

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);

    //Mobile prefs
    mMobileImg = (Mp02CommonPreference) findPreference(PREF_MOBILE_IMAGES);
    mMobileImg.setOnPreferenceClickListener(this);
    mMobileAudio = (Mp02CommonPreference) findPreference(PREF_MOBILE_AUDIO);
    mMobileAudio.setOnPreferenceClickListener(this);
    mMobileVideo = (Mp02CommonPreference) findPreference(PREF_MOBILE_VIDEO);
    mMobileVideo.setOnPreferenceClickListener(this);
    mMobileDoc = (Mp02CommonPreference) findPreference(PREF_MOBILE_DOCUMENTS);
    mMobileDoc.setOnPreferenceClickListener(this);
    //Wifi prefs
    mWifiImg = (Mp02CommonPreference) findPreference(PREF_WIFI_IMAGES);
    mWifiImg.setOnPreferenceClickListener(this);
    mWifiAudio = (Mp02CommonPreference) findPreference(PREF_WIFI_AUDIO);
    mWifiAudio.setOnPreferenceClickListener(this);
    mWifiVideo = (Mp02CommonPreference) findPreference(PREF_WIFI_VIDEO);
    mWifiVideo.setOnPreferenceClickListener(this);
    mWifiDoc = (Mp02CommonPreference) findPreference(PREF_WIFI_DOCUMENTS);
    mWifiDoc.setOnPreferenceClickListener(this);
    //Roaming prefs
    mRoamingImg = (Mp02CommonPreference) findPreference(PREF_ROAMING_IMAGES);
    mRoamingImg.setOnPreferenceClickListener(this);
    mRoamingAudio = (Mp02CommonPreference) findPreference(PREF_ROAMING_AUDIO);
    mRoamingAudio.setOnPreferenceClickListener(this);
    mRoamingVideo = (Mp02CommonPreference) findPreference(PREF_ROAMING_VIDEO);
    mRoamingVideo.setOnPreferenceClickListener(this);
    mRoamingDoc = (Mp02CommonPreference) findPreference(PREF_ROAMING_DOCUMENTS);
    mRoamingDoc.setOnPreferenceClickListener(this);

    mPrefGroup = getPreferenceGroup();
    mPrefCount = mPrefGroup.getPreferenceCount();

    updatePrefStatus(PREF_MOBILE);
    updatePrefStatus(PREF_WIFI);
    updatePrefStatus(PREF_ROAMING);
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View view = super.onCreateView(inflater, container, savedInstanceState);
//    mParentViewController = getParentAnimViewController();
//    changeView(mCurPoi, false);
    return view;
  }

  @Override
  public void onCreatePreferences(@Nullable Bundle savedInstanceState, String rootKey) {
    addPreferencesFromResource(R.xml.preferences_chats);
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
    if (currentPosition > 0) {
      preference1 = mPrefGroup.getPreference(currentPosition - 1);
    }
    if (currentPosition < mPrefCount - 1) {
      preference2 = mPrefGroup.getPreference(currentPosition + 1);
    }

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

  @Override
  public void onResume() {
    super.onResume();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    EventBus.getDefault().unregister(this);
  }

  @Override
  public boolean onPreferenceClick(Preference preference) {
    String key = preference.getKey();
    switch (key) {
      //Mobil
      case PREF_MOBILE_IMAGES:
        setMediaDownloadAllowed(PREF_MOBILE, !mMobileImgEnabled, mMobileAudioEnabled, mMobileVideoEnabled, mMobileDocEnabled);
        updatePrefStatus(PREF_MOBILE);
        break;
      case PREF_MOBILE_AUDIO:
        setMediaDownloadAllowed(PREF_MOBILE, mMobileImgEnabled, !mMobileAudioEnabled, mMobileVideoEnabled, mMobileDocEnabled);
        updatePrefStatus(PREF_MOBILE);
        break;
      case PREF_MOBILE_VIDEO:
        setMediaDownloadAllowed(PREF_MOBILE, mMobileImgEnabled, mMobileAudioEnabled, !mMobileVideoEnabled, mMobileDocEnabled);
        updatePrefStatus(PREF_MOBILE);
        break;
      case PREF_MOBILE_DOCUMENTS:
        setMediaDownloadAllowed(PREF_MOBILE, mMobileImgEnabled, mMobileAudioEnabled, mMobileVideoEnabled, !mMobileDocEnabled);
        updatePrefStatus(PREF_MOBILE);
        break;
      //Wifi
      case PREF_WIFI_IMAGES:
        setMediaDownloadAllowed(PREF_WIFI, !mWifiImgEnabled, mWifiAudioEnabled, mWifiVideoEnabled, mWifiDocEnabled);
        updatePrefStatus(PREF_WIFI);
        break;
      case PREF_WIFI_AUDIO:
        setMediaDownloadAllowed(PREF_WIFI, mWifiImgEnabled, !mWifiAudioEnabled, mWifiVideoEnabled, mWifiDocEnabled);
        updatePrefStatus(PREF_WIFI);
        break;
      case PREF_WIFI_VIDEO:
        setMediaDownloadAllowed(PREF_WIFI, mWifiImgEnabled, mWifiAudioEnabled, !mWifiVideoEnabled, mWifiDocEnabled);
        updatePrefStatus(PREF_WIFI);
        break;
      case PREF_WIFI_DOCUMENTS:
        setMediaDownloadAllowed(PREF_WIFI, mWifiImgEnabled, mWifiAudioEnabled, mWifiVideoEnabled, !mWifiDocEnabled);
        updatePrefStatus(PREF_WIFI);
        break;
      //Roaming
      case PREF_ROAMING_IMAGES:
        setMediaDownloadAllowed(PREF_ROAMING, !mRoamingImgEnabled, mRoamingAudioEnabled, mRoamingVideoEnabled, mRoamingDocEnabled);
        updatePrefStatus(PREF_ROAMING);
        break;
      case PREF_ROAMING_AUDIO:
        setMediaDownloadAllowed(PREF_ROAMING, mRoamingImgEnabled, !mRoamingAudioEnabled, mRoamingVideoEnabled, mRoamingDocEnabled);
        updatePrefStatus(PREF_ROAMING);
        break;
      case PREF_ROAMING_VIDEO:
        setMediaDownloadAllowed(PREF_ROAMING, mRoamingImgEnabled, mRoamingAudioEnabled, !mRoamingVideoEnabled, mRoamingDocEnabled);
        updatePrefStatus(PREF_ROAMING);
        break;
      case PREF_ROAMING_DOCUMENTS:
        setMediaDownloadAllowed(PREF_ROAMING, mRoamingImgEnabled, mRoamingAudioEnabled, mRoamingVideoEnabled, !mRoamingDocEnabled);
        updatePrefStatus(PREF_ROAMING);
        break;
    }
    return true;
  }

  private void setMediaDownloadAllowed(String category, boolean var1, boolean var2, boolean var3, boolean var4) {
    Set<String> s = new HashSet<>();
    String[] keys = getResources().getStringArray(R.array.pref_media_download_entries);
    if (var1) s.add(keys[0]);
    if (var2) s.add(keys[1]);
    if (var3) s.add(keys[2]);
    if (var4) s.add(keys[3]);
    switch (category) {
      case PREF_MOBILE:
        TextSecurePreferences.setMobileMediaDownloadAllowed(getContext(), s);
        break;
      case PREF_WIFI:
        TextSecurePreferences.setWifiMediaDownloadAllowed(getContext(), s);
        break;
      case PREF_ROAMING:
        TextSecurePreferences.setRoamingMediaDownloadAllowed(getContext(), s);
        break;
    }
  }

  private void updatePrefStatus(String prefs) {
    String[] keys = getResources().getStringArray(R.array.pref_media_download_entries);
    switch (prefs) {
      case PREF_MOBILE:
        Set<String> mobileAllowed = TextSecurePreferences.getMobileMediaDownloadAllowed(getContext());
        mMobileImgEnabled = mobileAllowed.contains(keys[0]);
        mMobileImg.setTitle(getString(R.string.arrays__images) + (mMobileImgEnabled ? PREF_STATUS_ON : PREF_STATUS_OFF));
        mMobileAudioEnabled = mobileAllowed.contains(keys[1]);
        mMobileAudio.setTitle(getString(R.string.arrays__audio) + (mMobileAudioEnabled ? PREF_STATUS_ON : PREF_STATUS_OFF));
        mMobileVideoEnabled = mobileAllowed.contains(keys[2]);
        mMobileVideo.setTitle(getString(R.string.arrays__video) + (mMobileVideoEnabled ? PREF_STATUS_ON : PREF_STATUS_OFF));
        mMobileDocEnabled = mobileAllowed.contains(keys[3]);
        mMobileDoc.setTitle(getString(R.string.arrays__documents) + (mMobileDocEnabled ? PREF_STATUS_ON : PREF_STATUS_OFF));
        break;
      case PREF_WIFI:
        Set<String> wifiAllowed = TextSecurePreferences.getWifiMediaDownloadAllowed(getContext());
        mWifiImgEnabled = wifiAllowed.contains(keys[0]);
        mWifiImg.setTitle(getString(R.string.arrays__images) + (mWifiImgEnabled ? PREF_STATUS_ON : PREF_STATUS_OFF));
        mWifiAudioEnabled = wifiAllowed.contains(keys[1]);
        mWifiAudio.setTitle(getString(R.string.arrays__audio) + (mWifiAudioEnabled ? PREF_STATUS_ON : PREF_STATUS_OFF));
        mWifiVideoEnabled = wifiAllowed.contains(keys[2]);
        mWifiVideo.setTitle(getString(R.string.arrays__video) + (mWifiVideoEnabled ? PREF_STATUS_ON : PREF_STATUS_OFF));
        mWifiDocEnabled = wifiAllowed.contains(keys[3]);
        mWifiDoc.setTitle(getString(R.string.arrays__documents) + (mWifiDocEnabled ? PREF_STATUS_ON : PREF_STATUS_OFF));
        break;
      case PREF_ROAMING:
        Set<String> roamingAllowed = TextSecurePreferences.getRoamingMediaDownloadAllowed(getContext());
        mRoamingImgEnabled = roamingAllowed.contains(keys[0]);
        mRoamingImg.setTitle(getString(R.string.arrays__images) + (mRoamingImgEnabled ? PREF_STATUS_ON : PREF_STATUS_OFF));
        mRoamingAudioEnabled = roamingAllowed.contains(keys[1]);
        mRoamingAudio.setTitle(getString(R.string.arrays__audio) + (mRoamingAudioEnabled ? PREF_STATUS_ON : PREF_STATUS_OFF));
        mRoamingVideoEnabled = roamingAllowed.contains(keys[2]);
        mRoamingVideo.setTitle(getString(R.string.arrays__video) + (mRoamingVideoEnabled ? PREF_STATUS_ON : PREF_STATUS_OFF));
        mRoamingDocEnabled = roamingAllowed.contains(keys[3]);
        mRoamingDoc.setTitle(getString(R.string.arrays__documents) + (mRoamingDocEnabled ? PREF_STATUS_ON : PREF_STATUS_OFF));
        break;
    }
  }
}
