package org.whispersystems.textsecure.push;

import com.google.thoughtcrimegson.GsonBuilder;
import com.google.thoughtcrimegson.JsonDeserializationContext;
import com.google.thoughtcrimegson.JsonDeserializer;
import com.google.thoughtcrimegson.JsonElement;
import com.google.thoughtcrimegson.JsonParseException;
import com.google.thoughtcrimegson.JsonPrimitive;
import com.google.thoughtcrimegson.JsonSerializationContext;
import com.google.thoughtcrimegson.JsonSerializer;

import org.whispersystems.libaxolotl.IdentityKey;
import org.whispersystems.libaxolotl.InvalidKeyException;
import org.whispersystems.textsecure.util.Base64;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;

public class PreKeyResponse {

  private IdentityKey              identityKey;
  private List<PreKeyResponseItem> devices;

  public IdentityKey getIdentityKey() {
    return identityKey;
  }

  public List<PreKeyResponseItem> getDevices() {
    return devices;
  }

  public static PreKeyResponse fromJson(String serialized) {
    GsonBuilder builder = new GsonBuilder();
    return PreKeyResponseItem.forBuilder(builder)
                             .registerTypeAdapter(IdentityKey.class, new IdentityKeyJsonAdapter())
                             .create().fromJson(serialized, PreKeyResponse.class);
  }

  public static class IdentityKeyJsonAdapter
      implements JsonSerializer<IdentityKey>, JsonDeserializer<IdentityKey>
  {
    @Override
    public JsonElement serialize(IdentityKey identityKey, Type type,
                                 JsonSerializationContext jsonSerializationContext)
    {
      return new JsonPrimitive(Base64.encodeBytesWithoutPadding(identityKey.serialize()));
    }

    @Override
    public IdentityKey deserialize(JsonElement jsonElement, Type type,
                                   JsonDeserializationContext jsonDeserializationContext)
        throws JsonParseException
    {
      try {
        return new IdentityKey(Base64.decodeWithoutPadding(jsonElement.getAsJsonPrimitive().getAsString()), 0);
      } catch (InvalidKeyException | IOException e) {
        throw new JsonParseException(e);
      }
    }
  }



}
