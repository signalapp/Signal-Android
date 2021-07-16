package org.thoughtcrime.securesms.preferences;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.mp02anim.ItemAnimViewController;
import org.thoughtcrime.securesms.preferences.widgets.Mp02CommonPreference;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

public class SmsMmsPreferenceFragment extends CorrectedPreferenceFragment implements Preference.OnPreferenceClickListener {

  private static final String PREF_DELIVERY_REPORT_SMS = "pref_delivery_report_sms";
  private static final String PREF_WIFI_SMS = "pref_wifi_sms";

  private Mp02CommonPreference mSmsDeliveryPref;
  private Mp02CommonPreference mWifiCallingPref;
  private boolean mSmsDeliveryStatus;
  private boolean mWifiCallingStatus;

  private PreferenceGroup mPrefGroup;
  private int mPrefCount;
  private int mCurPoi = 0;
  private ItemAnimViewController mParentViewController;

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);
    initPreferences();
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
    addPreferencesFromResource(R.xml.preferences_sms_mms);
  }

  private void initPreferences() {
    mSmsDeliveryPref = (Mp02CommonPreference) this.findPreference(PREF_DELIVERY_REPORT_SMS);
    mWifiCallingPref = (Mp02CommonPreference) this.findPreference(PREF_WIFI_SMS);
    mSmsDeliveryPref.setOnPreferenceClickListener(this);
    mWifiCallingPref.setOnPreferenceClickListener(this);
    updatePrefStatus(PREF_DELIVERY_REPORT_SMS, TextSecurePreferences.isSmsDeliveryReportsEnabled(getContext()));
    updatePrefStatus(PREF_WIFI_SMS, TextSecurePreferences.isWifiSmsEnabled(getContext()));

    mPrefGroup = getPreferenceGroup();
    mPrefCount = mPrefGroup.getPreferenceCount();
  }

  private void updatePrefStatus(String key, boolean enabled) {
    String status = enabled ? PREF_STATUS_ON : PREF_STATUS_OFF;
    switch (key) {
      case PREF_DELIVERY_REPORT_SMS:
        mSmsDeliveryStatus = enabled;
        mSmsDeliveryPref.setTitle(getString(R.string.preferences__sms_delivery_reports) + status);
        break;
      case PREF_WIFI_SMS:
        mWifiCallingStatus = enabled;
        mWifiCallingPref.setTitle(getString(R.string.preferences__support_wifi_calling) + status);
        break;
      default:
        break;
    }
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
  public boolean onPreferenceClick(Preference preference) {
    String key = preference.getKey();
    switch (key) {
      case PREF_DELIVERY_REPORT_SMS:
        TextSecurePreferences.setSmsDeliveryReportsEnabled(getContext(), !mSmsDeliveryStatus);
        updatePrefStatus(PREF_DELIVERY_REPORT_SMS, TextSecurePreferences.isSmsDeliveryReportsEnabled(getContext()));
        break;
      case PREF_WIFI_SMS:
        TextSecurePreferences.setWifiSmsEnabled(getContext(), !mWifiCallingStatus);
        updatePrefStatus(PREF_WIFI_SMS, TextSecurePreferences.isWifiSmsEnabled(getContext()));
        break;
      default:
        break;
    }
    return true;
  }
}