/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.window

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowHeightSizeClass
import androidx.window.core.layout.WindowWidthSizeClass
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.main.MainNavigationBar
import org.thoughtcrime.securesms.main.MainNavigationRail
import org.thoughtcrime.securesms.main.MainNavigationState
import org.thoughtcrime.securesms.util.RemoteConfig

enum class Navigation {
  RAIL,
  BAR
}

/**
 * Describes the size of screen we are displaying, and what components should be displayed.
 *
 * Screens should utilize this class by convention instead of calling [currentWindowAdaptiveInfo]
 * themselves, as this class includes checks with [RemoteConfig] to ensure we're allowed to display
 * content in different screen sizes.
 *
 * https://developer.android.com/develop/ui/compose/layouts/adaptive/use-window-size-classes
 */
enum class WindowSizeClass(
  val navigation: Navigation
) {
  COMPACT_PORTRAIT(Navigation.BAR),
  COMPACT_LANDSCAPE(Navigation.BAR),
  MEDIUM_PORTRAIT(Navigation.BAR),
  MEDIUM_LANDSCAPE(Navigation.RAIL),
  EXTENDED_PORTRAIT(Navigation.RAIL),
  EXTENDED_LANDSCAPE(Navigation.RAIL);

  companion object {
    @Composable
    fun rememberWindowSizeClass(): WindowSizeClass {
      val orientation = LocalConfiguration.current.orientation

      if (!LocalInspectionMode.current && !RemoteConfig.largeScreenUi) {
        return when (orientation) {
          Configuration.ORIENTATION_PORTRAIT, Configuration.ORIENTATION_UNDEFINED, Configuration.ORIENTATION_SQUARE -> {
            COMPACT_PORTRAIT
          }
          Configuration.ORIENTATION_LANDSCAPE -> COMPACT_LANDSCAPE
          else -> error("Unexpected orientation: $orientation")
        }
      }

      val wsc = currentWindowAdaptiveInfo().windowSizeClass

      return remember(orientation, wsc) {
        when (orientation) {
          Configuration.ORIENTATION_PORTRAIT, Configuration.ORIENTATION_UNDEFINED, Configuration.ORIENTATION_SQUARE -> {
            when (wsc.windowWidthSizeClass) {
              WindowWidthSizeClass.COMPACT -> COMPACT_PORTRAIT
              WindowWidthSizeClass.MEDIUM -> MEDIUM_PORTRAIT
              WindowWidthSizeClass.EXPANDED -> EXTENDED_PORTRAIT
              else -> error("Unsupported.")
            }
          }
          Configuration.ORIENTATION_LANDSCAPE -> {
            when (wsc.windowHeightSizeClass) {
              WindowHeightSizeClass.COMPACT -> COMPACT_LANDSCAPE
              WindowHeightSizeClass.MEDIUM -> MEDIUM_LANDSCAPE
              WindowHeightSizeClass.EXPANDED -> EXTENDED_LANDSCAPE
              else -> error("Unsupported.")
            }
          }
          else -> error("Unexpected orientation: $orientation")
        }
      }
    }
  }
}

/**
 * Composable who's precise layout will depend on the window size class of the device it is being utilized on.
 * This is built to be generic so that we can use it throughout the application to support different device classes.
 */
@Composable
fun AppScaffold(
  detailContent: @Composable () -> Unit = {},
  navRailContent: @Composable () -> Unit = {},
  bottomNavContent: @Composable () -> Unit = {},
  listContent: @Composable () -> Unit
) {
  val windowSizeClass = WindowSizeClass.rememberWindowSizeClass()

  Row {
    if (windowSizeClass.navigation == Navigation.RAIL) {
      navRailContent()
    }

    BoxWithConstraints(
      modifier = Modifier.weight(1f)
    ) {
      val listWidth = when (windowSizeClass) {
        WindowSizeClass.COMPACT_PORTRAIT -> maxWidth
        WindowSizeClass.COMPACT_LANDSCAPE -> maxWidth
        WindowSizeClass.MEDIUM_PORTRAIT -> maxWidth * 0.5f
        WindowSizeClass.MEDIUM_LANDSCAPE -> 360.dp
        WindowSizeClass.EXTENDED_PORTRAIT -> 360.dp
        WindowSizeClass.EXTENDED_LANDSCAPE -> 360.dp
      }

      val detailWidth = maxWidth - listWidth

      Row {
        Column(
          modifier = Modifier.width(listWidth).navigationBarsPadding()
        ) {
          Box(modifier = Modifier.weight(1f)) {
            listContent()
          }

          if (windowSizeClass.navigation == Navigation.BAR) {
            bottomNavContent()
          }
        }

        if (detailWidth > 0.dp) {
          // TODO -- slider to divide sizing?
          Box(modifier = Modifier.width(detailWidth)) {
            detailContent()
          }
        }
      }
    }
  }
}

@Preview(device = "spec:width=360dp,height=640dp,orientation=portrait")
@Preview(device = "spec:width=640dp,height=360dp,orientation=landscape")
@Preview(device = "spec:width=600dp,height=1024dp,orientation=portrait")
@Preview(device = "spec:width=1024dp,height=600dp,orientation=landscape")
@Preview(device = "spec:width=840dp,height=1280dp,orientation=portrait")
@Preview(device = "spec:width=1280dp,height=840dp,orientation=landscape")
@Composable
private fun AppScaffoldPreview() {
  Previews.Preview {
    AppScaffold(
      listContent = {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .fillMaxSize()
            .background(color = Color.Red)
        ) {
          Text(
            text = "ListContent",
            textAlign = TextAlign.Center
          )
        }
      },
      detailContent = {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .fillMaxSize()
            .background(color = Color.Blue)
        ) {
          Text(
            text = "DetailContent",
            textAlign = TextAlign.Center
          )
        }
      },
      navRailContent = {
        MainNavigationRail(
          state = MainNavigationState(),
          onDestinationSelected = {}
        )
      },
      bottomNavContent = {
        MainNavigationBar(
          state = MainNavigationState(),
          onDestinationSelected = {}
        )
      }
    )
  }
}
