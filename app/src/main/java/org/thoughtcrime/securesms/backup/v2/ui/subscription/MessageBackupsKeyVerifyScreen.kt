/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.subscription

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalPreview
import org.signal.core.ui.compose.horizontalGutters
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.fonts.MonoTypeface
import org.thoughtcrime.securesms.registrationv3.ui.restore.BackupKeyVisualTransformation
import org.thoughtcrime.securesms.registrationv3.ui.restore.attachBackupKeyAutoFillHelper
import org.thoughtcrime.securesms.registrationv3.ui.restore.backupKeyAutoFillHelper
import org.whispersystems.signalservice.api.AccountEntropyPool
import kotlin.random.Random
import kotlin.random.nextInt
import org.signal.core.ui.R as CoreUiR

/**
 * Prompt user to re-enter backup key (AEP) to confirm they have it still.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
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
    navigationIconPainter = painterResource(R.drawable.symbol_arrow_start_24),
    onNavigationClick = onNavigationClick
  ) { paddingValues ->

    Column(
      verticalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier
        .padding(paddingValues)
        .fillMaxSize()
    ) {
      val scrollState = rememberScrollState()

      val focusRequester = remember { FocusRequester() }
      val visualTransform = remember { BackupKeyVisualTransformation(chunkSize = 4) }
      val keyboardController = LocalSoftwareKeyboardController.current

      var enteredBackupKey by remember { mutableStateOf("") }
      var isBackupKeyValid by remember { mutableStateOf(false) }
      var showError by remember { mutableStateOf(false) }

      Column(
        modifier = Modifier
          .verticalScroll(scrollState)
          .weight(weight = 1f, fill = false)
          .horizontalGutters(),
        horizontalAlignment = Alignment.Start
      ) {
        Text(
          text = stringResource(R.string.MessageBackupsKeyVerifyScreen__enter_the_backup_key_that_you_just_recorded),
          style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
        )

        Spacer(modifier = Modifier.height(48.dp))

        val updateEnteredBackupKey = { input: String ->
          enteredBackupKey = AccountEntropyPool.removeIllegalCharacters(input).uppercase()
          isBackupKeyValid = enteredBackupKey == backupKey
          showError = !isBackupKeyValid && enteredBackupKey.length >= backupKey.length
        }

        var requestFocus: Boolean by remember { mutableStateOf(true) }
        val autoFillHelper = backupKeyAutoFillHelper { updateEnteredBackupKey(it) }

        TextField(
          value = enteredBackupKey,
          onValueChange = {
            updateEnteredBackupKey(it)
            autoFillHelper.onValueChanged(it)
          },
          label = {
            Text(text = stringResource(id = R.string.MessageBackupsKeyVerifyScreen__backup_key))
          },
          textStyle = LocalTextStyle.current.copy(
            fontFamily = MonoTypeface.fontFamily(),
            lineHeight = 36.sp
          ),
          keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            capitalization = KeyboardCapitalization.None,
            imeAction = ImeAction.Next,
            autoCorrectEnabled = false
          ),
          keyboardActions = KeyboardActions(
            onNext = {
              if (isBackupKeyValid) {
                keyboardController?.hide()
                coroutineScope.launch { sheetState.show() }
              }
            }
          ),
          supportingText = { if (showError) Text(text = stringResource(R.string.MessageBackupsKeyVerifyScreen__incorrect_backup_key)) },
          isError = showError,
          minLines = 4,
          visualTransformation = visualTransform,
          modifier = Modifier
            .testTag("message-backups-key-verify-screen-backup-key-input-field")
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .attachBackupKeyAutoFillHelper(autoFillHelper)
            .onGloballyPositioned {
              if (requestFocus) {
                focusRequester.requestFocus()
                requestFocus = false
              }
            }
        )
      }

      Surface(
        shadowElevation = if (scrollState.canScrollForward) 8.dp else 0.dp,
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          horizontalArrangement = Arrangement.SpaceBetween,
          modifier = Modifier
            .padding(top = 8.dp, bottom = 24.dp)
            .horizontalGutters()
            .fillMaxWidth()
        ) {
          TextButton(
            onClick = onNavigationClick
          ) {
            Text(
              text = stringResource(id = R.string.MessageBackupsKeyVerifyScreen__see_key_again)
            )
          }

          Buttons.LargeTonal(
            enabled = isBackupKeyValid,
            onClick = {
              coroutineScope.launch { sheetState.show() }
            }
          ) {
            Text(
              text = stringResource(id = R.string.RegistrationActivity_next)
            )
          }
        }
      }
    }

    if (sheetState.isVisible) {
      ModalBottomSheet(
        sheetState = sheetState,
        dragHandle = null,
        onDismissRequest = {
          coroutineScope.launch {
            sheetState.hide()
          }
        }
      ) {
        BottomSheetContent(
          onContinueClick = {
            coroutineScope.launch {
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
