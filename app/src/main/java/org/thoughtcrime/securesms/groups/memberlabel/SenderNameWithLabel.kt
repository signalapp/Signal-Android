/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.memberlabel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews

private val defaultLabelModifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
private val defaultLabelTextStyle: @Composable () -> TextStyle = { MaterialTheme.typography.bodySmall }

/**
 * Displays a sender name with an optional member label pill with label colors derived from [senderColor].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SenderNameWithLabel(
  senderName: String,
  senderColor: Color,
  memberLabel: MemberLabel?,
  modifier: Modifier = Modifier
) {
  SenderNameWithLabel(
    senderName = senderName,
    senderColor = senderColor,
    memberLabel = memberLabel,
    modifier = modifier,
    labelSlot = { label ->
      MemberLabelPill(
        emoji = label.emoji,
        text = label.text,
        tintColor = senderColor,
        modifier = defaultLabelModifier,
        textStyle = defaultLabelTextStyle()
      )
    }
  )
}

/**
 * Displays a sender name with an optional member label pill with explicit label colors.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SenderNameWithLabel(
  senderName: String,
  senderColor: Color,
  memberLabel: MemberLabel?,
  labelTextColor: Color,
  labelBackgroundColor: Color,
  modifier: Modifier = Modifier
) {
  SenderNameWithLabel(
    senderName = senderName,
    senderColor = senderColor,
    memberLabel = memberLabel,
    modifier = modifier,
    labelSlot = { label ->
      MemberLabelPill(
        emoji = label.emoji,
        text = label.text,
        textColor = labelTextColor,
        backgroundColor = labelBackgroundColor,
        modifier = defaultLabelModifier,
        textStyle = defaultLabelTextStyle()
      )
    }
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SenderNameWithLabel(
  senderName: String,
  senderColor: Color,
  memberLabel: MemberLabel?,
  modifier: Modifier = Modifier,
  labelSlot: @Composable (MemberLabel) -> Unit
) {
  FlowRow(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    verticalArrangement = Arrangement.spacedBy(2.dp),
    itemVerticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = senderName,
      color = senderColor,
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.Bold,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )

    if (memberLabel != null) {
      labelSlot(memberLabel)
    }
  }
}

@DayNightPreviews
@Composable
private fun SenderNameWithLabelPreview() = Previews.Preview {
  Box(modifier = Modifier.width(200.dp)) {
    SenderNameWithLabel(
      senderName = "Foo Bar",
      senderColor = Color(0xFF7C4DFF),
      memberLabel = MemberLabel(emoji = "\uD83D\uDC36", text = "Vet Coordinator")
    )
  }
}

@DayNightPreviews
@Composable
private fun SenderNameWithLabelLongLabelPreview() = Previews.Preview {
  Box(modifier = Modifier.width(200.dp)) {
    SenderNameWithLabel(
      senderName = "Foo Bar",
      senderColor = Color(0xFF7C4DFF),
      memberLabel = MemberLabel(emoji = "🧠", text = "Zero-Knowledge Know-It-It-All")
    )
  }
}

@DayNightPreviews
@Composable
private fun SenderNameWithLabelLongNamePreview() = Previews.Preview {
  Box(modifier = Modifier.width(200.dp)) {
    SenderNameWithLabel(
      senderName = "Cassandra NullPointer-Exception",
      senderColor = Color(0xFF7C4DFF),
      memberLabel = MemberLabel(emoji = "🧠", text = "Vet Coordinator")
    )
  }
}

@DayNightPreviews
@Composable
private fun SenderNameWithLabelNoLabelPreview() = Previews.Preview {
  Box(modifier = Modifier.width(200.dp)) {
    SenderNameWithLabel(
      senderName = "Sam",
      senderColor = Color(0xFF4CAF50),
      memberLabel = null
    )
  }
}

@DayNightPreviews
@Composable
private fun SenderNameWithLabelExplicitColorsPreview() = Previews.Preview {
  Box(modifier = Modifier.width(200.dp)) {
    SenderNameWithLabel(
      senderName = "Foo Bar",
      senderColor = MaterialTheme.colorScheme.onSurface,
      memberLabel = MemberLabel(emoji = "\uD83D\uDC36", text = "Vet Coordinator"),
      labelTextColor = MaterialTheme.colorScheme.onSurface,
      labelBackgroundColor = MaterialTheme.colorScheme.surfaceVariant
    )
  }
}
