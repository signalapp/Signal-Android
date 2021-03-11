package org.signal.devicetransfer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.spongycastle.asn1.x500.X500Name;
import org.spongycastle.asn1.x500.X500NameBuilder;
import org.spongycastle.asn1.x500.style.BCStyle;
import org.spongycastle.asn1.x509.SubjectPublicKeyInfo;
import org.spongycastle.cert.X509CertificateHolder;
import org.spongycastle.cert.X509v3CertificateBuilder;
import org.spongycastle.jce.provider.BouncyCastleProvider;
import org.spongycastle.operator.ContentSigner;
import org.spongycastle.operator.OperatorCreationException;
import org.spongycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Date;

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
  private static final int    KEY_SIZE                 = 4096;
  private static final String SSL_CONTEXT_PROTOCOL     = "TLS";
  private static final String CERTIFICATE_TYPE         = "X509";
  private static final String KEYSTORE_TYPE            = "BKS";
  private static final String SIGNATURE_ALGORITHM      = "SHA256WithRSAEncryption";

  private SelfSignedIdentity() { }

  public static @NonNull SelfSignedKeys create() throws KeyGenerationFailedException {
    try {
      KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(KEY_GENERATION_ALGORITHM);
      keyPairGenerator.initialize(KEY_SIZE);

      KeyPair               keyPair = keyPairGenerator.generateKeyPair();
      X509CertificateHolder x509    = createX509(keyPair);

      return new SelfSignedKeys(x509.getEncoded(), keyPair.getPrivate());
    } catch (GeneralSecurityException | OperatorCreationException | IOException e) {
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
    keyStore.setKeyEntry("client", keys.getPrivateKey(), null, new Certificate[]{certificate});

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
    sslContext.init(null, new TrustManager[]{trustManager}, new SecureRandom());
    return sslContext.getSocketFactory();
  }

  private static @NonNull X509CertificateHolder createX509(@NonNull KeyPair keyPair) throws OperatorCreationException {
    Date startDate = new Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000);
    Date endDate   = new Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000);

    X500NameBuilder nameBuilder = new X500NameBuilder(BCStyle.INSTANCE);
    nameBuilder.addRDN(BCStyle.C, "United States");
    nameBuilder.addRDN(BCStyle.ST, "California");
    nameBuilder.addRDN(BCStyle.L, "San Francisco");
    nameBuilder.addRDN(BCStyle.O, "Signal Foundation");
    nameBuilder.addRDN(BCStyle.CN, "SignalTransfer");

    X500Name             x500Name             = nameBuilder.build();
    BigInteger           serialNumber         = BigInteger.valueOf(new SecureRandom().nextLong()).abs();
    SubjectPublicKeyInfo subjectPublicKeyInfo = SubjectPublicKeyInfo.getInstance(keyPair.getPublic().getEncoded());

    X509v3CertificateBuilder certificateBuilder = new X509v3CertificateBuilder(x500Name,
                                                                               serialNumber,
                                                                               startDate,
                                                                               endDate,
                                                                               x500Name,
                                                                               subjectPublicKeyInfo);

    Security.addProvider(new BouncyCastleProvider());
    ContentSigner signer = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM).setProvider(BouncyCastleProvider.PROVIDER_NAME)
                                                                           .build(keyPair.getPrivate());
    Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME);

    return certificateBuilder.build(signer);
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
