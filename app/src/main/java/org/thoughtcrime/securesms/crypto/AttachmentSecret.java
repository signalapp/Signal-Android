package org.thoughtcrime.securesms.crypto;


import androidx.annotation.NonNull;
import android.util.Base64;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

/**
 * Encapsulates the key material used to encrypt attachments on disk.
 *
 * There are two logical pieces of material, a deprecated set of keys used to encrypt
 * legacy attachments, and a key that is used to encrypt attachments going forward.
 */
public class AttachmentSecret {

  @JsonProperty
  @JsonSerialize(using = ByteArraySerializer.class)
  @JsonDeserialize(using = ByteArrayDeserializer.class)
  private byte[] classicCipherKey;

  @JsonProperty
  @JsonSerialize(using = ByteArraySerializer.class)
  @JsonDeserialize(using = ByteArrayDeserializer.class)
  private byte[] classicMacKey;

  @JsonProperty
  @JsonSerialize(using = ByteArraySerializer.class)
  @JsonDeserialize(using = ByteArrayDeserializer.class)
  private byte[] modernKey;

  public AttachmentSecret(byte[] classicCipherKey, byte[] classicMacKey, byte[] modernKey)
  {
    this.classicCipherKey = classicCipherKey;
    this.classicMacKey    = classicMacKey;
    this.modernKey        = modernKey;
  }

  @SuppressWarnings("unused")
  public AttachmentSecret() {

  }

  @JsonIgnore
  byte[] getClassicCipherKey() {
    return classicCipherKey;
  }

  @JsonIgnore
  byte[] getClassicMacKey() {
    return classicMacKey;
  }

  @JsonIgnore
  public byte[] getModernKey() {
    return modernKey;
  }

  @JsonIgnore
  void setClassicCipherKey(byte[] classicCipherKey) {
    this.classicCipherKey = classicCipherKey;
  }

  @JsonIgnore
  void setClassicMacKey(byte[] classicMacKey) {
    this.classicMacKey = classicMacKey;
  }

  public String serialize() {
    try {
      return JsonUtils.toJson(this);
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  static AttachmentSecret fromString(@NonNull String value) {
    try {
      return JsonUtils.fromJson(value, AttachmentSecret.class);
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
