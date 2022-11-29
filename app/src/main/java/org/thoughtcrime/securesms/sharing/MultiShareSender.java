package org.thoughtcrime.securesms.sharing;

import android.content.Context;
import android.graphics.Color;
import android.net.Uri;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.util.Consumer;

import com.annimon.stream.Stream;

import org.signal.core.util.BreakIteratorCompat;
import org.signal.core.util.ThreadUtil;
import org.signal.core.util.concurrent.SimpleTask;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.attachments.UriAttachment;
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey;
import org.thoughtcrime.securesms.conversation.MessageSendType;
import org.thoughtcrime.securesms.conversation.colors.ChatColors;
import org.thoughtcrime.securesms.database.AttachmentTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.ThreadTable;
import org.thoughtcrime.securesms.database.model.Mention;
import org.thoughtcrime.securesms.database.model.StoryType;
import org.thoughtcrime.securesms.database.model.databaseprotos.StoryTextPost;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.keyvalue.StorySend;
import org.thoughtcrime.securesms.linkpreview.LinkPreview;
import org.thoughtcrime.securesms.mediasend.Media;
import org.thoughtcrime.securesms.mediasend.v2.text.TextStoryBackgroundColors;
import org.thoughtcrime.securesms.mms.ImageSlide;
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage;
import org.thoughtcrime.securesms.mms.OutgoingSecureMediaMessage;
import org.thoughtcrime.securesms.mms.SentMediaQuality;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.mms.SlideFactory;
import org.thoughtcrime.securesms.mms.StickerSlide;
import org.thoughtcrime.securesms.mms.VideoSlide;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.sms.OutgoingEncryptedMessage;
import org.thoughtcrime.securesms.sms.OutgoingTextMessage;
import org.thoughtcrime.securesms.stories.Stories;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.MessageUtil;
import org.thoughtcrime.securesms.util.Util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * MultiShareSender encapsulates send logic (stolen from {@link org.thoughtcrime.securesms.conversation.ConversationActivity}
 * and provides a means to:
 * <p>
 * 1. Send messages based off a {@link MultiShareArgs} object and
 * 1. Parse through the result of the send via a {@link MultiShareSendResultCollection}
 */
public final class MultiShareSender {

  private static final String TAG = Log.tag(MultiShareSender.class);

  private MultiShareSender() {
  }

  @MainThread
  public static void send(@NonNull MultiShareArgs multiShareArgs, @NonNull Consumer<MultiShareSendResultCollection> results) {
    SimpleTask.run(() -> sendSync(multiShareArgs), results::accept);
  }

  @WorkerThread
  public static MultiShareSendResultCollection sendSync(@NonNull MultiShareArgs multiShareArgs) {
    List<MultiShareSendResult> results                           = new ArrayList<>(multiShareArgs.getContactSearchKeys().size());
    Context                    context                           = ApplicationDependencies.getApplication();
    boolean                    isMmsEnabled                      = Util.isMmsCapable(context);
    String                     message                           = multiShareArgs.getDraftText();
    SlideDeck                  slideDeck;
    List<OutgoingMediaMessage> storiesBatch                      = new LinkedList<>();
    ChatColors                 generatedTextStoryBackgroundColor = TextStoryBackgroundColors.getRandomBackgroundColor();

    try {
      slideDeck = buildSlideDeck(context, multiShareArgs);
    } catch (SlideNotFoundException e) {
      Log.w(TAG, "Could not create slide for media message");
      for (ContactSearchKey.RecipientSearchKey recipientSearchKey : multiShareArgs.getRecipientSearchKeys()) {
        results.add(new MultiShareSendResult(recipientSearchKey, MultiShareSendResult.Type.GENERIC_ERROR));
      }

      return new MultiShareSendResultCollection(results);
    }

    long distributionListSentTimestamp = System.currentTimeMillis();
    for (ContactSearchKey.RecipientSearchKey recipientSearchKey : multiShareArgs.getRecipientSearchKeys()) {
      Recipient recipient = Recipient.resolved(recipientSearchKey.getRecipientId());

      long            threadId       = SignalDatabase.threads().getOrCreateThreadIdFor(recipient);
      List<Mention>   mentions       = getValidMentionsForRecipient(recipient, multiShareArgs.getMentions());
      MessageSendType sendType       = resolveTransportOption(context, recipient);
      boolean         forceSms       = recipient.isForceSmsSelection() && sendType.usesSmsTransport();
      int             subscriptionId = sendType.getSimSubscriptionIdOr(-1);
      long            expiresIn      = TimeUnit.SECONDS.toMillis(recipient.getExpiresInSeconds());
      boolean needsSplit = !sendType.usesSmsTransport() &&
                           message != null &&
                           message.length() > sendType.calculateCharacters(message).maxPrimaryMessageSize;
      boolean hasMmsMedia = !multiShareArgs.getMedia().isEmpty() ||
                            (multiShareArgs.getDataUri() != null && multiShareArgs.getDataUri() != Uri.EMPTY) ||
                            multiShareArgs.getStickerLocator() != null ||
                            recipient.isGroup() ||
                            recipient.getEmail().isPresent();
      boolean hasPushMedia = hasMmsMedia ||
                             multiShareArgs.getLinkPreview() != null ||
                             !mentions.isEmpty() ||
                             needsSplit;
      long    sentTimestamp      = recipient.isDistributionList() ? distributionListSentTimestamp : System.currentTimeMillis();
      boolean canSendAsTextStory = recipientSearchKey.isStory() && multiShareArgs.isValidForTextStoryGeneration();

      if ((recipient.isMmsGroup() || recipient.getEmail().isPresent()) && !isMmsEnabled) {
        results.add(new MultiShareSendResult(recipientSearchKey, MultiShareSendResult.Type.MMS_NOT_ENABLED));
      } else if (hasMmsMedia && sendType.usesSmsTransport() || hasPushMedia && !sendType.usesSmsTransport() || canSendAsTextStory) {
        sendMediaMessageOrCollectStoryToBatch(context,
                                              multiShareArgs,
                                              recipient,
                                              slideDeck,
                                              sendType,
                                              threadId,
                                              forceSms,
                                              expiresIn,
                                              multiShareArgs.isViewOnce(),
                                              subscriptionId,
                                              mentions,
                                              recipientSearchKey.isStory(),
                                              sentTimestamp,
                                              canSendAsTextStory,
                                              storiesBatch,
                                              generatedTextStoryBackgroundColor);
        results.add(new MultiShareSendResult(recipientSearchKey, MultiShareSendResult.Type.SUCCESS));
      } else if (recipientSearchKey.isStory()) {
        results.add(new MultiShareSendResult(recipientSearchKey, MultiShareSendResult.Type.INVALID_SHARE_TO_STORY));
      } else {
        sendTextMessage(context, multiShareArgs, recipient, threadId, forceSms, expiresIn, subscriptionId);
        results.add(new MultiShareSendResult(recipientSearchKey, MultiShareSendResult.Type.SUCCESS));
      }

      if (!recipientSearchKey.isStory()) {
        SignalDatabase.threads().setRead(threadId, true);
      }

      // XXX We must do this to avoid sending out messages to the same recipient with the same
      //     sentTimestamp. If we do this, they'll be considered dupes by the receiver.
      ThreadUtil.sleep(5);
    }

    if (!storiesBatch.isEmpty()) {
      MessageSender.sendStories(context,
                                storiesBatch.stream()
                                            .map(OutgoingSecureMediaMessage::new)
                                            .collect(Collectors.toList()),
                                null,
                                null);
    }

    return new MultiShareSendResultCollection(results);
  }

  public static @NonNull MessageSendType getWorstTransportOption(@NonNull Context context, @NonNull Set<ContactSearchKey.RecipientSearchKey> recipientSearchKeys) {
    for (ContactSearchKey.RecipientSearchKey recipientSearchKey : recipientSearchKeys) {
      MessageSendType type = resolveTransportOption(context, Recipient.resolved(recipientSearchKey.getRecipientId()).isForceSmsSelection() && !recipientSearchKey.isStory());
      if (type.usesSmsTransport()) {
        return type;
      }
    }

    return MessageSendType.SignalMessageSendType.INSTANCE;
  }

  private static @NonNull MessageSendType resolveTransportOption(@NonNull Context context, @NonNull Recipient recipient) {
    return resolveTransportOption(context, !recipient.isDistributionList() && (recipient.isForceSmsSelection() || !recipient.isRegistered()));
  }

  public static @NonNull MessageSendType resolveTransportOption(@NonNull Context context, boolean forceSms) {
    if (forceSms) {
      return MessageSendType.getFirstForTransport(context, false, MessageSendType.TransportType.SMS);
    } else {
      return MessageSendType.SignalMessageSendType.INSTANCE;
    }
  }

  private static void sendMediaMessageOrCollectStoryToBatch(@NonNull Context context,
                                                            @NonNull MultiShareArgs multiShareArgs,
                                                            @NonNull Recipient recipient,
                                                            @NonNull SlideDeck slideDeck,
                                                            @NonNull MessageSendType sendType,
                                                            long threadId,
                                                            boolean forceSms,
                                                            long expiresIn,
                                                            boolean isViewOnce,
                                                            int subscriptionId,
                                                            @NonNull List<Mention> validatedMentions,
                                                            boolean isStory,
                                                            long sentTimestamp,
                                                            boolean canSendAsTextStory,
                                                            @NonNull List<OutgoingMediaMessage> storiesToBatchSend,
                                                            @NonNull ChatColors generatedTextStoryBackgroundColor)
  {
    String body = multiShareArgs.getDraftText();
    if (sendType.usesSignalTransport() && !forceSms && body != null) {
      MessageUtil.SplitResult splitMessage = MessageUtil.getSplitMessage(context, body, sendType.calculateCharacters(body).maxPrimaryMessageSize);
      body = splitMessage.getBody();

      if (splitMessage.getTextSlide().isPresent()) {
        slideDeck.addSlide(splitMessage.getTextSlide().get());
      }
    }

    List<OutgoingMediaMessage> outgoingMessages = new ArrayList<>();

    if (isStory) {
      final StoryType storyType;
      if (recipient.isDistributionList()) {
        storyType = SignalDatabase.distributionLists().getStoryType(recipient.requireDistributionListId());
      } else {
        storyType = StoryType.STORY_WITH_REPLIES;
      }

      if (!recipient.isMyStory()) {
        SignalStore.storyValues().setLatestStorySend(StorySend.newSend(recipient));
      }

      if (multiShareArgs.isTextStory()) {
        OutgoingMediaMessage outgoingMediaMessage = new OutgoingMediaMessage(recipient,
                                                                             new SlideDeck(),
                                                                             body,
                                                                             sentTimestamp,
                                                                             subscriptionId,
                                                                             0L,
                                                                             false,
                                                                             ThreadTable.DistributionTypes.DEFAULT,
                                                                             storyType.toTextStoryType(),
                                                                             null,
                                                                             false,
                                                                             null,
                                                                             Collections.emptyList(),
                                                                             buildLinkPreviews(context, multiShareArgs.getLinkPreview()),
                                                                             Collections.emptyList(),
                                                                             null);

        outgoingMessages.add(outgoingMediaMessage);
      } else if (canSendAsTextStory) {
        outgoingMessages.add(generateTextStory(context, recipient, multiShareArgs, sentTimestamp, storyType, generatedTextStoryBackgroundColor));
      } else {
        List<Slide> storySupportedSlides = slideDeck.getSlides()
                                                    .stream()
                                                    .flatMap(slide -> {
                                                      if (slide instanceof VideoSlide) {
                                                        return expandToClips(context, (VideoSlide) slide).stream();
                                                      } else if (slide instanceof ImageSlide) {
                                                        return java.util.stream.Stream.of(ensureDefaultQuality(context, (ImageSlide) slide));
                                                      } else {
                                                        return java.util.stream.Stream.of(slide);
                                                      }
                                                    })
                                                    .filter(it -> MediaUtil.isStorySupportedType(it.getContentType()))
                                                    .collect(Collectors.toList());

        for (final Slide slide : storySupportedSlides) {
          SlideDeck singletonDeck = new SlideDeck();
          singletonDeck.addSlide(slide);

          OutgoingMediaMessage outgoingMediaMessage = new OutgoingMediaMessage(recipient,
                                                                               singletonDeck,
                                                                               body,
                                                                               sentTimestamp,
                                                                               subscriptionId,
                                                                               0L,
                                                                               false,
                                                                               ThreadTable.DistributionTypes.DEFAULT,
                                                                               storyType,
                                                                               null,
                                                                               false,
                                                                               null,
                                                                               Collections.emptyList(),
                                                                               Collections.emptyList(),
                                                                               validatedMentions,
                                                                               null);

          outgoingMessages.add(outgoingMediaMessage);
        }
      }
    } else {
      OutgoingMediaMessage outgoingMediaMessage = new OutgoingMediaMessage(recipient,
                                                                           slideDeck,
                                                                           body,
                                                                           sentTimestamp,
                                                                           subscriptionId,
                                                                           expiresIn,
                                                                           isViewOnce,
                                                                           ThreadTable.DistributionTypes.DEFAULT,
                                                                           StoryType.NONE,
                                                                           null,
                                                                           false,
                                                                           null,
                                                                           Collections.emptyList(),
                                                                           buildLinkPreviews(context, multiShareArgs.getLinkPreview()),
                                                                           validatedMentions,
                                                                           null);

      outgoingMessages.add(outgoingMediaMessage);
    }

    if (isStory) {
      storiesToBatchSend.addAll(outgoingMessages);
    } else if (shouldSendAsPush(recipient, forceSms)) {
      for (final OutgoingMediaMessage outgoingMessage : outgoingMessages) {
        MessageSender.send(context, new OutgoingSecureMediaMessage(outgoingMessage), threadId, false, null, null);
      }
    } else {
      for (final OutgoingMediaMessage outgoingMessage : outgoingMessages) {
        MessageSender.send(context, outgoingMessage, threadId, forceSms, null, null);
      }
    }
  }

  private static Collection<Slide> expandToClips(@NonNull Context context, @NonNull VideoSlide videoSlide) {
    long duration = Stories.MediaTransform.getVideoDuration(Objects.requireNonNull(videoSlide.getUri()));
    if (duration > Stories.MAX_VIDEO_DURATION_MILLIS) {
      return Stories.MediaTransform.clipMediaToStoryDuration(Stories.MediaTransform.videoSlideToMedia(videoSlide, duration))
                                   .stream()
                                   .map(media -> Stories.MediaTransform.mediaToVideoSlide(context, media))
                                   .collect(Collectors.toList());
    } else if (duration == 0L) {
      return Collections.emptyList();
    } else {
      return Collections.singletonList(videoSlide);
    }
  }

  private static List<LinkPreview> buildLinkPreviews(@NonNull Context context, @Nullable LinkPreview linkPreview) {
    if (linkPreview == null) {
      return Collections.emptyList();
    } else {
      return Collections.singletonList(new LinkPreview(
          linkPreview.getUrl(),
          linkPreview.getTitle(),
          linkPreview.getDescription(),
          linkPreview.getDate(),
          linkPreview.getThumbnail().map(thumbnail ->
                                             thumbnail instanceof UriAttachment ? thumbnail
                                                                                : new ImageSlide(context,
                                                                                                 thumbnail.getUri(),
                                                                                                 thumbnail.getContentType(),
                                                                                                 thumbnail.getSize(),
                                                                                                 thumbnail.getWidth(),
                                                                                                 thumbnail.getHeight(),
                                                                                                 thumbnail.isBorderless(),
                                                                                                 thumbnail.getCaption(),
                                                                                                 thumbnail.getBlurHash(),
                                                                                                 thumbnail.getTransformProperties()).asAttachment()
          )
      ));
    }
  }

  private static Slide ensureDefaultQuality(@NonNull Context context, @NonNull ImageSlide imageSlide) {
    Attachment attachment = imageSlide.asAttachment();
    if (attachment.getTransformProperties().getSentMediaQuality() == SentMediaQuality.HIGH.getCode()) {
      return new ImageSlide(
          context,
          attachment.getUri(),
          attachment.getContentType(),
          attachment.getSize(),
          attachment.getWidth(),
          attachment.getHeight(),
          attachment.isBorderless(),
          attachment.getCaption(),
          attachment.getBlurHash(),
          AttachmentTable.TransformProperties.empty()
      );
    } else {
      return imageSlide;
    }
  }

  private static void sendTextMessage(@NonNull Context context,
                                      @NonNull MultiShareArgs multiShareArgs,
                                      @NonNull Recipient recipient,
                                      long threadId,
                                      boolean forceSms,
                                      long expiresIn,
                                      int subscriptionId)
  {

    final OutgoingTextMessage outgoingTextMessage;
    if (shouldSendAsPush(recipient, forceSms)) {
      outgoingTextMessage = new OutgoingEncryptedMessage(recipient, multiShareArgs.getDraftText(), expiresIn);
    } else {
      outgoingTextMessage = new OutgoingTextMessage(recipient, multiShareArgs.getDraftText(), expiresIn, subscriptionId);
    }

    MessageSender.send(context, outgoingTextMessage, threadId, forceSms, null, null);
  }

  private static @NonNull OutgoingMediaMessage generateTextStory(@NonNull Context context,
                                                                 @NonNull Recipient recipient,
                                                                 @NonNull MultiShareArgs multiShareArgs,
                                                                 long sentTimestamp,
                                                                 @NonNull StoryType storyType,
                                                                 @NonNull ChatColors background)
  {
    return new OutgoingMediaMessage(
        recipient,
        Base64.encodeBytes(StoryTextPost.newBuilder()
                                        .setBody(getBodyForTextStory(multiShareArgs.getDraftText(), multiShareArgs.getLinkPreview()))
                                        .setStyle(StoryTextPost.Style.DEFAULT)
                                        .setBackground(background.serialize())
                                        .setTextBackgroundColor(0)
                                        .setTextForegroundColor(Color.WHITE)
                                        .build()
                                        .toByteArray()),
        Collections.emptyList(),
        sentTimestamp,
        -1,
        0,
        false,
        ThreadTable.DistributionTypes.DEFAULT,
        storyType.toTextStoryType(),
        null,
        false,
        null,
        Collections.emptyList(),
        buildLinkPreviews(context, multiShareArgs.getLinkPreview()),
        Collections.emptyList(),
        Collections.emptySet(),
        Collections.emptySet(),
        null);
  }

  private static @NonNull String getBodyForTextStory(@Nullable String draftText, @Nullable LinkPreview linkPreview) {
    if (Util.isEmpty(draftText)) {
      return "";
    }

    BreakIteratorCompat breakIteratorCompat = BreakIteratorCompat.getInstance();
    breakIteratorCompat.setText(draftText);

    String trimmed = breakIteratorCompat.take(Stories.MAX_TEXT_STORY_SIZE).toString();
    if (linkPreview == null) {
      return trimmed;
    }

    if (linkPreview.getUrl().equals(trimmed)) {
      return "";
    }

    return trimmed.replace(linkPreview.getUrl(), "").trim();
  }

  private static boolean shouldSendAsPush(@NonNull Recipient recipient, boolean forceSms) {
    return recipient.isDistributionList() ||
           recipient.isServiceIdOnly() ||
           (recipient.isRegistered() && !forceSms);
  }

  private static @NonNull SlideDeck buildSlideDeck(@NonNull Context context, @NonNull MultiShareArgs multiShareArgs) throws SlideNotFoundException {
    SlideDeck slideDeck = new SlideDeck();
    if (multiShareArgs.getStickerLocator() != null) {
      slideDeck.addSlide(new StickerSlide(context, multiShareArgs.getDataUri(), 0, multiShareArgs.getStickerLocator(), multiShareArgs.getDataType()));
    } else if (!multiShareArgs.getMedia().isEmpty()) {
      for (Media media : multiShareArgs.getMedia()) {
        Slide slide = SlideFactory.getSlide(context, media.getMimeType(), media.getUri(), media.getWidth(), media.getHeight(), media.getTransformProperties().orElse(null));
        if (slide != null) {
          slideDeck.addSlide(slide);
        } else {
          throw new SlideNotFoundException();
        }
      }
    } else if (multiShareArgs.getDataUri() != null) {
      Slide slide = SlideFactory.getSlide(context, multiShareArgs.getDataType(), multiShareArgs.getDataUri(), 0, 0, null);
      if (slide != null) {
        slideDeck.addSlide(slide);
      } else {
        throw new SlideNotFoundException();
      }
    }

    return slideDeck;
  }

  private static @NonNull List<Mention> getValidMentionsForRecipient(@NonNull Recipient recipient, @NonNull List<Mention> mentions) {
    if (mentions.isEmpty() || !recipient.isPushV2Group() || !recipient.isActiveGroup()) {
      return Collections.emptyList();
    } else {
      Set<RecipientId> validRecipientIds = new HashSet<>(recipient.getParticipantIds());

      return mentions.stream()
                     .filter(mention -> validRecipientIds.contains(mention.getRecipientId()))
                     .collect(Collectors.toList());
    }
  }

  public static final class MultiShareSendResultCollection {
    private final List<MultiShareSendResult> results;

    private MultiShareSendResultCollection(List<MultiShareSendResult> results) {
      this.results = results;
    }

    public boolean containsFailures() {
      return Stream.of(results).anyMatch(result -> result.type != MultiShareSendResult.Type.SUCCESS);
    }

    public boolean containsOnlyFailures() {
      return Stream.of(results).allMatch(result -> result.type != MultiShareSendResult.Type.SUCCESS);
    }
  }

  private static final class MultiShareSendResult {
    private final ContactSearchKey.RecipientSearchKey recipientSearchKey;
    private final Type                                type;

    private MultiShareSendResult(ContactSearchKey.RecipientSearchKey contactSearchKey, Type type) {
      this.recipientSearchKey = contactSearchKey;
      this.type               = type;
    }

    public ContactSearchKey.RecipientSearchKey getContactSearchKey() {
      return recipientSearchKey;
    }

    public Type getType() {
      return type;
    }

    private enum Type {
      GENERIC_ERROR,
      INVALID_SHARE_TO_STORY,
      MMS_NOT_ENABLED,
      SUCCESS
    }
  }

  private static final class SlideNotFoundException extends Exception {
  }
}
