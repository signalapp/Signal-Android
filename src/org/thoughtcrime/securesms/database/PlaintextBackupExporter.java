package org.thoughtcrime.securesms.database;


import android.content.Context;

import org.thoughtcrime.securesms.database.model.SmsMessageRecord;
import org.thoughtcrime.securesms.util.StorageUtil;

import java.io.File;
import java.io.IOException;

public class PlaintextBackupExporter {

  private static final String FILENAME = "SignalPlaintextBackup.xml";

  public static void exportPlaintextToSd(Context context)
      throws NoExternalStorageException, IOException
  {
    exportPlaintext(context);
  }

  public static File getPlaintextExportFile() throws NoExternalStorageException {
    return new File(StorageUtil.getBackupDir(), FILENAME);
  }

  private static void exportPlaintext(Context context)
      throws NoExternalStorageException, IOException
  {
    SmsDatabase      database = DatabaseFactory.getSmsDatabase(context);
    int              count    = database.getMessageCount();
    XmlBackup.Writer writer   = new XmlBackup.Writer(getPlaintextExportFile().getAbsolutePath(), count);


    SmsMessageRecord record;

    SmsDatabase.Reader reader    = null;
    int                skip      = 0;
    int                ROW_LIMIT = 500;

    do {
      if (reader != null)
        reader.close();

      reader = database.readerFor(database.getMessages(skip, ROW_LIMIT));

      while ((record = reader.getNext()) != null) {
        XmlBackup.XmlBackupItem item =
            new XmlBackup.XmlBackupItem(0, record.getIndividualRecipient().getAddress().serialize(),
                                        record.getIndividualRecipient().getName(),
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
