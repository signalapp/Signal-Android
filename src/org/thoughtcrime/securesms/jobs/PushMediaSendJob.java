package org.thoughtcrime.securesms.jobs;

import android.content.Context;
import android.util.Log;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.storage.TextSecureAxolotlStore;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.NoSuchMessageException;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.thoughtcrime.securesms.mms.MediaConstraints;
import org.thoughtcrime.securesms.mms.PartParser;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.RecipientFormattingException;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.transport.InsecureFallbackApprovalException;
import org.thoughtcrime.securesms.transport.RetryLaterException;
import org.thoughtcrime.securesms.transport.SecureFallbackApprovalException;
import org.thoughtcrime.securesms.transport.UndeliverableMessageException;
import org.whispersystems.libaxolotl.state.AxolotlStore;
import org.whispersystems.textsecure.api.TextSecureMessageSender;
import org.whispersystems.textsecure.api.crypto.UntrustedIdentityException;
import org.whispersystems.textsecure.api.messages.TextSecureAttachment;
import org.whispersystems.textsecure.api.messages.TextSecureMessage;
import org.whispersystems.textsecure.api.push.PushAddress;
import org.whispersystems.textsecure.api.push.exceptions.UnregisteredUserException;
import org.whispersystems.textsecure.api.util.InvalidNumberException;

import java.io.IOException;
import java.util.List;

import javax.inject.Inject;

import ws.com.google.android.mms.MmsException;
import ws.com.google.android.mms.pdu.SendReq;

import static org.thoughtcrime.securesms.dependencies.TextSecureCommunicationModule.TextSecureMessageSenderFactory;

public class PushMediaSendJob extends PushSendJob implements InjectableType {

  private static final String TAG = PushMediaSendJob.class.getSimpleName();

  @Inject transient TextSecureMessageSenderFactory messageSenderFactory;

  private final long messageId;

  public PushMediaSendJob(Context context, long messageId, String destination) {
    super(context, constructParameters(context, destination, true));
    this.messageId = messageId;
  }

  @Override
  public void onAdded() {
    MmsDatabase mmsDatabase = DatabaseFactory.getMmsDatabase(context);
    mmsDatabase.markAsSending(messageId);
    mmsDatabase.markAsPush(messageId);
  }

  @Override
  public void onSend(MasterSecret masterSecret)
      throws RetryLaterException, MmsException, NoSuchMessageException,
             UndeliverableMessageException, RecipientFormattingException
  {
    MmsDatabase database = DatabaseFactory.getMmsDatabase(context);
    SendReq     message  = database.getOutgoingMessage(masterSecret, messageId);

    try {
      if (deliver(masterSecret, message)) {
        database.markAsPush(messageId);
        database.markAsSecure(messageId);
        database.markAsSent(messageId, "push".getBytes(), 0);
      }
    } catch (InsecureFallbackApprovalException ifae) {
      Log.w(TAG, ifae);
      database.markAsPendingInsecureSmsFallback(messageId);
      notifyMediaMessageDeliveryFailed(context, messageId);
    } catch (SecureFallbackApprovalException sfae) {
      Log.w(TAG, sfae);
      database.markAsPendingSecureSmsFallback(messageId);
      notifyMediaMessageDeliveryFailed(context, messageId);
    } catch (UntrustedIdentityException uie) {
      Log.w(TAG, uie);
      Recipients recipients  = RecipientFactory.getRecipientsFromString(context, uie.getE164Number(), false);
      long       recipientId = recipients.getPrimaryRecipient().getRecipientId();

      database.addMismatchedIdentity(messageId, recipientId, uie.getIdentityKey());
      database.markAsSentFailed(messageId);
      database.markAsPush(messageId);
    }
  }

  @Override
  public boolean onShouldRetryThrowable(Exception exception) {
    if (exception instanceof RequirementNotMetException) return true;
    return false;
  }

  @Override
  public void onCanceled() {
    DatabaseFactory.getMmsDatabase(context).markAsSentFailed(messageId);
    notifyMediaMessageDeliveryFailed(context, messageId);
  }


  private boolean deliver(MasterSecret masterSecret, SendReq message)
      throws RetryLaterException, SecureFallbackApprovalException,
             InsecureFallbackApprovalException, UntrustedIdentityException,
             UndeliverableMessageException
  {
    MmsDatabase             database               = DatabaseFactory.getMmsDatabase(context);
    TextSecureMessageSender messageSender          = messageSenderFactory.create(masterSecret);
    String                  destination            = message.getTo()[0].getString();
    boolean                 isSmsFallbackSupported = isSmsFallbackSupported(context, destination, true);

    try {
      prepareMessageMedia(masterSecret, message, MediaConstraints.PUSH_CONSTRAINTS, false);
      Recipients                 recipients   = RecipientFactory.getRecipientsFromString(context, destination, false);
      PushAddress                address      = getPushAddress(recipients.getPrimaryRecipient());
      List<TextSecureAttachment> attachments  = getAttachments(masterSecret, message);
      String                     body         = PartParser.getMessageText(message.getBody());
      TextSecureMessage          mediaMessage = new TextSecureMessage(message.getSentTimestamp(), attachments, body);

      messageSender.sendMessage(address, mediaMessage);
      return true;
    } catch (InvalidNumberException | UnregisteredUserException e) {
      Log.w(TAG, e);
      if (isSmsFallbackSupported) fallbackOrAskApproval(masterSecret, message, destination);
      else                        database.markAsSentFailed(messageId);
    } catch (IOException | RecipientFormattingException e) {
      Log.w(TAG, e);
      if (isSmsFallbackSupported) fallbackOrAskApproval(masterSecret, message, destination);
      else                        throw new RetryLaterException(e);
    }
    return false;
  }

  private void fallbackOrAskApproval(MasterSecret masterSecret, SendReq mediaMessage, String destination)
      throws SecureFallbackApprovalException, InsecureFallbackApprovalException
  {
    try {
      Recipient    recipient                     = RecipientFactory.getRecipientsFromString(context, destination, false).getPrimaryRecipient();
      boolean      isSmsFallbackApprovalRequired = isSmsFallbackApprovalRequired(destination, true);
      AxolotlStore axolotlStore                  = new TextSecureAxolotlStore(context, masterSecret);

      if (!isSmsFallbackApprovalRequired) {
        Log.w(TAG, "Falling back to MMS");
        DatabaseFactory.getMmsDatabase(context).markAsForcedSms(mediaMessage.getDatabaseMessageId());
        ApplicationContext.getInstance(context).getJobManager().add(new MmsSendJob(context, messageId));
      } else if (!axolotlStore.containsSession(recipient.getRecipientId(), PushAddress.DEFAULT_DEVICE_ID)) {
        Log.w(TAG, "Marking message as pending insecure SMS fallback");
        throw new InsecureFallbackApprovalException("Pending user approval for fallback to insecure SMS");
      } else {
        Log.w(TAG, "Marking message as pending secure SMS fallback");
        throw new SecureFallbackApprovalException("Pending user approval for fallback secure to SMS");
      }
    } catch (RecipientFormattingException rfe) {
      Log.w(TAG, rfe);
      DatabaseFactory.getMmsDatabase(context).markAsSentFailed(messageId);
    }
  }


}
