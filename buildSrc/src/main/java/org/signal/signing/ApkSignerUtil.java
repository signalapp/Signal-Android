package org.signal.signing;

import com.android.apksig.ApkSigner;
import com.android.apksig.apk.ApkFormatException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;

public class ApkSignerUtil {

  private final String providerClass;

  private final String providerArgument;

  private final String keyStoreType;

  private final String keyStorePassword;


  public ApkSignerUtil(String providerClass, String providerArgument, String keyStoreType, String keyStorePassword) {
    this.providerClass    = providerClass;
    this.providerArgument = providerArgument;
    this.keyStoreType     = keyStoreType;
    this.keyStorePassword = keyStorePassword;
  }

  public void calculateSignature(String inputApkFile, String outputApkFile)
      throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException, UnrecoverableKeyException, ApkFormatException, InvalidKeyException, SignatureException
  {
    System.out.println("Running calculateSignature()...");

    if (providerClass != null) {
      installProvider(providerClass, providerArgument);
    }

    ApkSigner apkSigner = new ApkSigner.Builder(Collections.singletonList(loadKeyStore(keyStoreType, keyStorePassword)))
        .setV1SigningEnabled(true)
        .setV2SigningEnabled(true)
        .setInputApk(new File(inputApkFile))
        .setOutputApk(new File(outputApkFile))
        .setOtherSignersSignaturesPreserved(false)
        .build();

    apkSigner.sign();
  }

  private void installProvider(String providerName, String providerArgument) {
    try {
      Class<?> providerClass = Class.forName(providerName);

      if (!Provider.class.isAssignableFrom(providerClass)) {
        throw new IllegalArgumentException("JCA Provider class " + providerClass + " not subclass of " + Provider.class.getName());
      }

      Provider provider;

      if (providerArgument != null) {
        provider = (Provider) providerClass.getConstructor(String.class).newInstance(providerArgument);
      } else {
        provider = (Provider) providerClass.getConstructor().newInstance();
      }

      Security.addProvider(provider);
    } catch (ClassNotFoundException | InstantiationException | InvocationTargetException | IllegalAccessException | NoSuchMethodException e) {
      throw new IllegalArgumentException(e);
    }
  }

  private ApkSigner.SignerConfig loadKeyStore(String keyStoreType, String keyStorePassword) throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException, UnrecoverableKeyException {
    KeyStore keyStoreEntity = KeyStore.getInstance(keyStoreType == null ? KeyStore.getDefaultType() : keyStoreType);
    char[]   password       = getPassword(keyStorePassword);
    keyStoreEntity.load(null, password);

    Enumeration<String> aliases  = keyStoreEntity.aliases();
    String              keyAlias = null;

    while (aliases != null && aliases.hasMoreElements()) {
      String alias = aliases.nextElement();
      if (keyStoreEntity.isKeyEntry(alias)) {
        keyAlias = alias;
        break;
      }
    }

    if (keyAlias == null) {
      throw new IllegalArgumentException("Keystore has no key entries!");
    }

    PrivateKey    privateKey   = (PrivateKey) keyStoreEntity.getKey(keyAlias, password);
    Certificate[] certificates = keyStoreEntity.getCertificateChain(keyAlias);

    if (certificates == null || certificates.length == 0) {
      throw new IllegalArgumentException("Unable to load certificates!");
    }

    List<X509Certificate> results = new LinkedList<>();

    for (Certificate certificate : certificates) {
      results.add((X509Certificate)certificate);
    }


    return new ApkSigner.SignerConfig.Builder("Signal Signer", privateKey, results).build();
  }

  private char[] getPassword(String encoded) throws IOException {
    if (encoded.startsWith("file:")) {
      String         name     = encoded.substring("file:".length());
      BufferedReader reader   = new BufferedReader(new FileReader(new File(name)));
      String         password = reader.readLine();

      if (password.length() == 0) {
        throw new IOException("Failed to read password from file: " + name);
      }

      return password.toCharArray();
    } else {
      return encoded.toCharArray();
    }
  }

}
