/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.window

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.thoughtcrime.securesms.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@AllDevicePreviews
@Composable
private fun AppScaffoldWithTopBarPreview() {
  Previews.Preview {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass

    AppScaffold(
      navigator = rememberAppScaffoldNavigator(),

      topBarContent = {
        Scaffolds.DefaultTopAppBar(
          title = "Hello World!",
          titleContent = { _, title -> Text(text = title, style = MaterialTheme.typography.titleLarge) },
          navigationIcon = ImageVector.vectorResource(R.drawable.symbol_arrow_start_24),
          navigationContentDescription = "",
          onNavigationClick = { }
        )
      },

      secondaryContent = {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .fillMaxSize()
            .background(color = Color.Red)
        ) {
          Text(
            text = "ListContent\n$windowSizeClass",
            textAlign = TextAlign.Center
          )
        }
      },

      primaryContent = {
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
      }
    )
  }
}
