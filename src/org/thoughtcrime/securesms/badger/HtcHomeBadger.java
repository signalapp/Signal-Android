package org.thoughtcrime.securesms.badger;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

/**
 * Created with IntelliJ IDEA.
 * User: leolin
 * Date: 2013/11/14
 * Time: 7:15
 * To change this template use File | Settings | File Templates.
 */
public class HtcHomeBadger extends ShortcutBadger {
    private static final String CONTENT_URI = "content://com.htc.launcher.settings/favorites?notify=true";
    private static final String UNSUPPORTED_LAUNCHER = "ShortcutBadger is currently not supporting this home launcher package \"%s\"";

    public HtcHomeBadger(Context context) {
        super(context);
    }

    @Override
    protected void executeBadge(int badgeCount) throws ShortcutBadgeException {
        ContentResolver contentResolver = context.getContentResolver();
        Uri uri = Uri.parse(CONTENT_URI);
        String appName = context.getResources().getText(context.getResources().getIdentifier("app_name",
                "string", getContextPackageName())).toString();

        try {
            Cursor cursor = contentResolver.query(uri, new String[]{"notifyCount"}, "title=?", new String[]{appName}, null);
            ContentValues contentValues = new ContentValues();
            contentValues.put("notifyCount", badgeCount);
            contentResolver.update(uri, contentValues, "title=?", new String[]{appName});
        } catch (Throwable e) {
            throw new ShortcutBadgeException(UNSUPPORTED_LAUNCHER);
        }
    }
}
