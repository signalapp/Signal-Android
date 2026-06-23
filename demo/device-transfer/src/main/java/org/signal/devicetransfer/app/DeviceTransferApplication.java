package org.signal.devicetransfer.app;

import android.app.Application;

import org.signal.core.util.logging.AndroidLogger;
import org.signal.core.util.logging.Log;

public class DeviceTransferApplication extends Application {
  @Override
  public void onCreate() {
    super.onCreate();
    Log.initialize(AndroidLogger.INSTANCE);
  }
}
