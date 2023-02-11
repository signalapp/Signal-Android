package org.thoughtcrime.securesms.backup;


import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import net.zetetic.database.sqlcipher.SQLiteConstraintException;
import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.greenrobot.eventbus.EventBus;
import org.signal.core.util.SqlUtil;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.backup.proto.Attachment;
import org.thoughtcrime.securesms.backup.proto.Avatar;
import org.thoughtcrime.securesms.backup.proto.BackupFrame;
import org.thoughtcrime.securesms.backup.proto.DatabaseVersion;
import org.thoughtcrime.securesms.backup.proto.KeyValue;
import org.thoughtcrime.securesms.backup.proto.SharedPreference;
import org.thoughtcrime.securesms.backup.proto.SqlStatement;
import org.thoughtcrime.securesms.backup.proto.Sticker;
import org.thoughtcrime.securesms.crypto.AttachmentSecret;
import org.thoughtcrime.securesms.crypto.ModernEncryptingPartOutputStream;
import org.thoughtcrime.securesms.database.AttachmentTable;
import org.thoughtcrime.securesms.database.EmojiSearchTable;
import org.thoughtcrime.securesms.database.KeyValueDatabase;
import org.thoughtcrime.securesms.database.SearchTable;
import org.thoughtcrime.securesms.database.StickerTable;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.keyvalue.KeyValueDataSet;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.profiles.AvatarHelper;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.BackupUtil;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

public class FullBackupImporter extends FullBackupBase {

  @SuppressWarnings("unused")
  private static final String TAG = Log.tag(FullBackupImporter.class);

  public static void importFile(@NonNull Context context, @NonNull AttachmentSecret attachmentSecret,
                                @NonNull SQLiteDatabase db, @NonNull Uri uri, @NonNull String passphrase)
      throws IOException
  {
    try (InputStream is = getInputStream(context, uri)) {
      importFile(context, attachmentSecret, db, is, passphrase);
    }
  }

  public static void importFile(@NonNull Context context, @NonNull AttachmentSecret attachmentSecret,
                                @NonNull SQLiteDatabase db, @NonNull InputStream is, @NonNull String passphrase)
      throws IOException
  {
    int count = 0;

    SQLiteDatabase keyValueDatabase = KeyValueDatabase.getInstance(ApplicationDependencies.getApplication()).getSqlCipherDatabase();

    db.beginTransaction();
    keyValueDatabase.beginTransaction();
    try {
      BackupRecordInputStream inputStream = new BackupRecordInputStream(is, passphrase);

      dropAllTables(db);

      BackupFrame frame;

      while ((frame = inputStream.readFrame()).end != Boolean.TRUE) {
        if (count % 100 == 0) EventBus.getDefault().post(new BackupEvent(BackupEvent.Type.PROGRESS, count, 0));
        count++;

        if      (frame.version != null)    processVersion(db, frame.version);
        else if (frame.statement != null)  tryProcessStatement(db, frame.statement);
        else if (frame.preference != null) processPreference(context, frame.preference);
        else if (frame.attachment != null) processAttachment(context, attachmentSecret, db, frame.attachment, inputStream);
        else if (frame.sticker != null)    processSticker(context, attachmentSecret, db, frame.sticker, inputStream);
        else if (frame.avatar != null)     processAvatar(context, db, frame.avatar, inputStream);
        else if (frame.keyValue != null)   processKeyValue(frame.keyValue);
        else                            count--;
      }

      db.setTransactionSuccessful();
      keyValueDatabase.setTransactionSuccessful();
    } finally {
      db.endTransaction();
      keyValueDatabase.endTransaction();
    }

    EventBus.getDefault().post(new BackupEvent(BackupEvent.Type.FINISHED, count, 0));
  }

  private static @NonNull InputStream getInputStream(@NonNull Context context, @NonNull Uri uri) throws IOException{
    if (BackupUtil.isUserSelectionRequired(context) || uri.getScheme().equals("content")) {
      return Objects.requireNonNull(context.getContentResolver().openInputStream(uri));
    } else {
      return new FileInputStream(new File(Objects.requireNonNull(uri.getPath())));
    }
  }

  private static void processVersion(@NonNull SQLiteDatabase db, DatabaseVersion version) throws IOException {
    if (version.version == null || version.version > db.getVersion()) {
      throw new DatabaseDowngradeException(db.getVersion(), version.version != null ? version.version : -1);
    }

    db.setVersion(version.version);
  }

  private static void tryProcessStatement(@NonNull SQLiteDatabase db, SqlStatement statement) {
    try {
      processStatement(db, statement);
    } catch (SQLiteConstraintException e) {
      String tableName       = "?";
      String statementString = statement.statement;

      if (statementString != null && statementString.startsWith("INSERT INTO ")) {
        int nameStart = "INSERT INTO ".length();
        int nameEnd   = statementString.indexOf(" ", "INSERT INTO ".length());

        if (nameStart < statementString.length() && nameEnd > nameStart) {
          tableName = statementString.substring(nameStart, nameEnd);
        }
      }

      if (tableName.startsWith("msl_")) {
        Log.w(TAG, "Constraint failed when inserting into " + tableName + ". Ignoring.");
      } else {
        Log.w(TAG, "Constraint failed when inserting into " + tableName + ". Throwing!");
        throw e;
      }
    }
  }

  private static void processStatement(@NonNull SQLiteDatabase db, SqlStatement statement) {
    if (statement.statement == null) {
      Log.w(TAG, "Null statement!");
      return;
    }

    boolean isForMmsFtsSecretTable = statement.statement.contains(SearchTable.FTS_TABLE_NAME + "_");
    boolean isForEmojiSecretTable  = statement.statement.contains(EmojiSearchTable.TABLE_NAME + "_");
    boolean isForSqliteSecretTable = statement.statement.toLowerCase().startsWith("create table sqlite_");

    if (isForMmsFtsSecretTable || isForEmojiSecretTable || isForSqliteSecretTable) {
      Log.i(TAG, "Ignoring import for statement: " + statement.statement);
      return;
    }

    List<Object> parameters = new LinkedList<>();

    for (SqlStatement.SqlParameter parameter : statement.parameters) {
      if      (parameter.stringParamter != null)   parameters.add(parameter.stringParamter);
      else if (parameter.doubleParameter != null)  parameters.add(parameter.doubleParameter);
      else if (parameter.integerParameter != null) parameters.add(parameter.integerParameter);
      else if (parameter.blobParameter != null)    parameters.add(parameter.blobParameter.toByteArray());
      else if (parameter.nullparameter != null)    parameters.add(null);
    }

    if (parameters.size() > 0) db.execSQL(statement.statement, parameters.toArray());
    else                       db.execSQL(statement.statement);
  }

  private static void processAttachment(@NonNull Context context, @NonNull AttachmentSecret attachmentSecret, @NonNull SQLiteDatabase db, @NonNull Attachment attachment, BackupRecordInputStream inputStream)
      throws IOException
  {
    File                       dataFile = AttachmentTable.newFile(context);
    Pair<byte[], OutputStream> output   = ModernEncryptingPartOutputStream.createFor(attachmentSecret, dataFile, false);

    ContentValues contentValues = new ContentValues();

    try {
      inputStream.readAttachmentTo(output.second, attachment.length);

      contentValues.put(AttachmentTable.DATA, dataFile.getAbsolutePath());
      contentValues.put(AttachmentTable.DATA_RANDOM, output.first);
    } catch (BackupRecordInputStream.BadMacException e) {
      Log.w(TAG, "Bad MAC for attachment " + attachment.attachmentId + "! Can't restore it.", e);
      dataFile.delete();
      contentValues.put(AttachmentTable.DATA, (String) null);
      contentValues.put(AttachmentTable.DATA_RANDOM, (String) null);
    }

    db.update(AttachmentTable.TABLE_NAME, contentValues,
              AttachmentTable.ROW_ID + " = ? AND " + AttachmentTable.UNIQUE_ID + " = ?",
              new String[] {String.valueOf(attachment.rowId), String.valueOf(attachment.attachmentId)});
  }

  private static void processSticker(@NonNull Context context, @NonNull AttachmentSecret attachmentSecret, @NonNull SQLiteDatabase db, @NonNull Sticker sticker, BackupRecordInputStream inputStream)
      throws IOException
  {
    File stickerDirectory = context.getDir(StickerTable.DIRECTORY, Context.MODE_PRIVATE);
    File dataFile         = File.createTempFile("sticker", ".mms", stickerDirectory);

    Pair<byte[], OutputStream> output = ModernEncryptingPartOutputStream.createFor(attachmentSecret, dataFile, false);

    inputStream.readAttachmentTo(output.second, sticker.length);

    ContentValues contentValues = new ContentValues();
    contentValues.put(StickerTable.FILE_PATH, dataFile.getAbsolutePath());
    contentValues.put(StickerTable.FILE_LENGTH, sticker.length);
    contentValues.put(StickerTable.FILE_RANDOM, output.first);

    db.update(StickerTable.TABLE_NAME, contentValues,
              StickerTable._ID + " = ?",
              new String[] {String.valueOf(sticker.rowId)});
  }

  private static void processAvatar(@NonNull Context context, @NonNull SQLiteDatabase db, @NonNull Avatar avatar, @NonNull BackupRecordInputStream inputStream) throws IOException {
    if (avatar.recipientId != null) {
      RecipientId recipientId = RecipientId.from(avatar.recipientId);
      inputStream.readAttachmentTo(AvatarHelper.getOutputStream(context, recipientId, false), avatar.length);
    } else {
      if (avatar.name != null && SqlUtil.tableExists(db, "recipient_preferences")) {
        Log.w(TAG, "Avatar is missing a recipientId. Clearing signal_profile_avatar (legacy) so it can be fetched later.");
        db.execSQL("UPDATE recipient_preferences SET signal_profile_avatar = NULL WHERE recipient_ids = ?", new String[] { avatar.name });
      } else if (avatar.name != null && SqlUtil.tableExists(db, "recipient")) {
        Log.w(TAG, "Avatar is missing a recipientId. Clearing signal_profile_avatar so it can be fetched later.");
        db.execSQL("UPDATE recipient SET signal_profile_avatar = NULL WHERE phone = ?", new String[] { avatar.name });
      } else {
        Log.w(TAG, "Avatar is missing a recipientId. Skipping avatar restore.");
      }

      inputStream.readAttachmentTo(new ByteArrayOutputStream(), avatar.length);
    }
  }

  private static void processKeyValue(KeyValue keyValue) {
    KeyValueDataSet dataSet = new KeyValueDataSet();

    if (keyValue.key == null) {
      Log.w(TAG, "Null preference key!");
      return;
    }

    if (keyValue.blobValue != null) {
      dataSet.putBlob(keyValue.key, keyValue.blobValue.toByteArray());
    } else if (keyValue.booleanValue != null) {
      dataSet.putBoolean(keyValue.key, keyValue.booleanValue);
    } else if (keyValue.floatValue != null) {
      dataSet.putFloat(keyValue.key, keyValue.floatValue);
    } else if (keyValue.integerValue != null) {
      dataSet.putInteger(keyValue.key, keyValue.integerValue);
    } else if (keyValue.longValue != null) {
      dataSet.putLong(keyValue.key, keyValue.longValue);
    } else if (keyValue.stringValue != null) {
      dataSet.putString(keyValue.key, keyValue.stringValue);
    } else {
      Log.i(TAG, "Unknown KeyValue backup value, skipping");
      return;
    }

    KeyValueDatabase.getInstance(ApplicationDependencies.getApplication()).writeDataSet(dataSet, Collections.emptyList());
  }

  @SuppressLint("ApplySharedPref")
  private static void processPreference(@NonNull Context context, SharedPreference preference) {
    SharedPreferences preferences = context.getSharedPreferences(preference.file_, 0);

    // Identity keys were moved from shared prefs into SignalStore. Need to handle importing backups made before the migration.
    if ("SecureSMS-Preferences".equals(preference.file_)) {
      if ("pref_identity_public_v3".equals(preference.key) && preference.value_ != null) {
        SignalStore.account().restoreLegacyIdentityPublicKeyFromBackup(preference.value_);
      } else if ("pref_identity_private_v3".equals(preference.key) && preference.value_ != null) {
        SignalStore.account().restoreLegacyIdentityPrivateKeyFromBackup(preference.value_);
      }

      return;
    }

    if (preference.value_ != null) {
      preferences.edit().putString(preference.key, preference.value_).commit();
    } else if (preference.booleanValue != null) {
      preferences.edit().putBoolean(preference.key, preference.booleanValue).commit();
    } else if (preference.isStringSetValue == Boolean.TRUE) {
      preferences.edit().putStringSet(preference.key, new HashSet<>(preference.stringSetValue)).commit();
    }
  }

  private static void dropAllTables(@NonNull SQLiteDatabase db) {
    for (String trigger : SqlUtil.getAllTriggers(db)) {
      Log.i(TAG, "Dropping trigger: " + trigger);
      db.execSQL("DROP TRIGGER IF EXISTS " + trigger);
    }
    for (String table : getTablesToDropInOrder(db)) {
      Log.i(TAG, "Dropping table: " + table);
      db.execSQL("DROP TABLE IF EXISTS " + table);
    }
  }

  /**
   * Returns the list of tables we should drop, in the order they should be dropped in.
   * The order is chosen to ensure we won't violate any foreign key constraints when we import them.
   */
  private static List<String> getTablesToDropInOrder(@NonNull SQLiteDatabase input) {
    List<String> tables = SqlUtil.getAllTables(input)
                                 .stream()
                                 .filter(table -> !table.startsWith("sqlite_"))
                                 .sorted()
                                 .collect(Collectors.toList());


    Map<String, Set<String>> dependsOn = new LinkedHashMap<>();
    for (String table : tables) {
      dependsOn.put(table, SqlUtil.getForeignKeyDependencies(input, table));
    }

    for (String table : tables) {
      Set<String> dependsOnTable = dependsOn.keySet().stream().filter(t -> dependsOn.get(t).contains(table)).collect(Collectors.toSet());
      Log.i(TAG, "Tables that depend on " + table + ": " + dependsOnTable);
    }

    return computeTableDropOrder(dependsOn);
  }

  @VisibleForTesting
  static List<String> computeTableDropOrder(@NonNull Map<String, Set<String>> dependsOn) {
    List<String> rootNodes = dependsOn.keySet()
                                      .stream()
                                      .filter(table -> {
                                        boolean nothingDependsOnIt = dependsOn.values().stream().noneMatch(it -> it.contains(table));
                                        return nothingDependsOnIt;
                                      })
                                      .sorted()
                                      .collect(Collectors.toList());

    LinkedHashSet<String> dropOrder = new LinkedHashSet<>();

    Queue<String> processOrder = new LinkedList<>(rootNodes);

    while (!processOrder.isEmpty()) {
      String head = processOrder.remove();

      dropOrder.remove(head);
      dropOrder.add(head);

      Set<String> dependencies = dependsOn.get(head);
      if (dependencies != null) {
        processOrder.addAll(dependencies);
      }
    }

    return new ArrayList<>(dropOrder);
  }

  public static class DatabaseDowngradeException extends IOException {
    DatabaseDowngradeException(int currentVersion, int backupVersion) {
      super("Tried to import a backup with version " + backupVersion + " into a database with version " + currentVersion);
    }
  }
}
