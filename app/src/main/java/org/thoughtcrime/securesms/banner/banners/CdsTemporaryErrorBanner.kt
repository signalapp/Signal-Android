/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.banner.banners

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentManager
import kotlinx.coroutines.flow.Flow
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.banner.Banner
import org.thoughtcrime.securesms.banner.ui.compose.Action
import org.thoughtcrime.securesms.banner.ui.compose.DefaultBanner
import org.thoughtcrime.securesms.banner.ui.compose.Importance
import org.thoughtcrime.securesms.contacts.sync.CdsTemporaryErrorBottomSheet
import org.thoughtcrime.securesms.keyvalue.SignalStore

class CdsTemporaryErrorBanner(private val fragmentManager: FragmentManager) : Banner() {
  private val timeUntilUnblock = SignalStore.misc.cdsBlockedUtil - System.currentTimeMillis()

  override val enabled: Boolean = SignalStore.misc.isCdsBlocked && timeUntilUnblock < CdsPermanentErrorBanner.PERMANENT_TIME_CUTOFF

  @Composable
  override fun DisplayBanner() {
    DefaultBanner(
      title = null,
      body = stringResource(id = R.string.reminder_cds_warning_body),
      importance = Importance.ERROR,
      actions = listOf(
        Action(R.string.reminder_cds_warning_learn_more) {
          CdsTemporaryErrorBottomSheet.show(fragmentManager)
        }
      )
    )
  }

  companion object {

    @JvmStatic
    fun createFlow(fragmentManager: FragmentManager): Flow<CdsTemporaryErrorBanner> = createAndEmit {
      CdsTemporaryErrorBanner(fragmentManager)
    }
  }
}
