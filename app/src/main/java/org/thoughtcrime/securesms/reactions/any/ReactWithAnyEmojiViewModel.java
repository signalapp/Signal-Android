package org.thoughtcrime.securesms.reactions.any;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.thoughtcrime.securesms.components.emoji.EmojiPageModel;
import org.thoughtcrime.securesms.components.emoji.EmojiPageViewGridAdapter;
import org.thoughtcrime.securesms.components.emoji.RecentEmojiPageModel;
import org.thoughtcrime.securesms.database.model.MessageId;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.emoji.EmojiCategory;
import org.thoughtcrime.securesms.keyboard.emoji.EmojiCategoryMappingModel;
import org.thoughtcrime.securesms.keyboard.emoji.RecentsMappingModel;
import org.thoughtcrime.securesms.keyboard.emoji.search.EmojiSearchRepository;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.reactions.ReactionsRepository;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModelList;

import java.util.List;
import java.util.stream.Collectors;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;

public final class ReactWithAnyEmojiViewModel extends ViewModel {

  private static final int SEARCH_LIMIT = 40;

  private final ReactWithAnyEmojiRepository repository;
  private final long                        messageId;
  private final boolean                     isMms;
  private final EmojiSearchRepository       emojiSearchRepository;

  private final Observable<MappingModelList>       categories;
  private final Observable<MappingModelList>       emojiList;
  private final BehaviorSubject<EmojiSearchResult> searchResults;
  private final BehaviorSubject<String>            selectedKey;

  private ReactWithAnyEmojiViewModel(@NonNull ReactWithAnyEmojiRepository repository,
                                     long messageId,
                                     boolean isMms,
                                     @NonNull EmojiSearchRepository emojiSearchRepository)
  {
    this.repository            = repository;
    this.messageId             = messageId;
    this.isMms                 = isMms;
    this.emojiSearchRepository = emojiSearchRepository;
    this.searchResults         = BehaviorSubject.createDefault(new EmojiSearchResult());
    this.selectedKey           = BehaviorSubject.createDefault(getStartingKey());

    Observable<List<ReactWithAnyEmojiPage>> emojiPages = new ReactionsRepository().getReactions(new MessageId(messageId, isMms))
                                                                                  .map(repository::getEmojiPageModels);

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

    this.categories = Observable.combineLatest(emojiPages, this.selectedKey.distinctUntilChanged(), (pages, selectedKey) -> {
      MappingModelList list = new MappingModelList();
      list.add(new RecentsMappingModel(RecentEmojiPageModel.KEY.equals(selectedKey)));
      list.addAll(pages.stream()
                       .filter(p -> !RecentEmojiPageModel.KEY.equals(p.getKey()))
                       .map(p -> {
                         EmojiCategory category = EmojiCategory.forKey(p.getKey());
                         return new EmojiCategoryMappingModel(category, category.getKey().equals(selectedKey));
                       })
                       .collect(Collectors.toList()));
      return list;
    });

    this.emojiList = Observable.combineLatest(emojiList, searchResults.distinctUntilChanged(), (all, search) -> {
      if (TextUtils.isEmpty(search.query)) {
        return all;
      } else {
        if (search.model.getDisplayEmoji().isEmpty()) {
          return MappingModelList.singleton(new EmojiPageViewGridAdapter.EmojiNoResultsModel());
        }
        return toMappingModels(search.model);
      }
    });
  }

  @NonNull Observable<MappingModelList> getCategories() {
    return categories.observeOn(AndroidSchedulers.mainThread());
  }

  @NonNull Observable<String> getSelectedKey() {
    return selectedKey.observeOn(AndroidSchedulers.mainThread());
  }

  @NonNull Observable<MappingModelList> getEmojiList() {
    return emojiList.observeOn(AndroidSchedulers.mainThread());
  }

  void onEmojiSelected(@NonNull String emoji) {
    if (messageId > 0) {
      SignalStore.emojiValues().setPreferredVariation(emoji);
      repository.addEmojiToMessage(emoji, new MessageId(messageId, isMms));
    }
  }

  public void onQueryChanged(String query) {
    emojiSearchRepository.submitQuery(query, false, SEARCH_LIMIT, m -> searchResults.onNext(new EmojiSearchResult(query, m)));
  }

  public void selectPage(@NonNull String key) {
    selectedKey.onNext(key);
  }

  private static @NonNull MappingModelList toMappingModels(@NonNull EmojiPageModel model) {
    return model.getDisplayEmoji()
                .stream()
                .map(e -> new EmojiPageViewGridAdapter.EmojiModel(model.getKey(), e))
                .collect(MappingModelList.collect());
  }

  private static @NonNull String getStartingKey() {
    if (RecentEmojiPageModel.hasRecents(ApplicationDependencies.getApplication(), TextSecurePreferences.RECENT_STORAGE_KEY)) {
      return RecentEmojiPageModel.KEY;
    } else {
      return EmojiCategory.PEOPLE.getKey();
    }
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
      return modelClass.cast(new ReactWithAnyEmojiViewModel(repository, messageId, isMms, new EmojiSearchRepository(ApplicationDependencies.getApplication())));
    }
  }

}
