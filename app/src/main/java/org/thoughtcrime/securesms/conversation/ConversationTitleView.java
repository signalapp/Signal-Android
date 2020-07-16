package org.thoughtcrime.securesms.conversation;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.recipients.LiveRecipient;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.ExpirationUtil;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;

import java.util.UUID;

public class ConversationTitleView extends RelativeLayout {

  @SuppressWarnings("unused")
  private static final String TAG = ConversationTitleView.class.getSimpleName();

  private View            content;
  private AvatarImageView avatar;
  private TextView        title;
  private TextView        subtitle;
  private ImageView       verified;
  private View            subtitleContainer;
  private View            verifiedSubtitle;
  private View            expirationBadgeContainer;
  private TextView        expirationBadgeTime;

  public ConversationTitleView(Context context) {
    this(context, null);
  }

  public ConversationTitleView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  public void onFinishInflate() {
    super.onFinishInflate();

    this.content                  = findViewById(R.id.content);
    this.title                    = findViewById(R.id.title);
    this.subtitle                 = findViewById(R.id.subtitle);
    this.verified                 = findViewById(R.id.verified_indicator);
    this.subtitleContainer        = findViewById(R.id.subtitle_container);
    this.verifiedSubtitle         = findViewById(R.id.verified_subtitle);
    this.avatar                   = findViewById(R.id.contact_photo_image);
    this.expirationBadgeContainer = findViewById(R.id.expiration_badge_container);
    this.expirationBadgeTime      = findViewById(R.id.expiration_badge);

    ViewUtil.setTextViewGravityStart(this.title, getContext());
    ViewUtil.setTextViewGravityStart(this.subtitle, getContext());
  }

  public void showExpiring(@NonNull LiveRecipient recipient) {
    expirationBadgeTime.setText(ExpirationUtil.getExpirationAbbreviatedDisplayValue(getContext(), recipient.get().getExpireMessages()));
    expirationBadgeContainer.setVisibility(View.VISIBLE);
    updateSubtitleVisibility();
  }

  public void clearExpiring() {
    expirationBadgeContainer.setVisibility(View.GONE);
    updateSubtitleVisibility();
  }

  public void setTitle(@NonNull GlideRequests glideRequests, @Nullable Recipient recipient) {
    this.subtitleContainer.setVisibility(View.VISIBLE);

    if      (recipient == null) setComposeTitle();
    else                        setRecipientTitle(recipient);

    int startDrawable = 0;
    int endDrawable   = 0;

    if (recipient != null && recipient.isBlocked()) {
      startDrawable = R.drawable.ic_block_white_18dp;
    } else if (recipient != null && recipient.isMuted()) {
      startDrawable = R.drawable.ic_volume_off_white_18dp;
    }

    if (recipient != null && recipient.isSystemContact() && !recipient.isLocalNumber()) {
      endDrawable = R.drawable.ic_profile_circle_outline_16;
    }

    title.setCompoundDrawablesRelativeWithIntrinsicBounds(startDrawable, 0, endDrawable, 0);
    TextViewCompat.setCompoundDrawableTintList(title, ColorStateList.valueOf(ContextCompat.getColor(getContext(), R.color.transparent_white_90)));

    if (recipient != null) {
      this.avatar.setAvatar(glideRequests, recipient, false);
    }

    updateVerifiedSubtitleVisibility();
  }

  public void setVerified(boolean verified) {
    this.verified.setVisibility(verified ? View.VISIBLE : View.GONE);

    updateVerifiedSubtitleVisibility();
  }

  private void setComposeTitle() {
    this.title.setText(R.string.ConversationActivity_compose_message);
    this.subtitle.setText(null);
    updateSubtitleVisibility();
  }

  private void setRecipientTitle(Recipient recipient) {
    if      (recipient.isGroup())       setGroupRecipientTitle(recipient);
    else if (recipient.isLocalNumber()) setSelfTitle();
    else                                setIndividualRecipientTitle(recipient);
  }

  @SuppressLint("SetTextI18n")
  private void setNonContactRecipientTitle(Recipient recipient) {
    this.title.setText(Util.getFirstNonEmpty(recipient.getE164().orNull(), recipient.getUuid().transform(UUID::toString).orNull()));

    if (recipient.getProfileName().isEmpty()) {
      this.subtitle.setText(null);
    } else {
      this.subtitle.setText("~" + recipient.getProfileName().toString());
    }

    updateSubtitleVisibility();
  }

  private void setGroupRecipientTitle(Recipient recipient) {
    this.title.setText(recipient.getDisplayName(getContext()));
    this.subtitle.setText(Stream.of(recipient.getParticipants())
                                .sorted((a, b) -> Boolean.compare(a.isLocalNumber(), b.isLocalNumber()))
                                .map(r -> r.isLocalNumber() ? getResources().getString(R.string.ConversationTitleView_you)
                                                            : r.getDisplayName(getContext()))
                                .collect(Collectors.joining(", ")));

    updateSubtitleVisibility();
  }

  private void setSelfTitle() {
    this.title.setText(R.string.note_to_self);
    this.subtitleContainer.setVisibility(View.GONE);
  }

  private void setIndividualRecipientTitle(Recipient recipient) {
    final String displayName = recipient.getDisplayName(getContext());
    this.title.setText(displayName);
    this.subtitle.setText(null);
    updateVerifiedSubtitleVisibility();
  }

  private void updateVerifiedSubtitleVisibility() {
    verifiedSubtitle.setVisibility(subtitle.getVisibility() != VISIBLE && verified.getVisibility() == VISIBLE ? VISIBLE : GONE);
  }

  private void updateSubtitleVisibility() {
    subtitle.setVisibility(expirationBadgeContainer.getVisibility() != VISIBLE && !TextUtils.isEmpty(subtitle.getText()) ? VISIBLE : GONE);
    updateVerifiedSubtitleVisibility();
  }
}
