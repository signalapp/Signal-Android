/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.internal.svr

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import kotlinx.collections.immutable.persistentListOf
import org.signal.core.ui.Rows
import org.signal.core.ui.theme.SignalTheme
import org.thoughtcrime.securesms.compose.ComposeFragment

class InternalSvrPlaygroundFragment : ComposeFragment() {

  private val viewModel: InternalSvrPlaygroundViewModel by viewModels()

  @Composable
  override fun FragmentContent() {
    val state: InternalSvrPlaygroundState by viewModel.state

    SvrPlaygroundScreen(
      state = state,
      onTabSelected = viewModel::onTabSelected,
      onCreateClicked = viewModel::onCreateClicked,
      onRestoreClicked = viewModel::onRestoreClicked,
      onDeleteClicked = viewModel::onDeleteClicked,
      onPinChanged = viewModel::onPinChanged
    )
  }
}

@Composable
fun SvrPlaygroundScreen(
  state: InternalSvrPlaygroundState,
  modifier: Modifier = Modifier,
  onTabSelected: (SvrImplementation) -> Unit = {},
  onCreateClicked: () -> Unit = {},
  onRestoreClicked: () -> Unit = {},
  onDeleteClicked: () -> Unit = {},
  onPinChanged: (String) -> Unit = {}
) {
  Column(modifier = modifier.fillMaxWidth()) {
    TabRow(selectedTabIndex = state.options.indexOf(state.selected)) {
      state.options.forEach { option ->
        Tab(
          text = { Text(option.title) },
          selected = option == state.selected,
          onClick = { onTabSelected(option) }
        )
      }
    }

    Rows.TextRow(
      text = "Create backup data",
      onClick = onCreateClicked
    )

    Rows.TextRow(
      text = "Restore backup data",
      onClick = onRestoreClicked
    )

    Rows.TextRow(
      text = "Delete backup data",
      onClick = onDeleteClicked
    )

    Row(modifier = Modifier.fillMaxWidth()) {
      Column(
        modifier = Modifier
          .padding(8.dp)
          .align(Alignment.CenterVertically)
      ) {
        Text(text = "PIN: ")
      }
      Column {
        TextField(
          value = state.userPin,
          onValueChange = onPinChanged,
          modifier = Modifier.fillMaxWidth()
        )
      }
    }

    if (state.loading) {
      Row(modifier = Modifier.fillMaxWidth().padding(48.dp)) {
        CircularProgressIndicator(
          modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
          color = Color.Blue
        )
      }
    } else if (state.lastResult != null) {
      Rows.TextRow(text = state.lastResult)
    }
  }
}

@Preview
@Composable
fun SvrPlaygroundScreenLightTheme() {
  SignalTheme(isDarkMode = false) {
    Surface {
      SvrPlaygroundScreen(
        state = InternalSvrPlaygroundState(
          options = persistentListOf(SvrImplementation.SVR2)
        )
      )
    }
  }
}

@Preview
@Composable
fun SvrPlaygroundScreenDarkTheme() {
  SignalTheme(isDarkMode = true) {
    Surface {
      SvrPlaygroundScreen(
        state = InternalSvrPlaygroundState(
          options = persistentListOf(SvrImplementation.SVR2, SvrImplementation.SVR3)
        )
      )
    }
  }
}
