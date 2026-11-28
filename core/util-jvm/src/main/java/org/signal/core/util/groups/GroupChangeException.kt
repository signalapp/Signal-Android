package org.signal.core.util.groups

abstract class GroupChangeException : Exception {
  protected constructor()

  protected constructor(throwable: Throwable?) : super(throwable)

  protected constructor(message: String?) : super(message)
}
