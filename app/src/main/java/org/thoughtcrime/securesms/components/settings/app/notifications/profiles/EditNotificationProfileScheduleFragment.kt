package org.thoughtcrime.securesms.components.settings.app.notifications.profiles

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
import com.dd.CircularProgressButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.notifications.profiles.EditNotificationProfileScheduleViewModel.SaveScheduleResult
import org.thoughtcrime.securesms.util.LifecycleDisposable
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.formatHours
import org.thoughtcrime.securesms.util.visible
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Can edit existing or use during create flow to setup a profile schedule.
 */
class EditNotificationProfileScheduleFragment : LoggingFragment(R.layout.fragment_edit_notification_profile_schedule) {

  private val viewModel: EditNotificationProfileScheduleViewModel by viewModels(factoryProducer = { EditNotificationProfileScheduleViewModel.Factory(profileId) })
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

    val enableToggle: SwitchMaterial = view.findViewById(R.id.edit_notification_profile_schedule_switch)
    enableToggle.setOnClickListener { viewModel.setEnabled(enableToggle.isChecked) }

    val startTime: TextView = view.findViewById(R.id.edit_notification_profile_schedule_start_time)
    val endTime: TextView = view.findViewById(R.id.edit_notification_profile_schedule_end_time)

    val next: CircularProgressButton = view.findViewById(R.id.edit_notification_profile_schedule__next)
    next.setOnClickListener {
      lifecycleDisposable += viewModel.save(createMode)
        .subscribeBy(
          onSuccess = { result ->
            when (result) {
              SaveScheduleResult.Success -> {
                if (createMode) {
                  findNavController().navigate(EditNotificationProfileScheduleFragmentDirections.actionEditNotificationProfileScheduleFragmentToNotificationProfileCreatedFragment(profileId))
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

    val sunday: CheckedTextView = view.findViewById(R.id.edit_notification_profile_schedule_sunday)
    val monday: CheckedTextView = view.findViewById(R.id.edit_notification_profile_schedule_monday)
    val tuesday: CheckedTextView = view.findViewById(R.id.edit_notification_profile_schedule_tuesday)
    val wednesday: CheckedTextView = view.findViewById(R.id.edit_notification_profile_schedule_wednesday)
    val thursday: CheckedTextView = view.findViewById(R.id.edit_notification_profile_schedule_thursday)
    val friday: CheckedTextView = view.findViewById(R.id.edit_notification_profile_schedule_friday)
    val saturday: CheckedTextView = view.findViewById(R.id.edit_notification_profile_schedule_saturday)

    val days: Map<CheckedTextView, DayOfWeek> = mapOf(
      sunday to DayOfWeek.SUNDAY,
      monday to DayOfWeek.MONDAY,
      tuesday to DayOfWeek.TUESDAY,
      wednesday to DayOfWeek.WEDNESDAY,
      thursday to DayOfWeek.THURSDAY,
      friday to DayOfWeek.FRIDAY,
      saturday to DayOfWeek.SATURDAY
    )

    days.forEach { (view, day) ->
      DrawableCompat.setTintList(view.background, ContextCompat.getColorStateList(requireContext(), R.color.notification_profile_schedule_background_tint))
      view.setOnClickListener { viewModel.toggleDay(day) }
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

          startTime.text = schedule.startTime().formatTime()
          startTime.setOnClickListener { showTimeSelector(true, schedule.startTime()) }
          startTime.isEnabled = schedule.enabled

          endTime.text = schedule.endTime().formatTime()
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

private fun LocalTime.formatTime(): SpannableString {
  val amPm = DateTimeFormatter.ofPattern("a")
    .format(this)

  val formattedTime: String = this.formatHours()

  return SpannableString(formattedTime).apply {
    val amPmIndex = formattedTime.indexOf(string = amPm, ignoreCase = true)
    if (amPmIndex != -1) {
      setSpan(AbsoluteSizeSpan(ViewUtil.spToPx(20f)), amPmIndex, amPmIndex + amPm.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
  }
}
