package org.thoughtcrime.securesms.backup;

import android.content.Context;

import org.thoughtcrime.securesms.crypto.DecryptingPartInputStream;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.MasterSecretFromPassphraseSpec;
import org.thoughtcrime.securesms.database.NoExternalStorageException;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import static org.thoughtcrime.securesms.crypto.MasterSecretFromPassphraseSpec.ITERATION_LENGTH;
import static org.thoughtcrime.securesms.crypto.MasterSecretFromPassphraseSpec.SALT_LENGTH;

public class FullBackupImporter {

  // forbid instantiation
  private FullBackupImporter() {

  }

  public static void importXml(Context context, String passphrase)
          throws ParserConfigurationException, NoExternalStorageException, SAXException,
          IOException, InvalidKeySpecException, NoSuchAlgorithmException {
    importEncrypted(context, passphrase);
  }

  private static void importEncrypted(Context context, String passphrase)
          throws IOException, ParserConfigurationException, SAXException, InvalidKeySpecException,
          NoSuchAlgorithmException, NoExternalStorageException {
    String encryptedBackupPath = Utils.getEncryptedBackupPath();
    File encryptedBackupFile = new File(encryptedBackupPath);

    byte[] saltAndIterationBytes = read(encryptedBackupFile, SALT_LENGTH + ITERATION_LENGTH);
    byte[] salt = Arrays.copyOfRange(saltAndIterationBytes, 0, SALT_LENGTH);
    byte[] iterations = Arrays.copyOfRange(saltAndIterationBytes, SALT_LENGTH, saltAndIterationBytes.length);
    MasterSecretFromPassphraseSpec spec = new MasterSecretFromPassphraseSpec(passphrase, salt, iterations);
    MasterSecret masterSecret = spec.deriveMasterSecret();

    InputStream inputStream = DecryptingPartInputStream.createFor(masterSecret, encryptedBackupFile, saltAndIterationBytes.length);
    try {
      importWithInputStream(context, inputStream);
    } finally {
      inputStream.close();
    }
  }

  private static byte[] read(File file, int numberOfBytes) throws IOException {
    if (file.length() <= numberOfBytes) {
      throw new IOException("File too short");
    }

    FileInputStream stream = new FileInputStream(file);
    try {
      return readFully(stream, numberOfBytes);
    } finally {
      stream.close();
    }
  }

  private static byte[] readFully(FileInputStream stream, int numberOfBytes) throws IOException {
    byte[] result = new byte[numberOfBytes];
    int read;
    for (int i = 0; i < numberOfBytes; ++i) {
      read = stream.read();
      if (read == -1) {
        throw new IOException("unexpected end of file");
      }
      result[i] = (byte) read;
    }
    return result;
  }

  private static void importWithInputStream(Context context, InputStream inputStream)
          throws ParserConfigurationException, SAXException, IOException {
    InputSource is = new InputSource(inputStream);
    DefaultHandler handler = new XmlImportHandler(context);
    SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
    parser.parse(is, handler);
  }
}
