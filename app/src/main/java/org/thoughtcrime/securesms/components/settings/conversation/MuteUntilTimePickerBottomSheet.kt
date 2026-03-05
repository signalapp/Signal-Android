package org.thoughtcrime.securesms.components.settings.conversation

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.setFragmentResult
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import org.signal.core.ui.BottomSheetUtil
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.ComposeBottomSheetDialogFragment
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.atMidnight
import org.thoughtcrime.securesms.util.atUTC
import org.thoughtcrime.securesms.util.formatHours
import org.thoughtcrime.securesms.util.toLocalDateTime
import org.thoughtcrime.securesms.util.toMillis
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

class MuteUntilTimePickerBottomSheet : ComposeBottomSheetDialogFragment() {

  override val peekHeightPercentage: Float = 0.66f

  companion object {
    const val REQUEST_KEY = "mute_until_result"
    const val RESULT_TIMESTAMP = "timestamp"

    @JvmStatic
    fun show(fragmentManager: FragmentManager) {
      val fragment = MuteUntilTimePickerBottomSheet()
      fragment.show(fragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
    }
  }

  @Composable
  override fun SheetContent() {
    val context = LocalContext.current
    val now = remember { LocalDateTime.now() }

    val defaultDateTime = remember {
      if (now.hour < 17) {
        now.withHour(17).withMinute(0).withSecond(0).withNano(0)
      } else {
        val nextMorning = if (now.dayOfWeek == DayOfWeek.FRIDAY || now.dayOfWeek == DayOfWeek.SATURDAY || now.dayOfWeek == DayOfWeek.SUNDAY) {
          now.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        } else {
          now.plusDays(1)
        }
        nextMorning.withHour(8).withMinute(0).withSecond(0).withNano(0)
      }
    }

    var selectedDate by remember { mutableLongStateOf(defaultDateTime.toMillis()) }
    var selectedHour by remember { mutableIntStateOf(defaultDateTime.hour) }
    var selectedMinute by remember { mutableIntStateOf(defaultDateTime.minute) }

    val dateText = remember(selectedDate) {
      DateUtils.getDayPrecisionTimeString(context, Locale.getDefault(), selectedDate)
    }

    val timeText = remember(selectedHour, selectedMinute) {
      LocalTime.of(selectedHour, selectedMinute).formatHours(context)
    }

    val zonedDateTime = remember { ZonedDateTime.now() }
    val timezoneDisclaimer = remember {
      val zoneOffsetFormatter = DateTimeFormatter.ofPattern("OOOO")
      val zoneNameFormatter = DateTimeFormatter.ofPattern("zzzz")
      context.getString(
        R.string.MuteUntilTimePickerBottomSheet__timezone_disclaimer,
        zoneOffsetFormatter.format(zonedDateTime),
        zoneNameFormatter.format(zonedDateTime)
      )
    }

    MuteUntilSheetContent(
      dateText = dateText,
      timeText = timeText,
      timezoneDisclaimer = timezoneDisclaimer,
      onDateClick = {
        val local = LocalDateTime.now().atMidnight().atUTC().toMillis()
        val datePicker = MaterialDatePicker.Builder.datePicker()
          .setTitleText(context.getString(R.string.MuteUntilTimePickerBottomSheet__select_date_title))
          .setSelection(selectedDate)
          .setCalendarConstraints(CalendarConstraints.Builder().setStart(local).setValidator(DateValidatorPointForward.now()).build())
          .build()

        datePicker.addOnDismissListener {
          datePicker.clearOnDismissListeners()
          datePicker.clearOnPositiveButtonClickListeners()
        }

        datePicker.addOnPositiveButtonClickListener {
          selectedDate = it.toLocalDateTime(ZoneOffset.UTC).atZone(ZoneId.systemDefault()).toMillis()
        }
        datePicker.show(childFragmentManager, "DATE_PICKER")
      },
      onTimeClick = {
        val timeFormat = if (DateFormat.is24HourFormat(context)) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H
        val timePicker = MaterialTimePicker.Builder()
          .setTimeFormat(timeFormat)
          .setHour(selectedHour)
          .setMinute(selectedMinute)
          .setTitleText(context.getString(R.string.MuteUntilTimePickerBottomSheet__select_time_title))
          .build()

        timePicker.addOnDismissListener {
          timePicker.clearOnDismissListeners()
          timePicker.clearOnPositiveButtonClickListeners()
        }

        timePicker.addOnPositiveButtonClickListener {
          selectedHour = timePicker.hour
          selectedMinute = timePicker.minute
        }
        timePicker.show(childFragmentManager, "TIME_PICKER")
      },
      onMuteClick = {
        val timestamp = selectedDate.toLocalDateTime()
          .withHour(selectedHour)
          .withMinute(selectedMinute)
          .withSecond(0)
          .withNano(0)
          .toMillis()

        if (timestamp > System.currentTimeMillis()) {
          setFragmentResult(REQUEST_KEY, bundleOf(RESULT_TIMESTAMP to timestamp))
          dismissAllowingStateLoss()
        }
      }
    )
  }
}

@Composable
private fun MuteUntilSheetContent(
  dateText: String,
  timeText: String,
  timezoneDisclaimer: String,
  onDateClick: () -> Unit,
  onTimeClick: () -> Unit,
  onMuteClick: () -> Unit
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier.fillMaxWidth()
  ) {
    BottomSheets.Handle()

    Text(
      text = stringResource(R.string.MuteUntilTimePickerBottomSheet__dialog_title),
      style = MaterialTheme.typography.titleLarge,
      modifier = Modifier.padding(top = 18.dp, bottom = 24.dp)
    )

    Text(
      text = timezoneDisclaimer,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
      modifier = Modifier
        .padding(horizontal = 56.dp)
        .align(Alignment.Start)
    )

    Row(
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable(onClick = onDateClick)
      ) {
        Text(
          text = dateText,
          style = MaterialTheme.typography.bodyLarge
        )
        Icon(
          painter = painterResource(R.drawable.ic_expand_down_24),
          contentDescription = null,
          modifier = Modifier
            .padding(start = 8.dp)
            .size(24.dp)
        )
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable(onClick = onTimeClick)
      ) {
        Text(
          text = timeText,
          style = MaterialTheme.typography.bodyLarge
        )
        Icon(
          painter = painterResource(R.drawable.ic_expand_down_24),
          contentDescription = null,
          modifier = Modifier
            .padding(start = 8.dp)
            .size(24.dp)
        )
      }
    }

    Row(
      horizontalArrangement = Arrangement.End,
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 24.dp)
    ) {
      Buttons.MediumTonal(
        onClick = onMuteClick
      ) {
        Text(stringResource(R.string.MuteUntilTimePickerBottomSheet__mute_notifications))
      }
    }
  }
}

@DayNightPreviews
@Composable
private fun MuteUntilSheetContentPreview() {
  Previews.BottomSheetContentPreview {
    MuteUntilSheetContent(
      dateText = "Today",
      timeText = "5:00 PM",
      timezoneDisclaimer = "All times in (GMT-05:00) Eastern Standard Time",
      onDateClick = {},
      onTimeClick = {},
      onMuteClick = {}
    )
  }
}
