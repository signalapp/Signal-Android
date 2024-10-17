/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.banner.banners

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.signal.core.ui.Previews
import org.signal.core.ui.SignalPreview
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.banner.Banner
import org.thoughtcrime.securesms.banner.ui.compose.Action
import org.thoughtcrime.securesms.banner.ui.compose.DefaultBanner
import org.thoughtcrime.securesms.banner.ui.compose.Importance
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.PlayStoreUtil
import org.thoughtcrime.securesms.util.Util
import kotlin.time.Duration.Companion.milliseconds

/**
 * Banner to let the user know their build is about to expire.
 */
class OutdatedBuildBanner : Banner<Int>() {

  companion object {
    private const val MAX_DAYS_UNTIL_EXPIRE = 10
  }

  override val enabled: Boolean
    get() {
      val daysUntilExpiry = Util.getTimeUntilBuildExpiry(SignalStore.misc.estimatedServerTime).milliseconds.inWholeDays.toInt()
      return daysUntilExpiry <= MAX_DAYS_UNTIL_EXPIRE
    }

  override val dataFlow: Flow<Int>
    get() = flowOf(Util.getTimeUntilBuildExpiry(SignalStore.misc.estimatedServerTime).milliseconds.inWholeDays.toInt())

  @Composable
  override fun DisplayBanner(model: Int, contentPadding: PaddingValues) {
    val context = LocalContext.current

    Banner(
      contentPadding = contentPadding,
      daysUntilExpiry = model,
      onUpdateClicked = {
        PlayStoreUtil.openPlayStoreOrOurApkDownloadPage(context)
      }
    )
  }

  data class Model(
    val daysUntilExpiry: Int,
    val isClientDeprecated: Boolean
  )
}

@Composable
private fun Banner(contentPadding: PaddingValues, daysUntilExpiry: Int, onUpdateClicked: () -> Unit = {}) {
  val bodyText = if (daysUntilExpiry == 0) {
    stringResource(id = R.string.OutdatedBuildReminder_your_version_of_signal_will_expire_today)
  } else {
    pluralStringResource(id = R.plurals.OutdatedBuildReminder_your_version_of_signal_will_expire_in_n_days, count = daysUntilExpiry, daysUntilExpiry)
  }

  DefaultBanner(
    title = null,
    body = bodyText,
    importance = if (daysUntilExpiry == 0) {
      Importance.ERROR
    } else {
      Importance.NORMAL
    },
    actions = listOf(
      Action(R.string.ExpiredBuildReminder_update_now) {
        onUpdateClicked()
      }
    ),
    paddingValues = contentPadding
  )
}

@SignalPreview
@Composable
private fun BannerPreviewExpireToday() {
  Previews.Preview {
    Banner(
      contentPadding = PaddingValues(0.dp),
      daysUntilExpiry = 0
    )
  }
}

@SignalPreview
@Composable
private fun BannerPreviewExpireTomorrow() {
  Previews.Preview {
    Banner(
      contentPadding = PaddingValues(0.dp),
      daysUntilExpiry = 1
    )
  }
}

@SignalPreview
@Composable
private fun BannerPreviewExpireLater() {
  Previews.Preview {
    Banner(
      contentPadding = PaddingValues(0.dp),
      daysUntilExpiry = 3
    )
  }
}
