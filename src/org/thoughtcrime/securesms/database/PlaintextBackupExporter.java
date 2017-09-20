package org.thoughtcrime.securesms.database;


import android.content.Context;
import android.util.Log;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;
import org.thoughtcrime.securesms.util.StorageUtil;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class PlaintextBackupExporter {
  private static final String TAG = PlaintextBackupExporter.class.getSimpleName();

  public static File exportPlaintextToSd(Context context, MasterSecret masterSecret)
      throws NoExternalStorageException, IOException
  {
    return exportPlaintext(context, masterSecret);
  }

  private static File getTimestampedPlaintextExportFile() throws NoExternalStorageException {
    DateFormat iso8601DateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    iso8601DateFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
    Date   timestamp = new Date(System.currentTimeMillis());
    String filename  = "SignalPlaintextBackup-" + iso8601DateFormatter.format(timestamp) + ".xml";
    return new File(StorageUtil.getBackupDir(), filename);
  }

  private static File exportPlaintext(Context context, MasterSecret masterSecret)
      throws NoExternalStorageException, IOException
  {
    int  count      = DatabaseFactory.getSmsDatabase(context).getMessageCount();
    File exportFile = getTimestampedPlaintextExportFile();
    File exportDir  = exportFile.getParentFile();
    if (!exportDir.exists()) {
      if (!exportDir.mkdirs()) {
        Log.w(TAG, "mkdirs() returned false, attempting to continue exporting");
      }
    }
    Log.w(TAG, "Exporting to " + exportFile.getAbsolutePath());
    XmlBackup.Writer writer = new XmlBackup.Writer(exportFile.getAbsolutePath(), count);


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
    return exportFile;
  }
}
