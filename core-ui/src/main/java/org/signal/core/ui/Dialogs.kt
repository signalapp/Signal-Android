package org.signal.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.signal.core.ui.Dialogs.PermissionRationaleDialog
import org.signal.core.ui.Dialogs.SimpleAlertDialog
import org.signal.core.ui.Dialogs.SimpleMessageDialog
import org.signal.core.ui.theme.SignalTheme

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
      title = if (title == null) {
        null
      } else {
        { Text(text = title) }
      },
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
          onDismiss()
          onConfirm()
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

  /**
   * Customizable progress spinner that shows [message] below the spinner to let users know
   * an action is completing
   */
  @Composable
  fun IndeterminateProgressDialog(message: String) {
    androidx.compose.material3.AlertDialog(
      onDismissRequest = {},
      confirmButton = {},
      dismissButton = {},
      text = {
        Column(
          verticalArrangement = Arrangement.Center,
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.fillMaxWidth().fillMaxHeight()
        ) {
          Spacer(modifier = Modifier.size(24.dp))
          CircularProgressIndicator()
          Spacer(modifier = Modifier.size(20.dp))
          Text(message)
        }
      },
      modifier = Modifier
        .size(200.dp)
    )
  }

  @OptIn(ExperimentalLayoutApi::class)
  @Composable
  fun PermissionRationaleDialog(
    icon: Painter,
    rationale: String,
    confirm: String,
    dismiss: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
  ) {
    Dialog(
      onDismissRequest = onDismiss,
      properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
      Surface(
        modifier = Modifier
          .fillMaxWidth(fraction = 0.75f)
          .background(
            color = SignalTheme.colors.colorSurface2,
            shape = AlertDialogDefaults.shape
          )
          .clip(AlertDialogDefaults.shape)
      ) {
        Column {
          Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
              .fillMaxWidth()
              .background(color = MaterialTheme.colorScheme.primary)
              .padding(48.dp)
          ) {
            Icon(
              painter = icon,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onPrimary,
              modifier = Modifier.size(32.dp)
            )
          }
          Text(
            text = rationale,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
              .padding(horizontal = 24.dp, vertical = 16.dp)
          )

          FlowRow(
            horizontalArrangement = Arrangement.End,
            modifier = Modifier
              .fillMaxWidth()
              .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
          ) {
            TextButton(onClick = onDismiss) {
              Text(text = dismiss)
            }

            TextButton(onClick = onConfirm) {
              Text(text = confirm)
            }
          }
        }
      }
    }
  }
}

@Preview
@Composable
private fun PermissionRationaleDialogPreview() {
  Previews.Preview {
    PermissionRationaleDialog(
      icon = painterResource(id = android.R.drawable.ic_menu_camera),
      rationale = "This is rationale text about why we need permission.",
      confirm = "Continue",
      dismiss = "Not now",
      onConfirm = {},
      onDismiss = {}
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

@Preview
@Composable
private fun IndeterminateProgressDialogMessagePreview() {
  Dialogs.IndeterminateProgressDialog("Completing...")
}
