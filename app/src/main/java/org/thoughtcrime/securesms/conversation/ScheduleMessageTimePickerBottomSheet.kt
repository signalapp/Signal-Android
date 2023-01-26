package org.thoughtcrime.securesms.conversation

import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.FixedRoundedCornerBottomSheetDialogFragment
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.databinding.ScheduleMessageTimePickerBottomSheetBinding
import org.thoughtcrime.securesms.util.BottomSheetUtil
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.atMidnight
import org.thoughtcrime.securesms.util.atUTC
import org.thoughtcrime.securesms.util.formatHours
import org.thoughtcrime.securesms.util.fragments.findListener
import org.thoughtcrime.securesms.util.toLocalDateTime
import org.thoughtcrime.securesms.util.toMillis
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Bottom sheet dialog that allows selecting a timestamp after the current time for
 * scheduling a message send.
 *
 * Will call [ScheduleCallback.onScheduleSend] with the selected time, if called with [showSchedule]
 * Will call [RescheduleCallback.onReschedule] with the selected time, if called with [showReschedule]
 */
class ScheduleMessageTimePickerBottomSheet : FixedRoundedCornerBottomSheetDialogFragment() {
  override val peekHeightPercentage: Float = 0.66f
  override val themeResId: Int = R.style.Widget_Signal_FixedRoundedCorners_Messages

  private var scheduledDate: Long = 0
  private var scheduledHour: Int = 0
  private var scheduledMinute: Int = 0

  private val binding by ViewBinderDelegate(ScheduleMessageTimePickerBottomSheetBinding::bind)

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    return inflater.inflate(R.layout.schedule_message_time_picker_bottom_sheet, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    val initialTime = arguments?.getLong(KEY_INITIAL_TIME)
    scheduledDate = initialTime ?: System.currentTimeMillis()
    var scheduledLocalDateTime = scheduledDate.toLocalDateTime()
    if (initialTime == null) {
      scheduledLocalDateTime = scheduledLocalDateTime.plusMinutes(5L - (scheduledLocalDateTime.minute % 5))
    }

    scheduledHour = scheduledLocalDateTime.hour
    scheduledMinute = scheduledLocalDateTime.minute

    binding.scheduleSend.setOnClickListener {
      dismiss()
      val messageId = arguments?.getLong(KEY_MESSAGE_ID)
      if (messageId == null) {
        findListener<ScheduleCallback>()?.onScheduleSend(getSelectedTimestamp())
      } else {
        val selectedTime = getSelectedTimestamp()
        if (selectedTime != arguments?.getLong(KEY_INITIAL_TIME)) {
          findListener<RescheduleCallback>()?.onReschedule(selectedTime, messageId)
        }
      }
    }

    val zoneOffsetFormatter = DateTimeFormatter.ofPattern("OOOO")
    val zoneNameFormatter = DateTimeFormatter.ofPattern("zzzz")
    val zonedDateTime = ZonedDateTime.now()
    binding.timezoneDisclaimer.apply {
      text = getString(
        R.string.ScheduleMessageTimePickerBottomSheet__timezone_disclaimer,
        zoneOffsetFormatter.format(zonedDateTime),
        zoneNameFormatter.format(zonedDateTime),
      )
    }

    updateSelectedDate()
    updateSelectedTime()

    setupDateSelector()
    setupTimeSelector()
  }

  private fun setupDateSelector() {
    binding.daySelector.setOnClickListener {
      val local = LocalDateTime.now()
        .atMidnight()
        .atUTC()
        .toMillis()
      val datePicker =
        MaterialDatePicker.Builder.datePicker()
          .setTitleText(getString(R.string.ScheduleMessageTimePickerBottomSheet__select_date_title))
          .setSelection(scheduledDate)
          .setCalendarConstraints(CalendarConstraints.Builder().setStart(local).setValidator(DateValidatorPointForward.now()).build())
          .build()

      datePicker.addOnDismissListener {
        datePicker.clearOnDismissListeners()
        datePicker.clearOnPositiveButtonClickListeners()
      }

      datePicker.addOnPositiveButtonClickListener {
        it.let {
          scheduledDate = it.toLocalDateTime(ZoneOffset.UTC).atZone(ZoneId.systemDefault()).toMillis()
          updateSelectedDate()
        }
      }
      datePicker.show(childFragmentManager, "DATE_PICKER")
    }
  }

  private fun setupTimeSelector() {
    binding.timeSelector.setOnClickListener {
      val timeFormat = if (DateFormat.is24HourFormat(requireContext())) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H
      val timePickerFragment = MaterialTimePicker.Builder()
        .setTimeFormat(timeFormat)
        .setHour(scheduledHour)
        .setMinute(scheduledMinute)
        .setTitleText(getString(R.string.ScheduleMessageTimePickerBottomSheet__select_time_title))
        .build()

      timePickerFragment.addOnDismissListener {
        timePickerFragment.clearOnDismissListeners()
        timePickerFragment.clearOnPositiveButtonClickListeners()
      }

      timePickerFragment.addOnPositiveButtonClickListener {
        scheduledHour = timePickerFragment.hour
        scheduledMinute = timePickerFragment.minute

        updateSelectedTime()
      }

      timePickerFragment.show(childFragmentManager, "TIME_PICKER")
    }
  }

  private fun getSelectedTimestamp(): Long {
    return scheduledDate.toLocalDateTime()
      .withMinute(scheduledMinute)
      .withHour(scheduledHour)
      .withSecond(0)
      .withNano(0)
      .toMillis()
  }

  private fun updateSelectedDate() {
    binding.dateText.text = DateUtils.getDayPrecisionTimeString(requireContext(), Locale.getDefault(), scheduledDate)
  }

  private fun updateSelectedTime() {
    val scheduledTime = LocalTime.of(scheduledHour, scheduledMinute)
    binding.timeText.text = scheduledTime.formatHours(requireContext())
  }

  interface ScheduleCallback {
    fun onScheduleSend(scheduledTime: Long)
  }

  interface RescheduleCallback {
    fun onReschedule(scheduledTime: Long, messageId: Long)
  }

  companion object {

    private const val KEY_MESSAGE_ID = "message_id"
    private const val KEY_INITIAL_TIME = "initial_time"

    @JvmStatic
    fun showSchedule(fragmentManager: FragmentManager) {
      val fragment = ScheduleMessageTimePickerBottomSheet()

      fragment.show(fragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
    }

    @JvmStatic
    fun showReschedule(fragmentManager: FragmentManager, messageId: Long, initialTime: Long) {
      val args = Bundle().apply {
        putLong(KEY_MESSAGE_ID, messageId)
        putLong(KEY_INITIAL_TIME, initialTime)
      }

      val fragment = ScheduleMessageTimePickerBottomSheet().apply {
        arguments = args
      }

      fragment.show(fragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
    }
  }
}
