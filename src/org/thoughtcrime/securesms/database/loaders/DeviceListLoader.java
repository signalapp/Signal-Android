package org.thoughtcrime.securesms.database.loaders;

import android.content.Context;
import android.support.annotation.NonNull;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.devicelist.Device;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.loki.redesign.utilities.MnemonicUtilities;
import org.thoughtcrime.securesms.util.AsyncLoader;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.signalservice.loki.api.LokiStorageAPI;
import org.whispersystems.signalservice.loki.crypto.MnemonicCodec;

import java.io.File;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class DeviceListLoader extends AsyncLoader<List<Device>> {

  private static final String TAG = DeviceListLoader.class.getSimpleName();
  private MnemonicCodec mnemonicCodec;

  public DeviceListLoader(Context context, File languageFileDirectory) {
    super(context);
    this.mnemonicCodec = new MnemonicCodec(languageFileDirectory);
  }

  @Override
  public List<Device> loadInBackground() {
    try {
      String ourPublicKey = TextSecurePreferences.getLocalNumber(getContext());
      List<String> secondaryDevicePublicKeys = LokiStorageAPI.shared.getSecondaryDevicePublicKeys(ourPublicKey).get();
      List<Device> devices = Stream.of(secondaryDevicePublicKeys).map(this::mapToDevice).toList();
      Collections.sort(devices, new DeviceComparator());
      return devices;
    } catch (Exception e) {
      Log.w(TAG, e);
      return null;
    }
  }

  private Device mapToDevice(@NonNull String hexEncodedPublicKey) {
    String shortId = MnemonicUtilities.getFirst3Words(mnemonicCodec, hexEncodedPublicKey);
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
