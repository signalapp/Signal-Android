/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.thoughtcrime.securesms.backup.v2.ui.subscription

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement.Absolute.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withAnnotation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.signal.core.ui.Buttons
import org.signal.core.ui.Previews
import org.signal.core.ui.Scaffolds
import org.signal.core.ui.SignalPreview
import org.signal.core.ui.theme.SignalTheme
import org.signal.core.util.money.FiatMoney
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.payments.FiatMoneyUtil
import java.math.BigDecimal

/**
 * Screen which allows the user to select their preferred backup type.
 */
@OptIn(ExperimentalTextApi::class)
@Composable
fun MessageBackupsTypeSelectionScreen(
  selectedBackupTier: MessageBackupTier?,
  availableBackupTypes: List<MessageBackupsType>,
  onMessageBackupsTierSelected: (MessageBackupTier) -> Unit,
  onNavigationClick: () -> Unit,
  onReadMoreClicked: () -> Unit,
  onNextClicked: () -> Unit
) {
  Scaffolds.Settings(
    title = "",
    onNavigationClick = onNavigationClick,
    navigationIconPainter = painterResource(id = R.drawable.symbol_arrow_left_24)
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .padding(paddingValues)
        .padding(horizontal = dimensionResource(id = R.dimen.core_ui__gutter))
        .fillMaxSize()
    ) {
      LazyColumn(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
      ) {
        item {
          Image(
            painter = painterResource(id = R.drawable.ic_signal_logo_large), // TODO [message-backups] Finalized art asset
            contentDescription = null,
            modifier = Modifier.size(88.dp)
          )
        }

        item {
          Text(
            text = "Choose your backup type", // TODO [message-backups] Finalized copy
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 12.dp)
          )
        }

        item {
          // TODO [message-backups] Finalized copy
          val primaryColor = MaterialTheme.colorScheme.primary
          val readMoreString = buildAnnotatedString {
            append("All backups are end-to-end encrypted. Signal is a non-profit—paying for backups helps support our mission. ")
            withAnnotation(tag = "URL", annotation = "read-more") {
              withStyle(
                style = SpanStyle(
                  color = primaryColor
                )
              ) {
                append("Read more")
              }
            }
          }

          ClickableText(
            text = readMoreString,
            style = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface),
            onClick = { offset ->
              readMoreString
                .getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()?.let { onReadMoreClicked() }
            },
            modifier = Modifier.padding(top = 8.dp)
          )
        }

        itemsIndexed(
          availableBackupTypes,
          { _, item -> item.tier }
        ) { index, item ->
          MessageBackupsTypeBlock(
            messageBackupsType = item,
            isSelected = item.tier == selectedBackupTier,
            onSelected = { onMessageBackupsTierSelected(item.tier) },
            modifier = Modifier.padding(top = if (index == 0) 20.dp else 18.dp)
          )
        }
      }

      Buttons.LargePrimary(
        onClick = onNextClicked,
        enabled = selectedBackupTier != null,
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 16.dp)
      ) {
        Text(
          text = "Next" // TODO [message-backups] Finalized copy
        )
      }
    }
  }
}

@SignalPreview
@Composable
private fun MessageBackupsTypeSelectionScreenPreview() {
  var selectedBackupsType by remember { mutableStateOf(MessageBackupTier.FREE) }

  Previews.Preview {
    MessageBackupsTypeSelectionScreen(
      selectedBackupTier = MessageBackupTier.FREE,
      availableBackupTypes = emptyList(),
      onMessageBackupsTierSelected = { selectedBackupsType = it },
      onNavigationClick = {},
      onReadMoreClicked = {},
      onNextClicked = {}
    )
  }
}

@Composable
fun MessageBackupsTypeBlock(
  messageBackupsType: MessageBackupsType,
  isSelected: Boolean,
  onSelected: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true
) {
  val borderColor = if (isSelected) {
    MaterialTheme.colorScheme.primary
  } else {
    Color.Transparent
  }

  val background = if (isSelected) {
    MaterialTheme.colorScheme.secondaryContainer
  } else {
    SignalTheme.colors.colorSurface2
  }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(color = background, shape = RoundedCornerShape(18.dp))
      .border(width = 2.dp, color = borderColor, shape = RoundedCornerShape(18.dp))
      .clip(shape = RoundedCornerShape(18.dp))
      .clickable(onClick = onSelected, enabled = enabled)
      .padding(vertical = 16.dp, horizontal = 20.dp)
  ) {
    Text(
      text = formatCostPerMonth(messageBackupsType.pricePerMonth),
      style = MaterialTheme.typography.titleSmall
    )

    Text(
      text = messageBackupsType.title,
      style = MaterialTheme.typography.titleMedium
    )

    Column(
      verticalArrangement = spacedBy(4.dp),
      modifier = Modifier
        .padding(top = 8.dp)
        .padding(horizontal = 16.dp)
    ) {
      messageBackupsType.features.forEach {
        MessageBackupsTypeFeatureRow(messageBackupsTypeFeature = it)
      }
    }
  }
}

@Composable
private fun formatCostPerMonth(pricePerMonth: FiatMoney): String {
  return if (pricePerMonth.amount == BigDecimal.ZERO) {
    "Free"
  } else {
    "${FiatMoneyUtil.format(LocalContext.current.resources, pricePerMonth, FiatMoneyUtil.formatOptions().trimZerosAfterDecimal())}/month"
  }
}
