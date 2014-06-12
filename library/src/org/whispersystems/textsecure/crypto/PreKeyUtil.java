/**
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

package org.whispersystems.textsecure.crypto;

import android.content.Context;
import android.util.Log;

import com.google.thoughtcrimegson.Gson;

import org.whispersystems.textsecure.crypto.ecc.Curve25519;
import org.whispersystems.textsecure.crypto.ecc.ECKeyPair;
import org.whispersystems.textsecure.storage.InvalidKeyIdException;
import org.whispersystems.textsecure.storage.PreKeyRecord;
import org.whispersystems.textsecure.util.Medium;
import org.whispersystems.textsecure.util.Util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

public class PreKeyUtil {

  public static final int BATCH_SIZE = 100;

  public static List<PreKeyRecord> generatePreKeys(Context context, MasterSecret masterSecret) {
    List<PreKeyRecord> records        = new LinkedList<PreKeyRecord>();
    int                preKeyIdOffset = getNextPreKeyId(context);

    for (int i=0;i<BATCH_SIZE;i++) {
      int          preKeyId = (preKeyIdOffset + i) % Medium.MAX_VALUE;
      ECKeyPair    keyPair  = Curve25519.generateKeyPair(true);
      PreKeyRecord record   = new PreKeyRecord(context, masterSecret, preKeyId, keyPair);

      record.save();
      records.add(record);
    }

    setNextPreKeyId(context, (preKeyIdOffset + BATCH_SIZE + 1) % Medium.MAX_VALUE);
    return records;
  }

  public static PreKeyRecord generateLastResortKey(Context context, MasterSecret masterSecret) {
    if (PreKeyRecord.hasRecord(context, Medium.MAX_VALUE)) {
      try {
        return new PreKeyRecord(context, masterSecret, Medium.MAX_VALUE);
      } catch (InvalidKeyIdException e) {
        Log.w("PreKeyUtil", e);
        PreKeyRecord.delete(context, Medium.MAX_VALUE);
      }
    }

    ECKeyPair    keyPair = Curve25519.generateKeyPair(true);
    PreKeyRecord record  = new PreKeyRecord(context, masterSecret, Medium.MAX_VALUE, keyPair);

    record.save();

    return record;
  }

//  public static List<PreKeyRecord> getPreKeys(Context context, MasterSecret masterSecret) {
//    List<PreKeyRecord> records      = new LinkedList<PreKeyRecord>();
//    File               directory    = getPreKeysDirectory(context);
//    String[]           keyRecordIds = directory.list();
//
//    Arrays.sort(keyRecordIds, new PreKeyRecordIdComparator());
//
//    for (String keyRecordId : keyRecordIds) {
//      try {
//        if (!keyRecordId.equals(PreKeyIndex.FILE_NAME) && Integer.parseInt(keyRecordId) != Medium.MAX_VALUE) {
//          records.add(new PreKeyRecord(context, masterSecret, Integer.parseInt(keyRecordId)));
//        }
//      } catch (InvalidKeyIdException e) {
//        Log.w("PreKeyUtil", e);
//        new File(getPreKeysDirectory(context), keyRecordId).delete();
//      } catch (NumberFormatException nfe) {
//        Log.w("PreKeyUtil", nfe);
//        new File(getPreKeysDirectory(context), keyRecordId).delete();
//      }
//    }
//
//    return records;
//  }
//
//  public static void clearPreKeys(Context context) {
//    File     directory  = getPreKeysDirectory(context);
//    String[] keyRecords = directory.list();
//
//    for (String keyRecord : keyRecords) {
//      new File(directory, keyRecord).delete();
//    }
//  }

  private static void setNextPreKeyId(Context context, int id) {
    try {
      File             nextFile = new File(getPreKeysDirectory(context), PreKeyIndex.FILE_NAME);
      FileOutputStream fout     = new FileOutputStream(nextFile);
      fout.write(new Gson().toJson(new PreKeyIndex(id)).getBytes());
      fout.close();
    } catch (IOException e) {
      Log.w("PreKeyUtil", e);
    }
  }

  private static int getNextPreKeyId(Context context) {
    try {
      File nextFile = new File(getPreKeysDirectory(context), PreKeyIndex.FILE_NAME);

      if (!nextFile.exists()) {
        return Util.getSecureRandom().nextInt(Medium.MAX_VALUE);
      } else {
        InputStreamReader reader = new InputStreamReader(new FileInputStream(nextFile));
        PreKeyIndex       index  = new Gson().fromJson(reader, PreKeyIndex.class);
        reader.close();
        return index.nextPreKeyId;
      }
    } catch (IOException e) {
      Log.w("PreKeyUtil", e);
      return Util.getSecureRandom().nextInt(Medium.MAX_VALUE);
    }
  }

  private static File getPreKeysDirectory(Context context) {
    File directory = new File(context.getFilesDir(), PreKeyRecord.PREKEY_DIRECTORY);

    if (!directory.exists())
      directory.mkdirs();

    return directory;
  }

  private static class PreKeyRecordIdComparator implements Comparator<String> {
    @Override
    public int compare(String lhs, String rhs) {
      if      (lhs.equals(PreKeyIndex.FILE_NAME)) return -1;
      else if (rhs.equals(PreKeyIndex.FILE_NAME)) return 1;

      try {
        long lhsLong = Long.parseLong(lhs);
        long rhsLong = Long.parseLong(rhs);

        if      (lhsLong < rhsLong) return -1;
        else if (lhsLong > rhsLong) return 1;
        else                        return 0;
      } catch (NumberFormatException e) {
        return 0;
      }
    }
  }

  private static class PreKeyIndex {
    public static final String FILE_NAME = "index.dat";

    private int nextPreKeyId;

    public PreKeyIndex() {}

    public PreKeyIndex(int nextPreKeyId) {
      this.nextPreKeyId = nextPreKeyId;
    }
  }

}
