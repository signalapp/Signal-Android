package de.gdata.messaging.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

/**
 * Created by jan on 12.01.15.
 */
public class GDataPreferences {

  private static final String VIEW_PAGER_LAST_PAGE = "VIEW_PAGER_LAST_PAGE";

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
}
