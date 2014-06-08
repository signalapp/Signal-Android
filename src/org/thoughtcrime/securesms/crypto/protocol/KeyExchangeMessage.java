/**
 * Copyright (C) 2011-2012 Whisper Systems
 * Copyright (C) 2013-2014 Open WhisperSystems
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
import org.whispersystems.textsecure.crypto.LegacyMessageException;


public abstract class KeyExchangeMessage {
  public abstract IdentityKey getIdentityKey();
  public abstract boolean     hasIdentityKey();
  public abstract int         getMaxVersion();
  public abstract int         getVersion();

  public static KeyExchangeMessage createFor(String rawMessage)
      throws InvalidMessageException, InvalidKeyException, InvalidVersionException, LegacyMessageException
  {
    return new KeyExchangeMessageV2(rawMessage);
  }
}