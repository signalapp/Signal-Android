package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.AppCompatImageView;
import android.util.AttributeSet;
import android.view.View;

import com.bumptech.glide.load.engine.DiskCacheStrategy;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.contacts.avatars.ContactColors;
import org.thoughtcrime.securesms.contacts.avatars.GeneratedContactPhoto;
import org.thoughtcrime.securesms.contacts.avatars.ResourceContactPhoto;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.ThemeUtil;

public class AvatarImageView extends AppCompatImageView {

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

  private boolean         inverted;
  private Paint           outlinePaint;
  private OnClickListener listener;

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
      inverted = typedArray.getBoolean(0, false);
      typedArray.recycle();
    }

    outlinePaint = ThemeUtil.isDarkTheme(getContext()) ? DARK_THEME_OUTLINE_PAINT : LIGHT_THEME_OUTLINE_PAINT;
  }

  @Override
  protected void dispatchDraw(Canvas canvas) {
    super.dispatchDraw(canvas);

    float cx     = canvas.getWidth()  / 2;
    float cy     = canvas.getHeight() / 2;
    float radius = (canvas.getWidth() / 2) - (outlinePaint.getStrokeWidth() / 2);
    
    canvas.drawCircle(cx, cy, radius, outlinePaint);
  }

  @Override
  public void setOnClickListener(OnClickListener listener) {
    this.listener = listener;
    super.setOnClickListener(listener);
  }

  public void setAvatar(@NonNull GlideRequests requestManager, @Nullable Recipient recipient, boolean quickContactEnabled) {
    if (recipient != null) {
      requestManager.load(recipient.getContactPhoto())
                    .fallback(recipient.getFallbackContactPhotoDrawable(getContext(), inverted))
                    .error(recipient.getFallbackContactPhotoDrawable(getContext(), inverted))
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .circleCrop()
                    .into(this);
      setAvatarClickHandler(recipient, quickContactEnabled);
    } else {
      setImageDrawable(new ResourceContactPhoto(R.drawable.ic_profile_default).asDrawable(getContext(), ContactColors.UNKNOWN_COLOR.toConversationColor(getContext()), inverted));
      super.setOnClickListener(listener);
    }
  }

  public void clear(@NonNull GlideRequests glideRequests) {
    glideRequests.clear(this);
  }

  private void setAvatarClickHandler(final Recipient recipient, boolean quickContactEnabled) {
    if (!recipient.isGroupRecipient() && quickContactEnabled) {
      super.setOnClickListener(v -> {
        if (recipient.getContactUri() != null) {
          ContactsContract.QuickContact.showQuickContact(getContext(), AvatarImageView.this, recipient.getContactUri(), ContactsContract.QuickContact.MODE_LARGE, null);
        } else {
          final Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
          if (recipient.getAddress().isEmail()) {
            intent.putExtra(ContactsContract.Intents.Insert.EMAIL, recipient.getAddress().toEmailString());
          } else {
            intent.putExtra(ContactsContract.Intents.Insert.PHONE, recipient.getAddress().toPhoneString());
          }
          intent.setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE);
          getContext().startActivity(intent);
        }
      });
    } else {
      super.setOnClickListener(listener);
    }
  }

}
