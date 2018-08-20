package org.thoughtcrime.securesms.jobs;

import android.content.Context;
import android.support.annotation.NonNull;

import org.greenrobot.eventbus.EventBus;
import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.TextSecureExpiredException;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.contactshare.Contact;
import org.thoughtcrime.securesms.contactshare.ContactModelMapper;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.events.PartProgressEvent;
import org.thoughtcrime.securesms.jobmanager.JobParameters;
import org.thoughtcrime.securesms.jobmanager.requirements.NetworkBackoffRequirement;
import org.thoughtcrime.securesms.jobs.requirements.MasterSecretRequirement;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader;
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.BitmapDecodingException;
import org.thoughtcrime.securesms.util.BitmapUtil;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.shared.SharedContact;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public abstract class PushSendJob extends SendJob {

  private static final long   serialVersionUID = 5906098204770900739L;
  private static final String TAG              = PushSendJob.class.getSimpleName();

  protected PushSendJob(Context context, JobParameters parameters) {
    super(context, parameters);
  }

  protected static JobParameters constructParameters(Context context, Address destination) {
    JobParameters.Builder builder = JobParameters.newBuilder();
    builder.withPersistence();
    builder.withGroupId(destination.serialize());
    builder.withRequirement(new MasterSecretRequirement(context));
    builder.withRequirement(new NetworkBackoffRequirement(context));
    builder.withRetryDuration(TimeUnit.DAYS.toMillis(1));

    return builder.create();
  }

  @Override
  protected final void onSend(MasterSecret masterSecret) throws Exception {
    if (TextSecurePreferences.getSignedPreKeyFailureCount(context) > 5) {
      ApplicationContext.getInstance(context)
                        .getJobManager()
                        .add(new RotateSignedPreKeyJob(context));

      throw new TextSecureExpiredException("Too many signed prekey rotation failures");
    }

    Log.i(TAG, "Starting message send attempt");
    onPushSend();
    Log.i(TAG, "Message send completed");
  }

  @Override
  public void onRetry() {
    super.onRetry();
    Log.i(TAG, "onRetry()");

    if (getRunIteration() > 1) {
      Log.i(TAG, "Scheduling service outage detection job.");
      ApplicationContext.getInstance(context).getJobManager().add(new ServiceOutageDetectionJob(context));
    }
  }

  protected Optional<byte[]> getProfileKey(@NonNull Recipient recipient) {
    if (!recipient.resolve().isSystemContact() && !recipient.resolve().isProfileSharing()) {
      return Optional.absent();
    }

    return Optional.of(ProfileKeyUtil.getProfileKey(context));
  }

  protected SignalServiceAddress getPushAddress(Address address) {
//    String relay = TextSecureDirectory.getInstance(context).getRelay(address.toPhoneString());
    String relay = null;
    return new SignalServiceAddress(address.toPhoneString(), Optional.fromNullable(relay));
  }

  protected List<SignalServiceAttachment> getAttachmentsFor(List<Attachment> parts) {
    List<SignalServiceAttachment> attachments = new LinkedList<>();

    for (final Attachment attachment : parts) {
      SignalServiceAttachment converted = getAttachmentFor(attachment);
      if (converted != null) {
        attachments.add(converted);
      }
    }

    return attachments;
  }

  protected SignalServiceAttachment getAttachmentFor(Attachment attachment) {
    try {
      if (attachment.getDataUri() == null || attachment.getSize() == 0) throw new IOException("Assertion failed, outgoing attachment has no data!");
      InputStream is = PartAuthority.getAttachmentStream(context, attachment.getDataUri());
      return SignalServiceAttachment.newStreamBuilder()
                                    .withStream(is)
                                    .withContentType(attachment.getContentType())
                                    .withLength(attachment.getSize())
                                    .withFileName(attachment.getFileName())
                                    .withVoiceNote(attachment.isVoiceNote())
                                    .withWidth(attachment.getWidth())
                                    .withHeight(attachment.getHeight())
                                    .withListener((total, progress) -> EventBus.getDefault().postSticky(new PartProgressEvent(attachment, total, progress)))
                                    .build();
    } catch (IOException ioe) {
      Log.w(TAG, "Couldn't open attachment", ioe);
    }
    return null;
  }

  protected void notifyMediaMessageDeliveryFailed(Context context, long messageId) {
    long      threadId  = DatabaseFactory.getMmsDatabase(context).getThreadIdForMessage(messageId);
    Recipient recipient = DatabaseFactory.getThreadDatabase(context).getRecipientForThreadId(threadId);

    if (threadId != -1 && recipient != null) {
      MessageNotifier.notifyMessageDeliveryFailed(context, recipient, threadId);
    }
  }

  protected Optional<SignalServiceDataMessage.Quote> getQuoteFor(OutgoingMediaMessage message) {
    if (message.getOutgoingQuote() == null) return Optional.absent();

    long                                                  quoteId          = message.getOutgoingQuote().getId();
    String                                                quoteBody        = message.getOutgoingQuote().getText();
    Address                                               quoteAuthor      = message.getOutgoingQuote().getAuthor();
    List<SignalServiceDataMessage.Quote.QuotedAttachment> quoteAttachments = new LinkedList<>();

    for (Attachment attachment : message.getOutgoingQuote().getAttachments()) {
      BitmapUtil.ScaleResult  thumbnailData = null;
      SignalServiceAttachment thumbnail     = null;

      try {
        if (MediaUtil.isImageType(attachment.getContentType()) && attachment.getDataUri() != null) {
          thumbnailData = BitmapUtil.createScaledBytes(context, new DecryptableStreamUriLoader.DecryptableUri(attachment.getDataUri()), 100, 100, 500 * 1024);
        } else if (MediaUtil.isVideoType(attachment.getContentType()) && attachment.getThumbnailUri() != null) {
          thumbnailData = BitmapUtil.createScaledBytes(context, new DecryptableStreamUriLoader.DecryptableUri(attachment.getThumbnailUri()), 100, 100, 500 * 1024);
        }

        if (thumbnailData != null) {
          thumbnail = SignalServiceAttachment.newStreamBuilder()
                                             .withContentType("image/jpeg")
                                             .withWidth(thumbnailData.getWidth())
                                             .withHeight(thumbnailData.getHeight())
                                             .withLength(thumbnailData.getBitmap().length)
                                             .withStream(new ByteArrayInputStream(thumbnailData.getBitmap()))
                                             .build();
        }

        quoteAttachments.add(new SignalServiceDataMessage.Quote.QuotedAttachment(attachment.getContentType(),
                                                                                 attachment.getFileName(),
                                                                                 thumbnail));
      } catch (BitmapDecodingException e) {
        Log.w(TAG, e);
      }
    }

    return Optional.of(new SignalServiceDataMessage.Quote(quoteId, new SignalServiceAddress(quoteAuthor.serialize()), quoteBody, quoteAttachments));
  }

  List<SharedContact> getSharedContactsFor(OutgoingMediaMessage mediaMessage) {
    List<SharedContact> sharedContacts = new LinkedList<>();

    for (Contact contact : mediaMessage.getSharedContacts()) {
      SharedContact.Builder builder = ContactModelMapper.localToRemoteBuilder(contact);
      SharedContact.Avatar  avatar  = null;

      if (contact.getAvatar() != null && contact.getAvatar().getAttachment() != null) {
        avatar = SharedContact.Avatar.newBuilder().withAttachment(getAttachmentFor(contact.getAvatarAttachment()))
                                                  .withProfileFlag(contact.getAvatar().isProfile())
                                                  .build();
      }

      builder.setAvatar(avatar);
      sharedContacts.add(builder.build());
    }

    return sharedContacts;
  }

  protected abstract void onPushSend() throws Exception;
}
