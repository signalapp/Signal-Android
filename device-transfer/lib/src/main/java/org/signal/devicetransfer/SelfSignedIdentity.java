package org.signal.devicetransfer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.libsignal.devicetransfer.DeviceTransferKey;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Generate and configure use of self-signed x509 and private key for establishing a TLS connection.
 */
final class SelfSignedIdentity {

  private static final String KEY_GENERATION_ALGORITHM = "RSA";
  private static final String SSL_CONTEXT_PROTOCOL     = "TLS";
  private static final String CERTIFICATE_TYPE         = "X509";
  private static final String KEYSTORE_TYPE            = "BKS";

  private SelfSignedIdentity() { }

  public static @NonNull SelfSignedKeys create() throws KeyGenerationFailedException {
    try {
      DeviceTransferKey key = new DeviceTransferKey();
      byte[]     x509       = key.generateCertificate("SignalTransfer", 1);
      PrivateKey privateKey = KeyFactory.getInstance(KEY_GENERATION_ALGORITHM).generatePrivate(new PKCS8EncodedKeySpec(key.keyMaterial()));
      return new SelfSignedKeys(x509, privateKey);
    } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
      throw new KeyGenerationFailedException(e);
    }
  }

  public static @NonNull SSLServerSocketFactory getServerSocketFactory(@NonNull SelfSignedKeys keys)
      throws GeneralSecurityException, IOException
  {
    Certificate certificate = CertificateFactory.getInstance(CERTIFICATE_TYPE)
                                                .generateCertificate(new ByteArrayInputStream(keys.getX509Encoded()));

    KeyStore keyStore = KeyStore.getInstance(KEYSTORE_TYPE);
    keyStore.load(null);
    keyStore.setKeyEntry("client", keys.getPrivateKey(), null, new Certificate[] { certificate });

    KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    keyManagerFactory.init(keyStore, null);

    SSLContext sslContext = SSLContext.getInstance(SSL_CONTEXT_PROTOCOL);
    sslContext.init(keyManagerFactory.getKeyManagers(), null, new SecureRandom());

    return sslContext.getServerSocketFactory();
  }

  public static @NonNull SSLSocketFactory getApprovingSocketFactory(@NonNull ApprovingTrustManager trustManager)
      throws GeneralSecurityException
  {
    SSLContext sslContext = SSLContext.getInstance(SSL_CONTEXT_PROTOCOL);
    sslContext.init(null, new TrustManager[] { trustManager }, new SecureRandom());
    return sslContext.getSocketFactory();
  }

  static final class SelfSignedKeys {
    private final byte[]     x509Encoded;
    private final PrivateKey privateKey;

    public SelfSignedKeys(@NonNull byte[] x509Encoded, @NonNull PrivateKey privateKey) {
      this.x509Encoded = x509Encoded;
      this.privateKey  = privateKey;
    }

    public @NonNull byte[] getX509Encoded() {
      return x509Encoded;
    }

    public @NonNull PrivateKey getPrivateKey() {
      return privateKey;
    }
  }

  static final class ApprovingTrustManager implements X509TrustManager {

    private @Nullable X509Certificate x509Certificate;

    @Override
    public void checkClientTrusted(@NonNull X509Certificate[] x509Certificates, @NonNull String authType) throws CertificateException {
      throw new CertificateException();
    }

    @Override
    public void checkServerTrusted(@NonNull X509Certificate[] x509Certificates, @NonNull String authType) throws CertificateException {
      if (x509Certificates.length != 1) {
        throw new CertificateException("More than 1 x509 certificate");
      }

      this.x509Certificate = x509Certificates[0];
    }

    @Override
    public @NonNull X509Certificate[] getAcceptedIssuers() {
      return new X509Certificate[0];
    }

    public @Nullable X509Certificate getX509Certificate() {
      return x509Certificate;
    }
  }
}
