package org.thoughtcrime.securesms.sharing;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.core.util.Consumer;

import com.annimon.stream.Stream;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.TransportOption;
import org.thoughtcrime.securesms.TransportOptions;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.StickerDatabase;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.database.model.StickerRecord;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.mediasend.Media;
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.mms.SlideFactory;
import org.thoughtcrime.securesms.mms.StickerSlide;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFormattingException;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.sms.OutgoingTextMessage;
import org.thoughtcrime.securesms.stickers.StickerLocator;
import org.thoughtcrime.securesms.util.MessageUtil;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.concurrent.SimpleTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

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
    SimpleTask.run(() -> sendInternal(multiShareArgs), results::accept);
  }

  @WorkerThread
  private static MultiShareSendResultCollection sendInternal(@NonNull MultiShareArgs multiShareArgs) {
    Context   context      = ApplicationDependencies.getApplication();
    boolean   isMmsEnabled = Util.isMmsCapable(context);
    String    message      = multiShareArgs.getDraftText();
    SlideDeck slideDeck    = buildSlideDeck(context, multiShareArgs);

    List<MultiShareSendResult> results = new ArrayList<>(multiShareArgs.getShareContactAndThreads().size());

    for (ShareContactAndThread shareContactAndThread : multiShareArgs.getShareContactAndThreads()) {
      Recipient recipient = Recipient.resolved(shareContactAndThread.getRecipientId());

      TransportOption transport      = resolveTransportOption(context, recipient);
      boolean         forceSms       = recipient.isForceSmsSelection() && transport.isSms();
      int             subscriptionId = transport.getSimSubscriptionId().or(-1);
      long            expiresIn      = recipient.getExpireMessages() * 1000L;
      boolean         needsSplit     = !transport.isSms() &&
                                       message != null    &&
                                       message.length() > transport.calculateCharacters(message).maxPrimaryMessageSize;
      boolean         isMediaMessage = !multiShareArgs.getMedia().isEmpty()                                              ||
                                       (multiShareArgs.getDataUri() != null && multiShareArgs.getDataUri() != Uri.EMPTY) ||
                                       multiShareArgs.getStickerLocator() != null                                        ||
                                       multiShareArgs.getLinkPreview() != null                                           ||
                                       recipient.isGroup()                                                               ||
                                       recipient.getEmail().isPresent()                                                  ||
                                       needsSplit;

      if ((recipient.isMmsGroup() || recipient.getEmail().isPresent()) && !isMmsEnabled) {
        results.add(new MultiShareSendResult(shareContactAndThread, MultiShareSendResult.Type.MMS_NOT_ENABLED));
      } else if (isMediaMessage) {
        sendMediaMessage(context, multiShareArgs, recipient, slideDeck, transport, shareContactAndThread.getThreadId(), forceSms, expiresIn, multiShareArgs.isViewOnce(), subscriptionId);
        results.add(new MultiShareSendResult(shareContactAndThread, MultiShareSendResult.Type.SUCCESS));
      } else {
        sendTextMessage(context, multiShareArgs, recipient, shareContactAndThread.getThreadId() ,forceSms, expiresIn, subscriptionId);
        results.add(new MultiShareSendResult(shareContactAndThread, MultiShareSendResult.Type.SUCCESS));
      }
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
                                       int subscriptionId)
  {
    String body = multiShareArgs.getDraftText();
    if (transportOption.isType(TransportOption.Type.TEXTSECURE) && !forceSms && body != null) {
      MessageUtil.SplitResult splitMessage = MessageUtil.getSplitMessage(context, body, transportOption.calculateCharacters(body).maxPrimaryMessageSize);
      body = splitMessage.getBody();

      if (splitMessage.getTextSlide().isPresent()) {
        slideDeck.addSlide(splitMessage.getTextSlide().get());
      }
    }

    OutgoingMediaMessage outgoingMediaMessage = new OutgoingMediaMessage(recipient,
                                                                         slideDeck,
                                                                         body,
                                                                         System.currentTimeMillis(),
                                                                         subscriptionId,
                                                                         expiresIn,
                                                                         isViewOnce,
                                                                         ThreadDatabase.DistributionTypes.DEFAULT,
                                                                         null,
                                                                         Collections.emptyList(),
                                                                         multiShareArgs.getLinkPreview() != null ? Collections.singletonList(multiShareArgs.getLinkPreview())
                                                                                                                 : Collections.emptyList(),
                                                                         Collections.emptyList());

    MessageSender.send(context, outgoingMediaMessage, threadId, forceSms, null);
  }

  private static void sendTextMessage(@NonNull Context context,
                                      @NonNull MultiShareArgs multiShareArgs,
                                      @NonNull Recipient recipient,
                                      long threadId,
                                      boolean forceSms,
                                      long expiresIn,
                                      int subscriptionId)
  {
    OutgoingTextMessage outgoingTextMessage = new OutgoingTextMessage(recipient, multiShareArgs.getDraftText(), expiresIn, subscriptionId);

    MessageSender.send(context, outgoingTextMessage, threadId, forceSms, null);
  }

  private static @NonNull SlideDeck buildSlideDeck(@NonNull Context context, @NonNull MultiShareArgs multiShareArgs) {
    SlideDeck slideDeck = new SlideDeck();
    if (multiShareArgs.getStickerLocator() != null) {
      slideDeck.addSlide(new StickerSlide(context, multiShareArgs.getDataUri(), 0, multiShareArgs.getStickerLocator(), multiShareArgs.getDataType()));
    } else if (!multiShareArgs.getMedia().isEmpty()) {
      for (Media media : multiShareArgs.getMedia()) {
        slideDeck.addSlide(SlideFactory.getSlide(context, media.getMimeType(), media.getUri(), media.getWidth(), media.getHeight()));
      }
    } else if (multiShareArgs.getDataUri() != null) {
      slideDeck.addSlide(SlideFactory.getSlide(context, multiShareArgs.getDataType(), multiShareArgs.getDataUri(), 0, 0));
    }

    return slideDeck;
  }

  public static final class MultiShareSendResultCollection {
    private final List<MultiShareSendResult> results;

    private MultiShareSendResultCollection(List<MultiShareSendResult> results) {
      this.results = results;
    }

    public boolean containsFailures() {
      return Stream.of(results).anyMatch(result -> result.type != MultiShareSendResult.Type.SUCCESS);
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
      MMS_NOT_ENABLED,
      SUCCESS
    }
  }
}
