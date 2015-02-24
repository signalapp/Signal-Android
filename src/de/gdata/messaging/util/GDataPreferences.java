package de.gdata.messaging.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.thoughtcrimegson.Gson;
import com.google.thoughtcrimegson.reflect.TypeToken;

import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;

import java.lang.reflect.Type;
import java.util.ArrayList;

public class GDataPreferences {

  public static final String INTENT_ACCESS_SERVER = "de.gdata.mobilesecurity.ACCESS_SERVER";
  private static final String VIEW_PAGER_LAST_PAGE = "VIEW_PAGER_LAST_PAGE";
  private static final String APPLICATION_FONT = "APPLICATION_FONT";
  private static final String PREMIUM_INSTALLED = "PREMIUM_INSTALLED";
  private static final String PRIVACY_ACTIVATED = "PRIVACY_ACTIVATED";
  private static final String SAVED_HIDDEN_RECIPIENTS = "SAVED_HIDDEN_RECIPIENTSA";
  private static final String SAVED_RECIPIENTS = "SAVED_HIDDEN_RECIPIENTSA";

  private final SharedPreferences mPreferences;
  private final Context mContext;

  public GDataPreferences(Context context) {
    mPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    mContext = context;
  }

  public void setViewPagerLastPage(int page) {
    mPreferences.edit().putInt(VIEW_PAGER_LAST_PAGE, page).commit();
  }

  public int getViewPagersLastPage() {
    return mPreferences.getInt(VIEW_PAGER_LAST_PAGE, 0);
  }

  public void setPremiumInstalled(boolean installed) {
    mPreferences.edit().putBoolean(PREMIUM_INSTALLED, installed).commit();
  }

  public boolean isPremiumInstalled() {
    return mPreferences.getBoolean(PREMIUM_INSTALLED, false);
  }

  public void setPrivacyActivated(boolean activated) {
    mPreferences.edit().putBoolean(PRIVACY_ACTIVATED, activated).commit();
  }

  public boolean isPrivacyActivated() {
    return mPreferences.getBoolean(PRIVACY_ACTIVATED, false);
  }

  public void setApplicationFont(String applicationFont) {
    mPreferences.edit().putString(APPLICATION_FONT, applicationFont).commit();
  }
  public void saveFilterGroupIdForContact(String phoneNo, long filterGroupId) {
    mPreferences.edit().putLong(phoneNo, filterGroupId).commit();
  }
  public long getFilterGroupIdForContact(String phoneNo) {
    return mPreferences.getLong(phoneNo, -1L);
  }
  public void saveHiddenRecipients(ArrayList<Recipient> hiddenRecipients) {
    ArrayList<Long> recIds = new ArrayList<Long>();
    for (Recipient recipient : hiddenRecipients) {
      recIds.add(recipient.getRecipientId());
    }
    mPreferences.edit().putString(SAVED_HIDDEN_RECIPIENTS, new Gson().toJson(recIds)).commit();
  }
  public ArrayList<Recipient> getSavedHiddenRecipients() {
    Type listType = new TypeToken<ArrayList<Long>>() {
    }.getType();
    ArrayList<Long> recipients = new Gson().fromJson(mPreferences.getString(SAVED_HIDDEN_RECIPIENTS, new Gson().toJson(new ArrayList<Long>())), listType);
    ArrayList<Recipient> hiddenRecipients = new ArrayList<Recipient>();
    for (Long recId : recipients) {
      hiddenRecipients.add(RecipientFactory.getRecipientForId(mContext, recId, false));
    }
    return hiddenRecipients != null ? hiddenRecipients : new ArrayList<Recipient>();
  }
  public String getApplicationFont() {
    return mPreferences.getString(APPLICATION_FONT, "");
  }

}

