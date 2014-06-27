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

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

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
