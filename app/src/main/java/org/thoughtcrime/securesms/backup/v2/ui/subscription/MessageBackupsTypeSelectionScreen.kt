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
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.res.pluralStringResource
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
import org.signal.core.ui.Dialogs
import org.signal.core.ui.Previews
import org.signal.core.ui.Scaffolds
import org.signal.core.ui.SignalPreview
import org.signal.core.ui.theme.SignalTheme
import org.signal.core.util.bytes
import org.signal.core.util.money.FiatMoney
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.fonts.SignalSymbols
import org.thoughtcrime.securesms.fonts.SignalSymbols.SignalSymbol
import org.thoughtcrime.securesms.payments.FiatMoneyUtil
import org.thoughtcrime.securesms.util.ByteUnit
import java.math.BigDecimal
import java.util.Currency

/**
 * Screen which allows the user to select their preferred backup type.
 */
@OptIn(ExperimentalTextApi::class)
@Composable
fun MessageBackupsTypeSelectionScreen(
  stage: MessageBackupsStage,
  currentBackupTier: MessageBackupTier?,
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
            painter = painterResource(id = R.drawable.image_signal_backups_plans),
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
        enabled = selectedBackupTier != currentBackupTier && selectedBackupTier != null,
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

      when (stage) {
        MessageBackupsStage.CREATING_IN_APP_PAYMENT -> Dialogs.IndeterminateProgressDialog()
        MessageBackupsStage.PROCESS_PAYMENT -> Dialogs.IndeterminateProgressDialog()
        MessageBackupsStage.PROCESS_FREE -> Dialogs.IndeterminateProgressDialog()
        else -> Unit
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
      stage = MessageBackupsStage.TYPE_SELECTION,
      selectedBackupTier = MessageBackupTier.FREE,
      availableBackupTypes = testBackupTypes(),
      onMessageBackupsTierSelected = { selectedBackupsType = it },
      onNavigationClick = {},
      onReadMoreClicked = {},
      onNextClicked = {},
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
      stage = MessageBackupsStage.TYPE_SELECTION,
      selectedBackupTier = MessageBackupTier.FREE,
      availableBackupTypes = testBackupTypes(),
      onMessageBackupsTierSelected = { selectedBackupsType = it },
      onNavigationClick = {},
      onReadMoreClicked = {},
      onNextClicked = {},
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
  enabled: Boolean = true,
  iconColors: MessageBackupsTypeIconColors = MessageBackupsTypeIconColors.default()
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
    if (isCurrent) {
      Text(
        text = buildAnnotatedString {
          SignalSymbol(weight = SignalSymbols.Weight.REGULAR, glyph = SignalSymbols.Glyph.CHECKMARK)
          append(" ")
          append(stringResource(R.string.MessageBackupsTypeSelectionScreen__current_plan))
        },
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
          .padding(bottom = 12.dp)
          .background(
            color = SignalTheme.colors.colorTransparent1,
            shape = RoundedCornerShape(14.dp)
          )
          .padding(vertical = 4.dp, horizontal = 12.dp)
      )
    }

    Text(
      text = getFormattedPricePerMonth(messageBackupsType),
      style = MaterialTheme.typography.titleSmall
    )

    Text(
      text = when (messageBackupsType) {
        is MessageBackupsType.Free -> pluralStringResource(id = R.plurals.MessageBackupsTypeSelectionScreen__text_plus_d_days_of_media, messageBackupsType.mediaRetentionDays, messageBackupsType.mediaRetentionDays)
        is MessageBackupsType.Paid -> stringResource(id = R.string.MessageBackupsTypeSelectionScreen__text_plus_all_your_media)
      },
      style = MaterialTheme.typography.titleMedium
    )

    val featureIconTint = if (isSelected) {
      iconColors.iconColorSelected
    } else {
      iconColors.iconColorNormal
    }

    Column(
      verticalArrangement = spacedBy(4.dp),
      modifier = Modifier
        .padding(top = 8.dp)
        .padding(horizontal = 16.dp)
    ) {
      getFeatures(messageBackupsType = messageBackupsType).forEach {
        MessageBackupsTypeFeatureRow(messageBackupsTypeFeature = it, iconTint = featureIconTint)
      }
    }
  }
}

@Composable
private fun getFormattedPricePerMonth(messageBackupsType: MessageBackupsType): String {
  return when (messageBackupsType) {
    is MessageBackupsType.Free -> stringResource(id = R.string.MessageBackupsTypeSelectionScreen__free)
    is MessageBackupsType.Paid -> {
      val formattedAmount = FiatMoneyUtil.format(LocalContext.current.resources, messageBackupsType.pricePerMonth, FiatMoneyUtil.formatOptions().trimZerosAfterDecimal())
      stringResource(id = R.string.MessageBackupsTypeSelectionScreen__s_month, formattedAmount)
    }
  }
}

@Composable
private fun getFeatures(messageBackupsType: MessageBackupsType): List<MessageBackupsTypeFeature> {
  return when (messageBackupsType) {
    is MessageBackupsType.Free -> persistentListOf(
      MessageBackupsTypeFeature(
        iconResourceId = R.drawable.symbol_thread_compact_bold_16,
        label = stringResource(id = R.string.MessageBackupsTypeSelectionScreen__full_text_message_backup)
      ),
      MessageBackupsTypeFeature(
        iconResourceId = R.drawable.symbol_album_compact_bold_16,
        label = pluralStringResource(
          id = R.plurals.MessageBackupsTypeSelectionScreen__last_d_days_of_media,
          count = messageBackupsType.mediaRetentionDays,
          messageBackupsType.mediaRetentionDays
        )
      )
    )

    is MessageBackupsType.Paid -> {
      val photoCount = messageBackupsType.storageAllowanceBytes / ByteUnit.MEGABYTES.toBytes(2)
      val photoCountThousands = photoCount / 1000
      val sizeUnitString = messageBackupsType.storageAllowanceBytes.bytes.toUnitString(spaced = false)

      persistentListOf(
        MessageBackupsTypeFeature(
          iconResourceId = R.drawable.symbol_thread_compact_bold_16,
          label = stringResource(id = R.string.MessageBackupsTypeSelectionScreen__full_text_message_backup)
        ),
        MessageBackupsTypeFeature(
          iconResourceId = R.drawable.symbol_album_compact_bold_16,
          label = stringResource(id = R.string.MessageBackupsTypeSelectionScreen__full_media_backup)
        ),
        MessageBackupsTypeFeature(
          iconResourceId = R.drawable.symbol_thread_compact_bold_16,
          label = stringResource(
            id = R.string.MessageBackupsTypeSelectionScreen__s_of_storage_s_photos,
            sizeUnitString,
            "~${photoCountThousands}K"
          )
        ),
        MessageBackupsTypeFeature(
          iconResourceId = R.drawable.symbol_heart_compact_bold_16,
          label = stringResource(id = R.string.MessageBackupsTypeSelectionScreen__thanks_for_supporting_signal)
        )
      )
    }
  }
}

fun testBackupTypes(): List<MessageBackupsType> {
  return listOf(
    MessageBackupsType.Free(
      mediaRetentionDays = 30
    ),
    MessageBackupsType.Paid(
      pricePerMonth = FiatMoney(BigDecimal.ONE, Currency.getInstance("USD")),
      storageAllowanceBytes = 107374182400
    )
  )
}

/**
 * Feature row iconography coloring
 */
@Immutable
data class MessageBackupsTypeIconColors(
  val iconColorNormal: Color,
  val iconColorSelected: Color
) {
  companion object {
    @Composable
    fun default(): MessageBackupsTypeIconColors {
      return MessageBackupsTypeIconColors(
        iconColorNormal = MaterialTheme.colorScheme.onSurfaceVariant,
        iconColorSelected = MaterialTheme.colorScheme.primary
      )
    }
  }
}
