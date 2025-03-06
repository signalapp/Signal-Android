package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import androidx.annotation.DrawableRes;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.fragment.app.FragmentActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.MultiTransformation;
import com.bumptech.glide.load.Transformation;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.avatar.fallback.FallbackAvatar;
import org.thoughtcrime.securesms.avatar.fallback.FallbackAvatarDrawable;
import org.thoughtcrime.securesms.components.settings.conversation.ConversationSettingsActivity;
import org.thoughtcrime.securesms.contacts.avatars.ContactPhoto;
import org.thoughtcrime.securesms.contacts.avatars.ProfileContactPhoto;
import org.thoughtcrime.securesms.contacts.avatars.SystemContactPhoto;
import org.thoughtcrime.securesms.conversation.colors.AvatarColor;
import org.thoughtcrime.securesms.conversation.colors.AvatarGradientColors;
import org.thoughtcrime.securesms.conversation.colors.ChatColors;
import org.thoughtcrime.securesms.conversation.v2.data.AvatarDownloadStateCache;
import org.thoughtcrime.securesms.jobs.RetrieveProfileAvatarJob;
import org.thoughtcrime.securesms.profiles.AvatarHelper;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.ui.bottomsheet.RecipientBottomSheetDialogFragment;
import org.thoughtcrime.securesms.util.AvatarUtil;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class AvatarImageView extends AppCompatImageView {

  private static final int SIZE_LARGE = 1;
  private static final int SIZE_SMALL = 2;

  @SuppressWarnings("unused")
  private static final String TAG = Log.tag(AvatarImageView.class);

  private final RequestListener<Drawable> redownloadRequestListener = new RedownloadRequestListener();

  private int                             size;
  private boolean                         inverted;
  private OnClickListener                 listener;
  private boolean                         blurred;
  private ChatColors                      chatColors;
  private FixedSizeTarget                 fixedSizeTarget;

  private @Nullable RecipientContactPhoto  recipientContactPhoto;
  private @NonNull  Drawable               unknownRecipientDrawable;
  private @NonNull  FallbackAvatarProvider fallbackAvatarProvider = new DefaultFallbackAvatarProvider();


  public AvatarImageView(Context context) {
    this(context, null);
  }

  public AvatarImageView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public AvatarImageView(Context context, @Nullable AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
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

    unknownRecipientDrawable = new FallbackAvatarDrawable(context, new FallbackAvatar.Resource.Person(AvatarColor.UNKNOWN)).circleCrop();
    blurred                  = false;
    chatColors               = null;
  }

  @Override
  public void setOnClickListener(OnClickListener listener) {
    this.listener = listener;
    super.setOnClickListener(listener);
  }

  public void setFallbackAvatarProvider(@Nullable FallbackAvatarProvider fallbackAvatarProvider) {
    this.fallbackAvatarProvider = fallbackAvatarProvider != null ? fallbackAvatarProvider : new DefaultFallbackAvatarProvider();
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
      setAvatar(Glide.with(this), null, quickContactEnabled);
      AvatarUtil.loadIconIntoImageView(recipient, this);
    } else {
      setAvatar(Glide.with(this), recipient, quickContactEnabled);
    }
  }

  public AvatarOptions.Builder buildOptions() {
    return new AvatarOptions.Builder(this);
  }

  /**
   * Shows self as the note to self icon.
   */
  public void setAvatar(@Nullable Recipient recipient) {
    setAvatar(Glide.with(this), recipient, false);
  }

  /**
   * Shows self as the profile avatar.
   */
  public void setAvatarUsingProfile(@Nullable Recipient recipient) {
    setAvatar(Glide.with(this), recipient, false, true);
  }

  public void setAvatar(@NonNull RequestManager requestManager, @Nullable Recipient recipient, boolean quickContactEnabled) {
    setAvatar(requestManager, recipient, quickContactEnabled, false);
  }

  public void setAvatar(@NonNull RequestManager requestManager, @Nullable Recipient recipient, boolean quickContactEnabled, boolean useSelfProfileAvatar) {
    setAvatar(requestManager, recipient, quickContactEnabled, useSelfProfileAvatar, false);
  }

  public void setAvatar(@NonNull RequestManager requestManager, @Nullable Recipient recipient, boolean quickContactEnabled, boolean useSelfProfileAvatar, boolean useBlurGradient) {
    setAvatar(requestManager, recipient, new AvatarOptions.Builder(this)
                                                          .withUseSelfProfileAvatar(useSelfProfileAvatar)
                                                          .withQuickContactEnabled(quickContactEnabled)
                                                          .withUseBlurGradient(useBlurGradient)
                                                          .build());
  }

  private void setAvatar(@Nullable Recipient recipient, @NonNull AvatarOptions avatarOptions) {
    setAvatar(Glide.with(this), recipient, avatarOptions);
  }

  private void setAvatar(@NonNull RequestManager requestManager, @Nullable Recipient recipient, @NonNull AvatarOptions avatarOptions) {
    if (recipient != null) {
      RecipientContactPhoto photo = (recipient.isSelf() && avatarOptions.useSelfProfileAvatar) ? new RecipientContactPhoto(recipient,
                                                                                                                           new ProfileContactPhoto(Recipient.self()))
                                                                                               : new RecipientContactPhoto(recipient);

      boolean    shouldBlur = recipient.getShouldBlurAvatar();
      ChatColors chatColors = recipient.getChatColors();

      if (!photo.equals(recipientContactPhoto) || shouldBlur != blurred || !Objects.equals(chatColors, this.chatColors)) {
        requestManager.clear(this);
        this.chatColors       = chatColors;
        recipientContactPhoto = photo;

        FallbackAvatarProvider activeFallbackPhotoProvider = this.fallbackAvatarProvider;
        if (recipient.isSelf() && avatarOptions.useSelfProfileAvatar) {
          activeFallbackPhotoProvider = new FallbackAvatarProvider() {
            @Override
            public @NonNull FallbackAvatar getFallbackAvatar(@NonNull Recipient recipient) {
              if (recipient.isSelf()) {
                return new FallbackAvatar.Resource.Person(recipient.getAvatarColor());
              }

              return FallbackAvatarProvider.super.getFallbackAvatar(recipient);
            }
          };
        }

        boolean  wasUnblurred           = shouldBlur != blurred;
        boolean  inProgressDownload     = AvatarDownloadStateCache.getDownloadState(recipient) == AvatarDownloadStateCache.DownloadState.IN_PROGRESS;
        boolean  shouldShowBlurGradient = avatarOptions.useBlurGradient && (!recipient.getShouldShowAvatarByDefault() || inProgressDownload);
        boolean  shouldHaveAvatar       = recipient.getHasAvatar();
        boolean  hasAvatar              = AvatarHelper.hasAvatar(getContext(), recipient.getId()) || (photo.contactPhoto instanceof SystemContactPhoto);
        Drawable fallback               = new FallbackAvatarDrawable(getContext(), activeFallbackPhotoProvider.getFallbackAvatar(recipient)).circleCrop();

        if (fixedSizeTarget != null) {
          requestManager.clear(fixedSizeTarget);
        }

        if (photo.contactPhoto != null && hasAvatar && !shouldBlur) {
          List<Transformation<Bitmap>> transforms = Collections.singletonList(new CircleCrop());

          RequestBuilder<Drawable> request = requestManager.load(photo.contactPhoto)
                                                         .fallback(fallback)
                                                         .error(fallback)
                                                         .diskCacheStrategy(DiskCacheStrategy.ALL)
                                                         .downsample(DownsampleStrategy.CENTER_INSIDE)
                                                         .transform(new MultiTransformation<>(transforms))
                                                         .addListener(redownloadRequestListener);

          if (wasUnblurred) {
            blurred = shouldBlur;
            request = request.transition(DrawableTransitionOptions.withCrossFade(200));
          } else {
            request = request.dontAnimate();
          }

          if (avatarOptions.fixedSize > 0) {
            fixedSizeTarget = new FixedSizeTarget(avatarOptions.fixedSize);
            request.into(fixedSizeTarget);
          } else {
            request.into(this);
          }

        } else if ((shouldBlur || shouldShowBlurGradient) && shouldHaveAvatar) {
          setImageDrawable(AvatarGradientColors.getGradientDrawable(recipient));
          blurred = true;
        } else {
          if (!shouldBlur && shouldHaveAvatar && !hasAvatar && recipient.isIndividual()) {
            RetrieveProfileAvatarJob.enqueueForceUpdate(recipient);
          }
          setImageDrawable(fallback);
        }
      }

      setAvatarClickHandler(recipient, avatarOptions.quickContactEnabled);
    } else {
      recipientContactPhoto = null;
      requestManager.clear(this);
      setImageDrawable(unknownRecipientDrawable);

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
            RecipientBottomSheetDialogFragment.show(((FragmentActivity) context).getSupportFragmentManager(), recipient.getId(), null);
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

  public void setImageBytesForGroup(@Nullable byte[] avatarBytes, @NonNull AvatarColor color)
  {
    Drawable fallback = new FallbackAvatarDrawable(getContext(), new FallbackAvatar.Resource.Group(color)).circleCrop();

    Glide.with(this)
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

  public interface FallbackAvatarProvider {
    default @NonNull FallbackAvatar getFallbackAvatar(@NonNull Recipient recipient) {
      return recipient.getFallbackAvatar();
    }
  }

  private static class DefaultFallbackAvatarProvider implements FallbackAvatarProvider {}

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
    private final boolean useBlurGradient;
    private final int     fixedSize;

    private AvatarOptions(@NonNull Builder builder) {
      this.quickContactEnabled  = builder.quickContactEnabled;
      this.useSelfProfileAvatar = builder.useSelfProfileAvatar;
      this.useBlurGradient      = builder.useBlurGradient;
      this.fixedSize            = builder.fixedSize;
    }

    public static final class Builder {

      private final AvatarImageView avatarImageView;

      private boolean quickContactEnabled  = false;
      private boolean useSelfProfileAvatar = false;
      private boolean useBlurGradient      = false;
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

      public @NonNull Builder withUseBlurGradient(boolean useBlurGradient) {
        this.useBlurGradient = useBlurGradient;
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

  private static class RedownloadRequestListener implements RequestListener<Drawable> {
    @Override
    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
      if (model instanceof ProfileContactPhoto) {
        RetrieveProfileAvatarJob.enqueueForceUpdate(((ProfileContactPhoto) model).getRecipient());
      }
      return false;
    }

    @Override
    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
      return false;
    }
  }
}
