package org.thoughtcrime.securesms.database.loaders;

import android.content.Context;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.thoughtcrime.securesms.database.Database;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.devicelist.Device;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.loki.MnemonicUtilities;
import org.thoughtcrime.securesms.util.AsyncLoader;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECPrivateKey;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.util.ByteUtil;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceInfo;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.loki.api.LokiStorageAPI;
import org.whispersystems.signalservice.loki.crypto.MnemonicCodec;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import static org.thoughtcrime.securesms.devicelist.DeviceNameProtos.*;
import static org.whispersystems.signalservice.loki.utilities.TrimmingKt.removing05PrefixIfNeeded;

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
