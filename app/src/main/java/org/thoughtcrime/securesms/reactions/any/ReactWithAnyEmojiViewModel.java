package org.thoughtcrime.securesms.reactions.any;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.thoughtcrime.securesms.components.emoji.EmojiPageModel;

import java.util.List;

public final class ReactWithAnyEmojiViewModel extends ViewModel {

  private final ReactWithAnyEmojiRepository repository;
  private final long                        messageId;
  private final boolean                     isMms;

  private ReactWithAnyEmojiViewModel(@NonNull ReactWithAnyEmojiRepository repository, long messageId, boolean isMms) {
    this.repository = repository;
    this.messageId  = messageId;
    this.isMms      = isMms;
  }

  List<EmojiPageModel> getEmojiPageModels() {
    return repository.getEmojiPageModels();
  }

  int getStartIndex() {
    return repository.getEmojiPageModels().get(0).getEmoji().size() == 0 ? 1 : 0;
  }

  void onEmojiSelected(@NonNull String emoji) {
    repository.addEmojiToMessage(emoji, messageId, isMms);
  }

  @AttrRes int getCategoryIconAttr(int position) {
    return repository.getEmojiPageModels().get(position).getIconAttr();
  }

  static class Factory implements ViewModelProvider.Factory {

    private final ReactWithAnyEmojiRepository repository;
    private final long                        messageId;
    private final boolean                     isMms;

    Factory(@NonNull ReactWithAnyEmojiRepository repository, long messageId, boolean isMms) {
      this.repository = repository;
      this.messageId  = messageId;
      this.isMms      = isMms;
    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      //noinspection ConstantConditions
      return modelClass.cast(new ReactWithAnyEmojiViewModel(repository, messageId, isMms));
    }
  }

}
