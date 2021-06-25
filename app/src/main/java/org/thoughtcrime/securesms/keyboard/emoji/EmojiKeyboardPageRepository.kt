package org.thoughtcrime.securesms.keyboard.emoji

import android.content.Context
import org.signal.core.util.concurrent.SignalExecutors
import org.thoughtcrime.securesms.components.emoji.EmojiKeyboardProvider
import org.thoughtcrime.securesms.components.emoji.EmojiPageModel
import org.thoughtcrime.securesms.components.emoji.RecentEmojiPageModel
import org.thoughtcrime.securesms.emoji.EmojiSource.Companion.latest
import java.util.function.Consumer

class EmojiKeyboardPageRepository(context: Context) {

  private val recentEmojiPageModel: RecentEmojiPageModel = RecentEmojiPageModel(context, EmojiKeyboardProvider.RECENT_STORAGE_KEY)

  fun getEmoji(consumer: Consumer<List<EmojiPageModel>>) {
    SignalExecutors.BOUNDED.execute {
      val list = mutableListOf<EmojiPageModel>()
      list += recentEmojiPageModel
      list += latest.displayPages
      consumer.accept(list)
    }
  }
}
