/** 
 * Copyright (C) 2011 Whisper Systems
 * Copyright (C) 2013 Open Whisper Systems
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
package org.thoughtcrime.securesms.crypto.protocol;

import org.whispersystems.textsecure.crypto.IdentityKey;
import org.whispersystems.textsecure.crypto.InvalidKeyException;
import org.whispersystems.textsecure.crypto.InvalidMessageException;
import org.whispersystems.textsecure.crypto.InvalidVersionException;
import org.whispersystems.textsecure.crypto.PublicKey;
import org.whispersystems.textsecure.crypto.ecc.ECPublicKey;
import org.whispersystems.textsecure.crypto.protocol.CiphertextMessage;
import org.whispersystems.textsecure.util.Base64;
import org.whispersystems.textsecure.util.Conversions;

import java.io.IOException;


public abstract class KeyExchangeMessage {
  public abstract boolean     isLegacy();
  public abstract IdentityKey getIdentityKey();
  public abstract boolean     hasIdentityKey();
  public abstract int         getMaxVersion();
  public abstract int         getVersion();

  public static KeyExchangeMessage createFor(String rawMessage)
      throws InvalidMessageException, InvalidKeyException, InvalidVersionException
  {
    try {
      byte[] decodedMessage = Base64.decodeWithoutPadding(rawMessage);

      if (Conversions.highBitsToInt(decodedMessage[0]) <= CiphertextMessage.LEGACY_VERSION) {
        return new KeyExchangeMessageV1(rawMessage);
      } else {
        return new KeyExchangeMessageV2(rawMessage);
      }
    } catch (IOException e) {
      throw new InvalidMessageException(e);
    }
  }
}