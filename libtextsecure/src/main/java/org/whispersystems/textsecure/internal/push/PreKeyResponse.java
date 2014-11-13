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
import org.whispersystems.textsecure.internal.util.Base64;

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
