package org.thoughtcrime.securesms.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.os.Environment;
import android.util.Log;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.naming.NoNameCoder;
import com.thoughtworks.xstream.io.xml.Xpp3DomDriver;

import org.whispersystems.textsecure.crypto.MasterCipher;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.RecipientFormattingException;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;


public class PlaintextBackupImporter {

  public static void importPlaintextFromSd(Context context, MasterSecret masterSecret)
      throws NoExternalStorageException, IOException
  {
    Log.w("PlaintextBackupImporter", "Importing plaintext...");
    verifyExternalStorageForPlaintextImport();
    importPlaintext(context, masterSecret);
  }

  private static void verifyExternalStorageForPlaintextImport() throws NoExternalStorageException {
    if (!Environment.getExternalStorageDirectory().canRead() ||
        !(new File(getPlaintextExportDirectoryPath()).exists()))
      throw new NoExternalStorageException();
  }

  private static String getPlaintextExportDirectoryPath() {
    File sdDirectory = Environment.getExternalStorageDirectory();
    return sdDirectory.getAbsolutePath() + File.separator + "TextSecurePlaintextBackup.xml";
  }

  private static void importPlaintext(Context context, MasterSecret masterSecret)
      throws IOException
  {
    Log.w("PlaintextBackupImporter", "importPlaintext()");
    SmsDatabase    db          = DatabaseFactory.getSmsDatabase(context);
    SQLiteDatabase transaction = db.beginTransaction();

    try {
      ThreadDatabase threads = DatabaseFactory.getThreadDatabase(context);
      MasterCipher masterCipher = new MasterCipher(masterSecret);
      Set<Long> modifiedThreads = new HashSet<Long>();

      XStream xstream = new XStream(new Xpp3DomDriver(new NoNameCoder()));
      xstream.autodetectAnnotations(true);
      xstream.alias("smses", XmlBackup.Smses.class);
      xstream.alias("sms", XmlBackup.Sms.class);
      Log.w("PlaintextBackupImporter", getPlaintextExportDirectoryPath());
      FileReader reader = new FileReader(getPlaintextExportDirectoryPath());
      XmlBackup.Smses smses = (XmlBackup.Smses)xstream.fromXML(reader);
      for (XmlBackup.Sms sms : smses.smses) {
        try {
          Recipients recipients = RecipientFactory.getRecipientsFromString(context, sms.address, false);
          long threadId = threads.getThreadIdFor(recipients);
          SQLiteStatement statement = db.createInsertStatement(transaction);

          if (sms.address == null || sms.address.equals("null"))
            continue;

          addStringToStatement(statement, 1, sms.address);
          addNullToStatement(statement, 2);
          addLongToStatement(statement, 3, sms.date);
          addLongToStatement(statement, 4, sms.date);
          addLongToStatement(statement, 5, sms.protocol);
          addLongToStatement(statement, 6, sms.read);
          addLongToStatement(statement, 7, sms.status);
          addTranslatedTypeToStatement(statement, 8, sms.type);
          addNullToStatement(statement, 9);
          addStringToStatement(statement, 10, sms.subject);
          addEncryptedStingToStatement(masterCipher, statement, 11, sms.body);
          addStringToStatement(statement, 12, sms.service_center);
          addLongToStatement(statement, 13, threadId);
          modifiedThreads.add(threadId);
          statement.execute();
        } catch (RecipientFormattingException rfe) {
          Log.w("PlaintextBackupImporter", rfe);
        }
      }

      for (long threadId : modifiedThreads) {
        threads.update(threadId);
      }

      Log.w("PlaintextBackupImporter", "Exited loop");
    } finally {
      db.endTransaction(transaction);
    }
  }

  private static void addEncryptedStingToStatement(MasterCipher masterCipher, SQLiteStatement statement, int index, String value) {
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

}
