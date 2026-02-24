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
import org.signal.libsignal.protocol.ecc.ECPublicKey;
import org.signal.core.util.Base64;

import java.io.IOException;

public class PreKeyEntity {

  @JsonProperty
  private int keyId;

  @JsonProperty
  @JsonSerialize(using = ECPublicKeySerializer.class)
  @JsonDeserialize(using = ECPublicKeyDeserializer.class)
  private ECPublicKey publicKey;

  public PreKeyEntity() {}

  public PreKeyEntity(int keyId, ECPublicKey publicKey) {
    this.keyId     = keyId;
    this.publicKey = publicKey;
  }

  public int getKeyId() {
    return keyId;
  }

  public ECPublicKey getPublicKey() {
    return publicKey;
  }

  private static class ECPublicKeySerializer extends JsonSerializer<ECPublicKey> {
    @Override
    public void serialize(ECPublicKey value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
      gen.writeString(Base64.encodeWithoutPadding(value.serialize()));
    }
  }

  private static class ECPublicKeyDeserializer extends JsonDeserializer<ECPublicKey> {
    @Override
    public ECPublicKey deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
      try {
        return new ECPublicKey(Base64.decode(p.getValueAsString()));
      } catch (InvalidKeyException e) {
        throw new IOException(e);
      }
    }
  }
}
