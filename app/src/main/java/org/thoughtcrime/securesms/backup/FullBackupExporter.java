package org.thoughtcrime.securesms.backup;


import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import androidx.documentfile.provider.DocumentFile;

import com.annimon.stream.function.Predicate;
import com.google.protobuf.ByteString;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.greenrobot.eventbus.EventBus;
import org.signal.core.util.Conversions;
import org.signal.core.util.CursorUtil;
import org.signal.core.util.SetUtil;
import org.signal.core.util.SqlUtil;
import org.signal.core.util.Stopwatch;
import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.kdf.HKDF;
import org.signal.libsignal.protocol.util.ByteUtil;
import org.thoughtcrime.securesms.attachments.AttachmentId;
import org.thoughtcrime.securesms.crypto.AttachmentSecret;
import org.thoughtcrime.securesms.crypto.ClassicDecryptingPartInputStream;
import org.thoughtcrime.securesms.crypto.ModernDecryptingPartInputStream;
import org.thoughtcrime.securesms.database.AttachmentTable;
import org.thoughtcrime.securesms.database.EmojiSearchTable;
import org.thoughtcrime.securesms.database.GroupReceiptTable;
import org.thoughtcrime.securesms.database.KeyValueDatabase;
import org.thoughtcrime.securesms.database.MentionTable;
import org.thoughtcrime.securesms.database.MessageTable;
import org.thoughtcrime.securesms.database.OneTimePreKeyTable;
import org.thoughtcrime.securesms.database.PendingRetryReceiptTable;
import org.thoughtcrime.securesms.database.ReactionTable;
import org.thoughtcrime.securesms.database.SearchTable;
import org.thoughtcrime.securesms.database.SenderKeyTable;
import org.thoughtcrime.securesms.database.SenderKeySharedTable;
import org.thoughtcrime.securesms.database.SessionTable;
import org.thoughtcrime.securesms.database.SignedPreKeyTable;
import org.thoughtcrime.securesms.database.StickerTable;
import org.thoughtcrime.securesms.database.model.AvatarPickerDatabase;
import org.thoughtcrime.securesms.database.model.MessageId;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.keyvalue.KeyValueDataSet;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.profiles.AvatarHelper;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class FullBackupExporter extends FullBackupBase {

  private static final String TAG = Log.tag(FullBackupExporter.class);

  private static final long DATABASE_VERSION_RECORD_COUNT    = 1L;
  private static final long TABLE_RECORD_COUNT_MULTIPLIER    = 3L;
  private static final long IDENTITY_KEY_BACKUP_RECORD_COUNT = 2L;
  private static final long FINAL_MESSAGE_COUNT              = 1L;

  /**
   * Tables in list will still have their *schema* exported (so the tables will be created),
   * but we will not export the actual contents.
   */
  private static final Set<String> TABLE_CONTENT_BLOCKLIST = SetUtil.newHashSet(
      SignedPreKeyTable.TABLE_NAME,
      OneTimePreKeyTable.TABLE_NAME,
      SessionTable.TABLE_NAME,
      SearchTable.FTS_TABLE_NAME,
      EmojiSearchTable.TABLE_NAME,
      SenderKeyTable.TABLE_NAME,
      SenderKeySharedTable.TABLE_NAME,
      PendingRetryReceiptTable.TABLE_NAME,
      AvatarPickerDatabase.TABLE_NAME
  );

  public static BackupEvent export(@NonNull Context context,
                            @NonNull AttachmentSecret attachmentSecret,
                            @NonNull SQLiteDatabase input,
                            @NonNull File output,
                            @NonNull String passphrase,
                            @NonNull BackupCancellationSignal cancellationSignal)
      throws IOException
  {
    try (OutputStream outputStream = new FileOutputStream(output)) {
      return internalExport(context, attachmentSecret, input, outputStream, passphrase, true, cancellationSignal);
    }
  }

  @RequiresApi(29)
  public static BackupEvent export(@NonNull Context context,
                            @NonNull AttachmentSecret attachmentSecret,
                            @NonNull SQLiteDatabase input,
                            @NonNull DocumentFile output,
                            @NonNull String passphrase,
                            @NonNull BackupCancellationSignal cancellationSignal)
      throws IOException
  {
    try (OutputStream outputStream = Objects.requireNonNull(context.getContentResolver().openOutputStream(output.getUri()))) {
      return internalExport(context, attachmentSecret, input, outputStream, passphrase, true, cancellationSignal);
    }
  }

  public static void transfer(@NonNull Context context,
                              @NonNull AttachmentSecret attachmentSecret,
                              @NonNull SQLiteDatabase input,
                              @NonNull OutputStream outputStream,
                              @NonNull String passphrase)
      throws IOException
  {
    EventBus.getDefault().post(internalExport(context, attachmentSecret, input, outputStream, passphrase, false, () -> false));
  }

  private static BackupEvent internalExport(@NonNull Context context,
                                            @NonNull AttachmentSecret attachmentSecret,
                                            @NonNull SQLiteDatabase input,
                                            @NonNull OutputStream fileOutputStream,
                                            @NonNull String passphrase,
                                            boolean closeOutputStream,
                                            @NonNull BackupCancellationSignal cancellationSignal)
      throws IOException
  {
    BackupFrameOutputStream outputStream          = new BackupFrameOutputStream(fileOutputStream, passphrase);
    int                     count                 = 0;
    long                    estimatedCountOutside;

    try {
      outputStream.writeDatabaseVersion(input.getVersion());
      count++;

      List<String> tables = exportSchema(input, outputStream);
      count += tables.size() * TABLE_RECORD_COUNT_MULTIPLIER;

      final long estimatedCount = calculateCount(context, input, tables);
      estimatedCountOutside = estimatedCount;

      Stopwatch stopwatch = new Stopwatch("Backup");

      for (String table : tables) {
        throwIfCanceled(cancellationSignal);
        if (table.equals(MessageTable.TABLE_NAME)) {
          count = exportTable(table, input, outputStream, FullBackupExporter::isNonExpiringMmsMessage, null, count, estimatedCount, cancellationSignal);
        } else if (table.equals(ReactionTable.TABLE_NAME)) {
          count = exportTable(table, input, outputStream, cursor -> isForNonExpiringMessage(input, new MessageId(CursorUtil.requireLong(cursor, ReactionTable.MESSAGE_ID))), null, count, estimatedCount, cancellationSignal);
        } else if (table.equals(MentionTable.TABLE_NAME)) {
          count = exportTable(table, input, outputStream, cursor -> isForNonExpiringMmsMessage(input, CursorUtil.requireLong(cursor, MentionTable.MESSAGE_ID)), null, count, estimatedCount, cancellationSignal);
        } else if (table.equals(GroupReceiptTable.TABLE_NAME)) {
          count = exportTable(table, input, outputStream, cursor -> isForNonExpiringMmsMessage(input, cursor.getLong(cursor.getColumnIndexOrThrow(GroupReceiptTable.MMS_ID))), null, count, estimatedCount, cancellationSignal);
        } else if (table.equals(AttachmentTable.TABLE_NAME)) {
          count = exportTable(table, input, outputStream, cursor -> isForNonExpiringMmsMessage(input, cursor.getLong(cursor.getColumnIndexOrThrow(AttachmentTable.MMS_ID))), (cursor, innerCount) -> exportAttachment(attachmentSecret, cursor, outputStream, innerCount, estimatedCount), count, estimatedCount, cancellationSignal);
        } else if (table.equals(StickerTable.TABLE_NAME)) {
          count = exportTable(table, input, outputStream, cursor -> true, (cursor, innerCount) -> exportSticker(attachmentSecret, cursor, outputStream, innerCount, estimatedCount), count, estimatedCount, cancellationSignal);
        } else if (!TABLE_CONTENT_BLOCKLIST.contains(table)) {
          count = exportTable(table, input, outputStream, null, null, count, estimatedCount, cancellationSignal);
        }
        stopwatch.split("table::" + table);
      }

      for (BackupProtos.SharedPreference preference : TextSecurePreferences.getPreferencesToSaveToBackup(context)) {
        throwIfCanceled(cancellationSignal);
        EventBus.getDefault().post(new BackupEvent(BackupEvent.Type.PROGRESS, ++count, estimatedCount));
        outputStream.write(preference);
      }

      stopwatch.split("prefs");

      count = exportKeyValues(outputStream, SignalStore.getKeysToIncludeInBackup(), count, estimatedCount, cancellationSignal);

      stopwatch.split("key_values");

      for (AvatarHelper.Avatar avatar : AvatarHelper.getAvatars(context)) {
        throwIfCanceled(cancellationSignal);
        if (avatar != null) {
          EventBus.getDefault().post(new BackupEvent(BackupEvent.Type.PROGRESS, ++count, estimatedCount));
          try (InputStream inputStream = avatar.getInputStream()) {
            outputStream.write(avatar.getFilename(), inputStream, avatar.getLength());
          }
        }
      }

      stopwatch.split("avatars");
      stopwatch.stop(TAG);

      outputStream.writeEnd();
    } finally {
      if (closeOutputStream) {
        outputStream.close();
      }
    }
    return new BackupEvent(BackupEvent.Type.FINISHED, ++count, estimatedCountOutside);
  }

  private static long calculateCount(@NonNull Context context, @NonNull SQLiteDatabase input, List<String> tables) {
    long count = DATABASE_VERSION_RECORD_COUNT + TABLE_RECORD_COUNT_MULTIPLIER * tables.size();

    for (String table : tables) {
      if (table.equals(MessageTable.TABLE_NAME)) {
        count += getCount(input, BackupCountQueries.mmsCount);
      } else if (table.equals(GroupReceiptTable.TABLE_NAME)) {
        count += getCount(input, BackupCountQueries.getGroupReceiptCount());
      } else if (table.equals(AttachmentTable.TABLE_NAME)) {
        count += getCount(input, BackupCountQueries.getAttachmentCount());
      } else if (table.equals(StickerTable.TABLE_NAME)) {
        count += getCount(input, "SELECT COUNT(*) FROM " + table);
      } else if (!TABLE_CONTENT_BLOCKLIST.contains(table)) {
        count += getCount(input, "SELECT COUNT(*) FROM " + table);
      }
    }

    count += IDENTITY_KEY_BACKUP_RECORD_COUNT;

    count += TextSecurePreferences.getPreferencesToSaveToBackupCount(context);

    KeyValueDataSet dataSet = KeyValueDatabase.getInstance(ApplicationDependencies.getApplication())
                                              .getDataSet();
    for (String key : SignalStore.getKeysToIncludeInBackup()) {
      if (dataSet.containsKey(key)) {
        count++;
      }
    }

    count += AvatarHelper.getAvatarCount(context);

    return count + FINAL_MESSAGE_COUNT;
  }

  private static long getCount(@NonNull SQLiteDatabase input, @NonNull String query) {
    try (Cursor cursor = input.rawQuery(query)) {
      return cursor.moveToFirst() ? cursor.getLong(0) : 0;
    }
  }

  private static void throwIfCanceled(@NonNull BackupCancellationSignal cancellationSignal) throws BackupCanceledException {
    if (cancellationSignal.isCanceled()) {
      throw new BackupCanceledException();
    }
  }

  private static List<String> exportSchema(@NonNull SQLiteDatabase input, @NonNull BackupFrameOutputStream outputStream)
      throws IOException
  {
    List<String> tablesInOrder = getTablesToExportInOrder(input);

    Map<String, String> createStatementsByTable = new HashMap<>();

    try (Cursor cursor = input.rawQuery("SELECT sql, name, type FROM sqlite_master WHERE type = 'table' AND sql NOT NULL", null)) {
      while (cursor != null && cursor.moveToNext()) {
        String sql  = cursor.getString(0);
        String name = cursor.getString(1);

        createStatementsByTable.put(name, sql);
      }
    }

    for (String table :  tablesInOrder) {
      String statement = createStatementsByTable.get(table);

      if (statement != null) {
        outputStream.write(BackupProtos.SqlStatement.newBuilder().setStatement(statement).build());
      } else {
        throw new IOException("Failed to find a create statement for table: " + table);
      }
    }

    try (Cursor cursor = input.rawQuery("SELECT sql, name, type FROM sqlite_master where type != 'table' AND sql NOT NULL", null)) {
      while (cursor != null && cursor.moveToNext()) {
        String sql  = cursor.getString(0);
        String name = cursor.getString(1);

        if (isTableAllowed(name)) {
          outputStream.write(BackupProtos.SqlStatement.newBuilder().setStatement(sql).build());
        }
      }
    }

    return tablesInOrder;
  }

  /**
   * Returns the list of tables we should export, in the order they should be exported in.
   * The order is chosen to ensure we won't violate any foreign key constraints when we import them.
   */
  private static List<String> getTablesToExportInOrder(@NonNull SQLiteDatabase input) {
    List<String> tables = SqlUtil.getAllTables(input)
                                 .stream()
                                 .filter(FullBackupExporter::isTableAllowed)
                                 .sorted()
                                 .collect(Collectors.toList());

    
    Map<String, Set<String>> dependsOn = new LinkedHashMap<>();
    for (String table : tables) {
      dependsOn.put(table, SqlUtil.getForeignKeyDependencies(input, table));
    }
    
    return computeTableOrder(dependsOn);
  }

  @VisibleForTesting
  static List<String> computeTableOrder(@NonNull Map<String, Set<String>> dependsOn) {
    List<String> rootNodes = dependsOn.keySet()
                                      .stream()
                                      .filter(table -> {
                                        boolean nothingDependsOnIt = dependsOn.values().stream().noneMatch(it -> it.contains(table));
                                        return nothingDependsOnIt;
                                      })
                                      .sorted()
                                      .collect(Collectors.toList());

    LinkedHashSet<String> outputOrder = new LinkedHashSet<>();

    for (String root : rootNodes) {
      postOrderTraversal(root, dependsOn, outputOrder);
    }

    return new ArrayList<>(outputOrder);
  }

  private static void postOrderTraversal(String current, Map<String, Set<String>> dependsOn, LinkedHashSet<String> outputOrder) {
    Set<String> dependencies = dependsOn.get(current);

    if (dependencies == null || dependencies.isEmpty()) {
      outputOrder.add(current);
      return;
    }

    for (String dependency : dependencies) {
      postOrderTraversal(dependency, dependsOn, outputOrder);
    }

    outputOrder.add(current);
  }

  private static boolean isTableAllowed(@Nullable String table) {
    if (table == null) {
      return true;
    }

    boolean isReservedTable       = table.startsWith("sqlite_");
    boolean isMmsFtsSecretTable   = !table.equals(SearchTable.FTS_TABLE_NAME) && table.startsWith(SearchTable.FTS_TABLE_NAME);
    boolean isEmojiFtsSecretTable = !table.equals(EmojiSearchTable.TABLE_NAME) && table.startsWith(EmojiSearchTable.TABLE_NAME);

    return !isReservedTable &&
           !isMmsFtsSecretTable &&
           !isEmojiFtsSecretTable;
  }

  private static int exportTable(@NonNull String table,
                                 @NonNull SQLiteDatabase input,
                                 @NonNull BackupFrameOutputStream outputStream,
                                 @Nullable Predicate<Cursor> predicate,
                                 @Nullable PostProcessor postProcess,
                                 int count,
                                 long estimatedCount,
                                 @NonNull BackupCancellationSignal cancellationSignal)
      throws IOException
  {
    String template = "INSERT INTO " + table + " VALUES ";

    try (Cursor cursor = input.rawQuery("SELECT * FROM " + table, null)) {
      while (cursor != null && cursor.moveToNext()) {
        throwIfCanceled(cancellationSignal);

        if (predicate == null || predicate.test(cursor)) {
          StringBuilder                     statement        = new StringBuilder(template);
          BackupProtos.SqlStatement.Builder statementBuilder = BackupProtos.SqlStatement.newBuilder();

          statement.append('(');

          for (int i = 0; i < cursor.getColumnCount(); i++) {
            statement.append('?');

            if (cursor.getType(i) == Cursor.FIELD_TYPE_STRING) {
              statementBuilder.addParameters(BackupProtos.SqlStatement.SqlParameter.newBuilder().setStringParamter(cursor.getString(i)));
            } else if (cursor.getType(i) == Cursor.FIELD_TYPE_FLOAT) {
              statementBuilder.addParameters(BackupProtos.SqlStatement.SqlParameter.newBuilder().setDoubleParameter(cursor.getDouble(i)));
            } else if (cursor.getType(i) == Cursor.FIELD_TYPE_INTEGER) {
              statementBuilder.addParameters(BackupProtos.SqlStatement.SqlParameter.newBuilder().setIntegerParameter(cursor.getLong(i)));
            } else if (cursor.getType(i) == Cursor.FIELD_TYPE_BLOB) {
              statementBuilder.addParameters(BackupProtos.SqlStatement.SqlParameter.newBuilder().setBlobParameter(ByteString.copyFrom(cursor.getBlob(i))));
            } else if (cursor.getType(i) == Cursor.FIELD_TYPE_NULL) {
              statementBuilder.addParameters(BackupProtos.SqlStatement.SqlParameter.newBuilder().setNullparameter(true));
            } else {
              throw new AssertionError("unknown type?" + cursor.getType(i));
            }

            if (i < cursor.getColumnCount() - 1) {
              statement.append(',');
            }
          }

          statement.append(')');

          EventBus.getDefault().post(new BackupEvent(BackupEvent.Type.PROGRESS, ++count, estimatedCount));
          outputStream.write(statementBuilder.setStatement(statement.toString()).build());

          if (postProcess != null) {
            count = postProcess.postProcess(cursor, count);
          }
        }
      }
    }

    return count;
  }

  private static int exportAttachment(@NonNull AttachmentSecret attachmentSecret,
                                      @NonNull Cursor cursor,
                                      @NonNull BackupFrameOutputStream outputStream,
                                      int count,
                                      long estimatedCount)
      throws IOException
  {
    long rowId    = cursor.getLong(cursor.getColumnIndexOrThrow(AttachmentTable.ROW_ID));
    long uniqueId = cursor.getLong(cursor.getColumnIndexOrThrow(AttachmentTable.UNIQUE_ID));
    long size     = cursor.getLong(cursor.getColumnIndexOrThrow(AttachmentTable.SIZE));

    String data   = cursor.getString(cursor.getColumnIndexOrThrow(AttachmentTable.DATA));
    byte[] random = cursor.getBlob(cursor.getColumnIndexOrThrow(AttachmentTable.DATA_RANDOM));

    if (!TextUtils.isEmpty(data)) {
      long fileLength = new File(data).length();
      long dbLength   = size;

      if (size <= 0 || fileLength != dbLength) {
        size = calculateVeryOldStreamLength(attachmentSecret, random, data);
        Log.w(TAG, "Needed size calculation! Manual: " + size + " File: " + fileLength + "  DB: " + dbLength + " ID: " + new AttachmentId(rowId, uniqueId));
      }
    }

    EventBus.getDefault().post(new BackupEvent(BackupEvent.Type.PROGRESS, ++count, estimatedCount));
    if (!TextUtils.isEmpty(data) && size > 0) {
      try (InputStream inputStream = openAttachmentStream(attachmentSecret, random, data)) {
        outputStream.write(new AttachmentId(rowId, uniqueId), inputStream, size);
      } catch (FileNotFoundException e) {
        Log.w(TAG, "Missing attachment", e);
      }
    }

    return count;
  }

  private static int exportSticker(@NonNull AttachmentSecret attachmentSecret,
                                   @NonNull Cursor cursor,
                                   @NonNull BackupFrameOutputStream outputStream,
                                   int count,
                                   long estimatedCount)
      throws IOException
  {
    long rowId = cursor.getLong(cursor.getColumnIndexOrThrow(StickerTable._ID));
    long size  = cursor.getLong(cursor.getColumnIndexOrThrow(StickerTable.FILE_LENGTH));

    String data   = cursor.getString(cursor.getColumnIndexOrThrow(StickerTable.FILE_PATH));
    byte[] random = cursor.getBlob(cursor.getColumnIndexOrThrow(StickerTable.FILE_RANDOM));

    if (!TextUtils.isEmpty(data) && size > 0) {
      EventBus.getDefault().post(new BackupEvent(BackupEvent.Type.PROGRESS, ++count, estimatedCount));
      try (InputStream inputStream = ModernDecryptingPartInputStream.createFor(attachmentSecret, random, new File(data), 0)) {
        outputStream.writeSticker(rowId, inputStream, size);
      } catch (FileNotFoundException e) {
        Log.w(TAG, "Missing sticker", e);
      }
    }

    return count;
  }

  private static long calculateVeryOldStreamLength(@NonNull AttachmentSecret attachmentSecret, @Nullable byte[] random, @NonNull String data) {
    long result = 0;

    try (InputStream inputStream = openAttachmentStream(attachmentSecret, random, data)) {
      int    read;
      byte[] buffer = new byte[8192];

      while ((read = inputStream.read(buffer, 0, buffer.length)) != -1) {
        result += read;
      }
    } catch (FileNotFoundException e) {
      Log.w(TAG, "Missing attachment for size calculation", e);
      return 0;
    } catch (IOException e) {
      Log.w(TAG, "Failed to determine stream length", e);
      return 0;
    }

    return result;
  }

  private static InputStream openAttachmentStream(@NonNull AttachmentSecret attachmentSecret, @Nullable byte[] random, @NonNull String data) throws IOException {
    if (random != null && random.length == 32) {
      return ModernDecryptingPartInputStream.createFor(attachmentSecret, random, new File(data), 0);
    } else {
      return ClassicDecryptingPartInputStream.createFor(attachmentSecret, new File(data));
    }
  }

  private static int exportKeyValues(@NonNull BackupFrameOutputStream outputStream,
                                     @NonNull List<String> keysToIncludeInBackup,
                                     int count,
                                     long estimatedCount,
                                     BackupCancellationSignal cancellationSignal) throws IOException
  {
    KeyValueDataSet dataSet = KeyValueDatabase.getInstance(ApplicationDependencies.getApplication())
                                              .getDataSet();

    for (String key : keysToIncludeInBackup) {
      throwIfCanceled(cancellationSignal);
      if (!dataSet.containsKey(key)) {
        continue;
      }
      BackupProtos.KeyValue.Builder builder = BackupProtos.KeyValue.newBuilder()
                                                                   .setKey(key);

      Class<?> type = dataSet.getType(key);
      if (type == byte[].class) {
        byte[] data = dataSet.getBlob(key, null);
        if (data != null) {
          builder.setBlobValue(ByteString.copyFrom(dataSet.getBlob(key, null)));
        } else {
          Log.w(TAG, "Skipping storing null blob for key: " + key);
        }
      } else if (type == Boolean.class) {
        builder.setBooleanValue(dataSet.getBoolean(key, false));
      } else if (type == Float.class) {
        builder.setFloatValue(dataSet.getFloat(key, 0));
      } else if (type == Integer.class) {
        builder.setIntegerValue(dataSet.getInteger(key, 0));
      } else if (type == Long.class) {
        builder.setLongValue(dataSet.getLong(key, 0));
      } else if (type == String.class) {
        String data = dataSet.getString(key, null);
        if (data != null) {
          builder.setStringValue(dataSet.getString(key, null));
        } else {
          Log.w(TAG, "Skipping storing null string for key: " + key);
        }
      } else {
        throw new AssertionError("Unknown type: " + type);
      }

      EventBus.getDefault().post(new BackupEvent(BackupEvent.Type.PROGRESS, ++count, estimatedCount));
      outputStream.write(builder.build());
    }

    return count;
  }

  private static boolean isNonExpiringMmsMessage(@NonNull Cursor cursor) {
    return cursor.getLong(cursor.getColumnIndexOrThrow(MessageTable.EXPIRES_IN)) <= 0 &&
           cursor.getLong(cursor.getColumnIndexOrThrow(MessageTable.VIEW_ONCE)) <= 0;
  }

  private static boolean isNonExpiringSmsMessage(@NonNull Cursor cursor) {
    return cursor.getLong(cursor.getColumnIndexOrThrow(MessageTable.EXPIRES_IN)) <= 0;
  }

  private static boolean isForNonExpiringMessage(@NonNull SQLiteDatabase db, @NonNull MessageId messageId) {
    return isForNonExpiringMmsMessage(db, messageId.getId());
  }

  private static boolean isForNonExpiringMmsMessage(@NonNull SQLiteDatabase db, long mmsId) {
    String[] columns = new String[] { MessageTable.RECIPIENT_ID, MessageTable.EXPIRES_IN, MessageTable.VIEW_ONCE };
    String   where   = MessageTable.ID + " = ?";
    String[] args    = new String[] { String.valueOf(mmsId) };

    try (Cursor mmsCursor = db.query(MessageTable.TABLE_NAME, columns, where, args, null, null, null)) {
      if (mmsCursor != null && mmsCursor.moveToFirst()) {
        return isNonExpiringMmsMessage(mmsCursor);
      }
    }

    return false;
  }

  private static class BackupFrameOutputStream extends BackupStream {

    private final OutputStream outputStream;
    private final Cipher       cipher;
    private final Mac          mac;

    private final byte[] cipherKey;
    private final byte[] iv;
    private       int    counter;

    private BackupFrameOutputStream(@NonNull OutputStream output, @NonNull String passphrase) throws IOException {
      try {
        byte[]   salt    = Util.getSecretBytes(32);
        byte[]   key     = getBackupKey(passphrase, salt);
        byte[]   derived = HKDF.deriveSecrets(key, "Backup Export".getBytes(), 64);
        byte[][] split   = ByteUtil.split(derived, 32, 32);

        this.cipherKey = split[0];
        byte[] macKey  = split[1];

        this.cipher       = Cipher.getInstance("AES/CTR/NoPadding");
        this.mac          = Mac.getInstance("HmacSHA256");
        this.outputStream = output;
        this.iv           = Util.getSecretBytes(16);
        this.counter      = Conversions.byteArrayToInt(iv);

        mac.init(new SecretKeySpec(macKey, "HmacSHA256"));

        byte[] header = BackupProtos.BackupFrame.newBuilder().setHeader(BackupProtos.Header.newBuilder()
                                                                                           .setIv(ByteString.copyFrom(iv))
                                                                                           .setSalt(ByteString.copyFrom(salt)))
                                                .build().toByteArray();

        outputStream.write(Conversions.intToByteArray(header.length));
        outputStream.write(header);
      } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException e) {
        throw new AssertionError(e);
      }
    }

    public void write(BackupProtos.SharedPreference preference) throws IOException {
      write(outputStream, BackupProtos.BackupFrame.newBuilder().setPreference(preference).build());
    }

    public void write(BackupProtos.KeyValue keyValue) throws IOException {
      write(outputStream, BackupProtos.BackupFrame.newBuilder().setKeyValue(keyValue).build());
    }

    public void write(BackupProtos.SqlStatement statement) throws IOException {
      write(outputStream, BackupProtos.BackupFrame.newBuilder().setStatement(statement).build());
    }

    public void write(@NonNull String avatarName, @NonNull InputStream in, long size) throws IOException {
      try {
        write(outputStream, BackupProtos.BackupFrame.newBuilder()
                                                    .setAvatar(BackupProtos.Avatar.newBuilder()
                                                                                  .setRecipientId(avatarName)
                                                                                  .setLength(Util.toIntExact(size))
                                                                                  .build())
                                                    .build());
      } catch (ArithmeticException e) {
        Log.w(TAG, "Unable to write avatar to backup", e);
        throw new InvalidBackupStreamException();
      }

      if (writeStream(in) != size) {
        throw new IOException("Size mismatch!");
      }
    }

    public void write(@NonNull AttachmentId attachmentId, @NonNull InputStream in, long size) throws IOException {
      try {
        write(outputStream, BackupProtos.BackupFrame.newBuilder()
                                                    .setAttachment(BackupProtos.Attachment.newBuilder()
                                                                                          .setRowId(attachmentId.getRowId())
                                                                                          .setAttachmentId(attachmentId.getUniqueId())
                                                                                          .setLength(Util.toIntExact(size))
                                                                                          .build())
                                                    .build());
      } catch (ArithmeticException e) {
        Log.w(TAG, "Unable to write " + attachmentId + " to backup", e);
        throw new InvalidBackupStreamException();
      }

      if (writeStream(in) != size) {
        throw new IOException("Size mismatch!");
      }
    }

    public void writeSticker(long rowId, @NonNull InputStream in, long size) throws IOException {
      try {
        write(outputStream, BackupProtos.BackupFrame.newBuilder()
                                                    .setSticker(BackupProtos.Sticker.newBuilder()
                                                                                    .setRowId(rowId)
                                                                                    .setLength(Util.toIntExact(size))
                                                                                    .build())
                                                    .build());
      } catch (ArithmeticException e) {
        Log.w(TAG, "Unable to write sticker to backup", e);
        throw new InvalidBackupStreamException();
      }

      if (writeStream(in) != size) {
        throw new IOException("Size mismatch!");
      }
    }

    void writeDatabaseVersion(int version) throws IOException {
      write(outputStream, BackupProtos.BackupFrame.newBuilder()
                                                  .setVersion(BackupProtos.DatabaseVersion.newBuilder().setVersion(version))
                                                  .build());
    }

    void writeEnd() throws IOException {
      write(outputStream, BackupProtos.BackupFrame.newBuilder().setEnd(true).build());
    }

    /**
     * @return The amount of data written from the provided InputStream.
     */
    private long writeStream(@NonNull InputStream inputStream) throws IOException {
      try {
        Conversions.intToByteArray(iv, 0, counter++);
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cipherKey, "AES"), new IvParameterSpec(iv));
        mac.update(iv);

        byte[] buffer = new byte[8192];
        long   total  = 0;

        int read;

        while ((read = inputStream.read(buffer)) != -1) {
          byte[] ciphertext = cipher.update(buffer, 0, read);

          if (ciphertext != null) {
            outputStream.write(ciphertext);
            mac.update(ciphertext);
          }

          total += read;
        }

        byte[] remainder = cipher.doFinal();
        outputStream.write(remainder);
        mac.update(remainder);

        byte[] attachmentDigest = mac.doFinal();
        outputStream.write(attachmentDigest, 0, 10);

        return total;
      } catch (InvalidKeyException | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
        throw new AssertionError(e);
      }
    }

    private void write(@NonNull OutputStream out, @NonNull BackupProtos.BackupFrame frame) throws IOException {
      try {
        Conversions.intToByteArray(iv, 0, counter++);
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cipherKey, "AES"), new IvParameterSpec(iv));

        byte[] frameCiphertext = cipher.doFinal(frame.toByteArray());
        byte[] frameMac        = mac.doFinal(frameCiphertext);
        byte[] length          = Conversions.intToByteArray(frameCiphertext.length + 10);

        out.write(length);
        out.write(frameCiphertext);
        out.write(frameMac, 0, 10);
      } catch (InvalidKeyException | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
        throw new AssertionError(e);
      }
    }


    public void close() throws IOException {
      outputStream.flush();
      outputStream.close();
    }
  }

  public interface PostProcessor {
    int postProcess(@NonNull Cursor cursor, int count) throws IOException;
  }

  public interface BackupCancellationSignal {
    boolean isCanceled();
  }

  public static final class BackupCanceledException extends IOException {}

  public static final class InvalidBackupStreamException extends IOException {}
}
