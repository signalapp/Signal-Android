package org.thoughtcrime.securesms.keyboard.sticker

import androidx.annotation.WorkerThread
import org.signal.core.util.SqlUtil.buildCaseInsensitiveGlobPattern
import org.thoughtcrime.securesms.components.emoji.EmojiUtil
import org.thoughtcrime.securesms.database.EmojiSearchTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.StickerTable
import org.thoughtcrime.securesms.database.StickerTable.StickerPackRecordReader
import org.thoughtcrime.securesms.database.StickerTable.StickerRecordReader
import org.thoughtcrime.securesms.database.model.StickerPackRecord
import org.thoughtcrime.securesms.database.model.StickerRecord

private const val RECENT_LIMIT = 24
private const val EMOJI_SEARCH_RESULTS_LIMIT = 20

class StickerSearchRepository {

  private val emojiSearchTable: EmojiSearchTable = SignalDatabase.emojiSearch
  private val stickerTable: StickerTable = SignalDatabase.stickers

  @WorkerThread
  fun search(query: String): List<StickerRecord> {
    if (query.isEmpty()) {
      return StickerRecordReader(stickerTable.getRecentlyUsedStickers(RECENT_LIMIT)).readAll()
    }

    val maybeEmojiQuery: List<StickerRecord> = findStickersForEmoji(query)
    val searchResults: List<StickerRecord> =
      // Match by title first, then by emoji.
      StickerRecordReader(stickerTable.getStickerPacksByTitle(query)).readAll()
        .plus(emojiSearchTable.query(query, EMOJI_SEARCH_RESULTS_LIMIT)
          .map(::findStickersForEmoji)
          .flatten())

    return maybeEmojiQuery + searchResults
  }

  @WorkerThread
  private fun findStickersForEmoji(emoji: String): List<StickerRecord> {
    val searchEmoji: String = EmojiUtil.getCanonicalRepresentation(emoji)

    return EmojiUtil.getAllRepresentations(searchEmoji)
      .filterNotNull()
      .map { candidate -> StickerRecordReader(stickerTable.getStickersByEmoji(candidate)).readAll() }
      .flatten()
  }
}

private fun StickerRecordReader.readAll(): List<StickerRecord> {
  val stickers: MutableList<StickerRecord> = mutableListOf()
  use { reader ->
    var record: StickerRecord? = reader.next
    while (record != null) {
      stickers.add(record)
      record = reader.next
    }
  }
  return stickers
}

private fun StickerPackRecordReader.readAll(): List<StickerPackRecord> {
  val stickers: MutableList<StickerPackRecord> = mutableListOf()
  use { reader ->
    var record: StickerPackRecord? = reader.next
    while (record != null) {
      stickers.add(record)
      record = reader.next
    }
  }
  return stickers
}
