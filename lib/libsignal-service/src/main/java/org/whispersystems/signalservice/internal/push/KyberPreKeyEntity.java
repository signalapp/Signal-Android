/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.kem.KEMPublicKey;
import org.signal.core.util.Base64;

import java.io.IOException;

public class KyberPreKeyEntity {

  @JsonProperty
  private int keyId;

  @JsonProperty
  @JsonSerialize(using = KEMPublicKeySerializer.class)
  @JsonDeserialize(using = KEMPublicKeyDeserializer.class)
  private KEMPublicKey publicKey;

  @JsonProperty
  @JsonSerialize(using = ByteArraySerializer.class)
  @JsonDeserialize(using = ByteArrayDeserializer.class)
  private byte[] signature;

  public KyberPreKeyEntity() {}

  public KyberPreKeyEntity(int keyId, KEMPublicKey publicKey, byte[] signature) {
    this.keyId     = keyId;
    this.publicKey = publicKey;
    this.signature = signature;
  }

  public int getKeyId() {
    return keyId;
  }

  public KEMPublicKey getPublicKey() {
    return publicKey;
  }

  public byte[] getSignature() {
    return signature;
  }

  private static class KEMPublicKeySerializer extends JsonSerializer<KEMPublicKey> {
    @Override
    public void serialize(KEMPublicKey value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
      gen.writeString(Base64.encodeWithoutPadding(value.serialize()));
    }
  }

  private static class KEMPublicKeyDeserializer extends JsonDeserializer<KEMPublicKey> {
    @Override
    public KEMPublicKey deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
      try {
        return new KEMPublicKey(Base64.decode(p.getValueAsString()), 0);
      } catch (InvalidKeyException e) {
        throw new IOException(e);
      }
    }
  }

  private static class ByteArraySerializer extends JsonSerializer<byte[]> {
    @Override
    public void serialize(byte[] value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
      gen.writeString(Base64.encodeWithoutPadding(value));
    }
  }

  private static class ByteArrayDeserializer extends JsonDeserializer<byte[]> {

    @Override
    public byte[] deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
      return Base64.decode(p.getValueAsString());
    }
  }
}
