/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.demo.data

import org.signal.mediakeyboard.data.EmojiCategoryPage
import org.signal.mediakeyboard.data.EmojiKeyboardCategory
import org.signal.mediakeyboard.data.EmojiKeyboardRepository
import org.signal.mediakeyboard.data.KeyboardEmoji

/**
 * An in-memory emoji source with a small but representative data set, including skin tone
 * variations and a keyword search index.
 */
class DemoEmojiKeyboardRepository : EmojiKeyboardRepository {

  private val skinTones = listOf("🏻", "🏼", "🏽", "🏾", "🏿")

  private fun toned(base: String): KeyboardEmoji {
    return KeyboardEmoji(canonical = base, variations = listOf(base) + skinTones.map { base + it })
  }

  private fun plain(emoji: String): KeyboardEmoji = KeyboardEmoji(emoji)

  private val pages: List<EmojiCategoryPage> = listOf(
    EmojiCategoryPage(
      category = EmojiKeyboardCategory.PEOPLE,
      emoji = listOf(
        "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "🙂", "🙃", "😉", "😊", "😇", "🥰", "😍", "🤩",
        "😘", "😋", "😛", "😜", "🤪", "🤑", "🤗", "🤭", "🤫", "🤔", "🤐", "🤨", "😐", "😶", "😏", "😒",
        "🙄", "😬", "😌", "😔", "😪", "🤤", "😴", "😷", "🤒", "🤢", "🥵", "🥶", "🥴", "🤯", "🥳", "😎",
        "🤓", "🧐", "😕", "😟", "🙁", "😮", "😯", "😲", "😳", "🥺", "😨", "😰", "😥", "😢", "😭", "😱",
        "😖", "😞", "😓", "😩", "😫", "🥱", "😤", "😡", "😠", "🤬"
      ).map(::plain) + listOf(
        toned("👍"), toned("👎"), toned("👋"), toned("🙏"), toned("👏"), toned("💪"), toned("🤞"),
        toned("🤘"), toned("👌"), toned("👈"), toned("👉"), toned("👆"), toned("👇"), toned("✋"),
        toned("🤚"), toned("👊"), toned("✊"), toned("🤛"), toned("🤜")
      )
    ),
    EmojiCategoryPage(
      category = EmojiKeyboardCategory.NATURE,
      emoji = listOf(
        "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯", "🦁", "🐮", "🐷", "🐸", "🐵", "🐔",
        "🐧", "🐦", "🐤", "🦆", "🦅", "🦉", "🐺", "🐴", "🦄", "🐝", "🐛", "🦋", "🐌", "🐞", "🐢", "🐍",
        "🐙", "🦀", "🐠", "🐟", "🐬", "🐳", "🦈", "🔥", "🌸", "💐", "🌹", "🌺", "🌻", "🌼", "🌷", "🌱",
        "🌲", "🌳", "🌴", "🌵", "🍀", "🌈", "⭐", "🌙", "☀️", "⛅", "❄️", "💧"
      ).map(::plain)
    ),
    EmojiCategoryPage(
      category = EmojiKeyboardCategory.FOODS,
      emoji = listOf(
        "🍏", "🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🍒", "🍑", "🥭", "🍍", "🥥", "🥝", "🍅",
        "🍆", "🥑", "🥦", "🥒", "🌶️", "🌽", "🥕", "🥔", "🥐", "🍞", "🥖", "🥨", "🧀", "🍳", "🥞", "🧇",
        "🥓", "🍗", "🌭", "🍔", "🍟", "🍕", "🥪", "🌮", "🌯", "🥗", "🍝", "🍜", "🍣", "🍱", "🍤", "🍚",
        "🍦", "🍧", "🍨", "🍩", "🍪", "🎂", "🍰", "🧁", "🍫", "🍬", "🍭", "☕", "🍵", "🥤", "🍺", "🍷"
      ).map(::plain)
    ),
    EmojiCategoryPage(
      category = EmojiKeyboardCategory.ACTIVITY,
      emoji = listOf(
        "⚽", "🏀", "🏈", "⚾", "🥎", "🎾", "🏐", "🏉", "🎱", "🏓", "🏸", "🏒", "🥅", "⛳", "🏹", "🎣",
        "🥊", "🥋", "🛹", "⛸️", "🎿", "🎪", "🎉", "🎊", "🎤", "🎧", "🎼", "🎹", "🥁", "🎷", "🎺", "🎸",
        "🎻", "🎲", "🎯", "🎳", "🎮", "🧩"
      ).map(::plain)
    ),
    EmojiCategoryPage(
      category = EmojiKeyboardCategory.PLACES,
      emoji = listOf(
        "🚗", "🚕", "🚙", "🚌", "🚓", "🚑", "🚒", "🚚", "🚜", "🛴", "🚲", "🛵", "🚨", "🚄", "🚅", "🚂",
        "✈️", "🛫", "🚀", "🛸", "🚁", "⛵", "🚤", "🚢", "⚓", "🗼", "🏰", "🎡", "🎢", "🎠", "⛲", "🏖️",
        "🌋", "🗽", "🏠", "🏢", "🏥", "🏦", "🏫", "⛪"
      ).map(::plain)
    ),
    EmojiCategoryPage(
      category = EmojiKeyboardCategory.OBJECTS,
      emoji = listOf(
        "⌚", "📱", "💻", "⌨️", "🖥️", "🖨️", "📷", "📸", "📹", "🎥", "📞", "☎️", "📺", "📻", "⏰", "⌛",
        "🔋", "🔌", "💡", "🔦", "🕯️", "💸", "💵", "💰", "💳", "💎", "🔧", "🔨", "🛠️", "⚙️", "🔮", "🔭",
        "🔬", "💊", "💉", "🎁", "🎈", "🎀", "📚", "📖", "✏️", "📌"
      ).map(::plain)
    ),
    EmojiCategoryPage(
      category = EmojiKeyboardCategory.SYMBOLS,
      emoji = listOf(
        "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔", "❣️", "💕", "💞", "💓", "💗", "💖",
        "💘", "💝", "☮️", "☯️", "✅", "❌", "❓", "❗", "💯", "♻️", "💤", "🆒", "🆕", "🆓", "🔴", "🟠",
        "🟡", "🟢", "🔵", "🟣", "⚫", "⚪", "💬", "💭"
      ).map(::plain)
    ),
    EmojiCategoryPage(
      category = EmojiKeyboardCategory.FLAGS,
      emoji = listOf(
        "🏁", "🚩", "🎌", "🏴", "🏳️", "🏳️‍🌈", "🏴‍☠️", "🇺🇸", "🇨🇦", "🇬🇧", "🇫🇷", "🇩🇪", "🇮🇹", "🇪🇸", "🇵🇹", "🇧🇷",
        "🇲🇽", "🇯🇵", "🇰🇷", "🇨🇳", "🇮🇳", "🇦🇺", "🇳🇿", "🇿🇦", "🇳🇬", "🇪🇬", "🇸🇪", "🇳🇴", "🇩🇰", "🇫🇮", "🇺🇦", "🇵🇱"
      ).map(::plain)
    ),
    EmojiCategoryPage(
      category = EmojiKeyboardCategory.EMOTICONS,
      emoji = listOf(
        ":-)", ";-)", ":-(", ":-P", ":-D", ":-O", ":-*", "<3", ":-/", ":-|", "^_^", "T_T"
      ).map(::plain)
    )
  )

  private val searchIndex: Map<String, List<String>> = mapOf(
    "smile" to listOf("😀", "😃", "😄", "🙂", "😊"),
    "laugh" to listOf("😂", "🤣", "😆"),
    "happy" to listOf("😀", "😃", "😄", "😁", "🥳"),
    "sad" to listOf("😢", "😭", "🙁", "😞", "💔"),
    "cry" to listOf("😢", "😭"),
    "angry" to listOf("😡", "😠", "🤬"),
    "love" to listOf("❤️", "😍", "🥰", "😘", "💕", "💖"),
    "heart" to listOf("❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "💔", "💕", "💖"),
    "kiss" to listOf("😘", ":-*"),
    "wink" to listOf("😉", ";-)"),
    "cool" to listOf("😎", "🆒"),
    "party" to listOf("🥳", "🎉", "🎊", "🎈"),
    "celebrate" to listOf("🎉", "🎊", "🥳", "🍾"),
    "thumbs up" to listOf("👍"),
    "thumbs down" to listOf("👎"),
    "wave" to listOf("👋"),
    "clap" to listOf("👏"),
    "pray" to listOf("🙏"),
    "strong" to listOf("💪"),
    "fire" to listOf("🔥"),
    "dog" to listOf("🐶"),
    "cat" to listOf("🐱"),
    "fox" to listOf("🦊"),
    "unicorn" to listOf("🦄"),
    "flower" to listOf("🌸", "🌹", "🌺", "🌻", "🌷", "💐"),
    "star" to listOf("⭐", "🤩"),
    "sun" to listOf("☀️"),
    "moon" to listOf("🌙"),
    "rain" to listOf("💧", "🌈"),
    "snow" to listOf("❄️"),
    "pizza" to listOf("🍕"),
    "burger" to listOf("🍔"),
    "cake" to listOf("🎂", "🍰", "🧁"),
    "coffee" to listOf("☕"),
    "beer" to listOf("🍺"),
    "car" to listOf("🚗", "🚕", "🚙"),
    "plane" to listOf("✈️", "🛫"),
    "rocket" to listOf("🚀"),
    "music" to listOf("🎤", "🎧", "🎼", "🎸"),
    "game" to listOf("🎮", "🎲"),
    "ball" to listOf("⚽", "🏀", "🏈", "⚾", "🎾"),
    "money" to listOf("💸", "💵", "💰", "🤑"),
    "phone" to listOf("📱", "📞", "☎️"),
    "check" to listOf("✅"),
    "hundred" to listOf("💯"),
    "sleep" to listOf("😴", "💤"),
    "flag" to listOf("🏁", "🚩", "🏳️", "🏴")
  )

  private val emojiByCanonical: Map<String, KeyboardEmoji> = pages
    .flatMap { it.emoji }
    .associateBy { it.canonical }

  private val recents = ArrayDeque(listOf("😂", "❤️", "👍", "😍", "🔥", "🎉", "😭", "🙏"))
  private val preferredVariations = mutableMapOf<String, String>()

  override suspend fun getEmojiPages(): List<EmojiCategoryPage> = pages

  override suspend fun getRecentEmoji(): List<KeyboardEmoji> {
    return recents.map { emojiByCanonical[it] ?: KeyboardEmoji(it) }
  }

  override suspend fun search(query: String): List<KeyboardEmoji> {
    val normalized = query.trim().lowercase()
    if (normalized.isEmpty()) {
      return emptyList()
    }

    return searchIndex
      .filterKeys { it.contains(normalized) }
      .values
      .flatten()
      .distinct()
      .map { emojiByCanonical[it] ?: KeyboardEmoji(it) }
  }

  override suspend fun getPreferredVariations(): Map<String, String> = preferredVariations.toMap()

  override fun setPreferredVariation(canonical: String, variation: String) {
    if (canonical == variation) {
      preferredVariations.remove(canonical)
    } else {
      preferredVariations[canonical] = variation
    }
  }

  override suspend fun onEmojiUsed(emoji: String) {
    recents.remove(emoji)
    recents.addFirst(emoji)
    while (recents.size > MAX_RECENTS) {
      recents.removeLast()
    }
  }

  companion object {
    private const val MAX_RECENTS = 24
  }
}
