package org.thoughtcrime.securesms.database;


import android.content.Context;
import android.os.Environment;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class PlaintextBackupExporter {

  private static final String TIMESTAMP = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date(System.currentTimeMillis()));
  private static final String FILENAME = "SignalPlaintextBackup_" + TIMESTAMP + ".xml";

  public static void exportPlaintextToSd(Context context, MasterSecret masterSecret)
      throws NoExternalStorageException, IOException
  {
    verifyExternalStorageForPlaintextExport();
    exportPlaintext(context, masterSecret);
  }

  private static void verifyExternalStorageForPlaintextExport() throws NoExternalStorageException {
    if (!Environment.getExternalStorageDirectory().canWrite())
      throw new NoExternalStorageException();
  }

  public static File getPlaintextExportFile() {
    return new File(Environment.getExternalStorageDirectory(), FILENAME);
  }

  private static void exportPlaintext(Context context, MasterSecret masterSecret)
      throws IOException
  {
    int count               = DatabaseFactory.getSmsDatabase(context).getMessageCount();
    XmlBackup.Writer writer = new XmlBackup.Writer(getPlaintextExportFile().getAbsolutePath(), count);


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
