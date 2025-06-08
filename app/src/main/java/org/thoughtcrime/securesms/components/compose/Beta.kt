package org.thoughtcrime.securesms.components.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalPreview
import org.thoughtcrime.securesms.R

/**
 * Adds a 'Beta' label next to [text] to indicate a feature is in development
 */
@Composable
fun TextWithBetaLabel(
  text: String,
  textStyle: TextStyle = TextStyle.Default,
  modifier: Modifier = Modifier
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
  ) {
    Text(
      text = text,
      style = textStyle
    )
    Text(
      text = stringResource(R.string.Beta__beta_title).uppercase(),
      color = MaterialTheme.colorScheme.onPrimaryContainer,
      style = MaterialTheme.typography.labelSmall,
      modifier = Modifier
        .padding(horizontal = 4.dp)
        .background(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(28.dp))
        .padding(horizontal = 12.dp, vertical = 4.dp)
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
        color = MaterialTheme.colorScheme.surfaceVariant,
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

@SignalPreview
@Composable
fun BetaLabelPreview() {
  Previews.Preview {
    TextWithBetaLabel("Signal Backups")
  }
}

@SignalPreview
@Composable
fun BetaHeaderPreview() {
  Previews.Preview {
    BetaHeader()
  }
}
