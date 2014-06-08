/**
 * Copyright (C) 2014 Open WhisperSystems
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
package org.thoughtcrime.securesms.sms;

import org.whispersystems.textsecure.crypto.IdentityKey;
import org.whispersystems.textsecure.util.Base64;

public class IncomingIdentityUpdateMessage extends IncomingKeyExchangeMessage {

  public IncomingIdentityUpdateMessage(IncomingTextMessage base, String newBody) {
    super(base, newBody);
  }

  @Override
  public IncomingIdentityUpdateMessage withMessageBody(String messageBody) {
    return new IncomingIdentityUpdateMessage(this, messageBody);
  }

  @Override
  public boolean isIdentityUpdate() {
    return true;
  }

  public static IncomingIdentityUpdateMessage createFor(String sender, IdentityKey identityKey) {
    return createFor(sender, identityKey, null);
  }

  public static IncomingIdentityUpdateMessage createFor(String sender, IdentityKey identityKey, String groupId) {
    IncomingTextMessage base = new IncomingTextMessage(sender, groupId);
    return new IncomingIdentityUpdateMessage(base, Base64.encodeBytesWithoutPadding(identityKey.serialize()));
  }
}
