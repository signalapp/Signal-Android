package org.thoughtcrime.securesms.wallpaper;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.database.model.databaseprotos.Wallpaper;

import java.util.Arrays;
import java.util.Objects;

final class GradientChatWallpaper implements ChatWallpaper, Parcelable {

  public static final GradientChatWallpaper SOLID_1    = new GradientChatWallpaper(0xFFE26983);
  public static final GradientChatWallpaper SOLID_2    = new GradientChatWallpaper(0xFFDF9171);
  public static final GradientChatWallpaper SOLID_3    = new GradientChatWallpaper(0xFF9E9887);
  public static final GradientChatWallpaper SOLID_4    = new GradientChatWallpaper(0xFF89AE8F);
  public static final GradientChatWallpaper SOLID_5    = new GradientChatWallpaper(0xFF32C7E2);
  public static final GradientChatWallpaper SOLID_6    = new GradientChatWallpaper(0xFF7C99B6);
  public static final GradientChatWallpaper SOLID_7    = new GradientChatWallpaper(0xFFC988E7);
  public static final GradientChatWallpaper SOLID_8    = new GradientChatWallpaper(0xFFE297C3);
  public static final GradientChatWallpaper SOLID_9    = new GradientChatWallpaper(0xFFA2A2AA);
  public static final GradientChatWallpaper SOLID_10   = new GradientChatWallpaper(0xFF146148);
  public static final GradientChatWallpaper SOLID_11   = new GradientChatWallpaper(0xFF403B91);
  public static final GradientChatWallpaper SOLID_12   = new GradientChatWallpaper(0xFF624249);
  public static final GradientChatWallpaper GRADIENT_1 = new GradientChatWallpaper(167.96f,
                                                                                   new int[] { 0xFFF3DC47, 0xFFF3DA47, 0xFFF2D546, 0xFFF2CC46, 0xFFF1C146, 0xFFEFB445, 0xFFEEA544, 0xFFEC9644, 0xFFEB8743, 0xFFE97743, 0xFFE86942, 0xFFE65C41, 0xFFE55041, 0xFFE54841, 0xFFE44240, 0xFFE44040 },
                                                                                   new float[] { 0.0f, 0.0807f, 0.1554f, 0.225f, 0.2904f, 0.3526f, 0.4125f, 0.471f, 0.529f, 0.5875f, 0.6474f, 0.7096f, 0.775f, 0.8446f, 0.9193f, 1f });
  public static final GradientChatWallpaper GRADIENT_2 = new GradientChatWallpaper(180f,
                                                                                   new int[] { 0xFF16161D, 0xFF17171E, 0xFF1A1A22, 0xFF1F1F28, 0xFF26262F, 0xFF2D2D38, 0xFF353542, 0xFF3E3E4C, 0xFF474757, 0xFF4F4F61, 0xFF57576B, 0xFF5F5F74, 0xFF65657C, 0xFF6A6A82, 0xFF6D6D85, 0xFF6E6E87 },
                                                                                   new float[] { 0.0000f, 0.0807f, 0.1554f, 0.2250f, 0.2904f, 0.3526f, 0.4125f, 0.4710f, 0.5290f, 0.5875f, 0.6474f, 0.7096f, 0.7750f, 0.8446f, 0.9193f, 1.0000f });

  private final float   degrees;
  private final int[]   colors;
  private final float[] positions;

  GradientChatWallpaper(int color) {
    this(0f, new int[]{color, color}, null);
  }

  GradientChatWallpaper(float degrees, int[] colors, float[] positions) {
    this.degrees   = degrees;
    this.colors    = colors;
    this.positions = positions;
  }

  private GradientChatWallpaper(Parcel in) {
    degrees   = in.readFloat();
    colors    = in.createIntArray();
    positions = in.createFloatArray();
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeFloat(degrees);
    dest.writeIntArray(colors);
    dest.writeFloatArray(positions);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  private @NonNull Drawable buildDrawable() {
    return new RotatableGradientDrawable(degrees, colors, positions);
  }

  @Override
  public void loadInto(@NonNull ImageView imageView) {
    imageView.setImageDrawable(buildDrawable());
  }

  @Override
  public @NonNull Wallpaper serialize() {
    Wallpaper.LinearGradient.Builder builder = Wallpaper.LinearGradient.newBuilder();

    builder.setRotation(degrees);

    for (int color : colors) {
      builder.addColors(color);
    }

    for (float position : positions) {
      builder.addPositions(position);
    }

    return Wallpaper.newBuilder()
                    .setLinearGradient(builder)
                    .build();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    GradientChatWallpaper that = (GradientChatWallpaper) o;
    return Float.compare(that.degrees, degrees) == 0 &&
        Arrays.equals(colors, that.colors) &&
        Arrays.equals(positions, that.positions);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(degrees);
    result = 31 * result + Arrays.hashCode(colors);
    result = 31 * result + Arrays.hashCode(positions);
    return result;
  }

  public static final Creator<GradientChatWallpaper> CREATOR = new Creator<GradientChatWallpaper>() {
    @Override
    public GradientChatWallpaper createFromParcel(Parcel in) {
      return new GradientChatWallpaper(in);
    }

    @Override
    public GradientChatWallpaper[] newArray(int size) {
      return new GradientChatWallpaper[size];
    }
  };

  private static final class RotatableGradientDrawable extends Drawable {

    private final float   degrees;
    private final int[]   colors;
    private final float[] positions;

    private final Rect  fillRect  = new Rect();
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private RotatableGradientDrawable(float degrees, int[] colors, @Nullable float[] positions) {
      this.degrees   = degrees + 225f;
      this.colors    = colors;
      this.positions = positions;
    }

    @Override
    public void setBounds(int left, int top, int right, int bottom) {
      super.setBounds(left, top, right, bottom);

      Point topLeft     = new Point(left, top);
      Point topRight    = new Point(right, top);
      Point bottomLeft  = new Point(left, bottom);
      Point bottomRight = new Point(right, bottom);
      Point origin      = new Point(getBounds().width() / 2, getBounds().height() / 2);

      Point rotationTopLeft     = cornerPrime(origin, topLeft, degrees);
      Point rotationTopRight    = cornerPrime(origin, topRight, degrees);
      Point rotationBottomLeft  = cornerPrime(origin, bottomLeft, degrees);
      Point rotationBottomRight = cornerPrime(origin, bottomRight, degrees);

      fillRect.left   = Integer.MAX_VALUE;
      fillRect.top    = Integer.MAX_VALUE;
      fillRect.right  = Integer.MIN_VALUE;
      fillRect.bottom = Integer.MIN_VALUE;

      for (Point point : Arrays.asList(topLeft, topRight, bottomLeft, bottomRight, rotationTopLeft, rotationTopRight, rotationBottomLeft, rotationBottomRight)) {
        if (point.x < fillRect.left) {
          fillRect.left = point.x;
        }

        if (point.x > fillRect.right) {
          fillRect.right = point.x;
        }

        if (point.y < fillRect.top) {
          fillRect.top = point.y;
        }

        if (point.y > fillRect.bottom) {
          fillRect.bottom = point.y;
        }
      }

      fillPaint.setShader(new LinearGradient(fillRect.left, fillRect.top, fillRect.right, fillRect.bottom, colors, positions, Shader.TileMode.CLAMP));
    }

    private static Point cornerPrime(@NonNull Point origin, @NonNull Point corner, float degrees) {
      return new Point(xPrime(origin, corner, Math.toRadians(degrees)), yPrime(origin, corner, Math.toRadians(degrees)));
    }

    private static int xPrime(@NonNull Point origin, @NonNull Point corner, double theta) {
      return (int) Math.ceil(((corner.x - origin.x) * Math.cos(theta)) - ((corner.y - origin.y) * Math.sin(theta)) + origin.x);
    }

    private static int yPrime(@NonNull Point origin, @NonNull Point corner, double theta) {
      return (int) Math.ceil(((corner.x - origin.x) * Math.sin(theta)) + ((corner.y - origin.y) * Math.cos(theta)) + origin.y);
    }

    @Override
    public void draw(Canvas canvas) {
      int save = canvas.save();
      canvas.rotate(degrees, getBounds().width() / 2f, getBounds().height() / 2f);
      canvas.drawRect(fillRect, fillPaint);
      canvas.restoreToCount(save);
    }

    @Override
    public void setAlpha(int alpha) {
      // Not supported
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
      // Not supported
    }

    @Override
    public int getOpacity() {
      return PixelFormat.OPAQUE;
    }
  }
}
