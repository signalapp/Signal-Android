/**
 * Copyright (C) 2014 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.whispersystems.textsecure.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import org.whispersystems.libaxolotl.InvalidKeyException;
import org.whispersystems.libaxolotl.ecc.Curve;
import org.whispersystems.libaxolotl.ecc.ECPublicKey;
import org.whispersystems.textsecure.internal.util.Base64;

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
      gen.writeString(Base64.encodeBytesWithoutPadding(value.serialize()));
    }
  }

  private static class ECPublicKeyDeserializer extends JsonDeserializer<ECPublicKey> {
    @Override
    public ECPublicKey deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
      try {
        return Curve.decodePoint(Base64.decodeWithoutPadding(p.getValueAsString()), 0);
      } catch (InvalidKeyException e) {
        throw new IOException(e);
      }
    }
  }
}
