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

import java.util.List;
import java.util.concurrent.Executor;

class ChatWallpaperRepository {

  private static final Executor EXECUTOR = new SerialExecutor(SignalExecutors.BOUNDED);

  @MainThread
  @Nullable ChatWallpaper getCurrentWallpaper(@Nullable RecipientId recipientId) {
    if (recipientId != null) {
      return Recipient.resolved(recipientId).getWallpaper();
    } else {
      return SignalStore.wallpaper().getWallpaper();
    }
  }

  void getAllWallpaper(@NonNull Consumer<List<ChatWallpaper>> consumer) {
    consumer.accept(ChatWallpaper.BUILTINS);
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

  void setDimInDarkTheme(@NonNull RecipientId recipientId, boolean dimInDarkTheme) {
    if (recipientId != null) {
      EXECUTOR.execute(() -> {
        DatabaseFactory.getRecipientDatabase(ApplicationDependencies.getApplication()).setDimWallpaperInDarkTheme(recipientId, dimInDarkTheme);
      });
    } else {
      SignalStore.wallpaper().setDimInDarkTheme(dimInDarkTheme);
    }
  }
}
