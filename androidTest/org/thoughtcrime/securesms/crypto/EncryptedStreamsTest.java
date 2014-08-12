package org.thoughtcrime.securesms.crypto;

import android.test.InstrumentationTestCase;

import junit.framework.AssertionFailedError;

import org.whispersystems.textsecure.crypto.MasterSecret;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static org.fest.assertions.api.Assertions.assertThat;

public class EncryptedStreamsTest extends InstrumentationTestCase {
  private static byte[] HEADER = new byte[]{(byte) 0x11, (byte) 0x11, (byte) 0x11, (byte) 0x11, (byte) 0x11, (byte) 0x11, (byte) 0x11, (byte) 0x11,
                                            (byte) 0x11, (byte) 0x11, (byte) 0x11, (byte) 0x11, (byte) 0x11, (byte) 0x11, (byte) 0x11, (byte) 0x11};

  private static byte[] BODY   = new byte[]{(byte) 0xaa, (byte) 0xaa, (byte) 0xaa, (byte) 0xaa, (byte) 0xaa, (byte) 0xaa, (byte) 0xaa, (byte) 0xaa,
                                            (byte) 0xaa, (byte) 0xaa, (byte) 0xaa, (byte) 0xaa, (byte) 0xaa, (byte) 0xaa, (byte) 0xaa, (byte) 0xaa};

  private MasterSecret masterSecret;
  private File         testFile;

  public void setUp() throws Exception {
    super.setUp();

    masterSecret = MockMasterSecret.create();
    testFile     = new File(getInstrumentation().getTargetContext().getCacheDir().getAbsolutePath() + File.separator + "encrypted_part");
    if (!testFile.exists() && !testFile.createNewFile()) throw new IOException("couldn't get or create file");
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  public void tearDown() throws Exception {
    testFile.delete();
  }

  public void testEncryptDecryptNoHeader() throws Exception {

    FileOutputStream output = new EncryptingPartOutputStream(testFile, masterSecret);
    output.write(BODY);
    output.flush();
    output.close();

    InputStream input = new BufferedInputStream(new DecryptingPartInputStream(testFile, masterSecret));
    byte[]      body  = new byte[BODY.length];
    if (BODY.length != input.read(body)) throw new AssertionFailedError("couldn't read full body length");
    input.close();
    assertThat(body).isEqualTo(BODY);
  }

  public void testEncryptDecryptWithHeader() throws Exception {
    OutputStream headerOutput = new FileOutputStream(testFile);
    headerOutput.write(HEADER);
    headerOutput.flush();
    headerOutput.close();

    OutputStream encryptedOutput = new EncryptingPartOutputStream(testFile, masterSecret, true);
    encryptedOutput.write(BODY);
    encryptedOutput.flush();
    encryptedOutput.close();

    InputStream headerInput = new BufferedInputStream(new FileInputStream(testFile));
    byte[]      header      = new byte[HEADER.length];
    if (HEADER.length != headerInput.read(header)) throw new AssertionFailedError("couldn't read full header length");
    headerInput.close();

    InputStream encryptedInput = new BufferedInputStream(new DecryptingPartInputStream(testFile, masterSecret, HEADER.length));
    byte[]      body           = new byte[BODY.length];
    if (BODY.length != encryptedInput.read(body)) throw new AssertionFailedError("couldn't read full body length");
    encryptedInput.close();

    assertThat(header).isEqualTo(HEADER);
    assertThat(body).isEqualTo(BODY);
  }

}