package org.thoughtcrime.securesms.database

import android.database.Cursor
import com.google.protobuf.ByteString
import org.signal.core.util.requireBlob
import org.signal.core.util.requireString
import org.signal.spinner.ColumnTransformer
import org.signal.storageservice.protos.groups.local.DecryptedBannedMember
import org.signal.storageservice.protos.groups.local.DecryptedGroup
import org.signal.storageservice.protos.groups.local.DecryptedMember
import org.signal.storageservice.protos.groups.local.DecryptedPendingMember
import org.signal.storageservice.protos.groups.local.DecryptedRequestingMember
import org.whispersystems.signalservice.api.util.UuidUtil

object GV2Transformer : ColumnTransformer {
  override fun matches(tableName: String?, columnName: String): Boolean {
    return columnName == GroupTable.V2_DECRYPTED_GROUP || columnName == GroupTable.MEMBERS
  }

  override fun transform(tableName: String?, columnName: String, cursor: Cursor): String {
    return if (columnName == GroupTable.V2_DECRYPTED_GROUP) {
      val groupBytes = cursor.requireBlob(GroupTable.V2_DECRYPTED_GROUP)
      val group = DecryptedGroup.parseFrom(groupBytes)
      group.formatAsHtml()
    } else {
      val members = cursor.requireString(GroupTable.MEMBERS)
      members?.split(',')?.chunked(20)?.joinToString("<br>") { it.joinToString(",") } ?: ""
    }
  }
}

private fun DecryptedGroup.formatAsHtml(): String {
  val members: String = describeList(membersList, DecryptedMember::getUuid)
  val pending: String = describeList(pendingMembersList, DecryptedPendingMember::getUuid)
  val requesting: String = describeList(requestingMembersList, DecryptedRequestingMember::getUuid)
  val banned: String = describeList(bannedMembersList, DecryptedBannedMember::getUuid)

  return """
    Revision:     $revision
    Title:        $title
    Avatar:       ${(avatar?.length ?: 0) != 0}
    Timer:        ${disappearingMessagesTimer.duration}
    Description:  "$description"
    Announcement: $isAnnouncementGroup
    Access:       attributes(${accessControl.attributes}) members(${accessControl.members}) link(${accessControl.addFromInviteLink})
    Members:      $members
    Pending:      $pending
    Requesting:   $requesting
    Banned:       $banned
  """.trimIndent().replace("\n", "<br>")
}

private fun <T> describeList(list: List<T>, getUuid: (T) -> ByteString): String {
  return if (list.isNotEmpty() && list.size < 10) {
    var pendingMembers = "${list.size}\n"
    list.forEachIndexed { i, pendingMember ->
      pendingMembers += "      ${UuidUtil.fromByteString(getUuid(pendingMember))}"
      if (i != list.lastIndex) {
        pendingMembers += "\n"
      }
    }
    pendingMembers
  } else {
    list.size.toString()
  }
}
