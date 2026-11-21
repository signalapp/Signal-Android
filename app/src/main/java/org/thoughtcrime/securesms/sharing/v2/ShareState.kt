package org.thoughtcrime.securesms.sharing.v2

data class ShareState(
  val loadState: ShareDataLoadState = ShareDataLoadState.Init
) {
  sealed class ShareDataLoadState {
    object Init : ShareDataLoadState()
    data class Loaded(val resolvedShareData: ResolvedShareData) : ShareDataLoadState()
    data class Failed(val error: ShareError) : ShareDataLoadState()
  }
}
