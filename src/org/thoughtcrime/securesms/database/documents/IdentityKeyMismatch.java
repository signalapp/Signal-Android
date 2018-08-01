package org.thoughtcrime.securesms.database.documents;

import org.thoughtcrime.securesms.logging.Log;

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

import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.util.Base64;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.InvalidKeyException;

import java.io.IOException;

public class IdentityKeyMismatch {

  private static final String TAG = IdentityKeyMismatch.class.getSimpleName();

  @JsonProperty(value = "a")
  private String address;

  @JsonProperty(value = "k")
  @JsonSerialize(using = IdentityKeySerializer.class)
  @JsonDeserialize(using = IdentityKeyDeserializer.class)
  private IdentityKey identityKey;

  public IdentityKeyMismatch() {}

  public IdentityKeyMismatch(Address address, IdentityKey identityKey) {
    this.address     = address.serialize();
    this.identityKey = identityKey;
  }

  @JsonIgnore
  public Address getAddress() {
    return Address.fromSerialized(address);
  }

  public IdentityKey getIdentityKey() {
    return identityKey;
  }

  @Override
  public boolean equals(Object other) {
    if (other == null || !(other instanceof IdentityKeyMismatch)) {
      return false;
    }

    IdentityKeyMismatch that = (IdentityKeyMismatch)other;
    return that.address.equals(this.address) && that.identityKey.equals(this.identityKey);
  }

  @Override
  public int hashCode() {
    return address.hashCode() ^ identityKey.hashCode();
  }

  private static class IdentityKeySerializer extends JsonSerializer<IdentityKey> {
    @Override
    public void serialize(IdentityKey value, JsonGenerator jsonGenerator, SerializerProvider serializers)
        throws IOException
    {
      jsonGenerator.writeString(Base64.encodeBytes(value.serialize()));
    }
  }

  private static class IdentityKeyDeserializer extends JsonDeserializer<IdentityKey> {
    @Override
    public IdentityKey deserialize(JsonParser jsonParser, DeserializationContext ctxt)
        throws IOException
    {
      try {
        return new IdentityKey(Base64.decode(jsonParser.getValueAsString()), 0);
      } catch (InvalidKeyException e) {
        Log.w(TAG, e);
        throw new IOException(e);
      }
    }
  }
}
