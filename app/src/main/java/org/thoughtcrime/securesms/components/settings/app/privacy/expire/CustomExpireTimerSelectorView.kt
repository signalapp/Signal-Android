package org.thoughtcrime.securesms.components.settings.app.privacy.expire

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.NumberPicker
import org.thoughtcrime.securesms.R
import java.util.concurrent.TimeUnit

/**
 * Show number pickers for value and units that are valid for expiration timer.
 */
class CustomExpireTimerSelectorView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

  private val valuePicker: NumberPicker
  private val unitPicker: NumberPicker

  init {
    orientation = HORIZONTAL
    gravity = Gravity.CENTER
    inflate(context, R.layout.custom_expire_timer_selector_view, this)

    valuePicker = findViewById(R.id.custom_expire_timer_selector_value)
    unitPicker = findViewById(R.id.custom_expire_timer_selector_unit)

    valuePicker.minValue = TimerUnit.get(1).minValue
    valuePicker.maxValue = TimerUnit.get(1).maxValue

    unitPicker.minValue = 0
    unitPicker.maxValue = 4
    unitPicker.value = 1
    unitPicker.wrapSelectorWheel = false
    unitPicker.isLongClickable = false
    unitPicker.displayedValues = context.resources.getStringArray(R.array.CustomExpireTimerSelectorView__unit_labels)
    unitPicker.setOnValueChangedListener { _, _, newValue -> unitChange(newValue) }
  }

  fun setTimer(timer: Int?) {
    if (timer == null || timer == 0) {
      return
    }

    TimerUnit.values()
      .find { (timer / it.valueMultiplier) < it.maxValue }
      ?.let { timerUnit ->
        valuePicker.value = (timer / timerUnit.valueMultiplier).toInt()
        unitPicker.value = TimerUnit.values().indexOf(timerUnit)
        unitChange(unitPicker.value)
      }
  }

  fun getTimer(): Int {
    return valuePicker.value * TimerUnit.get(unitPicker.value).valueMultiplier.toInt()
  }

  private fun unitChange(newValue: Int) {
    val timerUnit: TimerUnit = TimerUnit.values()[newValue]

    valuePicker.minValue = timerUnit.minValue
    valuePicker.maxValue = timerUnit.maxValue
  }

  fun setUnits(minValue: Int, maxValue: Int, timeUnitRes: Int) {
    unitPicker.minValue = minValue
    unitPicker.maxValue = maxValue
    unitPicker.displayedValues = context.resources.getStringArray(timeUnitRes)
  }

  private enum class TimerUnit(val minValue: Int, val maxValue: Int, val valueMultiplier: Long) {
    SECONDS(1, 59, TimeUnit.SECONDS.toSeconds(1)),
    MINUTES(1, 59, TimeUnit.MINUTES.toSeconds(1)),
    HOURS(1, 23, TimeUnit.HOURS.toSeconds(1)),
    DAYS(1, 6, TimeUnit.DAYS.toSeconds(1)),
    WEEKS(1, 4, TimeUnit.DAYS.toSeconds(7));

    companion object {
      fun get(value: Int) = values()[value]
    }
  }
}
