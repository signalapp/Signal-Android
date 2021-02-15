package org.thoughtcrime.securesms.jobs;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Stream;

import org.session.libsession.messaging.jobs.Data;
import org.session.libsession.messaging.threads.Address;
import org.session.libsession.messaging.sending_receiving.attachments.Attachment;
import org.session.libsession.messaging.sending_receiving.attachments.DatabaseAttachment;
import org.session.libsession.messaging.threads.recipients.Recipient.UnidentifiedAccessMode;
import org.session.libsession.utilities.TextSecurePreferences;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.crypto.UnidentifiedAccessUtil;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MessagingDatabase.SyncMessageId;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.NoSuchMessageException;
import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.session.libsignal.utilities.logging.Log;
import org.thoughtcrime.securesms.loki.database.LokiMessageDatabase;
import org.thoughtcrime.securesms.mms.MmsException;
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage;
import org.session.libsession.messaging.threads.recipients.Recipient;
import org.thoughtcrime.securesms.service.ExpiringMessageManager;
import org.thoughtcrime.securesms.transport.InsecureFallbackApprovalException;
import org.thoughtcrime.securesms.transport.RetryLaterException;
import org.thoughtcrime.securesms.transport.UndeliverableMessageException;
import org.session.libsignal.libsignal.util.guava.Optional;
import org.session.libsignal.service.api.SignalServiceMessageSender;
import org.session.libsignal.service.api.crypto.UnidentifiedAccessPair;
import org.session.libsignal.service.api.crypto.UntrustedIdentityException;
import org.session.libsignal.service.api.messages.SendMessageResult;
import org.session.libsignal.service.api.messages.SignalServiceAttachment;
import org.session.libsignal.service.api.messages.SignalServiceDataMessage;
import org.session.libsignal.service.api.messages.SignalServiceDataMessage.Preview;
import org.session.libsignal.service.api.messages.multidevice.SignalServiceSyncMessage;
import org.session.libsignal.service.api.messages.shared.SharedContact;
import org.session.libsignal.service.api.push.SignalServiceAddress;
import org.session.libsignal.service.api.push.exceptions.UnregisteredUserException;
import org.session.libsignal.service.loki.api.SnodeAPI;
import org.session.libsignal.service.loki.protocol.meta.SessionMetaProtocol;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import javax.inject.Inject;

public class PushMediaSendJob extends PushSendJob implements InjectableType {

  public static final String KEY = "PushMediaSendJob";

  private static final String TAG = PushMediaSendJob.class.getSimpleName();

  private static final String KEY_TEMPLATE_MESSAGE_ID = "template_message_id";
  private static final String KEY_MESSAGE_ID = "message_id";
  private static final String KEY_DESTINATION = "destination";

  @Inject SignalServiceMessageSender messageSender;

  private long messageId;
  private long templateMessageId;
  private Address destination;

  public PushMediaSendJob(long messageId, Address destination) {
    this(messageId, messageId, destination);
  }

  public PushMediaSendJob(long templateMessageId, long messageId, Address destination) {
    this(constructParameters(destination), templateMessageId, messageId, destination);
  }

  private PushMediaSendJob(@NonNull Job.Parameters parameters, long templateMessageId, long messageId, Address destination) {
    super(parameters);
    this.templateMessageId = templateMessageId;
    this.messageId = messageId;
    this.destination = destination;
  }

  public static void enqueue(@NonNull Context context, @NonNull JobManager jobManager, long messageId, @NonNull Address destination) {
    enqueue(context, jobManager, messageId, messageId, destination);
  }

  public static void enqueue(@NonNull Context context, @NonNull JobManager jobManager, long templateMessageId, long messageId, @NonNull Address destination) {
    enqueue(context, jobManager, Collections.singletonList(new PushMediaSendJob(templateMessageId, messageId, destination)));
  }

  @WorkerThread
  public static void enqueue(@NonNull Context context, @NonNull JobManager jobManager, List<PushMediaSendJob> jobs) {
    if (jobs.size() == 0) { return; }
    PushMediaSendJob first = jobs.get(0);
    long messageId = first.templateMessageId;
    try {
      List<AttachmentUploadJob> attachmentJobs = getAttachmentUploadJobs(context, messageId, first.destination);

      if (attachmentJobs.isEmpty()) {
        for (PushMediaSendJob job : jobs) { jobManager.add(job); }
      } else {
        jobManager.startChain(attachmentJobs)
                .then((List<Job>)(List)jobs)
                .enqueue();
      }
    } catch (NoSuchMessageException | MmsException e) {
      Log.w(TAG, "Failed to enqueue message.", e);
      DatabaseFactory.getMmsDatabase(context).markAsSentFailed(messageId);
      notifyMediaMessageDeliveryFailed(context, messageId);
    }
  }

  public static List<AttachmentUploadJob> getAttachmentUploadJobs(@NonNull Context context, long messageId, @NonNull Address destination)
    throws NoSuchMessageException, MmsException
  {
    MmsDatabase database = DatabaseFactory.getMmsDatabase(context);
    OutgoingMediaMessage message = database.getOutgoingMessage(messageId);
    List<Attachment> attachments = new LinkedList<>();

    attachments.addAll(message.getAttachments());
    attachments.addAll(Stream.of(message.getLinkPreviews()).filter(p -> p.getThumbnail().isPresent()).map(p -> p.getThumbnail().get()).toList());
    attachments.addAll(Stream.of(message.getSharedContacts()).filter(c -> c.getAvatar() != null).map(c -> c.getAvatar().getAttachment()).withoutNulls().toList());

    return Stream.of(attachments).map(a -> new AttachmentUploadJob(((DatabaseAttachment) a).getAttachmentId(), destination)).toList();
  }

  @Override
  public @NonNull
  Data serialize() {
    return new Data.Builder()
                   .putLong(KEY_TEMPLATE_MESSAGE_ID, templateMessageId)
                   .putLong(KEY_MESSAGE_ID, messageId)
                   .putString(KEY_DESTINATION, destination.serialize()).build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onAdded() {
    DatabaseFactory.getMmsDatabase(context).markAsSending(messageId);
  }

  @Override
  public void onPushSend()
      throws RetryLaterException, MmsException, NoSuchMessageException,
             UndeliverableMessageException
  {
    ExpiringMessageManager expirationManager = ApplicationContext.getInstance(context).getExpiringMessageManager();
    MmsDatabase            database          = DatabaseFactory.getMmsDatabase(context);
    OutgoingMediaMessage   message           = database.getOutgoingMessage(templateMessageId);

    if (messageId >= 0 && database.isSent(messageId)) {
      warn(TAG, "Message " + messageId + " was already sent. Ignoring.");
      return;
    }

    try {
      log(TAG, "Sending message: " + messageId);

      Recipient              recipient  = Recipient.from(context, destination, false);
      byte[]                 profileKey = recipient.getProfileKey();
      UnidentifiedAccessMode accessMode = recipient.getUnidentifiedAccessMode();

      boolean unidentified = deliver(message);

      if (messageId >= 0) {
        database.markAsSent(messageId, true);
        markAttachmentsUploaded(messageId, message.getAttachments());
        database.markUnidentified(messageId, unidentified);
      }

      if (recipient.isLocalNumber()) {
        SyncMessageId id = new SyncMessageId(recipient.getAddress(), message.getSentTimeMillis());
        DatabaseFactory.getMmsSmsDatabase(context).incrementDeliveryReceiptCount(id, System.currentTimeMillis());
        DatabaseFactory.getMmsSmsDatabase(context).incrementReadReceiptCount(id, System.currentTimeMillis());
      }

      if (TextSecurePreferences.isUnidentifiedDeliveryEnabled(context)) {
        if (unidentified && accessMode == UnidentifiedAccessMode.UNKNOWN && profileKey == null) {
          log(TAG, "Marking recipient as UD-unrestricted following a UD send.");
          DatabaseFactory.getRecipientDatabase(context).setUnidentifiedAccessMode(recipient, UnidentifiedAccessMode.UNRESTRICTED);
        } else if (unidentified && accessMode == UnidentifiedAccessMode.UNKNOWN) {
          log(TAG, "Marking recipient as UD-enabled following a UD send.");
          DatabaseFactory.getRecipientDatabase(context).setUnidentifiedAccessMode(recipient, UnidentifiedAccessMode.ENABLED);
        } else if (!unidentified && accessMode != UnidentifiedAccessMode.DISABLED) {
          log(TAG, "Marking recipient as UD-disabled following a non-UD send.");
          DatabaseFactory.getRecipientDatabase(context).setUnidentifiedAccessMode(recipient, UnidentifiedAccessMode.DISABLED);
        }
      }

      if (messageId > 0 && message.getExpiresIn() > 0 && !message.isExpirationUpdate()) {
        database.markExpireStarted(messageId);
        expirationManager.scheduleDeletion(messageId, true, message.getExpiresIn());
      }

      log(TAG, "Sent message: " + messageId);

    } catch (InsecureFallbackApprovalException ifae) {
      warn(TAG, "Failure", ifae);
      if (messageId >= 0) {
        database.markAsPendingInsecureSmsFallback(messageId);
        notifyMediaMessageDeliveryFailed(context, messageId);
      }
    } catch (UntrustedIdentityException uie) {
      warn(TAG, "Failure", uie);
      if (messageId >= 0) {
        database.addMismatchedIdentity(messageId, Address.Companion.fromSerialized(uie.getE164Number()), uie.getIdentityKey());
        database.markAsSentFailed(messageId);
      }
    } catch (SnodeAPI.Error e) {
      Log.d("Loki", "Couldn't send message due to error: " + e.getDescription());
      if (messageId >= 0) {
        LokiMessageDatabase lokiMessageDatabase = DatabaseFactory.getLokiMessageDatabase(context);
        lokiMessageDatabase.setErrorMessage(messageId, e.getDescription());
        database.markAsSentFailed(messageId);
      }
    }
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception exception) {
    // Loki - Disable since we have our own retrying
    return false;
  }

  @Override
  public void onCanceled() {
    if (messageId >= 0) {
      DatabaseFactory.getMmsDatabase(context).markAsSentFailed(messageId);
      notifyMediaMessageDeliveryFailed(context, messageId);
    }
  }

  private boolean deliver(OutgoingMediaMessage message)
      throws RetryLaterException, InsecureFallbackApprovalException, UntrustedIdentityException,
             UndeliverableMessageException, SnodeAPI.Error
  {
    try {
      Recipient                                  recipient          = Recipient.from(context, destination, false);
      String                                     userPublicKey      = TextSecurePreferences.getLocalNumber(context);
      SignalServiceAddress                       address            = getPushAddress(recipient.getAddress());
      SignalServiceAddress                       localAddress       = new SignalServiceAddress(userPublicKey);
      List<Attachment>                           attachments        = Stream.of(message.getAttachments()).filterNot(Attachment::isSticker).toList();
      List<SignalServiceAttachment>              serviceAttachments = getAttachmentPointersFor(attachments);
      Optional<byte[]>                           profileKey         = getProfileKey(message.getRecipient());
      Optional<SignalServiceDataMessage.Quote>   quote              = getQuoteFor(message);
      Optional<SignalServiceDataMessage.Sticker> sticker            = getStickerFor(message);
      List<SharedContact>                        sharedContacts     = getSharedContactsFor(message);
      List<Preview>                              previews           = getPreviewsFor(message);

      Optional<UnidentifiedAccessPair> unidentifiedAccessPair = UnidentifiedAccessUtil.getAccessFor(context, recipient);

      SignalServiceDataMessage mediaMessage = SignalServiceDataMessage.newBuilder()
                                                                      .withBody(message.getBody())
                                                                      .withAttachments(serviceAttachments)
                                                                      .withTimestamp(message.getSentTimeMillis())
                                                                      .withExpiration((int)(message.getExpiresIn() / 1000))
                                                                      .withProfileKey(profileKey.orNull())
                                                                      .withQuote(quote.orNull())
                                                                      .withSticker(sticker.orNull())
                                                                      .withSharedContacts(sharedContacts)
                                                                      .withPreviews(previews)
                                                                      .asExpirationUpdate(message.isExpirationUpdate())
                                                                      .build();

      SignalServiceDataMessage mediaSelfSendMessage = SignalServiceDataMessage.newBuilder()
              .withBody(message.getBody())
              .withAttachments(serviceAttachments)
              .withTimestamp(message.getSentTimeMillis())
              .withSyncTarget(destination.serialize())
              .withExpiration((int)(message.getExpiresIn() / 1000))
              .withProfileKey(profileKey.orNull())
              .withQuote(quote.orNull())
              .withSticker(sticker.orNull())
              .withSharedContacts(sharedContacts)
              .withPreviews(previews)
              .asExpirationUpdate(message.isExpirationUpdate())
              .build();

      if (SessionMetaProtocol.shared.isNoteToSelf(address.getNumber())) {
        // Loki - Device link messages don't go through here
        SendMessageResult result = messageSender.sendMessage(messageId, address, unidentifiedAccessPair, mediaMessage);
        if (result.getLokiAPIError() != null) {
          throw result.getLokiAPIError();
        } else {
          return result.getSuccess().isUnidentified();
        }
      } else {
        SendMessageResult result = messageSender.sendMessage(messageId, address, unidentifiedAccessPair, mediaMessage);
        if (result.getLokiAPIError() != null) {
          throw result.getLokiAPIError();
        } else {
          boolean isUnidentified = result.getSuccess().isUnidentified();

          try {
            // send to ourselves to sync multi-device
            Optional<UnidentifiedAccessPair> syncAccess  = UnidentifiedAccessUtil.getAccessForSync(context);
            SendMessageResult selfSendResult = messageSender.sendMessage(messageId, localAddress, syncAccess, mediaSelfSendMessage);
            if (selfSendResult.getLokiAPIError() != null) {
              throw selfSendResult.getLokiAPIError();
            }
          } catch (Exception e) {
            Log.e("Loki", "Error sending message to ourselves", e);
          }

          return isUnidentified;
        }
      }
    } catch (UnregisteredUserException e) {
      warn(TAG, e);
      throw new InsecureFallbackApprovalException(e);
    } catch (FileNotFoundException e) {
      warn(TAG, e);
      throw new UndeliverableMessageException(e);
    } catch (IOException e) {
      warn(TAG, e);
      throw new RetryLaterException(e);
    }
  }

  public static final class Factory implements Job.Factory<PushMediaSendJob> {
    @Override
    public @NonNull PushMediaSendJob create(@NonNull Parameters parameters, @NonNull Data data) {
      long templateMessageID = data.getLong(KEY_TEMPLATE_MESSAGE_ID);
      long messageID = data.getLong(KEY_MESSAGE_ID);
      Address destination = Address.Companion.fromSerialized(data.getString(KEY_DESTINATION));
      return new PushMediaSendJob(parameters, templateMessageID, messageID, destination);
    }
  }
}
