/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.appicon

import androidx.annotation.DrawableRes
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import org.signal.appsettings.R
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.R as CoreUiR

@VisibleForTesting
object AppIconTutorialTestTags {
  const val SCROLLER = "scroller"
}

/**
 * Explains where a replacement app icon and name show up, and that notifications keep the default Signal icon.
 */
@Composable
fun AppIconTutorialScreen(
  onEvent: (AppIconTutorialEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  Scaffolds.Settings(
    title = "",
    onNavigationClick = { onEvent(AppIconTutorialEvent.NavigateBackClicked) },
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    navigationContentDescription = stringResource(CoreUiR.string.CloseButton__content_description),
    modifier = modifier
  ) { contentPadding ->
    Box(modifier = Modifier.padding(contentPadding)) {
      Column(
        modifier = Modifier
          .padding(horizontal = 24.dp)
          .align(Alignment.Center)
          .verticalScroll(rememberScrollState())
          .testTag(AppIconTutorialTestTags.SCROLLER),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        TutorialText(stringResource(R.string.preferences__app_icon_warning))
        TutorialIllustration(R.drawable.app_icon_tutorial_apps_homescreen)
        TutorialText(stringResource(R.string.preferences__app_icon_notification_warning))
        TutorialIllustration(R.drawable.app_icon_tutorial_notification)
      }
    }
  }
}

@Composable
private fun TutorialText(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Start,
    modifier = Modifier
      .padding(vertical = 20.dp)
      .fillMaxWidth()
  )
}

@Composable
private fun TutorialIllustration(@DrawableRes illustration: Int) {
  val borderShape = RoundedCornerShape(12.dp)

  Box(
    contentAlignment = Alignment.Center,
    modifier = Modifier
      .fillMaxWidth()
      .clip(borderShape)
      .border(1.dp, MaterialTheme.colorScheme.outline, shape = borderShape)
  ) {
    Image(
      painter = painterResource(illustration),
      contentDescription = stringResource(R.string.preferences__graphic_illustrating_where_the_replacement_app_icon_will_be_visible),
      contentScale = ContentScale.FillWidth,
      modifier = Modifier.widthIn(max = 328.dp)
    )
  }
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun AppIconTutorialScreenPreview() {
  AppIconTutorialScreen(onEvent = {})
}
