package org.thoughtcrime.securesms.util;

import java.io.FileDescriptor;

public class FileUtils {

  static {
    System.loadLibrary("native-utils");
  }

  public static native int getFileDescriptorOwner(FileDescriptor fileDescriptor);

}
