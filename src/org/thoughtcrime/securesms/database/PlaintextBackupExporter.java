package org.thoughtcrime.securesms.database;


import android.content.Context;
import android.os.Environment;
import android.util.Log;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;

import java.io.File;
import java.io.IOException;

public class PlaintextBackupExporter extends AbstractBackup {

  public static void exportPlaintextToSd(Context context, MasterSecret masterSecret)
      throws NoExternalStorageException, IOException
  {
    Log.w("PlaintextBackupExporter", "Exporting plaintext...");
    verifyCanWrite();
    setup();
    exportPlaintext(context, masterSecret);
  }

  public static void setup() {
    File backupDirectory = getDirectoryPath();
    if (!backupDirectory.exists()) {
      backupDirectory.mkdirs();
    }
  }

  private static void exportPlaintext(Context context, MasterSecret masterSecret)
      throws IOException
  {
    String filePath         = getFilePath("SignalPlaintextBackup.xml").getAbsolutePath();
    int    count            = DatabaseFactory.getSmsDatabase(context).getMessageCount();
    XmlBackup.Writer writer = new XmlBackup.Writer(filePath, count);


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
