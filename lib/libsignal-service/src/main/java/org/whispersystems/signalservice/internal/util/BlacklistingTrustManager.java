/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.internal.util;

import org.jetbrains.annotations.VisibleForTesting;
import org.signal.core.util.CryptoUtil;
import org.signal.core.util.Hex;
import org.signal.network.config.TrustStore;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import kotlin.Pair;

/**
 * Trust manager that defers to a system X509 trust manager, and
 * additionally rejects certificates if they have a blacklisted serial or public key.
 *
 * @author Moxie Marlinspike
 */
public class BlacklistingTrustManager implements X509TrustManager {

  private static final List<Pair<String, BigInteger>> BLACKLIST = new LinkedList<Pair<String, BigInteger>>() {{
    add(new Pair<>("Open Whisper Systems", new BigInteger("4098")));
  }};

  /**
   * SHA-256 hashes of the SubjectPublicKeyInfo for the eight compromised eMudhra intermediates.
   *
   * We blacklist the public keys rather than the individual certificates to break both known trust
   * chains (and also, conservatively, any as-of-yet undiscovered chains) at once.
   *
   * See Mozilla's incident report at https://bugzilla.mozilla.org/show_bug.cgi?id=2083449 and its guidance for trust
   * store operators at https://groups.google.com/a/mozilla.org/g/dev-security-policy/c/0R3XLJL6FuE
   */
  @VisibleForTesting
  static final Set<String> BLACKLISTED_PUBLIC_KEY_SHA256 = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
      "ebed6475c2c2d80fcb199f3fe8dd6e6b3ce60b77eb15d4e42c3dec0c6f5b5105",
      "43bffa19d6ef651451f6841e019931e96f6f2eaeff28ab4b6f1ce4576de2717b",
      "e0254c7fcdc265e2f0f8a428dabb75ab4ae039b4872d62f5ee57f0877d5a8a41",
      "49dd353dc2b919a3770645117d0e60c16115e7d1bc259eefe734b00ea434fe5f",
      "63ecefee431d49523bc1136c4e72d0206a477086caf606cd5cf025686c109cf8",
      "5c0660a2b164910d8ff589c18e14552e9e0ec237dcefe2cb736d41a4811f81a0",
      "ec4ffa1ad0ac88081314b7aaffef6a91f97cd57291bb5ba686c5415f5eb4f6b6",
      "92c063d67e8f14db71d288e11266ffdaf69cbf115f7bab14f3bc2f68d25e0e30"
  )));

  public static BlacklistingTrustManager createFor(TrustManager[] trustManagers) {
    for (TrustManager trustManager : trustManagers) {
      if (trustManager instanceof X509TrustManager) {
        return new BlacklistingTrustManager((X509TrustManager) trustManager);
      }
    }

    throw new AssertionError("No X509 Trust Managers!");
  }

  /**
   * Creates a blacklisting trust manager backed by the platform's default trust store.
   */
  public static BlacklistingTrustManager createForDefaultTrustStore() {
    try {
      TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      trustManagerFactory.init((KeyStore) null);

      return BlacklistingTrustManager.createFor(trustManagerFactory.getTrustManagers());
    } catch (KeyStoreException | NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  public static TrustManager[] createFor(TrustStore trustStore) {
    try {
      InputStream keyStoreInputStream = trustStore.getKeyStoreInputStream();
      KeyStore    keyStore            = KeyStore.getInstance("BKS");

      keyStore.load(keyStoreInputStream, trustStore.getKeyStorePassword().toCharArray());

      TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("X509");
      trustManagerFactory.init(keyStore);

      return new TrustManager[] { BlacklistingTrustManager.createFor(trustManagerFactory.getTrustManagers()) };
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
    for (X509Certificate certificate : chain) {
      String publicKeySha256 = Hex.toStringCondensed(CryptoUtil.sha256(certificate.getPublicKey().getEncoded()));
      if (BLACKLISTED_PUBLIC_KEY_SHA256.contains(publicKeySha256)) {
        throw new CertificateException("Blacklisted public key: " + publicKeySha256);
      }

      for (Pair<String, BigInteger> blacklistedSerial : BLACKLIST) {
        if (certificate.getIssuerDN().getName().equals(blacklistedSerial.getFirst()) &&
            certificate.getSerialNumber().equals(blacklistedSerial.getSecond()))
        {
          throw new CertificateException("Blacklisted Serial: " + certificate.getSerialNumber());
        }
      }
    }

    trustManager.checkServerTrusted(chain, authType);
  }

  @Override
  public X509Certificate[] getAcceptedIssuers() {
    return trustManager.getAcceptedIssuers();
  }
}
