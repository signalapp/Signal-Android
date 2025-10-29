package org.thoughtcrime.securesms.components.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.horizontalGutters
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.R

/**
 * Adds a 'Beta' label next to [text] to indicate a feature is in development
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TextWithBetaLabel(
  text: String,
  textStyle: TextStyle = TextStyle.Default,
  enabled: Boolean = true,
  modifier: Modifier = Modifier
) {
  FlowRow(
    verticalArrangement = Arrangement.Center,
    horizontalArrangement = Arrangement.Center,
    modifier = modifier
  ) {
    Text(
      text = text,
      style = textStyle,
      modifier = Modifier
        .align(Alignment.CenterVertically)
        .alpha(if (enabled) 1f else Rows.DISABLED_ALPHA)
    )
    Text(
      text = stringResource(R.string.Beta__beta_title).uppercase(),
      color = MaterialTheme.colorScheme.onPrimaryContainer,
      style = MaterialTheme.typography.labelSmall,
      modifier = Modifier
        .padding(start = 6.dp)
        .padding(vertical = 6.dp)
        .background(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(28.dp))
        .padding(horizontal = 12.dp, vertical = 4.dp)
        .alpha(if (enabled) 1f else Rows.DISABLED_ALPHA)
        .align(Alignment.CenterVertically)
    )
  }
}

/**
 * 'Beta' header to indicate a feature is currently in development
 */
@Composable
fun BetaHeader(modifier: Modifier = Modifier) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
      .background(
        color = SignalTheme.colors.colorSurface2,
        shape = RoundedCornerShape(12.dp)
      )
      .padding(16.dp)
  ) {
    Icon(
      imageVector = ImageVector.vectorResource(id = R.drawable.symbol_info_24),
      contentDescription = stringResource(id = R.string.Beta__info),
      tint = MaterialTheme.colorScheme.onPrimaryContainer
    )
    Text(
      text = stringResource(id = R.string.Beta__this_is_beta),
      style = MaterialTheme.typography.bodyMedium,
      modifier = Modifier.padding(start = 12.dp)
    )
  }
}

@DayNightPreviews
@Composable
fun BetaLabelPreview() {
  Previews.Preview {
    TextWithBetaLabel("Signal Backups")
  }
}

@DayNightPreviews
@Composable
fun BetaLabelDisabledPreview() {
  Previews.Preview {
    TextWithBetaLabel("Signal Backups", enabled = false)
  }
}

@Preview(locale = "de")
@Composable
fun LongTextBetaLabelPreview() {
  Previews.Preview {
    Scaffold {
      TextWithBetaLabel(
        text = stringResource(id = R.string.RemoteBackupsSettingsFragment__signal_backups),
        textStyle = MaterialTheme.typography.headlineMedium,
        modifier = Modifier
          .fillMaxWidth()
          .horizontalGutters()
          .padding(it)
      )
    }
  }
}

@DayNightPreviews
@Composable
fun BetaHeaderPreview() {
  Previews.Preview {
    BetaHeader()
  }
}
