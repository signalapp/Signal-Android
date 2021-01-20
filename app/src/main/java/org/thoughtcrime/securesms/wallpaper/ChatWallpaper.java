package org.thoughtcrime.securesms.wallpaper;

import android.os.Parcelable;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.database.model.databaseprotos.Wallpaper;

import java.util.Arrays;
import java.util.List;

public interface ChatWallpaper extends Parcelable {

  float FIXED_DIM_LEVEL_FOR_DARK_THEME = 0.2f;

  List<ChatWallpaper> BUILTINS = Arrays.asList(SingleColorChatWallpaper.SOLID_1,
                                               SingleColorChatWallpaper.SOLID_2,
                                               SingleColorChatWallpaper.SOLID_3,
                                               SingleColorChatWallpaper.SOLID_4,
                                               SingleColorChatWallpaper.SOLID_5,
                                               SingleColorChatWallpaper.SOLID_6,
                                               SingleColorChatWallpaper.SOLID_7,
                                               SingleColorChatWallpaper.SOLID_8,
                                               SingleColorChatWallpaper.SOLID_9,
                                               SingleColorChatWallpaper.SOLID_10,
                                               SingleColorChatWallpaper.SOLID_11,
                                               SingleColorChatWallpaper.SOLID_12,
                                               GradientChatWallpaper.GRADIENT_1,
                                               GradientChatWallpaper.GRADIENT_2,
                                               GradientChatWallpaper.GRADIENT_3,
                                               GradientChatWallpaper.GRADIENT_4,
                                               GradientChatWallpaper.GRADIENT_5,
                                               GradientChatWallpaper.GRADIENT_6,
                                               GradientChatWallpaper.GRADIENT_7,
                                               GradientChatWallpaper.GRADIENT_8,
                                               GradientChatWallpaper.GRADIENT_9);

  float getDimLevelForDarkTheme();

  void loadInto(@NonNull ImageView imageView);

  @NonNull Wallpaper serialize();
}
