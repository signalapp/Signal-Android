/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.theme.LocalSnackbarColors
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Properly themed Snackbars. Since these use internal color state, we need to
 * use a local provider to pass the properly themed colors around. These composables
 * allow for quick and easy access to the proper theming for snackbars.
 */
object Snackbars {

  /**
   * Snackbar duration that allows specifying exact durations or using predefined
   * durations that match view-based snackbar timings.
   *
   * Compose's default [SnackbarDuration] values are significantly longer than view-based snackbars:
   * - Compose Short = 4000ms vs View-based LENGTH_SHORT = 1500ms
   * - Compose Long = 10000ms vs View-based LENGTH_LONG = 2750ms
   *
   * This sealed class provides durations that match view-based behavior for consistency.
   */
  sealed class Duration {
    abstract val duration: kotlin.time.Duration?

    companion object {
      /** 1500ms - matches view-based Snackbar.LENGTH_SHORT */
      @JvmField
      val SHORT: Duration = Short

      /** 2750ms - matches view-based Snackbar.LENGTH_LONG */
      @JvmField
      val LONG: Duration = Long

      @JvmField
      val INDEFINITE: Duration = Indefinite
    }

    private data object Short : Duration() {
      override val duration: kotlin.time.Duration = 1500.milliseconds
    }

    private data object Long : Duration() {
      override val duration: kotlin.time.Duration = 2750.milliseconds
    }

    private data object Indefinite : Duration() {
      override val duration: kotlin.time.Duration? = null
    }

    data class Custom(override val duration: kotlin.time.Duration) : Duration()
  }

  @Composable
  fun Host(snackbarHostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState = snackbarHostState, modifier = modifier) {
      Default(snackbarData = it)
    }
  }

  @Composable
  fun Default(snackbarData: SnackbarData) {
    val colors = LocalSnackbarColors.current
    Snackbar(
      snackbarData = snackbarData,
      containerColor = colors.color,
      contentColor = colors.contentColor,
      actionColor = colors.actionColor,
      actionContentColor = colors.actionContentColor,
      dismissActionContentColor = colors.dismissActionContentColor
    )
  }
}

/**
 * Shows a snackbar with custom duration support.
 *
 * Unlike the standard [SnackbarHostState.showSnackbar] which only supports Compose's
 * [SnackbarDuration] (Short=4s, Long=10s, Indefinite), this function allows specifying
 * exact durations via [Snackbars.Duration], including durations that match view-based snackbars.
 *
 * @param message The message to display in the snackbar.
 * @param actionLabel Optional action label to show. If null, no action button is shown.
 * @param withDismissAction Whether to show a dismiss action (X button).
 * @param duration The duration to show the snackbar. Defaults to [Snackbars.Duration.SHORT] (1500ms).
 * @return [SnackbarResult] indicating whether the snackbar was dismissed or the action was performed.
 */
suspend fun SnackbarHostState.showSnackbar(
  message: String,
  actionLabel: String? = null,
  withDismissAction: Boolean = false,
  duration: Snackbars.Duration = Snackbars.Duration.SHORT
): SnackbarResult {
  val durationValue = duration.duration

  return if (durationValue == null) {
    showSnackbar(
      message = message,
      actionLabel = actionLabel,
      withDismissAction = withDismissAction,
      duration = SnackbarDuration.Indefinite
    )
  } else {
    coroutineScope {
      val dismissJob = launch {
        delay(durationValue)
        currentSnackbarData?.dismiss()
      }

      val result = showSnackbar(
        message = message,
        actionLabel = actionLabel,
        withDismissAction = withDismissAction,
        duration = SnackbarDuration.Indefinite
      )

      dismissJob.cancel()

      result
    }
  }
}

@DayNightPreviews
@Composable
private fun SnackbarPreview() {
  Previews.Preview {
    Snackbars.Default(snackbarData = SampleSnackbarData)
  }
}

private object SampleSnackbarData : SnackbarData {
  override val visuals = object : SnackbarVisuals {
    override val actionLabel: String = "Action Label"
    override val duration: SnackbarDuration = SnackbarDuration.Short
    override val message: String = "Message"
    override val withDismissAction: Boolean = true
  }

  override fun dismiss() = Unit

  override fun performAction() = Unit
}
