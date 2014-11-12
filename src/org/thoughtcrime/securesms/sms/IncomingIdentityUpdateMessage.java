package org.thoughtcrime.securesms.sms;

import org.thoughtcrime.securesms.util.Base64;
import org.whispersystems.libaxolotl.IdentityKey;

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
