package org.signal.core.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.signal.core.ui.theme.SignalTheme

object Texts {
  /**
   * Header row for settings pages.
   */
  @Composable
  fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.titleSmall,
      modifier = modifier
        .padding(
          horizontal = dimensionResource(id = R.dimen.core_ui__gutter)
        )
        .padding(top = 16.dp, bottom = 12.dp)
    )
  }
}

@Preview
@Composable
private fun SectionHeaderPreview() {
  SignalTheme(isDarkMode = false) {
    Texts.SectionHeader("Header")
  }
}
