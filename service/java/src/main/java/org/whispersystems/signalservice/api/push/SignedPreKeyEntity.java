/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.push;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.signalservice.internal.push.PreKeyEntity;
import org.whispersystems.signalservice.internal.util.Base64;

import java.io.IOException;

public class SignedPreKeyEntity extends PreKeyEntity {

  @JsonProperty
  @JsonSerialize(using = ByteArraySerializer.class)
  @JsonDeserialize(using = ByteArrayDeserializer.class)
  private byte[] signature;

  public SignedPreKeyEntity() {}

  public SignedPreKeyEntity(int keyId, ECPublicKey publicKey, byte[] signature) {
    super(keyId, publicKey);
    this.signature = signature;
  }

  public byte[] getSignature() {
    return signature;
  }

  private static class ByteArraySerializer extends JsonSerializer<byte[]> {
    @Override
    public void serialize(byte[] value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
      gen.writeString(Base64.encodeBytesWithoutPadding(value));
    }
  }

  private static class ByteArrayDeserializer extends JsonDeserializer<byte[]> {

    @Override
    public byte[] deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
      return Base64.decodeWithoutPadding(p.getValueAsString());
    }
  }
}
