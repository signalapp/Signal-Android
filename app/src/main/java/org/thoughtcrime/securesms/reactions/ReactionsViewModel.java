package org.thoughtcrime.securesms.reactions;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.annimon.stream.Stream;

import java.util.List;
import java.util.Map;

import static org.thoughtcrime.securesms.reactions.ReactionsLoader.*;

public class ReactionsViewModel extends ViewModel {

  private final Repository              repository;
  private final MutableLiveData<String> filterEmoji = new MutableLiveData<>();

  public ReactionsViewModel(@NonNull Repository repository) {
    this.repository = repository;
  }

  public @NonNull LiveData<List<Reaction>> getRecipients() {
    return Transformations.switchMap(filterEmoji,
                                     emoji -> Transformations.map(repository.getReactions(),
                                                                  reactions -> Stream.of(reactions)
                                                                                     .filter(reaction -> emoji == null || reaction.getEmoji().equals(emoji))
                                                                                     .toList()));
  }

  public @NonNull LiveData<List<EmojiCount>> getEmojiCounts() {
    return Transformations.map(repository.getReactions(),
                               reactionList -> Stream.of(reactionList)
                                                     .groupBy(Reaction::getEmoji)
                                                     .sorted(this::compareReactions)
                                                     .map(entry -> new EmojiCount(entry.getKey(), entry.getValue().size()))
                                                     .toList());
  }

  public void setFilterEmoji(String filterEmoji) {
    this.filterEmoji.setValue(filterEmoji);
  }

  private int compareReactions(@NonNull Map.Entry<String, List<Reaction>> lhs, @NonNull Map.Entry<String, List<Reaction>> rhs) {
    int lengthComparison = -Integer.compare(lhs.getValue().size(), rhs.getValue().size());
    if (lengthComparison != 0) return lengthComparison;

    long latestTimestampLhs = getLatestTimestamp(lhs.getValue());
    long latestTimestampRhs = getLatestTimestamp(rhs.getValue());

    return -Long.compare(latestTimestampLhs, latestTimestampRhs);
  }

  private long getLatestTimestamp(List<Reaction> reactions) {
    return Stream.of(reactions)
                 .max((a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()))
                 .map(Reaction::getTimestamp)
                 .orElse(-1L);
  }

  interface Repository {
    LiveData<List<Reaction>>  getReactions();
  }

  static final class Factory implements ViewModelProvider.Factory {

    private final Repository repository;

    Factory(@NonNull Repository repository) {
      this.repository = repository;
    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      return (T) new ReactionsViewModel(repository);
    }
  }
}
