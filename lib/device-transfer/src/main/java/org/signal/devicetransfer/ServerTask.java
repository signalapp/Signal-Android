package org.signal.devicetransfer;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;

/**
 * Self-contained chunk of code to run once the {@link DeviceTransferServer} has a
 * connected {@link DeviceTransferClient}.
 */
public interface ServerTask extends Serializable {

  /**
   * @param context     Android context, mostly like the foreground transfer service
   * @param inputStream Input stream associated with socket connected to remote client.
   */
  void run(@NonNull Context context, @NonNull InputStream inputStream) throws IOException;
}
