package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.AppCompatImageView;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;

import org.thoughtcrime.securesms.color.MaterialColor;
import org.thoughtcrime.securesms.contacts.avatars.ContactColors;
import org.thoughtcrime.securesms.contacts.avatars.GeneratedContactPhoto;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.loki.JazzIdenticonDrawable;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientExporter;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.ThemeUtil;
import org.whispersystems.libsignal.util.guava.Optional;

import network.loki.messenger.R;

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
  private Recipient       recipient;

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
    setOutlineProvider(new ViewOutlineProvider() {

      @Override
      public void getOutline(View view, Outline outline) {
          outline.setOval(0, 0, view.getWidth(), view.getHeight());
      }
    });
    setClipToOutline(true);
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

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    updateImage(w, h);
  }

  public void update(String hexEncodedPublicKey) {
    Address address = Address.fromSerialized(hexEncodedPublicKey);
    if (!address.equals(recipient.getAddress())) {
      this.recipient = Recipient.from(getContext(), address, false);
      updateImage();
    }
  }

  public void setAvatar(@NonNull GlideRequests requestManager, @Nullable Recipient recipient, boolean quickContactEnabled) {
    if (this.recipient == null || !this.recipient.equals(recipient)) {
      this.recipient = recipient;
      updateImage();
    }
    /*
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
     */
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
          getContext().startActivity(RecipientExporter.export(recipient).asAddContactIntent());
        }
      });
    } else {
      super.setOnClickListener(listener);
    }
  }

  private void updateImage() { updateImage(getWidth(), getHeight()); }

  private void updateImage(int w, int h) {
    if (w == 0 || h == 0 || recipient == null) { return; }

    Drawable image;
    Context context = this.getContext();

    if (recipient.isGroupRecipient()) {
      String name = Optional.fromNullable(recipient.getName()).or(Optional.fromNullable(TextSecurePreferences.getProfileName(context))).or("");
      MaterialColor fallbackColor = recipient.getColor();

      if (fallbackColor == ContactColors.UNKNOWN_COLOR && !TextUtils.isEmpty(name)) {
        fallbackColor = ContactColors.generateFor(name);
      }

      image = new GeneratedContactPhoto(name, R.drawable.ic_profile_default).asDrawable(context, fallbackColor.toAvatarColor(context));
    } else {
      // Default to primary device image
      String ourPublicKey = TextSecurePreferences.getLocalNumber(context);
      String ourPrimaryDevice = TextSecurePreferences.getMasterHexEncodedPublicKey(context);
      String recipientAddress = recipient.getAddress().serialize();
      String profileAddress = (ourPrimaryDevice != null && ourPublicKey.equals(recipientAddress)) ? ourPrimaryDevice : recipientAddress;
      image = new JazzIdenticonDrawable(w, h, profileAddress.toLowerCase());
    }
    setImageDrawable(image);
  }

}
