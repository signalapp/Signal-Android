/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.subscription

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalPreview
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.keyvalue.SignalStore
import kotlin.random.Random
import kotlin.random.nextInt
import org.signal.core.ui.R as CoreUiR

/**
 * Prompt user to re-enter backup key (AEP) to confirm they have it still.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageBackupsKeyVerifyScreen(
  backupKey: String,
  onNavigationClick: () -> Unit = {},
  onNextClick: () -> Unit = {}
) {
  val coroutineScope = rememberCoroutineScope()
  val sheetState = rememberModalBottomSheetState(
    skipPartiallyExpanded = true
  )

  Scaffolds.Settings(
    title = stringResource(R.string.MessageBackupsKeyVerifyScreen__confirm_your_backup_key),
    navigationIcon = ImageVector.vectorResource(R.drawable.symbol_arrow_start_24),
    onNavigationClick = onNavigationClick
  ) { paddingValues ->

    EnterKeyScreen(
      paddingValues = paddingValues,
      backupKey = backupKey,
      onNextClick = {
        coroutineScope.launch { sheetState.show() }
      },
      captionContent = {
        Text(
          text = stringResource(R.string.MessageBackupsKeyVerifyScreen__enter_the_backup_key_that_you_just_recorded),
          style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
        )
      },
      seeKeyButton = {
        TextButton(
          onClick = onNavigationClick
        ) {
          Text(
            text = stringResource(id = R.string.MessageBackupsKeyVerifyScreen__see_key_again)
          )
        }
      }
    )

    if (sheetState.isVisible) {
      ModalBottomSheet(
        sheetState = sheetState,
        dragHandle = null,
        containerColor = SignalTheme.colors.colorSurface1,
        onDismissRequest = {
          coroutineScope.launch {
            SignalStore.backup.lastVerifyKeyTime = System.currentTimeMillis()
            sheetState.hide()
          }
        }
      ) {
        BottomSheetContent(
          onContinueClick = {
            coroutineScope.launch {
              SignalStore.backup.lastVerifyKeyTime = System.currentTimeMillis()
              sheetState.hide()
            }
            onNextClick()
          },
          onSeeKeyAgainClick = {
            coroutineScope.launch {
              sheetState.hide()
            }
            onNavigationClick()
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
  LazyColumn(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = dimensionResource(CoreUiR.dimen.gutter))
      .testTag("message-backups-key-record-screen-sheet-content")
  ) {
    item {
      BottomSheets.Handle()
    }

    item {
      Image(
        painter = painterResource(R.drawable.image_signal_backups_key),
        contentDescription = null,
        modifier = Modifier
          .padding(top = 26.dp)
          .size(80.dp)
      )
    }

    item {
      Text(
        text = stringResource(R.string.MessageBackupsKeyRecordScreen__keep_your_key_safe),
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 16.dp)
      )
    }

    item {
      Text(
        text = stringResource(R.string.MessageBackupsKeyRecordScreen__signal_will_not),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 12.dp)
      )
    }

    item {
      Spacer(modifier = Modifier.height(54.dp))
      Buttons.LargeTonal(
        onClick = onContinueClick,
        modifier = Modifier
          .padding(bottom = 16.dp)
          .defaultMinSize(minWidth = 220.dp)
      ) {
        Text(text = stringResource(R.string.MessageBackupsKeyRecordScreen__continue))
      }
    }

    item {
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
}

@SignalPreview
@Composable
private fun MessageBackupsKeyRecordScreenPreview() {
  Previews.Preview {
    MessageBackupsKeyVerifyScreen(
      backupKey = (0 until 64).map { Random.nextInt(65..90).toChar() }.joinToString("").uppercase()
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
