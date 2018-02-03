package org.thoughtcrime.securesms.database.helpers;


import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import com.annimon.stream.function.BiFunction;

import org.thoughtcrime.securesms.DatabaseUpgradeActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.crypto.AsymmetricMasterCipher;
import org.thoughtcrime.securesms.crypto.AttachmentSecretProvider;
import org.thoughtcrime.securesms.crypto.MasterCipher;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.MasterSecretUtil;
import org.thoughtcrime.securesms.service.GenericForegroundService;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.InvalidMessageException;

import java.io.IOException;

public class SQLCipherMigrationHelper {

  private static final String TAG = SQLCipherMigrationHelper.class.getSimpleName();

  private static final long ENCRYPTION_SYMMETRIC_BIT  = 0x80000000;
  private static final long ENCRYPTION_ASYMMETRIC_BIT = 0x40000000;

  static void migratePlaintext(@NonNull Context context,
                               @NonNull android.database.sqlite.SQLiteDatabase legacyDb,
                               @NonNull net.sqlcipher.database.SQLiteDatabase modernDb)
  {
    modernDb.beginTransaction();
    try {
      GenericForegroundService.startForegroundTask(context, context.getString(R.string.SQLCipherMigrationHelper_migrating_signal_database));
      copyTable("identities", legacyDb, modernDb, null);
      copyTable("push", legacyDb, modernDb, null);
      copyTable("groups", legacyDb, modernDb, null);
      copyTable("recipient_preferences", legacyDb, modernDb, null);
      copyTable("group_receipts", legacyDb, modernDb, null);
      modernDb.setTransactionSuccessful();
    } finally {
      modernDb.endTransaction();
      GenericForegroundService.stopForegroundTask(context);
    }
  }

  public static void migrateCiphertext(@NonNull Context context,
                                       @NonNull MasterSecret masterSecret,
                                       @NonNull android.database.sqlite.SQLiteDatabase legacyDb,
                                       @NonNull net.sqlcipher.database.SQLiteDatabase modernDb,
                                       @Nullable DatabaseUpgradeActivity.DatabaseUpgradeListener listener)
  {
    MasterCipher           legacyCipher           = new MasterCipher(masterSecret);
    AsymmetricMasterCipher legacyAsymmetricCipher = new AsymmetricMasterCipher(MasterSecretUtil.getAsymmetricMasterSecret(context, masterSecret));

    modernDb.beginTransaction();

    try {
      GenericForegroundService.startForegroundTask(context, context.getString(R.string.SQLCipherMigrationHelper_migrating_signal_database));
      int total = 5000;

      copyTable("sms", legacyDb, modernDb, (row, progress) -> {
        Pair<Long, String> plaintext = getPlaintextBody(legacyCipher, legacyAsymmetricCipher,
                                                        row.getAsLong("type"),
                                                        row.getAsString("body"));

        row.put("body", plaintext.second);
        row.put("type", plaintext.first);

        if (listener != null && (progress.first % 1000 == 0)) {
          listener.setProgress(getTotalProgress(0, progress.first, progress.second), total);
        }

        return row;
      });

      copyTable("mms", legacyDb, modernDb, (row, progress) -> {
        Pair<Long, String> plaintext = getPlaintextBody(legacyCipher, legacyAsymmetricCipher,
                                                        row.getAsLong("msg_box"),
                                                        row.getAsString("body"));

        row.put("body", plaintext.second);
        row.put("msg_box", plaintext.first);

        if (listener != null && (progress.first % 1000 == 0)) {
          listener.setProgress(getTotalProgress(1000, progress.first, progress.second), total);
        }

        return row;
      });

      copyTable("part", legacyDb, modernDb, (row, progress) -> {
        String fileName = row.getAsString("file_name");
        String mediaKey = row.getAsString("cd");

        try {
          if (!TextUtils.isEmpty(fileName)) {
            row.put("file_name", legacyCipher.decryptBody(fileName));
          }
        } catch (InvalidMessageException e) {
          Log.w(TAG, e);
        }

        try {
          if (!TextUtils.isEmpty(mediaKey)) {
            byte[] plaintext;

            if (mediaKey.startsWith("?ASYNC-")) {
              plaintext = legacyAsymmetricCipher.decryptBytes(Base64.decode(mediaKey.substring("?ASYNC-".length())));
            } else {
              plaintext = legacyCipher.decryptBytes(Base64.decode(mediaKey));
            }

            row.put("cd", Base64.encodeBytes(plaintext));
          }
        } catch (IOException | InvalidMessageException e) {
          Log.w(TAG, e);
        }

        if (listener != null && (progress.first % 1000 == 0)) {
          listener.setProgress(getTotalProgress(2000, progress.first, progress.second), total);
        }

        return row;
      });

      copyTable("thread", legacyDb, modernDb, (row, progress) -> {
        Pair<Long, String> plaintext = getPlaintextBody(legacyCipher, legacyAsymmetricCipher,
                                                        row.getAsLong("snippet_type"),
                                                        row.getAsString("snippet"));

        row.put("snippet", plaintext.second);
        row.put("snippet_type", plaintext.first);

        if (listener != null && (progress.first % 1000 == 0)) {
          listener.setProgress(getTotalProgress(3000, progress.first, progress.second), total);
        }

        return row;
      });


      copyTable("drafts", legacyDb, modernDb, (row, progress) -> {
        String draftType = row.getAsString("type");
        String draft     = row.getAsString("value");

        try {
          if (!TextUtils.isEmpty(draftType)) row.put("type", legacyCipher.decryptBody(draftType));
          if (!TextUtils.isEmpty(draft))     row.put("value", legacyCipher.decryptBody(draft));
        } catch (InvalidMessageException e) {
          Log.w(TAG, e);
        }

        if (listener != null && (progress.first % 1000 == 0)) {
          listener.setProgress(getTotalProgress(4000, progress.first, progress.second), total);
        }

        return row;
      });

      AttachmentSecretProvider.getInstance(context).setClassicKey(context, masterSecret.getEncryptionKey().getEncoded(), masterSecret.getMacKey().getEncoded());
      TextSecurePreferences.setNeedsSqlCipherMigration(context, false);
      modernDb.setTransactionSuccessful();
    } finally {
      modernDb.endTransaction();
      GenericForegroundService.stopForegroundTask(context);
    }
  }

  private static void copyTable(@NonNull String tableName,
                                @NonNull android.database.sqlite.SQLiteDatabase legacyDb,
                                @NonNull net.sqlcipher.database.SQLiteDatabase modernDb,
                                @Nullable BiFunction<ContentValues, Pair<Integer, Integer>, ContentValues> transformer)
  {
    try (Cursor cursor = legacyDb.query(tableName, null, null, null, null, null, null)) {
      int count    = (cursor != null) ? cursor.getCount() : 0;
      int progress = 1;

      while (cursor != null && cursor.moveToNext()) {
        ContentValues row = new ContentValues();

        for (int i=0;i<cursor.getColumnCount();i++) {
          String columnName = cursor.getColumnName(i);

          switch (cursor.getType(i)) {
            case Cursor.FIELD_TYPE_STRING:  row.put(columnName, cursor.getString(i));  break;
            case Cursor.FIELD_TYPE_FLOAT:   row.put(columnName, cursor.getFloat(i));   break;
            case Cursor.FIELD_TYPE_INTEGER: row.put(columnName, cursor.getLong(i));    break;
            case Cursor.FIELD_TYPE_BLOB:    row.put(columnName, cursor.getBlob(i));    break;
          }
        }

        if (transformer != null) {
          row = transformer.apply(row, new Pair<>(progress++, count));
        }

        modernDb.insert(tableName, null, row);
      }
    }
  }

  private static Pair<Long, String> getPlaintextBody(@NonNull MasterCipher legacyCipher,
                                                     @NonNull AsymmetricMasterCipher legacyAsymmetricCipher,
                                                     long type,
                                                     @Nullable String body)
  {
    try {
      if (!TextUtils.isEmpty(body)) {
        if      ((type & ENCRYPTION_SYMMETRIC_BIT) != 0)  body = legacyCipher.decryptBody(body);
        else if ((type & ENCRYPTION_ASYMMETRIC_BIT) != 0) body = legacyAsymmetricCipher.decryptBody(body);
      }
    } catch (InvalidMessageException | IOException e) {
      Log.w(TAG, e);
    }

    type &= ~(ENCRYPTION_SYMMETRIC_BIT);
    type &= ~(ENCRYPTION_ASYMMETRIC_BIT);

    return new Pair<>(type, body);
  }

  private static int getTotalProgress(int sectionOffset, int sectionProgress, int sectionTotal) {
    double percentOfSectionComplete = ((double)sectionProgress) / ((double)sectionTotal);
    return sectionOffset + (int)(((double)1000) * percentOfSectionComplete);
  }
}
