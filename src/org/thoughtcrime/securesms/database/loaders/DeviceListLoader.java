package org.thoughtcrime.securesms.database.loaders;

import android.content.Context;

import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.AsyncLoader;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceInfo;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public class DeviceListLoader extends AsyncLoader<List<DeviceInfo>> {

  private static final String TAG = DeviceListLoader.class.getSimpleName();

  private final SignalServiceAccountManager accountManager;

  public DeviceListLoader(Context context, SignalServiceAccountManager accountManager) {
    super(context);
    this.accountManager = accountManager;
  }

  @Override
  public List<DeviceInfo> loadInBackground() {
    try {
      List<DeviceInfo>     devices  = accountManager.getDevices();
      Iterator<DeviceInfo> iterator = devices.iterator();

      while (iterator.hasNext()) {
        if ((iterator.next().getId() == SignalServiceAddress.DEFAULT_DEVICE_ID)) {
          iterator.remove();
        }
      }

      Collections.sort(devices, new DeviceInfoComparator());

      return devices;
    } catch (IOException e) {
      Log.w(TAG, e);
      return null;
    }
  }

  private static class DeviceInfoComparator implements Comparator<DeviceInfo> {

    @Override
    public int compare(DeviceInfo lhs, DeviceInfo rhs) {
      if      (lhs.getCreated() < rhs.getCreated())  return -1;
      else if (lhs.getCreated() != rhs.getCreated()) return 1;
      else                                           return 0;
    }
  }
}
