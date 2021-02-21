/*
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */
package org.session.libsignal.service.api;

import com.google.protobuf.ByteString;

import org.jetbrains.annotations.Nullable;
import org.session.libsignal.libsignal.ecc.ECKeyPair;
import org.session.libsignal.libsignal.state.IdentityKeyStore;
import org.session.libsignal.utilities.logging.Log;
import org.session.libsignal.libsignal.util.guava.Optional;
import org.session.libsignal.service.api.crypto.AttachmentCipherOutputStream;
import org.session.libsignal.service.api.crypto.UnidentifiedAccess;
import org.session.libsignal.service.api.crypto.UntrustedIdentityException;
import org.session.libsignal.service.api.messages.SendMessageResult;
import org.session.libsignal.service.api.messages.SignalServiceAttachment;
import org.session.libsignal.service.api.messages.SignalServiceAttachmentPointer;
import org.session.libsignal.service.api.messages.SignalServiceAttachmentStream;
import org.session.libsignal.service.api.messages.SignalServiceDataMessage;
import org.session.libsignal.service.api.messages.SignalServiceGroup;
import org.session.libsignal.service.api.messages.SignalServiceReceiptMessage;
import org.session.libsignal.service.api.messages.SignalServiceTypingMessage;
import org.session.libsignal.service.api.messages.shared.SharedContact;
import org.session.libsignal.service.api.push.SignalServiceAddress;
import org.session.libsignal.service.api.push.exceptions.PushNetworkException;
import org.session.libsignal.service.api.push.exceptions.UnregisteredUserException;
import org.session.libsignal.service.api.util.CredentialsProvider;
import org.session.libsignal.service.internal.crypto.PaddingInputStream;
import org.session.libsignal.service.internal.push.OutgoingPushMessage;
import org.session.libsignal.service.internal.push.OutgoingPushMessageList;
import org.session.libsignal.service.internal.push.PushAttachmentData;
import org.session.libsignal.service.internal.push.PushTransportDetails;
import org.session.libsignal.service.internal.push.SignalServiceProtos;
import org.session.libsignal.service.internal.push.SignalServiceProtos.AttachmentPointer;
import org.session.libsignal.service.internal.push.SignalServiceProtos.Content;
import org.session.libsignal.service.internal.push.SignalServiceProtos.DataMessage;
import org.session.libsignal.service.internal.push.SignalServiceProtos.GroupContext;
import org.session.libsignal.service.internal.push.SignalServiceProtos.DataMessage.LokiProfile;
import org.session.libsignal.service.internal.push.SignalServiceProtos.ReceiptMessage;
import org.session.libsignal.service.internal.push.SignalServiceProtos.TypingMessage;
import org.session.libsignal.service.internal.push.http.AttachmentCipherOutputStreamFactory;
import org.session.libsignal.service.internal.push.http.OutputStreamFactory;
import org.session.libsignal.utilities.Base64;
import org.session.libsignal.service.internal.util.StaticCredentialsProvider;
import org.session.libsignal.service.internal.util.Util;
import org.session.libsignal.utilities.concurrent.SettableFuture;
import org.session.libsignal.service.loki.api.LokiDotNetAPI;
import org.session.libsignal.service.loki.api.PushNotificationAPI;
import org.session.libsignal.service.loki.api.SignalMessageInfo;
import org.session.libsignal.service.loki.api.SnodeAPI;
import org.session.libsignal.service.loki.api.crypto.SessionProtocol;
import org.session.libsignal.service.loki.api.fileserver.FileServerAPI;
import org.session.libsignal.service.loki.api.opengroups.PublicChat;
import org.session.libsignal.service.loki.api.opengroups.PublicChatAPI;
import org.session.libsignal.service.loki.api.opengroups.PublicChatMessage;
import org.session.libsignal.service.loki.database.LokiAPIDatabaseProtocol;
import org.session.libsignal.service.loki.database.LokiMessageDatabaseProtocol;
import org.session.libsignal.service.loki.database.LokiOpenGroupDatabaseProtocol;
import org.session.libsignal.service.loki.database.LokiThreadDatabaseProtocol;
import org.session.libsignal.service.loki.database.LokiUserDatabaseProtocol;
import org.session.libsignal.service.loki.utilities.TTLUtilities;
import org.session.libsignal.service.loki.utilities.Broadcaster;
import org.session.libsignal.service.loki.utilities.HexEncodingKt;
import org.session.libsignal.service.loki.utilities.PlaintextOutputStreamFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import nl.komponents.kovenant.Promise;

/**
 * The main interface for sending Signal Service messages.
 *
 * @author Moxie Marlinspike
 */
public class SignalServiceMessageSender {

  private static final String TAG = SignalServiceMessageSender.class.getSimpleName();

  private final IdentityKeyStore                                    store;
  private final SignalServiceAddress                                localAddress;

  // Loki
  private final String                                              userPublicKey;
  private final LokiAPIDatabaseProtocol                             apiDatabase;
  private final LokiThreadDatabaseProtocol                          threadDatabase;
  private final LokiMessageDatabaseProtocol                         messageDatabase;
  private final SessionProtocol                                     sessionProtocolImpl;
  private final LokiUserDatabaseProtocol                            userDatabase;
  private final LokiOpenGroupDatabaseProtocol                       openGroupDatabase;
  private final Broadcaster                                         broadcaster;

  /**
   * Construct a SignalServiceMessageSender.
   *
   * @param user The Signal Service username (eg phone number).
   * @param password The Signal Service user password.
   * @param store The SignalProtocolStore.
   */
  public SignalServiceMessageSender(String user, String password,
                                    IdentityKeyStore store,
                                    String userPublicKey,
                                    LokiAPIDatabaseProtocol apiDatabase,
                                    LokiThreadDatabaseProtocol threadDatabase,
                                    LokiMessageDatabaseProtocol messageDatabase,
                                    SessionProtocol sessionProtocolImpl,
                                    LokiUserDatabaseProtocol userDatabase,
                                    LokiOpenGroupDatabaseProtocol openGroupDatabase,
                                    Broadcaster broadcaster)
  {
    this(new StaticCredentialsProvider(user, password, null), store, userPublicKey, apiDatabase, threadDatabase, messageDatabase, sessionProtocolImpl, userDatabase, openGroupDatabase, broadcaster);
  }

  public SignalServiceMessageSender(CredentialsProvider credentialsProvider,
                                    IdentityKeyStore store,
                                    String userPublicKey,
                                    LokiAPIDatabaseProtocol apiDatabase,
                                    LokiThreadDatabaseProtocol threadDatabase,
                                    LokiMessageDatabaseProtocol messageDatabase,
                                    SessionProtocol sessionProtocolImpl,
                                    LokiUserDatabaseProtocol userDatabase,
                                    LokiOpenGroupDatabaseProtocol openGroupDatabase,
                                    Broadcaster broadcaster)
  {
    this.store                     = store;
    this.localAddress              = new SignalServiceAddress(credentialsProvider.getUser());
    this.userPublicKey             = userPublicKey;
    this.apiDatabase               = apiDatabase;
    this.threadDatabase            = threadDatabase;
    this.messageDatabase           = messageDatabase;
    this.sessionProtocolImpl       = sessionProtocolImpl;
    this.userDatabase              = userDatabase;
    this.openGroupDatabase         = openGroupDatabase;
    this.broadcaster               = broadcaster;
  }

  /**
   * Send a read receipt for a received message.
   *
   * @param recipient The sender of the received message you're acknowledging.
   * @param message The read receipt to deliver.
   * @throws IOException
   */
  public void sendReceipt(SignalServiceAddress recipient,
                          Optional<UnidentifiedAccess> unidentifiedAccess,
                          SignalServiceReceiptMessage message)
      throws IOException {
    byte[] content = createReceiptContent(message);
    boolean useFallbackEncryption = true;
    sendMessage(recipient, unidentifiedAccess, message.getWhen(), content, false, message.getTTL(), useFallbackEncryption);
  }

  public void sendTyping(List<SignalServiceAddress>             recipients,
                         List<Optional<UnidentifiedAccess>>     unidentifiedAccess,
                         SignalServiceTypingMessage             message)
      throws IOException
  {
    byte[] content = createTypingContent(message);
    sendMessage(0, recipients, unidentifiedAccess, message.getTimestamp(), content, true, message.getTTL(), false, false);
  }

  /**
   * Send a message to a single recipient.
   *
   * @param recipient The message's destination.
   * @param message The message.
   * @throws IOException
   */
  public SendMessageResult sendMessage(long                             messageID,
                                       SignalServiceAddress             recipient,
                                       Optional<UnidentifiedAccess> unidentifiedAccess,
                                       SignalServiceDataMessage         message)
      throws IOException
  {
    byte[]            content               = createMessageContent(message, recipient);
    long              timestamp             = message.getTimestamp();
    boolean           isClosedGroup         = message.group.isPresent() && message.group.get().getGroupType() == SignalServiceGroup.GroupType.SIGNAL;
    SendMessageResult result                = sendMessage(messageID, recipient, unidentifiedAccess, timestamp, content, false, message.getTTL(), true, isClosedGroup, message.hasVisibleContent(), message.getSyncTarget());

    return result;
  }

  /**
   * Send a message to a group.
   *
   * @param recipients The group members.
   * @param message The group message.
   * @throws IOException
   */
  public List<SendMessageResult> sendMessage(long                                   messageID,
                                             List<SignalServiceAddress>             recipients,
                                             List<Optional<UnidentifiedAccess>>     unidentifiedAccess,
                                             SignalServiceDataMessage               message)
      throws IOException {
    // Loki - We only need the first recipient in the line below. This is because the recipient is only used to determine
    // whether an attachment is being sent to an open group or not.
    byte[]                  content            = createMessageContent(message, recipients.get(0));
    long                    timestamp          = message.getTimestamp();
    boolean                 isClosedGroup      = message.group.isPresent() && message.group.get().getGroupType() == SignalServiceGroup.GroupType.SIGNAL;

    return sendMessage(messageID, recipients, unidentifiedAccess, timestamp, content, false, message.getTTL(), isClosedGroup, message.hasVisibleContent());
  }

  public SignalServiceAttachmentPointer uploadAttachment(SignalServiceAttachmentStream attachment, boolean usePadding, @Nullable SignalServiceAddress recipient)
      throws IOException
  {
    boolean shouldEncrypt = true;
    String server = FileServerAPI.shared.getServer();

    // Loki - Check if we are sending to an open group
    if (recipient != null) {
      long threadID = threadDatabase.getThreadID(recipient.getNumber());
      PublicChat publicChat = threadDatabase.getPublicChat(threadID);
      if (publicChat != null) {
        shouldEncrypt = false;
        server = publicChat.getServer();
      }
    }

    byte[]             attachmentKey    = Util.getSecretBytes(64);
    long               paddedLength     = usePadding ? PaddingInputStream.getPaddedSize(attachment.getLength()) : attachment.getLength();
    InputStream        dataStream       = usePadding ? new PaddingInputStream(attachment.getInputStream(), attachment.getLength()) : attachment.getInputStream();
    long               ciphertextLength = shouldEncrypt ? AttachmentCipherOutputStream.getCiphertextLength(paddedLength) : attachment.getLength();

    OutputStreamFactory outputStreamFactory = shouldEncrypt ? new AttachmentCipherOutputStreamFactory(attachmentKey) : new PlaintextOutputStreamFactory();
    PushAttachmentData  attachmentData      = new PushAttachmentData(attachment.getContentType(), dataStream, ciphertextLength, outputStreamFactory, attachment.getListener());

    // Loki - Upload attachment
    LokiDotNetAPI.UploadResult result = FileServerAPI.shared.uploadAttachment(server, attachmentData);
    return new SignalServiceAttachmentPointer(result.getId(),
                                              attachment.getContentType(),
                                              attachmentKey,
                                              Optional.of(Util.toIntExact(attachment.getLength())),
                                              attachment.getPreview(),
                                              attachment.getWidth(), attachment.getHeight(),
                                              Optional.fromNullable(result.getDigest()),
                                              attachment.getFileName(),
                                              attachment.getVoiceNote(),
                                              attachment.getCaption(),
                                              result.getUrl());
  }

  private byte[] createTypingContent(SignalServiceTypingMessage message) {
    Content.Builder       container = Content.newBuilder();
    TypingMessage.Builder builder   = TypingMessage.newBuilder();

    builder.setTimestamp(message.getTimestamp());

    if      (message.isTypingStarted()) builder.setAction(TypingMessage.Action.STARTED);
    else if (message.isTypingStopped()) builder.setAction(TypingMessage.Action.STOPPED);
    else                                throw new IllegalArgumentException("Unknown typing indicator");

    return container.setTypingMessage(builder).build().toByteArray();
  }

  private byte[] createReceiptContent(SignalServiceReceiptMessage message) {
    Content.Builder        container = Content.newBuilder();
    ReceiptMessage.Builder builder   = ReceiptMessage.newBuilder();

    for (long timestamp : message.getTimestamps()) {
      builder.addTimestamp(timestamp);
    }

    if      (message.isDeliveryReceipt()) builder.setType(ReceiptMessage.Type.DELIVERY);
    else if (message.isReadReceipt())     builder.setType(ReceiptMessage.Type.READ);

    return container.setReceiptMessage(builder).build().toByteArray();
  }

  private byte[] createMessageContent(SignalServiceDataMessage message, SignalServiceAddress recipient)
      throws IOException
  {
    Content.Builder container = Content.newBuilder();

    DataMessage.Builder builder = DataMessage.newBuilder();
    List<AttachmentPointer> pointers = createAttachmentPointers(message.getAttachments(), recipient);

    if (!pointers.isEmpty()) {
      builder.addAllAttachments(pointers);
    }

    if (message.getBody().isPresent()) {
      builder.setBody(message.getBody().get());
    }

    if (message.getGroupInfo().isPresent()) {
      builder.setGroup(createGroupContent(message.getGroupInfo().get(), recipient));
    }

    if (message.isExpirationUpdate()) {
      builder.setFlags(DataMessage.Flags.EXPIRATION_TIMER_UPDATE_VALUE);
    }

    if (message.getExpiresInSeconds() > 0) {
      builder.setExpireTimer(message.getExpiresInSeconds());
    }

    if (message.getProfileKey().isPresent()) {
      builder.setProfileKey(ByteString.copyFrom(message.getProfileKey().get()));
    }

    if (message.getSyncTarget().isPresent()) {
      builder.setSyncTarget(message.getSyncTarget().get());
    }

    if (message.getQuote().isPresent()) {
      DataMessage.Quote.Builder quoteBuilder = DataMessage.Quote.newBuilder()
          .setId(message.getQuote().get().getId())
          .setAuthor(message.getQuote().get().getAuthor().getNumber())
          .setText(message.getQuote().get().getText());

      for (SignalServiceDataMessage.Quote.QuotedAttachment attachment : message.getQuote().get().getAttachments()) {
        DataMessage.Quote.QuotedAttachment.Builder quotedAttachment = DataMessage.Quote.QuotedAttachment.newBuilder();

        quotedAttachment.setContentType(attachment.getContentType());

        if (attachment.getFileName() != null) {
          quotedAttachment.setFileName(attachment.getFileName());
        }

        if (attachment.getThumbnail() != null) {
          quotedAttachment.setThumbnail(createAttachmentPointer(attachment.getThumbnail().asStream(), recipient));
        }

        quoteBuilder.addAttachments(quotedAttachment);
      }

      builder.setQuote(quoteBuilder);
    }

    if (message.getSharedContacts().isPresent()) {
      builder.addAllContact(createSharedContactContent(message.getSharedContacts().get(), recipient));
    }

    if (message.getPreviews().isPresent()) {
      for (SignalServiceDataMessage.Preview preview : message.getPreviews().get()) {
        DataMessage.Preview.Builder previewBuilder = DataMessage.Preview.newBuilder();
        previewBuilder.setTitle(preview.getTitle());
        previewBuilder.setUrl(preview.getUrl());

        if (preview.getImage().isPresent()) {
          if (preview.getImage().get().isStream()) {
            previewBuilder.setImage(createAttachmentPointer(preview.getImage().get().asStream(), recipient));
          } else {
            previewBuilder.setImage(createAttachmentPointer(preview.getImage().get().asPointer()));
          }
        }

        builder.addPreview(previewBuilder.build());
      }
    }

    LokiProfile.Builder lokiUserProfileBuilder = LokiProfile.newBuilder();
    String displayName = userDatabase.getDisplayName(userPublicKey);
    if (displayName != null) { lokiUserProfileBuilder.setDisplayName(displayName); }
    String profilePictureURL = userDatabase.getProfilePictureURL(userPublicKey);
    if (profilePictureURL != null) { lokiUserProfileBuilder.setProfilePicture(profilePictureURL); }
    builder.setProfile(lokiUserProfileBuilder.build());

    builder.setTimestamp(message.getTimestamp());

    container.setDataMessage(builder);

    return container.build().toByteArray();
  }

  private GroupContext createGroupContent(SignalServiceGroup group, SignalServiceAddress recipient)
      throws IOException
  {
    GroupContext.Builder builder = GroupContext.newBuilder();
    builder.setId(ByteString.copyFrom(group.getGroupId()));

    if (group.getType() != SignalServiceGroup.Type.DELIVER) {
      if      (group.getType() == SignalServiceGroup.Type.UPDATE)       builder.setType(GroupContext.Type.UPDATE);
      else if (group.getType() == SignalServiceGroup.Type.QUIT)         builder.setType(GroupContext.Type.QUIT);
      else if (group.getType() == SignalServiceGroup.Type.REQUEST_INFO) builder.setType(GroupContext.Type.REQUEST_INFO);
      else                                                              throw new AssertionError("Unknown type: " + group.getType());

      if (group.getName().isPresent()) builder.setName(group.getName().get());
      if (group.getMembers().isPresent()) builder.addAllMembers(group.getMembers().get());
      if (group.getAdmins().isPresent()) builder.addAllAdmins(group.getAdmins().get());

      if (group.getAvatar().isPresent()) {
        if (group.getAvatar().get().isStream()) {
          builder.setAvatar(createAttachmentPointer(group.getAvatar().get().asStream(), recipient));
        } else {
          builder.setAvatar(createAttachmentPointer(group.getAvatar().get().asPointer()));
        }
      }
    } else {
      builder.setType(GroupContext.Type.DELIVER);
    }

    return builder.build();
  }

  private List<DataMessage.Contact> createSharedContactContent(List<SharedContact> contacts, SignalServiceAddress recipient)
      throws IOException
  {
    List<DataMessage.Contact> results = new LinkedList<>();

    for (SharedContact contact : contacts) {
      DataMessage.Contact.Name.Builder nameBuilder = DataMessage.Contact.Name.newBuilder();

      if (contact.getName().getFamily().isPresent())  nameBuilder.setFamilyName(contact.getName().getFamily().get());
      if (contact.getName().getGiven().isPresent())   nameBuilder.setGivenName(contact.getName().getGiven().get());
      if (contact.getName().getMiddle().isPresent())  nameBuilder.setMiddleName(contact.getName().getMiddle().get());
      if (contact.getName().getPrefix().isPresent())  nameBuilder.setPrefix(contact.getName().getPrefix().get());
      if (contact.getName().getSuffix().isPresent())  nameBuilder.setSuffix(contact.getName().getSuffix().get());
      if (contact.getName().getDisplay().isPresent()) nameBuilder.setDisplayName(contact.getName().getDisplay().get());

      DataMessage.Contact.Builder contactBuilder = DataMessage.Contact.newBuilder()
                                                                      .setName(nameBuilder);

      if (contact.getAddress().isPresent()) {
        for (SharedContact.PostalAddress address : contact.getAddress().get()) {
          DataMessage.Contact.PostalAddress.Builder addressBuilder = DataMessage.Contact.PostalAddress.newBuilder();

          switch (address.getType()) {
            case HOME:   addressBuilder.setType(DataMessage.Contact.PostalAddress.Type.HOME); break;
            case WORK:   addressBuilder.setType(DataMessage.Contact.PostalAddress.Type.WORK); break;
            case CUSTOM: addressBuilder.setType(DataMessage.Contact.PostalAddress.Type.CUSTOM); break;
            default:     throw new AssertionError("Unknown type: " + address.getType());
          }

          if (address.getCity().isPresent())         addressBuilder.setCity(address.getCity().get());
          if (address.getCountry().isPresent())      addressBuilder.setCountry(address.getCountry().get());
          if (address.getLabel().isPresent())        addressBuilder.setLabel(address.getLabel().get());
          if (address.getNeighborhood().isPresent()) addressBuilder.setNeighborhood(address.getNeighborhood().get());
          if (address.getPobox().isPresent())        addressBuilder.setPobox(address.getPobox().get());
          if (address.getPostcode().isPresent())     addressBuilder.setPostcode(address.getPostcode().get());
          if (address.getRegion().isPresent())       addressBuilder.setRegion(address.getRegion().get());
          if (address.getStreet().isPresent())       addressBuilder.setStreet(address.getStreet().get());

          contactBuilder.addAddress(addressBuilder);
        }
      }

      if (contact.getEmail().isPresent()) {
        for (SharedContact.Email email : contact.getEmail().get()) {
          DataMessage.Contact.Email.Builder emailBuilder = DataMessage.Contact.Email.newBuilder()
                                                                                    .setValue(email.getValue());

          switch (email.getType()) {
            case HOME:   emailBuilder.setType(DataMessage.Contact.Email.Type.HOME);   break;
            case WORK:   emailBuilder.setType(DataMessage.Contact.Email.Type.WORK);   break;
            case MOBILE: emailBuilder.setType(DataMessage.Contact.Email.Type.MOBILE); break;
            case CUSTOM: emailBuilder.setType(DataMessage.Contact.Email.Type.CUSTOM); break;
            default:     throw new AssertionError("Unknown type: " + email.getType());
          }

          if (email.getLabel().isPresent()) emailBuilder.setLabel(email.getLabel().get());

          contactBuilder.addEmail(emailBuilder);
        }
      }

      if (contact.getPhone().isPresent()) {
        for (SharedContact.Phone phone : contact.getPhone().get()) {
          DataMessage.Contact.Phone.Builder phoneBuilder = DataMessage.Contact.Phone.newBuilder()
                                                                                    .setValue(phone.getValue());

          switch (phone.getType()) {
            case HOME:   phoneBuilder.setType(DataMessage.Contact.Phone.Type.HOME);   break;
            case WORK:   phoneBuilder.setType(DataMessage.Contact.Phone.Type.WORK);   break;
            case MOBILE: phoneBuilder.setType(DataMessage.Contact.Phone.Type.MOBILE); break;
            case CUSTOM: phoneBuilder.setType(DataMessage.Contact.Phone.Type.CUSTOM); break;
            default:     throw new AssertionError("Unknown type: " + phone.getType());
          }

          if (phone.getLabel().isPresent()) phoneBuilder.setLabel(phone.getLabel().get());

          contactBuilder.addNumber(phoneBuilder);
        }
      }

      if (contact.getAvatar().isPresent()) {
        AttachmentPointer pointer = contact.getAvatar().get().getAttachment().isStream() ? createAttachmentPointer(contact.getAvatar().get().getAttachment().asStream(), recipient)
                                                                                         : createAttachmentPointer(contact.getAvatar().get().getAttachment().asPointer());
        contactBuilder.setAvatar(DataMessage.Contact.Avatar.newBuilder()
                                                           .setAvatar(pointer)
                                                           .setIsProfile(contact.getAvatar().get().isProfile()));
      }

      if (contact.getOrganization().isPresent()) {
        contactBuilder.setOrganization(contact.getOrganization().get());
      }

      results.add(contactBuilder.build());
    }

    return results;
  }

  private List<SendMessageResult> sendMessage(long                               messageID,
                                              List<SignalServiceAddress>         recipients,
                                              List<Optional<UnidentifiedAccess>> unidentifiedAccess,
                                              long                               timestamp,
                                              byte[]                             content,
                                              boolean                            online,
                                              int                                ttl,
                                              boolean                            isClosedGroup,
                                              boolean                            notifyPNServer)
      throws IOException
  {
    List<SendMessageResult>                results                    = new LinkedList<>();
    SignalServiceAddress                   ownAddress                 = localAddress;
    Iterator<SignalServiceAddress>         recipientIterator          = recipients.iterator();
    Iterator<Optional<UnidentifiedAccess>> unidentifiedAccessIterator = unidentifiedAccess.iterator();

    while (recipientIterator.hasNext()) {
      SignalServiceAddress recipient = recipientIterator.next();

      try {
        SendMessageResult result = sendMessage(messageID, recipient, unidentifiedAccessIterator.next(), timestamp, content, online, ttl, true, isClosedGroup, notifyPNServer, Optional.absent());
        results.add(result);
      } catch (UnregisteredUserException e) {
        Log.w(TAG, e);
        results.add(SendMessageResult.unregisteredFailure(recipient));
      } catch (PushNetworkException e) {
        Log.w(TAG, e);
        results.add(SendMessageResult.networkFailure(recipient));
      }
    }

    return results;
  }

  private SendMessageResult sendMessage(SignalServiceAddress recipient,
                                        Optional<UnidentifiedAccess> unidentifiedAccess,
                                        long timestamp,
                                        byte[] content,
                                        boolean online,
                                        int ttl,
                                        boolean useFallbackEncryption)
      throws IOException
  {
    // Loki - This method is only invoked for various types of control messages
    return sendMessage(0, recipient, unidentifiedAccess, timestamp, content, online, ttl, false, useFallbackEncryption, false,Optional.absent());
  }

  public SendMessageResult sendMessage(final long messageID,
                                       final SignalServiceAddress recipient,
                                       Optional<UnidentifiedAccess> unidentifiedAccess,
                                       long timestamp,
                                       byte[] content,
                                       boolean online,
                                       int ttl,
                                       boolean useFallbackEncryption,
                                       boolean isClosedGroup,
                                       boolean notifyPNServer,
                                       Optional<String> syncTarget)
      throws IOException
  {
    boolean isSelfSend = syncTarget.isPresent() && !syncTarget.get().isEmpty();
    long threadID;
    if (isSelfSend) {
      threadID = threadDatabase.getThreadID(syncTarget.get());
    } else {
      threadID = threadDatabase.getThreadID(recipient.getNumber());
    }
    PublicChat publicChat = threadDatabase.getPublicChat(threadID);
    try {
      if (publicChat != null) {
        return sendMessageToPublicChat(messageID, recipient, timestamp, content, publicChat);
      } else {
        return sendMessageToPrivateChat(messageID, recipient, unidentifiedAccess, timestamp, content, online, ttl, useFallbackEncryption, isClosedGroup, notifyPNServer, syncTarget);
      }
    } catch (PushNetworkException e) {
      return SendMessageResult.networkFailure(recipient);
    } catch (UntrustedIdentityException e) {
      return SendMessageResult.identityFailure(recipient, e.getIdentityKey());
    }
  }

  private SendMessageResult sendMessageToPublicChat(final long                 messageID,
                                                    final SignalServiceAddress recipient,
                                                    long                       timestamp,
                                                    byte[]                     content,
                                                    PublicChat publicChat) {
    if (messageID == 0) {
      Log.d("Loki", "Missing message ID.");
    }
    final SettableFuture<?>[] future = { new SettableFuture<Unit>() };
    try {
      DataMessage data = Content.parseFrom(content).getDataMessage();
      String body = (data.getBody() != null && data.getBody().length() > 0) ? data.getBody() : Long.toString(data.getTimestamp());
      PublicChatMessage.Quote quote = null;
      if (data.hasQuote()) {
        long quoteID = data.getQuote().getId();
        String quoteePublicKey = data.getQuote().getAuthor();
        long serverID = messageDatabase.getQuoteServerID(quoteID, quoteePublicKey);
        quote = new PublicChatMessage.Quote(quoteID, quoteePublicKey, data.getQuote().getText(), serverID);
      }
      DataMessage.Preview linkPreview = (data.getPreviewList().size() > 0) ? data.getPreviewList().get(0) : null;
      ArrayList<PublicChatMessage.Attachment> attachments = new ArrayList<>();
      if (linkPreview != null && linkPreview.hasImage()) {
        AttachmentPointer attachmentPointer = linkPreview.getImage();
        String caption = attachmentPointer.hasCaption() ? attachmentPointer.getCaption() : null;
        attachments.add(new PublicChatMessage.Attachment(
            PublicChatMessage.Attachment.Kind.LinkPreview,
            publicChat.getServer(),
            attachmentPointer.getId(),
            attachmentPointer.getContentType(),
            attachmentPointer.getSize(),
            attachmentPointer.getFileName(),
            attachmentPointer.getFlags(),
            attachmentPointer.getWidth(),
            attachmentPointer.getHeight(),
            caption,
            attachmentPointer.getUrl(),
            linkPreview.getUrl(),
            linkPreview.getTitle()
        ));
      }
      for (AttachmentPointer attachmentPointer : data.getAttachmentsList()) {
        String caption = attachmentPointer.hasCaption() ? attachmentPointer.getCaption() : null;
        attachments.add(new PublicChatMessage.Attachment(
            PublicChatMessage.Attachment.Kind.Attachment,
            publicChat.getServer(),
            attachmentPointer.getId(),
            attachmentPointer.getContentType(),
            attachmentPointer.getSize(),
            attachmentPointer.getFileName(),
            attachmentPointer.getFlags(),
            attachmentPointer.getWidth(),
            attachmentPointer.getHeight(),
            caption,
            attachmentPointer.getUrl(),
            null,
            null
        ));
      }
      PublicChatMessage message = new PublicChatMessage(userPublicKey, "", body, timestamp, PublicChatAPI.getPublicChatMessageType(), quote, attachments);
      byte[] privateKey = store.getIdentityKeyPair().getPrivateKey().serialize();
      new PublicChatAPI(userPublicKey, privateKey, apiDatabase, userDatabase, openGroupDatabase).sendMessage(message, publicChat.getChannel(), publicChat.getServer()).success(new Function1<PublicChatMessage, Unit>() {

        @Override
        public Unit invoke(PublicChatMessage message) {
          @SuppressWarnings("unchecked") SettableFuture<Unit> f = (SettableFuture<Unit>)future[0];
          messageDatabase.setServerID(messageID, message.getServerID());
          f.set(Unit.INSTANCE);
          return Unit.INSTANCE;
        }
      }).fail(new Function1<Exception, Unit>() {

        @Override
        public Unit invoke(Exception exception) {
          @SuppressWarnings("unchecked") SettableFuture<Unit> f = (SettableFuture<Unit>)future[0];
          f.setException(exception);
          return Unit.INSTANCE;
        }
      });
    } catch (Exception exception) {
      @SuppressWarnings("unchecked") SettableFuture<Unit> f = (SettableFuture<Unit>)future[0];
      f.setException(exception);
    }
    @SuppressWarnings("unchecked") SettableFuture<Unit> f = (SettableFuture<Unit>)future[0];
    try {
      f.get(1, TimeUnit.MINUTES);
      return SendMessageResult.success(recipient, false, false);
    } catch (Exception exception) {
      return SendMessageResult.networkFailure(recipient);
    }
  }

  private SendMessageResult sendMessageToPrivateChat(final long                   messageID,
                                                     final SignalServiceAddress   recipient,
                                                     Optional<UnidentifiedAccess> unidentifiedAccess,
                                                     final long                   timestamp,
                                                     byte[]                       content,
                                                     boolean                      online,
                                                     int                          ttl,
                                                     boolean                      useFallbackEncryption,
                                                     boolean                      isClosedGroup,
                                                     final boolean                notifyPNServer,
                                                     Optional<String>             syncTarget)
      throws IOException, UntrustedIdentityException
  {
    final SettableFuture<?>[] future = { new SettableFuture<Unit>() };
    OutgoingPushMessageList messages = getSessionProtocolEncryptedMessage(recipient, timestamp, content);
    // Loki - Remove this when we have shared sender keys
    // ========
    if (messages.getMessages().isEmpty()) {
      return SendMessageResult.success(recipient, false, false);
    }
    // ========
    OutgoingPushMessage message = messages.getMessages().get(0);
    final SignalServiceProtos.Envelope.Type type = SignalServiceProtos.Envelope.Type.valueOf(message.type);
    final String senderID;
    if (type == SignalServiceProtos.Envelope.Type.CLOSED_GROUP_CIPHERTEXT) {
      senderID = recipient.getNumber();
    } else if (type == SignalServiceProtos.Envelope.Type.UNIDENTIFIED_SENDER) {
      senderID = "";
    } else {
      senderID = userPublicKey;
    }
    final int senderDeviceID = (type == SignalServiceProtos.Envelope.Type.UNIDENTIFIED_SENDER) ? 0 : SignalServiceAddress.DEFAULT_DEVICE_ID;
    // Make sure we have a valid ttl; otherwise default to 2 days
    if (ttl <= 0) { ttl = TTLUtilities.INSTANCE.getFallbackMessageTTL(); }
    final int regularMessageTTL = TTLUtilities.getTTL(TTLUtilities.MessageType.Regular);
    final int __ttl = ttl;
    final SignalMessageInfo messageInfo = new SignalMessageInfo(type, timestamp, senderID, senderDeviceID, message.content, recipient.getNumber(), ttl, false);
    SnodeAPI.shared.sendSignalMessage(messageInfo).success(new Function1<Set<Promise<Map<?, ?>, Exception>>, Unit>() {

      @Override
      public Unit invoke(Set<Promise<Map<?, ?>, Exception>> promises) {
        final boolean[] isSuccess = { false };
        final int[] promiseCount = {promises.size()};
        final int[] errorCount = { 0 };
        for (Promise<Map<?, ?>, Exception> promise : promises) {
          promise.success(new Function1<Map<?, ?>, Unit>() {

            @Override
            public Unit invoke(Map<?, ?> map) {
              if (isSuccess[0]) { return Unit.INSTANCE; } // Succeed as soon as the first promise succeeds
              if (__ttl == regularMessageTTL) {
                broadcaster.broadcast("messageSent", timestamp);
              }
              isSuccess[0] = true;
              if (notifyPNServer) {
                PushNotificationAPI.shared.notify(messageInfo);
              }
              @SuppressWarnings("unchecked") SettableFuture<Unit> f = (SettableFuture<Unit>)future[0];
              f.set(Unit.INSTANCE);
              return Unit.INSTANCE;
            }
          }).fail(new Function1<Exception, Unit>() {

            @Override
            public Unit invoke(Exception exception) {
              errorCount[0] += 1;
              if (errorCount[0] != promiseCount[0]) { return Unit.INSTANCE; } // Only error out if all promises failed
              if (__ttl == regularMessageTTL) {
                broadcaster.broadcast("messageFailed", timestamp);
              }
              @SuppressWarnings("unchecked") SettableFuture<Unit> f = (SettableFuture<Unit>)future[0];
              f.setException(exception);
              return Unit.INSTANCE;
            }
          });
        }
        return Unit.INSTANCE;
      }
    }).fail(exception -> {
      @SuppressWarnings("unchecked") SettableFuture<Unit> f = (SettableFuture<Unit>)future[0];
      f.setException(exception);
      return Unit.INSTANCE;
    });

    @SuppressWarnings("unchecked") SettableFuture<Unit> f = (SettableFuture<Unit>)future[0];
    try {
      f.get(1, TimeUnit.MINUTES);
      return SendMessageResult.success(recipient, false, true);
    } catch (Exception exception) {
      Throwable underlyingError = exception.getCause();
      if (underlyingError instanceof SnodeAPI.Error) {
        return SendMessageResult.lokiAPIError(recipient, (SnodeAPI.Error)underlyingError);
      } else {
        return SendMessageResult.networkFailure(recipient);
      }
    }
  }

  private List<AttachmentPointer> createAttachmentPointers(Optional<List<SignalServiceAttachment>> attachments, SignalServiceAddress recipient)
      throws IOException
  {
    List<AttachmentPointer> pointers = new LinkedList<>();

    if (!attachments.isPresent() || attachments.get().isEmpty()) {
      Log.w(TAG, "No attachments present...");
      return pointers;
    }

    for (SignalServiceAttachment attachment : attachments.get()) {
      if (attachment.isStream()) {
        Log.w(TAG, "Found attachment, creating pointer...");
        pointers.add(createAttachmentPointer(attachment.asStream(), recipient));
      } else if (attachment.isPointer()) {
        Log.w(TAG, "Including existing attachment pointer...");
        pointers.add(createAttachmentPointer(attachment.asPointer()));
      }
    }

    return pointers;
  }

  private AttachmentPointer createAttachmentPointer(SignalServiceAttachmentPointer attachment) {
    AttachmentPointer.Builder builder = AttachmentPointer.newBuilder()
                                                         .setContentType(attachment.getContentType())
                                                         .setId(attachment.getId())
                                                         .setKey(ByteString.copyFrom(attachment.getKey()))
                                                         .setDigest(ByteString.copyFrom(attachment.getDigest().get()))
                                                         .setSize(attachment.getSize().get())
                                                         .setUrl(attachment.getUrl());

    if (attachment.getFileName().isPresent()) {
      builder.setFileName(attachment.getFileName().get());
    }

    if (attachment.getPreview().isPresent()) {
      builder.setThumbnail(ByteString.copyFrom(attachment.getPreview().get()));
    }

    if (attachment.getWidth() > 0) {
      builder.setWidth(attachment.getWidth());
    }

    if (attachment.getHeight() > 0) {
      builder.setHeight(attachment.getHeight());
    }

    if (attachment.getVoiceNote()) {
      builder.setFlags(AttachmentPointer.Flags.VOICE_MESSAGE_VALUE);
    }

    if (attachment.getCaption().isPresent()) {
      builder.setCaption(attachment.getCaption().get());
    }

    return builder.build();
  }

  private AttachmentPointer createAttachmentPointer(SignalServiceAttachmentStream attachment, SignalServiceAddress recipient)
      throws IOException
  {
    return createAttachmentPointer(attachment, false, recipient);
  }

  private AttachmentPointer createAttachmentPointer(SignalServiceAttachmentStream attachment, boolean usePadding, SignalServiceAddress recipient)
      throws IOException
  {
    SignalServiceAttachmentPointer pointer = uploadAttachment(attachment, usePadding, recipient);
    return createAttachmentPointer(pointer);
  }

  private OutgoingPushMessageList getSessionProtocolEncryptedMessage(SignalServiceAddress recipient, long timestamp, byte[] plaintext)
  {
    List<OutgoingPushMessage> messages = new LinkedList<>();

    String publicKey = recipient.getNumber(); // Could be a contact's public key or the public key of a SSK group
    boolean isClosedGroup = apiDatabase.isClosedGroup(publicKey);
    String encryptionPublicKey;
    if (isClosedGroup) {
      ECKeyPair encryptionKeyPair = apiDatabase.getLatestClosedGroupEncryptionKeyPair(publicKey);
      encryptionPublicKey = HexEncodingKt.getHexEncodedPublicKey(encryptionKeyPair);
    } else {
      encryptionPublicKey = publicKey;
    }
    byte[] ciphertext = sessionProtocolImpl.encrypt(PushTransportDetails.getPaddedMessageBody(plaintext), encryptionPublicKey);
    String body = Base64.encodeBytes(ciphertext);
    int type = isClosedGroup ? SignalServiceProtos.Envelope.Type.CLOSED_GROUP_CIPHERTEXT_VALUE :
            SignalServiceProtos.Envelope.Type.UNIDENTIFIED_SENDER_VALUE;
    OutgoingPushMessage message = new OutgoingPushMessage(type, 1, 0, body);
    messages.add(message);

    return new OutgoingPushMessageList(publicKey, timestamp, messages, false);
  }

  public static interface EventListener {

    public void onSecurityEvent(SignalServiceAddress address);
  }
}
