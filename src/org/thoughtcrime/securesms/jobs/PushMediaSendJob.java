package org.thoughtcrime.securesms.jobs;

import android.content.Context;
import android.util.Log;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.storage.TextSecureAxolotlStore;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.NoSuchMessageException;
import org.thoughtcrime.securesms.mms.PartParser;
import org.thoughtcrime.securesms.push.TextSecureMessageSenderFactory;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.RecipientFormattingException;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.sms.IncomingIdentityUpdateMessage;
import org.thoughtcrime.securesms.transport.InsecureFallbackApprovalException;
import org.thoughtcrime.securesms.transport.RetryLaterException;
import org.thoughtcrime.securesms.transport.SecureFallbackApprovalException;
import org.whispersystems.libaxolotl.state.AxolotlStore;
import org.whispersystems.textsecure.api.TextSecureMessageSender;
import org.whispersystems.textsecure.api.messages.TextSecureAttachment;
import org.whispersystems.textsecure.api.messages.TextSecureMessage;
import org.whispersystems.textsecure.api.crypto.UntrustedIdentityException;
import org.whispersystems.textsecure.push.PushAddress;
import org.whispersystems.textsecure.push.UnregisteredUserException;
import org.whispersystems.textsecure.storage.RecipientDevice;
import org.whispersystems.textsecure.util.InvalidNumberException;

import java.io.IOException;
import java.util.List;

import ws.com.google.android.mms.MmsException;
import ws.com.google.android.mms.pdu.SendReq;

public class PushMediaSendJob extends PushSendJob {

  private static final String TAG = PushMediaSendJob.class.getSimpleName();

  private final long messageId;

  public PushMediaSendJob(Context context, long messageId, String destination) {
    super(context, constructParameters(context, destination));
    this.messageId = messageId;
  }

  @Override
  public void onAdded() {

  }

  @Override
  public void onRun()
      throws RequirementNotMetException, RetryLaterException, MmsException, NoSuchMessageException
  {
    MasterSecret masterSecret = getMasterSecret();
    MmsDatabase  database     = DatabaseFactory.getMmsDatabase(context);
    SendReq      message      = database.getOutgoingMessage(masterSecret, messageId);

    try {
      deliver(masterSecret, message);

      database.markAsPush(messageId);
      database.markAsSecure(messageId);
      database.markAsSent(messageId, "push".getBytes(), 0);
    } catch (InsecureFallbackApprovalException ifae) {
      Log.w(TAG, ifae);
      database.markAsPendingInsecureSmsFallback(messageId);
      notifyMediaMessageDeliveryFailed(context, messageId);
    } catch (SecureFallbackApprovalException sfae) {
      Log.w(TAG, sfae);
      database.markAsPendingSecureSmsFallback(messageId);
      notifyMediaMessageDeliveryFailed(context, messageId);
    } catch (UntrustedIdentityException uie) {
      IncomingIdentityUpdateMessage identityUpdateMessage = IncomingIdentityUpdateMessage.createFor(message.getTo()[0].getString(), uie.getIdentityKey());
      DatabaseFactory.getEncryptingSmsDatabase(context).insertMessageInbox(masterSecret, identityUpdateMessage);
      database.markAsSentFailed(messageId);
    }
  }

  @Override
  public void onCanceled() {
    DatabaseFactory.getMmsDatabase(context).markAsSentFailed(messageId);
    notifyMediaMessageDeliveryFailed(context, messageId);
  }

  @Override
  public boolean onShouldRetry(Throwable throwable) {
    if (throwable instanceof RetryLaterException)        return true;
    if (throwable instanceof RequirementNotMetException) return true;
    return false;
  }

  private void deliver(MasterSecret masterSecret, SendReq message)
      throws RetryLaterException, SecureFallbackApprovalException,
             InsecureFallbackApprovalException, UntrustedIdentityException
  {
    MmsDatabase             database               = DatabaseFactory.getMmsDatabase(context);
    TextSecureMessageSender messageSender          = TextSecureMessageSenderFactory.create(context, masterSecret);
    String                  destination            = message.getTo()[0].getString();
    boolean                 isSmsFallbackSupported = isSmsFallbackSupported(context, destination);

    try {
      Recipients                 recipients   = RecipientFactory.getRecipientsFromString(context, destination, false);
      PushAddress                address      = getPushAddress(recipients.getPrimaryRecipient());
      List<TextSecureAttachment> attachments  = getAttachments(message);
      String                     body         = PartParser.getMessageText(message.getBody());
      TextSecureMessage          mediaMessage = new TextSecureMessage(message.getSentTimestamp(), attachments, body);

      messageSender.sendMessage(address, mediaMessage);
    } catch (InvalidNumberException | UnregisteredUserException e) {
      Log.w(TAG, e);
      if (isSmsFallbackSupported) fallbackOrAskApproval(masterSecret, message, destination);
      else                        database.markAsSentFailed(messageId);
    } catch (IOException | RecipientFormattingException e) {
      Log.w(TAG, e);
      if (isSmsFallbackSupported) fallbackOrAskApproval(masterSecret, message, destination);
      else                        throw new RetryLaterException(e);
    }
  }

  private void fallbackOrAskApproval(MasterSecret masterSecret, SendReq mediaMessage, String destination)
      throws SecureFallbackApprovalException, InsecureFallbackApprovalException
  {
    try {
      Recipient    recipient                     = RecipientFactory.getRecipientsFromString(context, destination, false).getPrimaryRecipient();
      boolean      isSmsFallbackApprovalRequired = isSmsFallbackApprovalRequired(destination);
      AxolotlStore axolotlStore                  = new TextSecureAxolotlStore(context, masterSecret);

      if (!isSmsFallbackApprovalRequired) {
        Log.w(TAG, "Falling back to MMS");
        DatabaseFactory.getMmsDatabase(context).markAsForcedSms(mediaMessage.getDatabaseMessageId());
        ApplicationContext.getInstance(context).getJobManager().add(new MmsSendJob(context, messageId));
      } else if (!axolotlStore.containsSession(recipient.getRecipientId(), RecipientDevice.DEFAULT_DEVICE_ID)) {
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
