package org.thoughtcrime.securesms.util;

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.MemoryFile;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.dependencies.AppDependencies;

import java.io.FileDescriptor;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class MemoryFileUtil {

  private MemoryFileUtil() {}

  public static ParcelFileDescriptor getParcelFileDescriptor(@NonNull MemoryFile file)
      throws IOException
  {
    if (Build.VERSION.SDK_INT >= 26) {
      return MemoryFileDescriptorProxy.create(AppDependencies.getApplication(), file);
    } else {
      return getParcelFileDescriptorLegacy(file);
    }
  }

  @SuppressWarnings("JavaReflectionMemberAccess")
  @SuppressLint("PrivateApi")
  public static ParcelFileDescriptor getParcelFileDescriptorLegacy(@NonNull MemoryFile file)
      throws IOException
  {
    try {
      Method         method         = MemoryFile.class.getDeclaredMethod("getFileDescriptor");
      FileDescriptor fileDescriptor = (FileDescriptor) method.invoke(file);

      Field field = fileDescriptor.getClass().getDeclaredField("descriptor");
      field.setAccessible(true);

      int fd = field.getInt(fileDescriptor);

      return ParcelFileDescriptor.adoptFd(fd);
    } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException | NoSuchFieldException e) {
      throw new IOException(e);
    }
  }
}
