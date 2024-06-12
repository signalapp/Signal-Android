package org.thoughtcrime.securesms.contacts;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.chip.Chip;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.RelativeCornerSize;
import com.google.android.material.shape.RoundedCornerTreatment;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.google.android.material.shape.Shapeable;

import org.thoughtcrime.securesms.avatar.fallback.FallbackAvatarDrawable;
import org.thoughtcrime.securesms.contacts.avatars.ContactPhoto;
import org.thoughtcrime.securesms.recipients.Recipient;

public final class ContactChip extends Chip {

  @Nullable private SelectedContact contact;

  public ContactChip(Context context) {
    super(context);
  }

  public ContactChip(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public ContactChip(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  public void setContact(@NonNull SelectedContact contact) {
    this.contact = contact;
  }

  public @Nullable SelectedContact getContact() {
    return contact;
  }

  public void setAvatar(@NonNull RequestManager requestManager, @Nullable Recipient recipient, @Nullable Runnable onAvatarSet) {
    if (recipient != null) {
      requestManager.clear(this);

      FallbackAvatarDrawable fallbackContactPhotoDrawable = new FallbackAvatarDrawable(getContext(), recipient.getFallbackAvatar());
      ContactPhoto           contactPhoto                 = recipient.getContactPhoto();

      if (contactPhoto == null) {
        fallbackContactPhotoDrawable.setShapeAppearanceModel(
            ShapeAppearanceModel.builder()
                .setAllCorners(new RoundedCornerTreatment())
                .setAllCornerSizes(new RelativeCornerSize(0.5f)).build()
        );

        setChipIcon(fallbackContactPhotoDrawable);
        if (onAvatarSet != null) {
          onAvatarSet.run();
        }
      } else {
        requestManager.load(contactPhoto)
                      .placeholder(fallbackContactPhotoDrawable)
                      .fallback(fallbackContactPhotoDrawable)
                      .error(fallbackContactPhotoDrawable)
                      .circleCrop()
                      .diskCacheStrategy(DiskCacheStrategy.ALL)
                      .into(new CustomTarget<Drawable>() {
                        @Override
                        public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                          setChipIcon(resource);
                          if (onAvatarSet != null) {
                            onAvatarSet.run();
                          }
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {
                          setChipIcon(placeholder);
                        }
                      });
      }
    }
  }

  private static class HalfScaleDrawable extends Drawable {

    private final Drawable fallbackContactPhotoDrawable;

    HalfScaleDrawable(Drawable fallbackContactPhotoDrawable) {
      this.fallbackContactPhotoDrawable = fallbackContactPhotoDrawable;
    }

    @Override
    public void setBounds(int left, int top, int right, int bottom) {
      super.setBounds(left, top, right, bottom);
      fallbackContactPhotoDrawable.setBounds(left, top, 2 * right - left, 2 * bottom - top);
    }

    @Override
    public void setBounds(@NonNull Rect bounds) {
      super.setBounds(bounds);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
      canvas.save();
      canvas.scale(0.5f, 0.5f);
      fallbackContactPhotoDrawable.draw(canvas);
      canvas.restore();
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
      return PixelFormat.OPAQUE;
    }
  }
}
