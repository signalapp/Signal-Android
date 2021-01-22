package org.thoughtcrime.securesms.reactions.any;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.reactions.ReactionsLoader;

import java.util.List;

public final class ReactWithAnyEmojiViewModel extends ViewModel {

  private final ReactionsLoader             reactionsLoader;
  private final ReactWithAnyEmojiRepository repository;
  private final long                        messageId;
  private final boolean                     isMms;

  private final LiveData<List<ReactWithAnyEmojiPage>> pages;

  private ReactWithAnyEmojiViewModel(@NonNull ReactionsLoader reactionsLoader,
                                     @NonNull ReactWithAnyEmojiRepository repository,
                                     long messageId,
                                     boolean isMms) {
    this.reactionsLoader = reactionsLoader;
    this.repository      = repository;
    this.messageId       = messageId;
    this.isMms           = isMms;
    this.pages           = Transformations.map(reactionsLoader.getReactions(), repository::getEmojiPageModels);
  }

  LiveData<List<ReactWithAnyEmojiPage>> getEmojiPageModels() {
    return pages;
  }

  void onEmojiSelected(@NonNull String emoji) {
    if (messageId > 0) {
      SignalStore.emojiValues().setPreferredVariation(emoji);
      repository.addEmojiToMessage(emoji, messageId, isMms);
    }
  }

  static class Factory implements ViewModelProvider.Factory {

    private final ReactionsLoader             reactionsLoader;
    private final ReactWithAnyEmojiRepository repository;
    private final long                        messageId;
    private final boolean                     isMms;

    Factory(@NonNull ReactionsLoader reactionsLoader, @NonNull ReactWithAnyEmojiRepository repository, long messageId, boolean isMms) {
      this.reactionsLoader = reactionsLoader;
      this.repository      = repository;
      this.messageId       = messageId;
      this.isMms           = isMms;
    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      //noinspection ConstantConditions
      return modelClass.cast(new ReactWithAnyEmojiViewModel(reactionsLoader, repository, messageId, isMms));
    }
  }

}
