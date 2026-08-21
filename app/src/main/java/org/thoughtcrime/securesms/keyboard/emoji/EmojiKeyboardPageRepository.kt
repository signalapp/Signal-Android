package org.thoughtcrime.securesms.keyboard.emoji

import android.content.Context
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.emoji.EmojiPageModel
import org.signal.emoji.EmojiSource.Companion.latest
import org.thoughtcrime.securesms.components.emoji.RecentEmojiPageModel
import org.thoughtcrime.securesms.util.TextSecurePreferences
import java.util.function.Consumer

class EmojiKeyboardPageRepository(private val context: Context) {
  fun getEmoji(consumer: Consumer<List<EmojiPageModel>>) {
    SignalExecutors.BOUNDED.execute {
      val list = mutableListOf<EmojiPageModel>()
      list += RecentEmojiPageModel(context, TextSecurePreferences.RECENT_STORAGE_KEY)
      list += latest.displayPages
      consumer.accept(list)
    }
  }
}
