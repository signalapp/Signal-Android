package org.thoughtcrime.securesms.crypto;

import android.content.Context;
import android.util.Log;

import com.google.protobuf.ByteString;
import org.thoughtcrime.securesms.database.keys.InvalidKeyIdException;
import org.thoughtcrime.securesms.database.keys.PreKeyRecord;
import org.thoughtcrime.securesms.encoded.PreKeyProtos.PreKeyEntity;
import org.whispersystems.textsecure.push.PreKeyList;
import org.whispersystems.textsecure.util.Base64;

import java.io.File;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.LinkedList;
import java.util.List;

public class PreKeyUtil {

  public static final int BATCH_SIZE = 70;

  public static List<PreKeyRecord> generatePreKeys(Context context, MasterSecret masterSecret) {
    List<PreKeyRecord> records        = new LinkedList<PreKeyRecord>();
    long               preKeyIdOffset = getNextPreKeyId(context);

    for (int i=0;i<BATCH_SIZE;i++) {
      Log.w("PreKeyUtil", "Generating PreKey: " + (preKeyIdOffset + i));
      PreKeyPair   keyPair = new PreKeyPair(masterSecret, KeyUtil.generateKeyPair());
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

  public static PreKeyList toJson(List<PreKeyRecord> records) {
    List<String> encoded = new LinkedList<String>();

    for (PreKeyRecord record : records) {
      PreKeyEntity entity  = PreKeyEntity.newBuilder().setId(record.getId())
                                         .setKey(ByteString.copyFrom(KeyUtil.encodePoint(record.getKeyPair().getPublicKey().getQ())))
                                         .build();

      String encodedEntity = Base64.encodeBytesWithoutPadding(entity.toByteArray());

      encoded.add(encodedEntity);
    }

    return new PreKeyList(encoded);
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

}
