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
package org.whispersystems.textsecure.api.crypto;

import com.google.protobuf.InvalidProtocolBufferException;

import org.whispersystems.libaxolotl.AxolotlAddress;
import org.whispersystems.libaxolotl.DuplicateMessageException;
import org.whispersystems.libaxolotl.InvalidKeyException;
import org.whispersystems.libaxolotl.InvalidKeyIdException;
import org.whispersystems.libaxolotl.InvalidMessageException;
import org.whispersystems.libaxolotl.InvalidVersionException;
import org.whispersystems.libaxolotl.LegacyMessageException;
import org.whispersystems.libaxolotl.NoSessionException;
import org.whispersystems.libaxolotl.SessionCipher;
import org.whispersystems.libaxolotl.UntrustedIdentityException;
import org.whispersystems.libaxolotl.protocol.CiphertextMessage;
import org.whispersystems.libaxolotl.protocol.PreKeyWhisperMessage;
import org.whispersystems.libaxolotl.protocol.WhisperMessage;
import org.whispersystems.libaxolotl.state.AxolotlStore;
import org.whispersystems.textsecure.api.messages.TextSecureAttachment;
import org.whispersystems.textsecure.api.messages.TextSecureAttachmentPointer;
import org.whispersystems.textsecure.api.messages.TextSecureEnvelope;
import org.whispersystems.textsecure.api.messages.TextSecureGroup;
import org.whispersystems.textsecure.api.messages.TextSecureMessage;
import org.whispersystems.textsecure.api.messages.TextSecureSyncContext;
import org.whispersystems.textsecure.internal.push.OutgoingPushMessage;
import org.whispersystems.textsecure.internal.push.PushMessageProtos;
import org.whispersystems.textsecure.internal.push.PushTransportDetails;
import org.whispersystems.textsecure.internal.util.Base64;

import java.util.LinkedList;
import java.util.List;

import static org.whispersystems.textsecure.internal.push.PushMessageProtos.IncomingPushMessageSignal.Type;
import static org.whispersystems.textsecure.internal.push.PushMessageProtos.PushMessageContent;
import static org.whispersystems.textsecure.internal.push.PushMessageProtos.PushMessageContent.GroupContext.Type.DELIVER;

/**
 * This is used to decrypt received {@link org.whispersystems.textsecure.api.messages.TextSecureEnvelope}s.
 *
 * @author Moxie Marlinspike
 */
public class TextSecureCipher {

  private final AxolotlStore axolotlStore;

  public TextSecureCipher(AxolotlStore axolotlStore) {
    this.axolotlStore = axolotlStore;
  }

  public OutgoingPushMessage encrypt(AxolotlAddress destination, byte[] unpaddedMessage) {
    SessionCipher        sessionCipher        = new SessionCipher(axolotlStore, destination);
    PushTransportDetails transportDetails     = new PushTransportDetails(sessionCipher.getSessionVersion());
    CiphertextMessage    message              = sessionCipher.encrypt(transportDetails.getPaddedMessageBody(unpaddedMessage));
    int                  remoteRegistrationId = sessionCipher.getRemoteRegistrationId();
    String               body                 = Base64.encodeBytes(message.serialize());

    int type;

    switch (message.getType()) {
      case CiphertextMessage.PREKEY_TYPE:  type = Type.PREKEY_BUNDLE_VALUE; break;
      case CiphertextMessage.WHISPER_TYPE: type = Type.CIPHERTEXT_VALUE;    break;
      default: throw new AssertionError("Bad type: " + message.getType());
    }

    return new OutgoingPushMessage(type, destination.getDeviceId(), remoteRegistrationId, body);
  }

  /**
   * Decrypt a received {@link org.whispersystems.textsecure.api.messages.TextSecureEnvelope}
   *
   * @param envelope The received TextSecureEnvelope
   * @return a decrypted TextSecureMessage
   * @throws InvalidVersionException
   * @throws InvalidMessageException
   * @throws InvalidKeyException
   * @throws DuplicateMessageException
   * @throws InvalidKeyIdException
   * @throws UntrustedIdentityException
   * @throws LegacyMessageException
   * @throws NoSessionException
   */
  public TextSecureMessage decrypt(TextSecureEnvelope envelope)
      throws InvalidVersionException, InvalidMessageException, InvalidKeyException,
             DuplicateMessageException, InvalidKeyIdException, UntrustedIdentityException,
             LegacyMessageException, NoSessionException
  {
    try {
      AxolotlAddress sourceAddress = new AxolotlAddress(envelope.getSource(), envelope.getSourceDevice());
      SessionCipher  sessionCipher = new SessionCipher(axolotlStore, sourceAddress);

      byte[] paddedMessage;

      if (envelope.isPreKeyWhisperMessage()) {
        paddedMessage = sessionCipher.decrypt(new PreKeyWhisperMessage(envelope.getMessage()));
      } else if (envelope.isWhisperMessage()) {
        paddedMessage = sessionCipher.decrypt(new WhisperMessage(envelope.getMessage()));
      } else if (envelope.isPlaintext()) {
        paddedMessage = envelope.getMessage();
      } else {
        throw new InvalidMessageException("Unknown type: " + envelope.getType());
      }

      PushTransportDetails transportDetails = new PushTransportDetails(sessionCipher.getSessionVersion());
      PushMessageContent   content          = PushMessageContent.parseFrom(transportDetails.getStrippedPaddingMessageBody(paddedMessage));

      return createTextSecureMessage(envelope, content);
    } catch (InvalidProtocolBufferException e) {
      throw new InvalidMessageException(e);
    }
  }

  private TextSecureMessage createTextSecureMessage(TextSecureEnvelope envelope, PushMessageContent content) {
    TextSecureGroup            groupInfo   = createGroupInfo(envelope, content);
    TextSecureSyncContext      syncContext = createSyncContext(content);
    List<TextSecureAttachment> attachments = new LinkedList<>();
    boolean                    endSession  = ((content.getFlags() & PushMessageContent.Flags.END_SESSION_VALUE) != 0);
    boolean                    secure      = envelope.isWhisperMessage() || envelope.isPreKeyWhisperMessage();

    for (PushMessageContent.AttachmentPointer pointer : content.getAttachmentsList()) {
      attachments.add(new TextSecureAttachmentPointer(pointer.getId(),
                                                      pointer.getContentType(),
                                                      pointer.getKey().toByteArray(),
                                                      envelope.getRelay()));
    }

    return new TextSecureMessage(envelope.getTimestamp(), groupInfo, attachments,
                                 content.getBody(), syncContext, secure, endSession);
  }

  private TextSecureSyncContext createSyncContext(PushMessageContent content) {
    if (!content.hasSync()) return null;

    return new TextSecureSyncContext(content.getSync().getDestination(),
                                     content.getSync().getTimestamp());
  }

  private TextSecureGroup createGroupInfo(TextSecureEnvelope envelope, PushMessageContent content) {
    if (!content.hasGroup()) return null;

    TextSecureGroup.Type type;

    switch (content.getGroup().getType()) {
      case DELIVER: type = TextSecureGroup.Type.DELIVER; break;
      case UPDATE:  type = TextSecureGroup.Type.UPDATE;  break;
      case QUIT:    type = TextSecureGroup.Type.QUIT;    break;
      default:      type = TextSecureGroup.Type.UNKNOWN; break;
    }

    if (content.getGroup().getType() != DELIVER) {
      String                      name    = null;
      List<String>                members = null;
      TextSecureAttachmentPointer avatar  = null;

      if (content.getGroup().hasName()) {
        name = content.getGroup().getName();
      }

      if (content.getGroup().getMembersCount() > 0) {
        members = content.getGroup().getMembersList();
      }

      if (content.getGroup().hasAvatar()) {
        avatar = new TextSecureAttachmentPointer(content.getGroup().getAvatar().getId(),
                                                 content.getGroup().getAvatar().getContentType(),
                                                 content.getGroup().getAvatar().getKey().toByteArray(),
                                                 envelope.getRelay());
      }

      return new TextSecureGroup(type, content.getGroup().getId().toByteArray(), name, members, avatar);
    }

    return new TextSecureGroup(content.getGroup().getId().toByteArray());
  }


}

