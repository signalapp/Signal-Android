package org.thoughtcrime.securesms.conversation;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewKt;

import com.bumptech.glide.RequestManager;

import org.signal.core.util.DimensionUnit;
import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.emoji.EmojiTextView;
import org.thoughtcrime.securesms.conversation.colors.AvatarGradientColors;
import org.thoughtcrime.securesms.conversation.v2.data.AvatarDownloadStateCache;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.databinding.ConversationHeaderViewBinding;
import org.thoughtcrime.securesms.fonts.SignalSymbols;
import org.thoughtcrime.securesms.jobs.AvatarGroupsV2DownloadJob;
import org.thoughtcrime.securesms.jobs.RetrieveProfileAvatarJob;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.ContextUtil;
import org.thoughtcrime.securesms.util.LongClickMovementMethod;
import org.thoughtcrime.securesms.util.SpanUtil;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.whispersystems.signalservice.api.util.Preconditions;

public class ConversationHeaderView extends ConstraintLayout {

  private static final String TAG           = Log.tag(ConversationHeaderView.class);
  private static final int    FADE_DURATION = 150;
  private static final int    LOADING_DELAY = 800;

  private final ConversationHeaderViewBinding binding;

  private boolean inProgress = false;
  private Handler handler    = new Handler();

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

  public void showProgressBar(@NonNull Recipient recipient) {
    if (!inProgress) {
      inProgress = true;
      animateAvatarLoading(recipient);
      binding.messageRequestAvatarTapToView.setVisibility(GONE);
      binding.messageRequestAvatarTapToView.setOnClickListener(null);
      handler.postDelayed(() -> {
        boolean isDownloading = AvatarDownloadStateCache.getDownloadState(recipient) == AvatarDownloadStateCache.DownloadState.IN_PROGRESS;
        binding.progressBar.setVisibility(isDownloading ? View.VISIBLE : View.GONE);
      }, LOADING_DELAY);
    }
  }

  public void hideProgressBar() {
    inProgress = false;
    binding.progressBar.setVisibility(View.GONE);
  }

  public void showFailedAvatarDownload(@NonNull Recipient recipient) {
    AvatarDownloadStateCache.set(recipient, AvatarDownloadStateCache.DownloadState.NONE);
    binding.progressBar.setVisibility(View.GONE);
    binding.messageRequestAvatar.setImageDrawable(AvatarGradientColors.getGradientDrawable(recipient));
  }

  public void setBadge(@Nullable Recipient recipient) {
    if (recipient == null || recipient.isSelf()) {
      binding.messageRequestBadge.setBadge(null);
    } else {
      binding.messageRequestBadge.setBadgeFromRecipient(recipient);
    }
  }

  public void setAvatar(@NonNull RequestManager requestManager, @Nullable Recipient recipient) {
    if (recipient == null) {
      return;
    }

    if (AvatarDownloadStateCache.getDownloadState(recipient) != AvatarDownloadStateCache.DownloadState.IN_PROGRESS) {
      binding.messageRequestAvatar.setAvatar(requestManager, recipient, false, false, true);
      hideProgressBar();
    }

    if (recipient.getShouldBlurAvatar() && recipient.getHasAvatar()) {
      binding.messageRequestAvatarTapToView.setVisibility(VISIBLE);
      binding.messageRequestAvatarTapToView.setOnClickListener(v -> {
        AvatarDownloadStateCache.set(recipient, AvatarDownloadStateCache.DownloadState.IN_PROGRESS);
        SignalExecutors.BOUNDED.execute(() -> SignalDatabase.recipients().manuallyUpdateShowAvatar(recipient.getId(), true));
        if (recipient.isPushV2Group()) {
          AvatarGroupsV2DownloadJob.enqueueUnblurredAvatar(recipient.requireGroupId().requireV2());
        } else {
          RetrieveProfileAvatarJob.enqueueUnblurredAvatar(recipient);
        }
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
      CharSequence chevronRight = SignalSymbols.getSpannedString(getContext(), SignalSymbols.Weight.BOLD, SignalSymbols.Glyph.CHEVRON_RIGHT, R.color.signal_colorOutline);
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
    updateOutlineVisibility();
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

  public void setUnverifiedNameSubtitle(@DrawableRes int iconRes, boolean forGroup, @NonNull Runnable onClick) {
    binding.messageRequestProfileNameUnverified.setVisibility(View.VISIBLE);
    binding.messageRequestProfileNameUnverified.setOnClickListener(view -> onClick.run());

    String substring  = forGroup ? getContext().getString(R.string.ConversationFragment_group_names)
                                 : getContext().getString(R.string.ConversationFragment_profile_names);

    String fullString = forGroup ? getContext().getString(R.string.ConversationFragment_group_names_not_verified, substring)
                                 : getContext().getString(R.string.ConversationFragment_profile_names_not_verified, substring);

    CharSequence builder = SpanUtil.underlineSubstring(fullString, substring);
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

  private void animateAvatarLoading(@NonNull Recipient recipient) {
    Drawable loadingProfile = AppCompatResources.getDrawable(getContext(), R.drawable.circle_profile_photo);
    ObjectAnimator animator = ObjectAnimator.ofFloat(binding.messageRequestAvatar, "alpha", 1f, 0f).setDuration(FADE_DURATION);
    animator.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd(Animator animation) {
        if (AvatarDownloadStateCache.getDownloadState(recipient) == AvatarDownloadStateCache.DownloadState.IN_PROGRESS) {
          binding.messageRequestAvatar.setImageDrawable(loadingProfile);
        }
        ObjectAnimator.ofFloat(binding.messageRequestAvatar, "alpha", 0f, 1f).setDuration(FADE_DURATION).start();
      }
    });

    animator.start();
  }

  private void updateOutlineVisibility() {
    if (ViewKt.isVisible(binding.messageRequestSubtitle) || ViewKt.isVisible(binding.messageRequestDescription)) {
      if (getBackground() != null) {
        binding.messageRequestInfoOutline.setVisibility(View.GONE);
        binding.messageRequestDivider.setVisibility(View.VISIBLE);
      } else {
        binding.messageRequestInfoOutline.setVisibility(View.VISIBLE);
        binding.messageRequestDivider.setVisibility(View.GONE);
      }
    } else {
      binding.messageRequestInfoOutline.setVisibility(View.GONE);
      binding.messageRequestDivider.setVisibility(View.GONE);
    }
  }

  public void updateOutlineBoxSize() {
    int visibleCount = 0;
    for (int i = 0; i < binding.messageRequestInfo.getChildCount(); i++) {
      if (ViewKt.isVisible(binding.messageRequestInfo.getChildAt(i))) {
        visibleCount++;
      }
    }

    int padding = visibleCount == 1 ? getContext().getResources().getDimensionPixelOffset(R.dimen.conversation_header_padding) : getContext().getResources().getDimensionPixelOffset(R.dimen.conversation_header_padding_expanded);
    ViewUtil.setPaddingStart(binding.messageRequestInfo, padding);
    ViewUtil.setPaddingEnd(binding.messageRequestInfo, padding);
  }

  private @NonNull CharSequence prependIcon(@NonNull CharSequence input, @DrawableRes int iconRes) {
    return prependIcon(input, iconRes, false);
  }


  private @NonNull CharSequence prependIcon(@NonNull CharSequence input, @DrawableRes int iconRes, boolean useIntrinsicWidth) {
    Drawable drawable = ContextCompat.getDrawable(getContext(), iconRes);
    Preconditions.checkNotNull(drawable);
    int width = useIntrinsicWidth ? drawable.getIntrinsicWidth() : (int) DimensionUnit.SP.toPixels(16);
    drawable.setBounds(0, 0, width, (int) DimensionUnit.SP.toPixels(16));
    drawable.setColorFilter(ContextCompat.getColor(getContext(), R.color.signal_colorOnSurface), PorterDuff.Mode.SRC_ATOP);

    return new SpannableStringBuilder()
        .append(SpanUtil.buildCenteredImageSpan(drawable))
        .append(SpanUtil.space(8, DimensionUnit.SP))
        .append(input);
  }
}
