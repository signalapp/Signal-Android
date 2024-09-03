/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.banner.banners

import android.os.Build
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.flow.Flow
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.banner.Banner
import org.thoughtcrime.securesms.banner.DismissibleBannerProducer
import org.thoughtcrime.securesms.banner.ui.compose.Action
import org.thoughtcrime.securesms.banner.ui.compose.DefaultBanner
import org.thoughtcrime.securesms.keyvalue.SignalStore

class BubbleOptOutBanner(inBubble: Boolean, private val actionListener: (Boolean) -> Unit) : Banner() {

  override val enabled: Boolean = inBubble && !SignalStore.tooltips.hasSeenBubbleOptOutTooltip() && Build.VERSION.SDK_INT > 29

  @Composable
  override fun DisplayBanner(contentPadding: PaddingValues) {
    DefaultBanner(
      title = null,
      body = stringResource(id = R.string.BubbleOptOutTooltip__description),
      actions = listOf(
        Action(R.string.BubbleOptOutTooltip__turn_off) {
          actionListener(true)
        },
        Action(R.string.BubbleOptOutTooltip__not_now) {
          actionListener(false)
        }
      ),
      paddingValues = contentPadding
    )
  }

  private class Producer(inBubble: Boolean, actionListener: (Boolean) -> Unit) : DismissibleBannerProducer<BubbleOptOutBanner>(bannerProducer = {
    BubbleOptOutBanner(inBubble) { turnOffBubbles ->
      actionListener(turnOffBubbles)
      it()
    }
  }) {
    override fun createDismissedBanner(): BubbleOptOutBanner {
      return BubbleOptOutBanner(false) {}
    }
  }

  companion object {
    fun createFlow(inBubble: Boolean, actionListener: (Boolean) -> Unit): Flow<BubbleOptOutBanner> {
      return Producer(inBubble, actionListener).flow
    }
  }
}
