package org.whispersystems.textsecure.crypto;

import android.content.Context;
import android.util.Log;

import com.google.thoughtcrimegson.Gson;
import org.whispersystems.textsecure.storage.InvalidKeyIdException;
import org.whispersystems.textsecure.storage.PreKeyRecord;
import org.whispersystems.textsecure.util.Medium;
import org.whispersystems.textsecure.util.Util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

public class PreKeyUtil {

  public static final int BATCH_SIZE = 70;

  public static List<PreKeyRecord> generatePreKeys(Context context, MasterSecret masterSecret) {
    List<PreKeyRecord> records        = new LinkedList<PreKeyRecord>();
    int                preKeyIdOffset = getNextPreKeyId(context);

    for (int i=0;i<BATCH_SIZE;i++) {
      int          preKeyId = (preKeyIdOffset + i) % Medium.MAX_VALUE;
      PreKeyPair   keyPair  = new PreKeyPair(masterSecret, KeyUtil.generateKeyPair());
      PreKeyRecord record   = new PreKeyRecord(context, masterSecret, preKeyId, keyPair);

      record.save();
      records.add(record);
    }

    setNextPreKeyId(context, (preKeyIdOffset + BATCH_SIZE + 1) % Medium.MAX_VALUE);
    return records;
  }

  public static List<PreKeyRecord> getPreKeys(Context context, MasterSecret masterSecret) {
    List<PreKeyRecord> records      = new LinkedList<PreKeyRecord>();
    File               directory    = getPreKeysDirectory(context);
    String[]           keyRecordIds = directory.list();

    Arrays.sort(keyRecordIds, new PreKeyRecordIdComparator());

    for (String keyRecordId : keyRecordIds) {
      try {
        records.add(new PreKeyRecord(context, masterSecret, Integer.parseInt(keyRecordId)));
      } catch (InvalidKeyIdException e) {
        Log.w("PreKeyUtil", e);
        new File(getPreKeysDirectory(context), keyRecordId).delete();
      } catch (NumberFormatException nfe) {
        Log.w("PreKeyUtil", nfe);
        new File(getPreKeysDirectory(context), keyRecordId).delete();
      }
    }

    return records;
  }

  public static void clearPreKeys(Context context) {
    File     directory  = getPreKeysDirectory(context);
    String[] keyRecords = directory.list();

    for (String keyRecord : keyRecords) {
      new File(directory, keyRecord).delete();
    }
  }

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

      if (nextFile.exists()) {
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
