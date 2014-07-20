package org.thoughtcrime.securesms.crypto;

import android.content.Context;

import org.whispersystems.libaxolotl.DuplicateMessageException;
import org.whispersystems.libaxolotl.InvalidKeyException;
import org.whispersystems.libaxolotl.InvalidKeyIdException;
import org.whispersystems.libaxolotl.InvalidMessageException;
import org.whispersystems.libaxolotl.LegacyMessageException;
import org.whispersystems.libaxolotl.NoSessionException;
import org.whispersystems.libaxolotl.SessionCipher;
import org.whispersystems.libaxolotl.UntrustedIdentityException;
import org.whispersystems.libaxolotl.protocol.CiphertextMessage;
import org.whispersystems.libaxolotl.protocol.PreKeyWhisperMessage;
import org.whispersystems.libaxolotl.protocol.WhisperMessage;
import org.whispersystems.libaxolotl.state.IdentityKeyStore;
import org.whispersystems.libaxolotl.state.PreKeyStore;
import org.whispersystems.libaxolotl.state.SessionStore;
import org.whispersystems.libaxolotl.state.SignedPreKeyStore;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.whispersystems.textsecure.crypto.TransportDetails;
import org.whispersystems.textsecure.storage.RecipientDevice;
import org.whispersystems.textsecure.storage.TextSecurePreKeyStore;
import org.whispersystems.textsecure.storage.TextSecureSessionStore;

public class TextSecureCipher {

  private final SessionCipher    sessionCipher;
  private final TransportDetails transportDetails;

  public TextSecureCipher(Context context, MasterSecret masterSecret,
                          RecipientDevice recipient, TransportDetails transportDetails)
  {
    SessionStore      sessionStore      = new TextSecureSessionStore(context, masterSecret);
    PreKeyStore       preKeyStore       = new TextSecurePreKeyStore(context, masterSecret);
    SignedPreKeyStore signedPreKeyStore = new TextSecurePreKeyStore(context, masterSecret);
    IdentityKeyStore  identityKeyStore  = new TextSecureIdentityKeyStore(context, masterSecret);

    this.transportDetails = transportDetails;
    this.sessionCipher    = new SessionCipher(sessionStore, preKeyStore, signedPreKeyStore, identityKeyStore,
                                              recipient.getRecipientId(), recipient.getDeviceId());
  }

  public CiphertextMessage encrypt(byte[] unpaddedMessage) {
    return sessionCipher.encrypt(transportDetails.getPaddedMessageBody(unpaddedMessage));
  }

  public byte[] decrypt(WhisperMessage message)
      throws DuplicateMessageException, LegacyMessageException, InvalidMessageException, NoSessionException
  {
    byte[] paddedMessage = sessionCipher.decrypt(message);
    return transportDetails.getStrippedPaddingMessageBody(paddedMessage);
  }

  public byte[] decrypt(PreKeyWhisperMessage message)
      throws InvalidKeyException, LegacyMessageException, InvalidMessageException,
             DuplicateMessageException, InvalidKeyIdException, UntrustedIdentityException, NoSessionException
  {
    byte[] paddedMessage = sessionCipher.decrypt(message);
    return transportDetails.getStrippedPaddingMessageBody(paddedMessage);
  }

  public int getRemoteRegistrationId() {
    return sessionCipher.getRemoteRegistrationId();
  }

}
