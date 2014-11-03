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
import org.whispersystems.libaxolotl.ecc.Curve;
import org.whispersystems.libaxolotl.ecc.ECPublicKey;
import org.whispersystems.textsecure.util.Base64;

import java.io.IOException;
import java.lang.reflect.Type;

public class PreKeyEntity {

  private int         keyId;
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

  public static GsonBuilder forBuilder(GsonBuilder builder) {
    return builder.registerTypeAdapter(ECPublicKey.class, new ECPublicKeyJsonAdapter());
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
      } catch (InvalidKeyException | IOException e) {
        throw new JsonParseException(e);
      }
    }
  }

}
