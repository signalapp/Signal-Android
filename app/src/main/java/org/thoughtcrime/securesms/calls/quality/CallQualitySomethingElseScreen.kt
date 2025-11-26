/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.calls.quality

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.TextFields
import org.signal.core.ui.compose.horizontalGutters
import org.thoughtcrime.securesms.R

@Composable
fun CallQualitySomethingElseScreen(
  somethingElseDescription: String,
  onCancelClick: () -> Unit,
  onSaveClick: (String) -> Unit
) {
  Scaffolds.Settings(
    title = stringResource(R.string.CallQualitySomethingElseScreen__title),
    navigationIcon = ImageVector.vectorResource(R.drawable.symbol_arrow_start_24),
    onNavigationClick = onCancelClick,
    navigationContentDescription = stringResource(R.string.CallQualitySomethingElseScreen__back),
    modifier = Modifier.imePadding()
  ) { paddingValues ->

    var issue by remember { mutableStateOf(somethingElseDescription) }
    val focusRequester = remember { FocusRequester() }

    Column(
      modifier = Modifier.padding(paddingValues)
    ) {
      TextFields.TextField(
        label = {
          Text(stringResource(R.string.CallQualitySomethingElseScreen__describe_your_issue))
        },
        value = issue,
        minLines = 4,
        maxLines = 4,
        onValueChange = {
          issue = it
        },
        modifier = Modifier
          .focusRequester(focusRequester)
          .fillMaxWidth()
          .horizontalGutters()
      )

      Text(
        text = stringResource(R.string.CallQualitySomethingElseScreen__privacy_notice),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
          .horizontalGutters()
          .padding(top = 24.dp, bottom = 32.dp)
      )

      Spacer(modifier = Modifier.weight(1f))

      Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
          .fillMaxWidth()
          .horizontalGutters()
          .padding(bottom = 16.dp)
      ) {
        CancelButton(
          onClick = onCancelClick
        )

        Buttons.LargeTonal(
          onClick = { onSaveClick(issue) }
        ) {
          Text(text = stringResource(R.string.CallQualitySomethingElseScreen__save))
        }
      }
    }

    LaunchedEffect(Unit) {
      focusRequester.requestFocus()
    }
  }
}
