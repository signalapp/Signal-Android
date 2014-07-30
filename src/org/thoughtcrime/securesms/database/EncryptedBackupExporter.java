/**
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.database;

import android.content.Context;
import android.os.Environment;

import org.thoughtcrime.securesms.backup.EncryptedBackup;
import org.thoughtcrime.securesms.crypto.InvalidPassphraseException;
import org.whispersystems.textsecure.crypto.MasterSecret;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

public class EncryptedBackupExporter {

  private static File exportDirectory = new File(getExportDirectoryPath());
  private static File exportZipFile   = new File(exportDirectory.getAbsolutePath() + File.separator + "TextSecureBackup.tsbk");

  public static void exportToSd(Context context, MasterSecret masterSecret) throws NoExternalStorageException, IOException {
    verifyExternalStorageForExport();

    if (!exportZipFile.exists()) {
      if (!exportZipFile.createNewFile()) throw new AssertionError("export file didn't exist but then couldn't create one...");
    }
    OutputStream   exportStream = EncryptedBackup.getOutputStream(context, exportZipFile, masterSecret);
    BufferedWriter writer       = new BufferedWriter(new OutputStreamWriter(exportStream));

    try {
      PlaintextBackupExporter.exportPlaintextWithIdentity(context, masterSecret, writer);
      writer.flush();
    } finally {
      writer.close();
    }
  }

  public static void importFromSd(Context context, MasterSecret currentMasterSecret, String passphrase)
      throws NoExternalStorageException, IOException, InvalidPassphraseException
  {
    verifyExternalStorageForImport();
    File        exportDirectory = new File(getExportDirectoryPath());
    File        exportZipFile   = new File(exportDirectory.getAbsolutePath() + File.separator + "TextSecureBackup.tsbk");
    InputStream inputStream     = EncryptedBackup.getInputStream(context, exportZipFile, passphrase);
    PlaintextBackupImporter.importPlaintext(context, currentMasterSecret, inputStream);
  }

  private static String getExportDirectoryPath() {
    File sdDirectory = Environment.getExternalStorageDirectory();
    return sdDirectory.getAbsolutePath();
  }

  private static void verifyExternalStorageForExport() throws NoExternalStorageException {
    if (!Environment.getExternalStorageDirectory().canWrite())
      throw new NoExternalStorageException();

    String exportDirectoryPath = getExportDirectoryPath();
    File exportDirectory = new File(exportDirectoryPath);

    if (!exportDirectory.exists())
      exportDirectory.mkdir();
  }

  private static void verifyExternalStorageForImport() throws NoExternalStorageException {
    if (!Environment.getExternalStorageDirectory().canRead() ||
        !(new File(getExportDirectoryPath()).exists()))
      throw new NoExternalStorageException();
  }
}
