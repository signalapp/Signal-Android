package org.signal.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.signal.core.ui.theme.SignalTheme

object Rows {

  /**
   * A row consisting of a radio button and text, which takes up the full
   * width of the screen.
   */
  @Composable
  fun RadioRow(
    selected: Boolean,
    text: String,
    modifier: Modifier = Modifier,
    label: String? = null,
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

      Column(
        modifier = Modifier.alpha(if (enabled) 1f else 0.4f)
      ) {
        Text(
          text = text,
          style = MaterialTheme.typography.bodyLarge
        )

        if (label != null) {
          Text(
            text = label,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }
  }

  @Composable
  fun ToggleRow(
    checked: Boolean,
    text: String,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    onCheckChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
  ) {
    Row(
      modifier = modifier
        .fillMaxWidth()
        .padding(defaultPadding())
    ) {
      Text(
        text = text,
        color = textColor,
        modifier = Modifier
          .weight(1f)
          .align(CenterVertically)
      )

      Switch(
        checked = checked,
        onCheckedChange = onCheckChanged,
        modifier = Modifier.align(CenterVertically)
      )
    }
  }

  @Composable
  fun TextRow(
    text: String,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    icon: Painter? = null,
    foregroundTint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null
  ) {
    TextRow(
      text = {
        Text(
          text = text,
          color = foregroundTint,
          modifier = Modifier.align(CenterVertically)
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
      onClick = onClick
    )
  }

  @Composable
  fun TextRow(
    text: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null
  ) {
    Row(
      modifier = modifier
        .fillMaxWidth()
        .clickable(enabled = onClick != null, onClick = onClick ?: {})
        .padding(defaultPadding())
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
      horizontal = dimensionResource(id = R.dimen.core_ui__gutter),
      vertical = 16.dp
    )
  }
}

@Preview
@Composable
private fun RadioRowPreview() {
  SignalTheme(isDarkMode = false) {
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

@Preview
@Composable
private fun ToggleRowPreview() {
  SignalTheme(isDarkMode = false) {
    var checked by remember { mutableStateOf(false) }

    Rows.ToggleRow(
      checked = checked,
      text = "ToggleRow",
      onCheckChanged = {
        checked = it
      }
    )
  }
}

@SignalPreview
@Composable
private fun TextRowPreview() {
  Previews.Preview {
    Rows.TextRow(
      text = "TextRow",
      icon = painterResource(id = android.R.drawable.ic_menu_camera)
    )
  }
}
