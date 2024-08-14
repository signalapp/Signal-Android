package org.thoughtcrime.securesms.messagedetails;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.CountDownTimer;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.RecyclerView;

import androidx.media3.common.MediaItem;

import com.bumptech.glide.RequestManager;

import org.signal.core.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.BindableConversationItem;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.conversation.ConversationItem;
import org.thoughtcrime.securesms.conversation.ConversationMessage;
import org.thoughtcrime.securesms.conversation.colors.Colorizable;
import org.thoughtcrime.securesms.conversation.colors.Colorizer;
import org.thoughtcrime.securesms.conversation.ConversationItemDisplayMode;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4Playable;
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4PlaybackPolicyEnforcer;
import org.thoughtcrime.securesms.messagedetails.MessageDetailsAdapter.Callbacks;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.util.DateUtils;
import org.thoughtcrime.securesms.util.ExpirationUtil;
import org.thoughtcrime.securesms.util.RemoteConfig;
import org.thoughtcrime.securesms.util.Projection;
import org.thoughtcrime.securesms.util.ProjectionList;

import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

final class MessageHeaderViewHolder extends RecyclerView.ViewHolder implements GiphyMp4Playable, Colorizable {
  private final TextView      sentDate;
  private final TextView      receivedDate;
  private final TextView      expiresIn;
  private final TextView      transport;
  private final TextView      errorText;
  private final View          resendButton;
  private final View          messageMetadata;
  private final View          internalDetailsButton;
  private final ViewStub      updateStub;
  private final ViewStub      sentStub;
  private final ViewStub      receivedStub;
  private final Colorizer     colorizer;
  private final RequestManager requestManager;
  private final Callbacks      callbacks;

  private ConversationItem conversationItem;
  private CountDownTimer   expiresUpdater;

  MessageHeaderViewHolder(@NonNull View itemView, RequestManager requestManager, @NonNull Colorizer colorizer, @NonNull Callbacks callbacks) {
    super(itemView);
    this.requestManager = requestManager;
    this.colorizer      = colorizer;
    this.callbacks      = callbacks;

    sentDate              = itemView.findViewById(R.id.message_details_header_sent_time);
    receivedDate          = itemView.findViewById(R.id.message_details_header_received_time);
    expiresIn             = itemView.findViewById(R.id.message_details_header_expires_in);
    transport             = itemView.findViewById(R.id.message_details_header_transport);
    errorText             = itemView.findViewById(R.id.message_details_header_error_text);
    resendButton          = itemView.findViewById(R.id.message_details_header_resend_button);
    messageMetadata       = itemView.findViewById(R.id.message_details_header_message_metadata);
    internalDetailsButton = itemView.findViewById(R.id.message_details_header_internal_details_button);
    updateStub            = itemView.findViewById(R.id.message_details_header_message_view_update);
    sentStub              = itemView.findViewById(R.id.message_details_header_message_view_sent_multimedia);
    receivedStub          = itemView.findViewById(R.id.message_details_header_message_view_received_multimedia);
  }

  void bind(@NonNull LifecycleOwner lifecycleOwner, @NonNull ConversationMessage conversationMessage) {
    MessageRecord messageRecord = conversationMessage.getMessageRecord();
    bindMessageView(lifecycleOwner, conversationMessage);
    bindErrorState(messageRecord);
    bindSentReceivedDates(messageRecord);
    bindExpirationTime(lifecycleOwner, messageRecord);
    bindTransport(messageRecord);
    bindInternalDetails(messageRecord);
  }

  private void bindInternalDetails(MessageRecord messageRecord) {
    if (!RemoteConfig.internalUser()) {
      internalDetailsButton.setVisibility(View.GONE);
      return;
    }

    internalDetailsButton.setVisibility(View.VISIBLE);
    internalDetailsButton.setOnClickListener(v -> callbacks.onInternalDetailsClicked(messageRecord));
  }

  private void bindMessageView(@NonNull LifecycleOwner lifecycleOwner, @NonNull ConversationMessage conversationMessage) {
    if (conversationItem == null) {
      if (conversationMessage.getMessageRecord().isGroupAction()) {
        conversationItem = (ConversationItem) updateStub.inflate();
      } else if (conversationMessage.getMessageRecord().isOutgoing()) {
        conversationItem = (ConversationItem) sentStub.inflate();
      } else {
        conversationItem = (ConversationItem) receivedStub.inflate();
      }
    }

    conversationItem.setEventListener(callbacks);

    conversationItem.bind(lifecycleOwner,
                          conversationMessage,
                          Optional.empty(),
                          Optional.empty(),
                          requestManager,
                          Locale.getDefault(),
                          new HashSet<>(),
                          conversationMessage.getMessageRecord().getToRecipient(),
                          null,
                          false,
                          false,
                          false,
                          true,
                          colorizer,
                          ConversationItemDisplayMode.Detailed.INSTANCE);
  }

  private void bindErrorState(MessageRecord messageRecord) {
    if (messageRecord.hasFailedWithNetworkFailures()) {
      errorText.setVisibility(View.VISIBLE);
      resendButton.setVisibility(View.VISIBLE);
      resendButton.setOnClickListener(unused -> {
        resendButton.setOnClickListener(null);
        SignalExecutors.BOUNDED.execute(() -> MessageSender.resend(itemView.getContext().getApplicationContext(), messageRecord));
      });
      messageMetadata.setVisibility(View.GONE);
    } else if (messageRecord.isFailed()) {
      errorText.setVisibility(View.VISIBLE);
      resendButton.setVisibility(View.GONE);
      resendButton.setOnClickListener(null);
      messageMetadata.setVisibility(View.GONE);
    } else {
      errorText.setVisibility(View.GONE);
      resendButton.setVisibility(View.GONE);
      resendButton.setOnClickListener(null);
      messageMetadata.setVisibility(View.VISIBLE);
    }
  }

  private void bindSentReceivedDates(MessageRecord messageRecord) {
    sentDate.setOnLongClickListener(null);
    receivedDate.setOnLongClickListener(null);

    if (messageRecord.isPending() || messageRecord.isFailed()) {
      sentDate.setText(formatBoldString(R.string.message_details_header_sent, "-"));
      receivedDate.setVisibility(View.GONE);
    } else {
      Locale           dateLocale    = Locale.getDefault();
      SimpleDateFormat dateFormatter = DateUtils.getDetailedDateFormatter(itemView.getContext(), dateLocale);
      sentDate.setText(formatBoldString(R.string.message_details_header_sent, dateFormatter.format(new Date(messageRecord.getDateSent()))));
      sentDate.setOnLongClickListener(v -> {
        copyToClipboard(String.valueOf(messageRecord.getDateSent()));
        return true;
      });

      if (messageRecord.getDateReceived() != messageRecord.getDateSent() && !messageRecord.isOutgoing()) {
        receivedDate.setText(formatBoldString(R.string.message_details_header_received, dateFormatter.format(new Date(messageRecord.getDateReceived()))));
        receivedDate.setOnLongClickListener(v -> {
          copyToClipboard(String.valueOf(messageRecord.getDateReceived()));
          return true;
        });
        receivedDate.setVisibility(View.VISIBLE);
      } else {
        receivedDate.setVisibility(View.GONE);
      }
    }
  }

  private void bindExpirationTime(@NonNull LifecycleOwner lifecycleOwner, @NonNull MessageRecord messageRecord) {
    if (expiresUpdater != null) {
      expiresUpdater.cancel();
      expiresUpdater = null;
    }

    if (messageRecord.getExpiresIn() <= 0 || messageRecord.getExpireStarted() <= 0) {
      expiresIn.setVisibility(View.GONE);
      return;
    }

    expiresIn.setVisibility(View.VISIBLE);

    lifecycleOwner.getLifecycle().addObserver(new DefaultLifecycleObserver() {
      @Override
      public void onResume(@NonNull LifecycleOwner owner) {
        if (expiresUpdater != null) {
          expiresUpdater.cancel();
        }

        long elapsed    = System.currentTimeMillis() - messageRecord.getExpireStarted();
        long remaining  = messageRecord.getExpiresIn() - elapsed;
        long updateRate = (remaining < TimeUnit.HOURS.toMillis(1)) ? TimeUnit.SECONDS.toMillis(1) : TimeUnit.MINUTES.toMillis(1);

        expiresUpdater = new CountDownTimer(remaining, updateRate) {
          @Override
          public void onTick(long millisUntilFinished) {
            int    expirationTime = Math.max((int) (TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished)), 1);
            String duration       = ExpirationUtil.getExpirationDisplayValue(itemView.getContext(), expirationTime);

            expiresIn.setText(formatBoldString(R.string.message_details_header_disappears, duration));
          }

          @Override
          public void onFinish() {}
        };
        expiresUpdater.start();
      }

      @Override
      public void onPause(@NonNull LifecycleOwner owner) {
        if (expiresUpdater != null) {
          expiresUpdater.cancel();
          expiresUpdater = null;
        }
      }
    });
  }

  private void bindTransport(MessageRecord messageRecord) {
    final String transportText;
    if (messageRecord.isOutgoing() && messageRecord.isFailed()) {
      transportText = "-";
    } else if (messageRecord.isPending()) {
      transportText = itemView.getContext().getString(R.string.ConversationFragment_pending);
    } else if (messageRecord.isPush()) {
      transportText = itemView.getContext().getString(R.string.ConversationFragment_push);
    } else if (messageRecord.isMms()) {
      transportText = itemView.getContext().getString(R.string.ConversationFragment_mms);
    } else {
      transportText = itemView.getContext().getString(R.string.ConversationFragment_sms);
    }

    transport.setText(formatBoldString(R.string.message_details_header_via, transportText));
  }

  private CharSequence formatBoldString(int boldTextRes, CharSequence otherText) {
    SpannableStringBuilder builder  = new SpannableStringBuilder();
    StyleSpan              boldSpan = new StyleSpan(android.graphics.Typeface.BOLD);
    CharSequence           boldText = itemView.getContext().getString(boldTextRes);

    builder.append(boldText).append(" ").append(otherText);
    builder.setSpan(boldSpan, 0, boldText.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

    return builder;
  }

  private void copyToClipboard(String text) {
    ((ClipboardManager) itemView.getContext().getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("text", text));
  }

  @Override
  public void showProjectionArea() {
    conversationItem.showProjectionArea();
  }

  @Override
  public void hideProjectionArea() {
    conversationItem.hideProjectionArea();
  }

  @Override
  public @Nullable MediaItem getMediaItem() {
    return conversationItem.getMediaItem();
  }

  @Override
  public @Nullable GiphyMp4PlaybackPolicyEnforcer getPlaybackPolicyEnforcer() {
    return conversationItem.getPlaybackPolicyEnforcer();
  }

  @Override
  public @NonNull Projection getGiphyMp4PlayableProjection(@NonNull ViewGroup recyclerview) {
    return conversationItem.getGiphyMp4PlayableProjection(recyclerview);
  }

  @Override
  public boolean canPlayContent() {
    return conversationItem.canPlayContent();
  }

  @Override
  public boolean shouldProjectContent() {
    return conversationItem.shouldProjectContent();
  }

  @Override
  public @NonNull ProjectionList getColorizerProjections(@NonNull ViewGroup coordinateRoot) {
    return conversationItem.getColorizerProjections(coordinateRoot);
  }
}
