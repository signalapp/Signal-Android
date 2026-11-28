/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.banner.banners

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.util.AppUtil
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.banner.Banner
import org.thoughtcrime.securesms.banner.ui.compose.Action
import org.thoughtcrime.securesms.banner.ui.compose.DefaultBanner
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.signal.appsettings.R as AppSettingsR

/**
 * Asks the user to restart once the language they chose has been downloaded in the background.
 */
class LanguagePackReadyBanner(private val context: Context) : Banner<String>() {

  override val enabled: Boolean
    get() = AppDependencies.languageSplitRepository.pendingRestartLanguage.value != null

  override val dataFlow: Flow<String>
    get() = AppDependencies.languageSplitRepository.pendingRestartLanguage.filterNotNull().map(::displayName)

  override val stateUpdates: Flow<Unit>
    get() = AppDependencies.languageSplitRepository.pendingRestartLanguage.map { }

  @Composable
  override fun DisplayBanner(model: String, contentPadding: PaddingValues) {
    Banner(
      languageName = model,
      contentPadding = contentPadding,
      onDismissListener = AppDependencies.languageSplitRepository::dismissPendingRestart,
      onRestartListener = { AppUtil.restart(context) }
    )
  }

  /** The language's name as the picker shows it, which is in that language. */
  private fun displayName(language: String): String {
    val values = context.resources.getStringArray(AppSettingsR.array.language_values)
    val entries = context.resources.getStringArray(AppSettingsR.array.language_entries)
    return entries.getOrNull(values.indexOf(language))?.trim() ?: language
  }
}

@Composable
private fun Banner(languageName: String, contentPadding: PaddingValues, onDismissListener: () -> Unit = {}, onRestartListener: () -> Unit = {}) {
  DefaultBanner(
    title = null,
    body = stringResource(id = R.string.LanguagePackReadyBanner__body, languageName),
    actions = listOf(
      Action(R.string.LanguagePackReadyBanner__not_now) {
        onDismissListener()
      },
      Action(R.string.LanguagePackReadyBanner__restart) {
        onRestartListener()
      }
    ),
    paddingValues = contentPadding
  )
}

@DayNightPreviews
@Composable
private fun BannerPreview() {
  Previews.Preview {
    Banner(languageName = "Deutsch", contentPadding = PaddingValues(0.dp))
  }
}
