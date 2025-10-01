/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import org.signal.core.ui.R as CoreUiR

/**
 * Container for a backup alert sheet.
 *
 * Primary action padding will change depending on presence of secondary action.
 */
@Composable
fun BackupAlertBottomSheetContainer(
  icon: @Composable () -> Unit,
  title: String,
  primaryActionButtonState: BackupAlertActionButtonState,
  secondaryActionButtonState: BackupAlertActionButtonState? = null,
  content: @Composable () -> Unit
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = dimensionResource(id = CoreUiR.dimen.gutter))
  ) {
    BottomSheets.Handle()

    Spacer(modifier = Modifier.size(26.dp))

    icon()

    BackupAlertTitle(title = title)

    content()

    BackupAlertPrimaryActionButton(
      text = primaryActionButtonState.label,
      onClick = primaryActionButtonState.callback,
      modifier = Modifier.padding(
        bottom = if (secondaryActionButtonState != null) {
          16.dp
        } else {
          56.dp
        }
      )
    )

    if (secondaryActionButtonState != null) {
      BackupAlertSecondaryActionButton(
        text = secondaryActionButtonState.label,
        onClick = secondaryActionButtonState.callback
      )
    }
  }
}

/**
 * Backup alert sheet icon for the top of the sheet, vector only.
 */
@Composable
fun BackupAlertIcon(
  iconColors: BackupsIconColors
) {
  Icon(
    imageVector = ImageVector.vectorResource(id = R.drawable.symbol_backup_light),
    contentDescription = null,
    tint = iconColors.foreground,
    modifier = Modifier
      .size(80.dp)
      .background(color = iconColors.background, shape = CircleShape)
      .padding(20.dp)
  )
}

/**
 * Backup alert sheet image for the top of the sheet displaying a backup icon and alert indicator.
 */
@Composable
fun BackupAlertImage() {
  Box {
    Image(
      imageVector = ImageVector.vectorResource(id = R.drawable.image_signal_backups),
      contentDescription = null,
      modifier = Modifier
        .size(80.dp)
        .padding(2.dp)
    )
    Icon(
      imageVector = ImageVector.vectorResource(R.drawable.symbol_error_circle_fill_24),
      contentDescription = null,
      tint = MaterialTheme.colorScheme.error,
      modifier = Modifier.align(Alignment.TopEnd)
    )
  }
}

@Composable
private fun BackupAlertTitle(
  title: String
) {
  Text(
    text = title,
    style = MaterialTheme.typography.titleLarge,
    textAlign = TextAlign.Center,
    modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
  )
}

/**
 * Properly styled Text for Backup Alert sheets
 */
@Composable
fun BackupAlertText(
  text: String,
  modifier: Modifier = Modifier
) {
  Text(
    text = text,
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Center,
    modifier = modifier
  )
}

/**
 * Properly styled Text for Backup Alert sheets
 */
@Composable
fun BackupAlertText(
  text: AnnotatedString,
  modifier: Modifier = Modifier
) {
  Text(
    text = text,
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Center,
    modifier = modifier
  )
}

@Composable
private fun BackupAlertPrimaryActionButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Buttons.LargeTonal(
    onClick = onClick,
    modifier = Modifier
      .defaultMinSize(minWidth = 220.dp)
      .then(modifier)
  ) {
    Text(text = text)
  }
}

@Composable
private fun BackupAlertSecondaryActionButton(
  text: String,
  onClick: () -> Unit
) {
  TextButton(
    onClick = onClick,
    modifier = Modifier.padding(bottom = 32.dp)
  ) {
    Text(text = text)
  }
}

@DayNightPreviews
@Composable
private fun BackupAlertBottomSheetContainerPreview() {
  Previews.BottomSheetPreview {
    BackupAlertBottomSheetContainer(
      icon = { BackupAlertIcon(iconColors = BackupsIconColors.Warning) },
      title = "Test backup alert",
      primaryActionButtonState = BackupAlertActionButtonState("Test Primary", callback = {}),
      secondaryActionButtonState = BackupAlertActionButtonState("Test Secondary", callback = {})
    ) {
      BackupAlertText(text = "Content", modifier = Modifier.padding(bottom = 60.dp))
    }
  }
}

/**
 * Immutable state class for alert sheet actions.
 */
@Immutable
data class BackupAlertActionButtonState(
  val label: String,
  val callback: () -> Unit
)
