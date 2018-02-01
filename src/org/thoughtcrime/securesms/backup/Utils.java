package org.thoughtcrime.securesms.backup;

import org.thoughtcrime.securesms.crypto.EncryptingPartOutputStream;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.MasterSecretFromPassphraseSpec;
import org.thoughtcrime.securesms.database.NoExternalStorageException;
import org.thoughtcrime.securesms.util.StorageUtil;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

class Utils {

  private static final String ENCRYPTED_BACKUP_FILENAME = "EncryptedFullSignalBackup";

  static String getEncryptedBackupPath() throws NoExternalStorageException {
    return (new File(StorageUtil.getBackupDir(), ENCRYPTED_BACKUP_FILENAME)).getAbsolutePath();
  }

  static Writer createEncryptingExportWriter(String passphrase)
          throws NoSuchAlgorithmException, NoExternalStorageException, InvalidKeySpecException,
          FileNotFoundException {
    String path = getEncryptedBackupPath();
    File file = new File(path);
    MasterSecretFromPassphraseSpec spec = new MasterSecretFromPassphraseSpec(passphrase);
    MasterSecret masterSecret = spec.deriveMasterSecret();

    OutputStream outputStream =
            new EncryptingPartOutputStream(file, masterSecret, spec.saltAndIterationBytes());
    return new OutputStreamWriter(outputStream);
  }

}
