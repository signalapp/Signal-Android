package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.provider.ContactsContract;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

import com.bumptech.glide.load.engine.DiskCacheStrategy;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.contacts.avatars.ContactColors;
import org.thoughtcrime.securesms.contacts.avatars.ContactPhoto;
import org.thoughtcrime.securesms.contacts.avatars.ResourceContactPhoto;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientExporter;
import org.thoughtcrime.securesms.util.ThemeUtil;

import java.util.Objects;

public final class AvatarImageView extends AppCompatImageView {

  private static final int SIZE_LARGE = 1;
  private static final int SIZE_SMALL = 2;

  @SuppressWarnings("unused")
  private static final String TAG = AvatarImageView.class.getSimpleName();

  private static final Paint LIGHT_THEME_OUTLINE_PAINT = new Paint();
  private static final Paint DARK_THEME_OUTLINE_PAINT  = new Paint();

  static {
    LIGHT_THEME_OUTLINE_PAINT.setColor(Color.argb((int) (255 * 0.2), 0, 0, 0));
    LIGHT_THEME_OUTLINE_PAINT.setStyle(Paint.Style.STROKE);
    LIGHT_THEME_OUTLINE_PAINT.setStrokeWidth(1f);
    LIGHT_THEME_OUTLINE_PAINT.setAntiAlias(true);

    DARK_THEME_OUTLINE_PAINT.setColor(Color.argb((int) (255 * 0.2), 255, 255, 255));
    DARK_THEME_OUTLINE_PAINT.setStyle(Paint.Style.STROKE);
    DARK_THEME_OUTLINE_PAINT.setStrokeWidth(1f);
    DARK_THEME_OUTLINE_PAINT.setAntiAlias(true);
  }

  private int             size;
  private boolean         inverted;
  private Paint           outlinePaint;
  private OnClickListener listener;

  private @Nullable RecipientContactPhoto recipientContactPhoto;
  private @NonNull  Drawable              unknownRecipientDrawable;

  public AvatarImageView(Context context) {
    super(context);
    initialize(context, null);
  }

  public AvatarImageView(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize(context, attrs);
  }

  private void initialize(@NonNull Context context, @Nullable AttributeSet attrs) {
    setScaleType(ScaleType.CENTER_CROP);

    if (attrs != null) {
      TypedArray typedArray = context.getTheme().obtainStyledAttributes(attrs, R.styleable.AvatarImageView, 0, 0);
      inverted              = typedArray.getBoolean(R.styleable.AvatarImageView_inverted, false);
      size                  = typedArray.getInt(R.styleable.AvatarImageView_fallbackImageSize, SIZE_LARGE);
      typedArray.recycle();
    }

    outlinePaint = ThemeUtil.isDarkTheme(getContext()) ? DARK_THEME_OUTLINE_PAINT : LIGHT_THEME_OUTLINE_PAINT;

    unknownRecipientDrawable = new ResourceContactPhoto(R.drawable.ic_profile_outline_40, R.drawable.ic_profile_outline_20).asDrawable(getContext(), ContactColors.UNKNOWN_COLOR.toConversationColor(getContext()), inverted);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);

    float width  = getWidth()  - getPaddingRight()  - getPaddingLeft();
    float height = getHeight() - getPaddingBottom() - getPaddingTop();
    float cx     = width  / 2f;
    float cy     = height / 2f;
    float radius = Math.min(cx, cy) - (outlinePaint.getStrokeWidth() / 2f);

    canvas.translate(getPaddingLeft(), getPaddingTop());
    canvas.drawCircle(cx, cy, radius, outlinePaint);
  }

  @Override
  public void setOnClickListener(OnClickListener listener) {
    this.listener = listener;
    super.setOnClickListener(listener);
  }

  public void setAvatar(@NonNull GlideRequests requestManager, @Nullable Recipient recipient, boolean quickContactEnabled) {
    if (recipient != null) {
      RecipientContactPhoto photo = new RecipientContactPhoto(recipient);

      if (!photo.equals(recipientContactPhoto)) {
        requestManager.clear(this);
        recipientContactPhoto = photo;

        Drawable fallbackContactPhotoDrawable = size == SIZE_SMALL
            ? photo.recipient.getSmallFallbackContactPhotoDrawable(getContext(), inverted)
            : photo.recipient.getFallbackContactPhotoDrawable(getContext(), inverted);

        if (photo.contactPhoto != null) {
          requestManager.load(photo.contactPhoto)
                        .fallback(fallbackContactPhotoDrawable)
                        .error(fallbackContactPhotoDrawable)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .circleCrop()
                        .into(this);
        } else {
          setImageDrawable(fallbackContactPhotoDrawable);
        }
      }

      setAvatarClickHandler(recipient, quickContactEnabled);
    } else {
      recipientContactPhoto = null;
      requestManager.clear(this);
      setImageDrawable(unknownRecipientDrawable);
      super.setOnClickListener(listener);
    }
  }

  private void setAvatarClickHandler(final Recipient recipient, boolean quickContactEnabled) {
    if (!recipient.isGroup() && quickContactEnabled) {
      super.setOnClickListener(v -> {
        if (recipient.getContactUri() != null) {
          ContactsContract.QuickContact.showQuickContact(getContext(), AvatarImageView.this, recipient.getContactUri(), ContactsContract.QuickContact.MODE_LARGE, null);
        } else {
          getContext().startActivity(RecipientExporter.export(recipient).asAddContactIntent());
        }
      });
    } else {
      super.setOnClickListener(listener);
    }
  }

  private static class RecipientContactPhoto {

    private final @NonNull  Recipient    recipient;
    private final @Nullable ContactPhoto contactPhoto;
    private final           boolean      ready;

    RecipientContactPhoto(@NonNull Recipient recipient) {
      this.recipient    = recipient;
      this.ready        = !recipient.isResolving();
      this.contactPhoto = recipient.getContactPhoto();
    }

    public boolean equals(@Nullable RecipientContactPhoto other) {
      if (other == null) return false;

      return other.recipient.equals(recipient) &&
             other.recipient.getColor().equals(recipient.getColor()) &&
             other.ready == ready &&
             Objects.equals(other.contactPhoto, contactPhoto);
    }
  }
}
