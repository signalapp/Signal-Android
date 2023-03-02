package org.signal.core.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
    modifier: Modifier = Modifier
  ) {
    Row(
      modifier = modifier
        .fillMaxWidth()
        .padding(
          horizontal = dimensionResource(id = R.dimen.core_ui__gutter),
          vertical = 16.dp
        ),
      verticalAlignment = Alignment.CenterVertically
    ) {
      RadioButton(
        selected = selected,
        onClick = null,
        modifier = Modifier.padding(end = 24.dp)
      )

      Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge
      )
    }
  }
}

@Preview
@Composable
private fun RadioRowPreview() {
  SignalTheme(isDarkMode = false) {
    Rows.RadioRow(true, "RadioRow")
  }
}
