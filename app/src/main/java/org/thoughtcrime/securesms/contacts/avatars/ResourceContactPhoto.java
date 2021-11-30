package org.thoughtcrime.securesms.contacts.avatars;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.widget.ImageView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;

import com.makeramen.roundedimageview.RoundedDrawable;

import org.jetbrains.annotations.NotNull;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.avatar.Avatars;
import org.thoughtcrime.securesms.conversation.colors.AvatarColor;

import java.util.Objects;

public class ResourceContactPhoto implements FallbackContactPhoto {

  private final int resourceId;
  private final int smallResourceId;
  private final int callCardResourceId;

  private ImageView.ScaleType scaleType = ImageView.ScaleType.CENTER;

  public ResourceContactPhoto(@DrawableRes int resourceId) {
    this(resourceId, resourceId, resourceId);
  }

  public ResourceContactPhoto(@DrawableRes int resourceId, @DrawableRes int smallResourceId) {
    this(resourceId, smallResourceId, resourceId);
  }

  public ResourceContactPhoto(@DrawableRes int resourceId, @DrawableRes int smallResourceId, @DrawableRes int callCardResourceId) {
    this.resourceId         = resourceId;
    this.callCardResourceId = callCardResourceId;
    this.smallResourceId    = smallResourceId;
  }

  public void setScaleType(@NonNull ImageView.ScaleType scaleType) {
    this.scaleType = scaleType;
  }

  @Override
  public @NonNull Drawable asDrawable(@NonNull Context context, @NonNull AvatarColor color) {
    return asDrawable(context, color, false);
  }

  @Override
  public @NonNull Drawable asDrawable(@NonNull Context context, @NonNull AvatarColor color, boolean inverted) {
    return buildDrawable(context, resourceId, color, inverted);
  }

  @Override
  public @NonNull Drawable asSmallDrawable(@NonNull Context context, @NonNull AvatarColor color, boolean inverted) {
    return buildDrawable(context, smallResourceId, color, inverted);
  }

  private @NonNull Drawable buildDrawable(@NonNull Context context, int resourceId, @NonNull AvatarColor color, boolean inverted) {
    Avatars.ForegroundColor foregroundColor = Avatars.getForegroundColor(color);
    Drawable                background      = Objects.requireNonNull(ContextCompat.getDrawable(context, R.drawable.circle_tintable));
    RoundedDrawable         foreground      = (RoundedDrawable) RoundedDrawable.fromDrawable(AppCompatResources.getDrawable(context, resourceId));

    //noinspection ConstantConditions
    foreground.setScaleType(scaleType);
    background.setColorFilter(inverted ? foregroundColor.getColorInt() : color.colorInt(), PorterDuff.Mode.SRC_IN);
    foreground.setColorFilter(inverted ? color.colorInt() : foregroundColor.getColorInt(), PorterDuff.Mode.SRC_ATOP);

    return new ExpandingLayerDrawable(new Drawable[] {background, foreground});
  }

  @Override
  public @Nullable Drawable asCallCard(@NotNull @NonNull Context context) {
    return AppCompatResources.getDrawable(context, callCardResourceId);
  }

  private static class ExpandingLayerDrawable extends LayerDrawable {
    public ExpandingLayerDrawable(@NonNull Drawable[] layers) {
      super(layers);
    }

    @Override
    public int getIntrinsicWidth() {
      return -1;
    }

    @Override
    public int getIntrinsicHeight() {
      return -1;
    }
  }

}
