package org.whispersystems.textsecure.crypto;

import android.content.Context;
import android.util.Log;

import com.google.protobuf.ByteString;
import org.whispersystems.textsecure.encoded.PreKeyProtos.PreKeyEntity;
import org.whispersystems.textsecure.push.PreKeyList;
import org.whispersystems.textsecure.storage.InvalidKeyIdException;
import org.whispersystems.textsecure.storage.PreKeyRecord;
import org.whispersystems.textsecure.util.Base64;

import java.io.File;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

public class PreKeyUtil {

  public static final int BATCH_SIZE = 70;

  public static List<PreKeyRecord> generatePreKeys(Context context, MasterSecret masterSecret) {
    List<PreKeyRecord> records        = new LinkedList<PreKeyRecord>();
    long               preKeyIdOffset = getNextPreKeyId(context);

    for (int i=0;i<BATCH_SIZE;i++) {
      Log.w("PreKeyUtil", "Generating PreKey: " + (preKeyIdOffset + i));
      PreKeyPair keyPair = new PreKeyPair(masterSecret, KeyUtil.generateKeyPair());
      PreKeyRecord record  = new PreKeyRecord(context, masterSecret, preKeyIdOffset + i, keyPair);

      record.save();
      records.add(record);
    }

    return records;
  }

  public static List<PreKeyRecord> getPreKeys(Context context, MasterSecret masterSecret) {
    List<PreKeyRecord> records      = new LinkedList<PreKeyRecord>();
    File               directory    = getPreKeysDirectory(context);
    String[]           keyRecordIds = directory.list();

    Arrays.sort(keyRecordIds, new PreKeyRecordIdComparator());

    for (String keyRecordId : keyRecordIds) {
      try {
        records.add(new PreKeyRecord(context, masterSecret, Long.parseLong(keyRecordId)));
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

  private static long getNextPreKeyId(Context context) {
    try {
      File     directory    = getPreKeysDirectory(context);
      String[] keyRecordIds = directory.list();
      long     nextPreKeyId = 0;

      for (String keyRecordId : keyRecordIds) {
        if (Long.parseLong(keyRecordId) > nextPreKeyId)
          nextPreKeyId = Long.parseLong(keyRecordId);
      }

      if (nextPreKeyId == 0)
        nextPreKeyId = SecureRandom.getInstance("SHA1PRNG").nextInt(Integer.MAX_VALUE/2);

      return nextPreKeyId;
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
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

}
