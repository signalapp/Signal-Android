package org.thoughtcrime.securesms.database.documents;

import android.text.TextUtils;

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

import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.IdentityKey;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.signal.core.util.Base64;

import java.io.IOException;
import java.util.Objects;

public class IdentityKeyMismatch {

  private static final String TAG = Log.tag(IdentityKeyMismatch.class);

  /** DEPRECATED */
  @JsonProperty(value = "a")
  private String address;

  @JsonProperty(value = "r")
  private String recipientId;

  @JsonProperty(value = "k")
  @JsonSerialize(using = IdentityKeySerializer.class)
  @JsonDeserialize(using = IdentityKeyDeserializer.class)
  private IdentityKey identityKey;

  public IdentityKeyMismatch() {}

  public IdentityKeyMismatch(RecipientId recipientId, IdentityKey identityKey) {
    this.recipientId = recipientId.serialize();
    this.address     = "";
    this.identityKey = identityKey;
  }

  @JsonIgnore
  public RecipientId getRecipientId() {
    if (!TextUtils.isEmpty(recipientId)) {
      return RecipientId.from(recipientId);
    } else {
      Recipient recipient = Recipient.external(address);
      if (recipient != null) {
        return recipient.getId();
      } else {
        return RecipientId.UNKNOWN;
      }
    }
  }

  public IdentityKey getIdentityKey() {
    return identityKey;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    IdentityKeyMismatch that = (IdentityKeyMismatch) o;
    return Objects.equals(address, that.address) &&
        Objects.equals(recipientId, that.recipientId) &&
        Objects.equals(identityKey, that.identityKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(address, recipientId, identityKey);
  }

  private static class IdentityKeySerializer extends JsonSerializer<IdentityKey> {
    @Override
    public void serialize(IdentityKey value, JsonGenerator jsonGenerator, SerializerProvider serializers)
        throws IOException
    {
      jsonGenerator.writeString(Base64.encodeWithPadding(value.serialize()));
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
