package org.whispersystems.textsecure.crypto;

import com.google.protobuf.InvalidProtocolBufferException;

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
import org.whispersystems.textsecure.push.PushTransportDetails;

import java.util.LinkedList;
import java.util.List;

import static org.whispersystems.textsecure.push.PushMessageProtos.PushMessageContent;
import static org.whispersystems.textsecure.push.PushMessageProtos.PushMessageContent.GroupContext.Type.DELIVER;

public class TextSecureCipher {

  private final SessionCipher    sessionCipher;
  private final TransportDetails transportDetails;

  public TextSecureCipher(AxolotlStore axolotlStore, long recipientId, int deviceId) {
    int sessionVersion = axolotlStore.loadSession(recipientId, deviceId)
                                     .getSessionState().getSessionVersion();

    this.transportDetails = new PushTransportDetails(sessionVersion);
    this.sessionCipher    = new SessionCipher(axolotlStore, recipientId, deviceId);
  }

  public CiphertextMessage encrypt(byte[] unpaddedMessage) {
    return sessionCipher.encrypt(transportDetails.getPaddedMessageBody(unpaddedMessage));
  }

  public TextSecureMessage decrypt(TextSecureEnvelope envelope)
      throws InvalidVersionException, InvalidMessageException, InvalidKeyException,
             DuplicateMessageException, InvalidKeyIdException, UntrustedIdentityException,
             LegacyMessageException, NoSessionException
  {
    try {
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

      PushMessageContent content = PushMessageContent.parseFrom(transportDetails.getStrippedPaddingMessageBody(paddedMessage));
      return createTextSecureMessage(envelope, content);
    } catch (InvalidProtocolBufferException e) {
      throw new InvalidMessageException(e);
    }
  }

  public int getRemoteRegistrationId() {
    return sessionCipher.getRemoteRegistrationId();
  }

  private TextSecureMessage createTextSecureMessage(TextSecureEnvelope envelope, PushMessageContent content) {
    TextSecureGroup            groupInfo   = createGroupInfo(envelope, content);
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
                                 content.getBody(), secure, endSession);
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

