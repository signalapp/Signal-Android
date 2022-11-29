package org.thoughtcrime.securesms.conversation.colors

import androidx.annotation.NonNull
import org.thoughtcrime.securesms.database.GroupTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Class to assist managing the colors of author names in the UI in groups.
 * We want to be able to map each group member to a color, and for that to
 * remain constant throughout a "chat open lifecycle" (i.e. should never
 * change while looking at a chat, but can change if you close and open).
 */
class GroupAuthorNameColorHelper {

  /** Needed so that we have a full history of current *and* past members (so colors don't change when someone leaves) */
  private val fullMemberCache: MutableMap<GroupId, Set<Recipient>> = mutableMapOf()

  /**
   * Given a [GroupId], returns a map of member -> name color.
   */
  fun getColorMap(@NonNull groupId: GroupId): Map<RecipientId, NameColor> {
    val dbMembers: Set<Recipient> = SignalDatabase
      .groups
      .getGroupMembers(groupId, GroupTable.MemberSet.FULL_MEMBERS_INCLUDING_SELF)
      .toSet()
    val cachedMembers: Set<Recipient> = fullMemberCache.getOrDefault(groupId, setOf())
    val allMembers: Set<Recipient> = cachedMembers + dbMembers

    fullMemberCache[groupId] = allMembers

    val members: List<Recipient> = allMembers
      .filter { member -> member != Recipient.self() }
      .sortedBy { obj: Recipient -> obj.requireStringId() }

    val allColors: List<NameColor> = ChatColorsPalette.Names.all

    val colors: MutableMap<RecipientId, NameColor> = HashMap()
    for (i in members.indices) {
      colors[members[i].id] = allColors[i % allColors.size]
    }

    return colors.toMap()
  }
}
