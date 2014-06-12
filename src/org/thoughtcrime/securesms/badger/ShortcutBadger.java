package org.thoughtcrime.securesms.badger;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;import java.lang.String;

/**
 * Created with IntelliJ IDEA.
 * User: leolin
 * Date: 2013/11/14
 * Time: 5:51
 * To change this template use File | Settings | File Templates.
 */
public abstract class ShortcutBadger {
    private static final String HOME_PACKAGE_SONY = "com.sonyericsson.home";
    private static final String HOME_PACKAGE_SAMSUNG = "com.sec.android.app.launcher";
    private static final String HOME_PACKAGE_LG = "com.lge.launcher2";
    private static final String HOME_PACKAGE_HTC = "com.htc.launcher";

    private static final String UNSUPPORTED_LAUNCHER = "ShortcutBadger is currently not supporting this home launcher package \"%s\"";

    private static final int MIN_BADGE_COUNT = 0;
    private static final int MAX_BADGE_COUNT = 99;

    private ShortcutBadger() {
    }

    protected Context context;

    protected ShortcutBadger(Context context) {
        this.context = context;
    }

    protected abstract void executeBadge(int badgeCount) throws ShortcutBadgeException;

    public static void setBadge(Context context, int badgeCount) throws ShortcutBadgeException {

        if (badgeCount < MIN_BADGE_COUNT){
            badgeCount = MIN_BADGE_COUNT;
        }

        if (badgeCount > MAX_BADGE_COUNT) {
            badgeCount = MAX_BADGE_COUNT;
        }

        //find the home launcher Package
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        ResolveInfo resolveInfo = context.getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        String currentHomePackage = resolveInfo.activityInfo.packageName;

        //different home launcher packages use different ways for adding badges
        ShortcutBadger mShortcutBadger = null;
        if (HOME_PACKAGE_SONY.equals(currentHomePackage)) {
            mShortcutBadger = new SonyHomeBadger(context);
        } else if (HOME_PACKAGE_SAMSUNG.equals(currentHomePackage)) {
            mShortcutBadger = new SamsungHomeBadger(context);
        } else if (HOME_PACKAGE_LG.equals(currentHomePackage)) {
            mShortcutBadger = new LGHomeBadger(context);
        } else if (HOME_PACKAGE_HTC.equals(currentHomePackage)) {
            mShortcutBadger = new HtcHomeBadger(context);
        }

        if (mShortcutBadger == null) {
            String exceptionMessage = String.format(UNSUPPORTED_LAUNCHER, currentHomePackage);
            throw new ShortcutBadgeException(exceptionMessage);
        }
        mShortcutBadger.executeBadge(badgeCount);
    }

    protected String getEntryActivityName() {
        ComponentName componentName = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName()).getComponent();
        return componentName.getClassName();
    }

    protected String getContextPackageName() {
        return context.getPackageName();
    }
}
