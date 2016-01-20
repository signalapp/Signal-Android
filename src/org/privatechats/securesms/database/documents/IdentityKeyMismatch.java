package org.thoughtcrime.securesms.database.documents;

import android.util.Log;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import org.thoughtcrime.securesms.util.Base64;
import org.whispersystems.libaxolotl.IdentityKey;
import org.whispersystems.libaxolotl.InvalidKeyException;

import java.io.IOException;

public class IdentityKeyMismatch {

  private static final String TAG = IdentityKeyMismatch.class.getSimpleName();

  @JsonProperty(value = "r")
  private long recipientId;

  @JsonProperty(value = "k")
  @JsonSerialize(using = IdentityKeySerializer.class)
  @JsonDeserialize(using = IdentityKeyDeserializer.class)
  private IdentityKey identityKey;

  public IdentityKeyMismatch() {}

  public IdentityKeyMismatch(long recipientId, IdentityKey identityKey) {
    this.recipientId = recipientId;
    this.identityKey = identityKey;
  }

  public long getRecipientId() {
    return recipientId;
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
    return that.recipientId == this.recipientId && that.identityKey.equals(this.identityKey);
  }

  @Override
  public int hashCode() {
    return (int)recipientId ^ identityKey.hashCode();
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
