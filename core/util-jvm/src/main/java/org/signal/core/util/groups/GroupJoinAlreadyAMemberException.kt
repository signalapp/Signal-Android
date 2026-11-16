package org.signal.core.util.groups

class GroupJoinAlreadyAMemberException(throwable: Throwable?, @JvmField val isPending: Boolean, @JvmField val isFullMember: Boolean) : GroupChangeException(throwable)
