package org.thoughtcrime.securesms.database;

import android.os.Environment;

import java.io.File;

public abstract class AbstractBackup {

  protected static void verifyCanRead() throws NoExternalStorageException {
    if (!Environment.getExternalStorageDirectory().canRead() ||
        !(getDirectoryPath().exists() || getOldFilePath().exists()))
      throw new NoExternalStorageException();
  }

  protected static void verifyCanWrite() throws NoExternalStorageException {
    if (!Environment.getExternalStorageDirectory().canWrite())
      throw new NoExternalStorageException();
  }

  protected static File getDirectoryPath() {
    File sdDirectory = Environment.getExternalStorageDirectory();
    File backupDirectory = new File(sdDirectory.getAbsolutePath() + File.separator + "Signal", "Backup");
    return backupDirectory;
  }

  protected static File getOldFilePath() {
    File sdDirectory = Environment.getExternalStorageDirectory();
    File FilePath = new File(sdDirectory.getAbsolutePath(), "TextSecurePlaintextBackup.xml");
    return FilePath;
  }

  protected static File getFilePath(String file) {
    return new File(getDirectoryPath(), file);
  }

  ;

}
