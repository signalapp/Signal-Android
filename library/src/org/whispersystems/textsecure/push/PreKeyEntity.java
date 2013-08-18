package org.whispersystems.textsecure.push;

import com.google.thoughtcrimegson.GsonBuilder;
import com.google.thoughtcrimegson.JsonDeserializationContext;
import com.google.thoughtcrimegson.JsonDeserializer;
import com.google.thoughtcrimegson.JsonElement;
import com.google.thoughtcrimegson.JsonParseException;
import com.google.thoughtcrimegson.JsonPrimitive;
import com.google.thoughtcrimegson.JsonSerializationContext;
import com.google.thoughtcrimegson.JsonSerializer;
import org.whispersystems.textsecure.crypto.IdentityKey;
import org.whispersystems.textsecure.crypto.InvalidKeyException;
import org.whispersystems.textsecure.crypto.PreKeyPublic;
import org.whispersystems.textsecure.util.Base64;

import java.io.IOException;
import java.lang.reflect.Type;

public class PreKeyEntity {

  private long         keyId;
  private PreKeyPublic publicKey;
  private IdentityKey  identityKey;

  public PreKeyEntity(long keyId, PreKeyPublic publicKey, IdentityKey identityKey) {
    this.keyId       = keyId;
    this.publicKey   = publicKey;
    this.identityKey = identityKey;
  }

  public long getKeyId() {
    return keyId;
  }

  public PreKeyPublic getPublicKey() {
    return publicKey;
  }

  public IdentityKey getIdentityKey() {
    return identityKey;
  }

  public static String toJson(PreKeyEntity entity) {
    return getBuilder().create().toJson(entity);
  }

  public static PreKeyEntity fromJson(String encoded) {
    return getBuilder().create().fromJson(encoded, PreKeyEntity.class);
  }

  public static GsonBuilder getBuilder() {
    GsonBuilder builder = new GsonBuilder();
    builder.registerTypeAdapter(PreKeyPublic.class, new PreKeyPublicJsonAdapter());
    builder.registerTypeAdapter(IdentityKey.class, new IdentityKeyJsonAdapter());

    return builder;
  }

  private static class PreKeyPublicJsonAdapter
      implements JsonSerializer<PreKeyPublic>, JsonDeserializer<PreKeyPublic>
  {
    @Override
    public JsonElement serialize(PreKeyPublic preKeyPublic, Type type,
                                 JsonSerializationContext jsonSerializationContext)
    {
      return new JsonPrimitive(Base64.encodeBytesWithoutPadding(preKeyPublic.serialize()));
    }

    @Override
    public PreKeyPublic deserialize(JsonElement jsonElement, Type type,
                                    JsonDeserializationContext jsonDeserializationContext)
        throws JsonParseException
    {
      try {
        return new PreKeyPublic(Base64.decodeWithoutPadding(jsonElement.getAsJsonPrimitive().getAsString()), 0);
      } catch (InvalidKeyException e) {
        throw new JsonParseException(e);
      } catch (IOException e) {
        throw new JsonParseException(e);
      }
    }
  }

  private static class IdentityKeyJsonAdapter
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
      } catch (InvalidKeyException e) {
        throw new JsonParseException(e);
      } catch (IOException e) {
        throw new JsonParseException(e);
      }
    }
  }

}
