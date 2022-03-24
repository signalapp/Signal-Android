/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.internal.util;

import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.signalservice.api.push.TrustStore;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.LinkedList;
import java.util.List;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Trust manager that defers to a system X509 trust manager, and
 * additionally rejects certificates if they have a blacklisted
 * serial.
 *
 * @author Moxie Marlinspike
 */
public class BlacklistingTrustManager implements X509TrustManager {

  private static final List<Pair<String, BigInteger>> BLACKLIST = new LinkedList<Pair<String, BigInteger>>() {{
    add(new Pair<>("Open Whisper Systems", new BigInteger("4098")));
  }};

  public static TrustManager[] createFor(TrustManager[] trustManagers) {
    for (TrustManager trustManager : trustManagers) {
      if (trustManager instanceof X509TrustManager) {
        TrustManager[] results = new BlacklistingTrustManager[1];
        results[0] = new BlacklistingTrustManager((X509TrustManager)trustManager);

        return results;
      }
    }

    throw new AssertionError("No X509 Trust Managers!");
  }

  public static TrustManager[] createFor(TrustStore trustStore) {
    try {
      InputStream keyStoreInputStream = trustStore.getKeyStoreInputStream();
      KeyStore    keyStore            = KeyStore.getInstance("BKS");

      keyStore.load(keyStoreInputStream, trustStore.getKeyStorePassword().toCharArray());

      TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("X509");
      trustManagerFactory.init(keyStore);

      return BlacklistingTrustManager.createFor(trustManagerFactory.getTrustManagers());
    } catch (KeyStoreException | CertificateException | IOException | NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  private final X509TrustManager trustManager;

  public BlacklistingTrustManager(X509TrustManager trustManager) {
    this.trustManager = trustManager;
  }

  @Override
  public void checkClientTrusted(X509Certificate[] chain, String authType)
      throws CertificateException
  {
    trustManager.checkClientTrusted(chain, authType);
  }

  @Override
  public void checkServerTrusted(X509Certificate[] chain, String authType)
      throws CertificateException
  {
    trustManager.checkServerTrusted(chain, authType);

    for (X509Certificate certificate : chain) {
      for (Pair<String, BigInteger> blacklistedSerial : BLACKLIST) {
        if (certificate.getIssuerDN().getName().equals(blacklistedSerial.first()) &&
            certificate.getSerialNumber().equals(blacklistedSerial.second()))
        {
          throw new CertificateException("Blacklisted Serial: " + certificate.getSerialNumber());
        }
      }
    }

  }

  @Override
  public X509Certificate[] getAcceptedIssuers() {
    return trustManager.getAcceptedIssuers();
  }
}
