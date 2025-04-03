/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.subscription

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalPreview
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.fonts.MonoTypeface
import org.signal.core.ui.R as CoreUiR

/**
 * Screen displaying the backup key allowing the user to write it down
 * or copy it.
 */
@Composable
fun MessageBackupsKeyRecordScreen(
  backupKey: String,
  onNavigationClick: () -> Unit = {},
  onCopyToClipboardClick: (String) -> Unit = {},
  onNextClick: () -> Unit = {}
) {
  val backupKeyString = remember(backupKey) {
    backupKey.chunked(4).joinToString("  ")
  }

  Scaffolds.Settings(
    title = "",
    navigationIconPainter = painterResource(R.drawable.symbol_arrow_start_24),
    onNavigationClick = onNavigationClick
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .padding(paddingValues)
        .padding(horizontal = dimensionResource(CoreUiR.dimen.gutter))
        .fillMaxSize(),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      LazyColumn(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .weight(1f)
          .testTag("message-backups-key-record-screen-lazy-column")
      ) {
        item {
          Image(
            painter = painterResource(R.drawable.image_signal_backups_lock),
            contentDescription = null,
            modifier = Modifier
              .padding(top = 24.dp)
              .size(80.dp)
          )
        }

        item {
          Text(
            text = stringResource(R.string.MessageBackupsKeyRecordScreen__record_your_backup_key),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 16.dp)
          )
        }

        item {
          Text(
            text = stringResource(R.string.MessageBackupsKeyRecordScreen__this_key_is_required_to_recover),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp)
          )
        }

        item {
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
                  fontFamily = MonoTypeface.fontFamily()
                )
            )
          }
        }

        item {
          Buttons.Small(
            onClick = { onCopyToClipboardClick(backupKeyString) }
          ) {
            Text(
              text = stringResource(R.string.MessageBackupsKeyRecordScreen__copy_to_clipboard)
            )
          }
        }
      }

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 24.dp)
      ) {
        Buttons.LargeTonal(
          onClick = onNextClick,
          modifier = Modifier.align(Alignment.BottomEnd)
        ) {
          Text(
            text = stringResource(R.string.MessageBackupsKeyRecordScreen__next)
          )
        }
      }
    }
  }
}

@SignalPreview
@Composable
private fun MessageBackupsKeyRecordScreenPreview() {
  Previews.Preview {
    MessageBackupsKeyRecordScreen(
      backupKey = (0 until 63).map { (('A'..'Z') + ('0'..'9')).random() }.joinToString("") + "0"
    )
  }
}
