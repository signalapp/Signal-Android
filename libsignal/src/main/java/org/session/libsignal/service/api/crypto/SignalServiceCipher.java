/*
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.session.libsignal.service.api.crypto;

import com.google.protobuf.InvalidProtocolBufferException;

import org.session.libsignal.libsignal.ecc.ECKeyPair;
import org.session.libsignal.metadata.InvalidMetadataMessageException;
import org.session.libsignal.metadata.ProtocolInvalidMessageException;
import org.session.libsignal.libsignal.InvalidMessageException;
import org.session.libsignal.libsignal.util.guava.Optional;
import org.session.libsignal.service.api.messages.SignalServiceAttachment;
import org.session.libsignal.service.api.messages.SignalServiceAttachmentPointer;
import org.session.libsignal.service.api.messages.SignalServiceContent;
import org.session.libsignal.service.api.messages.SignalServiceDataMessage;
import org.session.libsignal.service.api.messages.SignalServiceDataMessage.Preview;
import org.session.libsignal.service.api.messages.SignalServiceEnvelope;
import org.session.libsignal.service.api.messages.SignalServiceGroup;
import org.session.libsignal.service.api.messages.SignalServiceReceiptMessage;
import org.session.libsignal.service.api.messages.SignalServiceTypingMessage;
import org.session.libsignal.service.api.push.SignalServiceAddress;
import org.session.libsignal.service.internal.push.PushTransportDetails;
import org.session.libsignal.service.internal.push.SignalServiceProtos;
import org.session.libsignal.service.internal.push.SignalServiceProtos.AttachmentPointer;
import org.session.libsignal.service.internal.push.SignalServiceProtos.DataMessage.ClosedGroupControlMessage;
import org.session.libsignal.service.internal.push.SignalServiceProtos.Content;
import org.session.libsignal.service.internal.push.SignalServiceProtos.DataMessage;
import org.session.libsignal.service.internal.push.SignalServiceProtos.ReceiptMessage;
import org.session.libsignal.service.internal.push.SignalServiceProtos.TypingMessage;
import org.session.libsignal.service.loki.api.crypto.SessionProtocol;
import org.session.libsignal.service.loki.api.crypto.SessionProtocolUtilities;
import org.session.libsignal.service.loki.LokiAPIDatabaseProtocol;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static org.session.libsignal.service.internal.push.SignalServiceProtos.GroupContext.Type.DELIVER;

/**
 * This is used to decrypt received {@link SignalServiceEnvelope}s.
 *
 * @author Moxie Marlinspike
 */
public class SignalServiceCipher {

  @SuppressWarnings("unused")
  private static final String TAG = SignalServiceCipher.class.getSimpleName();

  private final SessionProtocol                  sessionProtocolImpl;
  private final LokiAPIDatabaseProtocol          apiDB;

  public SignalServiceCipher(SessionProtocol sessionProtocolImpl,
                             LokiAPIDatabaseProtocol apiDB)
  {
    this.sessionProtocolImpl  = sessionProtocolImpl;
    this.apiDB                = apiDB;
  }

  /**
   * Decrypt a received {@link SignalServiceEnvelope}
   *
   * @param envelope The received SignalServiceEnvelope
   *
   * @return a decrypted SignalServiceContent
   */
  public SignalServiceContent decrypt(SignalServiceEnvelope envelope) throws InvalidMetadataMessageException,ProtocolInvalidMessageException
  {
    try {
      Plaintext plaintext = decrypt(envelope, envelope.getContent());
      Content   message   = Content.parseFrom(plaintext.getData());

      if (message.hasConfigurationMessage()) {
        SignalServiceCipher.Metadata metadata = plaintext.getMetadata();
        SignalServiceContent content = new SignalServiceContent(message, metadata.getSender(), metadata.getSenderDevice(), metadata.getTimestamp());

        if (message.hasDataMessage()) {
          setProfile(message.getDataMessage(), content);
          SignalServiceDataMessage signalServiceDataMessage = createSignalServiceMessage(metadata, message.getDataMessage());
          content.setDataMessage(signalServiceDataMessage);
        }

        return content;
      } else if (message.hasDataMessage()) {
        DataMessage dataMessage = message.getDataMessage();

        SignalServiceDataMessage signalServiceDataMessage = createSignalServiceMessage(plaintext.getMetadata(), dataMessage);
        SignalServiceContent content = new SignalServiceContent(signalServiceDataMessage,
                plaintext.getMetadata().getSender(),
                plaintext.getMetadata().getSenderDevice(),
                plaintext.getMetadata().getTimestamp(),
                plaintext.getMetadata().isNeedsReceipt());

        setProfile(dataMessage, content);

        return content;
      } else if (message.hasReceiptMessage()) {
        return new SignalServiceContent(createReceiptMessage(plaintext.getMetadata(), message.getReceiptMessage()),
                plaintext.getMetadata().getSender(),
                plaintext.getMetadata().getSenderDevice(),
                plaintext.getMetadata().getTimestamp());
      } else if (message.hasTypingMessage()) {
        return new SignalServiceContent(createTypingMessage(plaintext.getMetadata(), message.getTypingMessage()),
                plaintext.getMetadata().getSender(),
                plaintext.getMetadata().getSenderDevice(),
                plaintext.getMetadata().getTimestamp());
      }

      return null;
    } catch (InvalidProtocolBufferException e) {
      throw new InvalidMetadataMessageException(e);
    }
  }

  private void setProfile(DataMessage message, SignalServiceContent content) {
    if (!message.hasProfile()) { return; }
    SignalServiceProtos.DataMessage.LokiProfile profile = message.getProfile();
    if (profile.hasDisplayName()) { content.setSenderDisplayName(profile.getDisplayName()); }
    if (profile.hasProfilePicture()) { content.setSenderProfilePictureURL(profile.getProfilePicture()); }
  }

  protected Plaintext decrypt(SignalServiceEnvelope envelope, byte[] ciphertext) throws InvalidMetadataMessageException
  {
    byte[] paddedMessage;
    Metadata metadata;

    if (envelope.isClosedGroupCiphertext()) {
      String groupPublicKey = envelope.getSource();
      kotlin.Pair<byte[], String> plaintextAndSenderPublicKey = SessionProtocolUtilities.INSTANCE.decryptClosedGroupCiphertext(ciphertext, groupPublicKey, apiDB, sessionProtocolImpl);
      paddedMessage = plaintextAndSenderPublicKey.getFirst();
      String senderPublicKey = plaintextAndSenderPublicKey.getSecond();
      metadata = new Metadata(senderPublicKey, 1, envelope.getTimestamp(), false);
    } else if (envelope.isUnidentifiedSender()) {
      ECKeyPair userX25519KeyPair = apiDB.getUserX25519KeyPair();
      kotlin.Pair<byte[], String> plaintextAndSenderPublicKey = sessionProtocolImpl.decrypt(ciphertext, userX25519KeyPair);
      paddedMessage = plaintextAndSenderPublicKey.getFirst();
      String senderPublicKey = plaintextAndSenderPublicKey.getSecond();
      metadata = new Metadata(senderPublicKey, 1, envelope.getTimestamp(), false);
    } else {
      throw new InvalidMetadataMessageException("Unknown type: " + envelope.getType());
    }
    byte[] data = PushTransportDetails.getStrippedPaddingMessageBody(paddedMessage);

    return new Plaintext(metadata, data);
  }

  private SignalServiceDataMessage createSignalServiceMessage(Metadata metadata, DataMessage content) throws ProtocolInvalidMessageException {
    SignalServiceGroup             groupInfo                   = createGroupInfo(content);
    List<SignalServiceAttachment>  attachments                 = new LinkedList<SignalServiceAttachment>();
    boolean                        expirationUpdate            = ((content.getFlags() & DataMessage.Flags.EXPIRATION_TIMER_UPDATE_VALUE) != 0);
    SignalServiceDataMessage.Quote quote                       = createQuote(content);
    List<Preview>                  previews                    = createPreviews(content);
    ClosedGroupControlMessage      closedGroupControlMessage   = content.getClosedGroupControlMessage();
    String                         syncTarget                  = content.getSyncTarget();

    for (AttachmentPointer pointer : content.getAttachmentsList()) {
      attachments.add(createAttachmentPointer(pointer));
    }

    if (content.hasTimestamp() && content.getTimestamp() != metadata.getTimestamp()) {
      throw new ProtocolInvalidMessageException(new InvalidMessageException("Timestamps don't match: " + content.getTimestamp() + " vs " + metadata.getTimestamp()),
              metadata.getSender(),
              metadata.getSenderDevice());
    }

    return new SignalServiceDataMessage(metadata.getTimestamp(),
            groupInfo,
            attachments,
            content.getBody(),
            content.getExpireTimer(),
            expirationUpdate,
            content.hasProfileKey() ? content.getProfileKey().toByteArray() : null,
            quote,
            new ArrayList<>(),
            previews,
            closedGroupControlMessage,
            syncTarget);
  }

  private SignalServiceReceiptMessage createReceiptMessage(Metadata metadata, ReceiptMessage content) {
    SignalServiceReceiptMessage.Type type;

    if      (content.getType() == ReceiptMessage.Type.DELIVERY) type = SignalServiceReceiptMessage.Type.DELIVERY;
    else if (content.getType() == ReceiptMessage.Type.READ)     type = SignalServiceReceiptMessage.Type.READ;
    else                                                        type = SignalServiceReceiptMessage.Type.UNKNOWN;

    return new SignalServiceReceiptMessage(type, content.getTimestampList(), metadata.getTimestamp());
  }

  private SignalServiceTypingMessage createTypingMessage(Metadata metadata, TypingMessage content) throws ProtocolInvalidMessageException {
    SignalServiceTypingMessage.Action action;

    if      (content.getAction() == TypingMessage.Action.STARTED) action = SignalServiceTypingMessage.Action.STARTED;
    else if (content.getAction() == TypingMessage.Action.STOPPED) action = SignalServiceTypingMessage.Action.STOPPED;
    else                                                          action = SignalServiceTypingMessage.Action.UNKNOWN;

    if (content.hasTimestamp() && content.getTimestamp() != metadata.getTimestamp()) {
      throw new ProtocolInvalidMessageException(new InvalidMessageException("Timestamps don't match: " + content.getTimestamp() + " vs " + metadata.getTimestamp()),
              metadata.getSender(),
              metadata.getSenderDevice());
    }

    return new SignalServiceTypingMessage(action, content.getTimestamp());
  }

  private SignalServiceDataMessage.Quote createQuote(DataMessage content) {
    if (!content.hasQuote()) return null;

    List<SignalServiceDataMessage.Quote.QuotedAttachment> attachments = new LinkedList<SignalServiceDataMessage.Quote.QuotedAttachment>();

    for (DataMessage.Quote.QuotedAttachment attachment : content.getQuote().getAttachmentsList()) {
      attachments.add(new SignalServiceDataMessage.Quote.QuotedAttachment(attachment.getContentType(),
              attachment.getFileName(),
              attachment.hasThumbnail() ? createAttachmentPointer(attachment.getThumbnail()) : null));
    }

    return new SignalServiceDataMessage.Quote(content.getQuote().getId(),
            new SignalServiceAddress(content.getQuote().getAuthor()),
            content.getQuote().getText(),
            attachments);
  }

  private List<Preview> createPreviews(DataMessage content) {
    if (content.getPreviewCount() <= 0) return null;

    List<Preview> results = new LinkedList<Preview>();

    for (DataMessage.Preview preview : content.getPreviewList()) {
      SignalServiceAttachment attachment = null;

      if (preview.hasImage()) {
        attachment = createAttachmentPointer(preview.getImage());
      }

      results.add(new Preview(preview.getUrl(),
              preview.getTitle(),
              Optional.fromNullable(attachment)));
    }

    return results;
  }

  private SignalServiceAttachmentPointer createAttachmentPointer(AttachmentPointer pointer) {
    return new SignalServiceAttachmentPointer(pointer.getId(),
            pointer.getContentType(),
            pointer.getKey().toByteArray(),
            pointer.hasSize() ? Optional.of(pointer.getSize()) : Optional.<Integer>absent(),
            pointer.hasThumbnail() ? Optional.of(pointer.getThumbnail().toByteArray()): Optional.<byte[]>absent(),
            pointer.getWidth(), pointer.getHeight(),
            pointer.hasDigest() ? Optional.of(pointer.getDigest().toByteArray()) : Optional.<byte[]>absent(),
            pointer.hasFileName() ? Optional.of(pointer.getFileName()) : Optional.<String>absent(),
            (pointer.getFlags() & AttachmentPointer.Flags.VOICE_MESSAGE_VALUE) != 0,
            pointer.hasCaption() ? Optional.of(pointer.getCaption()) : Optional.<String>absent(),
            pointer.getUrl());

  }

  private SignalServiceGroup createGroupInfo(DataMessage content) {
    if (!content.hasGroup()) return null;

    SignalServiceGroup.Type type;

    switch (content.getGroup().getType()) {
      case DELIVER:      type = SignalServiceGroup.Type.DELIVER;      break;
      case UPDATE:       type = SignalServiceGroup.Type.UPDATE;       break;
      case QUIT:         type = SignalServiceGroup.Type.QUIT;         break;
      case REQUEST_INFO: type = SignalServiceGroup.Type.REQUEST_INFO; break;
      default:           type = SignalServiceGroup.Type.UNKNOWN;      break;
    }

    if (content.getGroup().getType() != DELIVER) {
      String                      name    = null;
      List<String>                members = null;
      SignalServiceAttachmentPointer avatar  = null;
      List<String> admins = null;

      if (content.getGroup().hasName()) {
        name = content.getGroup().getName();
      }

      if (content.getGroup().getMembersCount() > 0) {
        members = content.getGroup().getMembersList();
      }

      if (content.getGroup().hasAvatar()) {
        AttachmentPointer pointer = content.getGroup().getAvatar();

        avatar = new SignalServiceAttachmentPointer(pointer.getId(),
                pointer.getContentType(),
                pointer.getKey().toByteArray(),
                Optional.of(pointer.getSize()),
                Optional.<byte[]>absent(), 0, 0,
                Optional.fromNullable(pointer.hasDigest() ? pointer.getDigest().toByteArray() : null),
                Optional.<String>absent(),
                false,
                Optional.<String>absent(),
                pointer.getUrl());
      }

      if (content.getGroup().getAdminsCount() > 0) {
        admins = content.getGroup().getAdminsList();
      }

      return new SignalServiceGroup(type, content.getGroup().getId().toByteArray(), SignalServiceGroup.GroupType.SIGNAL, name, members, avatar, admins);
    }

    return new SignalServiceGroup(content.getGroup().getId().toByteArray(), SignalServiceGroup.GroupType.SIGNAL);
  }

  protected static class Metadata {
    private final String  sender;
    private final int     senderDevice;
    private final long    timestamp;
    private final boolean needsReceipt;

    public Metadata(String sender, int senderDevice, long timestamp, boolean needsReceipt) {
      this.sender            = sender;
      this.senderDevice      = senderDevice;
      this.timestamp         = timestamp;
      this.needsReceipt      = needsReceipt;
    }

    public String getSender() {
      return sender;
    }

    public int getSenderDevice() {
      return senderDevice;
    }

    public long getTimestamp() {
      return timestamp;
    }

    public boolean isNeedsReceipt() {
      return needsReceipt;
    }
  }

  protected static class Plaintext {
    private final Metadata metadata;
    private final byte[]   data;

    public Plaintext(Metadata metadata, byte[] data) {
      this.metadata = metadata;
      this.data     = data;
    }

    public Metadata getMetadata() {
      return metadata;
    }

    public byte[] getData() {
      return data;
    }
  }
}
