/**
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.calls.links

import android.app.Dialog
import android.os.Bundle
import android.view.WindowManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.End
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.navigation.fragment.navArgs
import org.signal.core.ui.Buttons
import org.signal.core.ui.Scaffolds
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeDialogFragment

class EditCallLinkNameDialogFragment : ComposeDialogFragment() {

  companion object {
    const val RESULT_KEY = "edit_call_link_name"
  }

  private val args: EditCallLinkNameDialogFragmentArgs by navArgs()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setStyle(STYLE_NO_FRAME, R.style.Signal_DayNight_Dialog_FullScreen)
  }

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val dialog = super.onCreateDialog(savedInstanceState)
    dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    return dialog
  }

  @Preview
  @Composable
  override fun DialogContent() {
    var callName by remember {
      mutableStateOf(
        TextFieldValue(
          text = args.name,
          selection = TextRange(args.name.length)
        )
      )
    }

    Scaffolds.Settings(
      title = stringResource(id = R.string.EditCallLinkNameDialogFragment__edit_call_name),
      onNavigationClick = this::dismiss,
      navigationIconPainter = painterResource(id = R.drawable.ic_arrow_left_24),
      navigationContentDescription = stringResource(id = R.string.Material3SearchToolbar__close)
    ) { paddingValues ->
      val focusRequester = remember { FocusRequester() }

      Surface(modifier = Modifier.padding(paddingValues)) {
        Column(
          modifier = Modifier
            .padding(
              horizontal = dimensionResource(id = org.signal.core.ui.R.dimen.core_ui__gutter)
            )
            .padding(top = 20.dp, bottom = 16.dp)
        ) {
          TextField(
            value = callName,
            label = {
              Text(text = stringResource(id = R.string.EditCallLinkNameDialogFragment__call_name))
            },
            onValueChange = { callName = it },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .focusRequester(focusRequester)
          )
          Spacer(modifier = Modifier.weight(1f))
          Buttons.MediumTonal(
            onClick = {
              setFragmentResult(RESULT_KEY, bundleOf(RESULT_KEY to callName.text))
              dismiss()
            },
            modifier = Modifier.align(End)
          ) {
            Text(text = stringResource(id = R.string.EditCallLinkNameDialogFragment__save))
          }
        }
      }

      LaunchedEffect(Unit) {
        focusRequester.requestFocus()
      }
    }
  }
}
