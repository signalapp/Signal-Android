package org.thoughtcrime.securesms.conversation.v2.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Cache used to store the progress of both 1:1 and group avatar downloads
 */
object AvatarDownloadStateCache {

  private val TAG = Log.tag(AvatarDownloadStateCache::class.java)

  private val cache = HashMap<RecipientId, MutableStateFlow<DownloadState>>(100)

  @JvmStatic
  fun set(recipient: Recipient, downloadState: DownloadState) {
    if (cache[recipient.id] == null) {
      cache[recipient.id] = MutableStateFlow(downloadState)
    } else {
      cache[recipient.id]!!.update { downloadState }
    }
  }

  @JvmStatic
  fun getDownloadState(recipient: Recipient): DownloadState {
    return cache[recipient.id]?.value ?: DownloadState.NONE
  }

  @JvmStatic
  fun forRecipient(id: RecipientId): StateFlow<DownloadState> {
    if (cache[id] == null) {
      cache[id] = MutableStateFlow(DownloadState.NONE)
    }

    return cache[id]!!.asStateFlow()
  }

  enum class DownloadState {
    NONE,
    IN_PROGRESS,
    FINISHED,
    FAILED
  }
}
