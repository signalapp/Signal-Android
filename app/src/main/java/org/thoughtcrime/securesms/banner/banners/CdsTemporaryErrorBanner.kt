/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.banner.banners

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentManager
import kotlinx.coroutines.flow.flowOf
import org.signal.core.ui.Previews
import org.signal.core.ui.SignalPreview
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.banner.Banner
import org.thoughtcrime.securesms.banner.ui.compose.Action
import org.thoughtcrime.securesms.banner.ui.compose.DefaultBanner
import org.thoughtcrime.securesms.banner.ui.compose.Importance
import org.thoughtcrime.securesms.contacts.sync.CdsTemporaryErrorBottomSheet
import org.thoughtcrime.securesms.keyvalue.SignalStore

class CdsTemporaryErrorBanner(private val fragmentManager: FragmentManager) : Banner<Unit>() {

  override val enabled: Boolean
    get() {
      val timeUntilUnblock = SignalStore.misc.cdsBlockedUtil - System.currentTimeMillis()
      return SignalStore.misc.isCdsBlocked && timeUntilUnblock < CdsPermanentErrorBanner.PERMANENT_TIME_CUTOFF
    }

  override val dataFlow
    get() = flowOf(Unit)

  @Composable
  override fun DisplayBanner(model: Unit, contentPadding: PaddingValues) {
    Banner(
      contentPadding = contentPadding,
      onLearnMoreClicked = { CdsTemporaryErrorBottomSheet.show(fragmentManager) }
    )
  }
}

@Composable
private fun Banner(contentPadding: PaddingValues, onLearnMoreClicked: () -> Unit = {}) {
  DefaultBanner(
    title = null,
    body = stringResource(id = R.string.reminder_cds_warning_body),
    importance = Importance.ERROR,
    actions = listOf(
      Action(R.string.reminder_cds_warning_learn_more) {
        onLearnMoreClicked()
      }
    ),
    paddingValues = contentPadding
  )
}

@SignalPreview
@Composable
private fun BannerPreview() {
  Previews.Preview {
    Banner(PaddingValues(0.dp))
  }
}
