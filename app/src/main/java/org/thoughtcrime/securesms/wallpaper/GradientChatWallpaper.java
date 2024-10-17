package org.thoughtcrime.securesms.wallpaper;

import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.components.RotatableGradientDrawable;
import org.thoughtcrime.securesms.database.model.databaseprotos.Wallpaper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class GradientChatWallpaper implements ChatWallpaper, Parcelable {

  public static final GradientChatWallpaper SUNSET     = new GradientChatWallpaper(168f,
                                                                                   new int[] { 0xFFF3DC47, 0xFFF3DA47, 0xFFF2D546, 0xFFF2CC46, 0xFFF1C146, 0xFFEFB445, 0xFFEEA544, 0xFFEC9644, 0xFFEB8743, 0xFFE97743, 0xFFE86942, 0xFFE65C41, 0xFFE55041, 0xFFE54841, 0xFFE44240, 0xFFE44040 },
                                                                                   new float[] { 0.0f, 0.0807f, 0.1554f, 0.225f, 0.2904f, 0.3526f, 0.4125f, 0.471f, 0.529f, 0.5875f, 0.6474f, 0.7096f, 0.775f, 0.8446f, 0.9193f, 1f },
                                                                                   0f);
  public static final GradientChatWallpaper NOIR       = new GradientChatWallpaper(180f,
                                                                                   new int[] { 0xFF16161D, 0xFF17171E, 0xFF1A1A22, 0xFF1F1F28, 0xFF26262F, 0xFF2D2D38, 0xFF353542, 0xFF3E3E4C, 0xFF474757, 0xFF4F4F61, 0xFF57576B, 0xFF5F5F74, 0xFF65657C, 0xFF6A6A82, 0xFF6D6D85, 0xFF6E6E87 },
                                                                                   new float[] { 0.0000f, 0.0807f, 0.1554f, 0.2250f, 0.2904f, 0.3526f, 0.4125f, 0.4710f, 0.5290f, 0.5875f, 0.6474f, 0.7096f, 0.7750f, 0.8446f, 0.9193f, 1.0000f },
                                                                                   0f);
  public static final GradientChatWallpaper HEATMAP    = new GradientChatWallpaper(192f,
                                                                                   new int[] { 0xFFF53844, 0xFFF33845, 0xFFEC3848, 0xFFE2384C, 0xFFD63851, 0xFFC73857, 0xFFB6385E, 0xFFA43866, 0xFF93376D, 0xFF813775, 0xFF70377C, 0xFF613782, 0xFF553787, 0xFF4B378B, 0xFF44378E, 0xFF42378F },
                                                                                   new float[] { 0.0000f, 0.0075f, 0.0292f, 0.0637f, 0.1097f, 0.1659f, 0.2310f, 0.3037f, 0.3827f, 0.4666f, 0.5541f, 0.6439f, 0.7347f, 0.8252f, 0.9141f, 1.0000f },
                                                                                   0f);
  public static final GradientChatWallpaper AQUA       = new GradientChatWallpaper(180f,
                                                                                   new int[] { 0xFF0093E9, 0xFF0294E9, 0xFF0696E7, 0xFF0D99E5, 0xFF169EE3, 0xFF21A3E0, 0xFF2DA8DD, 0xFF3AAEDA, 0xFF46B5D6, 0xFF53BBD3, 0xFF5FC0D0, 0xFF6AC5CD, 0xFF73CACB, 0xFF7ACDC9, 0xFF7ECFC7, 0xFF80D0C7 },
                                                                                   new float[] { 0.0000f, 0.0807f, 0.1554f, 0.2250f, 0.2904f, 0.3526f, 0.4125f, 0.4710f, 0.5290f, 0.5875f, 0.6474f, 0.7096f, 0.7750f, 0.8446f, 0.9193f, 1.0000f },
                                                                                   0f);
  public static final GradientChatWallpaper IRIDESCENT = new GradientChatWallpaper(192f,
                                                                                   new int[] { 0xFFF04CE6, 0xFFEE4BE6, 0xFFE54AE5, 0xFFD949E5, 0xFFC946E4, 0xFFB644E3, 0xFFA141E3, 0xFF8B3FE2, 0xFF743CE1, 0xFF5E39E0, 0xFF4936DF, 0xFF3634DE, 0xFF2632DD, 0xFF1930DD, 0xFF112FDD, 0xFF0E2FDD },
                                                                                   new float[] { 0.0000f, 0.0807f, 0.1554f, 0.2250f, 0.2904f, 0.3526f, 0.4125f, 0.4710f, 0.5290f, 0.5875f, 0.6474f, 0.7096f, 0.7750f, 0.8446f, 0.9193f, 1.0000f },
                                                                                   0f);
  public static final GradientChatWallpaper MONSTERA   = new GradientChatWallpaper(180f,
                                                                                   new int[] { 0xFF65CDAC, 0xFF64CDAB, 0xFF60CBA8, 0xFF5BC8A3, 0xFF55C49D, 0xFF4DC096, 0xFF45BB8F, 0xFF3CB687, 0xFF33B17F, 0xFF2AAC76, 0xFF21A76F, 0xFF1AA268, 0xFF139F62, 0xFF0E9C5E, 0xFF0B9A5B, 0xFF0A995A },
                                                                                   new float[] { 0.0000f, 0.0807f, 0.1554f, 0.2250f, 0.2904f, 0.3526f, 0.4125f, 0.4710f, 0.5290f, 0.5875f, 0.6474f, 0.7096f, 0.7750f, 0.8446f, 0.9193f, 1.0000f },
                                                                                   0f);
  public static final GradientChatWallpaper BLISS      = new GradientChatWallpaper(180f,
                                                                                   new int[] { 0xFFD8E1FA, 0xFFD8E0F9, 0xFFD8DEF7, 0xFFD8DBF3, 0xFFD8D6EE, 0xFFD7D1E8, 0xFFD7CCE2, 0xFFD7C6DB, 0xFFD7BFD4, 0xFFD7B9CD, 0xFFD6B4C7, 0xFFD6AFC1, 0xFFD6AABC, 0xFFD6A7B8, 0xFFD6A5B6, 0xFFD6A4B5 },
                                                                                   new float[] { 0.0000f, 0.0807f, 0.1554f, 0.2250f, 0.2904f, 0.3526f, 0.4125f, 0.4710f, 0.5290f, 0.5875f, 0.6474f, 0.7096f, 0.7750f, 0.8446f, 0.9193f, 1.0000f },
                                                                                   0f);
  public static final GradientChatWallpaper SKY        = new GradientChatWallpaper(180f,
                                                                                   new int[] { 0xFFD8EBFD, 0xFFD7EAFD, 0xFFD5E9FD, 0xFFD2E7FD, 0xFFCDE5FD, 0xFFC8E3FD, 0xFFC3E0FD, 0xFFBDDDFC, 0xFFB7DAFC, 0xFFB2D7FC, 0xFFACD4FC, 0xFFA7D1FC, 0xFFA3CFFB, 0xFFA0CDFB, 0xFF9ECCFB, 0xFF9DCCFB },
                                                                                   new float[] { 0.0000f, 0.0807f, 0.1554f, 0.2250f, 0.2904f, 0.3526f, 0.4125f, 0.4710f, 0.5290f, 0.5875f, 0.6474f, 0.7096f, 0.7750f, 0.8446f, 0.9193f, 1.0000f },
                                                                                   0f);
  public static final GradientChatWallpaper PEACH      = new GradientChatWallpaper(192f,
                                                                                   new int[] { 0xFFFFE5C2, 0xFFFFE4C1, 0xFFFFE2BF, 0xFFFFDFBD, 0xFFFEDBB9, 0xFFFED6B5, 0xFFFED1B1, 0xFFFDCCAC, 0xFFFDC6A8, 0xFFFDC0A3, 0xFFFCBB9F, 0xFFFCB69B, 0xFFFCB297, 0xFFFCAF95, 0xFFFCAD93, 0xFFFCAC92 },
                                                                                   new float[] { 0.0000f, 0.0807f, 0.1554f, 0.2250f, 0.2904f, 0.3526f, 0.4125f, 0.4710f, 0.5290f, 0.5875f, 0.6474f, 0.7096f, 0.7750f, 0.8446f, 0.9193f, 1.0000f },
                                                                                   0f);


  private final float   degrees;
  private final int[]   colors;
  private final float[] positions;
  private final float dimLevelInDarkTheme;

  GradientChatWallpaper(float degrees, int[] colors, float[] positions, float dimLevelInDarkTheme) {
    this.degrees             = degrees;
    this.colors              = colors;
    this.positions           = positions;
    this.dimLevelInDarkTheme = dimLevelInDarkTheme;
  }

  private GradientChatWallpaper(Parcel in) {
    degrees             = in.readFloat();
    colors              = in.createIntArray();
    positions           = in.createFloatArray();
    dimLevelInDarkTheme = in.readFloat();
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeFloat(degrees);
    dest.writeIntArray(colors);
    dest.writeFloatArray(positions);
    dest.writeFloat(dimLevelInDarkTheme);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  private @NonNull Drawable buildDrawable() {
    return new RotatableGradientDrawable(degrees, colors, positions);
  }

  @Override
  public float getDimLevelForDarkTheme() {
    return dimLevelInDarkTheme;
  }

  @Override
  public void loadInto(@NonNull ImageView imageView) {
    imageView.setImageDrawable(buildDrawable());
  }

  @Override
  public boolean isSameSource(@NonNull ChatWallpaper chatWallpaper) {
    if (this == chatWallpaper) return true;
    if (getClass() != chatWallpaper.getClass()) return false;
    GradientChatWallpaper that = (GradientChatWallpaper) chatWallpaper;

    return Float.compare(that.degrees, degrees) == 0 &&
           Arrays.equals(colors, that.colors) &&
           Arrays.equals(positions, that.positions);
  }

  @Override
  public @NonNull Wallpaper serialize() {
    Wallpaper.LinearGradient.Builder builder = new Wallpaper.LinearGradient.Builder();

    builder.rotation(degrees);

    List<Integer> colors = new ArrayList<>(this.colors.length);
    for (int color : this.colors) {
      colors.add(color);
    }
    builder.colors(colors);

    List<Float> positions = new ArrayList<>(this.positions.length);
    for (float position : this.positions) {
      positions.add(position);
    }
    builder.positions(positions);

    return new Wallpaper.Builder()
                        .linearGradient(builder.build())
                        .dimLevelInDarkTheme(dimLevelInDarkTheme)
                        .build();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    GradientChatWallpaper that = (GradientChatWallpaper) o;
    return Float.compare(that.degrees, degrees) == 0 &&
        Arrays.equals(colors, that.colors) &&
        Arrays.equals(positions, that.positions) &&
        Float.compare(that.dimLevelInDarkTheme, dimLevelInDarkTheme) == 0;
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(degrees, dimLevelInDarkTheme);
    result = 31 * result + Arrays.hashCode(colors);
    result = 31 * result + Arrays.hashCode(positions);
    return result;
  }

  public int[] getColors() {
    return colors;
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
}
