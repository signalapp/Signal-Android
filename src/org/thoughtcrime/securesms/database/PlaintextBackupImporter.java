package org.thoughtcrime.securesms.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.os.Environment;
import android.util.Log;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.naming.NoNameCoder;
import com.thoughtworks.xstream.io.xml.Xpp3DomDriver;
import com.thoughtworks.xstream.io.xml.XppReader;

import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.thoughtcrime.securesms.crypto.MasterSecretUtil;
import org.thoughtcrime.securesms.database.MmsSmsColumns.Types;
import org.thoughtcrime.securesms.database.XmlBackup.Identity;
import org.thoughtcrime.securesms.database.XmlBackup.Sms;
import org.whispersystems.textsecure.crypto.InvalidKeyException;
import org.whispersystems.textsecure.crypto.MasterCipher;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.RecipientFormattingException;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.whispersystems.textsecure.util.Hex;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;


public class PlaintextBackupImporter {

  public static void importPlaintextFromSd(Context context, MasterSecret masterSecret)
      throws NoExternalStorageException, IOException
  {
    Log.w("PlaintextBackupImporter", "Importing plaintext...");
    verifyExternalStorageForPlaintextImport();
    FileInputStream reader = new FileInputStream(getPlaintextExportDirectoryPath());
    importPlaintext(context, masterSecret, reader);
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

  public static void importPlaintext(Context context, MasterSecret masterSecret, InputStream input)
      throws IOException
  {
    Log.w("PlaintextBackupImporter", "importPlaintext()");
    SmsDatabase              db          = DatabaseFactory.getSmsDatabase(context);
    SQLiteDatabase           transaction = db.beginTransaction();
    Xpp3DomDriver            driver      = new Xpp3DomDriver(new NoNameCoder());
    XStream                  xstream     = new XStream(driver);
    HierarchicalStreamReader reader      = driver.createReader(new BufferedReader(new InputStreamReader(input)));

    try {
      ThreadDatabase threads         = DatabaseFactory.getThreadDatabase(context);
      MasterCipher   masterCipher    = new MasterCipher(masterSecret);
      Set<Long>      modifiedThreads = new HashSet<Long>();

      xstream.autodetectAnnotations(true);
      xstream.alias("sms", XmlBackup.Sms.class);
      xstream.alias("identity", XmlBackup.Identity.class);

      while (reader.hasMoreChildren()) {
        reader.moveDown();
        Object child = xstream.unmarshal(reader);
        if (child instanceof Sms) {
          Log.w("Plaintext", "got an sms!");
          Sms sms = (Sms) child;
          try {
            Recipients      recipients = RecipientFactory.getRecipientsFromString(context, sms.address, false);
            long            threadId   = threads.getThreadIdFor(recipients);
            SQLiteStatement statement  = db.createInsertStatement(transaction);

            if (sms.address == null || sms.address.equals("null"))
              continue;

            addStringToStatement(statement, 1, sms.address);
            addNullToStatement(statement, 2);
            addLongToStatement(statement, 3, sms.date);
            addLongToStatement(statement, 4, sms.date);
            addLongToStatement(statement, 5, sms.protocol);
            addLongToStatement(statement, 6, sms.read);
            addLongToStatement(statement, 7, sms.status);
            addTranslatedTypeToStatement(statement, 8, sms.type, sms.ts_secure > 0, sms.ts_push > 0);
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
        } else if (child instanceof Identity) {
          try {
            Identity identity = (Identity) child;
            Log.w("PlaintextBackupImporter", "identity key received: " + Hex.toStringCondensed(identity.public_key));

            IdentityKeyUtil.setIdentityKeys(context, masterSecret, identity.toKeyPair());
          } catch (InvalidKeyException ike) {
            Log.w("PlaintextBackupImporter", "invalid identity key was in the import file");
          }
        } else {
          Log.w("PlaintextBackupImporter", "unknown tag included in backup...");
        }
        reader.moveUp();
      }

      for (long threadId : modifiedThreads) {
        threads.update(threadId);
      }

      Log.w("PlaintextBackupImporter", "Exited loop");
    } finally {
      reader.close();
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

  private static void addTranslatedTypeToStatement(SQLiteStatement statement, int index, int type, boolean secure, boolean push) {
    long flags = SmsDatabase.Types.translateFromSystemBaseType(type) | SmsDatabase.Types.ENCRYPTION_SYMMETRIC_BIT;
    if (secure) flags |= Types.SECURE_MESSAGE_BIT;
    if (push)   flags |= Types.PUSH_MESSAGE_BIT;
    statement.bindLong(index, flags);
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
