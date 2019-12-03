package org.thoughtcrime.securesms.util;

import android.content.Context;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.push.IasTrustStore;
import org.whispersystems.signalservice.api.push.TrustStore;

import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

public final class IasKeyStore {

  private IasKeyStore() {
  }

  public static KeyStore getIasKeyStore(@NonNull Context context) {
    try {
      TrustStore contactTrustStore = new IasTrustStore(context);

      KeyStore keyStore = KeyStore.getInstance("BKS");
      keyStore.load(contactTrustStore.getKeyStoreInputStream(), contactTrustStore.getKeyStorePassword().toCharArray());

      return keyStore;
    } catch (KeyStoreException | CertificateException | IOException | NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }
}
