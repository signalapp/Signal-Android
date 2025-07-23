/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import android.R
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.signal.core.ui.compose.Dialogs.AdvancedAlertDialog
import org.signal.core.ui.compose.Dialogs.PermissionRationaleDialog
import org.signal.core.ui.compose.Dialogs.SimpleAlertDialog
import org.signal.core.ui.compose.Dialogs.SimpleMessageDialog
import org.signal.core.ui.compose.theme.SignalTheme

object Dialogs {

  const val NoTitle = ""
  const val NoDismiss = ""

  object Defaults {
    val shape: Shape @Composable get() = RoundedCornerShape(28.dp)
    val containerColor: Color @Composable get() = SignalTheme.colors.colorSurface1
    val iconContentColor: Color @Composable get() = MaterialTheme.colorScheme.onSurface
    val titleContentColor: Color @Composable get() = MaterialTheme.colorScheme.onSurface
    val textContentColor: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

    val TonalElevation: Dp = AlertDialogDefaults.TonalElevation
  }

  @Composable
  fun BaseAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = Defaults.shape,
    containerColor: Color = Defaults.containerColor,
    iconContentColor: Color = Defaults.iconContentColor,
    titleContentColor: Color = Defaults.titleContentColor,
    textContentColor: Color = Defaults.textContentColor,
    tonalElevation: Dp = Defaults.TonalElevation,
    properties: DialogProperties = DialogProperties()
  ) {
    androidx.compose.material3.AlertDialog(
      onDismissRequest = onDismissRequest,
      confirmButton = confirmButton,
      modifier = modifier,
      dismissButton = dismissButton,
      icon = icon,
      title = title,
      text = text,
      shape = shape,
      containerColor = containerColor,
      iconContentColor = iconContentColor,
      titleContentColor = titleContentColor,
      textContentColor = textContentColor,
      tonalElevation = tonalElevation,
      properties = properties
    )
  }

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
    BaseAlertDialog(
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
    onDismiss: () -> Unit = {},
    onDismissRequest: () -> Unit = onDismiss,
    onDeny: () -> Unit = {},
    modifier: Modifier = Modifier,
    dismiss: String = NoDismiss,
    confirmColor: Color = Color.Unspecified,
    dismissColor: Color = Color.Unspecified,
    properties: DialogProperties = DialogProperties()
  ) {
    BaseAlertDialog(
      onDismissRequest = onDismissRequest,
      title = if (title.isNotEmpty()) {
        {
          Text(text = title)
        }
      } else {
        null
      },
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
          TextButton(
            onClick =
            {
              onDismiss()
              onDeny()
            }
          ) {
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
    BaseAlertDialog(
      onDismissRequest = {},
      confirmButton = {},
      dismissButton = {},
      text = {
        CircularProgressIndicator(
          modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .testTag("dialog-circular-progress-indicator")
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
    BaseAlertDialog(
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
          Text(text = message, textAlign = TextAlign.Center)
        }
      },
      modifier = Modifier
        .size(200.dp)
    )
  }

  /**
   * Customizable progress spinner that can be dismissed while showing [message]
   * and [caption] below the spinner to let users know an action is completing
   */
  @Composable
  fun IndeterminateProgressDialog(message: String, caption: String = "", dismiss: String, onDismiss: () -> Unit) {
    BaseAlertDialog(
      onDismissRequest = {},
      confirmButton = {},
      dismissButton = {
        TextButton(
          onClick = onDismiss,
          modifier = Modifier.fillMaxWidth(),
          content = { Text(text = dismiss) }
        )
      },
      text = {
        Column(
          verticalArrangement = Arrangement.Center,
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.fillMaxWidth()
        ) {
          Spacer(modifier = Modifier.size(32.dp))
          CircularProgressIndicator()
          Spacer(modifier = Modifier.size(12.dp))
          Text(
            text = message,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
          )
          if (caption.isNotEmpty()) {
            Spacer(modifier = Modifier.size(8.dp))
            Text(
              text = caption,
              textAlign = TextAlign.Center,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      },
      modifier = Modifier.width(200.dp)
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

  /**
   * Alert dialog that supports three options.
   * If you only need two options (confirm/dismiss), use [SimpleAlertDialog] instead.
   */
  @Composable
  fun AdvancedAlertDialog(
    title: String = "",
    body: String = "",
    positive: String,
    neutral: String,
    negative: String,
    onPositive: () -> Unit,
    onNegative: () -> Unit,
    onNeutral: () -> Unit
  ) {
    Dialog(
      onDismissRequest = onNegative,
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
        Column(modifier = Modifier.padding(24.dp)) {
          Text(
            text = title,
            style = MaterialTheme.typography.titleLarge
          )

          Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 16.dp)
          )

          Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.fillMaxWidth()
          ) {
            TextButton(onClick = onPositive) {
              Text(text = positive)
            }
            TextButton(onClick = onNeutral) {
              Text(text = neutral)
            }
            TextButton(onClick = onNegative) {
              Text(text = negative)
            }
          }
        }
      }
    }
  }
}

@SignalPreview
@Composable
private fun PermissionRationaleDialogPreview() {
  Previews.Preview {
    PermissionRationaleDialog(
      icon = painterResource(id = R.drawable.ic_menu_camera),
      rationale = "This is rationale text about why we need permission.",
      confirm = "Continue",
      dismiss = "Not now",
      onConfirm = {},
      onDismiss = {}
    )
  }
}

@SignalPreview
@Composable
private fun AlertDialogPreview() {
  Previews.Preview {
    SimpleAlertDialog(
      title = "Title Text",
      body = "Body text message",
      confirm = "Confirm Button",
      dismiss = "Dismiss Button",
      onConfirm = {},
      onDismiss = {}
    )
  }
}

@SignalPreview
@Composable
private fun AdvancedAlertDialogPreview() {
  Previews.Preview {
    AdvancedAlertDialog(
      title = "Title text",
      body = "Body message text.",
      positive = "Positive",
      neutral = "Neutral",
      negative = "Negative",
      onPositive = {},
      onNegative = {},
      onNeutral = {}
    )
  }
}

@SignalPreview
@Composable
private fun MessageDialogPreview() {
  Previews.Preview {
    SimpleMessageDialog(
      message = "Message here",
      dismiss = "OK",
      onDismiss = {}
    )
  }
}

@SignalPreview
@Composable
private fun IndeterminateProgressDialogPreview() {
  Previews.Preview {
    Dialogs.IndeterminateProgressDialog()
  }
}

@SignalPreview
@Composable
private fun IndeterminateProgressDialogMessagePreview() {
  Previews.Preview {
    Dialogs.IndeterminateProgressDialog("Completing...")
  }
}

@SignalPreview
@Composable
private fun IndeterminateProgressDialogCancellablePreview() {
  Previews.Preview {
    Dialogs.IndeterminateProgressDialog("Completing...", "Do not close app", "Cancel") {}
  }
}
