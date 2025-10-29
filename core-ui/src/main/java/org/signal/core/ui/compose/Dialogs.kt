/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import android.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
  fun IndeterminateProgressDialog(
    onDismissRequest: () -> Unit = {}
  ) {
    Dialog(
      onDismissRequest = onDismissRequest,
      properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
      Surface(
        modifier = Modifier.size(100.dp),
        shape = Defaults.shape,
        color = Defaults.containerColor,
        tonalElevation = Defaults.TonalElevation
      ) {
        CircularProgressIndicator(
          modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(24.dp)
            .testTag("dialog-circular-progress-indicator")
        )
      }
    }
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
          modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
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

  @Composable
  fun RadioListDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    title: String,
    labels: Array<String>,
    values: Array<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit
  ) {
    Dialog(
      onDismissRequest = onDismissRequest,
      properties = properties
    ) {
      Surface(
        modifier = Modifier
          .heightIn(min = 0.dp, max = getScreenHeight() - 200.dp)
          .background(
            color = SignalTheme.colors.colorSurface2,
            shape = AlertDialogDefaults.shape
          )
          .clip(AlertDialogDefaults.shape)
      ) {
        Column {
          Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
              .padding(top = 16.dp)
              .horizontalGutters()
          )

          LazyColumn(
            modifier = Modifier.padding(top = 24.dp, bottom = 16.dp),
            state = rememberLazyListState(
              initialFirstVisibleItemIndex = selectedIndex
            )
          ) {
            items(
              count = values.size,
              key = { values[it] }
            ) { index ->
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                  .fillMaxWidth()
                  .defaultMinSize(minHeight = 48.dp)
                  .clickable(
                    enabled = true,
                    onClick = {
                      onSelected(index)
                      onDismissRequest()
                    }
                  )
                  .horizontalGutters()
              ) {
                RadioButton(
                  enabled = true,
                  selected = index == selectedIndex,
                  onClick = null,
                  modifier = Modifier.padding(end = 24.dp)
                )

                Text(text = labels[index])
              }
            }
          }
        }
      }
    }
  }

  @Composable
  fun MultiSelectListDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    title: String,
    labels: Array<String>,
    values: Array<String>,
    selection: Array<String>,
    onSelectionChanged: (Array<String>) -> Unit
  ) {
    var selectedIndicies by remember {
      mutableStateOf(
        values.mapIndexedNotNull { index, value ->
          if (value in selection) {
            index
          } else {
            null
          }
        }
      )
    }

    Dialog(
      onDismissRequest = onDismissRequest,
      properties = properties
    ) {
      Surface(
        modifier = Modifier
          .heightIn(min = 0.dp, max = getScreenHeight() - 200.dp)
          .background(
            color = SignalTheme.colors.colorSurface2,
            shape = AlertDialogDefaults.shape
          )
          .clip(AlertDialogDefaults.shape)
      ) {
        Column {
          Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
              .padding(top = 16.dp)
              .horizontalGutters()
          )

          LazyColumn(
            modifier = Modifier.padding(top = 24.dp, bottom = 16.dp)
          ) {
            items(
              count = values.size,
              key = { values[it] }
            ) { index ->
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                  .fillMaxWidth()
                  .defaultMinSize(minHeight = 48.dp)
                  .clickable(
                    enabled = true,
                    onClick = {
                      selectedIndicies = if (index in selectedIndicies) {
                        selectedIndicies - index
                      } else {
                        selectedIndicies + index
                      }
                    }
                  )
                  .horizontalGutters()
              ) {
                Checkbox(
                  enabled = true,
                  checked = index in selectedIndicies,
                  onCheckedChange = null,
                  modifier = Modifier.padding(end = 24.dp)
                )

                Text(text = labels[index])
              }
            }
          }

          FlowRow(
            horizontalArrangement = Arrangement.End,
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 16.dp)
          ) {
            TextButton(onClick = onDismissRequest) {
              Text(text = stringResource(R.string.cancel))
            }

            TextButton(onClick = {
              onSelectionChanged(selectedIndicies.sorted().map { values[it] }.toTypedArray())
              onDismissRequest()
            }) {
              Text(text = stringResource(R.string.ok))
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

  @Composable
  private fun getScreenHeight(): Dp {
    return with(LocalDensity.current) {
      LocalWindowInfo.current.containerSize.height.toDp()
    }
  }
}

@DayNightPreviews
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

@DayNightPreviews
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

@DayNightPreviews
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

@DayNightPreviews
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

@DayNightPreviews
@Composable
private fun IndeterminateProgressDialogPreview() {
  Previews.Preview {
    Dialogs.IndeterminateProgressDialog()
  }
}

@DayNightPreviews
@Composable
private fun IndeterminateProgressDialogMessagePreview() {
  Previews.Preview {
    Dialogs.IndeterminateProgressDialog("Completing...")
  }
}

@DayNightPreviews
@Composable
private fun IndeterminateProgressDialogCancellablePreview() {
  Previews.Preview {
    Dialogs.IndeterminateProgressDialog("Completing...", "Do not close app", "Cancel") {}
  }
}

@DayNightPreviews
@Composable
private fun RadioListDialogPreview() {
  Previews.Preview {
    Dialogs.RadioListDialog(
      onDismissRequest = {},
      title = "TestDialog",
      properties = DialogProperties(),
      labels = arrayOf(),
      values = arrayOf(),
      selectedIndex = -1,
      onSelected = {}
    )
  }
}
