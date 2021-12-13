package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.MemoryFile;
import android.os.ParcelFileDescriptor;
import android.os.ProxyFileDescriptorCallback;
import android.os.storage.StorageManager;
import android.system.ErrnoException;
import android.system.OsConstants;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import org.signal.core.util.logging.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

@RequiresApi(api = 26)
final class MemoryFileDescriptorProxy {

  private static final String TAG = Log.tag(MemoryFileDescriptorProxy.class);

  public static ParcelFileDescriptor create(@NonNull Context context,
                                            @NonNull MemoryFile file)
      throws IOException
  {
    StorageManager storageManager = Objects.requireNonNull(context.getSystemService(StorageManager.class));
    HandlerThread  thread         = new HandlerThread("MemoryFile");

    thread.start();
    Log.i(TAG, "Thread started");

    Handler       handler       = new Handler(thread.getLooper());
    ProxyCallback proxyCallback = new ProxyCallback(file, () -> {
      if (thread.quitSafely()) {
        Log.i(TAG, "Thread quitSafely true");
      } else {
        Log.w(TAG, "Thread quitSafely false");
      }
    });

    ParcelFileDescriptor parcelFileDescriptor = storageManager.openProxyFileDescriptor(ParcelFileDescriptor.MODE_READ_ONLY,
                                                                                       proxyCallback,
                                                                                       handler);

    Log.i(TAG, "Created");
    return parcelFileDescriptor;
  }

  private static final class ProxyCallback extends ProxyFileDescriptorCallback {

    private final MemoryFile memoryFile;
    private final Runnable   onClose;

    ProxyCallback(@NonNull MemoryFile memoryFile, Runnable onClose) {
      this.memoryFile = memoryFile;
      this.onClose    = onClose;
    }

    @Override
    public long onGetSize() {
      return memoryFile.length();
    }

    @Override
    public int onRead(long offset, int size, byte[] data) throws ErrnoException {
      try {
        InputStream inputStream = memoryFile.getInputStream();
        if(inputStream.skip(offset) != offset){
          if (offset > memoryFile.length()) {
            throw new ErrnoException("onRead", OsConstants.EIO);
          }

          throw new AssertionError();
        }
        return inputStream.read(data, 0, size);
      } catch (IOException e) {
        throw new ErrnoException("onRead", OsConstants.EBADF);
      }
    }

    @Override
    public void onRelease() {
      Log.i(TAG, "onRelease()");
      memoryFile.close();
      onClose.run();
    }
  }
}
