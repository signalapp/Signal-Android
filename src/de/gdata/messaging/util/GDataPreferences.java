package de.gdata.messaging.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class GDataPreferences {

    private static final String VIEW_PAGER_LAST_PAGE = "VIEW_PAGER_LAST_PAGE";
    private static final String APPLICATION_FONT = "APPLICATION_FONT";
    private static final String PREMIUM_INSTALLED = "PREMIUM_INSTALLED";

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

    public void setApplicationFont(String applicationFont) {
        mPreferences.edit().putString(APPLICATION_FONT, applicationFont).commit();
    }

    public String getApplicationFont() {
        return mPreferences.getString(APPLICATION_FONT, "");
    }
}
