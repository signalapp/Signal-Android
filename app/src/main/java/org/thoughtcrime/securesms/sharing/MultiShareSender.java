package org.thoughtcrime.securesms.sharing;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.core.util.Consumer;

import com.annimon.stream.Stream;

import org.signal.core.util.ThreadUtil;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.TransportOption;
import org.thoughtcrime.securesms.TransportOptions;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.database.model.Mention;
import org.thoughtcrime.securesms.database.model.StoryType;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.mediasend.Media;
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage;
import org.thoughtcrime.securesms.mms.OutgoingSecureMediaMessage;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.mms.SlideFactory;
import org.thoughtcrime.securesms.mms.StickerSlide;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.sms.OutgoingEncryptedMessage;
import org.thoughtcrime.securesms.sms.OutgoingTextMessage;
import org.thoughtcrime.securesms.util.MessageUtil;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.concurrent.SimpleTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * MultiShareSender encapsulates send logic (stolen from {@link org.thoughtcrime.securesms.conversation.ConversationActivity}
 * and provides a means to:
 *
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
    List<MultiShareSendResult> results      = new ArrayList<>(multiShareArgs.getShareContactAndThreads().size());
    Context                    context      = ApplicationDependencies.getApplication();
    boolean                    isMmsEnabled = Util.isMmsCapable(context);
    String                     message      = multiShareArgs.getDraftText();
    SlideDeck                  slideDeck;

    try {
      slideDeck = buildSlideDeck(context, multiShareArgs);
    } catch (SlideNotFoundException e) {
      Log.w(TAG, "Could not create slide for media message");
      for (ShareContactAndThread shareContactAndThread : multiShareArgs.getShareContactAndThreads()) {
        results.add(new MultiShareSendResult(shareContactAndThread, MultiShareSendResult.Type.GENERIC_ERROR));
      }

      return new MultiShareSendResultCollection(results);
    }


    for (ShareContactAndThread shareContactAndThread : multiShareArgs.getShareContactAndThreads()) {
      Recipient recipient = Recipient.resolved(shareContactAndThread.getRecipientId());

      List<Mention>   mentions       = getValidMentionsForRecipient(recipient, multiShareArgs.getMentions());
      TransportOption transport      = resolveTransportOption(context, recipient);
      boolean         forceSms       = recipient.isForceSmsSelection() && transport.isSms();
      int             subscriptionId = transport.getSimSubscriptionId().or(-1);
      long            expiresIn      = TimeUnit.SECONDS.toMillis(recipient.getExpiresInSeconds());
      boolean         needsSplit     = !transport.isSms() &&
                                       message != null    &&
                                       message.length() > transport.calculateCharacters(message).maxPrimaryMessageSize;
      boolean         hasMmsMedia    = !multiShareArgs.getMedia().isEmpty()                                              ||
                                       (multiShareArgs.getDataUri() != null && multiShareArgs.getDataUri() != Uri.EMPTY) ||
                                       multiShareArgs.getStickerLocator() != null                                        ||
                                       recipient.isGroup()                                                               ||
                                       recipient.getEmail().isPresent();
      boolean         hasPushMedia   = hasMmsMedia                             ||
                                       multiShareArgs.getLinkPreview() != null ||
                                       !mentions.isEmpty()                     ||
                                       needsSplit;

      if ((recipient.isMmsGroup() || recipient.getEmail().isPresent()) && !isMmsEnabled) {
        results.add(new MultiShareSendResult(shareContactAndThread, MultiShareSendResult.Type.MMS_NOT_ENABLED));
      } else if (hasMmsMedia && transport.isSms() || hasPushMedia && !transport.isSms()) {
        sendMediaMessage(context, multiShareArgs, recipient, slideDeck, transport, shareContactAndThread.getThreadId(), forceSms, expiresIn, multiShareArgs.isViewOnce(), subscriptionId, mentions, shareContactAndThread.isStory());
        results.add(new MultiShareSendResult(shareContactAndThread, MultiShareSendResult.Type.SUCCESS));
      } else if (shareContactAndThread.isStory()) {
        results.add(new MultiShareSendResult(shareContactAndThread, MultiShareSendResult.Type.INVALID_SHARE_TO_STORY));
      } else {
        sendTextMessage(context, multiShareArgs, recipient, shareContactAndThread.getThreadId(), forceSms, expiresIn, subscriptionId);
        results.add(new MultiShareSendResult(shareContactAndThread, MultiShareSendResult.Type.SUCCESS));
      }

      // XXX We must do this to avoid sending out messages to the same recipient with the same
      //     sentTimestamp. If we do this, they'll be considered dupes by the receiver.
      ThreadUtil.sleep(5);
    }

    return new MultiShareSendResultCollection(results);
  }

  public static @NonNull TransportOption getWorstTransportOption(@NonNull Context context, @NonNull Set<ShareContactAndThread> shareContactAndThreads) {
    for (ShareContactAndThread shareContactAndThread : shareContactAndThreads) {
      TransportOption option = resolveTransportOption(context, shareContactAndThread.isForceSms());
      if (option.isSms()) {
        return option;
      }
    }

    return TransportOptions.getPushTransportOption(context);
  }

  private static @NonNull TransportOption resolveTransportOption(@NonNull Context context, @NonNull Recipient recipient) {
    return resolveTransportOption(context, recipient.isForceSmsSelection() || !recipient.isRegistered());
  }

  public static @NonNull TransportOption resolveTransportOption(@NonNull Context context, boolean forceSms) {
    if (forceSms) {
      TransportOptions options = new TransportOptions(context, false);
      options.setDefaultTransport(TransportOption.Type.SMS);
      return options.getSelectedTransport();
    } else {
      return TransportOptions.getPushTransportOption(context);
    }
  }

  private static void sendMediaMessage(@NonNull Context context,
                                       @NonNull MultiShareArgs multiShareArgs,
                                       @NonNull Recipient recipient,
                                       @NonNull SlideDeck slideDeck,
                                       @NonNull TransportOption transportOption,
                                       long threadId,
                                       boolean forceSms,
                                       long expiresIn,
                                       boolean isViewOnce,
                                       int subscriptionId,
                                       @NonNull List<Mention> validatedMentions,
                                       boolean isStory)
  {
    String body = multiShareArgs.getDraftText();
    if (transportOption.isType(TransportOption.Type.TEXTSECURE) && !forceSms && body != null) {
      MessageUtil.SplitResult splitMessage = MessageUtil.getSplitMessage(context, body, transportOption.calculateCharacters(body).maxPrimaryMessageSize);
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

      if (recipient.isActiveGroup()) {
        SignalDatabase.groups().markDisplayAsStory(recipient.requireGroupId());
      }

      for (final Slide slide : slideDeck.getSlides()) {
        SlideDeck singletonDeck = new SlideDeck();
        singletonDeck.addSlide(slide);

        OutgoingMediaMessage outgoingMediaMessage = new OutgoingMediaMessage(recipient,
                                                                             singletonDeck,
                                                                             body,
                                                                             System.currentTimeMillis(),
                                                                             subscriptionId,
                                                                             expiresIn,
                                                                             isViewOnce,
                                                                             ThreadDatabase.DistributionTypes.DEFAULT,
                                                                             storyType,
                                                                             null,
                                                                             null,
                                                                             Collections.emptyList(),
                                                                             multiShareArgs.getLinkPreview() != null ? Collections.singletonList(multiShareArgs.getLinkPreview())
                                                                                                                     : Collections.emptyList(),
                                                                             validatedMentions);

        outgoingMessages.add(outgoingMediaMessage);

        // XXX We must do this to avoid sending out messages to the same recipient with the same
        //     sentTimestamp. If we do this, they'll be considered dupes by the receiver.
        ThreadUtil.sleep(5);
      }
    } else {
      OutgoingMediaMessage outgoingMediaMessage = new OutgoingMediaMessage(recipient,
                                                                           slideDeck,
                                                                           body,
                                                                           System.currentTimeMillis(),
                                                                           subscriptionId,
                                                                           expiresIn,
                                                                           isViewOnce,
                                                                           ThreadDatabase.DistributionTypes.DEFAULT,
                                                                           StoryType.NONE,
                                                                           null,
                                                                           null,
                                                                           Collections.emptyList(),
                                                                           multiShareArgs.getLinkPreview() != null ? Collections.singletonList(multiShareArgs.getLinkPreview())
                                                                                                                   : Collections.emptyList(),
                                                                           validatedMentions);

      outgoingMessages.add(outgoingMediaMessage);
    }

    if (recipient.isRegistered() && !forceSms) {
      for (final OutgoingMediaMessage outgoingMessage : outgoingMessages) {
        MessageSender.send(context, new OutgoingSecureMediaMessage(outgoingMessage), threadId, false, null, null);
      }
    } else {
      for (final OutgoingMediaMessage outgoingMessage : outgoingMessages) {
        MessageSender.send(context, new OutgoingSecureMediaMessage(outgoingMessage), threadId, forceSms, null, null);
      }
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
    if (recipient.isRegistered() && !forceSms) {
      outgoingTextMessage = new OutgoingEncryptedMessage(recipient, multiShareArgs.getDraftText(), expiresIn);
    } else {
      outgoingTextMessage = new OutgoingTextMessage(recipient, multiShareArgs.getDraftText(), expiresIn, subscriptionId);
    }

    MessageSender.send(context, outgoingTextMessage, threadId, forceSms, null, null);
  }

  private static @NonNull SlideDeck buildSlideDeck(@NonNull Context context, @NonNull MultiShareArgs multiShareArgs) throws SlideNotFoundException {
    SlideDeck slideDeck = new SlideDeck();
    if (multiShareArgs.getStickerLocator() != null) {
      slideDeck.addSlide(new StickerSlide(context, multiShareArgs.getDataUri(), 0, multiShareArgs.getStickerLocator(), multiShareArgs.getDataType()));
    } else if (!multiShareArgs.getMedia().isEmpty()) {
      for (Media media : multiShareArgs.getMedia()) {
        Slide slide = SlideFactory.getSlide(context, media.getMimeType(), media.getUri(), media.getWidth(), media.getHeight());
        if (slide != null) {
          slideDeck.addSlide(slide);
        } else {
          throw new SlideNotFoundException();
        }
      }
    } else if (multiShareArgs.getDataUri() != null) {
      Slide slide = SlideFactory.getSlide(context, multiShareArgs.getDataType(), multiShareArgs.getDataUri(), 0, 0);
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
      Set<RecipientId> validRecipientIds = recipient.getParticipants()
                                                    .stream()
                                                    .map(Recipient::getId)
                                                    .collect(Collectors.toSet());

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
    private final ShareContactAndThread contactAndThread;
    private final Type                  type;

    private MultiShareSendResult(ShareContactAndThread contactAndThread, Type type) {
      this.contactAndThread = contactAndThread;
      this.type             = type;
    }

    public ShareContactAndThread getContactAndThread() {
      return contactAndThread;
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
