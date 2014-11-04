/**
 * Copyright (C) 2013-2014 Open Whisper Systems
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

package org.thoughtcrime.securesms.transport;

import android.content.Context;
import android.util.Log;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsSmsColumns;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;
import org.thoughtcrime.securesms.mms.PartParser;
import org.thoughtcrime.securesms.push.TextSecureMessageSenderFactory;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.RecipientFormattingException;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.textsecure.api.TextSecureMessageSender;
import org.whispersystems.textsecure.api.messages.TextSecureAttachment;
import org.whispersystems.textsecure.api.messages.TextSecureAttachmentStream;
import org.whispersystems.textsecure.api.messages.TextSecureGroup;
import org.whispersystems.textsecure.api.messages.TextSecureMessage;
import org.whispersystems.textsecure.crypto.UntrustedIdentityException;
import org.whispersystems.textsecure.directory.Directory;
import org.whispersystems.textsecure.push.PushAddress;
import org.whispersystems.textsecure.push.UnregisteredUserException;
import org.whispersystems.textsecure.push.exceptions.EncapsulatedExceptions;
import org.whispersystems.textsecure.util.Base64;
import org.whispersystems.textsecure.util.InvalidNumberException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import ws.com.google.android.mms.ContentType;
import ws.com.google.android.mms.pdu.SendReq;

import static org.whispersystems.textsecure.push.PushMessageProtos.PushMessageContent.GroupContext;

public class PushTransport extends BaseTransport {

  private static final String TAG = PushTransport.class.getSimpleName();

  private final Context      context;
  private final MasterSecret masterSecret;

  public PushTransport(Context context, MasterSecret masterSecret) {
    this.context      = context.getApplicationContext();
    this.masterSecret = masterSecret;
  }

  public void deliver(SmsMessageRecord message)
      throws IOException, UntrustedIdentityException
  {
    try {
      PushAddress             address       = getPushAddress(message.getIndividualRecipient());
      TextSecureMessageSender messageSender = TextSecureMessageSenderFactory.create(context, masterSecret);

      if (message.isEndSession()) {
        messageSender.sendMessage(address, new TextSecureMessage(message.getDateSent(), null,
                                                                 null, null, true, true));
      } else {
        messageSender.sendMessage(address, new TextSecureMessage(message.getDateSent(), null,
                                                                 message.getBody().getBody()));
      }

      context.sendBroadcast(constructSentIntent(context, message.getId(), message.getType(), true, true));
    } catch (InvalidNumberException e) {
      Log.w(TAG, e);
      throw new IOException("Badly formatted number.");
    }
  }

  public void deliverGroupMessage(SendReq message)
      throws IOException, RecipientFormattingException, InvalidNumberException, EncapsulatedExceptions
  {
    TextSecureMessageSender    messageSender = TextSecureMessageSenderFactory.create(context, masterSecret);
    byte[]                     groupId       = GroupUtil.getDecodedId(message.getTo()[0].getString());
    Recipients                 recipients    = DatabaseFactory.getGroupDatabase(context).getGroupMembers(groupId, false);
    List<PushAddress>          addresses     = getPushAddresses(recipients);
    List<TextSecureAttachment> attachments   = getAttachments(message);

    if (MmsSmsColumns.Types.isGroupUpdate(message.getDatabaseMessageBox()) ||
        MmsSmsColumns.Types.isGroupQuit(message.getDatabaseMessageBox()))
    {
      String content = PartParser.getMessageText(message.getBody());

      if (content != null && !content.trim().isEmpty()) {
        GroupContext         groupContext = GroupContext.parseFrom(Base64.decode(content));
        TextSecureAttachment avatar       = attachments.isEmpty() ? null : attachments.get(0);
        TextSecureGroup.Type type         = MmsSmsColumns.Types.isGroupQuit(message.getDatabaseMessageBox()) ? TextSecureGroup.Type.QUIT : TextSecureGroup.Type.UPDATE;
        TextSecureGroup      group        = new TextSecureGroup(type, groupId, groupContext.getName(), groupContext.getMembersList(), avatar);
        TextSecureMessage    groupMessage = new TextSecureMessage(message.getSentTimestamp(), group, null, null);

        messageSender.sendMessage(addresses, groupMessage);
      }
    } else {
      String            body         = PartParser.getMessageText(message.getBody());
      TextSecureGroup   group        = new TextSecureGroup(groupId);
      TextSecureMessage groupMessage = new TextSecureMessage(message.getSentTimestamp(), group, attachments, body);

      messageSender.sendMessage(addresses, groupMessage);
    }
  }

  public void deliver(SendReq message)
      throws IOException, RecipientFormattingException, InvalidNumberException, EncapsulatedExceptions
  {
    TextSecureMessageSender messageSender = TextSecureMessageSenderFactory.create(context, masterSecret);
    String                  destination   = message.getTo()[0].getString();

    List<UntrustedIdentityException> untrustedIdentities = new LinkedList<>();
    List<UnregisteredUserException>  unregisteredUsers   = new LinkedList<>();

    if (GroupUtil.isEncodedGroup(destination)) {
      deliverGroupMessage(message);
      return;
    }

    try {
      Recipients                 recipients   = RecipientFactory.getRecipientsFromString(context, destination, false);
      PushAddress                address      = getPushAddress(recipients.getPrimaryRecipient());
      List<TextSecureAttachment> attachments  = getAttachments(message);
      String                     body         = PartParser.getMessageText(message.getBody());
      TextSecureMessage          mediaMessage = new TextSecureMessage(message.getSentTimestamp(), attachments, body);

      messageSender.sendMessage(address, mediaMessage);
    } catch (UntrustedIdentityException e) {
      Log.w(TAG, e);
      untrustedIdentities.add(e);
    } catch (UnregisteredUserException e) {
      Log.w(TAG, e);
      unregisteredUsers.add(e);
    }

    if (!untrustedIdentities.isEmpty() || !unregisteredUsers.isEmpty()) {
      throw new EncapsulatedExceptions(untrustedIdentities, unregisteredUsers);
    }
  }

  private PushAddress getPushAddress(Recipient recipient) throws InvalidNumberException {
    String e164number = Util.canonicalizeNumber(context, recipient.getNumber());
    String relay      = Directory.getInstance(context).getRelay(e164number);
    return new PushAddress(recipient.getRecipientId(), e164number, 1, relay);
  }

  private List<PushAddress> getPushAddresses(Recipients recipients) throws InvalidNumberException {
    List<PushAddress> addresses = new LinkedList<>();

    for (Recipient recipient : recipients.getRecipientsList()) {
      addresses.add(getPushAddress(recipient));
    }

    return addresses;
  }

  private List<TextSecureAttachment> getAttachments(SendReq message) {
    List<TextSecureAttachment> attachments = new LinkedList<>();

    for (int i=0;i<message.getBody().getPartsNum();i++) {
      String contentType = Util.toIsoString(message.getBody().getPart(i).getContentType());
      if (ContentType.isImageType(contentType) ||
          ContentType.isAudioType(contentType) ||
          ContentType.isVideoType(contentType))
      {
        byte[] data = message.getBody().getPart(i).getData();
        Log.w(TAG, "Adding attachment...");
        attachments.add(new TextSecureAttachmentStream(new ByteArrayInputStream(data), contentType, data.length));
      }
    }

    return attachments;
  }
}
