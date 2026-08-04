/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit

import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.DropdownMenus
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalIcon
import org.signal.core.ui.compose.SignalIcons
import org.signal.mediasend.R
import org.signal.mediasend.test.TestTags
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

/**
 * Renders the label for a scheduled send time, e.g. "Tomorrow at 8:00 AM". Injected because localized date formatting
 * lives with the host app.
 */
val LocalScheduledSendTimeFormatter = compositionLocalOf<(Long) -> String> {
  { timeMs ->
    DateTimeFormatter
      .ofLocalizedDateTime(FormatStyle.SHORT)
      .format(Instant.ofEpochMilli(timeMs).atZone(ZoneId.systemDefault()))
  }
}

/**
 * A time the user can schedule a send for, offered by [ScheduleSendMenu].
 */
sealed interface ScheduleSendOption {

  /**
   * One of the suggested times, as a timestamp matching [System.currentTimeMillis].
   */
  data class PresetTime(val timeMs: Long) : ScheduleSendOption

  /**
   * The user wants to choose a date and time themselves.
   */
  data object PickTime : ScheduleSendOption

  companion object {
    private val PRESET_HOURS = intArrayOf(8, 12, 18, 21)

    /**
     * The options to offer at [currentTimeMs], ordered for a menu that opens above its trigger: the soonest suggestion
     * sits closest to the send button and [PickTime] furthest from it.
     *
     * Suggestions are the next three [PRESET_HOURS] on the clock, plus Monday morning when the week is already over.
     */
    fun forCurrentTime(currentTimeMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): List<ScheduleSendOption> {
      var dateTime: LocalDateTime = currentTimeMs.toLocalDateTime(zoneId)

      var presetIndex = PRESET_HOURS.indexOfFirst { it > dateTime.hour }
      if (presetIndex == -1) {
        dateTime = dateTime.plusDays(1)
        presetIndex = 0
      }

      dateTime = dateTime.withMinute(0).withSecond(0)

      val times = ArrayList<Long>(5)
      while (times.size < 3) {
        dateTime = dateTime.withHour(PRESET_HOURS[presetIndex])
        times += dateTime.toEpochMillis(zoneId)
        presetIndex++

        if (presetIndex >= PRESET_HOURS.size) {
          presetIndex = 0
          dateTime = dateTime.plusDays(1)
        }
      }

      val now = currentTimeMs.toLocalDateTime(zoneId)
      if (now.dayOfWeek == DayOfWeek.FRIDAY || now.dayOfWeek == DayOfWeek.SATURDAY) {
        times += now
          .with(TemporalAdjusters.next(DayOfWeek.MONDAY))
          .withHour(8)
          .withMinute(0)
          .withSecond(0)
          .toEpochMillis(zoneId)
      }

      return listOf(PickTime) + times.reversed().map { PresetTime(it) }
    }

    private fun Long.toLocalDateTime(zoneId: ZoneId): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(this), zoneId)

    private fun LocalDateTime.toEpochMillis(zoneId: ZoneId): Long = TimeUnit.SECONDS.toMillis(atZone(zoneId).toEpochSecond())
  }
}

/**
 * Menu of times the user can schedule their send for. Options are computed each time the menu is shown, so that a menu
 * reopened after a preset hour has passed does not offer a time in the past.
 */
@Composable
fun ScheduleSendMenu(
  controller: DropdownMenus.MenuController,
  onOptionClick: (ScheduleSendOption) -> Unit,
  modifier: Modifier = Modifier
) {
  DropdownMenus.Menu(
    controller = controller,
    offsetX = 0.dp,
    modifier = modifier
  ) {
    ScheduleSendOptions(
      options = remember(controller.isShown()) { ScheduleSendOption.forCurrentTime(System.currentTimeMillis()) },
      onOptionClick = {
        controller.hide()
        onOptionClick(it)
      }
    )
  }
}

@Composable
internal fun ScheduleSendOptions(
  options: List<ScheduleSendOption>,
  onOptionClick: (ScheduleSendOption) -> Unit
) {
  options.forEach { option ->
    ScheduleSendMenuItem(
      option = option,
      onClick = { onOptionClick(option) }
    )
  }
}

@Composable
private fun ScheduleSendMenuItem(
  option: ScheduleSendOption,
  onClick: () -> Unit
) {
  val label = when (option) {
    is ScheduleSendOption.PresetTime -> LocalScheduledSendTimeFormatter.current(option.timeMs)
    ScheduleSendOption.PickTime -> stringResource(R.string.ScheduleSendMenu__pick_date_and_time)
  }

  val testTag = when (option) {
    is ScheduleSendOption.PresetTime -> TestTags.scheduleSendPresetOption(option.timeMs)
    ScheduleSendOption.PickTime -> TestTags.SCHEDULE_SEND_PICK_TIME_OPTION
  }

  DropdownMenus.Item(
    modifier = Modifier.testTag(testTag),
    text = {
      Row(
        horizontalArrangement = spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          painter = option.icon.painter,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurface
        )

        Text(text = label)
      }
    },
    onClick = onClick
  )
}

private val ScheduleSendOption.icon: SignalIcon
  get() = when (this) {
    is ScheduleSendOption.PresetTime -> if (LocalDateTime.ofInstant(Instant.ofEpochMilli(timeMs), ZoneId.systemDefault()).hour >= 18) SignalIcons.Nighttime else SignalIcons.Daytime
    ScheduleSendOption.PickTime -> SignalIcons.Calendar
  }

@DayNightPreviews
@Composable
private fun ScheduleSendMenuPreview() {
  Previews.Preview {
    ScheduleSendMenu(
      controller = remember { DropdownMenus.MenuController().apply { show() } },
      onOptionClick = {}
    )
  }
}
