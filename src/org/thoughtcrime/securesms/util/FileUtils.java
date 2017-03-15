package org.thoughtcrime.securesms.util;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class FileUtils {

  static {
    System.loadLibrary("native-utils");
  }

  public static native int getFileDescriptorOwner(FileDescriptor fileDescriptor);

  public static byte[] getFileDigest(FileInputStream fin) throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA256");

      byte[] buffer = new byte[4096];
      int read = 0;

      while ((read = fin.read(buffer, 0, buffer.length)) != -1) {
        digest.update(buffer, 0, read);
      }

      return digest.digest();
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }
}
