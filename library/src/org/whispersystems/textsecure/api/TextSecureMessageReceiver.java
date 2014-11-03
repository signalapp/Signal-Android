package org.whispersystems.textsecure.api;

import android.content.Context;

import com.google.protobuf.InvalidProtocolBufferException;

import org.whispersystems.libaxolotl.DuplicateMessageException;
import org.whispersystems.libaxolotl.InvalidKeyException;
import org.whispersystems.libaxolotl.InvalidKeyIdException;
import org.whispersystems.libaxolotl.InvalidMessageException;
import org.whispersystems.libaxolotl.InvalidVersionException;
import org.whispersystems.libaxolotl.LegacyMessageException;
import org.whispersystems.libaxolotl.NoSessionException;
import org.whispersystems.libaxolotl.UntrustedIdentityException;
import org.whispersystems.libaxolotl.protocol.PreKeyWhisperMessage;
import org.whispersystems.libaxolotl.protocol.WhisperMessage;
import org.whispersystems.libaxolotl.state.AxolotlStore;
import org.whispersystems.libaxolotl.util.guava.Optional;
import org.whispersystems.textsecure.api.messages.TextSecureAttachment;
import org.whispersystems.textsecure.api.messages.TextSecureAttachmentPointer;
import org.whispersystems.textsecure.api.messages.TextSecureGroup;
import org.whispersystems.textsecure.api.messages.TextSecureMessage;
import org.whispersystems.textsecure.crypto.AttachmentCipherInputStream;
import org.whispersystems.textsecure.crypto.TextSecureCipher;
import org.whispersystems.textsecure.push.IncomingEncryptedPushMessage;
import org.whispersystems.textsecure.push.IncomingPushMessage;
import org.whispersystems.textsecure.push.PushAddress;
import org.whispersystems.textsecure.push.PushServiceSocket;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

import static org.whispersystems.textsecure.push.PushMessageProtos.PushMessageContent;
import static org.whispersystems.textsecure.push.PushMessageProtos.PushMessageContent.AttachmentPointer;
import static org.whispersystems.textsecure.push.PushMessageProtos.PushMessageContent.GroupContext.Type.DELIVER;

public class TextSecureMessageReceiver {

  private final String            signalingKey;
  private final AxolotlStore      axolotlStore;
  private final PushServiceSocket socket;


  public TextSecureMessageReceiver(Context context, String signalingKey, String url,
                                   PushServiceSocket.TrustStore trustStore,
                                   String user, String password,
                                   AxolotlStore axolotlStore)
  {
    this.axolotlStore = axolotlStore;
    this.signalingKey = signalingKey;
    this.socket       = new PushServiceSocket(context, url, trustStore, user, password);
  }

  public InputStream retrieveAttachment(TextSecureAttachmentPointer pointer, File destination)
      throws IOException, InvalidMessageException
  {
    socket.retrieveAttachment(pointer.getRelay().orNull(), pointer.getId(), destination);
    return new AttachmentCipherInputStream(destination, pointer.getKey());
  }

  public IncomingPushMessage receiveSignal(String signal)
      throws IOException, InvalidVersionException
  {
    IncomingEncryptedPushMessage encrypted = new IncomingEncryptedPushMessage(signal, signalingKey);
    return encrypted.getIncomingPushMessage();
  }

  public TextSecureMessage receiveMessage(long recipientId, IncomingPushMessage signal)
      throws InvalidVersionException, InvalidMessageException, NoSessionException,
             LegacyMessageException, InvalidKeyIdException, DuplicateMessageException,
             InvalidKeyException, UntrustedIdentityException
  {
    try {
      PushAddress      sender = new PushAddress(recipientId, signal.getSource(), signal.getSourceDevice(), signal.getRelay());
      TextSecureCipher cipher = new TextSecureCipher(axolotlStore, sender);

      PushMessageContent message;

      if (signal.isPreKeyBundle()) {
        PreKeyWhisperMessage bundle = new PreKeyWhisperMessage(signal.getBody());
        message = PushMessageContent.parseFrom(cipher.decrypt(bundle));
      } else if (signal.isSecureMessage()) {
        WhisperMessage ciphertext = new WhisperMessage(signal.getBody());
        message = PushMessageContent.parseFrom(cipher.decrypt(ciphertext));
      } else if (signal.isPlaintext()) {
        message = PushMessageContent.parseFrom(signal.getBody());
      } else {
        throw new InvalidMessageException("Unknown type: " + signal.getType());
      }

      return createTextSecureMessage(signal, message);
    } catch (InvalidProtocolBufferException e) {
      throw new InvalidMessageException(e);
    }
  }

  private TextSecureMessage createTextSecureMessage(IncomingPushMessage signal, PushMessageContent content) {
    TextSecureGroup            groupInfo   = createGroupInfo(signal, content);
    List<TextSecureAttachment> attachments = new LinkedList<>();
    boolean                    endSession  = ((content.getFlags() & PushMessageContent.Flags.END_SESSION_VALUE) != 0);
    boolean                    secure      = signal.isSecureMessage() || signal.isPreKeyBundle();

    for (AttachmentPointer pointer : content.getAttachmentsList()) {
      attachments.add(new TextSecureAttachmentPointer(pointer.getId(),
                                                      pointer.getContentType(),
                                                      pointer.getKey().toByteArray(),
                                                      signal.getRelay()));
    }

    return new TextSecureMessage(signal.getTimestampMillis(), groupInfo, attachments,
                                 content.getBody(), secure, endSession);
  }

  private TextSecureGroup createGroupInfo(IncomingPushMessage signal, PushMessageContent content) {
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
                                                 signal.getRelay());
      }

      return new TextSecureGroup(type, content.getGroup().getId().toByteArray(), name, members, avatar);
    }

    return new TextSecureGroup(content.getGroup().getId().toByteArray());
  }

}
