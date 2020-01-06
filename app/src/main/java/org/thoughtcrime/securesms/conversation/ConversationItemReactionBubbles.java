package org.thoughtcrime.securesms.conversation;

import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.emoji.EmojiImageView;
import org.thoughtcrime.securesms.database.model.ReactionRecord;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.ThemeUtil;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class ConversationItemReactionBubbles {

  private final ViewGroup      reactionsContainer;
  private final EmojiImageView primaryEmojiReaction;
  private final EmojiImageView secondaryEmojiReaction;

  ConversationItemReactionBubbles(@NonNull ViewGroup reactionsContainer) {
    this.reactionsContainer     = reactionsContainer;
    this.primaryEmojiReaction   = reactionsContainer.findViewById(R.id.reactions_bubbles_primary);
    this.secondaryEmojiReaction = reactionsContainer.findViewById(R.id.reactions_bubbles_secondary);
  }

  void setReactions(@NonNull List<ReactionRecord> reactions) {
    if (reactions.size() == 0) {
      hideAllReactions();
      return;
    }

    final Collection<ReactionInfo> reactionInfos = getReactionInfos(reactions);

    if (reactionInfos.size() == 1) {
      displaySingleReaction(reactionInfos.iterator().next());
    } else {
      displayMultipleReactions(reactionInfos);
    }
  }

  private static @NonNull Collection<ReactionInfo> getReactionInfos(@NonNull List<ReactionRecord> reactions) {
    final Map<String, ReactionInfo> counters = new HashMap<>();

    for (ReactionRecord reaction : reactions) {

      ReactionInfo info = counters.get(reaction.getEmoji());
      if (info == null) {
        info = new ReactionInfo(reaction.getEmoji(),
                                1,
                                reaction.getDateReceived(),
                                Recipient.self().getId().equals(reaction.getAuthor()));
      } else {
        info = new ReactionInfo(reaction.getEmoji(),
                                info.count + 1,
                                Math.max(info.lastSeen, reaction.getDateReceived()),
                                info.userWasSender || Recipient.self().getId().equals(reaction.getAuthor()));
      }

      counters.put(reaction.getEmoji(), info);
    }

    return counters.values();
  }

  private void hideAllReactions() {
    reactionsContainer.setVisibility(View.GONE);
  }

  private void displaySingleReaction(@NonNull ReactionInfo reactionInfo) {
    reactionsContainer.setVisibility(View.VISIBLE);
    primaryEmojiReaction.setVisibility(View.VISIBLE);
    secondaryEmojiReaction.setVisibility(View.GONE);

    primaryEmojiReaction.setImageEmoji(reactionInfo.emoji);
    primaryEmojiReaction.setBackground(getBackgroundDrawableForReactionBubble(reactionInfo));
  }

  private void displayMultipleReactions(@NonNull Collection<ReactionInfo> reactionInfos) {
    reactionsContainer.setVisibility(View.VISIBLE);
    primaryEmojiReaction.setVisibility(View.VISIBLE);
    secondaryEmojiReaction.setVisibility(View.VISIBLE);

    Pair<ReactionInfo, ReactionInfo> primaryAndSecondaryReactions = getPrimaryAndSecondaryReactions(reactionInfos);
    primaryEmojiReaction.setImageEmoji(primaryAndSecondaryReactions.first.emoji);
    primaryEmojiReaction.setBackground(getBackgroundDrawableForReactionBubble(primaryAndSecondaryReactions.first));
    secondaryEmojiReaction.setImageEmoji(primaryAndSecondaryReactions.second.emoji);
    secondaryEmojiReaction.setBackground(getBackgroundDrawableForReactionBubble(primaryAndSecondaryReactions.second));
  }

  private Drawable getBackgroundDrawableForReactionBubble(@NonNull ReactionInfo reactionInfo) {
    return ThemeUtil.getThemedDrawable(reactionsContainer.getContext(),
                                       reactionInfo.userWasSender ? R.attr.reactions_sent_background : R.attr.reactions_recv_background);
  }

  private Pair<ReactionInfo, ReactionInfo> getPrimaryAndSecondaryReactions(@NonNull Collection<ReactionInfo> reactionInfos) {
    ReactionInfo mostPopular          = null;
    ReactionInfo latestReaction       = null;
    ReactionInfo secondLatestReaction = null;
    ReactionInfo ourReaction          = null;

    for (ReactionInfo current : reactionInfos) {
      if (current.userWasSender) {
        ourReaction = current;
      }

      if (mostPopular == null) {
        mostPopular = current;
      } else if (mostPopular.count < current.count) {
        mostPopular = current;
      }

      if (latestReaction == null) {
        latestReaction = current;
      } else if (latestReaction.lastSeen < current.lastSeen) {

        if (current.count == mostPopular.count) {
          mostPopular = current;
        }

        secondLatestReaction = latestReaction;
        latestReaction       = current;
      } else if (secondLatestReaction == null) {
        secondLatestReaction = current;
      }
    }

    if (mostPopular == null) {
      throw new AssertionError("getPrimaryAndSecondaryReactions was called with an empty list.");
    }

    if (ourReaction != null && !mostPopular.equals(ourReaction)) {
      return Pair.create(mostPopular, ourReaction);
    } else {
      return Pair.create(mostPopular, mostPopular.equals(latestReaction) ? secondLatestReaction : latestReaction);
    }
  }

  private static class ReactionInfo {
    private final String  emoji;
    private final int     count;
    private final long    lastSeen;
    private final boolean userWasSender;

    private ReactionInfo(@NonNull String emoji, int count, long lastSeen, boolean userWasSender) {
      this.emoji         = emoji;
      this.count         = count;
      this.lastSeen      = lastSeen;
      this.userWasSender = userWasSender;
    }

    @Override
    public int hashCode() {
      return Objects.hash(emoji, count, lastSeen, userWasSender);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (!(obj instanceof ReactionInfo)) return false;

      ReactionInfo other = (ReactionInfo) obj;

      return other.emoji.equals(emoji)       &&
             other.count         == count    &&
             other.lastSeen      == lastSeen &&
             other.userWasSender == userWasSender;
    }
  }
}
