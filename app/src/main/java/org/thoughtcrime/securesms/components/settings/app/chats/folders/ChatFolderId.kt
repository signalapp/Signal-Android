package org.thoughtcrime.securesms.components.settings.app.chats.folders

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.whispersystems.signalservice.api.util.UuidUtil
import java.util.UUID

/**
 * UUID wrapper class for chat folders, used in storage service
 */
@Parcelize
data class ChatFolderId(val uuid: UUID) : Parcelable {

  companion object {
    fun from(id: String): ChatFolderId {
      return ChatFolderId(UuidUtil.parseOrThrow(id))
    }

    fun from(uuid: UUID): ChatFolderId {
      return ChatFolderId(uuid)
    }

    fun generate(): ChatFolderId {
      return ChatFolderId(UUID.randomUUID())
    }
  }

  override fun toString(): String {
    return uuid.toString()
  }
}
