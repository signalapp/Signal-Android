package org.signal.core.util.groups

class GroupNotAMemberException : GroupChangeException {
  constructor()
  constructor(throwable: Throwable) : super(throwable)
  constructor(throwable: GroupNotAMemberException) : super(throwable.cause ?: throwable)
}
