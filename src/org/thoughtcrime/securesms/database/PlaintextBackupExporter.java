package org.thoughtcrime.securesms.database;


import android.content.Context;
import android.os.Environment;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;

import java.io.File;
import java.io.IOException;

public class PlaintextBackupExporter {

  private static final String FILENAME = "TextSecurePlaintextBackup.xml";
  private static final String FOLDERNAME = "TextSecure";
  private static final String BACKUPFOLDERNAME = "Backup";

  public static void exportPlaintextToSd(Context context, MasterSecret masterSecret)
      throws NoExternalStorageException, IOException
  {
    verifyExternalStorageForPlaintextExport();
    moveOldPlaintextExportToDirectory();
    exportPlaintext(context, masterSecret);
  }

  private static void verifyExternalStorageForPlaintextExport() throws NoExternalStorageException {
    if (!Environment.getExternalStorageDirectory().canWrite())
      throw new NoExternalStorageException();
  }

  private static String getPlaintextExportDirectoryPath() {
    File sdDirectory = Environment.getExternalStorageDirectory();
    return getOldPlaintextExportDirectoryPath() + FOLDERNAME + File.separator + BACKUPFOLDERNAME + File.separator;
  }

  private static String getOldPlaintextExportDirectoryPath() {
    File sdDirectory = Environment.getExternalStorageDirectory();
    return sdDirectory.getAbsolutePath() + File.separator;
  }

  private static void moveOldPlaintextExportToDirectory(){
    File oldBackup = new File(getOldPlaintextExportDirectoryPath() + FILENAME);
    File newBackup = new File(getPlaintextExportDirectoryPath() + FILENAME);
    File newDirectory = new File(getPlaintextExportDirectoryPath());

    if (! newDirectory.isDirectory())
      newDirectory.mkdirs();

    if (oldBackup.isFile()) {
      if (! newBackup.isFile())
        oldBackup.renameTo(newBackup);
    }
  }

  private static void exportPlaintext(Context context, MasterSecret masterSecret)
    throws IOException
  {
    int count               = DatabaseFactory.getSmsDatabase(context).getMessageCount();
    XmlBackup.Writer writer = new XmlBackup.Writer(getPlaintextExportDirectoryPath()+ FILENAME, count);

    SmsMessageRecord record;
    EncryptingSmsDatabase.Reader reader = null;
    int skip                            = 0;
    int ROW_LIMIT                       = 500;

    do {
      if (reader != null)
        reader.close();

      reader = DatabaseFactory.getEncryptingSmsDatabase(context).getMessages(masterSecret, skip, ROW_LIMIT);

      while ((record = reader.getNext()) != null) {
        XmlBackup.XmlBackupItem item =
            new XmlBackup.XmlBackupItem(0, record.getIndividualRecipient().getNumber(),
                                        record.getDateReceived(),
                                        MmsSmsColumns.Types.translateToSystemBaseType(record.getType()),
                                        null, record.getDisplayBody().toString(), null,
                                        1, record.getDeliveryStatus());

        writer.writeItem(item);
      }

      skip += ROW_LIMIT;
    } while (reader.getCount() > 0);

    writer.close();
  }
}
