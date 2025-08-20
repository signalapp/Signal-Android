package org.thoughtcrime.securesms;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.transition.TransitionInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;
import com.github.chrisbanes.photoview.PhotoView;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.avatar.fallback.FallbackAvatar;
import org.thoughtcrime.securesms.avatar.fallback.FallbackAvatarDrawable;
import org.thoughtcrime.securesms.components.emoji.EmojiTextView;
import org.thoughtcrime.securesms.contacts.avatars.ContactPhoto;
import org.thoughtcrime.securesms.contacts.avatars.ProfileContactPhoto;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.FullscreenHelper;

/**
 * Activity for displaying avatars full screen.
 */
public final class AvatarPreviewActivity extends PassphraseRequiredActivity {

  private static final String TAG = Log.tag(AvatarPreviewActivity.class);

  private static final String RECIPIENT_ID_EXTRA = "recipient_id";

  private static final int ZOOM_TRANSITION_DURATION = 300;

  private static final float ZOOM_LEVEL_MIN = 1.0f;
  private static final float SMALL_IMAGES_ZOOM_LEVEL_MID = 3.0f;
  private static final float SMALL_IMAGES_ZOOM_LEVEL_MAX = 8.0f;

  public static @NonNull Intent intentFromRecipientId(@NonNull Context context,
                                                      @NonNull RecipientId recipientId)
  {
    Intent intent = new Intent(context, AvatarPreviewActivity.class);
    intent.putExtra(RECIPIENT_ID_EXTRA, recipientId.serialize());
    return intent;
  }

  public static Bundle createTransitionBundle(@NonNull Activity activity, @NonNull View from) {
    return ActivityOptionsCompat.makeSceneTransitionAnimation(activity, from, "avatar").toBundle();
  }

  @Override
  protected void attachBaseContext(@NonNull Context newBase) {
    getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_YES);
    super.attachBaseContext(newBase);
  }

  @Override
  protected void onCreate(Bundle savedInstanceState, boolean ready) {
    super.onCreate(savedInstanceState, ready);

    setTheme(R.style.TextSecure_MediaPreview);
    setContentView(R.layout.contact_photo_preview_activity);

    postponeEnterTransition();
    TransitionInflater inflater = TransitionInflater.from(this);
    getWindow().setSharedElementEnterTransition(inflater.inflateTransition(R.transition.full_screen_avatar_image_enter_transition_set));
    getWindow().setSharedElementReturnTransition(inflater.inflateTransition(R.transition.full_screen_avatar_image_return_transition_set));

    Toolbar       toolbar = findViewById(R.id.toolbar);
    EmojiTextView title   = findViewById(R.id.title);
    PhotoView     avatar  = findViewById(R.id.avatar);
    avatar.setZoomTransitionDuration(ZOOM_TRANSITION_DURATION);
    avatar.setScaleLevels(ZOOM_LEVEL_MIN, SMALL_IMAGES_ZOOM_LEVEL_MID, SMALL_IMAGES_ZOOM_LEVEL_MAX);


    setSupportActionBar(toolbar);

    requireSupportActionBar().setDisplayHomeAsUpEnabled(true);
    requireSupportActionBar().setDisplayShowTitleEnabled(false);

    Context     context     = getApplicationContext();
    RecipientId recipientId = RecipientId.from(getIntent().getStringExtra(RECIPIENT_ID_EXTRA));

    Recipient.live(recipientId).observe(this, recipient -> {
      ContactPhoto contactPhoto  = recipient.isSelf() ? new ProfileContactPhoto(recipient)
                                                      : recipient.getContactPhoto();
      FallbackAvatar fallbackAvatar = recipient.isSelf() ? new FallbackAvatar.Resource.Person(recipient.getAvatarColor())
                                                         : recipient.getFallbackAvatar();

      Drawable fallbackDrawable = new FallbackAvatarDrawable(context, fallbackAvatar);

      Resources resources = this.getResources();

      Glide.with(this)
              .asBitmap()
              .load(contactPhoto)
              .fallback(fallbackDrawable)
              .error(fallbackDrawable)
              .diskCacheStrategy(DiskCacheStrategy.ALL)
              .addListener(new RequestListener<Bitmap>() {
                @Override
                public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Bitmap> target, boolean isFirstResource) {
                  Log.w(TAG, "Unable to load avatar, or avatar removed, closing");
                  finish();
                  return false;
                }

                @Override
                public boolean onResourceReady(Bitmap resource, Object model, Target<Bitmap> target, DataSource dataSource, boolean isFirstResource) {
                  return false;
                }
              })
              .into(new CustomTarget<Bitmap>() {
                @Override
                public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                  avatar.setImageDrawable(RoundedBitmapDrawableFactory.create(resources, resource));
                  startPostponedEnterTransition();
                }

                @Override
                public void onLoadCleared(@Nullable Drawable placeholder) {
                }
              });

      title.setText(recipient.getDisplayName(context));
    });

    FullscreenHelper fullscreenHelper = new FullscreenHelper(this);

    avatar.setOnClickListener(v -> fullscreenHelper.toggleUiVisibility());

    fullscreenHelper.configureToolbarLayout(findViewById(R.id.toolbar_cutout_spacer), toolbar);

    fullscreenHelper.showAndHideWithSystemUI(getWindow(), findViewById(R.id.toolbar_layout));
  }

  @Override
  public boolean onSupportNavigateUp() {
    onBackPressed();
    return true;
  }
}
