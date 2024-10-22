/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.subscription

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.signal.core.ui.BottomSheets
import org.signal.core.ui.Buttons
import org.signal.core.ui.Previews
import org.signal.core.ui.Scaffolds
import org.signal.core.ui.SignalPreview
import org.signal.core.ui.theme.SignalTheme
import org.signal.core.util.Hex
import org.thoughtcrime.securesms.R
import org.whispersystems.signalservice.api.backup.BackupKey
import kotlin.random.Random

/**
 * Screen displaying the backup key allowing the user to write it down
 * or copy it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageBackupsKeyRecordScreen(
  backupKey: BackupKey,
  onNavigationClick: () -> Unit = {},
  onCopyToClipboardClick: (String) -> Unit = {},
  onNextClick: () -> Unit = {}
) {
  val coroutineScope = rememberCoroutineScope()
  val sheetState = rememberModalBottomSheetState(
    skipPartiallyExpanded = true
  )

  Scaffolds.Settings(
    title = "",
    navigationIconPainter = painterResource(R.drawable.symbol_arrow_left_24),
    onNavigationClick = onNavigationClick
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .padding(paddingValues)
        .padding(horizontal = dimensionResource(R.dimen.core_ui__gutter))
        .fillMaxSize(),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Image(
        painter = painterResource(R.drawable.image_signal_backups_lock),
        contentDescription = null,
        modifier = Modifier
          .padding(top = 24.dp)
          .size(80.dp)
      )

      Text(
        text = stringResource(R.string.MessageBackupsKeyRecordScreen__record_your_backup_key),
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.padding(top = 16.dp)
      )

      Text(
        text = stringResource(R.string.MessageBackupsKeyRecordScreen__this_key_is_required_to_recover),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp)
      )

      val backupKeyString = remember(backupKey) {
        backupKey.value.toList().chunked(2).map { Hex.toStringCondensed(it.toByteArray()) }.joinToString("  ")
      }

      Box(
        modifier = Modifier
          .padding(top = 24.dp, bottom = 16.dp)
          .background(
            color = SignalTheme.colors.colorSurface1,
            shape = RoundedCornerShape(10.dp)
          )
          .padding(24.dp)
      ) {
        Text(
          text = backupKeyString,
          style = MaterialTheme.typography.bodyLarge
            .copy(
              fontSize = 18.sp,
              fontWeight = FontWeight(400),
              letterSpacing = 1.44.sp,
              lineHeight = 36.sp,
              textAlign = TextAlign.Center,
              fontFamily = FontFamily.Monospace
            )
        )
      }

      Buttons.Small(
        onClick = { onCopyToClipboardClick(backupKeyString) }
      ) {
        Text(
          text = stringResource(R.string.MessageBackupsKeyRecordScreen__copy_to_clipboard)
        )
      }

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
          .padding(bottom = 24.dp)
      ) {
        Buttons.LargeTonal(
          onClick = {
            coroutineScope.launch {
              sheetState.show()
            }
          },
          modifier = Modifier.align(Alignment.BottomEnd)
        ) {
          Text(
            text = stringResource(R.string.MessageBackupsKeyRecordScreen__next)
          )
        }
      }
    }

    if (sheetState.isVisible) {
      ModalBottomSheet(
        dragHandle = null,
        onDismissRequest = {
          coroutineScope.launch {
            sheetState.hide()
          }
        }
      ) {
        BottomSheetContent(
          onContinueClick = onNextClick,
          onSeeKeyAgainClick = {
            coroutineScope.launch {
              sheetState.hide()
            }
          }
        )
      }
    }
  }
}

@Composable
private fun BottomSheetContent(
  onContinueClick: () -> Unit,
  onSeeKeyAgainClick: () -> Unit
) {
  var checked by remember { mutableStateOf(false) }

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = dimensionResource(R.dimen.core_ui__gutter))
  ) {
    BottomSheets.Handle()
    Text(
      text = stringResource(R.string.MessageBackupsKeyRecordScreen__keep_your_key_safe),
      style = MaterialTheme.typography.titleLarge,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(top = 30.dp)
    )

    Text(
      text = stringResource(R.string.MessageBackupsKeyRecordScreen__signal_will_not),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(top = 12.dp)
    )

    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .padding(vertical = 24.dp)
        .defaultMinSize(minWidth = 220.dp)
        .clip(shape = RoundedCornerShape(percent = 50))
        .clickable(onClick = { checked = !checked })
    ) {
      Checkbox(
        checked = checked,
        onCheckedChange = { checked = it }
      )

      Text(
        text = stringResource(R.string.MessageBackupsKeyRecordScreen__ive_recorded_my_key),
        style = MaterialTheme.typography.bodyLarge
      )
    }

    Buttons.LargeTonal(
      enabled = checked,
      onClick = onContinueClick,
      modifier = Modifier
        .padding(bottom = 16.dp)
        .defaultMinSize(minWidth = 220.dp)
    ) {
      Text(text = stringResource(R.string.MessageBackupsKeyRecordScreen__continue))
    }

    TextButton(
      onClick = onSeeKeyAgainClick,
      modifier = Modifier
        .padding(bottom = 24.dp)
        .defaultMinSize(minWidth = 220.dp)
    ) {
      Text(
        text = stringResource(R.string.MessageBackupsKeyRecordScreen__see_key_again)
      )
    }
  }
}

@SignalPreview
@Composable
private fun MessageBackupsKeyRecordScreenPreview() {
  Previews.Preview {
    MessageBackupsKeyRecordScreen(
      backupKey = BackupKey(Random.nextBytes(32))
    )
  }
}

@SignalPreview
@Composable
private fun BottomSheetContentPreview() {
  Previews.BottomSheetPreview {
    BottomSheetContent({}, {})
  }
}
