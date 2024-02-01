package org.signal.core.ui

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import org.signal.core.ui.Dialogs.SimpleAlertDialog
import org.signal.core.ui.Dialogs.SimpleMessageDialog

object Dialogs {

  const val NoDismiss = ""

  @Composable
  fun SimpleMessageDialog(
    message: String,
    dismiss: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    dismissColor: Color = Color.Unspecified,
    properties: DialogProperties = DialogProperties()
  ) {
    androidx.compose.material3.AlertDialog(
      onDismissRequest = onDismiss,
      title = if (title == null) null else { { Text(text = title) } },
      text = { Text(text = message) },
      confirmButton = {
        TextButton(onClick = {
          onDismiss()
        }) {
          Text(text = dismiss, color = dismissColor)
        }
      },
      modifier = modifier,
      properties = properties
    )
  }

  @Composable
  fun SimpleAlertDialog(
    title: String,
    body: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismiss: String = NoDismiss,
    confirmColor: Color = Color.Unspecified,
    dismissColor: Color = Color.Unspecified,
    properties: DialogProperties = DialogProperties()
  ) {
    androidx.compose.material3.AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(text = title) },
      text = { Text(text = body) },
      confirmButton = {
        TextButton(onClick = {
          onConfirm()
          onDismiss()
        }) {
          Text(text = confirm, color = confirmColor)
        }
      },
      dismissButton = if (dismiss.isNotEmpty()) {
        {
          TextButton(onClick = onDismiss) {
            Text(text = dismiss, color = dismissColor)
          }
        }
      } else {
        null
      },
      modifier = modifier,
      properties = properties
    )
  }

  /**
   * A dialog that *just* shows a spinner. Useful for short actions where you need to
   * let the user know that some action is completing.
   */
  @Composable
  fun IndeterminateProgressDialog() {
    androidx.compose.material3.AlertDialog(
      onDismissRequest = {},
      confirmButton = {},
      dismissButton = {},
      text = {
        CircularProgressIndicator(
          modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
        )
      },
      modifier = Modifier
        .size(100.dp)
    )
  }
}

@Preview
@Composable
private fun AlertDialogPreview() {
  SimpleAlertDialog(
    title = "Title Text",
    body = "Body text message",
    confirm = "Confirm Button",
    dismiss = "Dismiss Button",
    onConfirm = {},
    onDismiss = {}
  )
}

@Preview
@Composable
private fun MessageDialogPreview() {
  SimpleMessageDialog(
    message = "Message here",
    dismiss = "OK",
    onDismiss = {}
  )
}

@Preview
@Composable
private fun IndeterminateProgressDialogPreview() {
  Dialogs.IndeterminateProgressDialog()
}
