/*
 *Copyright 2014 Leo Lin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Copyright (C) 2013 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.thoughtcrime.securesms.badger;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;import java.lang.String;

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
