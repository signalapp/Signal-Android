
/*
 * Copyright (C) 2018 Open Whisper Systems
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
package org.thoughtcrime.securesms.database;

import android.content.Context;

import net.sqlcipher.database.SQLiteDatabase;

import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper;
import org.thoughtcrime.securesms.dependencies.DatabaseComponent;

public class DatabaseFactory {
  public static void upgradeRestored(Context context, SQLiteDatabase database){
    SQLCipherOpenHelper databaseHelper = DatabaseComponent.get(context).openHelper();
    databaseHelper.onUpgrade(database, database.getVersion(), -1);
    databaseHelper.markCurrent(database);
  }
}
