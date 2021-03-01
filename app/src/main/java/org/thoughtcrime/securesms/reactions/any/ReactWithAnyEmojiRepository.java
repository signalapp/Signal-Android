package org.thoughtcrime.securesms.reactions.any;

import android.content.Context;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.annimon.stream.Stream;

import org.signal.core.util.ThreadUtil;
import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.emoji.EmojiUtil;
import org.thoughtcrime.securesms.components.emoji.RecentEmojiPageModel;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MessageDatabase;
import org.thoughtcrime.securesms.database.NoSuchMessageException;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.ReactionRecord;
import org.thoughtcrime.securesms.reactions.ReactionDetails;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.sms.MessageSender;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

final class ReactWithAnyEmojiRepository {

  private static final String TAG = Log.tag(ReactWithAnyEmojiRepository.class);

  private final Context                     context;
  private final RecentEmojiPageModel        recentEmojiPageModel;
  private final List<ReactWithAnyEmojiPage> emojiPages;

  ReactWithAnyEmojiRepository(@NonNull Context context, @NonNull String storageKey) {
    this.context              = context;
    this.recentEmojiPageModel = new RecentEmojiPageModel(context, storageKey);
    this.emojiPages           = new LinkedList<>();

    emojiPages.addAll(Stream.of(EmojiUtil.getDisplayPages())
                            .map(page -> new ReactWithAnyEmojiPage(Collections.singletonList(new ReactWithAnyEmojiPageBlock(getCategoryLabel(page.getIconAttr()), page))))
                            .toList());
    emojiPages.remove(emojiPages.size() - 1);
  }

  List<ReactWithAnyEmojiPage> getEmojiPageModels(@NonNull List<ReactionDetails> thisMessagesReactions) {
    List<ReactWithAnyEmojiPage> pages       = new LinkedList<>();
    List<String>                thisMessage = Stream.of(thisMessagesReactions)
                                                    .map(ReactionDetails::getDisplayEmoji)
                                                    .distinct()
                                                    .toList();

    if (thisMessage.isEmpty()) {
      pages.add(new ReactWithAnyEmojiPage(Collections.singletonList(new ReactWithAnyEmojiPageBlock(R.string.ReactWithAnyEmojiBottomSheetDialogFragment__recently_used, recentEmojiPageModel))));
    } else {
      pages.add(new ReactWithAnyEmojiPage(Arrays.asList(new ReactWithAnyEmojiPageBlock(R.string.ReactWithAnyEmojiBottomSheetDialogFragment__this_message, new ThisMessageEmojiPageModel(thisMessage)),
                                                        new ReactWithAnyEmojiPageBlock(R.string.ReactWithAnyEmojiBottomSheetDialogFragment__recently_used, recentEmojiPageModel))));
    }

    pages.addAll(emojiPages);

    return pages;
  }

  void addEmojiToMessage(@NonNull String emoji, long messageId, boolean isMms) {
    SignalExecutors.BOUNDED.execute(() -> {
      try {
        MessageDatabase db              = isMms ? DatabaseFactory.getMmsDatabase(context) : DatabaseFactory.getSmsDatabase(context);
        MessageRecord     messageRecord = db.getMessageRecord(messageId);
        ReactionRecord    oldRecord     = Stream.of(messageRecord.getReactions())
                                                .filter(record -> record.getAuthor().equals(Recipient.self().getId()))
                                                .findFirst()
                                                .orElse(null);

        if (oldRecord != null && oldRecord.getEmoji().equals(emoji)) {
          MessageSender.sendReactionRemoval(context, messageRecord.getId(), messageRecord.isMms(), oldRecord);
        } else {
          MessageSender.sendNewReaction(context, messageRecord.getId(), messageRecord.isMms(), emoji);
          ThreadUtil.runOnMain(() -> recentEmojiPageModel.onCodePointSelected(emoji));
        }
      } catch (NoSuchMessageException e) {
        Log.w(TAG, "Message not found! Ignoring.");
      }
    });
  }

  private @StringRes int getCategoryLabel(@AttrRes int iconAttr) {
    switch (iconAttr) {
      case R.attr.emoji_category_people:
        return R.string.ReactWithAnyEmojiBottomSheetDialogFragment__smileys_and_people;
      case R.attr.emoji_category_nature:
        return R.string.ReactWithAnyEmojiBottomSheetDialogFragment__nature;
      case R.attr.emoji_category_foods:
        return R.string.ReactWithAnyEmojiBottomSheetDialogFragment__food;
      case R.attr.emoji_category_activity:
        return R.string.ReactWithAnyEmojiBottomSheetDialogFragment__activities;
      case R.attr.emoji_category_places:
        return R.string.ReactWithAnyEmojiBottomSheetDialogFragment__places;
      case R.attr.emoji_category_objects:
        return R.string.ReactWithAnyEmojiBottomSheetDialogFragment__objects;
      case R.attr.emoji_category_symbols:
        return R.string.ReactWithAnyEmojiBottomSheetDialogFragment__symbols;
      case R.attr.emoji_category_flags:
        return R.string.ReactWithAnyEmojiBottomSheetDialogFragment__flags;
      case R.attr.emoji_category_emoticons:
        return R.string.ReactWithAnyEmojiBottomSheetDialogFragment__emoticons;
      default:
        throw new AssertionError();
    }
  }
}
