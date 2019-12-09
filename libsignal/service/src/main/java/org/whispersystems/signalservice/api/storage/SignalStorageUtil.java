package org.whispersystems.signalservice.api.storage;

import org.whispersystems.signalservice.api.crypto.CryptoUtil;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

public final class SignalStorageUtil {

  public static byte[] computeStorageServiceKey(byte[] kbsMasterKey) {
    return CryptoUtil.computeHmacSha256(kbsMasterKey, "Storage Service Encryption".getBytes(StandardCharsets.UTF_8));
  }
}
