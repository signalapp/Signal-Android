package org.thoughtcrime.securesms.reactions;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.annimon.stream.Stream;

import java.util.List;
import java.util.Map;

public class ReactionsViewModel extends ViewModel {

  private final Repository              repository;

  public ReactionsViewModel(@NonNull Repository repository) {
    this.repository = repository;
  }

  public @NonNull LiveData<List<EmojiCount>> getEmojiCounts() {
    return Transformations.map(repository.getReactions(),
                               reactionList -> {
                                 List<EmojiCount> emojiCounts = Stream.of(reactionList)
                                                                      .groupBy(ReactionDetails::getBaseEmoji)
                                                                      .sorted(this::compareReactions)
                                                                      .map(entry -> new EmojiCount(entry.getKey(),
                                                                                                   getCountDisplayEmoji(entry.getValue()),
                                                                                                   entry.getValue()))
                                                                      .toList();

                                 emojiCounts.add(0, EmojiCount.all(reactionList));

                                 return emojiCounts;
                               });
  }

  private int compareReactions(@NonNull Map.Entry<String, List<ReactionDetails>> lhs, @NonNull Map.Entry<String, List<ReactionDetails>> rhs) {
    int lengthComparison = -Integer.compare(lhs.getValue().size(), rhs.getValue().size());
    if (lengthComparison != 0) return lengthComparison;

    long latestTimestampLhs = getLatestTimestamp(lhs.getValue());
    long latestTimestampRhs = getLatestTimestamp(rhs.getValue());

    return -Long.compare(latestTimestampLhs, latestTimestampRhs);
  }

  private long getLatestTimestamp(List<ReactionDetails> reactions) {
    return Stream.of(reactions)
                 .max((a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()))
                 .map(ReactionDetails::getTimestamp)
                 .orElse(-1L);
  }

  private @NonNull String getCountDisplayEmoji(@NonNull List<ReactionDetails> reactions) {
    for (ReactionDetails reaction : reactions) {
      if (reaction.getSender().isLocalNumber()) {
        return reaction.getDisplayEmoji();
      }
    }

    return reactions.get(reactions.size() - 1).getDisplayEmoji();
  }

  interface Repository {
    LiveData<List<ReactionDetails>>  getReactions();
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
