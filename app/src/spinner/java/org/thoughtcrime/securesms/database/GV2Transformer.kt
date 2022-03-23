package org.thoughtcrime.securesms.database

import android.database.Cursor
import org.signal.core.util.requireBlob
import org.signal.core.util.requireString
import org.signal.spinner.ColumnTransformer
import org.signal.storageservice.protos.groups.local.DecryptedGroup

object GV2Transformer : ColumnTransformer {
  override fun matches(tableName: String?, columnName: String): Boolean {
    return columnName == GroupDatabase.V2_DECRYPTED_GROUP || columnName == GroupDatabase.MEMBERS
  }

  override fun transform(tableName: String?, columnName: String, cursor: Cursor): String {
    return if (columnName == GroupDatabase.V2_DECRYPTED_GROUP) {
      val groupBytes = cursor.requireBlob(GroupDatabase.V2_DECRYPTED_GROUP)
      val group = DecryptedGroup.parseFrom(groupBytes)
      group.formatAsHtml()
    } else {
      val members = cursor.requireString(GroupDatabase.MEMBERS)
      members?.split(',')?.chunked(20)?.joinToString("<br>") { it.joinToString(",") } ?: ""
    }
  }
}

private fun DecryptedGroup.formatAsHtml(): String {
  return """
    Revision:     $revision
    Title:        $title
    Avatar:       ${(avatar?.length ?: 0) != 0}
    Timer:        ${disappearingMessagesTimer.duration}
    Description:  "$description"
    Announcement: $isAnnouncementGroup
    Access:       attributes(${accessControl.attributes}) members(${accessControl.members}) link(${accessControl.addFromInviteLink})
    Members:      $membersCount
    Pending:      $pendingMembersCount
    Requesting:   $requestingMembersCount
    Banned:       $bannedMembersCount
  """.trimIndent().replace("\n", "<br>")
}
