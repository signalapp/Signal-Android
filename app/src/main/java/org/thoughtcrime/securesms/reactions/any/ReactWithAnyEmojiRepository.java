package org.thoughtcrime.securesms.reactions.any;

import android.content.Context;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.components.emoji.EmojiPageModel;
import org.thoughtcrime.securesms.components.emoji.EmojiUtil;
import org.thoughtcrime.securesms.components.emoji.RecentEmojiPageModel;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;

import java.util.LinkedList;
import java.util.List;

final class ReactWithAnyEmojiRepository {

  private final Context              context;
  private final RecentEmojiPageModel recentEmojiPageModel;
  private final List<EmojiPageModel> emojiPageModels;

  ReactWithAnyEmojiRepository(@NonNull Context context) {
    this.context              = context;
    this.recentEmojiPageModel = new RecentEmojiPageModel(context);
    this.emojiPageModels      = new LinkedList<>();

    emojiPageModels.add(recentEmojiPageModel);
    emojiPageModels.addAll(EmojiUtil.getDisplayPages());
    emojiPageModels.remove(emojiPageModels.size() - 1);
  }

  List<EmojiPageModel> getEmojiPageModels() {
    return emojiPageModels;
  }

  void addEmojiToMessage(@NonNull String emoji, long messageId, boolean isMms) {
    recentEmojiPageModel.onCodePointSelected(emoji);

    SignalExecutors.BOUNDED.execute(() -> MessageSender.sendNewReaction(context, messageId, isMms, emoji));
  }
}
