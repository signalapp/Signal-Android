/**
 * Copyright (C) 2014 Open Whisper Systems
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
import android.content.res.AssetManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.text.TextUtils;
import org.thoughtcrime.securesms.logging.Log;

import org.thoughtcrime.securesms.mms.LegacyMmsConnection.Apn;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Database to query APN and MMSC information
 */
public class ApnDatabase {
  private static final String TAG = ApnDatabase.class.getSimpleName();

  private final SQLiteDatabase db;
  private final Context        context;

  private static final String DATABASE_NAME = "apns.db";
  private static final String ASSET_PATH    = "databases" + File.separator + DATABASE_NAME;

  private static final String TABLE_NAME              = "apns";
  private static final String ID_COLUMN               = "_id";
  private static final String MCC_MNC_COLUMN          = "mccmnc";
  private static final String MCC_COLUMN              = "mcc";
  private static final String MNC_COLUMN              = "mnc";
  private static final String CARRIER_COLUMN          = "carrier";
  private static final String APN_COLUMN              = "apn";
  private static final String MMSC_COLUMN             = "mmsc";
  private static final String PORT_COLUMN             = "port";
  private static final String TYPE_COLUMN             = "type";
  private static final String PROTOCOL_COLUMN         = "protocol";
  private static final String BEARER_COLUMN           = "bearer";
  private static final String ROAMING_PROTOCOL_COLUMN = "roaming_protocol";
  private static final String CARRIER_ENABLED_COLUMN  = "carrier_enabled";
  private static final String MMS_PROXY_COLUMN        = "mmsproxy";
  private static final String MMS_PORT_COLUMN         = "mmsport";
  private static final String PROXY_COLUMN            = "proxy";
  private static final String MVNO_MATCH_DATA_COLUMN  = "mvno_match_data";
  private static final String MVNO_TYPE_COLUMN        = "mvno";
  private static final String AUTH_TYPE_COLUMN        = "authtype";
  private static final String USER_COLUMN             = "user";
  private static final String PASSWORD_COLUMN         = "password";
  private static final String SERVER_COLUMN           = "server";

  private static final String BASE_SELECTION = MCC_MNC_COLUMN + " = ?";

  private static ApnDatabase instance = null;

  public synchronized static ApnDatabase getInstance(Context context) throws IOException {
    if (instance == null) instance = new ApnDatabase(context.getApplicationContext());
    return instance;
  }

  private ApnDatabase(final Context context) throws IOException {
    this.context = context;

    File dbFile = context.getDatabasePath(DATABASE_NAME);

    if (!dbFile.getParentFile().exists() && !dbFile.getParentFile().mkdir()) {
      throw new IOException("couldn't make databases directory");
    }

    Util.copy(context.getAssets().open(ASSET_PATH, AssetManager.ACCESS_STREAMING),
              new FileOutputStream(dbFile));

    try {
      this.db = SQLiteDatabase.openDatabase(context.getDatabasePath(DATABASE_NAME).getPath(),
                                            null,
                                            SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
    } catch (SQLiteException e) {
      throw new IOException(e);
    }
  }

  private Apn getCustomApnParameters() {
      String mmsc = TextSecurePreferences.getMmscUrl(context).trim();

      if (!TextUtils.isEmpty(mmsc) && !mmsc.startsWith("http"))
        mmsc = "http://" + mmsc;

      String proxy = TextSecurePreferences.getMmscProxy(context);
      String port  = TextSecurePreferences.getMmscProxyPort(context);
      String user  = TextSecurePreferences.getMmscUsername(context);
      String pass  = TextSecurePreferences.getMmscPassword(context);

      return new Apn(mmsc, proxy, port, user, pass);
  }

  public Apn getDefaultApnParameters(String mccmnc, String apn) {
    if (mccmnc == null) {
      Log.w(TAG, "mccmnc was null, returning null");
      return Apn.EMPTY;
    }

    Cursor cursor = null;

    try {
      if (apn != null) {
        Log.d(TAG, "Querying table for MCC+MNC " + mccmnc + " and APN name " + apn);
        cursor = db.query(TABLE_NAME, null,
                          BASE_SELECTION + " AND " + APN_COLUMN + " = ?",
                          new String[] {mccmnc, apn},
                          null, null, null);
      }

      if (cursor == null || !cursor.moveToFirst()) {
        if (cursor != null) cursor.close();
        Log.d(TAG, "Querying table for MCC+MNC " + mccmnc + " without APN name");
        cursor = db.query(TABLE_NAME, null,
                          BASE_SELECTION,
                          new String[] {mccmnc},
                          null, null, null);
      }

      if (cursor != null && cursor.moveToFirst()) {
        Apn params = new Apn(cursor.getString(cursor.getColumnIndexOrThrow(MMSC_COLUMN)),
                             cursor.getString(cursor.getColumnIndexOrThrow(MMS_PROXY_COLUMN)),
                             cursor.getString(cursor.getColumnIndexOrThrow(MMS_PORT_COLUMN)),
                             cursor.getString(cursor.getColumnIndexOrThrow(USER_COLUMN)),
                             cursor.getString(cursor.getColumnIndexOrThrow(PASSWORD_COLUMN)));
        Log.d(TAG, "Returning preferred APN " + params);
        return params;
      }

      Log.w(TAG, "No matching APNs found, returning null");

      return Apn.EMPTY;
    } finally {
      if (cursor != null) cursor.close();
    }

  }

  public Optional<Apn> getMmsConnectionParameters(String mccmnc, String apn) {
    Apn customApn  = getCustomApnParameters();
    Apn defaultApn = getDefaultApnParameters(mccmnc, apn);
    Apn result     = new Apn(customApn, defaultApn,
                             TextSecurePreferences.getUseCustomMmsc(context),
                             TextSecurePreferences.getUseCustomMmscProxy(context),
                             TextSecurePreferences.getUseCustomMmscProxyPort(context),
                             TextSecurePreferences.getUseCustomMmscUsername(context),
                             TextSecurePreferences.getUseCustomMmscPassword(context));

    if (TextUtils.isEmpty(result.getMmsc())) return Optional.absent();
    else                                     return Optional.of(result);
  }
}
