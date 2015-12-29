package de.gdata.messaging.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.util.JsonUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class GDataPreferences {

  public static final String INTENT_ACCESS_SERVER = "de.gdata.mobilesecurity.ACCESS_SERVER";
  public static final String ISFA_PACKAGE = "de.gdata.mobilesecurity";
  public static final String ISFA_PACKAGE_2 = "de.gdata.mobilesecurity2";
  public static final String ISFA_PACKAGE_3 = "de.gdata.mobilesecurity2g";
  public static final String ISFA_PACKAGE_4 = "de.gdata.mobilesecurity2b";
  public static final String ISFA_PACKAGE_5 = "de.gdata.mobilesecurityorange";

  public static final String[] ISFA_PACKAGES = {ISFA_PACKAGE, ISFA_PACKAGE_2, ISFA_PACKAGE_3, ISFA_PACKAGE_4, ISFA_PACKAGE_5};

  private static final String VIEW_PAGER_LAST_PAGE = "VIEW_PAGER_LAST_PAGE";
  private static final String APPLICATION_FONT = "APPLICATION_FONT";
  private static final String PRIVACY_ACTIVATED = "PRIVACY_ACTIVATED";
  private static final String SAVED_HIDDEN_RECIPIENTS = "SAVED_HIDDEN_RECIPIENTS";
  private static final String SAVE_E164_NUMBER = "SAVE_E164_NUMBER";
  private static final String COLOR_HEX = "COLOR_HEX";
  private static final String COLOR_PROGRESS = "COLOR_PROGRESS";

  private static final String PROFILE_PICTURE_URI = "PROFILE_PICTURE_URI";
  private static final String PROFILE_STATUS = "PROFILE_STATUS";
  private static final String ACTIVE_CONTACTS = "ACTIVE_CONTACTS";
  private static final String LAST_IMAGE_NUMBER = "LAST_IMAGE_NUMBER";
  private static final String CP_COLOR_ACTIVATED = "CP_COLOR_ACTIVATED";

  private static final String COLOR_DEFAULT = "COLOR_DEFAULT";
  private static final String PROFILE_IMAGE_CHANGED = "PROFILE_IMAGE_CHANGED";

  private static final long ONE_SECOND = 1000;

  private final SharedPreferences mPreferences;
  private final Context mContext;
  private static final String MEDIA_HISTORY = "MEDIA_HISTORY";

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
  public void setLastImageNumber(int number) {
    mPreferences.edit().putInt(LAST_IMAGE_NUMBER, number).commit();
  }
  public int getLastImageIndicator() {
    return mPreferences.getInt(LAST_IMAGE_NUMBER, 0);
  }
  public void setPrivacyActivated(boolean activated) {
    mPreferences.edit().putBoolean(PRIVACY_ACTIVATED, activated).commit();
  }

  public boolean isPrivacyActivated() {
    return mPreferences.getBoolean(PRIVACY_ACTIVATED, true);
  }

  public void setProfilePictureUri(String uri) {
    mPreferences.edit().putString(PROFILE_PICTURE_URI, uri).commit();
  }
  public String getProfilePictureUri() {
    return mPreferences.getString(PROFILE_PICTURE_URI, "");
  }

  public void setProfilePartId(String profileId, Long profilePartId) {
    mPreferences.edit().putLong("id:" + profileId, profilePartId).commit();
  }
  public void setProfilePartRow(String profileId, Long profilePartId) {
    mPreferences.edit().putLong("row:" + profileId, profilePartId).commit();
  }
  public Long getProfilePartId(String profileId) {
    return mPreferences.getLong("id:" +profileId, -1L);
  }
  public Long getProfilePartRow(String profileId) {
    return mPreferences.getLong("row:" + profileId, -1L);
  }
  public void setProfileStatus(String profileStatus) {
    mPreferences.edit().putString(PROFILE_STATUS, profileStatus).commit();
  }
  public String getProfileStatus() {
    return mPreferences.getString(PROFILE_STATUS, "");
  }

  public void setProfileStatusForProfileId(String profileId, String status) {
    mPreferences.edit().putString("status:" + profileId, status).commit();
  }
  public void setProfileColorForProfileId(String profileId, String color) {
    mPreferences.edit().putString("color:" + profileId, color).commit();
  }
  public String getProfileColorForProfileId(String profileId) {
    return mPreferences.getString("color:" + profileId, "0");
  }
  public String getVersionForProfileId(String profileId) {
    return mPreferences.getString("version:" + profileId, "0");
  }
  public void setVersionForProfileId(String profileId, String version) {
    mPreferences.edit().putString("version:" + profileId, version).commit();
  }
  public String getProfileStatusForProfileId(String profileId) {
    return mPreferences.getString("status:" + profileId, "");
  }
  public Long getProfileUpdateTimeForProfileId(String profileId) {
    return mPreferences.getLong("date:" + profileId, System.currentTimeMillis());
  }
  public void setProfilUpdateTimeForProfileId(String profileId, Long date) {
    mPreferences.edit().putLong("date:" + profileId, date).commit();
  }
  public void saveReadCount(String threadId, Long count) {
    if(count<0) {
      count = 0L;
    }
    mPreferences.edit().putLong("count:" + threadId, count).commit();
  }
  public Long getReadCount(String threadId) {
    return mPreferences.getLong("count:" + threadId, 0);
  }
  public void setApplicationFont(String applicationFont) {
    mPreferences.edit().putString(APPLICATION_FONT, applicationFont).commit();
  }
  public void saveFilterGroupIdForContact(String phoneNo, long filterGroupId) {
    mPreferences.edit().putLong(phoneNo, filterGroupId).commit();
  }
  public void saveCurrentColorValue(int colorHex) {
    mPreferences.edit().putInt(COLOR_HEX, colorHex).commit();
  }
  public void saveCurrentSeekBarColorProgress(int progress) {
    mPreferences.edit().putInt(COLOR_PROGRESS, progress).commit();
  }
  public boolean saveActiveContacts(String[] array) {
    mPreferences.edit().putInt(ACTIVE_CONTACTS + "_size", array.length).commit();
    for(int i=0;i<array.length;i++) {
      mPreferences.edit().putString(ACTIVE_CONTACTS + "_" + i, array[i]).commit();
    }
    return  mPreferences.edit().commit();
  }
  public String[] getActiveContacts() {
    int size = mPreferences.getInt(ACTIVE_CONTACTS + "_size", 0);
    String array[] = new String[size];
    for(int i=0;i<size;i++) {
      array[i] = mPreferences.getString(ACTIVE_CONTACTS + "_" + i, "");
    }
    return array;
  }
  public long getFilterGroupIdForContact(String phoneNo) {
    return mPreferences.getLong(phoneNo, -1L);
  }
  public void saveHiddenRecipients(ArrayList<Recipient> hiddenRecipients) {
    ArrayList<Long> recIds = new ArrayList<Long>();
    for (Recipient recipient : hiddenRecipients) {
      recIds.add(recipient.getRecipientId());
    }
    try {
        Log.d("GDataPreferences-", JsonUtils.toJson(recIds));
        mPreferences.edit().putString(SAVED_HIDDEN_RECIPIENTS, JsonUtils.toJson(recIds)).commit();
      } catch (IOException e) {
        Log.e("GDataPreferences", e.getMessage());
      }
  }
  public ArrayList<Recipient> getSavedHiddenRecipients() {
    ArrayList<Recipient> hiddenRecipients = null;

    try {
      ArrayList<Integer> recipients = JsonUtils.fromJson(mPreferences.getString(SAVED_HIDDEN_RECIPIENTS, JsonUtils.toJson(new ArrayList<Long>())), ArrayList.class);

      hiddenRecipients = new ArrayList<Recipient>();
      try {
        Log.d("GDataPreferences", recipients.toString());
        for (Integer recId : recipients) {
          hiddenRecipients.add(RecipientFactory.getRecipientForId(mContext, recId, false));
        }
      } catch(ClassCastException ex) {
        Log.e("GDataPreferences", ex.getMessage());
        }
    } catch (IOException e) {
      Log.e("GDataPreferences", e.getMessage());
    }
    return hiddenRecipients != null ? hiddenRecipients : new ArrayList<Recipient>();
  }
  public String getApplicationFont() {
    return mPreferences.getString(APPLICATION_FONT, "");
  }
  public int getCurrentColorHex() {
    return getColorDefaultEnabled() ? mContext.getResources().getColor(R.color.gdata_primary_color) : mPreferences.getInt(COLOR_HEX, mContext.getResources().getColor(R.color.gdata_primary_color));
  }
  public int getColorProgress() {
    return mPreferences.getInt(COLOR_PROGRESS, 0);
  }
  public void saveE164Number(String e164number) {
    mPreferences.edit().putString(SAVE_E164_NUMBER, e164number).commit();
  }
  public String getE164Number() {
    return mPreferences.getString(SAVE_E164_NUMBER, "");
  }

  public boolean isMarkedAsRemoved(String id) {
    return mPreferences.getBoolean("msgid:" + id, false);
  }
  public void setAsDestroyed(String id) {
    mPreferences.edit().putBoolean("msgid:" + id, true).commit();
  }
  public void removeFromList(String id) {
    mPreferences.edit().remove("msgid:" + id).commit();
  }

  public boolean saveMediaForHistory(Uri mediaUri, String mediaType, long recipientId){
    if(!historyHasUriAlready(mediaUri.toString(), recipientId)) {
      String[] array = getMediaUriHistoryForId(recipientId);
      int newLength = array.length + 1;

      mPreferences.edit().putInt(MEDIA_HISTORY + "_size_" + recipientId, newLength).commit();
      mPreferences.edit().putString(MEDIA_HISTORY + "_uri_" + recipientId + "_" + (newLength - 1), mediaUri.toString()).commit();
      mPreferences.edit().putString(MEDIA_HISTORY + "_type_" + recipientId + "_" + (newLength - 1), mediaType).commit();
    }
    return  mPreferences.edit().commit();
  }
  public boolean historyHasUriAlready(String uri, long recipientId) {
    boolean hasAlready = false;
    String[] array = getMediaUriHistoryForId(recipientId);
    for(int i=0;i<array.length;i++) {
      if (array[i].contains(uri)) {
        hasAlready = true;
      }
    }
    return hasAlready;
  }
  public String[] getMediaUriHistoryForId(long recipientId) {
    int size = mPreferences.getInt(MEDIA_HISTORY + "_size_" + recipientId, 0);
    String arrayUri[] = new String[size];
    for(int i=0;i<size;i++) {
      arrayUri[i] = mPreferences.getString(MEDIA_HISTORY + "_uri_" + recipientId + "_" + i, "");
    }
    if(size > 0) {
      arrayUri = GUtil.reverseOrder(arrayUri);
    }
    return arrayUri;
  }
  public String[] getMediaMessageIdHistoryForContactId(long recipientId) {
    int size = mPreferences.getInt(MEDIA_HISTORY + "_size_" + recipientId, 0);
    String arrayType[] = new String[size];
    for(int i=0;i<size;i++) {
      arrayType[i] = mPreferences.getString(MEDIA_HISTORY + "_type_" + recipientId + "_" + i, "");
    }
    if(size > 0) {
      arrayType = GUtil.reverseOrder(arrayType);
    }
    return arrayType;
  }

  public int getNextImageIndicator() {
    int lastImageNumber = getLastImageIndicator();

    File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_PICTURES), "SecureChat");

    File mediaFile = new File("");
    for(int i = 0; i<= lastImageNumber;i++) {
      mediaFile = new File(mediaStorageDir.getPath() + File.separator +
              "media_file_"+ i +".jpg");
      if(mediaFile.exists()) {
        mediaFile.delete();
      }
      mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                "media_file_" + i + ".mp4");
      if (mediaFile.exists()) {
          mediaFile.delete();
      }
    }
    lastImageNumber = lastImageNumber + 1;
    setLastImageNumber(lastImageNumber);

    lastImageNumber = getLastImageIndicator();
    return lastImageNumber;
  }

  public void saveChatPartnersColorEnabled(boolean b) {
    mPreferences.edit().putBoolean(CP_COLOR_ACTIVATED, b).commit();
  }
  public boolean getChatPartnersColorEnabled() {
    return mPreferences.getBoolean(CP_COLOR_ACTIVATED, true);
  }
  public boolean isSoonAfterLastExchange(String id, long timestamp) {
    boolean isSoonAfterLastExchange = false;
    if (timestamp - mPreferences.getLong("lastExchange: " + id, 0) <= ONE_SECOND) {
      isSoonAfterLastExchange = true;
    }
    mPreferences.edit().putLong("lastExchange: " + id, timestamp).commit();
    return isSoonAfterLastExchange;
  }
  public boolean getColorDefaultEnabled() {
    return mPreferences.getBoolean(COLOR_DEFAULT, true);
  }
  public void setColorDefaultEnabled(boolean b) {
    mPreferences.edit().putBoolean(COLOR_DEFAULT, b).commit();
  }
  public boolean hasProfileImageChanged(boolean b) {
    boolean hasChanged = mPreferences.getBoolean(PROFILE_IMAGE_CHANGED, true);
    mPreferences.edit().putBoolean(PROFILE_IMAGE_CHANGED, b).commit();
    return hasChanged;
  }
  public boolean hasProfileImageChanged() {
    boolean hasChanged = mPreferences.getBoolean(PROFILE_IMAGE_CHANGED, true);
    return hasChanged;
  }
}

