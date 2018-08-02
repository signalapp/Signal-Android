package org.thoughtcrime.securesms.database.helpers;


import android.content.ContentValues;
import android.content.Context;
import org.thoughtcrime.securesms.logging.Log;

import net.sqlcipher.database.SQLiteDatabase;

import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.SessionDatabase;
import org.thoughtcrime.securesms.util.Conversions;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.libsignal.state.SessionState;
import org.whispersystems.libsignal.state.StorageProtos.SessionStructure;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

class SessionStoreMigrationHelper {

  private static final String TAG = SessionStoreMigrationHelper.class.getSimpleName();

  private static final String SESSIONS_DIRECTORY_V2 = "sessions-v2";
  private static final Object FILE_LOCK             = new Object();

  private static final int SINGLE_STATE_VERSION   = 1;
  private static final int ARCHIVE_STATES_VERSION = 2;
  private static final int PLAINTEXT_VERSION      = 3;
  private static final int CURRENT_VERSION        = 3;

  static void migrateSessions(Context context, SQLiteDatabase database) {
    File directory = new File(context.getFilesDir(), SESSIONS_DIRECTORY_V2);

    if (directory.exists()) {
      File[] sessionFiles = directory.listFiles();

      if (sessionFiles != null) {
        for (File sessionFile : sessionFiles) {
          try {
            String[] parts   = sessionFile.getName().split("[.]");
            Address  address = Address.fromSerialized(parts[0]);

            int deviceId;

            if (parts.length > 1) deviceId = Integer.parseInt(parts[1]);
            else                  deviceId = SignalServiceAddress.DEFAULT_DEVICE_ID;

            FileInputStream in            = new FileInputStream(sessionFile);
            int             versionMarker = readInteger(in);

            if (versionMarker > CURRENT_VERSION) {
              throw new AssertionError("Unknown version: " + versionMarker + ", " + sessionFile.getAbsolutePath());
            }

            byte[] serialized = readBlob(in);
            in.close();

            if (versionMarker < PLAINTEXT_VERSION) {
              throw new AssertionError("Not plaintext: " + versionMarker + ", " + sessionFile.getAbsolutePath());
            }

            SessionRecord sessionRecord;

            if (versionMarker == SINGLE_STATE_VERSION) {
              Log.i(TAG, "Migrating single state version: " + sessionFile.getAbsolutePath());
              SessionStructure sessionStructure = SessionStructure.parseFrom(serialized);
              SessionState     sessionState     = new SessionState(sessionStructure);

              sessionRecord = new SessionRecord(sessionState);
            } else if (versionMarker >= ARCHIVE_STATES_VERSION) {
              Log.i(TAG, "Migrating session: " + sessionFile.getAbsolutePath());
              sessionRecord = new SessionRecord(serialized);
            } else {
              throw new AssertionError("Unknown version: " + versionMarker + ", " + sessionFile.getAbsolutePath());
            }


            ContentValues contentValues = new ContentValues();
            contentValues.put(SessionDatabase.ADDRESS, address.serialize());
            contentValues.put(SessionDatabase.DEVICE, deviceId);
            contentValues.put(SessionDatabase.RECORD, sessionRecord.serialize());

            database.insert(SessionDatabase.TABLE_NAME, null, contentValues);
          } catch (NumberFormatException | IOException e) {
            Log.w(TAG, e);
          }
        }
      }
    }
  }

  private static byte[] readBlob(FileInputStream in) throws IOException {
    int length       = readInteger(in);
    byte[] blobBytes = new byte[length];

    in.read(blobBytes, 0, blobBytes.length);
    return blobBytes;
  }

  private static int readInteger(FileInputStream in) throws IOException {
    byte[] integer = new byte[4];
    in.read(integer, 0, integer.length);
    return Conversions.byteArrayToInt(integer);
  }

}
