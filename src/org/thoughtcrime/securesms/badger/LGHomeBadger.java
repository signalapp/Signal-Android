package org.thoughtcrime.securesms.badger;

import android.content.Context;
import android.content.Intent;
import java.lang.String;

/**
 * Created with IntelliJ IDEA.
 * User: leolin
 * Date: 2013/11/14
 * Time: 5:55
 * To change this template use File | Settings | File Templates.
 */
public class LGHomeBadger extends ShortcutBadger {

    private static final String INTENT_ACTION = "android.intent.action.BADGE_COUNT_UPDATE";
    private static final String INTENT_EXTRA_BADGE_COUNT = "badge_count";
    private static final String INTENT_EXTRA_PACKAGENAME = "badge_count_package_name";
    private static final String INTENT_EXTRA_ACTIVITY_NAME = "badge_count_class_name";

    public LGHomeBadger(Context context) {
        super(context);
    }

    @Override
    protected void executeBadge(int badgeCount) {
        Intent intent = new Intent(INTENT_ACTION);
        intent.putExtra(INTENT_EXTRA_BADGE_COUNT, badgeCount);
        intent.putExtra(INTENT_EXTRA_PACKAGENAME, getContextPackageName());
        intent.putExtra(INTENT_EXTRA_ACTIVITY_NAME, getEntryActivityName());
        context.sendBroadcast(intent);
    }
}
