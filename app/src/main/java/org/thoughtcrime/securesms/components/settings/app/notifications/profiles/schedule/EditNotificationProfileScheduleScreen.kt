/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.schedule

import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.CircularProgressWrapper
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.compose.Snackbars
import org.signal.core.ui.compose.horizontalGutters
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.util.orderOfDaysInWeek
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileSchedule
import org.thoughtcrime.securesms.util.formatHours
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val DAY_TO_STARTING_LETTER: Map<DayOfWeek, Int> = mapOf(
  DayOfWeek.SUNDAY to R.string.EditNotificationProfileSchedule__sunday_first_letter,
  DayOfWeek.MONDAY to R.string.EditNotificationProfileSchedule__monday_first_letter,
  DayOfWeek.TUESDAY to R.string.EditNotificationProfileSchedule__tuesday_first_letter,
  DayOfWeek.WEDNESDAY to R.string.EditNotificationProfileSchedule__wednesday_first_letter,
  DayOfWeek.THURSDAY to R.string.EditNotificationProfileSchedule__thursday_first_letter,
  DayOfWeek.FRIDAY to R.string.EditNotificationProfileSchedule__friday_first_letter,
  DayOfWeek.SATURDAY to R.string.EditNotificationProfileSchedule__saturday_first_letter
)

@VisibleForTesting
object EditNotificationProfileScheduleTestTags {
  const val SCROLLER = "scroller"
  const val ROW_SCHEDULE = "row-schedule"
  const val START_TIME = "start-time"
  const val END_TIME = "end-time"
  const val BUTTON_NEXT = "button-next"
  const val DAY_PREFIX = "day-"

  fun day(day: DayOfWeek): String = DAY_PREFIX + day.name
}

/**
 * Edits a profile's schedule, either as a step in creating the profile or on its own from the profile's details.
 */
@Composable
fun EditNotificationProfileScheduleScreen(
  state: EditNotificationProfileScheduleState,
  onEvent: (EditNotificationProfileScheduleEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  Scaffolds.Settings(
    title = if (state.createMode) "" else stringResource(R.string.EditNotificationProfileSchedule__schedule),
    onNavigationClick = { onEvent(EditNotificationProfileScheduleEvent.NavigateBackClicked) },
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    snackbarHost = { NoDaysSnackbar(snackbar = state.snackbar, onEvent = onEvent) },
    modifier = modifier
  ) { contentPadding ->
    val schedule = state.schedule

    Column(
      modifier = Modifier
        .padding(contentPadding)
        .navigationBarsPadding()
    ) {
      Column(
        modifier = Modifier
          .weight(1f)
          .verticalScroll(rememberScrollState())
          .testTag(EditNotificationProfileScheduleTestTags.SCROLLER)
      ) {
        if (state.createMode) {
          Text(
            text = stringResource(R.string.EditNotificationProfileSchedule__add_a_schedule),
            style = MaterialTheme.typography.headlineMedium.copy(hyphens = Hyphens.Auto),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
              .fillMaxWidth()
              .horizontalGutters()
              .padding(top = 20.dp)
          )

          Text(
            text = stringResource(R.string.EditNotificationProfileSchedule__set_up_a_schedule_to_enable_this_notification_profile_automatically),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
              .fillMaxWidth()
              .horizontalGutters()
              .padding(top = 12.dp)
          )
        }

        if (schedule != null) {
          ScheduleEditor(schedule = schedule, onEvent = onEvent)
        }
      }

      CircularProgressWrapper(
        isLoading = state.isSaving,
        modifier = Modifier
          .align(Alignment.End)
          .horizontalGutters()
          .padding(bottom = 16.dp)
      ) {
        Buttons.LargeTonal(
          onClick = { onEvent(EditNotificationProfileScheduleEvent.NextClicked) },
          enabled = state.canSave,
          modifier = Modifier.testTag(EditNotificationProfileScheduleTestTags.BUTTON_NEXT)
        ) {
          Text(text = stringResource(nextLabel(state)))
        }
      }
    }
  }

  val dialog = state.dialog
  val schedule = state.schedule
  if (dialog is EditNotificationProfileScheduleState.Dialog.TimePicker && schedule != null) {
    Dialogs.TimePickerDialog(
      title = stringResource(if (dialog.isStart) R.string.EditNotificationProfileSchedule__set_start_time else R.string.EditNotificationProfileSchedule__set_end_time),
      initialTime = if (dialog.isStart) schedule.startTime() else schedule.endTime(),
      onTimeSelected = { onEvent(EditNotificationProfileScheduleEvent.TimeSelected(it)) },
      onDismissRequest = { onEvent(EditNotificationProfileScheduleEvent.DialogDismissed) }
    )
  }
}

@StringRes
private fun nextLabel(state: EditNotificationProfileScheduleState): Int {
  return when {
    !state.createMode -> R.string.EditNotificationProfileSchedule__save
    state.schedule?.enabled == true -> R.string.EditNotificationProfileSchedule__next
    else -> R.string.EditNotificationProfileSchedule__skip
  }
}

@Composable
private fun ScheduleEditor(schedule: NotificationProfileSchedule, onEvent: (EditNotificationProfileScheduleEvent) -> Unit) {
  Rows.ToggleRow(
    checked = schedule.enabled,
    text = stringResource(R.string.EditNotificationProfileSchedule__schedule),
    onCheckChanged = { onEvent(EditNotificationProfileScheduleEvent.ScheduleToggled(it)) },
    modifier = Modifier
      .padding(top = 16.dp)
      .testTag(EditNotificationProfileScheduleTestTags.ROW_SCHEDULE)
  )

  Row(
    horizontalArrangement = Arrangement.SpaceBetween,
    modifier = Modifier
      .fillMaxWidth()
      .horizontalGutters()
      .padding(top = 16.dp)
  ) {
    TimeColumn(
      label = R.string.EditNotificationProfileSchedule__start,
      time = schedule.startTime(),
      enabled = schedule.enabled,
      horizontalAlignment = Alignment.Start,
      onClick = { onEvent(EditNotificationProfileScheduleEvent.StartTimeClicked) },
      modifier = Modifier.testTag(EditNotificationProfileScheduleTestTags.START_TIME)
    )

    TimeColumn(
      label = R.string.EditNotificationProfileSchedule__end,
      time = schedule.endTime(),
      enabled = schedule.enabled,
      horizontalAlignment = Alignment.End,
      onClick = { onEvent(EditNotificationProfileScheduleEvent.EndTimeClicked) },
      modifier = Modifier.testTag(EditNotificationProfileScheduleTestTags.END_TIME)
    )
  }

  FlowRow(
    horizontalArrangement = Arrangement.SpaceEvenly,
    modifier = Modifier
      .fillMaxWidth()
      .padding(start = 16.dp, top = 24.dp, end = 16.dp)
  ) {
    val locale = LocalLocale.current.platformLocale
    val days = remember(locale) { locale.orderOfDaysInWeek() }

    days.forEach { day ->
      DayToggle(
        day = day,
        locale = locale,
        checked = day in schedule.daysEnabled,
        enabled = schedule.enabled,
        onEvent = onEvent
      )
    }
  }
}

@Composable
private fun TimeColumn(
  @StringRes label: Int,
  time: LocalTime,
  enabled: Boolean,
  horizontalAlignment: Alignment.Horizontal,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant

  Column(
    horizontalAlignment = horizontalAlignment,
    modifier = modifier.clickable(enabled = enabled, onClick = onClick)
  ) {
    Text(
      text = stringResource(label),
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.Bold,
      color = color
    )

    Text(
      text = formatTime(time),
      fontSize = 36.sp,
      color = color
    )
  }
}

/**
 * The time in the user's 12 or 24 hour format, with any AM/PM marker drawn smaller than the digits.
 */
@Composable
private fun formatTime(time: LocalTime): AnnotatedString {
  val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
  val locale = LocalLocale.current.platformLocale

  return remember(time, is24Hour, locale) {
    val formatted = time.formatHours(is24Hour = is24Hour, locale = locale)
    val amPm = DateTimeFormatter.ofPattern("a", locale).format(time)
    val amPmIndex = if (is24Hour) -1 else formatted.indexOf(string = amPm, ignoreCase = true)

    buildAnnotatedString {
      append(formatted)
      if (amPmIndex != -1) {
        addStyle(SpanStyle(fontSize = 20.sp), amPmIndex, amPmIndex + amPm.length)
      }
    }
  }
}

@Composable
private fun DayToggle(
  day: DayOfWeek,
  locale: Locale,
  checked: Boolean,
  enabled: Boolean,
  onEvent: (EditNotificationProfileScheduleEvent) -> Unit
) {
  val background = when {
    checked && enabled -> MaterialTheme.colorScheme.primary
    !enabled && !checked -> SignalTheme.colors.colorSurface1
    else -> MaterialTheme.colorScheme.outlineVariant
  }
  val textColor = if (checked && enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
  val dayName = remember(day, locale) { day.getDisplayName(TextStyle.FULL, locale) }

  val textStyle = MaterialTheme.typography.bodyLarge
  val circleSize = with(LocalDensity.current) { maxOf(32.dp, textStyle.lineHeight.toDp() + 8.dp) }

  Box(
    contentAlignment = Alignment.Center,
    modifier = Modifier
      .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
      .toggleable(
        value = checked,
        enabled = enabled,
        role = Role.Checkbox,
        onValueChange = { onEvent(EditNotificationProfileScheduleEvent.DayToggled(day)) }
      )
      .semantics { contentDescription = dayName }
      .testTag(EditNotificationProfileScheduleTestTags.day(day))
  ) {
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier
        .size(circleSize)
        .background(color = background, shape = CircleShape)
    ) {
      Text(
        text = stringResource(DAY_TO_STARTING_LETTER.getValue(day)),
        style = textStyle,
        color = textColor
      )
    }
  }
}

@Composable
private fun NoDaysSnackbar(
  snackbar: EditNotificationProfileScheduleState.Snackbar,
  onEvent: (EditNotificationProfileScheduleEvent) -> Unit
) {
  Snackbars.Host(
    message = when (snackbar) {
      EditNotificationProfileScheduleState.Snackbar.None -> null
      EditNotificationProfileScheduleState.Snackbar.NoDaysSelected -> stringResource(R.string.EditNotificationProfileSchedule__schedule_must_have_at_least_one_day)
    },
    onDismissed = { onEvent(EditNotificationProfileScheduleEvent.SnackbarDismissed) },
    duration = Snackbars.Duration.LONG
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun EditNotificationProfileScheduleScreenCreatePreview() {
  EditNotificationProfileScheduleScreen(
    state = EditNotificationProfileScheduleState(
      createMode = true,
      schedule = NotificationProfileSchedule(id = 1L, enabled = true, start = 2100, end = 700, daysEnabled = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY))
    ),
    onEvent = {}
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun EditNotificationProfileScheduleScreenEditPreview() {
  EditNotificationProfileScheduleScreen(
    state = EditNotificationProfileScheduleState(
      schedule = NotificationProfileSchedule(id = 1L, enabled = false)
    ),
    onEvent = {}
  )
}
