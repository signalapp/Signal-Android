package org.thoughtcrime.securesms;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.signal.core.util.StreamUtil;
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.MasterSecretUtil;
import org.thoughtcrime.securesms.database.KeyValueDatabase;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.SetUtil;
import org.thoughtcrime.securesms.util.SqlUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Helper to initialize app state with the right database contents, shared prefs, etc.
 */
final class MockAppDataInitializer {

  private static final Set<String> IGNORED_TABLES = SetUtil.newHashSet(
      "sqlite_sequence",
      "sms_fts",
      "sms_fts_data",
      "sms_fts_idx",
      "sms_fts_docsize",
      "sms_fts_config",
      "mms_fts",
      "mms_fts_data",
      "mms_fts_idx",
      "mms_fts_docsize",
      "mms_fts_config",
      "emoji_search",
      "emoji_search_data",
      "emoji_search_idx",
      "emoji_search_docsize",
      "emoji_search_config"
  );

  public static void initialize(@NonNull Application application, @NonNull File sqlDirectory) throws IOException {
    String localE164   = StreamUtil.readFullyAsString(new FileInputStream(new File(sqlDirectory, "e164.txt"))).trim();
    String mainSql     = StreamUtil.readFullyAsString(new FileInputStream(new File(sqlDirectory, "signal.sql")));
    String keyValueSql = StreamUtil.readFullyAsString(new FileInputStream(new File(sqlDirectory, "signal-key-value.sql")));

    initializeDatabase(DatabaseFactory.getInstance(application).getRawDatabase(), mainSql);
    initializeDatabase(KeyValueDatabase.getInstance(application).getSqlCipherDatabase(), keyValueSql);

    initializePreferences(application, localE164);

    SignalStore.resetCache();
  }

  private static void initializeDatabase(@NonNull SQLiteDatabase db, @NonNull String sql) {
    db.beginTransaction();
    try {
      clearAllTables(db);
      execStatements(db, sql);

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  private static void clearAllTables(@NonNull SQLiteDatabase db) {
    List<String> tables = SqlUtil.getAllTables(db);

    for (String table : tables) {
      if (!IGNORED_TABLES.contains(table)) {
        db.execSQL("DELETE FROM " + table);
      }
    }
  }

  private static void execStatements(@NonNull SQLiteDatabase db, @NonNull String sql) {
    List<String> statements = SqlUtil.splitStatements(sql);

    for (String statement : statements) {
      db.execSQL(statement);
    }
  }

  private static void initializePreferences(@NonNull Context context, @NonNull String localE164) {
    MasterSecret masterSecret = MasterSecretUtil.generateMasterSecret(context, MasterSecretUtil.UNENCRYPTED_PASSPHRASE);

    MasterSecretUtil.generateAsymmetricMasterSecret(context, masterSecret);
    IdentityKeyUtil.generateIdentityKeys(context);

    TextSecurePreferences.setPromptedPushRegistration(context, true);
    TextSecurePreferences.setLocalNumber(context, localE164);
    TextSecurePreferences.setLocalAci(context, Recipient.external(context, localE164).requireAci());
    TextSecurePreferences.setPushRegistered(context, true);
  }
}
