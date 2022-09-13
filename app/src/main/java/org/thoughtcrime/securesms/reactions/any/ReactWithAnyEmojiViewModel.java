package org.thoughtcrime.securesms.reactions.any;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.annimon.stream.Stream;

import org.session.libsession.messaging.MessagingModuleConfiguration;
import org.thoughtcrime.securesms.components.emoji.EmojiPageModel;
import org.thoughtcrime.securesms.components.emoji.EmojiPageViewGridAdapter;
import org.thoughtcrime.securesms.database.model.MessageId;
import org.thoughtcrime.securesms.keyboard.emoji.search.EmojiSearchRepository;
import org.thoughtcrime.securesms.reactions.ReactionsRepository;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModelList;

import java.util.List;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.subjects.BehaviorSubject;

public final class ReactWithAnyEmojiViewModel extends ViewModel {

  private static final int SEARCH_LIMIT = 40;
  private final EmojiSearchRepository emojiSearchRepository;

  private final ReactWithAnyEmojiRepository  repository;
  private final Observable<MappingModelList> emojiList;
  private final BehaviorSubject<EmojiSearchResult> searchResults;

  private ReactWithAnyEmojiViewModel(@NonNull ReactWithAnyEmojiRepository repository,
                                     long messageId,
                                     boolean isMms,
                                     @NonNull EmojiSearchRepository emojiSearchRepository)
  {
    this.repository            = repository;
    this.emojiSearchRepository = emojiSearchRepository;
    this.searchResults         = BehaviorSubject.createDefault(new EmojiSearchResult());

    Observable<List<ReactWithAnyEmojiPage>> emojiPages = new ReactionsRepository().getReactions(new MessageId(messageId, isMms))
                                                                                  .map(thisMessagesReactions -> repository.getEmojiPageModels());

    Observable<MappingModelList> emojiList = emojiPages.map(pages -> {
      MappingModelList list = new MappingModelList();

      for (ReactWithAnyEmojiPage page : pages) {
        String key = page.getKey();
        for (ReactWithAnyEmojiPageBlock block : page.getPageBlocks()) {
          list.add(new EmojiPageViewGridAdapter.EmojiHeader(key, block.getLabel()));
          list.addAll(toMappingModels(block.getPageModel()));
        }
      }

      return list;
    });

    this.emojiList = Observable.combineLatest(emojiList, searchResults.distinctUntilChanged(), (all, search) -> {
      if (search.query.isEmpty()) {
        return all;
      } else {
        if (search.model.getDisplayEmoji().isEmpty()) {
          return MappingModelList.singleton(new EmojiPageViewGridAdapter.EmojiNoResultsModel());
        }
        return toMappingModels(search.model);
      }
    });
  }

  @NonNull Observable<MappingModelList> getEmojiList() {
    return emojiList.observeOn(AndroidSchedulers.mainThread());
  }

  void onEmojiSelected(@NonNull String emoji) {
    repository.addEmojiToMessage(emoji);
  }

  public void onQueryChanged(String query) {
    emojiSearchRepository.submitQuery(query, SEARCH_LIMIT, m -> searchResults.onNext(new EmojiSearchResult(query, m)));
  }

  private static @NonNull MappingModelList toMappingModels(@NonNull EmojiPageModel model) {
    return Stream.of(model.getDisplayEmoji())
                .map(e -> new EmojiPageViewGridAdapter.EmojiModel(model.getKey(), e))
                .collect(MappingModelList.collect());
  }

  private static class EmojiSearchResult {
    private final String         query;
    private final EmojiPageModel model;

    private EmojiSearchResult(@NonNull String query, @Nullable EmojiPageModel model) {
      this.query = query;
      this.model = model;
    }

    public EmojiSearchResult() {
      this("", null);
    }
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
      return modelClass.cast(new ReactWithAnyEmojiViewModel(repository, messageId, isMms, new EmojiSearchRepository(MessagingModuleConfiguration.getShared().getContext())));
    }
  }

}
