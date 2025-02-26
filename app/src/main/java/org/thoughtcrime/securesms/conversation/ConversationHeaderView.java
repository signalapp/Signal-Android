package org.thoughtcrime.securesms.conversation;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewKt;

import com.bumptech.glide.RequestManager;

import org.signal.core.util.DimensionUnit;
import org.signal.core.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.emoji.EmojiTextView;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.databinding.ConversationHeaderViewBinding;
import org.thoughtcrime.securesms.fonts.SignalSymbols;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.ContextUtil;
import org.thoughtcrime.securesms.util.LongClickMovementMethod;
import org.thoughtcrime.securesms.util.SpanUtil;
import org.whispersystems.signalservice.api.util.Preconditions;

public class ConversationHeaderView extends ConstraintLayout {

  private final ConversationHeaderViewBinding binding;

  public ConversationHeaderView(Context context) {
    this(context, null);
  }

  public ConversationHeaderView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public ConversationHeaderView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);

    inflate(getContext(), R.layout.conversation_header_view, this);

    binding = ConversationHeaderViewBinding.bind(this);
  }

  public void setBadge(@Nullable Recipient recipient) {
    if (recipient == null || recipient.isSelf()) {
      binding.messageRequestBadge.setBadge(null);
    } else {
      binding.messageRequestBadge.setBadgeFromRecipient(recipient);
    }
  }

  public void setAvatar(@NonNull RequestManager requestManager, @Nullable Recipient recipient) {
    binding.messageRequestAvatar.setAvatar(requestManager, recipient, false);

    if (recipient != null && recipient.getShouldBlurAvatar() && recipient.getContactPhoto() != null) {
      binding.messageRequestAvatarTapToView.setVisibility(VISIBLE);
      binding.messageRequestAvatarTapToView.setOnClickListener(v -> {
        SignalExecutors.BOUNDED.execute(() -> SignalDatabase.recipients().manuallyShowAvatar(recipient.getId()));
      });
    } else {
      binding.messageRequestAvatarTapToView.setVisibility(GONE);
      binding.messageRequestAvatarTapToView.setOnClickListener(null);
    }
  }

  public String setTitle(@NonNull Recipient recipient, @NonNull Runnable onTitleClicked) {
    SpannableStringBuilder title = new SpannableStringBuilder(recipient.isSelf() ? getContext().getString(R.string.note_to_self) : recipient.getDisplayName(getContext()));
    if (recipient.getShowVerified()) {
      SpanUtil.appendCenteredImageSpan(title, ContextUtil.requireDrawable(getContext(), R.drawable.ic_official_28), 28, 28);
    }

    if (recipient.isIndividual() && !recipient.isSelf()) {
      CharSequence chevronRight = SignalSymbols.getSpannedString(getContext(), SignalSymbols.Weight.BOLD, SignalSymbols.Glyph.CHEVRON_RIGHT);
      title.append(" ");
      title.append(SpanUtil.ofSize(chevronRight, 24));

      binding.messageRequestTitle.setOnClickListener(v -> onTitleClicked.run());
    } else {
      binding.messageRequestTitle.setOnClickListener(null);
    }

    binding.messageRequestTitle.setText(title);
    return title.toString();
  }

  public void showReleaseNoteHeader() {
    binding.messageRequestInfo.setVisibility(View.GONE);
    binding.releaseHeaderContainer.setVisibility(View.VISIBLE);
    binding.releaseHeaderDescription1.setText(prependIcon(getContext().getString(R.string.ReleaseNotes__this_is_official_chat_period), R.drawable.symbol_official_20));
    binding.releaseHeaderDescription2.setText(prependIcon(getContext().getString(R.string.ReleaseNotes__keep_up_to_date_period), R.drawable.symbol_bell_20));
  }

  public void setAbout(@NonNull Recipient recipient) {
    String about = recipient.getCombinedAboutAndEmoji();
    binding.messageRequestAbout.setText(about);
    binding.messageRequestAbout.setVisibility(TextUtils.isEmpty(about) || recipient.isReleaseNotes() ? GONE : VISIBLE);
  }

  public void setSubtitle(@NonNull CharSequence subtitle, @DrawableRes int iconRes, @Nullable Runnable onClick) {
    if (TextUtils.isEmpty(subtitle)) {
      hideSubtitle();
      return;
    }

    if (onClick != null) {
      binding.messageRequestSubtitle.setMovementMethod(LinkMovementMethod.getInstance());
      CharSequence builder = SpanUtil.clickSubstring(
          subtitle,
          subtitle,
          listener -> onClick.run(),
          ContextCompat.getColor(getContext(), R.color.signal_colorOnSurface),
          true
      );
      binding.messageRequestSubtitle.setText(prependIcon(builder, iconRes));
    } else {
      binding.messageRequestSubtitle.setText(prependIcon(subtitle, iconRes));
    }

    binding.messageRequestSubtitle.setVisibility(View.VISIBLE);
  }

  public void setDescription(@Nullable CharSequence description, @DrawableRes int iconRes) {
    if (TextUtils.isEmpty(description)) {
      hideDescription();
      return;
    }

    binding.messageRequestDescription.setText(prependIcon(description, iconRes));
    binding.messageRequestDescription.setVisibility(View.VISIBLE);
  }

  public @NonNull EmojiTextView getDescription() {
    return binding.messageRequestDescription;
  }

  public void setButton(@NonNull CharSequence button, Runnable onClick) {
    binding.messageRequestButton.setText(button);
    binding.messageRequestButton.setOnClickListener(v -> onClick.run());
    binding.messageRequestButton.setVisibility(View.VISIBLE);
  }

  public void showWarningSubtitle() {
    binding.messageRequestReviewCarefully.setVisibility(View.VISIBLE);
  }

  public void hideWarningSubtitle() {
    binding.messageRequestReviewCarefully.setVisibility(View.GONE);
  }

  public void setUnverifiedNameSubtitle(@DrawableRes int iconRes, @StringRes int clickableRes, boolean forGroup, @Nullable Runnable onClick) {
    binding.messageRequestProfileNameUnverified.setVisibility(View.VISIBLE);
    binding.messageRequestProfileNameUnverified.setMovementMethod(LinkMovementMethod.getInstance());
    CharSequence builder = SpanUtil.clickSubstring(
        getContext(),
        forGroup ? R.string.ConversationFragment_group_names_not_verified : R.string.ConversationFragment_profile_names_not_verified,
        clickableRes,
        listener -> onClick.run(),
        true,
        R.color.signal_colorOnSurface
    );
    binding.messageRequestProfileNameUnverified.setText(prependIcon(builder, iconRes, forGroup));
  }

  public void hideUnverifiedNameSubtitle() {
    binding.messageRequestProfileNameUnverified.setVisibility(View.GONE);
  }

  public void showBackgroundBubble(boolean enabled) {
    if (enabled) {
      setBackgroundResource(R.drawable.wallpaper_bubble_background_18);
    } else {
      setBackground(null);
    }

    updateOutlineVisibility();
  }

  public void hideSubtitle() {
    binding.messageRequestSubtitle.setVisibility(View.GONE);
    updateOutlineVisibility();
  }

  public void showDescription() {
    binding.messageRequestDescription.setVisibility(View.VISIBLE);
    updateOutlineVisibility();
  }

  public void hideDescription() {
    binding.messageRequestDescription.setVisibility(View.GONE);
    updateOutlineVisibility();
  }

  public void hideButton() {
    binding.messageRequestButton.setVisibility(View.GONE);
  }

  public void setLinkifyDescription(boolean enable) {
    binding.messageRequestDescription.setMovementMethod(enable ? LongClickMovementMethod.getInstance(getContext()) : null);
  }

  private void updateOutlineVisibility() {
    if (ViewKt.isVisible(binding.messageRequestSubtitle) || ViewKt.isVisible(binding.messageRequestDescription)) {
      if (getBackground() != null) {
        binding.messageRequestInfoOutline.setVisibility(View.GONE);
        binding.messageRequestDivider.setVisibility(View.VISIBLE);
      } else {
        binding.messageRequestInfoOutline.setVisibility(View.VISIBLE);
        binding.messageRequestDivider.setVisibility(View.INVISIBLE);
      }
    } else if (ViewKt.isVisible(binding.releaseHeaderContainer)) {
      binding.messageRequestInfoOutline.setVisibility(View.GONE);
      binding.messageRequestDivider.setVisibility(View.INVISIBLE);
    } else {
      binding.messageRequestInfoOutline.setVisibility(View.GONE);
      binding.messageRequestDivider.setVisibility(View.GONE);
    }
  }

  private @NonNull CharSequence prependIcon(@NonNull CharSequence input, @DrawableRes int iconRes) {
    return prependIcon(input, iconRes, false);
  }


  private @NonNull CharSequence prependIcon(@NonNull CharSequence input, @DrawableRes int iconRes, boolean useIntrinsicWidth) {
    Drawable drawable = ContextCompat.getDrawable(getContext(), iconRes);
    Preconditions.checkNotNull(drawable);
    int width = useIntrinsicWidth ? drawable.getIntrinsicWidth() : (int) DimensionUnit.SP.toPixels(20);
    drawable.setBounds(0, 0, width, (int) DimensionUnit.SP.toPixels(20));
    drawable.setColorFilter(ContextCompat.getColor(getContext(), R.color.signal_colorOnSurface), PorterDuff.Mode.SRC_ATOP);

    return new SpannableStringBuilder()
        .append(SpanUtil.buildCenteredImageSpan(drawable))
        .append(SpanUtil.space(8, DimensionUnit.SP))
        .append(input);
  }
}
