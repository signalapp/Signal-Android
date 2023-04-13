package org.thoughtcrime.securesms.components.settings.app.notifications.profiles

import android.content.Context
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.format.DateFormat
import android.text.style.AbsoluteSizeSpan
import android.view.View
import android.widget.CheckedTextView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.signal.core.util.concurrent.LifecycleDisposable
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.notifications.profiles.EditNotificationProfileScheduleViewModel.SaveScheduleResult
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.formatHours
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import org.thoughtcrime.securesms.util.orderOfDaysInWeek
import org.thoughtcrime.securesms.util.views.CircularProgressMaterialButton
import org.thoughtcrime.securesms.util.visible
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
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

/**
 * Can edit existing or use during create flow to setup a profile schedule.
 */
class EditNotificationProfileScheduleFragment : LoggingFragment(R.layout.fragment_edit_notification_profile_schedule) {

  private val viewModel: EditNotificationProfileScheduleViewModel by viewModels(factoryProducer = { EditNotificationProfileScheduleViewModel.Factory(profileId, createMode) })
  private val lifecycleDisposable = LifecycleDisposable()

  private val profileId: Long by lazy { EditNotificationProfileScheduleFragmentArgs.fromBundle(requireArguments()).profileId }
  private val createMode: Boolean by lazy { EditNotificationProfileScheduleFragmentArgs.fromBundle(requireArguments()).createMode }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    val toolbar: Toolbar = view.findViewById(R.id.toolbar)
    toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

    val title: View = view.findViewById(R.id.edit_notification_profile_schedule_title)
    val description: View = view.findViewById(R.id.edit_notification_profile_schedule_description)

    toolbar.title = if (!createMode) getString(R.string.EditNotificationProfileSchedule__schedule) else null
    title.visible = createMode
    description.visible = createMode

    lifecycleDisposable.bindTo(viewLifecycleOwner.lifecycle)

    val enableToggle: MaterialSwitch = view.findViewById(R.id.edit_notification_profile_schedule_switch)
    enableToggle.setOnClickListener { viewModel.setEnabled(enableToggle.isChecked) }

    val startTime: TextView = view.findViewById(R.id.edit_notification_profile_schedule_start_time)
    val endTime: TextView = view.findViewById(R.id.edit_notification_profile_schedule_end_time)

    val next: CircularProgressMaterialButton = view.findViewById(R.id.edit_notification_profile_schedule__next)
    next.setOnClickListener {
      lifecycleDisposable += viewModel.save(createMode)
        .subscribeBy(
          onSuccess = { result ->
            when (result) {
              SaveScheduleResult.Success -> {
                if (createMode) {
                  findNavController().safeNavigate(EditNotificationProfileScheduleFragmentDirections.actionEditNotificationProfileScheduleFragmentToNotificationProfileCreatedFragment(profileId))
                } else {
                  findNavController().navigateUp()
                }
              }
              SaveScheduleResult.NoDaysSelected -> {
                Toast.makeText(requireContext(), R.string.EditNotificationProfileSchedule__schedule_must_have_at_least_one_day, Toast.LENGTH_LONG).show()
              }
            }
          }
        )
    }

    val day1: CheckedTextView = view.findViewById(R.id.edit_notification_profile_schedule_day_1)
    val day2: CheckedTextView = view.findViewById(R.id.edit_notification_profile_schedule_day_2)
    val day3: CheckedTextView = view.findViewById(R.id.edit_notification_profile_schedule_day_3)
    val day4: CheckedTextView = view.findViewById(R.id.edit_notification_profile_schedule_day_4)
    val day5: CheckedTextView = view.findViewById(R.id.edit_notification_profile_schedule_day_5)
    val day6: CheckedTextView = view.findViewById(R.id.edit_notification_profile_schedule_day_6)
    val day7: CheckedTextView = view.findViewById(R.id.edit_notification_profile_schedule_day_7)

    val days: Map<CheckedTextView, DayOfWeek> = listOf(day1, day2, day3, day4, day5, day6, day7).zip(Locale.getDefault().orderOfDaysInWeek()).toMap()

    days.forEach { (view, day) ->
      DrawableCompat.setTintList(view.background, ContextCompat.getColorStateList(view.context, R.color.notification_profile_schedule_background_tint))
      view.setOnClickListener { viewModel.toggleDay(day) }
      view.setText(DAY_TO_STARTING_LETTER[day]!!)
    }

    lifecycleDisposable += viewModel.schedule()
      .subscribeBy(
        onNext = { schedule ->
          enableToggle.isChecked = schedule.enabled
          enableToggle.isEnabled = true

          days.forEach { (view, day) ->
            view.isChecked = schedule.daysEnabled.contains(day)
            view.isEnabled = schedule.enabled
          }

          startTime.text = schedule.startTime().formatTime(view.context)
          startTime.setOnClickListener { showTimeSelector(true, schedule.startTime()) }
          startTime.isEnabled = schedule.enabled

          endTime.text = schedule.endTime().formatTime(view.context)
          endTime.setOnClickListener { showTimeSelector(false, schedule.endTime()) }
          endTime.isEnabled = schedule.enabled

          if (createMode) {
            next.setText(if (schedule.enabled) R.string.EditNotificationProfileSchedule__next else R.string.EditNotificationProfileSchedule__skip)
          } else {
            next.setText(R.string.EditNotificationProfileSchedule__save)
          }
          next.isEnabled = true
        }
      )
  }

  private fun showTimeSelector(isStart: Boolean, time: LocalTime) {
    val timeFormat = if (DateFormat.is24HourFormat(requireContext())) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H
    val timePickerFragment = MaterialTimePicker.Builder()
      .setTimeFormat(timeFormat)
      .setHour(time.hour)
      .setMinute(time.minute)
      .setTitleText(if (isStart) R.string.EditNotificationProfileSchedule__set_start_time else R.string.EditNotificationProfileSchedule__set_end_time)
      .build()

    timePickerFragment.addOnDismissListener {
      timePickerFragment.clearOnDismissListeners()
      timePickerFragment.clearOnPositiveButtonClickListeners()
    }

    timePickerFragment.addOnPositiveButtonClickListener {
      val hour = timePickerFragment.hour
      val minute = timePickerFragment.minute

      if (isStart) {
        viewModel.setStartTime(hour, minute)
      } else {
        viewModel.setEndTime(hour, minute)
      }
    }

    timePickerFragment.show(childFragmentManager, "TIME_PICKER")
  }
}

private fun LocalTime.formatTime(context: Context): SpannableString {
  val amPm = DateTimeFormatter.ofPattern("a")
    .format(this)

  val formattedTime: String = this.formatHours(context)

  return SpannableString(formattedTime).apply {
    val amPmIndex = formattedTime.indexOf(string = amPm, ignoreCase = true)
    if (amPmIndex != -1) {
      setSpan(AbsoluteSizeSpan(ViewUtil.spToPx(20f)), amPmIndex, amPmIndex + amPm.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
  }
}
