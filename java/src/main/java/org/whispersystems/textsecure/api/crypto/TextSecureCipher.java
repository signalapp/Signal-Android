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
import org.whispersystems.libaxolotl.util.guava.Optional;
import org.whispersystems.textsecure.api.messages.TextSecureAttachment;
import org.whispersystems.textsecure.api.messages.TextSecureAttachmentPointer;
import org.whispersystems.textsecure.api.messages.TextSecureContent;
import org.whispersystems.textsecure.api.messages.TextSecureDataMessage;
import org.whispersystems.textsecure.api.messages.TextSecureEnvelope;
import org.whispersystems.textsecure.api.messages.TextSecureGroup;
import org.whispersystems.textsecure.api.messages.multidevice.RequestMessage;
import org.whispersystems.textsecure.api.messages.multidevice.SentTranscriptMessage;
import org.whispersystems.textsecure.api.messages.multidevice.TextSecureSyncMessage;
import org.whispersystems.textsecure.api.push.TextSecureAddress;
import org.whispersystems.textsecure.internal.push.OutgoingPushMessage;
import org.whispersystems.textsecure.internal.push.PushTransportDetails;
import org.whispersystems.textsecure.internal.push.TextSecureProtos.AttachmentPointer;
import org.whispersystems.textsecure.internal.push.TextSecureProtos.Content;
import org.whispersystems.textsecure.internal.push.TextSecureProtos.DataMessage;
import org.whispersystems.textsecure.internal.push.TextSecureProtos.Envelope.Type;
import org.whispersystems.textsecure.internal.push.TextSecureProtos.SyncMessage;
import org.whispersystems.textsecure.internal.util.Base64;

import java.util.LinkedList;
import java.util.List;

import static org.whispersystems.textsecure.internal.push.TextSecureProtos.GroupContext.Type.DELIVER;

/**
 * This is used to decrypt received {@link org.whispersystems.textsecure.api.messages.TextSecureEnvelope}s.
 *
 * @author Moxie Marlinspike
 */
public class TextSecureCipher {

  private static final String TAG = TextSecureCipher.class.getSimpleName();

  private final AxolotlStore      axolotlStore;
  private final TextSecureAddress localAddress;

  public TextSecureCipher(TextSecureAddress localAddress, AxolotlStore axolotlStore) {
    this.axolotlStore = axolotlStore;
    this.localAddress = localAddress;
  }

  public OutgoingPushMessage encrypt(AxolotlAddress destination, byte[] unpaddedMessage, boolean legacy) {
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

    return new OutgoingPushMessage(type, destination.getDeviceId(), remoteRegistrationId,
                                   legacy ? body : null, legacy ? null : body);
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
  public TextSecureContent decrypt(TextSecureEnvelope envelope)
      throws InvalidVersionException, InvalidMessageException, InvalidKeyException,
             DuplicateMessageException, InvalidKeyIdException, UntrustedIdentityException,
             LegacyMessageException, NoSessionException
  {
    try {
      TextSecureContent content = new TextSecureContent();

      if (envelope.hasLegacyMessage()) {
        DataMessage message = DataMessage.parseFrom(decrypt(envelope, envelope.getLegacyMessage()));
        content = new TextSecureContent(createTextSecureMessage(envelope, message));
      } else if (envelope.hasContent()) {
        Content message = Content.parseFrom(decrypt(envelope, envelope.getContent()));

        if (message.hasDataMessage()) {
          content = new TextSecureContent(createTextSecureMessage(envelope, message.getDataMessage()));
        } else if (message.hasSyncMessage() && localAddress.getNumber().equals(envelope.getSource())) {
          content = new TextSecureContent(createSynchronizeMessage(envelope, message.getSyncMessage()));
        }
      }

      return content;
    } catch (InvalidProtocolBufferException e) {
      throw new InvalidMessageException(e);
    }
  }

  private byte[] decrypt(TextSecureEnvelope envelope, byte[] ciphertext)
      throws InvalidVersionException, InvalidMessageException, InvalidKeyException,
             DuplicateMessageException, InvalidKeyIdException, UntrustedIdentityException,
             LegacyMessageException, NoSessionException
  {
    AxolotlAddress sourceAddress = new AxolotlAddress(envelope.getSource(), envelope.getSourceDevice());
    SessionCipher  sessionCipher = new SessionCipher(axolotlStore, sourceAddress);

    byte[] paddedMessage;

    if (envelope.isPreKeyWhisperMessage()) {
      paddedMessage = sessionCipher.decrypt(new PreKeyWhisperMessage(ciphertext));
    } else if (envelope.isWhisperMessage()) {
      paddedMessage = sessionCipher.decrypt(new WhisperMessage(ciphertext));
    } else {
      throw new InvalidMessageException("Unknown type: " + envelope.getType());
    }

    PushTransportDetails transportDetails = new PushTransportDetails(sessionCipher.getSessionVersion());
    return transportDetails.getStrippedPaddingMessageBody(paddedMessage);
  }

  private TextSecureDataMessage createTextSecureMessage(TextSecureEnvelope envelope, DataMessage content) {
    TextSecureGroup            groupInfo   = createGroupInfo(envelope, content);
    List<TextSecureAttachment> attachments = new LinkedList<>();
    boolean                    endSession  = ((content.getFlags() & DataMessage.Flags.END_SESSION_VALUE) != 0);

    for (AttachmentPointer pointer : content.getAttachmentsList()) {
      attachments.add(new TextSecureAttachmentPointer(pointer.getId(),
                                                      pointer.getContentType(),
                                                      pointer.getKey().toByteArray(),
                                                      envelope.getRelay(),
                                                      pointer.hasSize() ? Optional.of(pointer.getSize()) : Optional.<Integer>absent(),
                                                      pointer.hasThumbnail() ? Optional.of(pointer.getThumbnail().toByteArray()): Optional.<byte[]>absent()));
    }

    return new TextSecureDataMessage(envelope.getTimestamp(), groupInfo, attachments,
                                     content.getBody(), endSession);
  }

  private TextSecureSyncMessage createSynchronizeMessage(TextSecureEnvelope envelope, SyncMessage content) {
    if (content.hasSent()) {
      SyncMessage.Sent sentContent = content.getSent();
      return TextSecureSyncMessage.forSentTranscript(new SentTranscriptMessage(sentContent.getDestination(),
                                                                               sentContent.getTimestamp(),
                                                                               createTextSecureMessage(envelope, sentContent.getMessage())));
    }

    if (content.hasRequest()) {
      return TextSecureSyncMessage.forRequest(new RequestMessage(content.getRequest()));
    }

    return TextSecureSyncMessage.empty();
  }

  private TextSecureGroup createGroupInfo(TextSecureEnvelope envelope, DataMessage content) {
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

