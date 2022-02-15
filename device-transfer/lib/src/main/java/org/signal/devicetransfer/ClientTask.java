package org.signal.devicetransfer;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;

/**
 * Self-contained chunk of code to run once the {@link DeviceTransferClient} connects to a
 * {@link DeviceTransferServer}.
 */
public interface ClientTask extends Serializable {

  /**
   * @param context      Android context, mostly like the foreground transfer service
   * @param outputStream Output stream associated with socket connected to remote server.
   */
  void run(@NonNull Context context, @NonNull OutputStream outputStream) throws IOException;

  /**
   * Called after the output stream has been successfully flushed and closed.
   */
  void success();
}
