package org.whispersystems.textsecure.crypto;

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
import org.whispersystems.libaxolotl.state.AxolotlStore;
import org.whispersystems.textsecure.push.PushAddress;
import org.whispersystems.textsecure.push.PushTransportDetails;

public class TextSecureCipher {

  private final SessionCipher    sessionCipher;
  private final TransportDetails transportDetails;

  public TextSecureCipher(AxolotlStore axolotlStore, PushAddress pushAddress) {
    int sessionVersion = axolotlStore.loadSession(pushAddress.getRecipientId(),
                                                  pushAddress.getDeviceId())
                                     .getSessionState().getSessionVersion();

    this.transportDetails = new PushTransportDetails(sessionVersion);
    this.sessionCipher    = new SessionCipher(axolotlStore, pushAddress.getRecipientId(),
                                              pushAddress.getDeviceId());
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

