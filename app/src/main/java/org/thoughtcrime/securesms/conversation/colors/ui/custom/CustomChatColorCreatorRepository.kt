package org.thoughtcrime.securesms.conversation.colors.ui.custom

import android.content.Context
import org.signal.core.util.concurrent.SignalExecutors
import org.thoughtcrime.securesms.conversation.colors.ChatColors
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.wallpaper.ChatWallpaper

class CustomChatColorCreatorRepository(private val context: Context) {
  fun loadColors(chatColorsId: ChatColors.Id, consumer: (ChatColors) -> Unit) {
    SignalExecutors.BOUNDED.execute {
      val chatColorsDatabase = DatabaseFactory.getChatColorsDatabase(context)
      val chatColors = chatColorsDatabase.getById(chatColorsId)

      consumer(chatColors)
    }
  }

  fun getWallpaper(recipientId: RecipientId?, consumer: (ChatWallpaper?) -> Unit) {
    SignalExecutors.BOUNDED.execute {
      if (recipientId != null) {
        val recipient = Recipient.resolved(recipientId)
        consumer(recipient.wallpaper)
      } else {
        consumer(SignalStore.wallpaper().wallpaper)
      }
    }
  }

  fun setChatColors(chatColors: ChatColors, consumer: (ChatColors) -> Unit) {
    SignalExecutors.BOUNDED.execute {
      val chatColorsDatabase = DatabaseFactory.getChatColorsDatabase(context)
      val savedColors = chatColorsDatabase.saveChatColors(chatColors)

      consumer(savedColors)
    }
  }

  fun getUsageCount(chatColorsId: ChatColors.Id, consumer: (Int) -> Unit) {
    SignalExecutors.BOUNDED.execute {
      val recipientsDatabase = DatabaseFactory.getRecipientDatabase(context)

      consumer(recipientsDatabase.getColorUsageCount(chatColorsId))
    }
  }
}
