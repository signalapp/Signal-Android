package org.thoughtcrime.securesms.components;

import android.Manifest;
import android.animation.Animator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;

import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieProperty;
import com.airbnb.lottie.model.KeyPath;

import org.signal.core.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.animation.AnimationCompleteListener;
import org.thoughtcrime.securesms.conversation.ConversationItemDisplayMode;
import org.thoughtcrime.securesms.conversation.v2.computed.FormattedDate;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.MmsMessageRecord;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.DateUtils;
import org.thoughtcrime.securesms.util.MessageRecordUtil;
import org.thoughtcrime.securesms.util.Projection;
import org.thoughtcrime.securesms.util.SignalLocalMetrics;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.dualsim.SubscriptionInfoCompat;
import org.thoughtcrime.securesms.util.dualsim.SubscriptionManagerCompat;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class ConversationItemFooter extends ConstraintLayout {

  private TextView                    dateView;
  private TextView                    simView;
  private ExpirationTimerView         timerView;
  private ImageView                   insecureIndicatorView;
  private DeliveryStatusView          deliveryStatusView;
  private boolean                     onlyShowSendingStatus;
  private TextView                    audioDuration;
  private LottieAnimationView         revealDot;
  private PlaybackSpeedToggleTextView playbackSpeedToggleTextView;
  private boolean                     hasShrunkDate;

  private OnTouchDelegateChangedListener onTouchDelegateChangedListener;

  private final Rect speedToggleHitRect = new Rect();
  private final int  touchTargetSize    = ViewUtil.dpToPx(48);

  private long previousMessageId;

  public ConversationItemFooter(Context context) {
    super(context);
    init(null);
  }

  public ConversationItemFooter(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    init(attrs);
  }

  public ConversationItemFooter(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init(attrs);
  }

  private void init(@Nullable AttributeSet attrs) {
    final TypedArray typedArray;
    if (attrs != null) {
      typedArray = getContext().getTheme().obtainStyledAttributes(attrs, R.styleable.ConversationItemFooter, 0, 0);
    } else {
      typedArray = null;
    }

    inflate(getContext(), R.layout.conversation_item_footer, this);

    dateView                    = findViewById(R.id.footer_date);
    simView                     = findViewById(R.id.footer_sim_info);
    timerView                   = findViewById(R.id.footer_expiration_timer);
    insecureIndicatorView       = findViewById(R.id.footer_insecure_indicator);
    deliveryStatusView          = findViewById(R.id.footer_delivery_status);
    audioDuration               = findViewById(R.id.footer_audio_duration);
    revealDot                   = findViewById(R.id.footer_revealed_dot);
    playbackSpeedToggleTextView = findViewById(R.id.footer_audio_playback_speed_toggle);

    if (typedArray != null) {
      int     mode       = typedArray.getInt(R.styleable.ConversationItemFooter_footer_mode, 0);
      boolean isOutgoing = mode == 0;
      if (isOutgoing) {
        playbackSpeedToggleTextView.setTextColor(getResources().getColor(R.color.core_white));
        playbackSpeedToggleTextView.setBackgroundTintList(AppCompatResources.getColorStateList(getContext(), R.color.transparent_white_20));
      } else {
        playbackSpeedToggleTextView.setTextColor(getResources().getColor(R.color.signal_text_secondary));
        playbackSpeedToggleTextView.setBackgroundTintList(AppCompatResources.getColorStateList(getContext(), R.color.transparent_black_08));
      }

      setTextColor(typedArray.getInt(R.styleable.ConversationItemFooter_footer_text_color, getResources().getColor(R.color.core_white)));
      setIconColor(typedArray.getInt(R.styleable.ConversationItemFooter_footer_icon_color, getResources().getColor(R.color.core_white)));
      setRevealDotColor(typedArray.getInt(R.styleable.ConversationItemFooter_footer_reveal_dot_color, getResources().getColor(R.color.core_white)));
      typedArray.recycle();
    }

    dateView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
      if (oldLeft != left || oldRight != right) {
        notifyTouchDelegateChanged(getPlaybackSpeedToggleTouchDelegateRect(), playbackSpeedToggleTextView);
      }
    });
  }

  public void setOnTouchDelegateChangedListener(@Nullable OnTouchDelegateChangedListener onTouchDelegateChangedListener) {
    this.onTouchDelegateChangedListener = onTouchDelegateChangedListener;
  }

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    timerView.stopAnimation();
  }

  public void setMessageRecord(@NonNull MessageRecord messageRecord, @NonNull Locale locale, @NonNull ConversationItemDisplayMode displayMode) {
    presentDate(messageRecord, locale, displayMode);
    presentSimInfo(messageRecord);
    presentTimer(messageRecord);
    presentInsecureIndicator(messageRecord);
    presentDeliveryStatus(messageRecord);
    presentAudioDuration(messageRecord);
  }

  public void setAudioDuration(long totalDurationMillis, long currentPostionMillis) {
    long remainingSecs = Math.max(0, TimeUnit.MILLISECONDS.toSeconds(totalDurationMillis - currentPostionMillis));
    audioDuration.setText(getResources().getString(R.string.AudioView_duration, remainingSecs / 60, remainingSecs % 60));
  }

  public void setPlaybackSpeedListener(@Nullable PlaybackSpeedToggleTextView.PlaybackSpeedListener playbackSpeedListener) {
    playbackSpeedToggleTextView.setPlaybackSpeedListener(playbackSpeedListener);
  }

  public void setAudioPlaybackSpeed(float playbackSpeed, boolean isPlaying) {
    if (isPlaying) {
      showPlaybackSpeedToggle();
    } else {
      hidePlaybackSpeedToggle();
    }

    playbackSpeedToggleTextView.setCurrentSpeed(playbackSpeed);
  }

  public void setTextColor(int color) {
    dateView.setTextColor(color);
    simView.setTextColor(color);
    audioDuration.setTextColor(color);
  }

  public void setIconColor(int color) {
    timerView.setColorFilter(color, PorterDuff.Mode.SRC_IN);
    insecureIndicatorView.setColorFilter(color);
    deliveryStatusView.setTint(color);
  }

  public void setRevealDotColor(int color) {
    revealDot.addValueCallback(
        new KeyPath("**"),
        LottieProperty.COLOR_FILTER,
        frameInfo -> new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
    );
  }

  public void setOnlyShowSendingStatus(boolean onlyShowSending, MessageRecord messageRecord) {
    this.onlyShowSendingStatus = onlyShowSending;
    presentDeliveryStatus(messageRecord);
  }

  public void enableBubbleBackground(@DrawableRes int drawableRes, @Nullable Integer tint) {
    setBackgroundResource(drawableRes);

    if (tint != null) {
      getBackground().setColorFilter(tint, PorterDuff.Mode.MULTIPLY);
    } else {
      getBackground().clearColorFilter();
    }
  }

  public void disableBubbleBackground() {
    setBackground(null);
  }

  public @Nullable Projection getProjection(@NonNull ViewGroup coordinateRoot) {
    if (getVisibility() == VISIBLE) {
      return Projection.relativeToParent(coordinateRoot, this, new Projection.Corners(ViewUtil.dpToPx(11)));
    } else {
      return null;
    }
  }

  public View getDateView() {
    return dateView;
  }

  private void notifyTouchDelegateChanged(@NonNull Rect rect, @NonNull View touchDelegate) {
    if (onTouchDelegateChangedListener != null) {
      onTouchDelegateChangedListener.onTouchDelegateChanged(rect, touchDelegate);
    }
  }

  private void showPlaybackSpeedToggle() {
    if (hasShrunkDate) {
      return;
    }

    hasShrunkDate = true;

    playbackSpeedToggleTextView.animate()
                               .alpha(1f)
                               .scaleX(1f)
                               .scaleY(1f)
                               .setDuration(150L)
                               .setListener(new AnimationCompleteListener() {
                                 @Override
                                 public void onAnimationEnd(Animator animation) {
                                   playbackSpeedToggleTextView.setClickable(true);
                                 }
                               });

      dateView.setMaxWidth(ViewUtil.dpToPx(32));
  }

  private void hidePlaybackSpeedToggle() {
    if (!hasShrunkDate) {
      return;
    }

    hasShrunkDate = false;

    playbackSpeedToggleTextView.animate()
                               .alpha(0f)
                               .scaleX(0.5f)
                               .scaleY(0.5f)
                               .setDuration(150L).setListener(new AnimationCompleteListener() {
                                 @Override
                                 public void onAnimationEnd(Animator animation) {
                                   playbackSpeedToggleTextView.setClickable(false);
                                   playbackSpeedToggleTextView.clearRequestedSpeed();
                                 }
                               });

      dateView.setMaxWidth(Integer.MAX_VALUE);
  }

  private @NonNull Rect getPlaybackSpeedToggleTouchDelegateRect() {
    playbackSpeedToggleTextView.getHitRect(speedToggleHitRect);

    int widthOffset  = (touchTargetSize - speedToggleHitRect.width()) / 2;
    int heightOffset = (touchTargetSize - speedToggleHitRect.height()) / 2;

    speedToggleHitRect.top -= heightOffset;
    speedToggleHitRect.left -= widthOffset;
    speedToggleHitRect.right += widthOffset;
    speedToggleHitRect.bottom += heightOffset;

    return speedToggleHitRect;
  }

  private void presentDate(@NonNull MessageRecord messageRecord, @NonNull Locale locale, @NonNull ConversationItemDisplayMode displayMode) {
    dateView.forceLayout();
     if (MessageRecordUtil.isScheduled(messageRecord)) {
      dateView.setText(DateUtils.getOnlyTimeString(getContext(), ((MmsMessageRecord) messageRecord).getScheduledDate()));
    } else if (messageRecord.isMediaPending()) {
      dateView.setText(null);
    } else if (messageRecord.isFailed()) {
      int errorMsg;
      if (messageRecord.hasFailedWithNetworkFailures()) {
        errorMsg = R.string.ConversationItem_error_network_not_delivered;
      } else if (messageRecord.getToRecipient().isPushGroup() && messageRecord.isIdentityMismatchFailure()) {
        errorMsg = R.string.ConversationItem_error_partially_not_delivered;
      } else {
        errorMsg = R.string.ConversationItem_error_not_sent_tap_for_details;
      }

      dateView.setText(errorMsg);
    } else if (messageRecord.isRateLimited()) {
      dateView.setText(R.string.ConversationItem_send_paused);
    } else {
      long timestamp = messageRecord.getTimestamp();
      FormattedDate date = DateUtils.getDatelessRelativeTimeSpanFormattedDate(getContext(), locale, timestamp);
      String dateLabel = date.getValue();
      if (displayMode != ConversationItemDisplayMode.Detailed.INSTANCE && messageRecord.isEditMessage() && messageRecord.isLatestRevision()) {
        if (date.isNow()) {
          dateLabel = getContext().getString(R.string.ConversationItem_edited_now_timestamp_footer);
        } else if (date.isRelative()) {
          dateLabel = getContext().getString(R.string.ConversationItem_edited_relative_timestamp_footer, date.getValue());
        } else {
          dateLabel = getContext().getString(R.string.ConversationItem_edited_absolute_timestamp_footer, date.getValue());
        }
      }
      dateView.setText(dateLabel);
    }
  }

  private void presentSimInfo(@NonNull MessageRecord messageRecord) {
    SubscriptionManagerCompat subscriptionManager = new SubscriptionManagerCompat(getContext());

    if (messageRecord.isPush() || messageRecord.getSubscriptionId() == -1 || !Permissions.hasAll(getContext(), Manifest.permission.READ_PHONE_STATE) || !subscriptionManager.isMultiSim()) {
      simView.setVisibility(View.GONE);
    } else {
      Optional<SubscriptionInfoCompat> subscriptionInfo = subscriptionManager.getActiveSubscriptionInfo(messageRecord.getSubscriptionId());

      if (subscriptionInfo.isPresent() && messageRecord.isOutgoing()) {
        simView.setText(getContext().getString(R.string.ConversationItem_from_s, subscriptionInfo.get().getDisplayName()));
        simView.setVisibility(View.VISIBLE);
      } else if (subscriptionInfo.isPresent()) {
        simView.setText(getContext().getString(R.string.ConversationItem_to_s, subscriptionInfo.get().getDisplayName()));
        simView.setVisibility(View.VISIBLE);
      } else {
        simView.setVisibility(View.GONE);
      }
    }
  }

  @SuppressLint("StaticFieldLeak")
  private void presentTimer(@NonNull final MessageRecord messageRecord) {
    if (messageRecord.getExpiresIn() > 0 && !messageRecord.isPending()) {
      this.timerView.setVisibility(View.VISIBLE);
      this.timerView.setPercentComplete(0);

      if (messageRecord.getExpireStarted() > 0) {
        this.timerView.setExpirationTime(messageRecord.getExpireStarted(),
                                         messageRecord.getExpiresIn());
        this.timerView.startAnimation();

        if (messageRecord.getExpireStarted() + messageRecord.getExpiresIn() <= System.currentTimeMillis()) {
          AppDependencies.getExpiringMessageManager().checkSchedule();
        }
      } else if (!messageRecord.isOutgoing() && !messageRecord.isMediaPending()) {
        SignalExecutors.BOUNDED.execute(() -> {
          long    id  = messageRecord.getId();
          boolean mms = messageRecord.isMms();
          long    now = System.currentTimeMillis();

          SignalDatabase.messages().markExpireStarted(id, now);
          AppDependencies.getExpiringMessageManager().scheduleDeletion(id, mms, now, messageRecord.getExpiresIn());
        });
      }
    } else {
      this.timerView.setVisibility(View.GONE);
    }
  }

  private void presentInsecureIndicator(@NonNull MessageRecord messageRecord) {
    insecureIndicatorView.setVisibility(messageRecord.isSecure() ? View.GONE : View.VISIBLE);
  }

  private void presentDeliveryStatus(@NonNull MessageRecord messageRecord) {
    long newMessageId = buildMessageId(messageRecord);

    if (previousMessageId == newMessageId && deliveryStatusView.isPending() && !messageRecord.isPending()) {
      if (messageRecord.getToRecipient().isGroup()) {
        SignalLocalMetrics.GroupMessageSend.onUiUpdated(messageRecord.getId());
      } else {
        SignalLocalMetrics.IndividualMessageSend.onUiUpdated(messageRecord.getId());
      }
    }

    previousMessageId = newMessageId;


    if (messageRecord.isFailed() || MessageRecordUtil.isScheduled(messageRecord)) {
      deliveryStatusView.setNone();
      return;
    }

    if (onlyShowSendingStatus) {
      if (messageRecord.isOutgoing() && messageRecord.isPending()) {
        deliveryStatusView.setPending();
      } else {
        deliveryStatusView.setNone();
      }
    } else {
      if (!messageRecord.isOutgoing()) {
        deliveryStatusView.setNone();
      } else if (messageRecord.isPending()) {
        deliveryStatusView.setPending();
      } else if (messageRecord.hasReadReceipt()) {
        deliveryStatusView.setRead();
      } else if (messageRecord.isDelivered()) {
        deliveryStatusView.setDelivered();
      } else {
        deliveryStatusView.setSent();
      }
    }
  }

  private void presentAudioDuration(@NonNull MessageRecord messageRecord) {
    if (messageRecord.isMms()) {
      MmsMessageRecord mmsMessageRecord = (MmsMessageRecord) messageRecord;

      if (mmsMessageRecord.getSlideDeck().getAudioSlide() != null) {
        showAudioDurationViews();

        if (messageRecord.isViewed() || (messageRecord.isOutgoing() && Objects.equals(messageRecord.getToRecipient(), Recipient.self()))) {
          revealDot.setProgress(1f);
        } else {
          revealDot.setProgress(0f);
        }
      } else {
        hideAudioDurationViews();
      }
    } else {
      hideAudioDurationViews();
    }
  }

  private void showAudioDurationViews() {
    audioDuration.setVisibility(View.VISIBLE);
    revealDot.setVisibility(View.VISIBLE);
    playbackSpeedToggleTextView.setVisibility(View.VISIBLE);
  }

  private void hideAudioDurationViews() {
    audioDuration.setVisibility(View.GONE);
    revealDot.setVisibility(View.GONE);
    playbackSpeedToggleTextView.setVisibility(View.GONE);
  }

  private long buildMessageId(@NonNull MessageRecord record) {
    return record.isMms() ? -record.getId() : record.getId();
  }

  public interface OnTouchDelegateChangedListener {
    void onTouchDelegateChanged(@NonNull Rect delegateRect, @NonNull View delegateView);
  }
}
