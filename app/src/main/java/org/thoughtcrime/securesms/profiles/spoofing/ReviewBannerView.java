package org.thoughtcrime.securesms.profiles.spoofing;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.contacts.avatars.FallbackContactPhoto;
import org.thoughtcrime.securesms.contacts.avatars.FallbackPhoto20dp;
import org.thoughtcrime.securesms.contacts.avatars.GeneratedContactPhoto;
import org.thoughtcrime.securesms.conversation.colors.AvatarColor;
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

    FallbackPhotoProvider provider = new FallbackPhotoProvider();

    binding.bannerBottomRightAvatar.setFallbackPhotoProvider(provider);
    binding.bannerTopLeftAvatar.setFallbackPhotoProvider(provider);

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

  private static final class FallbackPhotoProvider extends Recipient.FallbackPhotoProvider {
    @Override
    public @NonNull
    FallbackContactPhoto getPhotoForGroup() {
      throw new UnsupportedOperationException("This provider does not support groups");
    }

    @Override
    public @NonNull FallbackContactPhoto getPhotoForResolvingRecipient() {
      throw new UnsupportedOperationException("This provider does not support resolving recipients");
    }

    @Override
    public @NonNull FallbackContactPhoto getPhotoForLocalNumber() {
      throw new UnsupportedOperationException("This provider does not support local number");
    }

    @NonNull
    @Override
    public FallbackContactPhoto getPhotoForRecipientWithName(String name, int targetSize) {
      return new FixedSizeGeneratedContactPhoto(name, R.drawable.ic_profile_outline_20);
    }

    @NonNull
    @Override
    public FallbackContactPhoto getPhotoForRecipientWithoutName() {
      return new FallbackPhoto20dp(R.drawable.ic_profile_outline_20);
    }
  }

  private static final class FixedSizeGeneratedContactPhoto extends GeneratedContactPhoto {
    public FixedSizeGeneratedContactPhoto(@NonNull String name, int fallbackResId) {
      super(name, fallbackResId);
    }

    @Override
    protected Drawable newFallbackDrawable(@NonNull Context context, @NonNull AvatarColor color, boolean inverted) {
      return new FallbackPhoto20dp(getFallbackResId()).asDrawable(context, color, inverted);
    }
  }

  public interface OnHideListener {
    boolean onHide();
  }
}
