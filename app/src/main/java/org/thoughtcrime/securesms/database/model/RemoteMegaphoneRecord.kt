package org.thoughtcrime.securesms.database.model

import android.net.Uri

/**
 * Represents a Remote Megaphone.
 */
data class RemoteMegaphoneRecord(
  val id: Long = -1,
  val priority: Long,
  val uuid: String,
  val countries: String?,
  val minimumVersion: Int,
  val doNotShowBefore: Long,
  val doNotShowAfter: Long,
  val showForNumberOfDays: Long,
  val conditionalId: String?,
  val primaryActionId: ActionId?,
  val secondaryActionId: ActionId?,
  val imageUrl: String?,
  val imageUri: Uri? = null,
  val title: String,
  val body: String,
  val primaryActionText: String?,
  val secondaryActionText: String?,
  val shownAt: Long = 0,
  val finishedAt: Long = 0
) {
  @get:JvmName("hasPrimaryAction")
  val hasPrimaryAction = primaryActionId != null && primaryActionText != null

  @get:JvmName("hasSecondaryAction")
  val hasSecondaryAction = secondaryActionId != null && secondaryActionText != null

  enum class ActionId(val id: String, val isDonateAction: Boolean = false) {
    SNOOZE("snooze"),
    FINISH("finish"),
    DONATE("donate", true);

    companion object {
      fun from(id: String?): ActionId? {
        return values().firstOrNull { it.id == id }
      }
    }
  }
}
