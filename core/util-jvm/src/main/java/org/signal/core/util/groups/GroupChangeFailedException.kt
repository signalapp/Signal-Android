package org.signal.core.util.groups

class GroupChangeFailedException : GroupChangeException {
  constructor()

  constructor(throwable: Throwable?) : super(throwable)

  constructor(message: String?) : super(message)
}
