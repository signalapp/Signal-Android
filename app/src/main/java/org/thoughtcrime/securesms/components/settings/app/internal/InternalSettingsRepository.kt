package org.thoughtcrime.securesms.components.settings.app.internal

import android.content.Context
import org.signal.core.util.concurrent.SignalExecutors
import org.thoughtcrime.securesms.emoji.EmojiFiles

class InternalSettingsRepository(context: Context) {

  private val context = context.applicationContext

  fun getEmojiVersionInfo(consumer: (EmojiFiles.Version?) -> Unit) {
    SignalExecutors.BOUNDED.execute {
      consumer(EmojiFiles.Version.readVersion(context))
    }
  }
}
