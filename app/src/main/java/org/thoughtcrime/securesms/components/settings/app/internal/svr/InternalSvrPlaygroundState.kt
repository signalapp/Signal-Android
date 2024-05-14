package org.thoughtcrime.securesms.components.settings.app.internal.svr

import kotlinx.collections.immutable.ImmutableList

data class InternalSvrPlaygroundState(
  val options: ImmutableList<SvrImplementation>,
  val selected: SvrImplementation = options[0],
  val loading: Boolean = false,
  val userPin: String = "",
  val lastResult: String? = null
)

enum class SvrImplementation(
  val title: String
) {
  SVR2("SVR2"),
  SVR3("SVR3")
}
