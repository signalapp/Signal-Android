package org.thoughtcrime.securesms.database;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteStatement;

import org.thoughtcrime.securesms.recipients.Recipient;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class PlaintextBackupImporter {

  public static void importPlaintextFromSd(Context context)
      throws NoExternalStorageException, IOException
  {
    Log.w("PlaintextBackupImporter", "importPlaintext()");
    SmsDatabase    db          = DatabaseFactory.getSmsDatabase(context);
    SQLiteDatabase transaction = db.beginTransaction();

    try {
      ThreadDatabase threads         = DatabaseFactory.getThreadDatabase(context);
      XmlBackup      backup          = new XmlBackup(getPlaintextExportFile().getAbsolutePath());
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
        addStringToStatement(statement, 11, item.getBody());
        addStringToStatement(statement, 12, item.getServiceCenter());
        addLongToStatement(statement, 13, threadId);
        modifiedThreads.add(threadId);
        statement.execute();
        statement.close();
      }

      for (long threadId : modifiedThreads) {
        threads.update(threadId, true);
      }

      Log.w("PlaintextBackupImporter", "Exited loop");
    } catch (XmlPullParserException e) {
      Log.w("PlaintextBackupImporter", e);
      throw new IOException("XML Parsing error!");
    } finally {
      db.endTransaction(transaction);
    }
  }

  private static File getPlaintextExportFile() throws NoExternalStorageException {
    File backup    = PlaintextBackupExporter.getPlaintextExportFile();
    File oldBackup = new File(Environment.getExternalStorageDirectory(), "TextSecurePlaintextBackup.xml");

    return !backup.exists() && oldBackup.exists() ? oldBackup : backup;
  }

  @SuppressWarnings("SameParameterValue")
  private static void addTranslatedTypeToStatement(SQLiteStatement statement, int index, int type) {
    statement.bindLong(index, SmsDatabase.Types.translateFromSystemBaseType(type));
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
