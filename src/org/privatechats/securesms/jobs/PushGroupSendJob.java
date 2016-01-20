package org.privatechats.securesms.jobs;

import android.content.Context;
import android.util.Log;

import org.privatechats.securesms.crypto.MasterSecret;
import org.privatechats.securesms.database.DatabaseFactory;
import org.privatechats.securesms.database.MmsDatabase;
import org.privatechats.securesms.database.NoSuchMessageException;
import org.privatechats.securesms.database.documents.NetworkFailure;
import org.privatechats.securesms.dependencies.InjectableType;
import org.privatechats.securesms.jobs.requirements.MasterSecretRequirement;
import org.privatechats.securesms.mms.OutgoingGroupMediaMessage;
import org.privatechats.securesms.mms.OutgoingMediaMessage;
import org.privatechats.securesms.recipients.Recipient;
import org.privatechats.securesms.recipients.RecipientFactory;
import org.privatechats.securesms.recipients.RecipientFormattingException;
import org.privatechats.securesms.recipients.Recipients;
import org.privatechats.securesms.transport.UndeliverableMessageException;
import org.privatechats.securesms.util.GroupUtil;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.jobqueue.requirements.NetworkRequirement;
import org.whispersystems.textsecure.api.TextSecureMessageSender;
import org.whispersystems.textsecure.api.crypto.UntrustedIdentityException;
import org.whispersystems.textsecure.api.messages.TextSecureAttachment;
import org.whispersystems.textsecure.api.messages.TextSecureDataMessage;
import org.whispersystems.textsecure.api.messages.TextSecureGroup;
import org.whispersystems.textsecure.api.push.TextSecureAddress;
import org.whispersystems.textsecure.api.push.exceptions.EncapsulatedExceptions;
import org.whispersystems.textsecure.api.push.exceptions.NetworkFailureException;
import org.whispersystems.textsecure.api.util.InvalidNumberException;
import org.whispersystems.textsecure.internal.push.TextSecureProtos.GroupContext;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import javax.inject.Inject;

import ws.com.google.android.mms.MmsException;

import static org.privatechats.securesms.dependencies.TextSecureCommunicationModule.TextSecureMessageSenderFactory;

public class PushGroupSendJob extends PushSendJob implements InjectableType {

  private static final long serialVersionUID = 1L;

  private static final String TAG = PushGroupSendJob.class.getSimpleName();

  @Inject transient TextSecureMessageSenderFactory messageSenderFactory;

  private final long messageId;
  private final long filterRecipientId;

  public PushGroupSendJob(Context context, long messageId, String destination, long filterRecipientId) {
    super(context, JobParameters.newBuilder()
                                .withPersistence()
                                .withGroupId(destination)
                                .withRequirement(new MasterSecretRequirement(context))
                                .withRequirement(new NetworkRequirement(context))
                                .withRetryCount(5)
                                .create());

    this.messageId         = messageId;
    this.filterRecipientId = filterRecipientId;
  }

  @Override
  public void onAdded() {
    DatabaseFactory.getMmsDatabase(context)
                   .markAsSending(messageId);
  }

  @Override
  public void onSend(MasterSecret masterSecret)
      throws MmsException, IOException, NoSuchMessageException
  {
    MmsDatabase          database = DatabaseFactory.getMmsDatabase(context);
    OutgoingMediaMessage message  = database.getOutgoingMessage(masterSecret, messageId);

    try {
      deliver(masterSecret, message, filterRecipientId);

      database.markAsPush(messageId);
      database.markAsSecure(messageId);
      database.markAsSent(messageId);
      markAttachmentsUploaded(messageId, message.getAttachments());
    } catch (InvalidNumberException | RecipientFormattingException | UndeliverableMessageException e) {
      Log.w(TAG, e);
      database.markAsSentFailed(messageId);
      notifyMediaMessageDeliveryFailed(context, messageId);
    } catch (EncapsulatedExceptions e) {
      Log.w(TAG, e);
      List<NetworkFailure> failures = new LinkedList<>();

      for (NetworkFailureException nfe : e.getNetworkExceptions()) {
        Recipient recipient = RecipientFactory.getRecipientsFromString(context, nfe.getE164number(), false).getPrimaryRecipient();
        failures.add(new NetworkFailure(recipient.getRecipientId()));
      }

      for (UntrustedIdentityException uie : e.getUntrustedIdentityExceptions()) {
        Recipient recipient = RecipientFactory.getRecipientsFromString(context, uie.getE164Number(), false).getPrimaryRecipient();
        database.addMismatchedIdentity(messageId, recipient.getRecipientId(), uie.getIdentityKey());
      }

      database.addFailures(messageId, failures);
      database.markAsSentFailed(messageId);
      database.markAsPush(messageId);

      notifyMediaMessageDeliveryFailed(context, messageId);
    }
  }

  @Override
  public boolean onShouldRetryThrowable(Exception exception) {
    if (exception instanceof IOException) return true;
    return false;
  }

  @Override
  public void onCanceled() {
    DatabaseFactory.getMmsDatabase(context).markAsSentFailed(messageId);
  }

  private void deliver(MasterSecret masterSecret, OutgoingMediaMessage message, long filterRecipientId)
      throws IOException, RecipientFormattingException, InvalidNumberException,
      EncapsulatedExceptions, UndeliverableMessageException
  {
    TextSecureMessageSender    messageSender = messageSenderFactory.create();
    byte[]                     groupId       = GroupUtil.getDecodedId(message.getRecipients().getPrimaryRecipient().getNumber());
    Recipients                 recipients    = DatabaseFactory.getGroupDatabase(context).getGroupMembers(groupId, false);
    List<TextSecureAttachment> attachments   = getAttachmentsFor(masterSecret, message.getAttachments());
    List<TextSecureAddress>    addresses;

    if (filterRecipientId >= 0) addresses = getPushAddresses(filterRecipientId);
    else                        addresses = getPushAddresses(recipients);

    if (message.isGroup()) {
      OutgoingGroupMediaMessage groupMessage     = (OutgoingGroupMediaMessage) message;
      GroupContext              groupContext     = groupMessage.getGroupContext();
      TextSecureAttachment      avatar           = attachments.isEmpty() ? null : attachments.get(0);
      TextSecureGroup.Type      type             = groupMessage.isGroupQuit() ? TextSecureGroup.Type.QUIT : TextSecureGroup.Type.UPDATE;
      TextSecureGroup           group            = new TextSecureGroup(type, groupId, groupContext.getName(), groupContext.getMembersList(), avatar);
      TextSecureDataMessage     groupDataMessage = new TextSecureDataMessage(message.getSentTimeMillis(), group, null, null);

      messageSender.sendMessage(addresses, groupDataMessage);
    } else {
      TextSecureGroup       group        = new TextSecureGroup(groupId);
      TextSecureDataMessage groupMessage = new TextSecureDataMessage(message.getSentTimeMillis(), group, attachments, message.getBody());

      messageSender.sendMessage(addresses, groupMessage);
    }
  }

  private List<TextSecureAddress> getPushAddresses(Recipients recipients) throws InvalidNumberException {
    List<TextSecureAddress> addresses = new LinkedList<>();

    for (Recipient recipient : recipients.getRecipientsList()) {
      addresses.add(getPushAddress(recipient.getNumber()));
    }

    return addresses;
  }

  private List<TextSecureAddress> getPushAddresses(long filterRecipientId) throws InvalidNumberException {
    List<TextSecureAddress> addresses = new LinkedList<>();
    addresses.add(getPushAddress(RecipientFactory.getRecipientForId(context, filterRecipientId, false).getNumber()));
    return addresses;
  }

}
