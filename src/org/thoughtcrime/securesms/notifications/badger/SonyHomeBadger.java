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

package org.thoughtcrime.securesms.notifications.badger;

import android.content.Context;
import android.content.Intent;
import java.lang.String;

public class SonyHomeBadger extends ShortcutBadger {

    private static final String INTENT_ACTION = "com.sonyericsson.home.action.UPDATE_BADGE";
    private static final String INTENT_EXTRA_PACKAGE_NAME = "com.sonyericsson.home.intent.extra.badge.PACKAGE_NAME";
    private static final String INTENT_EXTRA_ACTIVITY_NAME = "com.sonyericsson.home.intent.extra.badge.ACTIVITY_NAME";
    private static final String INTENT_EXTRA_MESSAGE = "com.sonyericsson.home.intent.extra.badge.MESSAGE";
    private static final String INTENT_EXTRA_SHOW_MESSAGE = "com.sonyericsson.home.intent.extra.badge.SHOW_MESSAGE";


    public SonyHomeBadger(Context context) {
        super(context);
    }

    @Override
    protected void executeBadge(int badgeCount) {
        Intent intent = new Intent(INTENT_ACTION);
        intent.putExtra(INTENT_EXTRA_PACKAGE_NAME, getContextPackageName());
        intent.putExtra(INTENT_EXTRA_ACTIVITY_NAME, getEntryActivityName());
        intent.putExtra(INTENT_EXTRA_MESSAGE, String.valueOf(badgeCount));
        intent.putExtra(INTENT_EXTRA_SHOW_MESSAGE, badgeCount > 0);
        context.sendBroadcast(intent);
    }
}
