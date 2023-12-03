package org.whispersystems.signalservice.api.util;

import org.signal.libsignal.zkgroup.profiles.ExpiringProfileKeyCredential;

import java.time.temporal.ChronoField;
import java.util.concurrent.TimeUnit;

public final class ExpiringProfileCredentialUtil {

  private ExpiringProfileCredentialUtil() {}

  public static boolean isValid(ExpiringProfileKeyCredential credential) {
    if (credential == null) {
      return false;
    }

    long now = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
    long expires = credential.getExpirationTime().getLong(ChronoField.INSTANT_SECONDS);
    return now < expires;
  }
}
