package org.thoughtcrime.securesms.profiles.spoofing;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.avatar.fallback.FallbackAvatar;
import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.databinding.ReviewBannerViewBinding;
import org.thoughtcrime.securesms.recipients.Recipient;

/**
 * Banner displayed within a conversation when a review is suggested.
 */
public class ReviewBannerView extends FrameLayout {

  private ReviewBannerViewBinding binding;
  private OnHideListener          onHideListener;

  public ReviewBannerView(@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
  }

  public ReviewBannerView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();

    binding = ReviewBannerViewBinding.bind(this);

    FallbackAvatarProvider provider = new FallbackAvatarProvider();

    binding.bannerBottomRightAvatar.setFallbackAvatarProvider(provider);
    binding.bannerTopLeftAvatar.setFallbackAvatarProvider(provider);

    binding.bannerClose.setOnClickListener(v -> {
      if (onHideListener != null && onHideListener.onHide()) {
        return;
      }

      setVisibility(GONE);
    });
  }

  public void setOnHideListener(@Nullable OnHideListener onHideListener) {
    this.onHideListener = onHideListener;
  }

  public void setBannerMessage(@Nullable CharSequence charSequence) {
    binding.bannerMessage.setText(charSequence);
  }

  public void setBannerIcon(@Nullable Drawable icon) {
    binding.bannerIcon.setImageDrawable(icon);

    binding.bannerIcon.setVisibility(VISIBLE);
    binding.bannerTopLeftAvatar.setVisibility(GONE);
    binding.bannerBottomRightAvatar.setVisibility(GONE);
    binding.bannerAvatarStroke.setVisibility(GONE);
  }

  public void setBannerRecipients(@NonNull Recipient target, @NonNull Recipient dupe) {
    binding.bannerTopLeftAvatar.setAvatar(target);
    binding.bannerBottomRightAvatar.setAvatar(dupe);

    binding.bannerIcon.setVisibility(GONE);
    binding.bannerTopLeftAvatar.setVisibility(VISIBLE);
    binding.bannerBottomRightAvatar.setVisibility(VISIBLE);
    binding.bannerAvatarStroke.setVisibility(VISIBLE);
  }

  @Override
  public void setOnClickListener(@Nullable OnClickListener l) {
    super.setOnClickListener(l);
    binding.bannerTapToReview.setOnClickListener(l);
  }

  private static final class FallbackAvatarProvider implements AvatarImageView.FallbackAvatarProvider {
    @Override
    public @NonNull FallbackAvatar getFallbackAvatar(@NonNull Recipient recipient) {
      if (recipient.isIndividual() && !recipient.isSelf()) {
        return new FallbackAvatar.Resource.Person(recipient.getAvatarColor());
      }

      return recipient.getFallbackAvatar();
    }
  }

  public interface OnHideListener {
    boolean onHide();
  }
}
