package org.thoughtcrime.securesms.crypto;

import android.os.Parcel;
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
import java.util.Arrays;

import static org.fest.assertions.api.Assertions.assertThat;

public class EncryptedStreamsTest extends InstrumentationTestCase {
  private static byte[] HEADER         = new byte[]{(byte)0x11, (byte)0x11, (byte)0x11, (byte)0x11, (byte)0x11, (byte)0x11, (byte)0x11, (byte)0x11,
                                                    (byte)0x11, (byte)0x11, (byte)0x11, (byte)0x11, (byte)0x11, (byte)0x11, (byte)0x11, (byte)0x11};

  private static byte[] BODY           = new byte[]{(byte)0xaa, (byte)0xaa, (byte)0xaa, (byte)0xaa, (byte)0xaa, (byte)0xaa, (byte)0xaa, (byte)0xaa,
                                                    (byte)0xaa, (byte)0xaa, (byte)0xaa, (byte)0xaa, (byte)0xaa, (byte)0xaa, (byte)0xaa, (byte)0xaa};

  private static byte[] ENCRYPTION_KEY = new byte[]{(byte)0x00, (byte)0x01, (byte)0x02, (byte)0x03, (byte)0x04, (byte)0x05, (byte)0x06, (byte)0x07,
                                                    (byte)0x08, (byte)0x09, (byte)0x0a, (byte)0x0b, (byte)0x0c, (byte)0x0d, (byte)0x0e, (byte)0x0f};

  private static byte[] MAC_KEY        = new byte[]{(byte)0x00, (byte)0x01, (byte)0x02, (byte)0x03, (byte)0x04, (byte)0x05, (byte)0x06, (byte)0x07,
                                                    (byte)0x08, (byte)0x09, (byte)0x0a, (byte)0x0b, (byte)0x0c, (byte)0x0d, (byte)0x0e, (byte)0x0f,
                                                    (byte)0x10, (byte)0x11, (byte)0x12, (byte)0x13};
  private MasterSecret masterSecret;
  private File         testFile;

  public void setUp() throws Exception {
    super.setUp();
    Parcel parcel = null;
    try {
      parcel = Parcel.obtain();
      parcel.writeInt(ENCRYPTION_KEY.length);
      parcel.writeByteArray(ENCRYPTION_KEY);
      parcel.writeInt(MAC_KEY.length);
      parcel.writeByteArray(MAC_KEY);
      parcel.setDataPosition(0);

      masterSecret = MasterSecret.CREATOR.createFromParcel(parcel);
    } finally {
      if (parcel != null) parcel.recycle();
    }

    testFile = new File(getInstrumentation().getTargetContext().getCacheDir().getAbsolutePath() + File.separator + "encrypted_part");
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
    byte[] body = new byte[BODY.length];
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
    byte[] header = new byte[HEADER.length];
    if (HEADER.length != headerInput.read(header)) throw new AssertionFailedError("couldn't read full header length");

    InputStream encryptedInput = new BufferedInputStream(new DecryptingPartInputStream(testFile, masterSecret, HEADER.length));
    byte[] body = new byte[BODY.length];
    if (BODY.length != encryptedInput.read(body)) throw new AssertionFailedError("couldn't read full body length");
    encryptedInput.close();

    assertThat(header).isEqualTo(HEADER);
    assertThat(body).isEqualTo(BODY);
  }

}