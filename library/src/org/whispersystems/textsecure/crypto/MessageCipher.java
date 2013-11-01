/**
 * Copyright (C) 2011 Whisper Systems
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
package org.whispersystems.textsecure.crypto;

import android.content.Context;

import org.whispersystems.textsecure.crypto.SessionCipher.SessionCipherContext;
import org.whispersystems.textsecure.crypto.protocol.CiphertextMessage;
import org.whispersystems.textsecure.storage.CanonicalRecipientAddress;

/**
 * Parses and serializes the encrypted message format.
 *
 * @author Moxie Marlinspike
 */

public class MessageCipher {

  private final Context          context;
  private final MasterSecret     masterSecret;
  private final IdentityKeyPair  localIdentityKey;

  public MessageCipher(Context context, MasterSecret masterSecret, IdentityKeyPair localIdentityKey) {
    this.context          = context.getApplicationContext();
    this.masterSecret     = masterSecret;
    this.localIdentityKey = localIdentityKey;
  }

  public CiphertextMessage encrypt(CanonicalRecipientAddress recipient, byte[] paddedBody) {
    synchronized (SessionCipher.CIPHER_LOCK) {
      SessionCipher        sessionCipher  = new SessionCipher();
      SessionCipherContext sessionContext = sessionCipher.getEncryptionContext(context, masterSecret, localIdentityKey, recipient);
      byte[]               ciphertextBody = sessionCipher.encrypt(sessionContext, paddedBody);

      return new CiphertextMessage(sessionContext, ciphertextBody);
    }
  }

  public byte[] decrypt(CanonicalRecipientAddress recipient, byte[] ciphertext)
      throws InvalidMessageException
  {
    synchronized (SessionCipher.CIPHER_LOCK) {
      try {
        CiphertextMessage message = new CiphertextMessage(ciphertext);

        int       messageVersion    = message.getCurrentVersion();
        int       supportedVersion  = message.getSupportedVersion();
        int       negotiatedVersion = Math.min(supportedVersion, CiphertextMessage.SUPPORTED_VERSION);
        int       senderKeyId       = message.getSenderKeyId();
        int       receiverKeyId     = message.getReceiverKeyId();
        PublicKey nextRemoteKey     = new PublicKey(message.getNextKeyBytes());
        int       counter           = message.getCounter();
        byte[]    body              = message.getBody();

        SessionCipher        sessionCipher     = new SessionCipher();
        SessionCipherContext sessionContext    = sessionCipher.getDecryptionContext(context, masterSecret,
                                                                                    localIdentityKey,
                                                                                    recipient, senderKeyId,
                                                                                    receiverKeyId,
                                                                                    nextRemoteKey,
                                                                                    counter,
                                                                                    messageVersion,
                                                                                    negotiatedVersion);

        message.verifyMac(sessionContext);

        return sessionCipher.decrypt(sessionContext, body);
      } catch (InvalidKeyException e) {
        throw new InvalidMessageException(e);
      }
    }
  }

}
