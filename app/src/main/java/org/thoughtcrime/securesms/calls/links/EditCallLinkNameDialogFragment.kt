package org.thoughtcrime.securesms.calls.links

import android.app.Dialog
import android.os.Bundle
import android.view.WindowManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.fragment.app.viewModels
import org.signal.core.ui.Buttons
import org.signal.core.ui.Scaffolds
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeDialogFragment

class EditCallLinkNameDialogFragment : ComposeDialogFragment() {

  private val viewModel: CreateCallLinkViewModel by viewModels(
    ownerProducer = { requireParentFragment() }
  )

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setStyle(STYLE_NO_FRAME, R.style.Signal_DayNight_Dialog_FullScreen)
  }

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val dialog = super.onCreateDialog(savedInstanceState)
    dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    return dialog
  }

  @OptIn(ExperimentalMaterial3Api::class)
  @Preview
  @Composable
  override fun DialogContent() {
    val viewModelCallName by viewModel.callName
    var callName by remember {
      mutableStateOf(
        TextFieldValue(
          text = viewModelCallName,
          selection = TextRange(viewModelCallName.length)
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
              viewModel.setCallName(callName.text)
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
