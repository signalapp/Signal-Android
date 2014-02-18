package org.whispersystems.textsecure.push;

import com.google.thoughtcrimegson.GsonBuilder;
import com.google.thoughtcrimegson.JsonDeserializationContext;
import com.google.thoughtcrimegson.JsonDeserializer;
import com.google.thoughtcrimegson.JsonElement;
import com.google.thoughtcrimegson.JsonParseException;
import com.google.thoughtcrimegson.JsonPrimitive;
import com.google.thoughtcrimegson.JsonSerializationContext;
import com.google.thoughtcrimegson.JsonSerializer;
import com.google.thoughtcrimegson.annotations.Expose;

import org.whispersystems.textsecure.crypto.IdentityKey;
import org.whispersystems.textsecure.crypto.InvalidKeyException;
import org.whispersystems.textsecure.crypto.ecc.Curve;
import org.whispersystems.textsecure.crypto.ecc.ECPublicKey;
import org.whispersystems.textsecure.util.Base64;

import java.io.IOException;
import java.lang.reflect.Type;

public class PreKeyEntity {

  @Expose(serialize = false)
  private int         deviceId;

  private int         keyId;
  private ECPublicKey publicKey;
  private IdentityKey identityKey;
  private int         registrationId;

  public PreKeyEntity(int keyId, ECPublicKey publicKey, IdentityKey identityKey) {
    this.keyId          = keyId;
    this.publicKey      = publicKey;
    this.identityKey    = identityKey;
    this.registrationId = registrationId;
  }

  public int getDeviceId() {
    return deviceId;
  }

  public int getKeyId() {
    return keyId;
  }

  public ECPublicKey getPublicKey() {
    return publicKey;
  }

  public IdentityKey getIdentityKey() {
    return identityKey;
  }

  public int getRegistrationId() {
    return registrationId;
  }

  public static String toJson(PreKeyEntity entity) {
    return getBuilder().create().toJson(entity);
  }

  public static PreKeyEntity fromJson(String encoded) {
    return getBuilder().create().fromJson(encoded, PreKeyEntity.class);
  }

  public static GsonBuilder getBuilder() {
    GsonBuilder builder = new GsonBuilder();
    builder.registerTypeAdapter(ECPublicKey.class, new ECPublicKeyJsonAdapter());
    builder.registerTypeAdapter(IdentityKey.class, new IdentityKeyJsonAdapter());

    return builder;
  }


  private static class ECPublicKeyJsonAdapter
      implements JsonSerializer<ECPublicKey>, JsonDeserializer<ECPublicKey>
  {
    @Override
    public JsonElement serialize(ECPublicKey preKeyPublic, Type type,
                                 JsonSerializationContext jsonSerializationContext)
    {
      return new JsonPrimitive(Base64.encodeBytesWithoutPadding(preKeyPublic.serialize()));
    }

    @Override
    public ECPublicKey deserialize(JsonElement jsonElement, Type type,
                                    JsonDeserializationContext jsonDeserializationContext)
        throws JsonParseException
    {
      try {
        return Curve.decodePoint(Base64.decodeWithoutPadding(jsonElement.getAsJsonPrimitive().getAsString()), 0);
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
