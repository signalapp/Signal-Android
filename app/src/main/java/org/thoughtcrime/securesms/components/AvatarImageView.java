package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import androidx.annotation.DrawableRes;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.fragment.app.FragmentActivity;

import com.bumptech.glide.load.MultiTransformation;
import com.bumptech.glide.load.Transformation;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.settings.conversation.ConversationSettingsActivity;
import org.thoughtcrime.securesms.contacts.avatars.ContactPhoto;
import org.thoughtcrime.securesms.contacts.avatars.ProfileContactPhoto;
import org.thoughtcrime.securesms.contacts.avatars.ResourceContactPhoto;
import org.thoughtcrime.securesms.conversation.colors.AvatarColor;
import org.thoughtcrime.securesms.conversation.colors.ChatColors;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.mms.GlideRequest;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.ui.bottomsheet.RecipientBottomSheetDialogFragment;
import org.thoughtcrime.securesms.util.AvatarUtil;
import org.thoughtcrime.securesms.util.BlurTransformation;
import org.thoughtcrime.securesms.util.ThemeUtil;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class AvatarImageView extends AppCompatImageView {

  private static final int SIZE_LARGE = 1;
  private static final int SIZE_SMALL = 2;

  @SuppressWarnings("unused")
  private static final String TAG = Log.tag(AvatarImageView.class);

  private static final Paint LIGHT_THEME_OUTLINE_PAINT = new Paint();
  private static final Paint DARK_THEME_OUTLINE_PAINT  = new Paint();

  static {
    LIGHT_THEME_OUTLINE_PAINT.setColor(Color.argb((int) (255 * 0.2), 0, 0, 0));
    LIGHT_THEME_OUTLINE_PAINT.setStyle(Paint.Style.STROKE);
    LIGHT_THEME_OUTLINE_PAINT.setStrokeWidth(1);
    LIGHT_THEME_OUTLINE_PAINT.setAntiAlias(true);

    DARK_THEME_OUTLINE_PAINT.setColor(Color.argb((int) (255 * 0.2), 255, 255, 255));
    DARK_THEME_OUTLINE_PAINT.setStyle(Paint.Style.STROKE);
    DARK_THEME_OUTLINE_PAINT.setStrokeWidth(1);
    DARK_THEME_OUTLINE_PAINT.setAntiAlias(true);
  }

  private int                             size;
  private boolean                         inverted;
  private Paint                           outlinePaint;
  private OnClickListener                 listener;
  private Recipient.FallbackPhotoProvider fallbackPhotoProvider;
  private boolean                         blurred;
  private ChatColors                      chatColors;
  private FixedSizeTarget                 fixedSizeTarget;

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

  public void initialize(@NonNull Context context, @Nullable AttributeSet attrs) {
    setScaleType(ScaleType.CENTER_CROP);

    if (attrs != null) {
      TypedArray typedArray = context.getTheme().obtainStyledAttributes(attrs, R.styleable.AvatarImageView, 0, 0);
      inverted = typedArray.getBoolean(R.styleable.AvatarImageView_inverted, false);
      size     = typedArray.getInt(R.styleable.AvatarImageView_fallbackImageSize, SIZE_LARGE);
      typedArray.recycle();
    }

    outlinePaint = ThemeUtil.isDarkTheme(context) ? DARK_THEME_OUTLINE_PAINT : LIGHT_THEME_OUTLINE_PAINT;

    unknownRecipientDrawable = new ResourceContactPhoto(R.drawable.ic_profile_outline_40, R.drawable.ic_profile_outline_20).asDrawable(context, AvatarColor.UNKNOWN, inverted);
    blurred                  = false;
    chatColors               = null;
  }

  @Override
  public void setClipBounds(Rect clipBounds) {
    super.setClipBounds(clipBounds);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);

    float width  = getWidth() - getPaddingRight() - getPaddingLeft();
    float height = getHeight() - getPaddingBottom() - getPaddingTop();
    float cx     = width / 2f;
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

  public void setFallbackPhotoProvider(Recipient.FallbackPhotoProvider fallbackPhotoProvider) {
    this.fallbackPhotoProvider = fallbackPhotoProvider;
  }

  /**
   * Shows self as the actual profile picture.
   */
  public void setRecipient(@NonNull Recipient recipient) {
    setRecipient(recipient, false);
  }

  /**
   * Shows self as the actual profile picture.
   */
  public void setRecipient(@NonNull Recipient recipient, boolean quickContactEnabled) {
    if (recipient.isSelf()) {
      setAvatar(GlideApp.with(this), null, quickContactEnabled);
      AvatarUtil.loadIconIntoImageView(recipient, this);
    } else {
      setAvatar(GlideApp.with(this), recipient, quickContactEnabled);
    }
  }

  public AvatarOptions.Builder buildOptions() {
    return new AvatarOptions.Builder(this);
  }

  /**
   * Shows self as the note to self icon.
   */
  public void setAvatar(@Nullable Recipient recipient) {
    setAvatar(GlideApp.with(this), recipient, false);
  }

  /**
   * Shows self as the profile avatar.
   */
  public void setAvatarUsingProfile(@Nullable Recipient recipient) {
    setAvatar(GlideApp.with(this), recipient, false, true);
  }

  public void setAvatar(@NonNull GlideRequests requestManager, @Nullable Recipient recipient, boolean quickContactEnabled) {
    setAvatar(requestManager, recipient, quickContactEnabled, false);
  }

  public void setAvatar(@NonNull GlideRequests requestManager, @Nullable Recipient recipient, boolean quickContactEnabled, boolean useSelfProfileAvatar) {
    setAvatar(requestManager, recipient, new AvatarOptions.Builder(this)
                                                          .withUseSelfProfileAvatar(useSelfProfileAvatar)
                                                          .withQuickContactEnabled(quickContactEnabled)
                                                          .build());
  }

  private void setAvatar(@Nullable Recipient recipient, @NonNull AvatarOptions avatarOptions) {
    setAvatar(GlideApp.with(this), recipient, avatarOptions);
  }

  private void setAvatar(@NonNull GlideRequests requestManager, @Nullable Recipient recipient, @NonNull AvatarOptions avatarOptions) {
    if (recipient != null) {
      RecipientContactPhoto photo = (recipient.isSelf() && avatarOptions.useSelfProfileAvatar) ? new RecipientContactPhoto(recipient,
                                                                                                                           new ProfileContactPhoto(Recipient.self(),
                                                                                                                                                   Recipient.self().getProfileAvatar()))
                                                                                               : new RecipientContactPhoto(recipient);

      boolean    shouldBlur = recipient.shouldBlurAvatar();
      ChatColors chatColors = recipient.getChatColors();

      if (!photo.equals(recipientContactPhoto) || shouldBlur != blurred || !Objects.equals(chatColors, this.chatColors)) {
        requestManager.clear(this);
        this.chatColors       = chatColors;
        recipientContactPhoto = photo;

        Drawable fallbackContactPhotoDrawable = size == SIZE_SMALL ? photo.recipient.getSmallFallbackContactPhotoDrawable(getContext(), inverted, fallbackPhotoProvider, ViewUtil.getWidth(this))
                                                                   : photo.recipient.getFallbackContactPhotoDrawable(getContext(), inverted, fallbackPhotoProvider, ViewUtil.getWidth(this));

        if (fixedSizeTarget != null) {
          requestManager.clear(fixedSizeTarget);
        }

        if (photo.contactPhoto != null) {

          List<Transformation<Bitmap>> transforms = new ArrayList<>();
          if (shouldBlur) {
            transforms.add(new BlurTransformation(ApplicationDependencies.getApplication(), 0.25f, BlurTransformation.MAX_RADIUS));
          }
          transforms.add(new CircleCrop());
          blurred = shouldBlur;

          GlideRequest<Drawable> request = requestManager.load(photo.contactPhoto)
                                                         .dontAnimate()
                                                         .fallback(fallbackContactPhotoDrawable)
                                                         .error(fallbackContactPhotoDrawable)
                                                         .diskCacheStrategy(DiskCacheStrategy.ALL)
                                                         .downsample(DownsampleStrategy.CENTER_INSIDE)
                                                         .transform(new MultiTransformation<>(transforms));

          if (avatarOptions.fixedSize > 0) {
            fixedSizeTarget = new FixedSizeTarget(avatarOptions.fixedSize);
            request.into(fixedSizeTarget);
          } else {
            request.into(this);
          }

        } else {
          setImageDrawable(fallbackContactPhotoDrawable);
        }
      }

      setAvatarClickHandler(recipient, avatarOptions.quickContactEnabled);
    } else {
      recipientContactPhoto = null;
      requestManager.clear(this);
      if (fallbackPhotoProvider != null) {
        setImageDrawable(fallbackPhotoProvider.getPhotoForRecipientWithoutName()
                                              .asDrawable(getContext(), AvatarColor.UNKNOWN, inverted));
      } else {
        setImageDrawable(unknownRecipientDrawable);
      }

      disableQuickContact();
    }
  }

  private void setAvatarClickHandler(@NonNull final Recipient recipient, boolean quickContactEnabled) {
    if (quickContactEnabled) {
      super.setOnClickListener(v -> {
        Context context = getContext();
        if (recipient.isPushGroup()) {
          context.startActivity(ConversationSettingsActivity.forGroup(context, recipient.requireGroupId().requirePush()),
                                ConversationSettingsActivity.createTransitionBundle(context, this));
        } else {
          if (context instanceof FragmentActivity) {
            RecipientBottomSheetDialogFragment.create(recipient.getId(), null)
                                              .show(((FragmentActivity) context).getSupportFragmentManager(), "BOTTOM");
          } else {
            context.startActivity(ConversationSettingsActivity.forRecipient(context, recipient.getId()),
                                  ConversationSettingsActivity.createTransitionBundle(context, this));
          }
        }
      });
    } else {
      disableQuickContact();
    }
  }

  public void setImageBytesForGroup(@Nullable byte[] avatarBytes,
                                    @Nullable Recipient.FallbackPhotoProvider fallbackPhotoProvider,
                                    @NonNull AvatarColor color)
  {
    Drawable fallback = Util.firstNonNull(fallbackPhotoProvider, Recipient.DEFAULT_FALLBACK_PHOTO_PROVIDER)
                            .getPhotoForGroup()
                            .asDrawable(getContext(), color);

    GlideApp.with(this)
            .load(avatarBytes)
            .dontAnimate()
            .fallback(fallback)
            .error(fallback)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .circleCrop()
            .into(this);
  }

  public void setNonAvatarImageResource(@DrawableRes int imageResource) {
    recipientContactPhoto = null;
    setImageResource(imageResource);
  }

  public void disableQuickContact() {
    super.setOnClickListener(listener);
    setClickable(listener != null);
  }

  private static class RecipientContactPhoto {

    private final @NonNull  Recipient    recipient;
    private final @Nullable ContactPhoto contactPhoto;
    private final           boolean      ready;

    RecipientContactPhoto(@NonNull Recipient recipient) {
      this(recipient, recipient.getContactPhoto());
    }

    RecipientContactPhoto(@NonNull Recipient recipient, @Nullable ContactPhoto contactPhoto) {
      this.recipient    = recipient;
      this.ready        = !recipient.isResolving();
      this.contactPhoto = contactPhoto;
    }

    public boolean equals(@Nullable RecipientContactPhoto other) {
      if (other == null) return false;

      return other.recipient.equals(recipient) &&
             other.recipient.getChatColors().equals(recipient.getChatColors()) &&
             other.ready == ready &&
             Objects.equals(other.contactPhoto, contactPhoto);
    }
  }

  private final class FixedSizeTarget extends SimpleTarget<Drawable> {

    FixedSizeTarget(int size) {
      super(size, size);
    }

    @Override
    public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
      setImageDrawable(resource);
    }
  }

  public static final class AvatarOptions {

    private final boolean quickContactEnabled;
    private final boolean useSelfProfileAvatar;
    private final int     fixedSize;

    private AvatarOptions(@NonNull Builder builder) {
      this.quickContactEnabled  = builder.quickContactEnabled;
      this.useSelfProfileAvatar = builder.useSelfProfileAvatar;
      this.fixedSize            = builder.fixedSize;
    }

    public static final class Builder {

      private final AvatarImageView avatarImageView;

      private boolean quickContactEnabled  = false;
      private boolean useSelfProfileAvatar = false;
      private int     fixedSize            = -1;

      private Builder(@NonNull AvatarImageView avatarImageView) {
        this.avatarImageView = avatarImageView;
      }

      public @NonNull Builder withQuickContactEnabled(boolean quickContactEnabled) {
        this.quickContactEnabled = quickContactEnabled;
        return this;
      }

      public @NonNull Builder withUseSelfProfileAvatar(boolean useSelfProfileAvatar) {
        this.useSelfProfileAvatar = useSelfProfileAvatar;
        return this;
      }

      public @NonNull Builder withFixedSize(@Px @IntRange(from = 1) int fixedSize) {
        this.fixedSize = fixedSize;
        return this;
      }

      public AvatarOptions build() {
        return new AvatarOptions(this);
      }

      public void load(@Nullable Recipient recipient) {
        avatarImageView.setAvatar(recipient, build());
      }
    }
  }
}
