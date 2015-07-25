package org.thoughtcrime.securesms.database.loaders;

import android.content.Context;
import android.support.v4.content.AsyncTaskLoader;
import android.util.Log;

import org.whispersystems.textsecure.api.TextSecureAccountManager;
import org.whispersystems.textsecure.api.messages.multidevice.DeviceInfo;
import org.whispersystems.textsecure.api.push.TextSecureAddress;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public class DeviceListLoader extends AsyncTaskLoader<List<DeviceInfo>> {

  private static final String TAG = DeviceListLoader.class.getSimpleName();

  private final TextSecureAccountManager accountManager;

  public DeviceListLoader(Context context, TextSecureAccountManager accountManager) {
    super(context);
    this.accountManager = accountManager;
  }

  @Override
  public List<DeviceInfo> loadInBackground() {
    try {
      List<DeviceInfo>     devices  = accountManager.getDevices();
      Iterator<DeviceInfo> iterator = devices.iterator();

      while (iterator.hasNext()) {
        if ((iterator.next().getId() == TextSecureAddress.DEFAULT_DEVICE_ID)) {
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
