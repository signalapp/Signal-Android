/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.banner.banners

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import org.thoughtcrime.securesms.backup.v2.ArchiveRestoreProgress
import org.thoughtcrime.securesms.backup.v2.ArchiveRestoreProgressState
import org.thoughtcrime.securesms.backup.v2.ArchiveRestoreProgressState.RestoreStatus
import org.thoughtcrime.securesms.backup.v2.ui.status.BackupStatusBanner
import org.thoughtcrime.securesms.banner.Banner

@OptIn(ExperimentalCoroutinesApi::class)
class MediaRestoreProgressBanner(private val listener: RestoreProgressBannerListener = EmptyListener) : Banner<ArchiveRestoreProgressState>() {

  override val enabled: Boolean
    get() = ArchiveRestoreProgress.state.let { it.restoreState.isMediaRestoreOperation || it.restoreStatus == RestoreStatus.FINISHED }

  override val dataFlow: Flow<ArchiveRestoreProgressState> by lazy {
    ArchiveRestoreProgress
      .stateFlow
      .filter {
        it.restoreStatus != RestoreStatus.NONE && (it.restoreState.isMediaRestoreOperation || it.restoreStatus == RestoreStatus.FINISHED)
      }
  }

  @Composable
  override fun DisplayBanner(model: ArchiveRestoreProgressState, contentPadding: PaddingValues) {
    BackupStatusBanner(
      data = model,
      onBannerClick = listener::onBannerClick,
      onActionClick = listener::onActionClick,
      onDismissClick = {
        ArchiveRestoreProgress.clearFinishedStatus()
        listener.onDismissComplete()
      }
    )
  }

  interface RestoreProgressBannerListener {
    fun onBannerClick()
    fun onActionClick(data: ArchiveRestoreProgressState)
    fun onDismissComplete()
  }

  private object EmptyListener : RestoreProgressBannerListener {
    override fun onBannerClick() = Unit
    override fun onActionClick(data: ArchiveRestoreProgressState) = Unit
    override fun onDismissComplete() = Unit
  }
}
