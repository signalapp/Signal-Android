package org.thoughtcrime.securesms.wallpaper;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Consumer;

import org.signal.core.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.concurrent.SerialExecutor;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;

class ChatWallpaperRepository {

  private static final Executor EXECUTOR = new SerialExecutor(SignalExecutors.BOUNDED);

  @MainThread
  @Nullable ChatWallpaper getCurrentWallpaper(@Nullable RecipientId recipientId) {
    if (recipientId != null) {
      return Recipient.live(recipientId).get().getWallpaper();
    } else {
      return SignalStore.wallpaper().getWallpaper();
    }
  }

  void getAllWallpaper(@NonNull Consumer<List<ChatWallpaper>> consumer) {
    EXECUTOR.execute(() -> {
      List<ChatWallpaper> wallpapers = new ArrayList<>(ChatWallpaper.BUILTINS);

      wallpapers.addAll(WallpaperStorage.getAll(ApplicationDependencies.getApplication()));
      consumer.accept(wallpapers);
    });
  }

  void saveWallpaper(@Nullable RecipientId recipientId, @Nullable ChatWallpaper chatWallpaper) {
    if (recipientId != null) {
      //noinspection CodeBlock2Expr
      EXECUTOR.execute(() -> {
        DatabaseFactory.getRecipientDatabase(ApplicationDependencies.getApplication()).setWallpaper(recipientId, chatWallpaper);
      });
    } else {
      SignalStore.wallpaper().setWallpaper(ApplicationDependencies.getApplication(), chatWallpaper);
    }
  }

  void resetAllWallpaper() {
    SignalStore.wallpaper().setWallpaper(ApplicationDependencies.getApplication(), null);
    EXECUTOR.execute(() -> {
      DatabaseFactory.getRecipientDatabase(ApplicationDependencies.getApplication()).resetAllWallpaper();
    });
  }

  void setDimInDarkTheme(@Nullable RecipientId recipientId, boolean dimInDarkTheme) {
    if (recipientId != null) {
      EXECUTOR.execute(() -> {
        Recipient recipient = Recipient.resolved(recipientId);
        if (recipient.hasOwnWallpaper()) {
          DatabaseFactory.getRecipientDatabase(ApplicationDependencies.getApplication()).setDimWallpaperInDarkTheme(recipientId, dimInDarkTheme);
        } else if (recipient.hasWallpaper()) {
          DatabaseFactory.getRecipientDatabase(ApplicationDependencies.getApplication())
                         .setWallpaper(recipientId,
                                       ChatWallpaperFactory.updateWithDimming(recipient.getWallpaper(),
                                                                              dimInDarkTheme ? ChatWallpaper.FIXED_DIM_LEVEL_FOR_DARK_THEME
                                                                                             : 0f));
        } else {
          throw new IllegalStateException("Unexpected call to setDimInDarkTheme, no wallpaper has been set on the given recipient or globally.");
        }
      });
    } else {
      SignalStore.wallpaper().setDimInDarkTheme(dimInDarkTheme);
    }
  }
}
