package org.thoughtcrime.securesms.wallpaper;

import android.os.Parcelable;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.database.model.databaseprotos.Wallpaper;

import java.util.Arrays;
import java.util.List;

public interface ChatWallpaper extends Parcelable {

  List<ChatWallpaper> BUILTINS = Arrays.asList(GradientChatWallpaper.SOLID_1,
                                               GradientChatWallpaper.SOLID_2,
                                               GradientChatWallpaper.SOLID_3,
                                               GradientChatWallpaper.SOLID_4,
                                               GradientChatWallpaper.SOLID_5,
                                               GradientChatWallpaper.SOLID_6,
                                               GradientChatWallpaper.SOLID_7,
                                               GradientChatWallpaper.SOLID_8,
                                               GradientChatWallpaper.SOLID_9,
                                               GradientChatWallpaper.SOLID_10,
                                               GradientChatWallpaper.SOLID_11,
                                               GradientChatWallpaper.SOLID_12,
                                               GradientChatWallpaper.GRADIENT_1,
                                               GradientChatWallpaper.GRADIENT_2);

  void loadInto(@NonNull ImageView imageView);

  @NonNull Wallpaper serialize();
}
