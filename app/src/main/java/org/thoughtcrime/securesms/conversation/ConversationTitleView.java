package org.thoughtcrime.securesms.conversation;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.RequestManager;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.avatar.view.AvatarView;
import org.thoughtcrime.securesms.badges.BadgeImageView;
import org.thoughtcrime.securesms.database.model.StoryViewState;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.ContextUtil;
import org.thoughtcrime.securesms.util.DrawableUtil;
import org.thoughtcrime.securesms.util.ExpirationUtil;
import org.thoughtcrime.securesms.util.ViewUtil;

public class ConversationTitleView extends ConstraintLayout {

  private static final String STATE_ROOT = "root";
  private static final String STATE_IS_SELF = "is_self";

  private AvatarView      avatar;
  private BadgeImageView  badge;
  private TextView        title;
  private TextView        subtitle;
  private ImageView       verified;
  private View            subtitleContainer;
  private View            verifiedSubtitle;
  private View            expirationBadgeContainer;
  private TextView        expirationBadgeTime;
  private boolean         isSelf;

  public ConversationTitleView(Context context) {
    this(context, null);
  }

  public ConversationTitleView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  public void onFinishInflate() {
    super.onFinishInflate();

    this.title                    = findViewById(R.id.title);
    this.badge                    = findViewById(R.id.badge);
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

  @Override
  protected @NonNull Parcelable onSaveInstanceState() {
    Bundle bundle = new Bundle();

    bundle.putParcelable(STATE_ROOT, super.onSaveInstanceState());
    bundle.putBoolean(STATE_IS_SELF, isSelf);

    return bundle;
  }

  @Override
  protected void onRestoreInstanceState(Parcelable state) {
    if (state instanceof Bundle) {
      Parcelable rootState = ((Bundle) state).getParcelable(STATE_ROOT);
      super.onRestoreInstanceState(rootState);

      isSelf = ((Bundle) state).getBoolean(STATE_IS_SELF, false);
    } else {
      super.onRestoreInstanceState(state);
    }
  }

  public void showExpiring(@NonNull Recipient recipient) {
    isSelf = recipient.isSelf();

    expirationBadgeTime.setText(ExpirationUtil.getExpirationAbbreviatedDisplayValue(getContext(), recipient.getExpiresInSeconds()));
    expirationBadgeContainer.setVisibility(View.VISIBLE);
    updateSubtitleVisibility();
  }

  public void clearExpiring() {
    expirationBadgeContainer.setVisibility(View.GONE);
    updateSubtitleVisibility();
  }

  public void setTitle(@NonNull RequestManager requestManager, @Nullable Recipient recipient) {
    isSelf = recipient != null && recipient.isSelf();

    this.subtitleContainer.setVisibility(View.VISIBLE);

    if   (recipient == null) setComposeTitle();
    else                     setRecipientTitle(recipient);

    Drawable startDrawable = null;
    Drawable endDrawable   = null;

    if (recipient != null && recipient.isBlocked()) {
      startDrawable = ContextUtil.requireDrawable(getContext(), R.drawable.symbol_block_16);
      startDrawable.setBounds(0, 0, ViewUtil.dpToPx(18), ViewUtil.dpToPx(18));
    } else if (recipient != null && recipient.isMuted()) {
      startDrawable = ContextUtil.requireDrawable(getContext(), R.drawable.ic_bell_disabled_16);
      startDrawable.setBounds(0, 0, ViewUtil.dpToPx(18), ViewUtil.dpToPx(18));
    }

    if (recipient != null && recipient.isSystemContact() && !recipient.isSelf()) {
      endDrawable = ContextCompat.getDrawable(getContext(), R.drawable.ic_profile_circle_outline_16);
    }

    if (startDrawable != null) {
      startDrawable = DrawableUtil.tint(startDrawable, ContextCompat.getColor(getContext(), R.color.signal_inverse_transparent_80));
    }

    if (endDrawable != null) {
      endDrawable = DrawableUtil.tint(endDrawable, ContextCompat.getColor(getContext(), R.color.signal_inverse_transparent_80));
    }

    if (recipient != null && recipient.getShowVerified()) {
      endDrawable = ContextUtil.requireDrawable(getContext(), R.drawable.ic_official_24);
    }

    title.setCompoundDrawablesRelativeWithIntrinsicBounds(startDrawable, null, endDrawable, null);

    if (recipient != null) {
      this.avatar.displayChatAvatar(requestManager, recipient, false);
    }

    if (recipient == null || recipient.isSelf()) {
      badge.setBadgeFromRecipient(null);
    } else {
      badge.setBadgeFromRecipient(recipient);
    }

    updateVerifiedSubtitleVisibility();
  }

  public void setStoryRingFromState(@NonNull StoryViewState storyViewState) {
    avatar.setStoryRingFromState(storyViewState);
  }

  public void setOnStoryRingClickListener(@NonNull OnClickListener onStoryRingClickListener) {
    avatar.setOnClickListener(v -> {
      if (avatar.hasStory()) {
        onStoryRingClickListener.onClick(v);
      } else {
        performClick();
      }
    });
  }

  public void setVerified(boolean verified) {
    this.verified.setVisibility(verified ? View.VISIBLE : View.GONE);

    updateVerifiedSubtitleVisibility();
  }

  public void setGroupRecipientSubtitle(@Nullable String members) {
    this.subtitle.setText(members);
    updateSubtitleVisibility();
  }

  private void setComposeTitle() {
    this.title.setText(R.string.ConversationActivity_compose_message);
    this.subtitle.setText(null);
    updateSubtitleVisibility();
  }

  private void setRecipientTitle(@NonNull Recipient recipient) {
    if      (recipient.isGroup()) setGroupRecipientTitle(recipient);
    else if (recipient.isSelf())  setSelfTitle();
    else                          setIndividualRecipientTitle(recipient);
  }

  private void setGroupRecipientTitle(@NonNull Recipient recipient) {
    this.title.setText(recipient.getDisplayName(getContext()));
  }

  private void setSelfTitle() {
    this.title.setText(R.string.note_to_self);
    updateSubtitleVisibility();
  }

  private void setIndividualRecipientTitle(@NonNull Recipient recipient) {
    final String displayName = recipient.getDisplayName(getContext());
    this.title.setText(displayName);
    this.subtitle.setText(null);
    updateSubtitleVisibility();
  }

  private void updateVerifiedSubtitleVisibility() {
    verifiedSubtitle.setVisibility(!isSelf && subtitle.getVisibility() != VISIBLE && verified.getVisibility() == VISIBLE ? VISIBLE : GONE);
  }

  private void updateSubtitleVisibility() {
    subtitle.setVisibility(!isSelf && expirationBadgeContainer.getVisibility() != VISIBLE && !TextUtils.isEmpty(subtitle.getText()) ? VISIBLE : GONE);
    updateVerifiedSubtitleVisibility();
  }
}
