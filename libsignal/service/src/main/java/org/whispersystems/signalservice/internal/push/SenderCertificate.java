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

import org.whispersystems.util.Base64;

import java.io.IOException;

public class SenderCertificate {

  @JsonProperty
  @JsonDeserialize(using = ByteArrayDesieralizer.class)
  @JsonSerialize(using = ByteArraySerializer.class)
  private byte[] certificate;

  public SenderCertificate() {}

  public byte[] getCertificate() {
    return certificate;
  }

  public static class ByteArraySerializer extends JsonSerializer<byte[]> {
    @Override
    public void serialize(byte[] value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
      gen.writeString(Base64.encodeBytes(value));
    }
  }

  public static class ByteArrayDesieralizer extends JsonDeserializer<byte[]> {

    @Override
    public byte[] deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
      return Base64.decode(p.getValueAsString());
    }
  }
}
