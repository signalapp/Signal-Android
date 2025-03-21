/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.appearance.appicon

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.fragment.findNavController
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeFragment

class AppIconTutorialFragment : ComposeFragment() {

  @Composable
  override fun FragmentContent() {
    Scaffolds.Settings(
      title = "",
      onNavigationClick = {
        findNavController().popBackStack()
      },
      navigationIconPainter = painterResource(id = R.drawable.ic_arrow_left_24),
      navigationContentDescription = stringResource(id = R.string.Material3SearchToolbar__close)
    ) { contentPadding: PaddingValues ->
      TutorialScreen(Modifier.padding(contentPadding))
    }
  }

  @Composable
  fun TutorialScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
      Column(
        modifier = Modifier
          .padding(horizontal = 24.dp)
          .align(Alignment.Center)
          .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        val borderShape = RoundedCornerShape(12.dp)

        Text(
          text = stringResource(R.string.preferences__app_icon_warning),
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Start,
          modifier = Modifier
            .padding(vertical = 20.dp)
            .fillMaxWidth()
        )
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .fillMaxWidth()
            .clip(borderShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape = borderShape)
        ) {
          Image(
            painter = painterResource(R.drawable.app_icon_tutorial_apps_homescreen),
            contentDescription = stringResource(R.string.preferences__graphic_illustrating_where_the_replacement_app_icon_will_be_visible),
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
              .widthIn(max = 328.dp)
          )
        }
        Text(
          text = stringResource(id = R.string.preferences__app_icon_notification_warning),
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Start,
          modifier = Modifier
            .padding(vertical = 20.dp)
            .fillMaxWidth()
        )
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .fillMaxWidth()
            .clip(borderShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape = borderShape)
        ) {
          Image(
            painter = painterResource(R.drawable.app_icon_tutorial_notification),
            contentDescription = stringResource(R.string.preferences__graphic_illustrating_where_the_replacement_app_icon_will_be_visible),
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
              .widthIn(max = 328.dp)
          )
        }
      }
    }
  }

  @Preview
  @Composable
  private fun TutorialScreenPreview() {
    TutorialScreen()
  }

  companion object {
    val TAG = Log.tag(AppIconTutorialFragment::class.java)
  }
}
