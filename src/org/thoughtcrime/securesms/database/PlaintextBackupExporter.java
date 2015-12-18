package org.thoughtcrime.securesms.database;


import android.content.Context;
import android.os.Environment;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;
import org.thoughtcrime.securesms.recipients.Recipients;

import java.io.File;
import java.io.IOException;

public class PlaintextBackupExporter {

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

  private static String getPlaintextExportDirectoryPath() {
    File sdDirectory = Environment.getExternalStorageDirectory();
    return sdDirectory.getAbsolutePath() + File.separator + "TextSecurePlaintextBackup.xml";
  }

  private static void exportPlaintext(Context context, MasterSecret masterSecret)
      throws IOException
  {
    int count               = DatabaseFactory.getSmsDatabase(context).getMessageCount();
    ThreadDatabase threads  = DatabaseFactory.getThreadDatabase(context);
    XmlBackup.Writer writer = new XmlBackup.Writer(getPlaintextExportDirectoryPath(), count);


    SmsMessageRecord record;
    EncryptingSmsDatabase.Reader reader = null;
    int skip                            = 0;
    final int ROW_LIMIT                 = 500;

    do {
      if (reader != null)
        reader.close();

      reader = DatabaseFactory.getEncryptingSmsDatabase(context).getMessages(masterSecret, skip, ROW_LIMIT);

      while ((record = reader.getNext()) != null) {
        String threadAddress = null;
        Recipients threadRecipients = threads.getRecipientsForThreadId(record.getThreadId());
        if (threadRecipients != null && !threadRecipients.isEmpty()) {
          threadAddress = threadRecipients.getPrimaryRecipient().getNumber();
        }
        XmlBackup.XmlBackupItem item =
            new XmlBackup.XmlBackupItem(0, record.getIndividualRecipient().getNumber(),
                                        threadAddress, record.getDateReceived(),
                                        MmsSmsColumns.Types.translateToSystemBaseType(record.getType()),
                                        null, record.getDisplayBody().toString(), null,
                                        1, record.getDeliveryStatus());

        writer.writeItem(item);
      }

      skip += ROW_LIMIT;
    } while (reader.getCount() > 0);

    skip = 0;
    MmsDatabase.Reader mmsReader = null;
    do {
      if (mmsReader != null)
        mmsReader.close();

      mmsReader = DatabaseFactory.getMmsDatabase(context).getMessages(masterSecret, skip, ROW_LIMIT);

      MessageRecord mmsRecord;
      while ((mmsRecord = mmsReader.getNext()) != null) {
        String threadAddress = null;
        Recipients threadRecipients = threads.getRecipientsForThreadId(mmsRecord.getThreadId());
        if (threadRecipients != null && !threadRecipients.isEmpty()) {
          threadAddress = threadRecipients.getPrimaryRecipient().getNumber();
        }
        XmlBackup.XmlBackupItem item =
                new XmlBackup.XmlBackupItem(1, mmsRecord.getIndividualRecipient().getNumber(),
                        threadAddress, mmsRecord.getDateReceived(),
                        MmsSmsColumns.Types.translateToSystemBaseType(mmsRecord.getType()),
                        null, mmsRecord.getDisplayBody().toString(), null,
                        1, mmsRecord.getDeliveryStatus());

        writer.writeItem(item);
      }

      skip += ROW_LIMIT;
    } while (mmsReader.getCount() > 0);

    writer.close();
  }
}
