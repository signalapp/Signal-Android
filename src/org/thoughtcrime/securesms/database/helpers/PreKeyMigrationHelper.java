package org.thoughtcrime.securesms.database.helpers;


import android.content.ContentValues;
import android.content.Context;
import android.support.annotation.NonNull;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.sqlcipher.database.SQLiteDatabase;

import org.thoughtcrime.securesms.database.OneTimePreKeyDatabase;
import org.thoughtcrime.securesms.database.SignedPreKeyDatabase;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.Conversions;
import org.thoughtcrime.securesms.util.JsonUtils;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

class PreKeyMigrationHelper {

  private static final String PREKEY_DIRECTORY        = "prekeys";
  private static final String SIGNED_PREKEY_DIRECTORY = "signed_prekeys";

  private static final int    PLAINTEXT_VERSION      = 2;
  private static final int    CURRENT_VERSION_MARKER = 2;

  private static final String TAG = PreKeyMigrationHelper.class.getSimpleName();

  static boolean migratePreKeys(Context context, SQLiteDatabase database) {
    File[]  preKeyFiles = getPreKeyDirectory(context).listFiles();
    boolean clean       = true;

    if (preKeyFiles != null) {
      for (File preKeyFile : preKeyFiles) {
        if (!"index.dat".equals(preKeyFile.getName())) {
          try {
            PreKeyRecord preKey = new PreKeyRecord(loadSerializedRecord(preKeyFile));

            ContentValues contentValues = new ContentValues();
            contentValues.put(OneTimePreKeyDatabase.KEY_ID, preKey.getId());
            contentValues.put(OneTimePreKeyDatabase.PUBLIC_KEY, Base64.encodeBytes(preKey.getKeyPair().getPublicKey().serialize()));
            contentValues.put(OneTimePreKeyDatabase.PRIVATE_KEY, Base64.encodeBytes(preKey.getKeyPair().getPrivateKey().serialize()));
            database.insert(OneTimePreKeyDatabase.TABLE_NAME, null, contentValues);
            Log.i(TAG, "Migrated one-time prekey: " + preKey.getId());
          } catch (IOException | InvalidMessageException e) {
            Log.w(TAG, e);
            clean = false;
          }
        }
      }
    }

    File[] signedPreKeyFiles = getSignedPreKeyDirectory(context).listFiles();

    if (signedPreKeyFiles != null) {
      for (File signedPreKeyFile : signedPreKeyFiles) {
        if (!"index.dat".equals(signedPreKeyFile.getName())) {
          try {
            SignedPreKeyRecord signedPreKey = new SignedPreKeyRecord(loadSerializedRecord(signedPreKeyFile));

            ContentValues contentValues = new ContentValues();
            contentValues.put(SignedPreKeyDatabase.KEY_ID, signedPreKey.getId());
            contentValues.put(SignedPreKeyDatabase.PUBLIC_KEY, Base64.encodeBytes(signedPreKey.getKeyPair().getPublicKey().serialize()));
            contentValues.put(SignedPreKeyDatabase.PRIVATE_KEY, Base64.encodeBytes(signedPreKey.getKeyPair().getPrivateKey().serialize()));
            contentValues.put(SignedPreKeyDatabase.SIGNATURE, Base64.encodeBytes(signedPreKey.getSignature()));
            contentValues.put(SignedPreKeyDatabase.TIMESTAMP, signedPreKey.getTimestamp());
            database.insert(SignedPreKeyDatabase.TABLE_NAME, null, contentValues);
            Log.i(TAG, "Migrated signed prekey: " + signedPreKey.getId());
          } catch (IOException | InvalidMessageException e) {
            Log.w(TAG, e);
            clean = false;
          }
        }
      }
    }

    File oneTimePreKeyIndex = new File(getPreKeyDirectory(context), PreKeyIndex.FILE_NAME);
    File signedPreKeyIndex  = new File(getSignedPreKeyDirectory(context), SignedPreKeyIndex.FILE_NAME);

    if (oneTimePreKeyIndex.exists()) {
      try {
        InputStreamReader reader = new InputStreamReader(new FileInputStream(oneTimePreKeyIndex));
        PreKeyIndex        index = JsonUtils.fromJson(reader, PreKeyIndex.class);
        reader.close();

        Log.i(TAG, "Setting next prekey id: " + index.nextPreKeyId);
        TextSecurePreferences.setNextPreKeyId(context, index.nextPreKeyId);
      } catch (IOException e) {
        Log.w(TAG, e);
      }
    }

    if (signedPreKeyIndex.exists()) {
      try {
        InputStreamReader reader = new InputStreamReader(new FileInputStream(signedPreKeyIndex));
        SignedPreKeyIndex index  = JsonUtils.fromJson(reader, SignedPreKeyIndex.class);
        reader.close();

        Log.i(TAG, "Setting next signed prekey id: " + index.nextSignedPreKeyId);
        Log.i(TAG, "Setting active signed prekey id: " + index.activeSignedPreKeyId);
        TextSecurePreferences.setNextSignedPreKeyId(context, index.nextSignedPreKeyId);
        TextSecurePreferences.setActiveSignedPreKeyId(context, index.activeSignedPreKeyId);
      } catch (IOException e) {
        Log.w(TAG, e);
      }
    }

    return clean;
  }

  static void cleanUpPreKeys(@NonNull Context context) {
    File   preKeyDirectory = getPreKeyDirectory(context);
    File[] preKeyFiles     = preKeyDirectory.listFiles();

    if (preKeyFiles != null) {
      for (File preKeyFile : preKeyFiles) {
        Log.i(TAG, "Deleting: " + preKeyFile.getAbsolutePath());
        preKeyFile.delete();
      }

      Log.i(TAG, "Deleting: " + preKeyDirectory.getAbsolutePath());
      preKeyDirectory.delete();
    }

    File   signedPreKeyDirectory = getSignedPreKeyDirectory(context);
    File[] signedPreKeyFiles     = signedPreKeyDirectory.listFiles();

    if (signedPreKeyFiles != null) {
      for (File signedPreKeyFile : signedPreKeyFiles) {
        Log.i(TAG, "Deleting: " + signedPreKeyFile.getAbsolutePath());
        signedPreKeyFile.delete();
      }

      Log.i(TAG, "Deleting: " + signedPreKeyDirectory.getAbsolutePath());
      signedPreKeyDirectory.delete();
    }
  }

  private static byte[] loadSerializedRecord(File recordFile)
      throws IOException, InvalidMessageException
  {
    FileInputStream fin           = new FileInputStream(recordFile);
    int             recordVersion = readInteger(fin);

    if (recordVersion > CURRENT_VERSION_MARKER) {
      throw new IOException("Invalid version: " + recordVersion);
    }

    byte[] serializedRecord = readBlob(fin);

    if (recordVersion < PLAINTEXT_VERSION) {
      throw new IOException("Migration didn't happen! " + recordFile.getAbsolutePath() + ", " + recordVersion);
    }

    fin.close();
    return serializedRecord;
  }

  private static File getPreKeyDirectory(Context context) {
    return getRecordsDirectory(context, PREKEY_DIRECTORY);
  }

  private static File getSignedPreKeyDirectory(Context context) {
    return getRecordsDirectory(context, SIGNED_PREKEY_DIRECTORY);
  }

  private static File getRecordsDirectory(Context context, String directoryName) {
    File directory = new File(context.getFilesDir(), directoryName);

    if (!directory.exists()) {
      if (!directory.mkdirs()) {
        Log.w(TAG, "PreKey directory creation failed!");
      }
    }

    return directory;
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

  private static class PreKeyIndex {
    static final String FILE_NAME = "index.dat";

    @JsonProperty
    private int nextPreKeyId;

    public PreKeyIndex() {}
  }

  private static class SignedPreKeyIndex {
    static final String FILE_NAME = "index.dat";

    @JsonProperty
    private int nextSignedPreKeyId;

    @JsonProperty
    private int activeSignedPreKeyId = -1;

    public SignedPreKeyIndex() {}

  }


}
