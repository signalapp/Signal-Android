package org.session.libsession.messaging.open_groups

data class GroupMember(
    val groupId: String,
    val profileId: String,
    val role: GroupMemberRole
)

enum class GroupMemberRole {
    STANDARD, ZOOMBIE, MODERATOR, ADMIN, HIDDEN_MODERATOR, HIDDEN_ADMIN
}
