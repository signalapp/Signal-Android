/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.compose.ScreenTitlePane
import org.thoughtcrime.securesms.util.viewModel
import org.thoughtcrime.securesms.window.AppScaffoldWithTopBar
import org.thoughtcrime.securesms.window.WindowSizeClass
import org.thoughtcrime.securesms.window.rememberAppScaffoldNavigator

/**
 * Allows the user to start a new conversation by selecting a recipient.
 *
 * A modernized compose-based replacement for [org.thoughtcrime.securesms.NewConversationActivity].
 */
class NewConversationActivityV2 : PassphraseRequiredActivity() {
  companion object {
    @JvmStatic
    fun createIntent(context: Context): Intent = Intent(context, NewConversationActivityV2::class.java)
  }

  private val viewModel by viewModel { NewConversationViewModel() }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    super.onCreate(savedInstanceState, ready)

    setContent {
      val uiState by viewModel.uiState.collectAsStateWithLifecycle()

      SignalTheme {
        NewConversationScreen(
          uiState = uiState,
          callbacks = object : Callbacks {
            override fun onBackPressed() = onBackPressedDispatcher.onBackPressed()
          }
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun NewConversationScreen(
  uiState: NewConversationUiState,
  callbacks: Callbacks
) {
  val windowSizeClass = WindowSizeClass.rememberWindowSizeClass()
  val isSplitPane = windowSizeClass.isSplitPane(forceSplitPaneOnCompactLandscape = uiState.forceSplitPaneOnCompactLandscape)

  AppScaffoldWithTopBar(
    topBarContent = {
      Scaffolds.DefaultTopAppBar(
        title = if (!isSplitPane) stringResource(R.string.NewConversationActivity__new_message) else "",
        titleContent = { _, title -> Text(text = title, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = ImageVector.vectorResource(R.drawable.symbol_arrow_start_24),
        navigationContentDescription = stringResource(R.string.DefaultTopAppBar__navigate_up_content_description),
        onNavigationClick = callbacks::onBackPressed
      )
    },
    listContent = {
      if (isSplitPane) {
        ScreenTitlePane(
          title = stringResource(R.string.NewConversationActivity__new_message),
          modifier = Modifier.fillMaxSize()
        )
      } else {
        DetailPaneContent()
      }
    },

    detailContent = {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize()
      ) {
        DetailPaneContent(
          modifier = Modifier
            .widthIn(max = windowSizeClass.detailPaneMaxContentWidth)
        )
      }
    },

    navigator = rememberAppScaffoldNavigator(
      isSplitPane = isSplitPane
    )
  )
}

private interface Callbacks {
  fun onBackPressed()

  object Empty : Callbacks {
    override fun onBackPressed() = Unit
  }
}

@Composable
private fun DetailPaneContent(
  modifier: Modifier = Modifier
) {
  RecipientPicker(
    showFindByUsernameAndPhoneOptions = true,
    callbacks = RecipientPickerCallbacks.Empty, // TODO(jeffrey) implement callbacks
    modifier = modifier
      .fillMaxSize()
      .padding(vertical = 12.dp)
  )
}

@AllDevicePreviews
@Composable
private fun NewConversationScreenPreview() {
  Previews.Preview {
    NewConversationScreen(
      uiState = NewConversationUiState(
        forceSplitPaneOnCompactLandscape = false
      ),
      callbacks = Callbacks.Empty
    )
  }
}
