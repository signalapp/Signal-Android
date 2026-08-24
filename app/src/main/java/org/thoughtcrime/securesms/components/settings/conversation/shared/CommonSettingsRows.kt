/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

/**
 * Setting rows that read and behave identically wherever they appear. Which of them a conversation actually offers is
 * up to each screen -- note to self has no notification settings, release notes has no wallpaper, and so on.
 */

package org.thoughtcrime.securesms.components.settings.conversation.shared

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Rows
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.ExpirationUtil
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import org.signal.core.ui.R as CoreUiR

@Composable
fun DisappearingMessagesRow(
  lifespanSeconds: Int,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true
) {
  val context = LocalContext.current
  val icon = if (lifespanSeconds <= 0) R.drawable.symbol_timer_slash_24 else R.drawable.symbol_timer_24

  Rows.TextRow(
    text = stringResource(R.string.ConversationSettingsFragment__disappearing_messages),
    label = formatDisappearingMessagesLifespan(context, lifespanSeconds),
    icon = painterResource(icon),
    enabled = enabled,
    onClick = onClick,
    modifier = modifier
  )
}

@Composable
fun ChatColorAndWallpaperRow(
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Rows.TextRow(
    text = stringResource(R.string.preferences__chat_color_and_wallpaper),
    icon = painterResource(R.drawable.symbol_color_24),
    onClick = onClick,
    modifier = modifier
  )
}

@Composable
fun SoundsAndNotificationsRow(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true
) {
  Rows.TextRow(
    text = stringResource(R.string.ConversationSettingsFragment__sounds_and_notifications),
    icon = painterResource(CoreUiR.drawable.symbol_speaker_24),
    enabled = enabled,
    onClick = onClick,
    modifier = modifier
  )
}

@Composable
fun StarredMessagesRow(
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Rows.TextRow(
    text = stringResource(R.string.ConversationSettingsFragment__starred_messages),
    icon = painterResource(R.drawable.symbol_star_outline_24),
    onClick = onClick,
    modifier = modifier
  )
}

@Composable
fun BlockRow(
  isBlocked: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  isGroup: Boolean = false,
  enabled: Boolean = true
) {
  val titleRes = when {
    isBlocked && isGroup -> R.string.ConversationSettingsFragment__unblock_group
    isBlocked -> R.string.ConversationSettingsFragment__unblock
    isGroup -> R.string.ConversationSettingsFragment__block_group
    else -> R.string.ConversationSettingsFragment__block
  }

  Rows.TextRow(
    text = stringResource(titleRes),
    icon = painterResource(R.drawable.symbol_block_24),
    foregroundTint = if (isBlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
    enabled = enabled,
    onClick = onClick,
    modifier = modifier
  )
}

@Composable
fun ReportSpamRow(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true
) {
  Rows.TextRow(
    text = stringResource(R.string.ConversationFragment_report_spam),
    icon = painterResource(R.drawable.symbol_spam_24),
    foregroundTint = MaterialTheme.colorScheme.error,
    enabled = enabled,
    onClick = onClick,
    modifier = modifier
  )
}

@Composable
fun ArchiveChatRow(
  isArchived: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Rows.TextRow(
    text = stringResource(if (isArchived) R.string.ConversationListFragment_unarchive else R.string.ConversationSettingsFragment__archive_chat),
    icon = painterResource(if (isArchived) R.drawable.symbol_archive_up_24 else R.drawable.symbol_archive_24),
    onClick = onClick,
    modifier = modifier
  )
}

@Composable
fun DeleteChatRow(
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Rows.TextRow(
    text = stringResource(R.string.ConversationSettingsFragment__delete_chat),
    icon = painterResource(CoreUiR.drawable.symbol_trash_24),
    foregroundTint = MaterialTheme.colorScheme.error,
    onClick = onClick,
    modifier = modifier
  )
}

private fun formatDisappearingMessagesLifespan(context: Context, lifespanSeconds: Int): String {
  return if (lifespanSeconds <= 0) {
    context.getString(R.string.preferences_off)
  } else {
    ExpirationUtil.getExpirationDisplayValue(context, lifespanSeconds)
  }
}

/** The rows that adjust how a chat behaves. */
@DayNightPreviews
@Composable
private fun ChatSettingsRowsPreview() {
  Previews.Preview {
    Column {
      DisappearingMessagesRow(lifespanSeconds = 0, onClick = {})
      DisappearingMessagesRow(lifespanSeconds = 4.hours.inWholeSeconds.toInt(), onClick = {})
      DisappearingMessagesRow(lifespanSeconds = 7.days.inWholeSeconds.toInt(), enabled = false, onClick = {})
      ChatColorAndWallpaperRow(onClick = {})
      SoundsAndNotificationsRow(onClick = {})
      SoundsAndNotificationsRow(enabled = false, onClick = {})
      StarredMessagesRow(onClick = {})
    }
  }
}

/** The rows at the bottom of the screen, which are destructive and tinted to say so. */
@DayNightPreviews
@Composable
private fun DestructiveSettingsRowsPreview() {
  Previews.Preview {
    Column {
      BlockRow(isBlocked = false, onClick = {})
      BlockRow(isBlocked = true, onClick = {})
      BlockRow(isBlocked = false, isGroup = true, onClick = {})
      BlockRow(isBlocked = true, isGroup = true, onClick = {})
      ReportSpamRow(onClick = {})
      ArchiveChatRow(isArchived = false, onClick = {})
      ArchiveChatRow(isArchived = true, onClick = {})
      DeleteChatRow(onClick = {})
    }
  }
}
