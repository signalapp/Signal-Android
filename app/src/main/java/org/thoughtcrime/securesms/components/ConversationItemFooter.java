package org.thoughtcrime.securesms.components;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieProperty;
import com.airbnb.lottie.model.KeyPath;

import org.signal.core.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.MmsMessageRecord;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.service.ExpiringMessageManager;
import org.thoughtcrime.securesms.util.DateUtils;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.dualsim.SubscriptionInfoCompat;
import org.thoughtcrime.securesms.util.dualsim.SubscriptionManagerCompat;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class ConversationItemFooter extends LinearLayout {

  private TextView            dateView;
  private TextView            simView;
  private ExpirationTimerView timerView;
  private ImageView           insecureIndicatorView;
  private DeliveryStatusView  deliveryStatusView;
  private boolean             onlyShowSendingStatus;
  private View                audioSpace;
  private TextView            audioDuration;
  private LottieAnimationView revealDot;

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
    inflate(getContext(), R.layout.conversation_item_footer, this);

    dateView              = findViewById(R.id.footer_date);
    simView               = findViewById(R.id.footer_sim_info);
    timerView             = findViewById(R.id.footer_expiration_timer);
    insecureIndicatorView = findViewById(R.id.footer_insecure_indicator);
    deliveryStatusView    = findViewById(R.id.footer_delivery_status);
    audioDuration         = findViewById(R.id.footer_audio_duration);
    audioSpace            = findViewById(R.id.footer_audio_duration_space);
    revealDot             = findViewById(R.id.footer_revealed_dot);

    if (attrs != null) {
      TypedArray typedArray = getContext().getTheme().obtainStyledAttributes(attrs, R.styleable.ConversationItemFooter, 0, 0);
      setTextColor(typedArray.getInt(R.styleable.ConversationItemFooter_footer_text_color, getResources().getColor(R.color.core_white)));
      setIconColor(typedArray.getInt(R.styleable.ConversationItemFooter_footer_icon_color, getResources().getColor(R.color.core_white)));
      setRevealDotColor(typedArray.getInt(R.styleable.ConversationItemFooter_footer_reveal_dot_color, getResources().getColor(R.color.core_white)));
      typedArray.recycle();
    }
  }

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    timerView.stopAnimation();
  }

  public void setMessageRecord(@NonNull MessageRecord messageRecord, @NonNull Locale locale) {
    presentDate(messageRecord, locale);
    presentSimInfo(messageRecord);
    presentTimer(messageRecord);
    presentInsecureIndicator(messageRecord);
    presentDeliveryStatus(messageRecord);
    hideAudioDurationViews();
  }

  public void setAudioDuration(long totalDurationMillis, long currentPostionMillis) {
    long remainingSecs = TimeUnit.MILLISECONDS.toSeconds(totalDurationMillis - currentPostionMillis);
    audioDuration.setText(getResources().getString(R.string.AudioView_duration, remainingSecs / 60, remainingSecs % 60));
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
        frameInfo -> new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_ATOP)
    );
  }

  public void setOnlyShowSendingStatus(boolean onlyShowSending, MessageRecord messageRecord) {
    this.onlyShowSendingStatus = onlyShowSending;
    presentDeliveryStatus(messageRecord);
  }

  private void presentDate(@NonNull MessageRecord messageRecord, @NonNull Locale locale) {
    dateView.forceLayout();
    if (messageRecord.isFailed()) {
      int errorMsg;
      if (messageRecord.hasFailedWithNetworkFailures()) {
        errorMsg = R.string.ConversationItem_error_network_not_delivered;
      } else if (messageRecord.getRecipient().isPushGroup() && messageRecord.isIdentityMismatchFailure()) {
        errorMsg = R.string.ConversationItem_error_partially_not_delivered;
      } else {
        errorMsg = R.string.ConversationItem_error_not_sent_tap_for_details;
      }

      dateView.setText(errorMsg);
    } else if (messageRecord.isPendingInsecureSmsFallback()) {
      dateView.setText(R.string.ConversationItem_click_to_approve_unencrypted);
    } else {
      dateView.setText(DateUtils.getExtendedRelativeTimeSpanString(getContext(), locale, messageRecord.getTimestamp()));
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
        simView.setText(getContext().getString(R.string.ConversationItem_to_s,  subscriptionInfo.get().getDisplayName()));
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
          ApplicationContext.getInstance(getContext()).getExpiringMessageManager().checkSchedule();
        }
      } else if (!messageRecord.isOutgoing() && !messageRecord.isMediaPending()) {
        SignalExecutors.BOUNDED.execute(() -> {
          ExpiringMessageManager expirationManager = ApplicationContext.getInstance(getContext()).getExpiringMessageManager();
          long                   id                = messageRecord.getId();
          boolean                mms               = messageRecord.isMms();

          if (mms) DatabaseFactory.getMmsDatabase(getContext()).markExpireStarted(id);
          else     DatabaseFactory.getSmsDatabase(getContext()).markExpireStarted(id);

          expirationManager.scheduleDeletion(id, mms, messageRecord.getExpiresIn());
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
    if (messageRecord.isFailed() || messageRecord.isPendingInsecureSmsFallback()) {
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
      if (!messageRecord.isOutgoing())  {
        deliveryStatusView.setNone();
      } else if (messageRecord.isPending()) {
        deliveryStatusView.setPending();
      } else if (messageRecord.isRemoteRead()) {
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
        if (messageRecord.isOutgoing()) {
          moveAudioViewsForOutgoing();
        } else {
          moveAudioViewsForIncoming();
        }
        showAudioDurationViews();
      } else {
        hideAudioDurationViews();
      }
    } else {
      hideAudioDurationViews();
    }
  }

  private void moveAudioViewsForOutgoing() {
    removeView(audioSpace);
    removeView(audioDuration);
    removeView(revealDot);
    addView(audioSpace, 0);
    addView(revealDot, 0);
    addView(audioDuration, 0);

    int padStart = ViewUtil.dpToPx(60);
    int padLeft  = getLayoutDirection() == LAYOUT_DIRECTION_LTR ? padStart : 0;
    int padRight = getLayoutDirection() == LAYOUT_DIRECTION_RTL ? padStart : 0;

    audioDuration.setPadding(padLeft, 0, padRight, 0);
  }

  private void moveAudioViewsForIncoming() {
    removeView(audioSpace);
    removeView(audioDuration);
    removeView(revealDot);
    addView(audioSpace);
    addView(revealDot);
    addView(audioDuration);

    audioDuration.setPadding(0, 0, 0, 0);
  }

  private void showAudioDurationViews() {
    audioSpace.setVisibility(View.VISIBLE);
    audioDuration.setVisibility(View.VISIBLE);

    if (FeatureFlags.viewedReceipts()) {
      revealDot.setVisibility(View.VISIBLE);
    }
  }

  private void hideAudioDurationViews() {
    audioSpace.setVisibility(View.GONE);
    audioDuration.setVisibility(View.GONE);
    revealDot.setVisibility(View.GONE);
  }
}
