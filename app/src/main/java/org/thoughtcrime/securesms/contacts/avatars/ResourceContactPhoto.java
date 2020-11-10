package org.thoughtcrime.securesms.contacts.avatars;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.widget.ImageView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;

import com.amulyakhare.textdrawable.TextDrawable;
import com.makeramen.roundedimageview.RoundedDrawable;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.ContextUtil;
import org.thoughtcrime.securesms.util.ThemeUtil;

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
  public @NonNull Drawable asDrawable(@NonNull Context context, int color) {
    return asDrawable(context, color, false);
  }

  @Override
  public @NonNull Drawable asDrawable(@NonNull Context context, int color, boolean inverted) {
    return buildDrawable(context, resourceId, color, inverted);
  }

  @Override
  public @NonNull Drawable asSmallDrawable(@NonNull Context context, int color, boolean inverted) {
    return buildDrawable(context, smallResourceId, color, inverted);
  }

  private @NonNull Drawable buildDrawable(@NonNull Context context, int resourceId, int color, boolean inverted) {
    Drawable        background = TextDrawable.builder().buildRound(" ", inverted ? Color.WHITE : color);
    RoundedDrawable foreground = (RoundedDrawable) RoundedDrawable.fromDrawable(AppCompatResources.getDrawable(context, resourceId));

    //noinspection ConstantConditions
    foreground.setScaleType(scaleType);

    if (inverted) {
      foreground.setColorFilter(color, PorterDuff.Mode.SRC_ATOP);
    }

    Drawable gradient = ContextUtil.requireDrawable(context, R.drawable.avatar_gradient);

    return new ExpandingLayerDrawable(new Drawable[] {background, foreground, gradient});
  }

  @Override
  public @Nullable Drawable asCallCard(@NonNull Context context) {
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
