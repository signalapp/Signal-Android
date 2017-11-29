package org.thoughtcrime.securesms.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.os.Environment;
import android.util.Log;

import org.thoughtcrime.securesms.crypto.MasterCipher;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.StorageUtil;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class PlaintextBackupImporter {
  private static final String TAG = PlaintextBackupImporter.class.getSimpleName();

  public static void importPlaintextFromSd(Context context, MasterSecret masterSecret)
      throws NoExternalStorageException, IOException
  {
    Log.w(TAG, "importPlaintext()");
    SmsDatabase    db          = DatabaseFactory.getSmsDatabase(context);
    SQLiteDatabase transaction = db.beginTransaction();

    try {
      ThreadDatabase threads         = DatabaseFactory.getThreadDatabase(context);
      String         filePath        = getPlaintextBackupFile().getAbsolutePath();
      Log.d(TAG, "Importing from " + filePath);
      XmlBackup      backup          = new XmlBackup(filePath);
      MasterCipher   masterCipher    = new MasterCipher(masterSecret);
      Set<Long>      modifiedThreads = new HashSet<>();
      XmlBackup.XmlBackupItem item;

      while ((item = backup.getNext()) != null) {
        Recipient       recipient  = Recipient.from(context, Address.fromExternal(context, item.getAddress()), false);
        long            threadId   = threads.getThreadIdFor(recipient);
        SQLiteStatement statement  = db.createInsertStatement(transaction);

        if (item.getAddress() == null || item.getAddress().equals("null"))
          continue;

        if (!isAppropriateTypeForImport(item.getType()))
          continue;

        addStringToStatement(statement, 1, item.getAddress());
        addNullToStatement(statement, 2);
        addLongToStatement(statement, 3, item.getDate());
        addLongToStatement(statement, 4, item.getDate());
        addLongToStatement(statement, 5, item.getProtocol());
        addLongToStatement(statement, 6, item.getRead());
        addLongToStatement(statement, 7, item.getStatus());
        addTranslatedTypeToStatement(statement, 8, item.getType());
        addNullToStatement(statement, 9);
        addStringToStatement(statement, 10, item.getSubject());
        addEncryptedStringToStatement(masterCipher, statement, 11, item.getBody());
        addStringToStatement(statement, 12, item.getServiceCenter());
        addLongToStatement(statement, 13, threadId);
        modifiedThreads.add(threadId);
        statement.execute();
      }

      for (long threadId : modifiedThreads) {
        threads.update(threadId, true);
      }

      Log.w(TAG, "Exited loop");
    } catch (XmlPullParserException e) {
      Log.w(TAG, e);
      throw new IOException("XML Parsing error!");
    } finally {
      db.endTransaction(transaction);
    }
  }

  public static File getPlaintextBackupFile() throws NoExternalStorageException, FileNotFoundException {
    File[] backupFiles = StorageUtil.getBackupDir().listFiles(new FilenameFilter() {
      @Override
      public boolean accept(File dir, String filename) {
        return filename.startsWith("SignalPlaintextBackup-") && filename.endsWith(".xml");
      }
    });
    if (backupFiles.length >= 1) {
      Arrays.sort(backupFiles);
      return backupFiles[backupFiles.length - 1];
    }

    File[] historicalBackupFiles = {
      new File(Environment.getExternalStorageDirectory(), "SignalPlaintextBackup.xml"),
      new File(Environment.getExternalStorageDirectory(), "TextSecurePlaintextBackup.xml"),
    };

    for (File possibleBackupFile: historicalBackupFiles) {
      if (possibleBackupFile.exists()) {
        return possibleBackupFile;
      }
    }

    throw new FileNotFoundException();
  }

  private static void addEncryptedStringToStatement(MasterCipher masterCipher, SQLiteStatement statement, int index, String value) {
    if (value == null || value.equals("null")) {
      statement.bindNull(index);
    } else {
      statement.bindString(index, masterCipher.encryptBody(value));
    }
  }

  private static void addTranslatedTypeToStatement(SQLiteStatement statement, int index, int type) {
    statement.bindLong(index, SmsDatabase.Types.translateFromSystemBaseType(type) | SmsDatabase.Types.ENCRYPTION_SYMMETRIC_BIT);
  }

  private static void addStringToStatement(SQLiteStatement statement, int index, String value) {
    if (value == null || value.equals("null")) statement.bindNull(index);
    else                                       statement.bindString(index, value);
  }

  private static void addNullToStatement(SQLiteStatement statement, int index) {
    statement.bindNull(index);
  }

  private static void addLongToStatement(SQLiteStatement statement, int index, long value) {
    statement.bindLong(index, value);
  }

  private static boolean isAppropriateTypeForImport(long theirType) {
    long ourType = SmsDatabase.Types.translateFromSystemBaseType(theirType);

    return ourType == MmsSmsColumns.Types.BASE_INBOX_TYPE ||
           ourType == MmsSmsColumns.Types.BASE_SENT_TYPE ||
           ourType == MmsSmsColumns.Types.BASE_SENT_FAILED_TYPE;
  }
}
