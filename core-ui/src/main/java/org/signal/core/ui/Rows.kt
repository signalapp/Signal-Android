package org.signal.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
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
    label: String? = null
  ) {
    Row(
      modifier = modifier
        .fillMaxWidth()
        .padding(defaultPadding()),
      verticalAlignment = Alignment.CenterVertically
    ) {
      RadioButton(
        selected = selected,
        onClick = null,
        modifier = Modifier.padding(end = 24.dp)
      )

      Column {
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
    icon: ImageVector? = null
  ) {
    if (icon != null) {
      Row(
        modifier = modifier
          .fillMaxWidth()
          .padding(defaultPadding())
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.width(24.dp))

        Text(
          text = text,
          modifier = Modifier.weight(1f)
        )
      }
    } else {
      Text(
        text = text,
        modifier = modifier
          .fillMaxWidth()
          .padding(defaultPadding())
      )
    }
  }

  @Composable
  private fun defaultPadding(): PaddingValues {
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

@Preview
@Composable
private fun TextRowPreview() {
  SignalTheme(isDarkMode = false) {
    Rows.TextRow(text = "TextRow")
    Rows.TextRow(text = "TextRow")
  }
}
