package org.thoughtcrime.securesms.database.loaders;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.annimon.stream.Stream;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.thoughtcrime.securesms.devicelist.Device;
import org.thoughtcrime.securesms.util.AsyncLoader;
import org.thoughtcrime.securesms.util.Base64;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECPrivateKey;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.util.ByteUtil;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceInfo;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

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

import static org.thoughtcrime.securesms.devicelist.DeviceNameProtos.DeviceName;

public class DeviceListLoader extends AsyncLoader<List<Device>> {

  private static final String TAG = Log.tag(DeviceListLoader.class);

  private final SignalServiceAccountManager accountManager;

  public DeviceListLoader(Context context, SignalServiceAccountManager accountManager) {
    super(context);
    this.accountManager = accountManager;
  }

  @Override
  public List<Device> loadInBackground() {
    try {
      List<Device> devices = Stream.of(accountManager.getDevices())
                                   .filter(d -> d.getId() != SignalServiceAddress.DEFAULT_DEVICE_ID)
                                   .map(this::mapToDevice)
                                   .toList();

      Collections.sort(devices, new DeviceComparator());

      return devices;
    } catch (IOException e) {
      Log.w(TAG, e);
      return null;
    }
  }

  private Device mapToDevice(@NonNull DeviceInfo deviceInfo) {
    try {
      if (TextUtils.isEmpty(deviceInfo.getName()) || deviceInfo.getName().length() < 4) {
        throw new IOException("Invalid DeviceInfo name.");
      }

      DeviceName deviceName = DeviceName.parseFrom(Base64.decode(deviceInfo.getName()));

      if (!deviceName.hasCiphertext() || !deviceName.hasEphemeralPublic() || !deviceName.hasSyntheticIv()) {
        throw new IOException("Got a DeviceName that wasn't properly populated.");
      }

      return new Device(deviceInfo.getId(), new String(decryptName(deviceName, IdentityKeyUtil.getIdentityKeyPair(getContext()))), deviceInfo.getCreated(), deviceInfo.getLastSeen());

    } catch (IOException e) {
      Log.w(TAG, "Failed while reading the protobuf.", e);
    } catch (GeneralSecurityException | InvalidKeyException e) {
      Log.w(TAG, "Failed during decryption.", e);
    }

    return new Device(deviceInfo.getId(), deviceInfo.getName(), deviceInfo.getCreated(), deviceInfo.getLastSeen());
  }

  @VisibleForTesting
  public static byte[] decryptName(DeviceName deviceName, IdentityKeyPair identityKeyPair) throws InvalidKeyException, GeneralSecurityException {
    byte[]       syntheticIv     = deviceName.getSyntheticIv().toByteArray();
    byte[]       cipherText      = deviceName.getCiphertext().toByteArray();
    ECPrivateKey identityKey     = identityKeyPair.getPrivateKey();
    ECPublicKey  ephemeralPublic = Curve.decodePoint(deviceName.getEphemeralPublic().toByteArray(), 0);
    byte[]       masterSecret    = Curve.calculateAgreement(ephemeralPublic, identityKey);

    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(masterSecret, "HmacSHA256"));
    byte[] cipherKeyPart1 = mac.doFinal("cipher".getBytes());

    mac.init(new SecretKeySpec(cipherKeyPart1, "HmacSHA256"));
    byte[] cipherKey = mac.doFinal(syntheticIv);

    Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
    cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cipherKey, "AES"), new IvParameterSpec(new byte[16]));
    final byte[] plaintext = cipher.doFinal(cipherText);

    mac.init(new SecretKeySpec(masterSecret, "HmacSHA256"));
    byte[] verificationPart1 = mac.doFinal("auth".getBytes());

    mac.init(new SecretKeySpec(verificationPart1, "HmacSHA256"));
    byte[] verificationPart2 = mac.doFinal(plaintext);
    byte[] ourSyntheticIv    = ByteUtil.trim(verificationPart2, 16);

    if (!MessageDigest.isEqual(ourSyntheticIv, syntheticIv)) {
      throw new GeneralSecurityException("The computed syntheticIv didn't match the actual syntheticIv.");
    }

    return plaintext;
  }

  private static class DeviceComparator implements Comparator<Device> {

    @Override
    public int compare(Device lhs, Device rhs) {
      if      (lhs.getCreated() < rhs.getCreated())  return -1;
      else if (lhs.getCreated() != rhs.getCreated()) return 1;
      else                                           return 0;
    }
  }
}
