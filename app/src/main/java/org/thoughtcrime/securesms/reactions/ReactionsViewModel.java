package org.thoughtcrime.securesms.reactions;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.database.model.MessageId;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;

public class ReactionsViewModel extends ViewModel {

  private final MessageId           messageId;
  private final ReactionsRepository repository;

  public ReactionsViewModel(@NonNull ReactionsRepository reactionRepository, @NonNull MessageId messageId) {
    this.messageId  = messageId;
    this.repository = reactionRepository;
  }

  public @NonNull Observable<List<EmojiCount>> getEmojiCounts() {
    return repository.getReactions(messageId)
                     .map(reactionList -> {
                       List<EmojiCount> emojiCounts = Stream.of(reactionList)
                                                            .groupBy(ReactionDetails::getBaseEmoji)
                                                            .sorted(this::compareReactions)
                                                            .map(entry -> new EmojiCount(entry.getKey(),
                                                                                         getCountDisplayEmoji(entry.getValue()),
                                                                                         entry.getValue()))
                                                            .toList();

                       emojiCounts.add(0, EmojiCount.all(reactionList));

                       return emojiCounts;
                     })
                     .observeOn(AndroidSchedulers.mainThread());
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
                 .max(Comparator.comparingLong(ReactionDetails::getTimestamp))
                 .map(ReactionDetails::getTimestamp)
                 .orElse(-1L);
  }

  private @NonNull String getCountDisplayEmoji(@NonNull List<ReactionDetails> reactions) {
    for (ReactionDetails reaction : reactions) {
      if (reaction.getSender().isSelf()) {
        return reaction.getDisplayEmoji();
      }
    }

    return reactions.get(reactions.size() - 1).getDisplayEmoji();
  }

  void removeReactionEmoji() {
    repository.sendReactionRemoval(messageId);
  }

  static final class Factory implements ViewModelProvider.Factory {

    private final ReactionsRepository reactionsRepository;
    private final MessageId           messageId;

    Factory(@NonNull ReactionsRepository reactionsRepository, @NonNull MessageId messageId) {
      this.reactionsRepository = reactionsRepository;
      this.messageId           = messageId;
    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      return modelClass.cast(new ReactionsViewModel(reactionsRepository, messageId));
    }
  }
}
