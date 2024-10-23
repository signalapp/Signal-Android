/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

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
import org.signal.core.util.Hex;
import org.signal.core.util.logging.Log;
import org.signal.libsignal.metadata.certificate.InvalidCertificateException;
import org.signal.libsignal.metadata.certificate.SenderCertificate;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.receipts.ReceiptCredentialPresentation;
import org.thoughtcrime.securesms.TextSecureExpiredException;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.attachments.AttachmentId;
import org.thoughtcrime.securesms.attachments.DatabaseAttachment;
import org.thoughtcrime.securesms.blurhash.BlurHash;
import org.thoughtcrime.securesms.contactshare.Contact;
import org.thoughtcrime.securesms.contactshare.ContactModelMapper;
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil;
import org.thoughtcrime.securesms.database.NoSuchMessageException;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.Mention;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.MmsMessageRecord;
import org.thoughtcrime.securesms.database.model.ParentStoryId;
import org.thoughtcrime.securesms.database.model.StickerRecord;
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList;
import org.thoughtcrime.securesms.database.model.databaseprotos.GiftBadge;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.events.PartProgressEvent;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.jobmanager.JobTracker;
import org.thoughtcrime.securesms.jobmanager.impl.BackoffUtil;
import org.thoughtcrime.securesms.keyvalue.CertificateType;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.linkpreview.LinkPreview;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader.DecryptableUri;
import org.thoughtcrime.securesms.mms.OutgoingMessage;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.mms.QuoteModel;
import org.thoughtcrime.securesms.net.NotPushRegisteredException;
import org.thoughtcrime.securesms.notifications.v2.ConversationId;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.transport.RetryLaterException;
import org.thoughtcrime.securesms.transport.UndeliverableMessageException;
import org.signal.core.util.Base64;
import org.thoughtcrime.securesms.util.BitmapDecodingException;
import org.thoughtcrime.securesms.util.RemoteConfig;
import org.thoughtcrime.securesms.util.ImageCompressionUtil;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentRemoteId;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServicePreview;
import org.whispersystems.signalservice.api.messages.shared.SharedContact;
import org.whispersystems.signalservice.api.push.ServiceId.ACI;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.signalservice.api.push.exceptions.ProofRequiredException;
import org.whispersystems.signalservice.api.push.exceptions.ServerRejectedException;
import org.whispersystems.signalservice.internal.push.BodyRange;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public abstract class PushSendJob extends SendJob {

  private static final String TAG                           = Log.tag(PushSendJob.class);
  private static final long   CERTIFICATE_EXPIRATION_BUFFER = TimeUnit.DAYS.toMillis(1);
  private static final long   PUSH_CHALLENGE_TIMEOUT        = TimeUnit.SECONDS.toMillis(10);

  protected PushSendJob(Job.Parameters parameters) {
    super(parameters);
  }

  @Override
  protected final void onSend() throws Exception {
    long timeSinceAciSignedPreKeyRotation = System.currentTimeMillis() - SignalStore.account().aciPreKeys().getLastSignedPreKeyRotationTime();
    long timeSincePniSignedPreKeyRotation = System.currentTimeMillis() - SignalStore.account().pniPreKeys().getLastSignedPreKeyRotationTime();

    if (timeSinceAciSignedPreKeyRotation > PreKeysSyncJob.MAXIMUM_ALLOWED_SIGNED_PREKEY_AGE ||
        timeSinceAciSignedPreKeyRotation < 0 ||
        timeSincePniSignedPreKeyRotation > PreKeysSyncJob.MAXIMUM_ALLOWED_SIGNED_PREKEY_AGE ||
        timeSincePniSignedPreKeyRotation < 0
    ) {
      warn(TAG, "It's been too long since rotating our signed prekeys (ACI: " + timeSinceAciSignedPreKeyRotation + " ms, PNI: " + timeSincePniSignedPreKeyRotation + " ms)! Attempting to rotate now.");

      Optional<JobTracker.JobState> state = AppDependencies.getJobManager().runSynchronously(PreKeysSyncJob.create(), TimeUnit.SECONDS.toMillis(30));

      if (state.isPresent() && state.get() == JobTracker.JobState.SUCCESS) {
        log(TAG, "Successfully refreshed prekeys. Continuing.");
      } else {
        throw new RetryLaterException(new TextSecureExpiredException("Failed to refresh prekeys! State: " + (state.isEmpty() ? "<empty>" : state.get())));
      }
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
      AppDependencies.getJobManager().add(new ServiceOutageDetectionJob());
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
        return BackoffUtil.exponentialBackoff(pastAttemptCount, RemoteConfig.getServerErrorMaxBackoff());
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
      return Optional.empty();
    }

    return Optional.of(ProfileKeyUtil.getSelfProfileKey().serialize());
  }

  protected SignalServiceAttachment getAttachmentFor(Contact.Avatar avatar) {
    Attachment attachment = avatar.getAttachment();

    try {
      if (attachment.getUri() == null || attachment.size == 0) throw new IOException("Assertion failed, outgoing attachment has no data!");
      InputStream is = PartAuthority.getAttachmentStream(context, attachment.getUri());
      return SignalServiceAttachment.newStreamBuilder()
                                    .withStream(is)
                                    .withContentType(attachment.contentType)
                                    .withLength(attachment.size)
                                    .withFileName(attachment.fileName)
                                    .withVoiceNote(attachment.voiceNote)
                                    .withBorderless(attachment.borderless)
                                    .withGif(attachment.videoGif)
                                    .withFaststart(attachment.transformProperties.mp4FastStart)
                                    .withWidth(attachment.width)
                                    .withHeight(attachment.height)
                                    .withCaption(attachment.caption)
                                    .withUuid(attachment.uuid)
                                    .withResumableUploadSpec(AppDependencies.getSignalServiceMessageSender().getResumableUploadSpec())
                                    .withListener(new SignalServiceAttachment.ProgressListener() {
                                      @Override
                                      public void onAttachmentProgress(long total, long progress) {
                                        EventBus.getDefault().postSticky(new PartProgressEvent(attachment, PartProgressEvent.Type.NETWORK, total, progress));
                                      }

                                      @Override
                                      public boolean shouldCancel() {
                                        return isCanceled();
                                      }
                                    })
                                    .build();
    } catch (IOException ioe) {
      Log.w(TAG, "Couldn't open attachment", ioe);
    }
    return null;
  }

  protected static Set<String> enqueueCompressingAndUploadAttachmentsChains(@NonNull JobManager jobManager, OutgoingMessage message) {
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
                                 final AttachmentId attachmentId = ((DatabaseAttachment) a).attachmentId;
                                 Log.d(TAG, "Enqueueing job chain to upload " + attachmentId);
                                 AttachmentUploadJob attachmentUploadJob = new AttachmentUploadJob(attachmentId);

                                 jobManager.startChain(AttachmentCompressionJob.fromAttachment((DatabaseAttachment) a, false, -1))
                                           .then(attachmentUploadJob)
                                           .enqueue();

                                 return attachmentUploadJob.getId();
                               })
                               .toList());
  }

  protected @NonNull List<SignalServiceAttachment> getAttachmentPointersFor(List<Attachment> attachments) {
    return Stream.of(attachments).map(this::getAttachmentPointerFor).filter(a -> a != null).toList();
  }

  protected @Nullable SignalServiceAttachment getAttachmentPointerFor(Attachment attachment) {
    if (TextUtils.isEmpty(attachment.remoteLocation)) {
      Log.w(TAG, "empty content id");
      return null;
    }

    if (TextUtils.isEmpty(attachment.remoteKey)) {
      Log.w(TAG, "empty encrypted key");
      return null;
    }

    try {
      final SignalServiceAttachmentRemoteId remoteId = SignalServiceAttachmentRemoteId.from(attachment.remoteLocation);
      final byte[]                          key      = Base64.decode(attachment.remoteKey);

      int width  = attachment.width;
      int height = attachment.height;

      if ((width == 0 || height == 0) && MediaUtil.hasVideoThumbnail(context, attachment.getUri())) {
        Bitmap thumbnail = MediaUtil.getVideoThumbnail(context, attachment.getUri(), 1000);

        if (thumbnail != null) {
          width  = thumbnail.getWidth();
          height = thumbnail.getHeight();
        }
      }

      return new SignalServiceAttachmentPointer(attachment.cdn.getCdnNumber(),
                                                remoteId,
                                                attachment.contentType,
                                                key,
                                                Optional.of(Util.toIntExact(attachment.size)),
                                                Optional.empty(),
                                                width,
                                                height,
                                                Optional.ofNullable(attachment.remoteDigest),
                                                Optional.ofNullable(attachment.getIncrementalDigest()),
                                                attachment.incrementalMacChunkSize,
                                                Optional.ofNullable(attachment.fileName),
                                                attachment.voiceNote,
                                                attachment.borderless,
                                                attachment.videoGif,
                                                Optional.ofNullable(attachment.caption),
                                                Optional.ofNullable(attachment.blurHash).map(BlurHash::getHash),
                                                attachment.uploadTimestamp,
                                                attachment.uuid);
    } catch (IOException | ArithmeticException e) {
      Log.w(TAG, e);
      return null;
    }
  }

  protected static void notifyMediaMessageDeliveryFailed(Context context, long messageId) {
    long                     threadId           = SignalDatabase.messages().getThreadIdForMessage(messageId);
    Recipient                recipient          = SignalDatabase.threads().getRecipientForThreadId(threadId);
    ParentStoryId.GroupReply groupReplyStoryId  = SignalDatabase.messages().getParentStoryIdForGroupReply(messageId);

    boolean isStory = false;
    try {
      MessageRecord record = SignalDatabase.messages().getMessageRecord(messageId);
      if (record instanceof MmsMessageRecord) {
        isStory = (((MmsMessageRecord) record).getStoryType().isStory());
      }
    } catch (NoSuchMessageException e) {
      Log.e(TAG, e);
    }

    if (threadId != -1 && recipient != null) {
      if (isStory) {
        SignalDatabase.messages().markAsNotNotified(messageId);
        AppDependencies.getMessageNotifier().notifyStoryDeliveryFailed(context, recipient, ConversationId.forConversation(threadId));
      } else {
        AppDependencies.getMessageNotifier().notifyMessageDeliveryFailed(context, recipient, ConversationId.fromThreadAndReply(threadId, groupReplyStoryId));
      }
    }
  }

  protected Optional<SignalServiceDataMessage.Quote> getQuoteFor(OutgoingMessage message) throws IOException {
    if (message.getOutgoingQuote() == null) return Optional.empty();
    if (message.isMessageEdit()) {
      return Optional.of(new SignalServiceDataMessage.Quote(0, ACI.UNKNOWN, "", null, null, SignalServiceDataMessage.Quote.Type.NORMAL, null));
    }

    long                                                  quoteId              = message.getOutgoingQuote().getId();
    String                                                quoteBody            = message.getOutgoingQuote().getText();
    RecipientId                                           quoteAuthor          = message.getOutgoingQuote().getAuthor();
    List<SignalServiceDataMessage.Mention>                quoteMentions        = getMentionsFor(message.getOutgoingQuote().getMentions());
    List<BodyRange>                                       bodyRanges           = getBodyRanges(message.getOutgoingQuote().getBodyRanges());
    QuoteModel.Type                                       quoteType            = message.getOutgoingQuote().getType();
    List<SignalServiceDataMessage.Quote.QuotedAttachment> quoteAttachments     = new LinkedList<>();
    Optional<Attachment>                                  localQuoteAttachment = message.getOutgoingQuote()
                                                                                        .getAttachments()
                                                                                        .stream()
                                                                                        .filter(a -> !MediaUtil.isViewOnceType(a.contentType))
                                                                                        .findFirst();

    if (localQuoteAttachment.isPresent()) {
      Attachment attachment = localQuoteAttachment.get();

      ImageCompressionUtil.Result thumbnailData = null;
      SignalServiceAttachment     thumbnail     = null;

      try {
        if (MediaUtil.isImageType(attachment.contentType) && attachment.getUri() != null) {
          thumbnailData = ImageCompressionUtil.compress(context, attachment.contentType, new DecryptableUri(attachment.getUri()), 100, 50);
        } else if (Build.VERSION.SDK_INT >= 23 && MediaUtil.isVideoType(attachment.contentType) && attachment.getUri() != null) {
          Bitmap bitmap = MediaUtil.getVideoThumbnail(context, attachment.getUri(), 1000);

          if (bitmap != null) {
            thumbnailData = ImageCompressionUtil.compress(context, attachment.contentType, new DecryptableUri(attachment.getUri()), 100, 50);
          }
        }

        if (thumbnailData != null) {
          SignalServiceAttachment.Builder builder = SignalServiceAttachment.newStreamBuilder()
                                                                           .withContentType(thumbnailData.getMimeType())
                                                                           .withWidth(thumbnailData.getWidth())
                                                                           .withHeight(thumbnailData.getHeight())
                                                                           .withLength(thumbnailData.getData().length)
                                                                           .withStream(new ByteArrayInputStream(thumbnailData.getData()))
                                                                           .withResumableUploadSpec(AppDependencies.getSignalServiceMessageSender().getResumableUploadSpec())
                                                                           .withUuid(UUID.randomUUID());

          thumbnail = builder.build();
        }

        quoteAttachments.add(new SignalServiceDataMessage.Quote.QuotedAttachment(attachment.videoGif ? MediaUtil.IMAGE_GIF : attachment.contentType,
                                                                                 attachment.fileName,
                                                                                 thumbnail));
      } catch (BitmapDecodingException e) {
        Log.w(TAG, e);
      }
    }

    Recipient quoteAuthorRecipient = Recipient.resolved(quoteAuthor);

    if (quoteAuthorRecipient.isMaybeRegistered()) {
      return Optional.of(new SignalServiceDataMessage.Quote(quoteId, RecipientUtil.getOrFetchServiceId(context, quoteAuthorRecipient), quoteBody, quoteAttachments, quoteMentions, quoteType.getDataMessageType(), bodyRanges));
    } else if (quoteAuthorRecipient.getHasServiceId()) {
      return Optional.of(new SignalServiceDataMessage.Quote(quoteId, quoteAuthorRecipient.requireAci(), quoteBody, quoteAttachments, quoteMentions, quoteType.getDataMessageType(), bodyRanges));
    } else {
      return Optional.empty();
    }
  }

  protected Optional<SignalServiceDataMessage.Sticker> getStickerFor(OutgoingMessage message) {
    Attachment stickerAttachment = Stream.of(message.getAttachments()).filter(Attachment::isSticker).findFirst().orElse(null);

    if (stickerAttachment == null) {
      return Optional.empty();
    }

    try {
      byte[]                  packId     = Hex.fromStringCondensed(stickerAttachment.stickerLocator.packId);
      byte[]                  packKey    = Hex.fromStringCondensed(stickerAttachment.stickerLocator.packKey);
      int                     stickerId  = stickerAttachment.stickerLocator.stickerId;
      StickerRecord           record     = SignalDatabase.stickers().getSticker(stickerAttachment.stickerLocator.packId, stickerId, false);
      String                  emoji      = record != null ? record.getEmoji() : null;
      SignalServiceAttachment attachment = getAttachmentPointerFor(stickerAttachment);

      return Optional.of(new SignalServiceDataMessage.Sticker(packId, packKey, stickerId, emoji, attachment));
    } catch (IOException e) {
      Log.w(TAG, "Failed to decode sticker id/key", e);
      return Optional.empty();
    }
  }

  protected Optional<SignalServiceDataMessage.Reaction> getStoryReactionFor(@NonNull OutgoingMessage message, @NonNull SignalServiceDataMessage.StoryContext storyContext) {
    if (message.isStoryReaction()) {
      return Optional.of(new SignalServiceDataMessage.Reaction(message.getBody(),
                                                               false,
                                                               storyContext.getAuthorServiceId(),
                                                               storyContext.getSentTimestamp()));
    } else {
      return Optional.empty();
    }
  }

  List<SharedContact> getSharedContactsFor(OutgoingMessage mediaMessage) {
    List<SharedContact> sharedContacts = new LinkedList<>();

    for (Contact contact : mediaMessage.getSharedContacts()) {
      SharedContact.Builder builder = ContactModelMapper.localToRemoteBuilder(contact);
      SharedContact.Avatar  avatar  = null;

      if (contact.getAvatar() != null && contact.getAvatar().getAttachment() != null) {
        SignalServiceAttachment attachment = getAttachmentPointerFor(contact.getAvatar().getAttachment());
        if (attachment == null) {
          attachment = getAttachmentFor(contact.getAvatar());
        }
        avatar = SharedContact.Avatar.newBuilder().withAttachment(attachment)
                                                  .withProfileFlag(contact.getAvatar().isProfile())
                                                  .build();
      }

      builder.setAvatar(avatar);
      sharedContacts.add(builder.build());
    }

    return sharedContacts;
  }

  List<SignalServicePreview> getPreviewsFor(OutgoingMessage mediaMessage) {
    return Stream.of(mediaMessage.getLinkPreviews()).map(lp -> {
      SignalServiceAttachment attachment = lp.getThumbnail().isPresent() ? getAttachmentPointerFor(lp.getThumbnail().get()) : null;
      return new SignalServicePreview(lp.getUrl(), lp.getTitle(), lp.getDescription(), lp.getDate(), Optional.ofNullable(attachment));
    }).toList();
  }

  List<SignalServiceDataMessage.Mention> getMentionsFor(@NonNull List<Mention> mentions) {
    return Stream.of(mentions)
                 .map(m -> new SignalServiceDataMessage.Mention(Recipient.resolved(m.getRecipientId()).requireAci(), m.getStart(), m.getLength()))
                 .toList();
  }

  @Nullable SignalServiceDataMessage.GiftBadge getGiftBadgeFor(@NonNull OutgoingMessage message) throws UndeliverableMessageException {
    GiftBadge giftBadge = message.getGiftBadge();
    if (giftBadge == null) {
      return null;
    }

    try {
      ReceiptCredentialPresentation presentation = new ReceiptCredentialPresentation(giftBadge.redemptionToken.toByteArray());

      return new SignalServiceDataMessage.GiftBadge(presentation);
    } catch (InvalidInputException invalidInputException) {
      throw new UndeliverableMessageException(invalidInputException);
    }
  }

  protected @Nullable List<BodyRange> getBodyRanges(@NonNull OutgoingMessage message) {
    return getBodyRanges(message.getBodyRanges());
  }

  protected @Nullable List<BodyRange> getBodyRanges(@Nullable BodyRangeList bodyRanges) {
    if (bodyRanges == null || bodyRanges.ranges.size() == 0) {
      return null;
    }

    return bodyRanges
        .ranges
        .stream()
        .map(range -> {
          BodyRange.Builder builder = new BodyRange.Builder().start(range.start).length(range.length);

          if (range.style != null) {
            switch (range.style) {
              case BOLD:
                builder.style(BodyRange.Style.BOLD);
                break;
              case ITALIC:
                builder.style(BodyRange.Style.ITALIC);
                break;
              case SPOILER:
                builder.style(BodyRange.Style.SPOILER);
                break;
              case STRIKETHROUGH:
                builder.style(BodyRange.Style.STRIKETHROUGH);
                break;
              case MONOSPACE:
                builder.style(BodyRange.Style.MONOSPACE);
                break;
              default:
                throw new IllegalArgumentException("Unrecognized style");
            }
          } else {
            throw new IllegalArgumentException("Only supports style");
          }

          return builder.build();
        }).collect(Collectors.toList());
  }

  protected void rotateSenderCertificateIfNecessary() throws IOException {
    try {
      Collection<CertificateType> requiredCertificateTypes = SignalStore.phoneNumberPrivacy()
                                                                        .getRequiredCertificateTypes();

      Log.i(TAG, "Ensuring we have these certificates " + requiredCertificateTypes);

      for (CertificateType certificateType : requiredCertificateTypes) {

        byte[] certificateBytes = SignalStore.certificate()
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
      if (!AppDependencies.getJobManager().runSynchronously(new RotateCertificateJob(), 5000).isPresent()) {
        throw new IOException("Timeout rotating certificate");
      }
    }
  }

  protected abstract void onPushSend() throws Exception;

}
