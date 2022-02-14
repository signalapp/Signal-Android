package org.thoughtcrime.securesms.conversation;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

import org.signal.core.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.badges.BadgeImageView;
import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.components.emoji.EmojiTextView;
import org.thoughtcrime.securesms.contacts.avatars.FallbackContactPhoto;
import org.thoughtcrime.securesms.contacts.avatars.ResourceContactPhoto;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.ContextUtil;
import org.thoughtcrime.securesms.util.LongClickMovementMethod;
import org.thoughtcrime.securesms.util.SpanUtil;

public class ConversationBannerView extends ConstraintLayout {

  private AvatarImageView contactAvatar;
  private TextView        contactTitle;
  private TextView        contactAbout;
  private TextView        contactSubtitle;
  private EmojiTextView   contactDescription;
  private View            tapToView;
  private BadgeImageView  contactBadge;

  public ConversationBannerView(Context context) {
    this(context, null);
  }

  public ConversationBannerView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public ConversationBannerView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);

    inflate(getContext(), R.layout.conversation_banner_view, this);

    contactAvatar      = findViewById(R.id.message_request_avatar);
    contactBadge       = findViewById(R.id.message_request_badge);
    contactTitle       = findViewById(R.id.message_request_title);
    contactAbout       = findViewById(R.id.message_request_about);
    contactSubtitle    = findViewById(R.id.message_request_subtitle);
    contactDescription = findViewById(R.id.message_request_description);
    tapToView          = findViewById(R.id.message_request_avatar_tap_to_view);

    contactAvatar.setFallbackPhotoProvider(new FallbackPhotoProvider());
  }

  public void setBadge(@Nullable Recipient recipient) {
    if (recipient == null || recipient.isSelf()) {
      contactBadge.setBadge(null);
    } else {
      contactBadge.setBadgeFromRecipient(recipient);
    }
  }

  public void setAvatar(@NonNull GlideRequests requests, @Nullable Recipient recipient) {
    contactAvatar.setAvatar(requests, recipient, false);

    if (recipient != null && recipient.shouldBlurAvatar() && recipient.getContactPhoto() != null) {
      tapToView.setVisibility(VISIBLE);
      tapToView.setOnClickListener(v -> {
        SignalExecutors.BOUNDED.execute(() -> SignalDatabase.recipients().manuallyShowAvatar(recipient.getId()));
      });
    } else {
      tapToView.setVisibility(GONE);
      tapToView.setOnClickListener(null);
    }
  }

  public String setTitle(@NonNull Recipient recipient) {
    SpannableStringBuilder title = new SpannableStringBuilder(recipient.isSelf() ? getContext().getString(R.string.note_to_self) : recipient.getDisplayNameOrUsername(getContext()));
    if (recipient.isReleaseNotes()) {
      SpanUtil.appendCenteredImageSpan(title, ContextUtil.requireDrawable(getContext(), R.drawable.ic_official_28), 28, 28);
    }
    contactTitle.setText(title);
    return title.toString();
  }

  public void setAbout(@NonNull Recipient recipient) {
    String about;
    if (recipient.isReleaseNotes()) {
      about = getContext().getString(R.string.ReleaseNotes__signal_release_notes_and_news);
    } else {
      about = recipient.getCombinedAboutAndEmoji();
    }

    contactAbout.setText(about);
    contactAbout.setVisibility(TextUtils.isEmpty(about) ? GONE : VISIBLE);
  }

  public void setSubtitle(@Nullable CharSequence subtitle) {
    contactSubtitle.setText(subtitle);
    contactSubtitle.setVisibility(TextUtils.isEmpty(subtitle) ? GONE : VISIBLE);
  }

  public void setDescription(@Nullable CharSequence description) {
    contactDescription.setText(description);
    contactDescription.setVisibility(TextUtils.isEmpty(description) ? GONE : VISIBLE);
  }

  public @NonNull EmojiTextView getDescription() {
    return contactDescription;
  }

  public void showBackgroundBubble(boolean enabled) {
    if (enabled) {
      setBackgroundResource(R.drawable.wallpaper_bubble_background_12);
    } else {
      setBackground(null);
    }
  }

  public void hideSubtitle() {
    contactSubtitle.setVisibility(View.GONE);
  }

  public void showDescription() {
    contactDescription.setVisibility(View.VISIBLE);
  }

  public void hideDescription() {
    contactDescription.setVisibility(View.GONE);
  }

  public void setLinkifyDescription(boolean enable) {
    contactDescription.setMovementMethod(enable ? LongClickMovementMethod.getInstance(getContext()) : null);
  }

  private static final class FallbackPhotoProvider extends Recipient.FallbackPhotoProvider {
    @Override
    public @NonNull FallbackContactPhoto getPhotoForRecipientWithoutName() {
      return new ResourceContactPhoto(R.drawable.ic_profile_64);
    }

    @Override
    public @NonNull FallbackContactPhoto getPhotoForGroup() {
      return new ResourceContactPhoto(R.drawable.ic_group_64);
    }

    @Override
    public @NonNull FallbackContactPhoto getPhotoForLocalNumber() {
      return new ResourceContactPhoto(R.drawable.ic_note_64);
    }
  }
}
