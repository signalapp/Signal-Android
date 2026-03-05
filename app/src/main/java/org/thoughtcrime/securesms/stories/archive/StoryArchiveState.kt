package org.thoughtcrime.securesms.stories.archive

import android.net.Uri
import org.signal.blurhash.BlurHash
import org.thoughtcrime.securesms.database.model.StoryType

data class StoryArchiveState(
  val stories: List<ArchivedStoryItem?> = emptyList(),
  val sortOrder: SortOrder = SortOrder.NEWEST,
  val isLoading: Boolean = true,
  val multiSelectEnabled: Boolean = false,
  val selectedIds: Set<Long> = emptySet(),
  val showDeleteConfirmation: Boolean = false
)

enum class SortOrder { NEWEST, OLDEST }

data class ArchivedStoryItem(
  val messageId: Long,
  val dateSent: Long,
  val thumbnailUri: Uri?,
  val blurHash: BlurHash?,
  val storyType: StoryType,
  val body: String?
)
