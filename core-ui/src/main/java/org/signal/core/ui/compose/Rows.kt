/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import org.signal.core.ui.R
import org.signal.core.ui.compose.Rows.TextAndLabel

object Rows {

  /**
   * Link row that positions [text] and optional [label] in a [TextAndLabel] to the side of an [icon] on the right.
   */
  @OptIn(ExperimentalFoundationApi::class)
  @Composable
  fun LinkRow(
    text: String,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    label: String? = null,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
    icon: ImageVector
  ) {
    Row(
      modifier = modifier
        .fillMaxWidth()
        .clickable(
          enabled = enabled,
          onClick = onClick ?: {}
        )
        .padding(defaultPadding()),
      verticalAlignment = CenterVertically
    ) {
      TextAndLabel(
        text = text,
        label = label,
        textColor = textColor,
        enabled = enabled,
        modifier = Modifier.padding(end = 16.dp)
      )

      Icon(
        imageVector = icon,
        contentDescription = null
      )
    }
  }

  /**
   * A row consisting of a radio button and [text] and optional [label] in a [TextAndLabel].
   */
  @Composable
  fun RadioRow(
    selected: Boolean,
    text: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true
  ) {
    RadioRow(
      content = {
        TextAndLabel(
          text = text,
          label = label,
          enabled = enabled
        )
      },
      selected = selected,
      modifier = modifier,
      enabled = enabled
    )
  }

  /**
   * Customizable radio row that allows [content] to be provided as composable functions instead of primitives.
   */
  @Composable
  fun RadioRow(
    content: @Composable RowScope.() -> Unit,
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
  ) {
    Row(
      modifier = modifier
        .fillMaxWidth()
        .padding(defaultPadding()),
      verticalAlignment = CenterVertically
    ) {
      RadioButton(
        enabled = enabled,
        selected = selected,
        onClick = null,
        modifier = Modifier.padding(end = 24.dp)
      )

      content()
    }
  }

  @Composable
  fun RadioListRow(
    text: String,
    labels: Array<String>,
    values: Array<String>,
    selectedValue: String,
    onSelected: (String) -> Unit,
    enabled: Boolean = true
  ) {
    RadioListRow(
      text = { selectedIndex ->
        val selectedLabel = if (selectedIndex in labels.indices) {
          labels[selectedIndex]
        } else {
          null
        }

        TextAndLabel(
          text = text,
          label = selectedLabel
        )
      },
      dialogTitle = text,
      labels = labels,
      values = values,
      selectedValue = selectedValue,
      onSelected = onSelected,
      enabled = enabled
    )
  }

  @Composable
  fun RadioListRow(
    text: @Composable RowScope.(Int) -> Unit,
    dialogTitle: String,
    labels: Array<String>,
    values: Array<String>,
    selectedValue: String,
    onSelected: (String) -> Unit,
    enabled: Boolean = true
  ) {
    val selectedIndex = values.indexOf(selectedValue)
    var displayDialog by remember { mutableStateOf(false) }

    TextRow(
      text = { text(selectedIndex) },
      enabled = enabled,
      onClick = {
        displayDialog = true
      }
    )

    if (displayDialog) {
      Dialogs.RadioListDialog(
        onDismissRequest = { displayDialog = false },
        labels = labels,
        values = values,
        selectedIndex = selectedIndex,
        title = dialogTitle,
        onSelected = {
          onSelected(values[it])
        }
      )
    }
  }

  @Composable
  fun MultiSelectRow(
    text: String,
    labels: Array<String>,
    values: Array<String>,
    selection: Array<String>,
    onSelectionChanged: (Array<String>) -> Unit
  ) {
    var displayDialog by remember { mutableStateOf(false) }

    TextRow(
      text = text,
      label = selection.joinToString(", ") {
        val index = values.indexOf(it)
        if (index == -1) error("not found: $it in ${values.joinToString(", ")}")
        labels[index]
      },
      onClick = {
        displayDialog = true
      }
    )

    if (displayDialog) {
      Dialogs.MultiSelectListDialog(
        onDismissRequest = { displayDialog = false },
        labels = labels,
        values = values,
        selection = selection,
        title = text,
        onSelectionChanged = onSelectionChanged
      )
    }
  }

  /**
   * Row that positions [text] and optional [label] in a [TextAndLabel] to the side of a [Switch].
   *
   * Can display a circular loading indicator by setting isLoaded to true. Setting isLoading to true
   * will disable the control by default.
   */
  @Composable
  fun ToggleRow(
    checked: Boolean,
    text: String,
    onCheckChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    icon: ImageVector? = null,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
    isLoading: Boolean = false
  ) {
    ToggleRow(
      checked = checked,
      text = AnnotatedString(text),
      onCheckChanged = onCheckChanged,
      modifier = modifier,
      label = label?.let { AnnotatedString(it) },
      icon = icon,
      textColor = textColor,
      enabled = enabled,
      isLoading = isLoading
    )
  }

  /**
   * Row that positions [text] and optional [label] in a [TextAndLabel] to the side of a [Switch].
   *
   * Can display a circular loading indicator by setting isLoaded to true. Setting isLoading to true
   * will disable the control by default.
   */
  @Composable
  fun ToggleRow(
    checked: Boolean,
    text: AnnotatedString,
    onCheckChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: AnnotatedString? = null,
    icon: ImageVector? = null,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    inlineContent: Map<String, InlineTextContent> = mapOf()
  ) {
    val isEnabled = enabled && !isLoading

    Row(
      modifier = modifier
        .fillMaxWidth()
        .clickable(enabled = isEnabled) { onCheckChanged(!checked) }
        .padding(defaultPadding()),
      verticalAlignment = CenterVertically
    ) {
      if (icon != null) {
        Icon(
          imageVector = icon,
          contentDescription = null
        )

        Spacer(modifier = Modifier.width(24.dp))
      }

      TextAndLabel(
        text = text,
        label = label,
        textColor = textColor,
        enabled = isEnabled,
        modifier = Modifier.padding(end = 16.dp),
        inlineContent = inlineContent
      )

      val loadingContent by rememberDelayedState(isLoading)
      val toggleState = remember(checked, loadingContent, isEnabled, onCheckChanged) {
        ToggleState(checked, loadingContent, isEnabled, onCheckChanged)
      }

      AnimatedContent(
        toggleState,
        label = "toggle-loading-state",
        contentKey = { it.isLoading },
        transitionSpec = {
          fadeIn(animationSpec = tween(220, delayMillis = 90))
            .togetherWith(fadeOut(animationSpec = tween(90)))
        }
      ) { state ->
        if (state.isLoading) {
          CircularProgressIndicator(
            modifier = Modifier.minimumInteractiveComponentSize()
          )
        } else {
          Switch(
            checked = state.checked,
            enabled = state.enabled,
            onCheckedChange = state.onCheckChanged,
            colors = SwitchDefaults.colors(
              checkedTrackColor = MaterialTheme.colorScheme.primary,
              uncheckedBorderColor = MaterialTheme.colorScheme.outline,
              uncheckedIconColor = MaterialTheme.colorScheme.outline,
              uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
          )
        }
      }
    }
  }

  /**
   * Text row that positions [text] and optional [label] in a [TextAndLabel] to the side of an optional [icon].
   */
  @Composable
  fun TextRow(
    text: String,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    label: String? = null,
    icon: Painter? = null,
    foregroundTint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true
  ) {
    TextRow(
      text = remember(text) { AnnotatedString(text) },
      label = remember(label) { label?.let { AnnotatedString(label) } },
      icon = icon,
      modifier = modifier,
      iconModifier = iconModifier,
      foregroundTint = foregroundTint,
      onClick = onClick,
      onLongClick = onLongClick,
      enabled = enabled
    )
  }

  /**
   * Text row that positions [text] and optional [label] in a [TextAndLabel] to the side of an optional [icon].
   */
  @Composable
  fun TextRow(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    label: AnnotatedString? = null,
    icon: Painter? = null,
    foregroundTint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true
  ) {
    TextRow(
      text = {
        TextAndLabel(
          text = text,
          label = label,
          textColor = foregroundTint,
          enabled = enabled
        )
      },
      icon = if (icon != null) {
        {
          Icon(
            painter = icon,
            contentDescription = null,
            tint = foregroundTint,
            modifier = iconModifier
          )
        }
      } else {
        null
      },
      modifier = modifier,
      onClick = onClick,
      onLongClick = onLongClick,
      enabled = enabled
    )
  }

  /**
   * Text row that positions [text] and optional [label] in a [TextAndLabel] to the side of an [icon] using ImageVector.
   */
  @Composable
  fun TextRow(
    text: String,
    icon: ImageVector?,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    label: String? = null,
    foregroundTint: Color = MaterialTheme.colorScheme.onSurface,
    iconTint: Color = foregroundTint,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true
  ) {
    TextRow(
      text = {
        TextAndLabel(
          text = text,
          label = label,
          textColor = foregroundTint,
          enabled = enabled
        )
      },
      icon = if (icon != null) {
        {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = iconModifier
          )
        }
      } else {
        null
      },
      modifier = modifier,
      onClick = onClick,
      onLongClick = onLongClick,
      enabled = enabled
    )
  }

  /**
   * Customizable text row that allows [text] and [icon] to be provided as composable functions instead of primitives.
   */
  @OptIn(ExperimentalFoundationApi::class)
  @Composable
  fun TextRow(
    text: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true
  ) {
    val haptics = LocalHapticFeedback.current
    Row(
      modifier = modifier
        .fillMaxWidth()
        .combinedClickable(
          enabled = enabled && (onClick != null || onLongClick != null),
          onClick = onClick ?: {},
          onLongClick = {
            if (onLongClick != null) {
              haptics.performHapticFeedback(HapticFeedbackType.LongPress)
              onLongClick()
            }
          }
        )
        .padding(defaultPadding()),
      verticalAlignment = CenterVertically
    ) {
      if (icon != null) {
        icon()
        Spacer(modifier = Modifier.width(24.dp))
      }
      text()
    }
  }

  @Composable
  fun defaultPadding(): PaddingValues {
    return PaddingValues(
      horizontal = dimensionResource(id = R.dimen.gutter),
      vertical = 16.dp
    )
  }

  /**
   * Row component to position text above an optional label.
   */
  @Composable
  fun RowScope.TextAndLabel(
    text: String? = null,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge
  ) {
    TextAndLabel(
      text = remember(text) { text?.let { AnnotatedString(it) } },
      label = remember(label) { label?.let { AnnotatedString(it) } },
      modifier = modifier,
      enabled = enabled,
      textColor = textColor,
      textStyle = textStyle
    )
  }

  /**
   * Row component to position text above an optional label.
   */
  @Composable
  fun RowScope.TextAndLabel(
    text: AnnotatedString? = null,
    modifier: Modifier = Modifier,
    label: AnnotatedString? = null,
    enabled: Boolean = true,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    inlineContent: Map<String, InlineTextContent> = mapOf()
  ) {
    Column(
      modifier = modifier
        .alpha(if (enabled) 1f else 0.4f)
        .weight(1f)
    ) {
      if (text != null) {
        Text(
          text = text,
          style = textStyle,
          color = textColor,
          inlineContent = inlineContent
        )
      }

      if (label != null) {
        Text(
          text = label,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

private data class ToggleState(
  val checked: Boolean,
  val isLoading: Boolean,
  val enabled: Boolean,
  val onCheckChanged: (Boolean) -> Unit
)

@DayNightPreviews
@Composable
private fun RadioRowPreview() {
  Previews.Preview {
    var selected by remember { mutableStateOf(true) }

    Rows.RadioRow(
      selected,
      "RadioRow",
      label = "RadioRow Label",
      modifier = Modifier.clickable {
        selected = !selected
      }
    )
  }
}

@DayNightPreviews
@Composable
private fun ToggleRowPreview() {
  Previews.Preview {
    var checked by remember { mutableStateOf(false) }

    Rows.ToggleRow(
      checked = checked,
      text = "ToggleRow",
      label = "ToggleRow label",
      onCheckChanged = {
        checked = it
      }
    )
  }
}

@DayNightPreviews
@Composable
private fun ToggleLoadingRowPreview() {
  Previews.Preview {
    var checked by remember { mutableStateOf(false) }

    Rows.ToggleRow(
      checked = checked,
      text = "ToggleRow",
      label = "ToggleRow label",
      isLoading = true,
      onCheckChanged = {
        checked = it
      }
    )
  }
}

@DayNightPreviews
@Composable
private fun TextRowPreview() {
  Previews.Preview {
    Rows.TextRow(
      text = "TextRow",
      icon = painterResource(id = android.R.drawable.ic_menu_camera),
      onClick = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun TextAndLabelPreview() {
  Previews.Preview {
    Row {
      TextAndLabel(
        text = "TextAndLabel Text",
        label = "TextAndLabel Label"
      )
      TextAndLabel(
        text = "TextAndLabel Text",
        label = "TextAndLabel Label",
        enabled = false
      )
    }
  }
}

@DayNightPreviews
@Composable
private fun RadioListRowPreview() {
  var selectedValue by remember { mutableStateOf("b") }

  Previews.Preview {
    Rows.RadioListRow(
      text = "Radio List",
      labels = arrayOf("A", "B", "C"),
      values = arrayOf("a", "b", "c"),
      selectedValue = selectedValue,
      onSelected = {
        selectedValue = it
      }
    )
  }
}

@DayNightPreviews
@Composable
private fun MultiSelectRowPreview() {
  var selectedValues by remember { mutableStateOf(arrayOf("b")) }

  Previews.Preview {
    Rows.MultiSelectRow(
      text = "MultiSelect List",
      labels = arrayOf("A", "B", "C"),
      values = arrayOf("a", "b", "c"),
      selection = selectedValues,
      onSelectionChanged = {
        selectedValues = it
      }
    )
  }
}
