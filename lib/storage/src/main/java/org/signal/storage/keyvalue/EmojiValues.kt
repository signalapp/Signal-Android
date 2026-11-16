package org.signal.storage.keyvalue

class EmojiValues(store: KeyValueStore) : SignalStoreValues(store) {

  companion object {
    @JvmField
    val DEFAULT_REACTIONS_LIST: List<String> = listOf(
      "❤️",
      "👍",
      "👎",
      "😂",
      "😮",
      "😢"
    )

    private const val NEXT_SCHEDULED_CHECK = "emojiPref__next_scheduled_check"
    private const val REACTIONS_LIST = "emojiPref__reactions_list"
    private const val SEARCH_VERSION = "emojiPref__search_version"
    private const val SEARCH_LANGUAGE = "emojiPref__search_language"
    private const val LAST_SEARCH_CHECK = "emojiPref__last_search_check"
    private const val JUMBO_EMOJI_DOWNLOAD = "emojiPref__jumbo_emoji_v"

    const val NO_LANGUAGE: String = "NO_LANGUAGE"
  }

  public override fun onFirstEverAppLaunch() {
    putInteger(SEARCH_VERSION, 0)
  }

  override val keysToIncludeInBackup: List<String>
    get() = listOf(REACTIONS_LIST)

  var nextScheduledImageCheck: Long by longValue(NEXT_SCHEDULED_CHECK, 0)

  /** Records [variation] as the preferred variation of the emoji whose canonical representation is [canonical]. */
  fun setPreferredVariation(canonical: String, variation: String) {
    if (canonical == variation) {
      store.beginWrite().remove("emojiPref__$canonical").apply()
    } else {
      putString("emojiPref__$canonical", variation)
    }
  }

  /** The preferred variation of the emoji whose canonical representation is [canonical], or [default] if none was set. */
  fun getPreferredVariation(canonical: String, default: String): String {
    return getString("emojiPref__$canonical", default)!!
  }

  fun removePreferredVariation(canonical: String) {
    store.beginWrite().remove("emojiPref__$canonical").apply()
  }

  /**
   * A raw list of the default reactions the user has selected. It will be empty if there hasn't been any custom ones set. It may contain unrenderable emoji.
   */
  var rawReactions: List<String>
    get() {
      val list = getString(REACTIONS_LIST, "")
      return if (list.isNullOrEmpty()) {
        emptyList()
      } else {
        list.split(",").dropLastWhile { it.isEmpty() }
      }
    }
    set(value) = putString(REACTIONS_LIST, value.joinToString(","))

  fun onSearchIndexUpdated(version: Int, language: String) {
    store.beginWrite()
      .putInteger(SEARCH_VERSION, version)
      .putString(SEARCH_LANGUAGE, language)
      .apply()
  }

  fun hasSearchIndex(): Boolean {
    return searchVersion > 0 && searchLanguage != null
  }

  val searchVersion: Int by integerValue(SEARCH_VERSION, 0)

  fun clearSearchIndexMetadata() {
    store.beginWrite()
      .remove(SEARCH_VERSION)
      .remove(SEARCH_LANGUAGE)
      .remove(LAST_SEARCH_CHECK)
      .apply()
  }

  fun clearSearchIndexVersion() {
    store.beginWrite().remove(SEARCH_VERSION).apply()
  }

  val searchLanguage: String? by stringValue(SEARCH_LANGUAGE, null)

  var lastSearchIndexCheck: Long by longValue(LAST_SEARCH_CHECK, 0)

  fun addJumboEmojiSheet(version: Int, sheet: String) {
    val sheets = getJumboEmojiSheets(version)
    sheets.add(sheet)
    store.beginWrite()
      .putString(JUMBO_EMOJI_DOWNLOAD + version, sheets.joinToString(","))
      .apply()
  }

  fun getJumboEmojiSheets(version: Int): HashSet<String> {
    return HashSet(store.getString(JUMBO_EMOJI_DOWNLOAD + version, "")!!.split(","))
  }

  fun clearJumboEmojiSheets(version: Int) {
    store.beginWrite()
      .remove(JUMBO_EMOJI_DOWNLOAD + version)
      .apply()
  }
}
