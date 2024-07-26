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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withAnnotation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.persistentListOf
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
import java.util.Currency

/**
 * Screen which allows the user to select their preferred backup type.
 */
@OptIn(ExperimentalTextApi::class)
@Composable
fun MessageBackupsTypeSelectionScreen(
  currentBackupTier: MessageBackupTier?,
  selectedBackupTier: MessageBackupTier?,
  availableBackupTypes: List<MessageBackupsType>,
  onMessageBackupsTierSelected: (MessageBackupTier) -> Unit,
  onNavigationClick: () -> Unit,
  onReadMoreClicked: () -> Unit,
  onNextClicked: () -> Unit,
  onCancelSubscriptionClicked: () -> Unit
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
            text = stringResource(id = R.string.MessagesBackupsTypeSelectionScreen__choose_your_backup_plan),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 12.dp)
          )
        }

        item {
          val primaryColor = MaterialTheme.colorScheme.primary
          val readMoreString = buildAnnotatedString {
            append(stringResource(id = R.string.MessageBackupsTypeSelectionScreen__all_backups_are_end_to_end_encrypted))

            val readMore = stringResource(id = R.string.MessageBackupsTypeSelectionScreen__read_more)
            append(" ")
            withAnnotation(tag = "URL", annotation = "read-more") {
              withStyle(
                style = SpanStyle(
                  color = primaryColor
                )
              ) {
                append(readMore)
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
            isCurrent = item.tier == currentBackupTier,
            isSelected = item.tier == selectedBackupTier,
            onSelected = { onMessageBackupsTierSelected(item.tier) },
            modifier = Modifier.padding(top = if (index == 0) 20.dp else 18.dp)
          )
        }
      }

      val hasCurrentBackupTier = currentBackupTier != null

      Buttons.LargePrimary(
        onClick = onNextClicked,
        enabled = selectedBackupTier != currentBackupTier && hasCurrentBackupTier,
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = if (hasCurrentBackupTier) 10.dp else 16.dp)
      ) {
        Text(
          text = stringResource(
            id = if (currentBackupTier == null) {
              R.string.MessageBackupsTypeSelectionScreen__next
            } else {
              R.string.MessageBackupsTypeSelectionScreen__change_backup_type
            }
          )
        )
      }

      if (hasCurrentBackupTier) {
        TextButton(
          onClick = onCancelSubscriptionClicked,
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
        ) {
          Text(text = stringResource(id = R.string.MessageBackupsTypeSelectionScreen__cancel_subscription))
        }
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
      availableBackupTypes = testBackupTypes(),
      onMessageBackupsTierSelected = { selectedBackupsType = it },
      onNavigationClick = {},
      onReadMoreClicked = {},
      onNextClicked = {},
      onCancelSubscriptionClicked = {},
      currentBackupTier = null
    )
  }
}

@SignalPreview
@Composable
private fun MessageBackupsTypeSelectionScreenWithCurrentTierPreview() {
  var selectedBackupsType by remember { mutableStateOf(MessageBackupTier.FREE) }

  Previews.Preview {
    MessageBackupsTypeSelectionScreen(
      selectedBackupTier = MessageBackupTier.FREE,
      availableBackupTypes = testBackupTypes(),
      onMessageBackupsTierSelected = { selectedBackupsType = it },
      onNavigationClick = {},
      onReadMoreClicked = {},
      onNextClicked = {},
      onCancelSubscriptionClicked = {},
      currentBackupTier = MessageBackupTier.PAID
    )
  }
}

@Composable
fun MessageBackupsTypeBlock(
  messageBackupsType: MessageBackupsType,
  isCurrent: Boolean,
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

  Box(
    modifier = modifier
      .fillMaxWidth()
      .background(color = background, shape = RoundedCornerShape(18.dp))
      .border(width = 2.dp, color = borderColor, shape = RoundedCornerShape(18.dp))
      .clip(shape = RoundedCornerShape(18.dp))
      .clickable(onClick = onSelected, enabled = enabled)
      .padding(vertical = 16.dp, horizontal = 20.dp)
  ) {
    Column {
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

    if (isCurrent) {
      Icon(
        painter = painterResource(id = R.drawable.symbol_check_24),
        contentDescription = null,
        modifier = Modifier.align(Alignment.TopEnd)
      )
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

private fun testBackupTypes(): List<MessageBackupsType> {
  return listOf(
    MessageBackupsType(
      tier = MessageBackupTier.FREE,
      pricePerMonth = FiatMoney(BigDecimal.ZERO, Currency.getInstance("USD")),
      title = "Text + 30 days of media",
      features = persistentListOf(
        MessageBackupsTypeFeature(
          iconResourceId = R.drawable.symbol_thread_compact_bold_16,
          label = "Full text message backup"
        ),
        MessageBackupsTypeFeature(
          iconResourceId = R.drawable.symbol_album_compact_bold_16,
          label = "Last 30 days of media"
        )
      )
    ),
    MessageBackupsType(
      tier = MessageBackupTier.PAID,
      pricePerMonth = FiatMoney(BigDecimal.ONE, Currency.getInstance("USD")),
      title = "Text + All your media",
      features = persistentListOf(
        MessageBackupsTypeFeature(
          iconResourceId = R.drawable.symbol_thread_compact_bold_16,
          label = "Full text message backup"
        ),
        MessageBackupsTypeFeature(
          iconResourceId = R.drawable.symbol_album_compact_bold_16,
          label = "Full media backup"
        ),
        MessageBackupsTypeFeature(
          iconResourceId = R.drawable.symbol_thread_compact_bold_16,
          label = "1TB of storage (~250K photos)"
        ),
        MessageBackupsTypeFeature(
          iconResourceId = R.drawable.symbol_heart_compact_bold_16,
          label = "Thanks for supporting Signal!"
        )
      )
    )
  )
}
