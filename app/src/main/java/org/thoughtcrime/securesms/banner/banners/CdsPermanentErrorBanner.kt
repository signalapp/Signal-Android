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
import org.thoughtcrime.securesms.contacts.sync.CdsPermanentErrorBottomSheet
import org.thoughtcrime.securesms.keyvalue.SignalStore
import kotlin.time.Duration.Companion.days

class CdsPermanentErrorBanner(private val fragmentManager: FragmentManager) : Banner<Unit>() {

  companion object {
    /**
     * Even if we're not truly "permanently blocked", if the time until we're unblocked is long enough, we'd rather show the permanent error message than
     * telling the user to wait for 3 months or something.
     */
    val PERMANENT_TIME_CUTOFF = 30.days.inWholeMilliseconds
  }

  override val enabled: Boolean
    get() {
      val timeUntilUnblock = SignalStore.misc.cdsBlockedUtil - System.currentTimeMillis()
      return SignalStore.misc.isCdsBlocked && timeUntilUnblock >= PERMANENT_TIME_CUTOFF
    }

  override val dataFlow
    get() = flowOf(Unit)

  @Composable
  override fun DisplayBanner(model: Unit, contentPadding: PaddingValues) {
    Banner(
      contentPadding = contentPadding,
      onLearnMoreClicked = { CdsPermanentErrorBottomSheet.show(fragmentManager) }
    )
  }
}

@Composable
private fun Banner(contentPadding: PaddingValues, onLearnMoreClicked: () -> Unit = {}) {
  DefaultBanner(
    title = null,
    body = stringResource(id = R.string.reminder_cds_permanent_error_body),
    importance = Importance.ERROR,
    actions = listOf(
      Action(R.string.reminder_cds_permanent_error_learn_more) {
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
