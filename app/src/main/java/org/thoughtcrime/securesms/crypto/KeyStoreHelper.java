package org.thoughtcrime.securesms.crypto;


import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import android.util.Base64;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import org.thoughtcrime.securesms.util.JsonUtils;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class KeyStoreHelper {

  private static final String ANDROID_KEY_STORE = "AndroidKeyStore";
  private static final String KEY_ALIAS         = "SignalSecret";

  @RequiresApi(Build.VERSION_CODES.M)
  public static SealedData seal(@NonNull byte[] input) {
    SecretKey secretKey = getOrCreateKeyStoreEntry();

    try {
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, secretKey);

      byte[] iv   = cipher.getIV();
      byte[] data = cipher.doFinal(input);

      return new SealedData(iv, data);
    } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
      throw new AssertionError(e);
    }
  }

  @RequiresApi(Build.VERSION_CODES.M)
  public static byte[] unseal(@NonNull SealedData sealedData) {
    SecretKey secretKey = getKeyStoreEntry();

    try {
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(128, sealedData.iv));

      return cipher.doFinal(sealedData.data);
    } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
      throw new AssertionError(e);
    }
  }

  @RequiresApi(Build.VERSION_CODES.M)
  private static SecretKey getOrCreateKeyStoreEntry() {
    if (hasKeyStoreEntry()) return getKeyStoreEntry();
    else                    return createKeyStoreEntry();
  }

  @RequiresApi(Build.VERSION_CODES.M)
  private static SecretKey createKeyStoreEntry() {
    try {
      KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE);
      KeyGenParameterSpec keyGenParameterSpec = new KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
          .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
          .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
          .build();

      keyGenerator.init(keyGenParameterSpec);

      return keyGenerator.generateKey();
    } catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidAlgorithmParameterException e) {
      throw new AssertionError(e);
    }
  }

  @RequiresApi(Build.VERSION_CODES.M)
  private static SecretKey getKeyStoreEntry() {
    try {
      KeyStore keyStore = KeyStore.getInstance(ANDROID_KEY_STORE);
      keyStore.load(null);

      return ((KeyStore.SecretKeyEntry) keyStore.getEntry(KEY_ALIAS, null)).getSecretKey();
    } catch (KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException | UnrecoverableEntryException e) {
      throw new AssertionError(e);
    }
  }

  @RequiresApi(Build.VERSION_CODES.M)
  private static boolean hasKeyStoreEntry() {
    try {
      KeyStore ks = KeyStore.getInstance(ANDROID_KEY_STORE);
      ks.load(null);

      return ks.containsAlias(KEY_ALIAS) && ks.entryInstanceOf(KEY_ALIAS, KeyStore.SecretKeyEntry.class);
    } catch (KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException e) {
      throw new AssertionError(e);
    }
  }

  public static class SealedData {

    @SuppressWarnings("unused")
    private static final String TAG = SealedData.class.getSimpleName();

    @JsonProperty
    @JsonSerialize(using = ByteArraySerializer.class)
    @JsonDeserialize(using = ByteArrayDeserializer.class)
    private byte[] iv;

    @JsonProperty
    @JsonSerialize(using = ByteArraySerializer.class)
    @JsonDeserialize(using = ByteArrayDeserializer.class)
    private byte[] data;

    SealedData(@NonNull byte[] iv, @NonNull byte[] data) {
      this.iv   = iv;
      this.data = data;
    }

    @SuppressWarnings("unused")
    public SealedData() {}

    public String serialize() {
      try {
        return JsonUtils.toJson(this);
      } catch (IOException e) {
        throw new AssertionError(e);
      }
    }

    public static SealedData fromString(@NonNull String value) {
      try {
        return JsonUtils.fromJson(value, SealedData.class);
      } catch (IOException e) {
        throw new AssertionError(e);
      }
    }

    private static class ByteArraySerializer extends JsonSerializer<byte[]> {
      @Override
      public void serialize(byte[] value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeString(Base64.encodeToString(value, Base64.NO_WRAP | Base64.NO_PADDING));
      }
    }

    private static class ByteArrayDeserializer extends JsonDeserializer<byte[]> {

      @Override
      public byte[] deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        return Base64.decode(p.getValueAsString(), Base64.NO_WRAP | Base64.NO_PADDING);
      }
    }

  }

}
