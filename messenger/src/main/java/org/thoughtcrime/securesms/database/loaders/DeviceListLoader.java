package org.thoughtcrime.securesms.database.loaders;

import android.content.Context;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.devicelist.Device;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.AsyncLoader;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.signalservice.loki.protocol.shelved.multidevice.MultiDeviceProtocol;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class DeviceListLoader extends AsyncLoader<List<Device>> {

  private static final String TAG = DeviceListLoader.class.getSimpleName();

  public DeviceListLoader(Context context) {
    super(context);
  }

  @Override
  public List<Device> loadInBackground() {
    try {
      String userPublicKey = TextSecurePreferences.getLocalNumber(getContext());
      Set<String> slaveDevicePublicKeys = MultiDeviceProtocol.shared.getSlaveDevices(userPublicKey);
      List<Device> devices = Stream.of(slaveDevicePublicKeys).map(this::mapToDevice).toList();
      Collections.sort(devices, new DeviceComparator());
      return devices;
    } catch (Exception e) {
      Log.w(TAG, e);
      return null;
    }
  }

  private Device mapToDevice(@NonNull String hexEncodedPublicKey) {
    String shortId = "";
    String name = DatabaseFactory.getLokiUserDatabase(getContext()).getDisplayName(hexEncodedPublicKey);
    return new Device(hexEncodedPublicKey, shortId, name);
  }

  private static class DeviceComparator implements Comparator<Device> {

    @Override
    public int compare(Device lhs, Device rhs) {
      return lhs.getName().compareTo(rhs.getName());
    }
  }
}
