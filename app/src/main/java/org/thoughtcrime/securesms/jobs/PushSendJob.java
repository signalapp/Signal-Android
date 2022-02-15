package org.thoughtcrime.securesms.jobs;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.signal.core.util.logging.Log;
import org.signal.libsignal.metadata.certificate.InvalidCertificateException;
import org.signal.libsignal.metadata.certificate.SenderCertificate;
import org.thoughtcrime.securesms.TextSecureExpiredException;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.attachments.DatabaseAttachment;
import org.thoughtcrime.securesms.blurhash.BlurHash;
import org.thoughtcrime.securesms.contactshare.Contact;
import org.thoughtcrime.securesms.contactshare.ContactModelMapper;
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.Mention;
import org.thoughtcrime.securesms.database.model.StickerRecord;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.events.PartProgressEvent;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.jobmanager.impl.BackoffUtil;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.keyvalue.CertificateType;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.linkpreview.LinkPreview;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader;
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.net.NotPushRegisteredException;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.transport.RetryLaterException;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.BitmapDecodingException;
import org.thoughtcrime.securesms.util.BitmapUtil;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.Hex;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccessPair;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentRemoteId;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage.Preview;
import org.whispersystems.signalservice.api.messages.multidevice.SentTranscriptMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.messages.shared.SharedContact;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.signalservice.api.push.exceptions.ProofRequiredException;
import org.whispersystems.signalservice.api.push.exceptions.ServerRejectedException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public abstract class PushSendJob extends SendJob {

  private static final String TAG                           = Log.tag(PushSendJob.class);
  private static final long   CERTIFICATE_EXPIRATION_BUFFER = TimeUnit.DAYS.toMillis(1);
  private static final long   PUSH_CHALLENGE_TIMEOUT        = TimeUnit.SECONDS.toMillis(10);

  protected PushSendJob(Job.Parameters parameters) {
    super(parameters);
  }

  protected static Job.Parameters constructParameters(@NonNull Recipient recipient, boolean hasMedia) {
    return new Parameters.Builder()
                         .setQueue(recipient.getId().toQueueKey(hasMedia))
                         .addConstraint(NetworkConstraint.KEY)
                         .setLifespan(TimeUnit.DAYS.toMillis(1))
                         .setMaxAttempts(Parameters.UNLIMITED)
                         .build();
  }

  @Override
  protected final void onSend() throws Exception {
    if (TextSecurePreferences.getSignedPreKeyFailureCount(context) > 5) {
      ApplicationDependencies.getJobManager().add(new RotateSignedPreKeyJob());
      throw new TextSecureExpiredException("Too many signed prekey rotation failures");
    }

    if (!Recipient.self().isRegistered()) {
      throw new NotPushRegisteredException();
    }

    onPushSend();

    if (SignalStore.rateLimit().needsRecaptcha()) {
      Log.i(TAG, "Successfully sent message. Assuming reCAPTCHA no longer needed.");
      SignalStore.rateLimit().onProofAccepted();
    }
  }

  @Override
  public void onRetry() {
    Log.i(TAG, "onRetry()");

    if (getRunAttempt() > 1) {
      Log.i(TAG, "Scheduling service outage detection job.");
      ApplicationDependencies.getJobManager().add(new ServiceOutageDetectionJob());
    }
  }

  @Override
  protected boolean shouldTrace() {
    return true;
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception exception) {
    if (exception instanceof ServerRejectedException) {
      return false;
    }

    if (exception instanceof NotPushRegisteredException) {
      return false;
    }

    return exception instanceof IOException         ||
           exception instanceof RetryLaterException ||
           exception instanceof ProofRequiredException;
  }

  @Override
  public long getNextRunAttemptBackoff(int pastAttemptCount, @NonNull Exception exception) {
    if (exception instanceof ProofRequiredException) {
      long backoff = ((ProofRequiredException) exception).getRetryAfterSeconds();
      warn(TAG, "[Proof Required] Retry-After is " + backoff + " seconds.");
      if (backoff >= 0) {
        return TimeUnit.SECONDS.toMillis(backoff);
      }
    } else if (exception instanceof NonSuccessfulResponseCodeException) {
      if (((NonSuccessfulResponseCodeException) exception).is5xx()) {
        return BackoffUtil.exponentialBackoff(pastAttemptCount, FeatureFlags.getServerErrorMaxBackoff());
      }
    } else if (exception instanceof RetryLaterException) {
      long backoff = ((RetryLaterException) exception).getBackoff();
      if (backoff >= 0) {
        return backoff;
      }
    }

    return super.getNextRunAttemptBackoff(pastAttemptCount, exception);
  }

  protected Optional<byte[]> getProfileKey(@NonNull Recipient recipient) {
    if (!recipient.resolve().isSystemContact() && !recipient.resolve().isProfileSharing()) {
      return Optional.absent();
    }

    return Optional.of(ProfileKeyUtil.getProfileKey(context));
  }

  protected SignalServiceAttachment getAttachmentFor(Attachment attachment) {
    try {
      if (attachment.getUri() == null || attachment.getSize() == 0) throw new IOException("Assertion failed, outgoing attachment has no data!");
      InputStream is = PartAuthority.getAttachmentStream(context, attachment.getUri());
      return SignalServiceAttachment.newStreamBuilder()
                                    .withStream(is)
                                    .withContentType(attachment.getContentType())
                                    .withLength(attachment.getSize())
                                    .withFileName(attachment.getFileName())
                                    .withVoiceNote(attachment.isVoiceNote())
                                    .withBorderless(attachment.isBorderless())
                                    .withGif(attachment.isVideoGif())
                                    .withWidth(attachment.getWidth())
                                    .withHeight(attachment.getHeight())
                                    .withCaption(attachment.getCaption())
                                    .withListener((total, progress) -> EventBus.getDefault().postSticky(new PartProgressEvent(attachment, PartProgressEvent.Type.NETWORK, total, progress)))
                                    .build();
    } catch (IOException ioe) {
      Log.w(TAG, "Couldn't open attachment", ioe);
    }
    return null;
  }

  protected static Set<String> enqueueCompressingAndUploadAttachmentsChains(@NonNull JobManager jobManager, OutgoingMediaMessage message) {
    List<Attachment> attachments = new LinkedList<>();

    attachments.addAll(message.getAttachments());

    attachments.addAll(Stream.of(message.getLinkPreviews())
                             .map(LinkPreview::getThumbnail)
                             .filter(Optional::isPresent)
                             .map(Optional::get)
                             .toList());

    attachments.addAll(Stream.of(message.getSharedContacts())
                             .map(Contact::getAvatar).withoutNulls()
                             .map(Contact.Avatar::getAttachment).withoutNulls()
                             .toList());

    return new HashSet<>(Stream.of(attachments).map(a -> {
                                                 AttachmentUploadJob attachmentUploadJob = new AttachmentUploadJob(((DatabaseAttachment) a).getAttachmentId());

                                                 if (message.isGroup()) {
                                                   jobManager.startChain(AttachmentCompressionJob.fromAttachment((DatabaseAttachment) a, false, -1))
                                                             .then(attachmentUploadJob)
                                                             .enqueue();
                                                 } else {
                                                   jobManager.startChain(AttachmentCompressionJob.fromAttachment((DatabaseAttachment) a, false, -1))
                                                             .then(new ResumableUploadSpecJob())
                                                             .then(attachmentUploadJob)
                                                             .enqueue();
                                                 }

                                                 return attachmentUploadJob.getId();
                                               })
                                               .toList());
  }

  protected @NonNull List<SignalServiceAttachment> getAttachmentPointersFor(List<Attachment> attachments) {
    return Stream.of(attachments).map(this::getAttachmentPointerFor).filter(a -> a != null).toList();
  }

  protected @Nullable SignalServiceAttachment getAttachmentPointerFor(Attachment attachment) {
    if (TextUtils.isEmpty(attachment.getLocation())) {
      Log.w(TAG, "empty content id");
      return null;
    }

    if (TextUtils.isEmpty(attachment.getKey())) {
      Log.w(TAG, "empty encrypted key");
      return null;
    }

    try {
      final SignalServiceAttachmentRemoteId remoteId = SignalServiceAttachmentRemoteId.from(attachment.getLocation());
      final byte[]                          key      = Base64.decode(attachment.getKey());

      int width  = attachment.getWidth();
      int height = attachment.getHeight();

      if ((width == 0 || height == 0) && MediaUtil.hasVideoThumbnail(context, attachment.getUri())) {
        Bitmap thumbnail = MediaUtil.getVideoThumbnail(context, attachment.getUri(), 1000);

        if (thumbnail != null) {
          width  = thumbnail.getWidth();
          height = thumbnail.getHeight();
        }
      }

      return new SignalServiceAttachmentPointer(attachment.getCdnNumber(),
                                                remoteId,
                                                attachment.getContentType(),
                                                key,
                                                Optional.of(Util.toIntExact(attachment.getSize())),
                                                Optional.absent(),
                                                width,
                                                height,
                                                Optional.fromNullable(attachment.getDigest()),
                                                Optional.fromNullable(attachment.getFileName()),
                                                attachment.isVoiceNote(),
                                                attachment.isBorderless(),
                                                attachment.isVideoGif(),
                                                Optional.fromNullable(attachment.getCaption()),
                                                Optional.fromNullable(attachment.getBlurHash()).transform(BlurHash::getHash),
                                                attachment.getUploadTimestamp());
    } catch (IOException | ArithmeticException e) {
      Log.w(TAG, e);
      return null;
    }
  }

  protected static void notifyMediaMessageDeliveryFailed(Context context, long messageId) {
    long      threadId  = SignalDatabase.mms().getThreadIdForMessage(messageId);
    Recipient recipient = SignalDatabase.threads().getRecipientForThreadId(threadId);

    if (threadId != -1 && recipient != null) {
      ApplicationDependencies.getMessageNotifier().notifyMessageDeliveryFailed(context, recipient, threadId);
    }
  }

  protected Optional<SignalServiceDataMessage.Quote> getQuoteFor(OutgoingMediaMessage message) throws IOException {
    if (message.getOutgoingQuote() == null) return Optional.absent();

    long                                                  quoteId             = message.getOutgoingQuote().getId();
    String                                                quoteBody           = message.getOutgoingQuote().getText();
    RecipientId                                           quoteAuthor         = message.getOutgoingQuote().getAuthor();
    List<SignalServiceDataMessage.Mention>                quoteMentions       = getMentionsFor(message.getOutgoingQuote().getMentions());
    List<SignalServiceDataMessage.Quote.QuotedAttachment> quoteAttachments    = new LinkedList<>();
    List<Attachment>                                      filteredAttachments = Stream.of(message.getOutgoingQuote().getAttachments())
                                                                                      .filterNot(a -> MediaUtil.isViewOnceType(a.getContentType()))
                                                                                      .toList();

    for (Attachment attachment : filteredAttachments) {
      BitmapUtil.ScaleResult  thumbnailData = null;
      SignalServiceAttachment thumbnail     = null;
      String                  thumbnailType = MediaUtil.IMAGE_JPEG;

      try {
        if (MediaUtil.isImageType(attachment.getContentType()) && attachment.getUri() != null) {
          Bitmap.CompressFormat format = BitmapUtil.getCompressFormatForContentType(attachment.getContentType());

          thumbnailData = BitmapUtil.createScaledBytes(context, new DecryptableStreamUriLoader.DecryptableUri(attachment.getUri()), 100, 100, 500 * 1024, format);
          thumbnailType = attachment.getContentType();
        } else if (Build.VERSION.SDK_INT >= 23 && MediaUtil.isVideoType(attachment.getContentType()) && attachment.getUri() != null) {
          Bitmap bitmap = MediaUtil.getVideoThumbnail(context, attachment.getUri(), 1000);

          if (bitmap != null) {
            thumbnailData = BitmapUtil.createScaledBytes(context, bitmap, 100, 100, 500 * 1024);
          }
        }

        if (thumbnailData != null) {
          SignalServiceAttachment.Builder builder = SignalServiceAttachment.newStreamBuilder()
                                                                           .withContentType(thumbnailType)
                                                                           .withWidth(thumbnailData.getWidth())
                                                                           .withHeight(thumbnailData.getHeight())
                                                                           .withLength(thumbnailData.getBitmap().length)
                                                                           .withStream(new ByteArrayInputStream(thumbnailData.getBitmap()))
                                                                           .withResumableUploadSpec(ApplicationDependencies.getSignalServiceMessageSender().getResumableUploadSpec());

          thumbnail = builder.build();
        }

        quoteAttachments.add(new SignalServiceDataMessage.Quote.QuotedAttachment(attachment.isVideoGif() ? MediaUtil.IMAGE_GIF : attachment.getContentType(),
                                                                                 attachment.getFileName(),
                                                                                 thumbnail));
      } catch (BitmapDecodingException e) {
        Log.w(TAG, e);
      }
    }

    Recipient quoteAuthorRecipient = Recipient.resolved(quoteAuthor);

    if (quoteAuthorRecipient.isMaybeRegistered()) {
      SignalServiceAddress quoteAddress = RecipientUtil.toSignalServiceAddress(context, quoteAuthorRecipient);
      return Optional.of(new SignalServiceDataMessage.Quote(quoteId, quoteAddress, quoteBody, quoteAttachments, quoteMentions));
    } else {
      return Optional.absent();
    }
  }

  protected Optional<SignalServiceDataMessage.Sticker> getStickerFor(OutgoingMediaMessage message) {
    Attachment stickerAttachment = Stream.of(message.getAttachments()).filter(Attachment::isSticker).findFirst().orElse(null);

    if (stickerAttachment == null) {
      return Optional.absent();
    }

    try {
      byte[]                  packId     = Hex.fromStringCondensed(stickerAttachment.getSticker().getPackId());
      byte[]                  packKey    = Hex.fromStringCondensed(stickerAttachment.getSticker().getPackKey());
      int                     stickerId  = stickerAttachment.getSticker().getStickerId();
      StickerRecord           record     = SignalDatabase.stickers().getSticker(stickerAttachment.getSticker().getPackId(), stickerId, false);
      String                  emoji      = record != null ? record.getEmoji() : null;
      SignalServiceAttachment attachment = getAttachmentPointerFor(stickerAttachment);

      return Optional.of(new SignalServiceDataMessage.Sticker(packId, packKey, stickerId, emoji, attachment));
    } catch (IOException e) {
      Log.w(TAG, "Failed to decode sticker id/key", e);
      return Optional.absent();
    }
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

  List<Preview> getPreviewsFor(OutgoingMediaMessage mediaMessage) {
    return Stream.of(mediaMessage.getLinkPreviews()).map(lp -> {
      SignalServiceAttachment attachment = lp.getThumbnail().isPresent() ? getAttachmentPointerFor(lp.getThumbnail().get()) : null;
      return new Preview(lp.getUrl(), lp.getTitle(), lp.getDescription(), lp.getDate(), Optional.fromNullable(attachment));
    }).toList();
  }

  List<SignalServiceDataMessage.Mention> getMentionsFor(@NonNull List<Mention> mentions) {
    return Stream.of(mentions)
                 .map(m -> new SignalServiceDataMessage.Mention(Recipient.resolved(m.getRecipientId()).requireAci(), m.getStart(), m.getLength()))
                 .toList();
  }

  protected void rotateSenderCertificateIfNecessary() throws IOException {
    try {
      Collection<CertificateType> requiredCertificateTypes = SignalStore.phoneNumberPrivacy()
                                                                        .getRequiredCertificateTypes();

      Log.i(TAG, "Ensuring we have these certificates " + requiredCertificateTypes);

      for (CertificateType certificateType : requiredCertificateTypes) {

        byte[] certificateBytes = SignalStore.certificateValues()
                                             .getUnidentifiedAccessCertificate(certificateType);

        if (certificateBytes == null) {
          throw new InvalidCertificateException(String.format("No certificate %s was present.", certificateType));
        }

        SenderCertificate certificate = new SenderCertificate(certificateBytes);

        if (System.currentTimeMillis() > (certificate.getExpiration() - CERTIFICATE_EXPIRATION_BUFFER)) {
          throw new InvalidCertificateException(String.format(Locale.US, "Certificate %s is expired, or close to it. Expires on: %d, currently: %d", certificateType, certificate.getExpiration(), System.currentTimeMillis()));
        }
        Log.d(TAG, String.format("Certificate %s is valid", certificateType));
      }

      Log.d(TAG, "All certificates are valid.");
    } catch (InvalidCertificateException e) {
      Log.w(TAG, "A certificate was invalid at send time. Fetching new ones.", e);
      if (!ApplicationDependencies.getJobManager().runSynchronously(new RotateCertificateJob(), 5000).isPresent()) {
        throw new IOException("Timeout rotating certificate");
      }
    }
  }

  protected void handleProofRequiredException(@NonNull ProofRequiredException proofRequired, @Nullable Recipient recipient, long threadId, long messageId, boolean isMms)
      throws ProofRequiredException, RetryLaterException
  {
    Log.w(TAG, "[Proof Required] Options: " + proofRequired.getOptions());

    try {
      if (proofRequired.getOptions().contains(ProofRequiredException.Option.PUSH_CHALLENGE)) {
        ApplicationDependencies.getSignalServiceAccountManager().requestRateLimitPushChallenge();
        log(TAG, "[Proof Required] Successfully requested a challenge. Waiting up to " + PUSH_CHALLENGE_TIMEOUT + " ms.");

        boolean success = new PushChallengeRequest(PUSH_CHALLENGE_TIMEOUT).blockUntilSuccess();

        if (success) {
          log(TAG, "Successfully responded to a push challenge. Retrying message send.");
          throw new RetryLaterException(1);
        } else {
          warn(TAG, "Failed to respond to the push challenge in time. Falling back.");
        }
      }
    } catch (NonSuccessfulResponseCodeException e) {
      warn(TAG, "[Proof Required] Could not request a push challenge (" + e.getCode() + "). Falling back.", e);
    } catch (IOException e) {
      warn(TAG, "[Proof Required] Network error when requesting push challenge. Retrying later.");
      throw new RetryLaterException(e);
    }

    warn(TAG, "[Proof Required] Marking message as rate-limited. (id: " + messageId + ", mms: " + isMms + ", thread: " + threadId + ")");
    if (isMms) {
      SignalDatabase.mms().markAsRateLimited(messageId);
    } else {
      SignalDatabase.sms().markAsRateLimited(messageId);
    }

    if (proofRequired.getOptions().contains(ProofRequiredException.Option.RECAPTCHA)) {
      log(TAG, "[Proof Required] ReCAPTCHA required.");
      SignalStore.rateLimit().markNeedsRecaptcha(proofRequired.getToken());

      if (recipient != null) {
        ApplicationDependencies.getMessageNotifier().notifyProofRequired(context, recipient, threadId);
      } else {
        warn(TAG, "[Proof Required] No recipient! Couldn't notify.");
      }
    }

    throw proofRequired;
  }

  protected abstract void onPushSend() throws Exception;

  public static class PushChallengeRequest {
    private final long           timeout;
    private final CountDownLatch latch;
    private final EventBus       eventBus;

    private PushChallengeRequest(long timeout) {
      this.timeout  = timeout;
      this.latch    = new CountDownLatch(1);
      this.eventBus = EventBus.getDefault();
    }

    public boolean blockUntilSuccess() {
      eventBus.register(this);

      try {
        return latch.await(timeout, TimeUnit.MILLISECONDS);
      } catch (InterruptedException e) {
        Log.w(TAG, "[Proof Required] Interrupted?", e);
        return false;
      } finally {
        eventBus.unregister(this);
      }
    }

    @Subscribe(threadMode = ThreadMode.POSTING)
    public void onSuccessReceived(SubmitRateLimitPushChallengeJob.SuccessEvent event) {
      Log.i(TAG, "[Proof Required] Received a successful result!");
      latch.countDown();
    }
  }
}
